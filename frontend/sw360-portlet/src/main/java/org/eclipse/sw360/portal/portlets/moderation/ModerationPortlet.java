/*
 * Copyright Siemens AG, 2013-2018. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.portal.portlets.moderation;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.model.Organization;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.ResourceBundleUtil;

import org.eclipse.sw360.commonIO.SampleOptions;
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.common.SW360Constants;
import org.eclipse.sw360.datahandler.common.SW360Utils;
import org.eclipse.sw360.datahandler.common.ThriftEnumUtils;
import org.eclipse.sw360.datahandler.couchdb.lucene.LuceneAwareDatabaseConnector;
import org.eclipse.sw360.datahandler.permissions.PermissionUtils;
import org.eclipse.sw360.datahandler.thrift.AddDocumentRequestStatus;
import org.eclipse.sw360.datahandler.thrift.AddDocumentRequestSummary;
import org.eclipse.sw360.datahandler.thrift.DateRange;
import org.eclipse.sw360.datahandler.permissions.DocumentPermissions;
import org.eclipse.sw360.datahandler.thrift.ModerationState;
import org.eclipse.sw360.datahandler.thrift.PaginationData;
import org.eclipse.sw360.datahandler.thrift.RemoveModeratorRequestStatus;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.components.Component;
import org.eclipse.sw360.datahandler.thrift.components.ComponentService;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.components.ReleaseClearingStateSummary;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseInfoService;
import org.eclipse.sw360.datahandler.thrift.licenses.License;
import org.eclipse.sw360.datahandler.thrift.licenses.LicenseService;
import org.eclipse.sw360.datahandler.thrift.licenses.Obligation;
import org.eclipse.sw360.datahandler.thrift.licenses.ObligationLevel;
import org.eclipse.sw360.datahandler.thrift.moderation.ModerationRequest;
import org.eclipse.sw360.datahandler.thrift.moderation.ModerationService;
import org.eclipse.sw360.datahandler.thrift.projects.ClearingRequest;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectLink;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectService;
import org.eclipse.sw360.datahandler.thrift.users.RequestedAction;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.users.UserGroup;
import org.eclipse.sw360.datahandler.thrift.users.UserService;
import org.eclipse.sw360.datahandler.thrift.vendors.VendorService;
import org.eclipse.sw360.portal.common.ChangeLogsPortletUtils;
import org.eclipse.sw360.portal.common.ErrorMessages;
import org.eclipse.sw360.portal.common.JsonHelpers;
import org.eclipse.sw360.portal.common.PortalConstants;
import org.eclipse.sw360.portal.common.PortletUtils;
import org.eclipse.sw360.portal.common.ThriftJsonSerializer;
import org.eclipse.sw360.portal.common.UsedAsLiferayAction;
import org.eclipse.sw360.portal.common.datatables.PaginationParser;
import org.eclipse.sw360.portal.common.datatables.data.PaginationParameters;
import org.eclipse.sw360.portal.portlets.FossologyAwarePortlet;
import org.eclipse.sw360.portal.users.UserCacheHolder;
import org.eclipse.sw360.portal.users.UserUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.thrift.TEnum;
import org.apache.thrift.TException;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import javax.portlet.*;
import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static com.liferay.portal.kernel.json.JSONFactoryUtil.createJSONArray;
import static com.liferay.portal.kernel.json.JSONFactoryUtil.createJSONObject;
import static org.eclipse.sw360.datahandler.common.CommonUtils.isNotNullEmptyOrWhitespace;
import static org.eclipse.sw360.datahandler.common.CommonUtils.isNullEmptyOrWhitespace;
import static org.eclipse.sw360.datahandler.common.CommonUtils.nullToEmptySet;
import static org.eclipse.sw360.datahandler.permissions.PermissionUtils.makePermission;
import static org.eclipse.sw360.portal.common.PortalConstants.*;
import static org.eclipse.sw360.datahandler.common.WrappedException.wrapTException;

@org.osgi.service.component.annotations.Component(
    immediate = true,
    properties = {
        "/org/eclipse/sw360/portal/portlets/base.properties",
        "/org/eclipse/sw360/portal/portlets/default.properties"
    },
    property = {
        "javax.portlet.name=" + MODERATION_PORTLET_NAME,

        "javax.portlet.display-name=Moderations",
        "javax.portlet.info.short-title=Moderations",
        "javax.portlet.info.title=Moderations",
        "javax.portlet.resource-bundle=content.Language",
        "javax.portlet.init-param.view-template=/html/moderation/view.jsp",
    },
    service = Portlet.class,
    configurationPolicy = ConfigurationPolicy.REQUIRE
)
public class ModerationPortlet extends FossologyAwarePortlet {

    private static final Logger log = LogManager.getLogger(ModerationPortlet.class);
    private static final ImmutableList<ModerationRequest._Fields> MODERATION_FILTERED_FIELDS = ImmutableList.of(
            ModerationRequest._Fields.TIMESTAMP,
            ModerationRequest._Fields.COMPONENT_TYPE,
            ModerationRequest._Fields.DOCUMENT_NAME,
            ModerationRequest._Fields.REQUESTING_USER,
            ModerationRequest._Fields.REQUESTING_USER_DEPARTMENT,
            ModerationRequest._Fields.MODERATORS,
            ModerationRequest._Fields.MODERATION_STATE);

    private static final int MODERATION_NO_SORT = -1;

    @Override
    public void serveResource(ResourceRequest request, ResourceResponse response) throws IOException, PortletException {
        String action = request.getParameter(PortalConstants.ACTION);
        if (PortalConstants.ACTION_REMOVEME.equals(action)) {
            removeMeFromModerators(request, response);
        } else if (PortalConstants.DELETE_MODERATION_REQUEST.equals(action)) {
            serveDeleteModerationRequest(request, response);
        } else if (PortalConstants.ADD_COMMENT.equals(action)) {
            addCommentToClearingRequest(request, response);
        } else if (PortalConstants.LOAD_PROJECT_INFO.equals(action)) {
            getProjectDetailsForCR(request, response);
        } else if (isGenericAction(action)) {
            dealWithGenericAction(request, response, action);
        } else if (PortalConstants.LOAD_CHANGE_LOGS.equals(action) || PortalConstants.VIEW_CHANGE_LOGS.equals(action)) {
            ChangeLogsPortletUtils changeLogsPortletUtilsPortletUtils = PortletUtils
                    .getChangeLogsPortletUtils(thriftClients);
            JSONObject dataForChangeLogs = changeLogsPortletUtilsPortletUtils.serveResourceForChangeLogs(request,
                    response, action);
            writeJSON(request, response, dataForChangeLogs);
        } else if (PortalConstants.LOAD_OPEN_MODERATION_REQUEST.equals(action)) {
            serveModerationList(request, response, true);
        } else if (PortalConstants.LOAD_CLOSED_MODERATION_REQUEST.equals(action)) {
            serveModerationList(request, response, false);
        }
    }

    private void serveModerationList(ResourceRequest request, ResourceResponse response, boolean open) {
        HttpServletRequest originalServletRequest = PortalUtil
                .getOriginalServletRequest(PortalUtil.getHttpServletRequest(request));
        PaginationParameters paginationParameters = PaginationParser.parametersFrom(originalServletRequest);
        PortletUtils.handlePaginationSortOrder(request, paginationParameters, MODERATION_FILTERED_FIELDS,
                MODERATION_NO_SORT);
        PaginationData pageData = new PaginationData();
        pageData.setRowsPerPage(paginationParameters.getDisplayLength());
        pageData.setDisplayStart(paginationParameters.getDisplayStart());
        pageData.setAscending(paginationParameters.isAscending().get());
        if(paginationParameters.getSortingColumn().isPresent()) {
            int sortParam = paginationParameters.getSortingColumn().get();
            if(sortParam == 0 &&  Integer.valueOf(paginationParameters.getEcho()) == 1) {
                pageData.setSortColumnNumber(-1);
            } else {
                pageData.setSortColumnNumber(paginationParameters.getSortingColumn().get());
            }
        } else {
            pageData.setSortColumnNumber(-1);
        }
        Map<PaginationData, List<ModerationRequest>> modRequestsWithPageData = getFilteredModerationList(request,
                pageData, open);
        List<ModerationRequest> moderations = new ArrayList<>();
        PaginationData pgDt = new PaginationData();
        if (!CommonUtils.isNullOrEmptyMap(modRequestsWithPageData)) {
            moderations = modRequestsWithPageData.values().iterator().next();
            pgDt = modRequestsWithPageData.keySet().iterator().next();
        }
        JSONArray jsonOpenModerations = getModerationData(moderations, paginationParameters, request, open);
        JSONObject jsonResult = createJSONObject();
        final Map<String, Long> countByModerationState = getCountByModerationState(request);
        long openModRequestCount = countByModerationState.get("OPEN") == null ? 0
                : countByModerationState.get("OPEN");
        long closedModRequestCount = countByModerationState.get("CLOSED") == null ? 0
                : countByModerationState.get("CLOSED");
        Map<String, Set<String>> filterMap = getModerationFilterMap(request);
        long noOfRecords = filterMap.isEmpty() ? (open ? openModRequestCount : closedModRequestCount)
                : pgDt.getTotalRowCount();
        jsonResult.put(DATATABLE_RECORDS_TOTAL, noOfRecords);
        jsonResult.put(DATATABLE_RECORDS_FILTERED, noOfRecords);
        jsonResult.put(DATATABLE_DISPLAY_DATA, jsonOpenModerations);
        jsonResult.put(CLOSED_MODERATION_REQUESTS, closedModRequestCount);
        jsonResult.put(OPEN_MODERATION_REQUESTS, openModRequestCount);
        jsonResult.put(MODERATION_REQUESTING_USER_DEPARTMENTS, getRequestingUserDepts());
        try {
            writeJSON(request, response, jsonResult);
        } catch (IOException e) {
            log.error("Problem rendering list of open moderation", e);
        }
    }

    private Map<String, Long> getCountByModerationState(ResourceRequest request) {
        Map<String, Long> countByModerationState = Maps.newHashMap();
        ModerationService.Iface client = thriftClients.makeModerationClient();
        User user = UserCacheHolder.getUserFromRequest(request);
        try {
            countByModerationState = client.getCountByModerationState(user);
        } catch (TException e) {
            log.error("Error geeting moderation requests count", e);
        }
        return countByModerationState;
    }

    private Set<String> getRequestingUserDepts() {
        Set<String> requestingUserDepts = Sets.newHashSet();
        ModerationService.Iface client = thriftClients.makeModerationClient();
        try {
            requestingUserDepts = client.getRequestingUserDepts();
        } catch (TException e) {
            log.error("Error geeting requesting user departments", e);
        }
        return requestingUserDepts;
    }

    private JSONArray getModerationData(List<ModerationRequest> moderationList,
            PaginationParameters paginationParameters, ResourceRequest request, boolean open) {
        Map<String, Set<String>> filterMap = getModerationFilterMap(request);
        int count = PortletUtils.getProjectDataCount(paginationParameters, moderationList.size());
        User user = UserCacheHolder.getUserFromRequest(request);
        boolean isClearingAdmin = PermissionUtils.isUserAtLeast(UserGroup.CLEARING_ADMIN, user);
        JSONArray moderationRequestData = createJSONArray();
        final int start = filterMap.isEmpty() ? 0 : paginationParameters.getDisplayStart();
        for (int i = start; i < count; i++) {
            JSONObject jsonObject = JSONFactoryUtil.createJSONObject();
            ModerationRequest modreq = moderationList.get(i);
            jsonObject.put("id", modreq.getId());
            jsonObject.put("renderTimestamp", modreq.getTimestamp() + "");
            jsonObject.put("componentType", printEnumValueWithTooltip(request, modreq.getComponentType()));
            jsonObject.put("documentName", modreq.getDocumentName());
            jsonObject.put("requestingUser", UserUtils.displayUser(modreq.getRequestingUser(), null));
            jsonObject.put("requestingUserDepartment", modreq.getRequestingUserDepartment());
            jsonObject.put("moderators", displayUserCollection(modreq.getModerators()));
            jsonObject.put("moderationState", printEnumValueWithTooltip(request, modreq.getModerationState()));
            if (!open) {
                jsonObject.put("isClearingAdmin", isClearingAdmin);
            }
            moderationRequestData.put(jsonObject);
        }

        return moderationRequestData;
    }

    private String displayUserCollection(Set<String> values) {
        if (!CommonUtils.isNotEmpty(values)) {
            return "";
        }
        List<String> valueList = new ArrayList<String>(values);
        Collections.sort(valueList, String.CASE_INSENSITIVE_ORDER);
        List<String> resultList = new ArrayList<>();

        for (String email : valueList) {
            if (!Strings.isNullOrEmpty(email)) {
                resultList.add(UserUtils.displayUser(email, null));
            }
        }
        return CommonUtils.COMMA_JOINER.join(resultList);
    }

    private String printEnumValueWithTooltip(ResourceRequest request, TEnum value) {
        if (value == null) {
            return "";
        }
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(),
                getClass());
        return "<span class='" + PortalConstants.TOOLTIP_CLASS__CSS + " " + PortalConstants.TOOLTIP_CLASS__CSS + "-"
                + value.getClass().getSimpleName() + "-" + value.toString() + "' data-content='"
                + LanguageUtil.get(resourceBundle, value.getClass().getSimpleName() + "-" + value.toString()) + "'>"
                + LanguageUtil.get(resourceBundle, ThriftEnumUtils.enumToString(value).replace(' ', '.').toLowerCase())
                + "</span>";
    }

    private Map<PaginationData, List<ModerationRequest>> getFilteredModerationList(PortletRequest request, PaginationData pageData, boolean open) {
        ModerationService.Iface client = thriftClients.makeModerationClient();
        Map<String, Set<String>> filterMap = getModerationFilterMap(request);
        User user = UserCacheHolder.getUserFromRequest(request);
        Map<PaginationData, List<ModerationRequest>> moderationRequestsWithPageData = Maps.newHashMap();
        try {
            if (filterMap.isEmpty()) {
                moderationRequestsWithPageData = client.getRequestsByModeratorWithPagination(user, pageData, open);
            } else {
                List<ModerationRequest> moderations = client.refineSearch(null, filterMap);
                moderations = moderations.stream().filter(mr -> mr.getModerators().contains(user.getEmail())).collect(Collectors.toList());
                Map<Boolean, List<ModerationRequest>> partitionedModerationRequests = moderations.stream()
                        .collect(Collectors.groupingBy(ModerationPortletUtils::isOpenModerationRequest));
                List<ModerationRequest> requests = CommonUtils.nullToEmptyList(partitionedModerationRequests.get(open));
                moderationRequestsWithPageData.put(pageData.setTotalRowCount(requests.size()), requests);
            }
        } catch (TException e) {
            log.error("Could not fetch moderation requests from backend!", e);
        }
        return moderationRequestsWithPageData;
    }

    private void serveDeleteModerationRequest(ResourceRequest request, ResourceResponse response) throws IOException {
        RequestStatus requestStatus = ModerationPortletUtils.deleteModerationRequest(request, log);
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());
        serveRequestStatus(request, response, requestStatus, LanguageUtil.get(resourceBundle,"problem.removing.moderation.request"), log);
    }

    private void addCommentToClearingRequest(ResourceRequest request, ResourceResponse response) throws PortletException {
        RequestStatus requestStatus = ModerationPortletUtils.addCommentToClearingRequest(request, log);
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());
        serveRequestStatus(request, response, requestStatus, LanguageUtil.get(resourceBundle,"error.adding.comment.to.clearing.request"), log);
    }

    private void getProjectDetailsForCR(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
        User user = UserCacheHolder.getUserFromRequest(request);
        List<Project> projects;
        String ids[] = request.getParameterValues("projectIds[]");
        Boolean isOpenCr = Boolean.parseBoolean(request.getParameter("isOpenCr"));
        if (ids == null || ids.length == 0) {
            log.warn("Invalid project Ids!");
            JSONArray jsonResponse = createJSONArray();
            writeJSON(request, response, jsonResponse);
        } else {
            ProjectService.Iface client = thriftClients.makeProjectClient();
            try {
                projects = client.getProjectsById(Arrays.asList(ids), user);
            } catch (TException e) {
                log.error("Could not fetch project summary from backend!", e);
                projects = Collections.emptyList();
            }

            if (isOpenCr) {
                projects = getWithFilledClearingStateSummary(client, projects, user);
            }

            JSONArray jsonResponse = createJSONArray();
            ThriftJsonSerializer thriftJsonSerializer = new ThriftJsonSerializer();
            for (Project project : projects) {
                try {
                    JSONObject row = createJSONObject();
                    row.put("id", project.getId());
                    row.put("crId", project.getClearingRequestId());
                    row.put("name", SW360Utils.printName(project));
                    if (isOpenCr && null != project.getReleaseClearingStateSummary()) {
                        row.put("clearing", JsonHelpers.toJson(project.getReleaseClearingStateSummary(), thriftJsonSerializer));
                    }
                    String babl = CommonUtils.nullToEmptyMap(project.getAdditionalData()).get("BA BL");
                    row.put("bu", CommonUtils.isNotNullEmptyOrWhitespace(babl) ? babl : CommonUtils.nullToEmptyString(project.getBusinessUnit()));
                    jsonResponse.put(row);
                } catch (JSONException e) {
                    log.error("cannot serialize json", e);
                }
            }
            writeJSON(request, response, jsonResponse);
        }
    }

    private List<Project> getWithFilledClearingStateSummary(ProjectService.Iface client, List<Project> projects, User user) {
        try {
            return client.fillClearingStateSummary(projects, user);
        } catch (TException e) {
            log.error("Could not get summary of release clearing states for projects!", e);
            return projects;
        }
    }

    private void removeMeFromModerators(ResourceRequest request, ResourceResponse response){
        final User user = UserCacheHolder.getUserFromRequest(request);
        final String id = request.getParameter(MODERATION_ID);
        ModerationService.Iface client = thriftClients.makeModerationClient();
        RemoveModeratorRequestStatus status = null;
        try {
            status = client.removeUserFromAssignees(id, user);
            request.setAttribute(PortalConstants.REQUEST_STATUS, status);
        } catch(TException e) {
            log.error("Error in Moderation ", e);
        }
        renderRemoveModerationRequestStatus(request, response, status);
    }

    @Override
    public void doView(RenderRequest request, RenderResponse response) throws IOException, PortletException {
        final String pageName = request.getParameter(PAGENAME);
        if (PAGENAME_EDIT.equals(pageName)) {
            renderEditView(request, response);
        } else if (PAGENAME_ACTION.equals(pageName)) {
            renderActionView(request, response);
        } else if (PAGENAME_EDIT_CLEARING_REQUEST.equals(pageName) || PAGENAME_DETAIL_CLEARING_REQUEST.equals(pageName)) {
            renderClearingRequest(request, response, pageName);
            include("/html/moderation/clearing/clearingRequest.jsp", request, response);
        } else {
            renderStandardView(request, response);
        }
    }

    private void renderActionView(RenderRequest request, RenderResponse response) throws IOException, PortletException {
        final User user = UserCacheHolder.getUserFromRequest(request);
        final String id = request.getParameter(MODERATION_ID);
        String sessionMessage;
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());
        if (id != null) {
            try {
                ModerationService.Iface client = thriftClients.makeModerationClient();
                ModerationRequest moderationRequest = client.getModerationRequestById(id);
                String action = request.getParameter(ACTION);
                String encodedModerationComment = request.getParameter(MODERATION_DECISION_COMMENT);
                String moderationComment = "";
                if (encodedModerationComment != null) {
                    moderationComment = new String(Base64.getDecoder().decode(encodedModerationComment));
                }

                if (ACTION_CANCEL.equals(action)) {
                    client.cancelInProgress(id);

                    sessionMessage = LanguageUtil.get(resourceBundle,"you.have.cancelled.working.on.the.previous.moderation.request");
                } else if (ACTION_DECLINE.equals(action)) {
                    declineModerationRequest(user, moderationRequest, request);

                    client.refuseRequest(id, moderationComment, user.getEmail());
                    sessionMessage = LanguageUtil.get(resourceBundle,"you.have.declined.the.previous.moderation.request");
                } else if (ACTION_ACCEPT.equals(action)) {
                    String requestingUserEmail = moderationRequest.getRequestingUser();
                    User requestingUser = UserCacheHolder.getUserFromEmail(requestingUserEmail);
                    acceptModerationRequest(user, requestingUser, moderationRequest, request);

                    client.acceptRequest(moderationRequest, moderationComment, user.getEmail());
                    sessionMessage = LanguageUtil.get(resourceBundle,"you.have.accepted.the.previous.moderation.request");
                } else if (ACTION_POSTPONE.equals(action)) {
                    // keep me assigned but do it later... so nothing to be done here, just update the comment message
                    moderationRequest.setCommentDecisionModerator(moderationComment);
                    client.updateModerationRequest(moderationRequest);
                    sessionMessage = LanguageUtil.get(resourceBundle,"you.have.postponed.the.previous.moderation.request");
                } else if (ACTION_RENDER_NEXT_AFTER_UNSUBSCRIBE.equals(action)) {
                    sessionMessage = LanguageUtil.get(resourceBundle,"you.are.removed.from.the.list.of.moderators.for.the.previous.moderation.request");
                } else {
                   throw new PortletException("Unknown action");
                }

                //! Actions are processed now we go and render the next one
                renderNextModeration(request, response, user, sessionMessage, client, moderationRequest);
            } catch (TException e) {
                log.error("Error in Moderation ", e);
            }
        }
    }

    private void renderClearingRequest(RenderRequest request, RenderResponse response, String pageName) throws PortletException {
        final String clearingId = request.getParameter(CLEARING_REQUEST_ID);
        final User user = UserCacheHolder.getUserFromRequest(request);

        if (CommonUtils.isNullEmptyOrWhitespace(clearingId)) {
            throw new PortletException("Clearing request ID not set!");
        }

        try {
            ModerationService.Iface client = thriftClients.makeModerationClient();
            ClearingRequest clearingRequest;
            if (PAGENAME_EDIT_CLEARING_REQUEST.equals(pageName)) {
                clearingRequest = client.getClearingRequestByIdForEdit(clearingId, user);
            } else {
                clearingRequest = client.getClearingRequestById(clearingId, user);
            }
            clearingRequest.setComments(Lists.reverse(CommonUtils.nullToEmptyList(clearingRequest.getComments())));
            boolean isPrimaryRoleOfUserAtLeastClearingExpert = PermissionUtils.isUserAtLeast(UserGroup.CLEARING_EXPERT,
                    user);
            request.setAttribute(CLEARING_REQUEST, clearingRequest);
            request.setAttribute(WRITE_ACCESS_USER, false);
            request.setAttribute(IS_CLEARING_EXPERT, isPrimaryRoleOfUserAtLeastClearingExpert);
            Integer approvedReleaseCount = 0;
            if (CommonUtils.isNotNullEmptyOrWhitespace(clearingRequest.getProjectId()) ) {
                ProjectService.Iface projectClient = thriftClients.makeProjectClient();
                Project project = projectClient.getProjectById(clearingRequest.getProjectId(), UserCacheHolder.getUserFromRequest(request));
                DocumentPermissions<Project> projectPermission = makePermission(project, user);
                ImmutableSet<UserGroup> clearingExpertRoles = ImmutableSet.of(UserGroup.CLEARING_EXPERT);
                ImmutableSet<UserGroup> adminRoles = ImmutableSet.of(UserGroup.ADMIN, UserGroup.SW360_ADMIN);
                request.setAttribute(IS_CLEARING_EXPERT,
                        isPrimaryRoleOfUserAtLeastClearingExpert
                                || projectPermission.isUserOfOwnGroupHasRole(clearingExpertRoles, UserGroup.CLEARING_EXPERT)
                                || projectPermission.isUserOfOwnGroupHasRole(adminRoles, UserGroup.ADMIN));
                request.setAttribute(WRITE_ACCESS_USER, projectPermission.isActionAllowed(RequestedAction.WRITE));
                String babl = CommonUtils.nullToEmptyMap(project.getAdditionalData()).get("BA BL");
                if (CommonUtils.isNotNullEmptyOrWhitespace(babl)) {
                    project.setBusinessUnit(babl);
                }
                request.setAttribute(PROJECT, project);
                List<Project> projects = getWithFilledClearingStateSummary(projectClient, Lists.newArrayList(project), user);
                Project projWithCsSummary = projects.get(0);
                if (null != projWithCsSummary && null != projWithCsSummary.getReleaseClearingStateSummary()) {
                    ReleaseClearingStateSummary summary = projWithCsSummary.getReleaseClearingStateSummary();
                    approvedReleaseCount = summary.getApproved() + summary.getReportAvailable();
                }
            }
            if (clearingRequest.getTimestampOfDecision() > 1) {
                Integer criticalCount = client.getCriticalClearingRequestCount();
                request.setAttribute(CRITICAL_CR_COUNT, criticalCount);
            }
            String dateLimit = CommonUtils.nullToEmptyString(ModerationPortletUtils.loadPreferredClearingDateLimit(request, user));
            request.setAttribute(CUSTOM_FIELD_PREFERRED_CLEARING_DATE_LIMIT, dateLimit);
            request.setAttribute(APPROVED_RELEASE_COUNT, approvedReleaseCount);
            addClearingBreadcrumb(request, response, clearingId);
        } catch (TException e) {
            log.error("Error fetching clearing request from backend!", e);
            setSW360SessionError(request, ErrorMessages.ERROR_GETTING_CLEARING_REQUEST);
        }
    }

    @UsedAsLiferayAction
    public void updateClearingRequest(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        AddDocumentRequestSummary requestSummary = ModerationPortletUtils.updateClearingRequest(request, log);
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());

        if (AddDocumentRequestStatus.SUCCESS.equals(requestSummary.getRequestStatus())) {
            String successMsg = new StringBuilder(LanguageUtil.get(resourceBundle, "clearing.request")).append(" ")
                    .append(requestSummary.getId()).append(" ").append(LanguageUtil.get(resourceBundle, "updated.successfully")).toString();
            SessionMessages.add(request, "request_processed", successMsg);
            response.setRenderParameter(CLEARING_REQUEST_ID, request.getParameter(CLEARING_REQUEST_ID));
            response.setRenderParameter(PAGENAME, PAGENAME_DETAIL_CLEARING_REQUEST);
        } else if (AddDocumentRequestStatus.FAILURE.equals(requestSummary.getRequestStatus())) {
            String errorMsg = LanguageUtil.get(resourceBundle, requestSummary.getMessage().replace(' ', '.').toLowerCase());
            setSW360SessionError(request, errorMsg);
            response.setRenderParameter(CLEARING_REQUEST_ID, request.getParameter(CLEARING_REQUEST_ID));
            response.setRenderParameter(PAGENAME, PAGENAME_DETAIL_CLEARING_REQUEST);
        }
    }

    private void declineModerationRequest(User user, ModerationRequest moderationRequest, RenderRequest request) throws TException {
        switch (moderationRequest.getDocumentType()) {
            case USER:
                UserUtils.deleteLiferayUser(request, moderationRequest.getUser());
                UserService.Iface userClient = thriftClients.makeUserClient();
                userClient.deleteUser(UserCacheHolder.getRefreshedUserFromEmail(moderationRequest.getUser().getEmail()), user);
                break;
            default:
                break;
        }
    }
    private void acceptModerationRequest(User user, User requestingUser, ModerationRequest moderationRequest, RenderRequest request) throws TException {
        switch (moderationRequest.getDocumentType()) {
            case COMPONENT: {
                ComponentService.Iface componentClient = thriftClients.makeComponentClient();
                if (moderationRequest.isRequestDocumentDelete()) {
                    componentClient.deleteComponent(moderationRequest.getDocumentId(), user);
                } else {
                    componentClient.updateComponentFromModerationRequest(
                            moderationRequest.getComponentAdditions(),
                            moderationRequest.getComponentDeletions(),
                            user);
                }
            }
            break;
            case RELEASE: {
                ComponentService.Iface componentClient = thriftClients.makeComponentClient();
                if (moderationRequest.isRequestDocumentDelete()) {
                    componentClient.deleteRelease(moderationRequest.getDocumentId(), user);
                } else {
                    componentClient.updateReleaseFromModerationRequest(
                            moderationRequest.getReleaseAdditions(),
                            moderationRequest.getReleaseDeletions(),
                            user);
                }
            }
            break;
            case PROJECT: {
                ProjectService.Iface projectClient = thriftClients.makeProjectClient();
                if (moderationRequest.isRequestDocumentDelete()) {
                    projectClient.deleteProject(moderationRequest.getDocumentId(), user);
                } else {
                    projectClient.updateProjectFromModerationRequest(
                            moderationRequest.getProjectAdditions(),
                            moderationRequest.getProjectDeletions(),
                            user);
                }
            }
            break;
            case LICENSE: {
                LicenseService.Iface licenseClient = thriftClients.makeLicenseClient();
                    licenseClient.updateLicenseFromModerationRequest(
                            moderationRequest.getLicenseAdditions(),
                            moderationRequest.getLicenseDeletions(),
                            user,
                            requestingUser);
            }
            break;
            case USER: {
                UserUtils.activateLiferayUser(request, moderationRequest.getUser());
            }
            break;
        }
    }

    private void renderNextModeration(RenderRequest request, RenderResponse response, final User user, String sessionMessage, ModerationService.Iface client, ModerationRequest moderationRequest) throws IOException, PortletException, TException {
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());
        if (ACTION_CANCEL.equals(request.getParameter(ACTION))) {
            SessionMessages.add(request, "request_processed", sessionMessage);
            renderStandardView(request, response);
            return;
        }

        List<ModerationRequest> requestsByModerator = client.getRequestsByModerator(user);
        List<ModerationRequest> openModerationRequests = requestsByModerator
                .stream()
                .filter(input-> ModerationState.PENDING.equals(input.getModerationState()))
                .collect(Collectors.toList());

        Collections.sort(openModerationRequests, compareByTimeStamp());

        int nextIndex = openModerationRequests.indexOf(moderationRequest) + 1;
        if (nextIndex < openModerationRequests.size()) {
            renderEditViewForId(request, response, openModerationRequests.get(nextIndex).getId());
        } else {
            List<ModerationRequest> requestsInProgressAndAssignedToMe = requestsByModerator
                    .stream()
                    .filter(input-> ModerationState.INPROGRESS.equals(input.getModerationState()) && user.getEmail().equals(input.getReviewer()))
                    .collect(Collectors.toList());

            if (requestsInProgressAndAssignedToMe.size()>0) {
                sessionMessage += LanguageUtil.get(resourceBundle,"you.have.returned.to.your.first.open.request");
                SessionMessages.add(request, "request_processed", sessionMessage);
                renderEditViewForId(request, response, Collections.min(requestsInProgressAndAssignedToMe, compareByTimeStamp()).getId());
            } else {
                sessionMessage += LanguageUtil.get(resourceBundle,"you.have.no.open.requests");
                SessionMessages.add(request, "request_processed", sessionMessage);
                renderStandardView(request, response);
            }
        }
    }

    @NotNull
    private Comparator<ModerationRequest> compareByTimeStamp() {
        return new Comparator<ModerationRequest>() {
            @Override
            public int compare(ModerationRequest o1, ModerationRequest o2) {
                return Long.compare(o1.getTimestamp(), o2.getTimestamp());
            }
        };
    }

    private void addModerationBreadcrumb(RenderRequest request, RenderResponse response, ModerationRequest moderationRequest) {
        PortletURL baseUrl = response.createRenderURL();
        baseUrl.setParameter(PAGENAME, PAGENAME_EDIT);
        baseUrl.setParameter(MODERATION_ID, moderationRequest.getId());

        addBreadcrumbEntry(request, moderationRequest.getDocumentName(), baseUrl);
    }

    private void addClearingBreadcrumb(RenderRequest request, RenderResponse response, String Id) {
        PortletURL baseUrl = response.createRenderURL();
        baseUrl.setParameter(PAGENAME, PAGENAME_EDIT_CLEARING_REQUEST);
        baseUrl.setParameter(CLEARING_REQUEST_ID, Id);

        addBreadcrumbEntry(request, Id, baseUrl);
    }

    public void renderStandardView(RenderRequest request, RenderResponse response) throws IOException, PortletException {
        User user = UserCacheHolder.getUserFromRequest(request);

        HttpServletRequest httpServletRequest = PortalUtil.getOriginalServletRequest(PortalUtil.getHttpServletRequest(request));
        String selectedTab = httpServletRequest.getParameter(SELECTED_TAB);

        List<ClearingRequest> openClearingRequests = null;
        List<ClearingRequest> closedClearingRequests = null;
        ModerationService.Iface client = thriftClients.makeModerationClient();

        try {
            Set<ClearingRequest> clearingRequestsSet = client.getMyClearingRequests(user);
            clearingRequestsSet.addAll(client.getClearingRequestsByBU(user.getDepartment()));

            if (!CommonUtils.isNullOrEmptyMap(user.getSecondaryDepartmentsAndRoles())) {
                user.getSecondaryDepartmentsAndRoles().keySet().stream().forEach(department -> wrapTException(() -> {
                    clearingRequestsSet.addAll(client.getClearingRequestsByBU(department));
                }));
            }

            Map<Boolean, List<ClearingRequest>> partitionedClearingRequests = clearingRequestsSet
                    .stream().collect(Collectors.groupingBy(ModerationPortletUtils::isClosedClearingRequest));
            closedClearingRequests = partitionedClearingRequests.get(true);
            openClearingRequests = partitionedClearingRequests.get(false);
        } catch (TException e) {
            log.error("Could not fetch clearing requests from backend!", e);
        }

        request.setAttribute(CLEARING_REQUESTS, CommonUtils.nullToEmptyList(openClearingRequests));
        request.setAttribute(CLOSED_CLEARING_REQUESTS, CommonUtils.nullToEmptyList(closedClearingRequests));
        request.setAttribute(IS_CLEARING_EXPERT, PermissionUtils.isUserAtLeast(UserGroup.CLEARING_EXPERT, user));
        PortletUtils.getBaBlSelection(request, user);
        List<Organization> organizations = UserUtils.getOrganizations(request);
        request.setAttribute(PortalConstants.ORGANIZATIONS, organizations);
        if (CommonUtils.isNotNullEmptyOrWhitespace(selectedTab)) {
            request.setAttribute(SELECTED_TAB, selectedTab);
        }
        for (ModerationRequest._Fields moderationFilteredField : MODERATION_FILTERED_FIELDS) {
            request.setAttribute(moderationFilteredField.getFieldName(),
                    nullToEmpty(request.getParameter(moderationFilteredField.toString())));
        }
        request.setAttribute(PortalConstants.DATE_RANGE, nullToEmpty(request.getParameter(PortalConstants.DATE_RANGE)));
        request.setAttribute(PortalConstants.END_DATE, nullToEmpty(request.getParameter(PortalConstants.END_DATE)));
        super.doView(request, response);
    }

    public void renderEditView(RenderRequest request, RenderResponse response) throws IOException, PortletException {
        String id = request.getParameter(MODERATION_ID);
        try {
            renderEditViewForId(request, response, id);
        } catch (TException e) {
            log.error("Thrift error", e);
        }
    }

    private void renderEditViewForId(RenderRequest request, RenderResponse response, String id) throws IOException, PortletException, TException {
        if (id != null) {
            ModerationRequest moderationRequest = null;
            User user = UserCacheHolder.getUserFromRequest(request);
            ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());
            try {

                ModerationService.Iface client = thriftClients.makeModerationClient();
                moderationRequest = client.getModerationRequestById(id);
                boolean actionsAllowed = moderationRequest.getModerators().contains(user.getEmail()) && ModerationPortletUtils.isOpenModerationRequest(moderationRequest);
                request.setAttribute(PortalConstants.MODERATION_ACTIONS_ALLOWED, actionsAllowed);
                if(actionsAllowed) {
                    SessionMessages.add(request, "request_processed", LanguageUtil.get(resourceBundle,"you.have.assigned.yourself.to.this.moderation.request"));
                    client.setInProgress(id, user);
                }
                request.setAttribute(PortalConstants.MODERATION_REQUEST, moderationRequest);
                addModerationBreadcrumb(request, response, moderationRequest);

            } catch (TException e) {
                log.error("Error fetching moderation  details from backend", e);
            }

            if (moderationRequest != null) {
                switch (moderationRequest.getDocumentType()) {
                    case COMPONENT:
                        renderComponentModeration(request, response, moderationRequest, user);
                        break;
                    case RELEASE:
                        VendorService.Iface vendorClient = thriftClients.makeVendorClient();
                        Release additions = moderationRequest.getReleaseAdditions();
                        if(additions.isSetVendorId()){
                            additions.setVendor(vendorClient.getByID(additions.getVendorId()));
                        }
                        Release deletions = moderationRequest.getReleaseDeletions();
                        if(deletions.isSetVendorId()){
                            deletions.setVendor(vendorClient.getByID(deletions.getVendorId()));
                        }
                        renderReleaseModeration(request, response, moderationRequest, user);
                        break;
                    case PROJECT:
                        renderProjectModeration(request, response, moderationRequest, user);
                        break;
                    case LICENSE:
                        renderLicenseModeration(request, response, moderationRequest, user);
                        break;
                    case USER:
                        renderUserModeration(request, response, moderationRequest, user);
                        break;
                }
            }
        }
    }

    public void renderComponentModeration(RenderRequest request, RenderResponse response, ModerationRequest moderationRequest, User user) throws IOException, PortletException, TException {

        final boolean requestDocumentDelete = moderationRequest.isRequestDocumentDelete();
        Boolean is_used = false;

        Component actual_component = null;

        try {
            ComponentService.Iface client = thriftClients.makeComponentClient();
            actual_component = client.getComponentById(moderationRequest.getDocumentId(), user);
            is_used = client.componentIsUsed(actual_component.getId());
        } catch (TException e) {
            log.error("Could not retrieve component", e);
        }

        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());
        if (actual_component == null) {
            renderNextModeration(request, response, user, LanguageUtil.get(resourceBundle,"eignored.unretrievable.target"), thriftClients.makeModerationClient(), moderationRequest);
            return;
        }

        if (refuseToDeleteUsedDocument(request, response, moderationRequest, user, requestDocumentDelete, is_used))
            return;

        prepareComponent(request, user, actual_component);
        request.setAttribute(PortalConstants.ACTUAL_COMPONENT, actual_component);
        if (moderationRequest.isRequestDocumentDelete()) {
            include("/html/moderation/components/delete.jsp", request, response);
        } else {
            include("/html/moderation/components/merge.jsp", request, response);
        }
    }

    private void prepareComponent(RenderRequest request, User user, Component actualComponent) {
        List<Release> releases;

        releases = CommonUtils.nullToEmptyList(actualComponent.getReleases());
        Set<String> releaseIds = SW360Utils.getReleaseIds(releases);

        Set<Project> usingProjects = null;
        int allUsingProjectsCount = 0;

        if (releaseIds != null && releaseIds.size() > 0) {
            try {
                ProjectService.Iface projectClient = thriftClients.makeProjectClient();
                usingProjects = projectClient.searchByReleaseIds(releaseIds, user);
                allUsingProjectsCount = projectClient.getCountByReleaseIds(releaseIds);
            } catch (TException e) {
                log.error("Could not retrieve using projects", e);
            }
        }
        request.setAttribute(DOCUMENT_TYPE, SW360Constants.TYPE_COMPONENT);
        setAttachmentsInRequest(request, actualComponent);
        request.setAttribute(USING_PROJECTS, nullToEmptySet(usingProjects));
        request.setAttribute(ALL_USING_PROJECTS_COUNT, allUsingProjectsCount);
    }

    public void renderReleaseModeration(RenderRequest request, RenderResponse response, ModerationRequest moderationRequest, User user) throws IOException, PortletException, TException {
        Release actual_release = null;

        final boolean requestDocumentDelete = moderationRequest.isRequestDocumentDelete();
        Boolean is_used = false;

        try {
            ComponentService.Iface client = thriftClients.makeComponentClient();
            actual_release = client.getReleaseById(moderationRequest.getDocumentId(), user);
            is_used = client.releaseIsUsed(actual_release.getId());
        } catch (TException e) {
            log.error("Could not retrieve release", e);
        }

        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());
        if (actual_release == null) {
            renderNextModeration(request, response, user, LanguageUtil.get(resourceBundle,"ignored.unretrievable.target"), thriftClients.makeModerationClient(), moderationRequest);
            return;
        }

        if (refuseToDeleteUsedDocument(request, response, moderationRequest, user, requestDocumentDelete, is_used))
            return;

        prepareRelease(request, user, actual_release);
        request.setAttribute(PortalConstants.ACTUAL_RELEASE, actual_release);
        if (requestDocumentDelete) {
            include("/html/moderation/releases/delete.jsp", request, response);
        } else {
            include("/html/moderation/releases/merge.jsp", request, response);
        }
    }

    private boolean refuseToDeleteUsedDocument(RenderRequest request, RenderResponse response, ModerationRequest moderationRequest, User user, boolean requestDocumentDelete, Boolean is_used) throws TException, IOException, PortletException {
        if (requestDocumentDelete && is_used) {
            ModerationService.Iface client = thriftClients.makeModerationClient();
            ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());
            client.refuseRequest(moderationRequest.getId(), LanguageUtil.get(resourceBundle,"you.cannot.delete.a.document.still.used.by.others"), user.getEmail());
            renderNextModeration(request, response, user, LanguageUtil.get(resourceBundle,"ignored.delete.of.used.target"), client, moderationRequest);
            return true;
        }
        return false;
    }

    private void prepareRelease(RenderRequest request, User user, Release actualRelease) {

        String actualReleaseId = actualRelease.getId();
        request.setAttribute(DOCUMENT_ID, actualReleaseId);
        request.setAttribute(DOCUMENT_TYPE, SW360Constants.TYPE_RELEASE);
        setAttachmentsInRequest(request, actualRelease);
        try {
            ProjectService.Iface projectClient = thriftClients.makeProjectClient();
            Set<Project> usingProjects = projectClient.searchByReleaseId(actualReleaseId, user);
            request.setAttribute(USING_PROJECTS, nullToEmptySet(usingProjects));
            int allUsingProjectsCount = projectClient.getCountByReleaseIds(Collections.singleton(actualReleaseId));
            request.setAttribute(ALL_USING_PROJECTS_COUNT, allUsingProjectsCount);
            putDirectlyLinkedReleaseRelationsInRequest(request, actualRelease);
        } catch (TException e) {
            log.error("Could not retrieve using projects", e);
        }

        try {
            request.setAttribute(COMPONENT, thriftClients.makeComponentClient().getComponentById(actualRelease.getComponentId(), user));
        } catch (TException e) {
            log.error("Could not fetch component from Backend ", e);
        }

    }

    public void renderProjectModeration(RenderRequest request, RenderResponse response, ModerationRequest moderationRequest, User user) throws IOException, PortletException, TException {
        final boolean requestDocumentDelete = moderationRequest.isRequestDocumentDelete();
        Boolean is_used = false;
        Project actual_project = null;
        try {
            ProjectService.Iface client = thriftClients.makeProjectClient();
            actual_project = client.getProjectById(moderationRequest.getDocumentId(), user);
            actual_project = client.fillClearingStateSummary(Collections.singletonList(actual_project), user).get(0);
            is_used = client.projectIsUsed(actual_project.getId());
            request.setAttribute(PortalConstants.ACTUAL_PROJECT, actual_project);
            request.setAttribute(PortalConstants.DEFAULT_LICENSE_INFO_HEADER_TEXT, getDefaultLicenseInfoHeaderText());
        } catch (TException e) {
            log.error("Could not retrieve project", e);
        }

        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());
        if (actual_project == null) {
            renderNextModeration(request, response, user, LanguageUtil.get(resourceBundle,"ignored.unretrievable.target"), thriftClients.makeModerationClient(), moderationRequest);
            return;
        }

        if (refuseToDeleteUsedDocument(request, response, moderationRequest, user, requestDocumentDelete, is_used))
            return;

        prepareProject(request, user, actual_project);
        if (moderationRequest.isRequestDocumentDelete()) {
            include("/html/moderation/projects/delete.jsp", request, response);
        } else {
            //updateProjectFromModerationRequest and add updated project to request.
            include("/html/moderation/projects/merge.jsp", request, response);
        }
    }

    private void prepareProject(RenderRequest request, User user, Project actual_project) {
        try {
            ProjectService.Iface client = thriftClients.makeProjectClient();
            List<ProjectLink> mappedProjectLinks = createLinkedProjects(actual_project, user);
            request.setAttribute(PROJECT_LIST, mappedProjectLinks);
            putDirectlyLinkedReleasesInRequest(request, actual_project);
            Set<Project> usingProjects = client.searchLinkingProjects(actual_project.getId(), user);
            request.setAttribute(USING_PROJECTS, usingProjects);
            int allUsingProjectsCount = client.getCountByProjectId(actual_project.getId());
            request.setAttribute(ALL_USING_PROJECTS_COUNT, allUsingProjectsCount);
            putReleasesAndProjectIntoRequest(request, actual_project.getId(), user);
            request.setAttribute(DOCUMENT_TYPE, SW360Constants.TYPE_PROJECT);
            setAttachmentsInRequest(request, actual_project);
            ModerationService.Iface modClient = thriftClients.makeModerationClient();
            Integer criticalCount = modClient.getCriticalClearingRequestCount();
            request.setAttribute(CRITICAL_CR_COUNT, criticalCount);
        } catch (TException e) {
            log.error("Error fetching project from backend!", e);
        }
    }

    public void renderLicenseModeration(RenderRequest request, RenderResponse response, ModerationRequest moderationRequest, User user) throws IOException, PortletException, TException {
        License actual_license = null;
        User requestingUser = UserCacheHolder.getUserFromEmail(moderationRequest.getRequestingUser());
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());
        try {
            LicenseService.Iface client = thriftClients.makeLicenseClient();
            actual_license = client.getByID(moderationRequest.getDocumentId(),requestingUser.getDepartment());
            request.setAttribute(KEY_LICENSE_DETAIL, actual_license);
            List<Obligation> obligations = client.getObligations().stream()
                    .filter(Objects::nonNull)
                    .filter(Obligation::isSetObligationLevel)
                    .filter(obl -> obl.getObligationLevel().equals(ObligationLevel.LICENSE_OBLIGATION))
                    .collect(Collectors.toList());
            request.setAttribute(KEY_OBLIGATION_LIST, obligations);
        } catch (TException e) {
            log.error("Could not retrieve license", e);
        }

        if (actual_license == null) {
            renderNextModeration(request, response, user, LanguageUtil.get(resourceBundle,"ignored.unretrievable.target"), thriftClients.makeModerationClient(), moderationRequest);
            return;
        }

        include("/html/moderation/licenses/merge.jsp", request, response);
    }

    public void  renderUserModeration(RenderRequest request, RenderResponse response, ModerationRequest moderationRequest, User user) throws IOException, PortletException, TException {
        User changedUser = null;
        try {
            UserService.Iface client = thriftClients.makeUserClient();
            changedUser = client.getByEmail(moderationRequest.getUser().getEmail());
            request.setAttribute(PortalConstants.USER, changedUser);
        } catch (TException e) {
            log.error("Could not retrieve user", e);
        }

        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());
        if (changedUser == null) {
            renderNextModeration(request, response, user, LanguageUtil.get(resourceBundle,"ignored.unretrievable.target"), thriftClients.makeModerationClient(), moderationRequest);
            return;
        }

        List<Organization> organizations = UserUtils.getOrganizations(request);
        request.setAttribute(PortalConstants.ORGANIZATIONS, organizations);

        include("/html/moderation/users/merge.jsp", request, response);
    }

    @UsedAsLiferayAction
    public void applyFilters(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        for (ModerationRequest._Fields moderationFilteredField : MODERATION_FILTERED_FIELDS) {
            response.setRenderParameter(moderationFilteredField.toString(),
                    nullToEmpty(request.getParameter(moderationFilteredField.toString())));
        }
        response.setRenderParameter(PortalConstants.DATE_RANGE, nullToEmpty(request.getParameter(PortalConstants.DATE_RANGE)));
        response.setRenderParameter(PortalConstants.END_DATE, nullToEmpty(request.getParameter(PortalConstants.END_DATE)));
    }

    private Map<String, Set<String>> getModerationFilterMap(PortletRequest request) {
        Map<String, Set<String>> filterMap = new HashMap<>();
        for (ModerationRequest._Fields filteredField : MODERATION_FILTERED_FIELDS) {
            String parameter = request.getParameter(filteredField.toString());
            if (!isNullOrEmpty(parameter) && !((filteredField.equals(ModerationRequest._Fields.COMPONENT_TYPE)
                    || filteredField.equals(ModerationRequest._Fields.MODERATION_STATE))
                    && parameter.equals(PortalConstants.NO_FILTER))) {
                if (filteredField.equals(ModerationRequest._Fields.TIMESTAMP) && isNotNullEmptyOrWhitespace(request.getParameter(PortalConstants.DATE_RANGE))) {
                    Date date = new Date();
                    String upperLimit = new SimpleDateFormat(SampleOptions.DATE_OPTION).format(date);
                    String dateRange = request.getParameter(PortalConstants.DATE_RANGE);
                    String query = new StringBuilder("[%s ").append(PortalConstants.TO).append(" %s]").toString();
                    DateRange range = ThriftEnumUtils.stringToEnum(dateRange, DateRange.class);
                    switch (range) {
                    case EQUAL:
                        break;
                    case LESS_THAN_OR_EQUAL_TO:
                        parameter = String.format(query, PortalConstants.EPOCH_DATE, parameter);
                        break;
                    case GREATER_THAN_OR_EQUAL_TO:
                        parameter = String.format(query, parameter, upperLimit);
                        break;
                    case BETWEEN:
                        String endDate = request.getParameter(PortalConstants.END_DATE);
                        if (isNullEmptyOrWhitespace(endDate)) {
                            endDate = upperLimit;
                        }
                        parameter = String.format(query, parameter, endDate);
                        break;
                    }
                }
                Set<String> values = CommonUtils.splitToSet(parameter);
                if (filteredField.equals(ModerationRequest._Fields.DOCUMENT_NAME)
                        || filteredField.equals(ModerationRequest._Fields.REQUESTING_USER)
                        || filteredField.equals(ModerationRequest._Fields.REQUESTING_USER_DEPARTMENT)) {
                    values = values.stream().map(LuceneAwareDatabaseConnector::prepareWildcardQuery)
                            .collect(Collectors.toSet());
                }
                filterMap.put(filteredField.getFieldName(), values);
            }
        }
        return filterMap;
    }

    private UnsupportedOperationException unsupportedActionException() {
        throw new UnsupportedOperationException("cannot call this action on the moderation portlet");
    }

    @Override
    protected void dealWithFossologyAction(ResourceRequest request, ResourceResponse response, String action) throws IOException, PortletException {
        throw unsupportedActionException();
    }

    @Override
    protected Set<Attachment> getAttachments(String documentId, String documentType, User user) {
        throw unsupportedActionException();
    }

    private String getDefaultLicenseInfoHeaderText() {
        final LicenseInfoService.Iface licenseInfoClient = thriftClients.makeLicenseInfoClient();
        try {
            String defaultLicenseInfoHeaderText = licenseInfoClient.getDefaultLicenseInfoHeaderText();
            return defaultLicenseInfoHeaderText;
        } catch (TException e) {
            log.error("Could not load default license info header text from backend.", e);
            return "";
        }
    }
}
