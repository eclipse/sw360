/*
 * Copyright Siemens AG, 2017-2018.
 * Copyright Bosch Software Innovations GmbH, 2017.
 * Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.rest.resourceserver.attachment;

import com.google.common.collect.Sets;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.eclipse.sw360.commonIO.AttachmentFrontendUtils;
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.common.DatabaseSettings;
import org.eclipse.sw360.datahandler.common.Duration;
import org.eclipse.sw360.datahandler.common.SW360Utils;
import org.eclipse.sw360.datahandler.couchdb.AttachmentConnector;
import org.eclipse.sw360.datahandler.thrift.Source;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentContent;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentService;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentType;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentUsage;
import org.eclipse.sw360.datahandler.thrift.attachments.CheckStatus;
import org.eclipse.sw360.datahandler.thrift.attachments.LicenseInfoUsage;
import org.eclipse.sw360.datahandler.thrift.attachments.UsageData;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.rest.resourceserver.core.RestControllerHelper;
import org.eclipse.sw360.rest.resourceserver.core.ThriftServiceProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class Sw360AttachmentService {
    @Value("${sw360.thrift-server-url:http://localhost:8080}")
    private String thriftServerUrl;

    @Value("${sw360.couchdb-url:http://localhost:5984}")
    private String couchdbUrl;

    @NonNull
    private final RestControllerHelper restControllerHelper;

    private static final Logger log = LogManager.getLogger(Sw360AttachmentService.class);

    @NonNull
    private final ThriftServiceProvider<AttachmentService.Iface> thriftAttachmentServiceProvider;

    private final Duration downloadTimeout = Duration.durationOf(30, TimeUnit.SECONDS);
    private AttachmentConnector attachmentConnector;

    public List<AttachmentUsage> getAttachemntUsages(String projectId) throws TException {
        AttachmentService.Iface attachmentClient = getThriftAttachmentClient();
        return attachmentClient.getUsedAttachments(Source.projectId(projectId),
                UsageData.licenseInfo(new LicenseInfoUsage(Sets.newHashSet())));
    }

    public AttachmentInfo getAttachmentById(String id) throws TException {
        AttachmentService.Iface attachmentClient = getThriftAttachmentClient();
        List<Attachment> attachments = attachmentClient.getAttachmentsByIds(Collections.singleton(id));
        if (attachments.isEmpty()) {
            throw new ResourceNotFoundException("Attachment not found.");
        }
        return createAttachmentInfo(attachmentClient, attachments.get(0));
    }

    public List<AttachmentInfo> getAttachmentsBySha1(String sha1) throws TException {
        AttachmentService.Iface attachmentClient = getThriftAttachmentClient();
        List<Attachment> attachments = attachmentClient.getAttachmentsBySha1s(Collections.singleton(sha1));
        return createAttachmentInfos(attachmentClient, attachments);
    }

    /**
     * Filters the attachments that can be actually removed before a delete
     * attachments operation. This method can be called by controllers to
     * handle a request to delete attachments. For each attachment to be
     * deleted, it checks whether all criteria are fulfilled: The attachment
     * must belong to the given owner, it must not be in use by a project, and
     * its checked status must not be ACCEPTED. The resulting set contains all
     * the attachments that are safe to be removed.
     *
     * @param owner          the {@code Source} referencing the attachment owner
     * @param allAttachments the full set of attachments of the owner
     * @param idsToDelete    collection with the IDs of the attachments to delete
     * @return the filtered set with attachments that can be removed
     */
    public Set<Attachment> filterAttachmentsToRemove(Source owner, Set<Attachment> allAttachments,
                                                     Collection<String> idsToDelete) throws TException {
        Map<String, Attachment> knownAttachmentIds = allAttachments.stream()
                .collect(Collectors.toMap(Attachment::getAttachmentContentId, v -> v));
        AttachmentService.Iface attachmentService = getThriftAttachmentClient();

        return idsToDelete.stream()
                .map(knownAttachmentIds::get)
                .filter(Objects::nonNull)
                .filter(attachment -> canDeleteAttachment(attachmentService, owner, attachment))
                .collect(Collectors.toSet());
    }

    private static boolean canDeleteAttachment(AttachmentService.Iface attachmentService, Source owner,
                                               Attachment attachment) {
        String id = attachment.getAttachmentContentId();
        if (attachment.getCheckStatus() == CheckStatus.ACCEPTED) {
            log.warn("Attachment " + id + " must not be deleted as it is in status checked.");
            return false;
        }

        try {
            List<AttachmentUsage> usages = attachmentService.getAttachmentUsages(owner, id, null);
            if (usages.stream().anyMatch(usage -> usage.usedBy.isSetProjectId())) {
                log.warn("Attachment " + id + " must not be deleted as it is used by a project.");
                return false;
            }
            return true;
        } catch (TException e) {
            log.error("Could not check attachment usage for " + id);
            return false;
        }
    }

    private AttachmentInfo createAttachmentInfo(AttachmentService.Iface attachmentClient, Attachment attachment)
            throws TException {
        AttachmentInfo attachmentInfo = new AttachmentInfo(attachment);
        attachmentInfo.setOwner(attachmentClient
                .getAttachmentOwnersByIds(Collections.singleton(attachment.getAttachmentContentId())).get(0));
        return attachmentInfo;
    }

    private List<AttachmentInfo> createAttachmentInfos(AttachmentService.Iface attachmentClient,
            List<Attachment> attachments) throws TException {
        List<AttachmentInfo> attachmentInfos = new ArrayList<>();
        for (Attachment attachment : attachments) {
            attachmentInfos.add(createAttachmentInfo(attachmentClient, attachment));
        }
        return attachmentInfos;
    }

    public void downloadAttachmentWithContext(Object context, String attachmentId, HttpServletResponse response, User sw360User) {
        AttachmentContent attachmentContent = getAttachmentContent(attachmentId);

        String filename = attachmentContent.getFilename();
        String contentType = attachmentContent.getContentType();

        try (InputStream attachmentStream = getStreamToAttachments(Collections.singleton(attachmentContent), sw360User, context)) {
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename));
            FileCopyUtils.copy(attachmentStream, response.getOutputStream());
        } catch (TException | IOException e) {
            log.error(e.getMessage());
        }
    }

    public <T> InputStream getStreamToAttachments(Set<AttachmentContent> attachments, User sw360User, T context) throws IOException, TException {
        return new AttachmentFrontendUtils().getStreamToServeAFile(attachments, sw360User, context);
    }

    public Attachment uploadAttachment(MultipartFile file, Attachment newAttachment, User sw360User) throws IOException, TException {
        String fileName = file.getOriginalFilename(); // TODO: shouldn't the fileName be taken from newAttachment?
        String contentType = file.getContentType();
        final AttachmentContent attachmentContent = makeAttachmentContent(fileName, contentType);

        final AttachmentConnector attachmentConnector = getConnector();
        Attachment attachment = new AttachmentFrontendUtils().uploadAttachmentContent(attachmentContent, file.getInputStream(), sw360User);
        attachment.setSha1(attachmentConnector.getSha1FromAttachmentContentId(attachmentContent.getId()));

        AttachmentType attachmentType = newAttachment.getAttachmentType();
        if (attachmentType != null) {
            attachment.setAttachmentType(attachmentType);
        }

        CheckStatus checkStatus = newAttachment.getCheckStatus();
        if (checkStatus != null) {
            attachment.setCheckStatus(checkStatus);
        }

        String createdComment = newAttachment.getCreatedComment();
        if (createdComment != null) {
            attachment.setCreatedComment(createdComment);
        }

        return attachment;
    }

    private AttachmentContent makeAttachmentContent(String filename, String contentType) {
        AttachmentContent attachment = new AttachmentContent()
                .setContentType(contentType)
                .setFilename(filename)
                .setOnlyRemote(false);
        return makeAttachmentContent(attachment);
    }

    private AttachmentContent makeAttachmentContent(AttachmentContent content) {
        try {
            return new AttachmentFrontendUtils().makeAttachmentContent(content);
        } catch (TException e) {
            throw new RuntimeException(e);
        }
    }

    public AttachmentContent getAttachmentContent(String id) {
        try {
            return new AttachmentFrontendUtils().getAttachmentContent(id);
        } catch (TException e) {
            throw new RuntimeException(e);
        }
    }

    public Resources<Resource<Attachment>> getResourcesFromList(Set<Attachment> attachmentList) {
        final List<Resource<Attachment>> attachmentResources = new ArrayList<>();
        for (final Attachment attachment : attachmentList) {
            final Attachment embeddedAttachment = restControllerHelper.convertToEmbeddedAttachment(attachment);
            final Resource<Attachment> attachmentResource = new Resource<>(embeddedAttachment);
            attachmentResources.add(attachmentResource);
        }
        return new Resources<>(attachmentResources);
    }

    public List<AttachmentUsage> getAllAttachmentUsage(String projectId) throws TException {
        AttachmentService.Iface attachmentClient = getThriftAttachmentClient();
        return attachmentClient.getUsedAttachments(Source.projectId(projectId), null);
    }

    public Attachment updateAttachment(Set<Attachment> attachments, Attachment newData, String attachmentId, User user) {
        if (CommonUtils.isNotEmpty(attachments)) {
            Optional<Attachment> matchingAttachment = attachments.stream()
                    .filter(att -> att.attachmentContentId.equals(attachmentId)).findFirst();
            if (matchingAttachment.isPresent()) {
                Attachment actualAtt = matchingAttachment.get();
                updateAttachment(actualAtt, newData, user);
                return actualAtt;
            }
        }
        throw new ResourceNotFoundException("Requested Attachment Not Found");
    }

    private AttachmentConnector getConnector() throws TException {
        if (attachmentConnector == null) makeConnector();
        return attachmentConnector;
    }

    private synchronized void makeConnector() throws TException {
        if (attachmentConnector == null) {
            try {
                attachmentConnector = new AttachmentConnector(DatabaseSettings.getConfiguredClient(), DatabaseSettings.COUCH_DB_ATTACHMENTS, downloadTimeout);
            } catch (MalformedURLException e) {
                log.error("Invalid database address received...", e);
                throw new TException(e);
            }
        }
    }

    private AttachmentService.Iface getThriftAttachmentClient() throws TTransportException {
        return thriftAttachmentServiceProvider.getService(thriftServerUrl);
    }

    private void updateAttachment(Attachment attachmentToUpdate, Attachment reqBodyAttachment, User user) {
        AttachmentType attachmentType = reqBodyAttachment.getAttachmentType();
        String createdComment = reqBodyAttachment.getCreatedComment();
        CheckStatus checkStatus = reqBodyAttachment.getCheckStatus();
        if (attachmentType != null) {
            attachmentToUpdate.setAttachmentType(attachmentType);
        }
        if (createdComment != null) {
            attachmentToUpdate.setCreatedComment(createdComment);
        }
        if (checkStatus != null) {
            attachmentToUpdate.setCheckStatus(checkStatus);
            String checkedComment = reqBodyAttachment.getCheckedComment();
            if (checkStatus != CheckStatus.NOTCHECKED) {
                if (checkedComment != null) {
                    attachmentToUpdate.setCheckedComment(checkedComment);
                }
                attachmentToUpdate.setCheckedBy(user.getEmail());
                attachmentToUpdate.setCheckedTeam(user.getDepartment());
                attachmentToUpdate.setCheckedOn(SW360Utils.getCreatedOn());
            } else {
                attachmentToUpdate.unsetCheckedBy();
                attachmentToUpdate.unsetCheckedTeam();
                attachmentToUpdate.setCheckedComment("");
                attachmentToUpdate.unsetCheckedOn();
            }
        }
    }
}
