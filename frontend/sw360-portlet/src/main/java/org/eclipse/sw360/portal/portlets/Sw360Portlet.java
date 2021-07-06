/*
 * Copyright Siemens AG, 2013-2017, 2019. Part of the SW360 Portal Project.
 * With contributions by Bosch Software Innovations GmbH, 2016.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.portal.portlets;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.portlet.PortletResponseUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.ResourceBundleUtil;

import org.eclipse.sw360.datahandler.thrift.*;
import org.eclipse.sw360.datahandler.thrift.components.ComponentService;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.licenses.License;
import org.eclipse.sw360.datahandler.thrift.licenses.LicenseService;
import org.eclipse.sw360.datahandler.thrift.users.RequestedAction;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.users.UserService;
import org.eclipse.sw360.datahandler.thrift.vendors.VendorService;
import org.eclipse.sw360.exporter.LicsExporter;
import org.eclipse.sw360.exporter.utils.ZipTools;
import org.eclipse.sw360.portal.common.ErrorMessages;
import org.eclipse.sw360.portal.common.PortalConstants;
import org.eclipse.sw360.portal.users.UserCacheHolder;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TSimpleJSONProtocol;
import org.apache.thrift.transport.TTransportException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

import javax.portlet.*;

import static com.google.common.base.Strings.isNullOrEmpty;


abstract public class Sw360Portlet extends MVCPortlet {
    private static final int MAX_LENGTH_USERS_IN_DISPLAY = 100;

    protected static final Logger log = LogManager.getLogger(Sw360Portlet.class);
    protected final ThriftClients thriftClients;

    protected Sw360Portlet() {
        thriftClients = new ThriftClients();
    }

    public Sw360Portlet(ThriftClients thriftClients) {
        this.thriftClients = thriftClients;
    }

    @Activate
    public void activate(Map<String, Object> properties) {
        Object portletName = properties.get("javax.portlet.name");
        log.info("Portlet [" + (portletName != null ? portletName : this.getClass().getSimpleName()) + "] has been ENABLED.");
    }

    @Modified
    public void modify(Map<String, Object> properties) {
        Object portletName = properties.get("javax.portlet.name");
        log.info("Portlet [" + (portletName != null ? portletName : this.getClass().getSimpleName()) + "] has been MODIFIED.");
    }

    @Deactivate
    public void deactivate(Map<String, Object> properties) {
        Object portletName = properties.get("javax.portlet.name");
        log.info("Portlet [" + (portletName != null ? portletName : this.getClass().getSimpleName()) + "] has been DISABLED.");
    }

    @Override
    public void doView(RenderRequest request, RenderResponse response) throws IOException, PortletException {
        super.doView(request, response);
    }

    protected void addBreadcrumbEntry(PortletRequest request, String name, PortletURL url) {
        PortalUtil.addPortletBreadcrumbEntry(PortalUtil.getOriginalServletRequest(PortalUtil.getHttpServletRequest(request)),
                name, url.toString());
    }

    protected void renderRequestSummary(PortletRequest request, ActionResponse response, RequestSummary requestSummary) {
        StringBuilder successMsg = new StringBuilder();
        successMsg.append(requestSummary.requestStatus.toString());

        if (requestSummary.isSetTotalAffectedElements() || requestSummary.isSetTotalElements() || requestSummary.isSetMessage())
            successMsg.append(": ");
        if (requestSummary.isSetTotalAffectedElements() && requestSummary.isSetTotalElements()) {
            successMsg.append(requestSummary.totalAffectedElements)
                    .append(" affected of ")
                    .append(requestSummary.totalElements)
                    .append(" total. ");
        } else if (requestSummary.isSetTotalElements()) {
            successMsg.append(requestSummary.totalElements)
                    .append(" total Elements. ");
        } else if (requestSummary.isSetTotalAffectedElements()) {
            successMsg.append(requestSummary.totalAffectedElements)
                    .append(" total affected elements. ");
        }

        if (requestSummary.isSetMessage())
            successMsg.append(requestSummary.getMessage());

        SessionMessages.add(request, "request_processed", successMsg.toString());
    }

    protected void renderRequestSummary(PortletRequest request, MimeResponse response, RequestSummary requestSummary) {
        JSONObject jsonObject = JSONFactoryUtil.createJSONObject();
        renderRequestSummary(request, response, requestSummary, jsonObject);
    }

    protected void renderRequestSummary(PortletRequest request, MimeResponse response, RequestSummary requestSummary, JSONObject jsonObject) {
        jsonObject.put(PortalConstants.RESULT, requestSummary.requestStatus.toString());
        if (requestSummary.isSetTotalAffectedElements())
            jsonObject.put("totalAffectedObjects", requestSummary.totalAffectedElements);
        if (requestSummary.isSetTotalElements())
            jsonObject.put("totalObjects", requestSummary.totalElements);
        if (requestSummary.isSetMessage())
            jsonObject.put("message", requestSummary.message);

        try {
            writeJSON(request, response, jsonObject);
        } catch (IOException e) {
            log.error("Problem rendering RequestStatus", e);
        }
    }

    protected void renderRequestStatus(PortletRequest request, MimeResponse response, RequestStatus requestStatus) {
        JSONObject jsonObject = JSONFactoryUtil.createJSONObject();
        jsonObject.put(PortalConstants.RESULT, requestStatus.toString());
        try {
            writeJSON(request, response, jsonObject);
        } catch (IOException e) {
            log.error("Problem rendering RequestStatus", e);
        }
    }

    protected void renderRemoveModerationRequestStatus(PortletRequest request, MimeResponse response, RemoveModeratorRequestStatus status) {
        JSONObject jsonObject = JSONFactoryUtil.createJSONObject();
        jsonObject.put(PortalConstants.RESULT, status.toString());
        try {
            writeJSON(request, response, jsonObject);
        } catch (IOException e) {
            log.error("Problem rendering RemoveModerationRequestStatus", e);
        }
    }

    protected void renderRequestStatus(PortletRequest request, ActionResponse response, RequestStatus requestStatus) {
        SessionMessages.add(request, "request_processed", requestStatus.toString());
    }

    protected void serveRequestStatus(PortletRequest request, ResourceResponse response, RequestStatus requestStatus, String message, Logger log) {
        if (requestStatus != RequestStatus.FAILURE) {
            request.setAttribute(PortalConstants.REQUEST_STATUS, requestStatus.toString());
            // We want failures to call the error method of the AJAX call so we do not send it
            renderRequestStatus(request, response, requestStatus);
        } else {
            log.error(message);
        }
    }


    public static boolean isUserAction(String action) {
        return action.startsWith(PortalConstants.USER_PREFIX);
    }

    public List<User> limitLengthOfUserList(List<User> userList) {
        if (userList.size() < MAX_LENGTH_USERS_IN_DISPLAY) {
            return userList;
        } else {
            return userList.subList(0, MAX_LENGTH_USERS_IN_DISPLAY);
        }
    }

    public void dealWithUserAction(ResourceRequest request, ResourceResponse response, String action) throws IOException, PortletException {
        if (PortalConstants.USER_SEARCH.equals(action)) {
            String searchText = request.getParameter(PortalConstants.WHAT);
            String how = request.getParameter(PortalConstants.HOW);

            Boolean multiUsers = false;
            if (!isNullOrEmpty(how)) {
                multiUsers = Boolean.parseBoolean(how);
            }

            try {
                List<User> users;
                UserService.Iface client = thriftClients.makeUserClient();
                if (isNullOrEmpty(searchText)) {
                    users = client.getAllUsers();
                } else {
                    users = client.searchUsers(searchText);
                }
                users = users.stream().filter(user -> !user.isDeactivated()).collect(Collectors.toList());
                List<User> truncatedUserList = limitLengthOfUserList(users);
                request.setAttribute(PortalConstants.USER_LIST, truncatedUserList);
                request.setAttribute(PortalConstants.HOW, multiUsers);
                request.setAttribute(PortalConstants.USER_SEARCH_GOT_TRUNCATED, isListTruncated(users, truncatedUserList));
                include("/html/utils/ajax/userListAjax.jsp", request, response, PortletRequest.RESOURCE_PHASE);
            } catch (TException e) {
                log.error("Error getting users", e);
            }

        }
    }

    public Boolean isListTruncated(List<User> fullList, List<User> truncatedList) {
        return ( fullList.size() > truncatedList.size() );
    }

    public void dealWithLicenseAction(ResourceRequest request, ResourceResponse response, String action) throws IOException, PortletException {
        if (PortalConstants.LICENSE_SEARCH.equals(action)) {
            final String searchText = request.getParameter(PortalConstants.WHAT);

            try {
                LicenseService.Iface client = thriftClients.makeLicenseClient();
                List<License> licenses = client.getLicenseSummary();


                licenses = FluentIterable.from(licenses).filter(new Predicate<License>() {
                    @Override
                    public boolean apply(License input) {
                        String fullname = input.getFullname();
                        String shortname = input.getShortname();
                        return (StringUtils.containsIgnoreCase(fullname, searchText)
                                || StringUtils.containsIgnoreCase(shortname, searchText));
                    }
                }).toList();


                request.setAttribute(PortalConstants.LICENSE_LIST, licenses);
                include("/html/utils/ajax/licenseListAjax.jsp", request, response, PortletRequest.RESOURCE_PHASE);
            } catch (TException e) {
                log.error("Error getting licenses", e);
            }

        }
    }

    public static boolean isLicenseAction(String action) {
        return action.startsWith(PortalConstants.LICENSE_PREFIX);
    }

    protected boolean isGenericAction(String action) {
        return isUserAction(action) || isLicenseAction(action);
    }

    protected void dealWithGenericAction(ResourceRequest request, ResourceResponse response, String action) throws IOException, PortletException {
        if (isUserAction(action)) {
            dealWithUserAction(request, response, action);
        } else if (isLicenseAction(action)) {
            dealWithLicenseAction(request, response, action);
        } else {
            throw new IllegalStateException("Cannot deal with action " + action + " as generic");
        }
    }

    public void setSessionMessage(PortletRequest request, RequestStatus requestStatus, String type, String verb, String name) throws PortletException {
        String statusMessage;
        if (isNullOrEmpty(name)) {
            name = "";
        }
        else {
            name = " " + name;
        }
        switch (requestStatus) {
        case SUCCESS:
            statusMessage = type + name + " " + verb + "d successfully!";
            SessionMessages.add(request, "request_processed", statusMessage);
            break;
        case SENT_TO_MODERATOR:
            statusMessage = "Moderation request was sent to " + verb + " the " + type + name + "!";
            SessionMessages.add(request, "request_processed", statusMessage);
            break;
        case FAILURE:
            setSW360SessionError(request, ErrorMessages.DOCUMENT_NOT_PROCESSED_SUCCESSFULLY);
            break;
        case IN_USE:
            if (type.equals("License")) {
                setSW360SessionError(request, ErrorMessages.LICENSE_USED_BY_RELEASE);
            } else {
                setSW360SessionError(request, ErrorMessages.DOCUMENT_USED_BY_PROJECT_OR_RELEASE);
            }
            break;
        case FAILED_SANITY_CHECK:
            setSW360SessionError(request, ErrorMessages.UPDATE_FAILED_SANITY_CHECK);
            break;
        case CLOSED_UPDATE_NOT_ALLOWED:
            setSW360SessionError(request, ErrorMessages.CLOSED_UPDATE_NOT_ALLOWED);
            break;
        case DUPLICATE:
        case DUPLICATE_ATTACHMENT:
        case NAMINGERROR:
            // just break to not throw an exception, error message has to be set by caller
            // because of type specific error messages
            break;
        default:
            throw new PortletException("Unknown request status");
        }
    }

    public void setSW360SessionError(PortletRequest request, String errorMessage) {
        if(ErrorMessages.allErrorMessages.contains(errorMessage)) {
            SessionErrors.add(request, errorMessage);
        } else {
            SessionErrors.add(request, ErrorMessages.DEFAULT_ERROR_MESSAGE);
        }
        SessionMessages.add(request, PortalUtil.getPortletId(request) +
                SessionMessages.KEY_SUFFIX_HIDE_DEFAULT_ERROR_MESSAGE);
        SessionMessages.add(request, PortalUtil.getPortletId(request) +
                SessionMessages.KEY_SUFFIX_HIDE_DEFAULT_SUCCESS_MESSAGE);
    }

    public void setSessionMessage(PortletRequest request, String successMsg) throws PortletException {
        SessionMessages.add(request, "request_processed", successMsg);
    }

    public void setSessionMessage(PortletRequest request, RequestStatus requestStatus, String type, String verb) throws PortletException {
        setSessionMessage(request, requestStatus, type, verb, null);
    }

    public void setSessionErrorBasedOnErrorCode(RenderRequest request, int errorCode) {
        if (errorCode == 404) {
            setSW360SessionError(request, ErrorMessages.ERROR_PROJECT_OR_DEPENDENCIES_NOT_FOUND);
        } else if (errorCode == 403) {
            setSW360SessionError(request, ErrorMessages.ERROR_PROJECT_OR_LINKEDPROJECT_NOT_ACCESSIBLE);
        } else {
            setSW360SessionError(request, ErrorMessages.ERROR_GETTING_PROJECT);
        }
    }

    protected void addEditDocumentMessage(RenderRequest request, Map<RequestedAction, Boolean> permissions, DocumentState documentState) {
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());

        List<String> msgs = new ArrayList<>();
        if (documentState.isSetModerationState()) {
            ModerationState moderationState = documentState.getModerationState();
            switch (moderationState) {
                case PENDING:
                    msgs.add(LanguageUtil.get(resourceBundle,"there.is.a.pending.moderation.request"));
                    break;
                case APPROVED:
                    break;
                case REJECTED:
                    break;
                case INPROGRESS:
                    msgs.add(LanguageUtil.get(resourceBundle,"there.is.a.moderation.request.in.progress"));
                    break;
            }
        }

        if (!permissions.get(RequestedAction.WRITE)) {
            msgs.add(LanguageUtil.get(resourceBundle,"you.will.create.a.moderation.request.if.you.update"));
        } else if (documentState.isIsOriginalDocument()) {
            msgs.add(LanguageUtil.get(resourceBundle,"you.are.editing.the.original.document"));
        }

        SessionMessages.add(request, "request_processed", Joiner.on(" ").join(msgs));
    }

    protected void serveReleaseSearch(ResourceRequest request, ResourceResponse response, String searchText) throws IOException, PortletException {
        final User user = UserCacheHolder.getUserFromRequest(request);
        List<Release> searchResult = serveReleaseListBySearchText(searchText, user);
        request.setAttribute(PortalConstants.RELEASE_SEARCH, searchResult);
        include("/html/utils/ajax/searchReleasesAjax.jsp", request, response, PortletRequest.RESOURCE_PHASE);
    }

    protected List<Release> serveReleaseListBySearchText(String searchText, User user) {
        try {
            ComponentService.Iface componentClient = thriftClients.makeComponentClient();
            if (isNullOrEmpty(searchText)) {
                return componentClient.getReleaseSummary(user);
            } else {
                List<Release> searchResult = componentClient.searchReleases(searchText);
                final VendorService.Iface vendorClient = thriftClients.makeVendorClient();
                List<String> vendorIds = vendorClient.searchVendorIds(searchText);
                if (vendorIds != null && vendorIds.size() > 0) {
                    searchResult.addAll(componentClient.getReleasesFromVendorIds(Sets.newHashSet(vendorIds)));
                }
                return searchResult;
            }
        } catch (TException e) {
            log.error("Error searching linked releases", e);
            return Collections.emptyList();
        }
    }

    public void backUpLicenses(ResourceRequest request, ResourceResponse response) throws IOException, TException {
        final LicenseService.Iface licenseClient = thriftClients.makeLicenseClient();
        Map<String, InputStream> fileNameToStreams = (new LicsExporter(licenseClient)).getFilenameToCSVStreams();

        final ByteArrayOutputStream outB = new ByteArrayOutputStream();
        final ZipOutputStream zipOutputStream = new ZipOutputStream(outB);

        for (Map.Entry<String, InputStream> entry : fileNameToStreams.entrySet()) {
            ZipTools.addToZip(zipOutputStream, entry.getKey(), entry.getValue());
        }

        zipOutputStream.flush();
        zipOutputStream.close(); // this closes outB

        final ByteArrayInputStream zipFile = new ByteArrayInputStream(outB.toByteArray());
        PortletResponseUtil.sendFile(request, response, "LicensesBackup.lics", zipFile, "application/zip");
    }

    public static TSerializer getJsonSerializer() {
        try {
            return new TSerializer(new TSimpleJSONProtocol.Factory());
        } catch (TTransportException e) {
            log.error("Error creating TSerializer " + e);
        }
        return null;
    }
}
