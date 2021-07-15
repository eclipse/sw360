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
package org.eclipse.sw360.rest.resourceserver.release;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.common.SW360Constants;
import org.eclipse.sw360.datahandler.resourcelists.PaginationParameterException;
import org.eclipse.sw360.datahandler.resourcelists.PaginationResult;
import org.eclipse.sw360.datahandler.resourcelists.ResourceClassNotFoundException;
import org.eclipse.sw360.datahandler.thrift.ReleaseRelationship;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.Source;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.components.ClearingState;
import org.eclipse.sw360.datahandler.thrift.components.Component;
import org.eclipse.sw360.datahandler.thrift.components.ExternalToolProcess;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.eclipse.sw360.rest.resourceserver.attachment.AttachmentInfo;
import org.eclipse.sw360.rest.resourceserver.attachment.Sw360AttachmentService;
import org.eclipse.sw360.rest.resourceserver.component.ComponentController;
import org.eclipse.sw360.rest.resourceserver.core.HalResource;
import org.eclipse.sw360.rest.resourceserver.core.MultiStatus;
import org.eclipse.sw360.rest.resourceserver.core.RestControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.BasePathAwareController;
import org.springframework.data.rest.webmvc.RepositoryLinksResource;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceProcessor;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.http.converter.HttpMessageNotReadableException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.eclipse.sw360.datahandler.common.WrappedException.wrapTException;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;

@BasePathAwareController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ReleaseController implements ResourceProcessor<RepositoryLinksResource> {
    public static final String RELEASES_URL = "/releases";
    private static final Logger log = LogManager.getLogger(ReleaseController.class);
    private static final Map<String, ReentrantLock> mapOfLocks = new HashMap<String, ReentrantLock>();
    private static final ImmutableMap<Release._Fields,String> mapOfFieldsTobeEmbedded = ImmutableMap.of(
            Release._Fields.MODERATORS, "sw360:moderators",
            Release._Fields.ATTACHMENTS, "sw360:attachments",
            Release._Fields.COTS_DETAILS, "sw360:cotsDetails",
            Release._Fields.RELEASE_ID_TO_RELATIONSHIP,"sw360:releaseIdToRelationship",
            Release._Fields.CLEARING_INFORMATION, "sw360:clearingInformation");
    private static final ImmutableMap<Release._Fields, String[]> mapOfBackwardCompatible_Field_OldFieldNames_NewFieldNames = ImmutableMap.<Release._Fields, String[]>builder()
            .put(Release._Fields.SOURCE_CODE_DOWNLOADURL, new String[] { "downloadurl", "sourceCodeDownloadurl" })
            .build();
    private static final ImmutableMap<String, String> RESPONSE_BODY_FOR_MODERATION_REQUEST = ImmutableMap.<String, String>builder()
            .put("message", "Moderation request is created").build();

    @NonNull
    private Sw360ReleaseService releaseService;

    @NonNull
    private Sw360AttachmentService attachmentService;

    @NonNull
    private RestControllerHelper restControllerHelper;

    @NonNull
    private final com.fasterxml.jackson.databind.Module sw360Module;

    @GetMapping(value = RELEASES_URL)
    public ResponseEntity<Resources<Resource>> getReleasesForUser(
            Pageable pageable,
            @RequestParam(value = "sha1", required = false) String sha1,
            @RequestParam(value = "fields", required = false) List<String> fields,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "allDetails", required = false) boolean allDetails, HttpServletRequest request) throws TException, URISyntaxException, PaginationParameterException, ResourceClassNotFoundException {

        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        List<Release> sw360Releases = new ArrayList<>();

        if (sha1 != null && !sha1.isEmpty()) {
            sw360Releases.addAll(searchReleasesBySha1(sha1, sw360User));
        } else {
            sw360Releases.addAll(releaseService.getReleasesForUser(sw360User));
        }

        sw360Releases = sw360Releases.stream()
                .filter(release -> name == null || name.isEmpty() || release.getName().equals(name))
                .collect(Collectors.toList());

        PaginationResult<Release> paginationResult = restControllerHelper.createPaginationResult(request, pageable, sw360Releases, SW360Constants.TYPE_RELEASE);

        List<Resource> releaseResources = new ArrayList<>();
        for (Release sw360Release : paginationResult.getResources()) {
            Resource<Release> releaseResource = null;
            if (!allDetails) {
                Release embeddedRelease = restControllerHelper.convertToEmbeddedRelease(sw360Release, fields);
                releaseResource = new Resource<>(embeddedRelease);
            } else {
                releaseResource = createHalReleaseResourceWithAllDetails(sw360Release);
            }

            releaseResources.add(releaseResource);
        }

        Resources resources = null;
        if (CommonUtils.isNotEmpty(releaseResources)) {
            resources = restControllerHelper.generatePagesResource(paginationResult, releaseResources);
        }

        HttpStatus status = resources == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(resources, status);
    }

    private List<Release> searchReleasesBySha1(String sha1, User sw360User) throws TException {
        List<AttachmentInfo> attachmentInfos = attachmentService.getAttachmentsBySha1(sha1);
        List<Release> releases = new ArrayList<>();
        for (AttachmentInfo attachmentInfo : attachmentInfos) {
            if (attachmentInfo.getOwner().isSetReleaseId()) {
                releases.add(releaseService.getReleaseForUserById(attachmentInfo.getOwner().getReleaseId(), sw360User));
            }
        }
        return releases;
    }

    @GetMapping(value = RELEASES_URL + "/{id}")
    public ResponseEntity<Resource> getRelease(
            @PathVariable("id") String id) throws TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        Release sw360Release = releaseService.getReleaseForUserById(id, sw360User);
        HalResource halRelease = createHalReleaseResource(sw360Release, true);
        Map<String, ReleaseRelationship> releaseIdToRelationship = sw360Release.getReleaseIdToRelationship();
        if (releaseIdToRelationship != null) {
            List<Release> listOfLinkedRelease = releaseIdToRelationship.keySet().stream()
                    .map(linkedReleaseId -> wrapTException(
                            () -> releaseService.getReleaseForUserById(linkedReleaseId, sw360User)))
                    .collect(Collectors.toList());
            restControllerHelper.addEmbeddedReleases(halRelease, listOfLinkedRelease);
        }
        return new ResponseEntity<>(halRelease, HttpStatus.OK);
    }

    @RequestMapping(value = RELEASES_URL + "/usedBy" + "/{id}", method = RequestMethod.GET)
    public ResponseEntity<Resources<Resource>> getUsedByResourceDetails(@PathVariable("id") String id)
            throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication(); // Project
        Set<org.eclipse.sw360.datahandler.thrift.projects.Project> sw360Projects = releaseService.getProjectsByRelease(id, user);
        Set<org.eclipse.sw360.datahandler.thrift.components.Component> sw360Components = releaseService.getUsingComponentsForRelease(id, user);

        List<Resource> resources = new ArrayList<>();
        sw360Projects.forEach(p -> {
            Project embeddedProject = restControllerHelper.convertToEmbeddedProject(p);
            resources.add(new Resource<>(embeddedProject));
        });

        sw360Components.forEach(c -> {
                    Component embeddedComponent = restControllerHelper.convertToEmbeddedComponent(c);
                    resources.add(new Resource<>(embeddedComponent));
                });

        Resources<Resource> finalResources = restControllerHelper.createResources(resources);
        HttpStatus status = finalResources == null ? HttpStatus.NO_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(finalResources, status);
    }

    @GetMapping(value = RELEASES_URL + "/searchByExternalIds")
    public ResponseEntity searchByExternalIds(@RequestParam MultiValueMap<String, String> externalIdsMultiMap) throws TException {
        return restControllerHelper.searchByExternalIds(externalIdsMultiMap, releaseService, null);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @DeleteMapping(value = RELEASES_URL + "/{ids}")
    public ResponseEntity<List<MultiStatus>> deleteReleases(
            @PathVariable("ids") List<String> idsToDelete) throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication();
        List<MultiStatus> results = new ArrayList<>();
        for(String id:idsToDelete) {
            RequestStatus requestStatus = releaseService.deleteRelease(id, user);
            if(requestStatus == RequestStatus.SUCCESS) {
                results.add(new MultiStatus(id, HttpStatus.OK));
            } else if (requestStatus == RequestStatus.SENT_TO_MODERATOR) {
                results.add(new MultiStatus(id, HttpStatus.ACCEPTED));
            } else if (requestStatus == RequestStatus.IN_USE) {
                results.add(new MultiStatus(id, HttpStatus.CONFLICT));
            } else {
                results.add(new MultiStatus(id, HttpStatus.INTERNAL_SERVER_ERROR));
            }
        }
        return new ResponseEntity<>(results, HttpStatus.MULTI_STATUS);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(value = RELEASES_URL + "/{id}")
    public ResponseEntity<Resource<Release>> patchRelease(
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> reqBodyMap) throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication();
        Release sw360Release = releaseService.getReleaseForUserById(id, user);
        Release updateRelease = setBackwardCompatibleFieldsInRelease(reqBodyMap);
        updateRelease.setClearingState(sw360Release.getClearingState());
        sw360Release = this.restControllerHelper.updateRelease(sw360Release, updateRelease);
        releaseService.setComponentNameAsReleaseName(sw360Release, user);
        RequestStatus updateReleaseStatus = releaseService.updateRelease(sw360Release, user);
        HalResource<Release> halRelease = createHalReleaseResource(sw360Release, true);
        if (updateReleaseStatus == RequestStatus.SENT_TO_MODERATOR) {
            return new ResponseEntity(RESPONSE_BODY_FOR_MODERATION_REQUEST, HttpStatus.ACCEPTED);
        }
        return new ResponseEntity<>(halRelease, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PostMapping(value = RELEASES_URL)
    public ResponseEntity<Resource<Release>> createRelease(
            @RequestBody Map<String, Object> reqBodyMap) throws URISyntaxException, TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        Release release = setBackwardCompatibleFieldsInRelease(reqBodyMap);
        if (release.isSetComponentId()) {
            URI componentURI = new URI(release.getComponentId());
            String path = componentURI.getPath();
            String componentId = path.substring(path.lastIndexOf('/') + 1);
            release.setComponentId(componentId);
        }
        if (release.isSetVendorId()) {
            URI vendorURI = new URI(release.getVendorId());
            String path = vendorURI.getPath();
            String vendorId = path.substring(path.lastIndexOf('/') + 1);
            release.setVendorId(vendorId);
        }

        if (release.getMainLicenseIds() != null) {
            Set<String> mainLicenseIds = new HashSet<>();
            Set<String> mainLicenseUris = release.getMainLicenseIds();
            for (String licenseURIString : mainLicenseUris.toArray(new String[mainLicenseUris.size()])) {
                URI licenseURI = new URI(licenseURIString);
                String path = licenseURI.getPath();
                String licenseId = path.substring(path.lastIndexOf('/') + 1);
                mainLicenseIds.add(licenseId);
            }
            release.setMainLicenseIds(mainLicenseIds);
        }

        release.unsetClearingState();
        Release sw360Release = releaseService.createRelease(release, sw360User);
        HalResource<Release> halResource = createHalReleaseResource(sw360Release, true);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(sw360Release.getId()).toUri();

        return ResponseEntity.created(location).body(halResource);
    }

    @GetMapping(value = RELEASES_URL + "/{id}/attachments")
    public ResponseEntity<Resources<Resource<Attachment>>> getReleaseAttachments(
            @PathVariable("id") String id) throws TException {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final Release sw360Release = releaseService.getReleaseForUserById(id, sw360User);
        final Resources<Resource<Attachment>> resources = attachmentService.getResourcesFromList(sw360Release.getAttachments());
        return new ResponseEntity<>(resources, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(value = RELEASES_URL + "/{id}/attachment/{attachmentId}")
    public ResponseEntity<Resource<Attachment>> patchReleaseAttachmentInfo(@PathVariable("id") String id,
            @PathVariable("attachmentId") String attachmentId, @RequestBody Attachment attachmentData)
            throws TException {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final Release sw360Release = releaseService.getReleaseForUserById(id, sw360User);
        Set<Attachment> attachments = sw360Release.getAttachments();
        Attachment updatedAttachment = attachmentService.updateAttachment(attachments, attachmentData, attachmentId, sw360User);
        RequestStatus updateReleaseStatus = releaseService.updateRelease(sw360Release, sw360User);
        if (updateReleaseStatus == RequestStatus.SENT_TO_MODERATOR) {
            return new ResponseEntity(RESPONSE_BODY_FOR_MODERATION_REQUEST, HttpStatus.ACCEPTED);
        }
        Resource<Attachment> attachmentResource = new Resource<>(updatedAttachment);
        return new ResponseEntity<>(attachmentResource, HttpStatus.OK);
    }

    @PostMapping(value = RELEASES_URL + "/{releaseId}/attachments", consumes = {"multipart/mixed", "multipart/form-data"})
    public ResponseEntity<HalResource> addAttachmentToRelease(@PathVariable("releaseId") String releaseId,
                                                              @RequestPart("file") MultipartFile file,
                                                              @RequestPart("attachment") Attachment newAttachment) throws TException {
        final User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        final Release release = releaseService.getReleaseForUserById(releaseId, sw360User);
        Attachment attachment = null;
        try {
            attachment = attachmentService.uploadAttachment(file, newAttachment, sw360User);
        } catch (IOException e) {
            log.error("failed to upload attachment", e);
            throw new RuntimeException("failed to upload attachment", e);
        }

        release.addToAttachments(attachment);
        RequestStatus updateReleaseStatus = releaseService.updateRelease(release, sw360User);
        HalResource halRelease = createHalReleaseResource(release, true);
        if (updateReleaseStatus == RequestStatus.SENT_TO_MODERATOR) {
            return new ResponseEntity(RESPONSE_BODY_FOR_MODERATION_REQUEST, HttpStatus.ACCEPTED);
        }
        return new ResponseEntity<>(halRelease, HttpStatus.OK);
    }

    @GetMapping(value = RELEASES_URL + "/{releaseId}/attachments/{attachmentId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void downloadAttachmentFromRelease(
            @PathVariable("releaseId") String releaseId,
            @PathVariable("attachmentId") String attachmentId,
            HttpServletResponse response) throws TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        Release release = releaseService.getReleaseForUserById(releaseId, sw360User);
        attachmentService.downloadAttachmentWithContext(release, attachmentId, response, sw360User);
    }

    @DeleteMapping(RELEASES_URL + "/{releaseId}/attachments/{attachmentIds}")
    public ResponseEntity<HalResource<Release>> deleteAttachmentsFromRelease(
            @PathVariable("releaseId") String releaseId,
            @PathVariable("attachmentIds") List<String> attachmentIds) throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication();
        Release release = releaseService.getReleaseForUserById(releaseId, user);

        Set<Attachment> attachmentsToDelete = attachmentService.filterAttachmentsToRemove(Source.releaseId(releaseId),
                release.getAttachments(), attachmentIds);
        if (attachmentsToDelete.isEmpty()) {
            // let the whole action fail if nothing can be deleted
            throw new RuntimeException("Could not delete attachments " + attachmentIds + " from release " + releaseId);
        }
        log.debug("Deleting the following attachments from release " + releaseId + ": " + attachmentsToDelete);
        release.getAttachments().removeAll(attachmentsToDelete);
        RequestStatus updateReleaseStatus = releaseService.updateRelease(release, user);
        HalResource<Release> halRelease = createHalReleaseResource(release, true);
        if (updateReleaseStatus == RequestStatus.SENT_TO_MODERATOR) {
            return new ResponseEntity(RESPONSE_BODY_FOR_MODERATION_REQUEST, HttpStatus.ACCEPTED);
        }
        return new ResponseEntity<>(halRelease, HttpStatus.OK);
    }

    @RequestMapping(value = RELEASES_URL + "/{id}/checkFossologyProcessStatus", method = RequestMethod.GET)
    public ResponseEntity<Map<String, Object>> checkFossologyProcessStatus(@PathVariable("id") String releaseId)
            throws TException {
        User user = restControllerHelper.getSw360UserFromAuthentication();
        Release release = releaseService.getReleaseForUserById(releaseId, user);
        Map<String, Object> responseMap = new HashMap<>();
        ExternalToolProcess fossologyProcess = releaseService.getExternalToolProcess(release);
        ReentrantLock lock = mapOfLocks.get(releaseId);
        if (lock != null && lock.isLocked()) {
            responseMap.put("status", RequestStatus.PROCESSING);
        } else if (fossologyProcess != null && releaseService.isFOSSologyProcessCompleted(fossologyProcess)) {
            log.info("FOSSology process for Release : " + releaseId + " is complete.");
            responseMap.put("status", RequestStatus.SUCCESS);
        } else {
            responseMap.put("status", RequestStatus.FAILURE);
        }
        responseMap.put("fossologyProcessInfo", fossologyProcess);
        return new ResponseEntity<>(responseMap, HttpStatus.OK);
    }

    @RequestMapping(value = RELEASES_URL + "/{id}/triggerFossologyProcess", method = RequestMethod.GET)
    public ResponseEntity<HalResource> triggerFossologyProcess(@PathVariable("id") String releaseId,
            @RequestParam(value = "markFossologyProcessOutdated", required = false) boolean markFossologyProcessOutdated,
            HttpServletResponse response) throws TException, IOException {
        releaseService.checkFossologyConnection();

        ReentrantLock lock = mapOfLocks.get(releaseId);
        Map<String, String> responseMap = new HashMap<>();
        HttpStatus status = null;
        if (lock == null || !lock.isLocked()) {
            if (mapOfLocks.size() > 10) {
                responseMap.put("message",
                        "Max 10 FOSSology Process can be triggered simultaneously. Please try after sometime.");
                status = HttpStatus.TOO_MANY_REQUESTS;
            } else {
                User user = restControllerHelper.getSw360UserFromAuthentication();
                releaseService.executeFossologyProcess(user, attachmentService, mapOfLocks, releaseId,
                        markFossologyProcessOutdated);
                responseMap.put("message", "FOSSology Process for Release Id : " + releaseId + " has been triggered.");
                status = HttpStatus.OK;
            }

        } else {
            status = HttpStatus.NOT_ACCEPTABLE;
            responseMap.put("message", "FOSSology Process for Release Id : " + releaseId
                    + " is already running. Please wait till it is completed.");
        }
        HalResource responseResource = new HalResource(responseMap);
        Link checkStatusLink = linkTo(ReleaseController.class).slash("api" + RELEASES_URL).slash(releaseId)
                .slash("checkFossologyProcessStatus").withSelfRel();
        responseResource.add(checkStatusLink);

        return new ResponseEntity<HalResource>(responseResource, status);
    }

    // Link release to release
    @PreAuthorize("hasAuthority('WRITE')")
    @RequestMapping(value = RELEASES_URL + "/{id}/releases", method = RequestMethod.POST)
    public ResponseEntity linkReleases(
            @PathVariable("id") String id,
            @RequestBody Map<String, ReleaseRelationship> releaseIdToRelationship) throws TException {
        User sw360User = restControllerHelper.getSw360UserFromAuthentication();
        Release sw360Release = releaseService.getReleaseForUserById(id, sw360User);
        if (releaseIdToRelationship.isEmpty()) {
            throw new HttpMessageNotReadableException("Input data can not empty!");
        }
        sw360Release.setReleaseIdToRelationship(releaseIdToRelationship);

        RequestStatus updateReleaseStatus = releaseService.updateRelease(sw360Release, sw360User);
        if (updateReleaseStatus == RequestStatus.SENT_TO_MODERATOR) {
            return new ResponseEntity(RESPONSE_BODY_FOR_MODERATION_REQUEST, HttpStatus.ACCEPTED);
        }
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @Override
    public RepositoryLinksResource process(RepositoryLinksResource resource) {
        resource.add(linkTo(ReleaseController.class).slash("api" + RELEASES_URL).withRel("releases"));
        return resource;
    }

    private HalResource<Release> createHalReleaseResource(Release release, boolean verbose) {
        HalResource<Release> halRelease = new HalResource<>(release);
        Link componentLink = linkTo(ReleaseController.class)
                .slash("api" + ComponentController.COMPONENTS_URL + "/" + release.getComponentId()).withRel("component");
        halRelease.add(componentLink);
        release.setComponentId(null);

        if (verbose) {
            if (release.getModerators() != null) {
                Set<String> moderators = release.getModerators();
                restControllerHelper.addEmbeddedModerators(halRelease, moderators);
                release.setModerators(null);
            }
            if (release.getAttachments() != null) {
                Set<Attachment> attachments = release.getAttachments();
                restControllerHelper.addEmbeddedAttachments(halRelease, attachments);
                release.setAttachments(null);
            }
            if (release.getVendor() != null) {
                Vendor vendor = release.getVendor();
                HalResource<Vendor> vendorHalResource = restControllerHelper.addEmbeddedVendor(vendor.getFullname());
                halRelease.addEmbeddedResource("sw360:vendors", vendorHalResource);
                release.setVendor(null);
            }
            if (release.getMainLicenseIds() != null) {
                restControllerHelper.addEmbeddedLicenses(halRelease, release.getMainLicenseIds());
                release.setMainLicenseIds(null);
            }
        }
        return halRelease;
    }

    private HalResource<Release> createHalReleaseResourceWithAllDetails(Release release) {
        HalResource<Release> halRelease = new HalResource<>(release);
        Link componentLink = linkTo(ReleaseController.class)
                .slash("api" + ComponentController.COMPONENTS_URL + "/" + release.getComponentId())
                .withRel("component");
        halRelease.add(componentLink);
        release.setComponentId(null);

        for (Entry<Release._Fields, String> field : mapOfFieldsTobeEmbedded.entrySet()) {
            restControllerHelper.addEmbeddedFields(field.getValue(), release.getFieldValue(field.getKey()), halRelease);
        }
        return halRelease;
    }

    private Release setBackwardCompatibleFieldsInRelease(Map<String, Object> reqBodyMap) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(sw360Module);
        Release release = mapper.convertValue(reqBodyMap, Release.class);
        mapOfBackwardCompatible_Field_OldFieldNames_NewFieldNames.entrySet().stream().forEach(entry -> {
            Release._Fields field = entry.getKey();
            String oldFieldName = entry.getValue()[0];
            String newFieldName = entry.getValue()[1];
            if (!reqBodyMap.containsKey(newFieldName) && reqBodyMap.containsKey(oldFieldName)) {
                release.setFieldValue(field, CommonUtils.nullToEmptyString(reqBodyMap.get(oldFieldName)));
            }
        });

        return release;
    }
}


