/*
 * Copyright Siemens AG, 2013-2019. Part of the SW360 Portal Project.
 * With contributions by Bosch Software Innovations GmbH, 2016.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.portal.portlets.components;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.portlet.LiferayPortletURL;
import com.liferay.portal.kernel.portlet.PortletResponseUtil;
import com.liferay.portal.kernel.portlet.PortletURLFactoryUtil;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.ResourceBundleUtil;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.kernel.language.LanguageUtil;

import org.eclipse.sw360.commonIO.SampleOptions;
import org.eclipse.sw360.datahandler.common.*;
import org.eclipse.sw360.datahandler.common.WrappedException.WrappedTException;
import org.eclipse.sw360.datahandler.couchdb.lucene.LuceneAwareDatabaseConnector;
import org.eclipse.sw360.datahandler.permissions.PermissionUtils;
import org.eclipse.sw360.datahandler.thrift.*;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentType;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentService;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentUsage;
import org.eclipse.sw360.datahandler.thrift.components.*;
import org.eclipse.sw360.datahandler.thrift.cvesearch.CveSearchService;
import org.eclipse.sw360.datahandler.thrift.cvesearch.VulnerabilityUpdateStatus;
import org.eclipse.sw360.datahandler.thrift.fossology.FossologyService;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseInfoParsingResult;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseInfoService;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseNameWithText;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectService;
import org.eclipse.sw360.datahandler.thrift.users.RequestedAction;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.users.UserGroup;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.eclipse.sw360.datahandler.thrift.vendors.VendorService;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.*;
import org.eclipse.sw360.exporter.ComponentExporter;
import org.eclipse.sw360.portal.common.*;
import org.eclipse.sw360.portal.common.datatables.PaginationParser;
import org.eclipse.sw360.portal.common.datatables.data.PaginationParameters;
import org.eclipse.sw360.portal.portlets.FossologyAwarePortlet;
import org.eclipse.sw360.portal.users.LifeRayUserSession;
import org.eclipse.sw360.portal.users.UserCacheHolder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.thrift.TEnum;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TSimpleJSONProtocol;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.apache.commons.lang.StringUtils;

import javax.portlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static com.liferay.portal.kernel.json.JSONFactoryUtil.createJSONArray;
import static com.liferay.portal.kernel.json.JSONFactoryUtil.createJSONObject;
import static java.lang.Math.min;
import static org.eclipse.sw360.datahandler.common.CommonUtils.*;
import static org.eclipse.sw360.datahandler.common.SW360Constants.CONTENT_TYPE_OPENXML_SPREADSHEET;
import static org.eclipse.sw360.datahandler.common.SW360Utils.printName;
import static org.eclipse.sw360.datahandler.common.WrappedException.wrapException;
import static org.eclipse.sw360.datahandler.common.WrappedException.wrapTException;
import static org.eclipse.sw360.portal.common.PortalConstants.*;
import static org.eclipse.sw360.portal.common.PortletUtils.getVerificationState;

@org.osgi.service.component.annotations.Component(
    immediate = true,
    properties = {
            "/org/eclipse/sw360/portal/portlets/base.properties",
            "/org/eclipse/sw360/portal/portlets/default.properties"
    },
    property = {
        "javax.portlet.name=" + COMPONENT_PORTLET_NAME,

        "javax.portlet.display-name=Components",
        "javax.portlet.info.short-title=Components",
        "javax.portlet.info.title=Components",
        "javax.portlet.resource-bundle=content.Language",
        "javax.portlet.init-param.view-template=/html/components/view.jsp",
    },
    service = Portlet.class,
    configurationPolicy = ConfigurationPolicy.REQUIRE
)
public class ComponentPortlet extends FossologyAwarePortlet {

    private static final String QUERY_PARAMS_FOSSOLOGY = "?mod=showjobs&upload=";

    private static final Logger log = LogManager.getLogger(ComponentPortlet.class);

    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final TSerializer JSON_THRIFT_SERIALIZER = new TSerializer(new TSimpleJSONProtocol.Factory());

    // Component view datatables, index of columns
    private static final int COMPONENT_NO_SORT = -1;
    private static final int COMPONENT_DT_ROW_VENDOR = 0;
    private static final int COMPONENT_DT_ROW_NAME = 1;
    private static final int COMPONENT_DT_ROW_MAIN_LICENSES = 2;
    private static final int COMPONENT_DT_ROW_TYPE = 3;
    private static final int COMPONENT_DT_ROW_ACTION = 4;

    private static final int MAX_RESULT_LIMIT_CHECK_COMPONENT_NAME = 15;

    private static final String CYCLIC_LINKED_RELEASE = "Release cannot be created/updated due to cyclic linked release present. Cyclic Hierarchy : ";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private boolean typeIsComponent(String documentType) {
        return SW360Constants.TYPE_COMPONENT.equals(documentType);
    }

    @Override
    protected Set<Attachment> getAttachments(String documentId, String documentType, User user) {

        try {
            ComponentService.Iface client = thriftClients.makeComponentClient();
            if (typeIsComponent(documentType)) {
                Component component = client.getComponentById(documentId, user);
                return nullToEmptySet(component.getAttachments());
            } else {
                Release release = client.getReleaseById(documentId, user);
                return nullToEmptySet(release.getAttachments());
            }
        } catch (TException e) {
            log.error("Could not get " + documentType + " attachments for " + documentId, e);
        }
        return Collections.emptySet();
    }

    private static final ImmutableList<Component._Fields> componentFilteredFields = ImmutableList.of(
            Component._Fields.NAME,
            Component._Fields.CATEGORIES,
            Component._Fields.LANGUAGES,
            Component._Fields.SOFTWARE_PLATFORMS,
            Component._Fields.OPERATING_SYSTEMS,
            Component._Fields.VENDOR_NAMES,
            Component._Fields.COMPONENT_TYPE,
            Component._Fields.MAIN_LICENSE_IDS,
            Component._Fields.CREATED_BY,
            Component._Fields.CREATED_ON);

    private static final String CONFIG_KEY_URL = "url";

    //! Serve resource and helpers
    @Override
    public void serveResource(ResourceRequest request, ResourceResponse response) throws IOException, PortletException {
        String action = request.getParameter(PortalConstants.ACTION);

        if (VIEW_VENDOR.equals(action)) {
            serveViewVendor(request, response);
        } else if (ADD_VENDOR.equals(action)) {
            serveAddVendor(request, response);
        } else if (CHECK_COMPONENT_NAME.equals(action)) {
            serveCheckComponentName(request, response);
        } else if (DELETE_COMPONENT.equals(action)) {
            serveDeleteComponent(request, response);
        } else if (DELETE_RELEASE.equals(action)) {
            serveDeleteRelease(request, response);
        } else if (LOAD_COMPONENT_LIST.equals(action)) {
            serveComponentList(request, response);
        } else if (SUBSCRIBE.equals(action)) {
            serveSubscribe(request, response);
        } else if (SUBSCRIBE_RELEASE.equals(action)) {
            serveSubscribeRelease(request, response);
        } else if (UNSUBSCRIBE.equals(action)) {
            serveUnsubscribe(request, response);
        } else if (UNSUBSCRIBE_RELEASE.equals(action)) {
            serveUnsubscribeRelease(request, response);
        } else if (PortalConstants.VIEW_LINKED_RELEASES.equals(action)) {
            serveLinkedReleases(request, response);
        } else if (PortalConstants.PROJECT_SEARCH.equals(action)) {
            serveProjectSearch(request, response);
        } else if (PortalConstants.UPDATE_VULNERABILITIES_RELEASE.equals(action)){
            updateVulnerabilitiesRelease(request,response);
        } else if (PortalConstants.UPDATE_VULNERABILITIES_COMPONENT.equals(action)){
            updateVulnerabilitiesComponent(request,response);
        } else if (PortalConstants.UPDATE_ALL_VULNERABILITIES.equals(action)) {
            updateAllVulnerabilities(request, response);
        } else if (PortalConstants.UPDATE_VULNERABILITY_VERIFICATION.equals(action)){
            updateVulnerabilityVerification(request, response);
        } else if (PortalConstants.EXPORT_TO_EXCEL.equals(action)) {
            exportExcel(request, response);
        } else if (PortalConstants.RELEASE_LINK_TO_PROJECT.equals(action)) {
            linkReleaseToProject(request, response);
        } else if (PortalConstants.LOAD_SPDX_LICENSE_INFO.equals(action)) {
            loadSpdxLicenseInfo(request, response);
        } else if (PortalConstants.WRITE_SPDX_LICENSE_INFO_INTO_RELEASE.equals(action)) {
            writeSpdxLicenseInfoIntoRelease(request, response);
        } else if (PortalConstants.IMPORT_BOM.equals(action)) {
            importBom(request, response);
        } else if (isGenericAction(action)) {
            dealWithGenericAction(request, response, action);
        } else if (PortalConstants.LOAD_CHANGE_LOGS.equals(action) || PortalConstants.VIEW_CHANGE_LOGS.equals(action)) {
            ChangeLogsPortletUtils changeLogsPortletUtilsPortletUtils = PortletUtils
                    .getChangeLogsPortletUtils(thriftClients);
            JSONObject dataForChangeLogs = changeLogsPortletUtilsPortletUtils.serveResourceForChangeLogs(request,
                    response, action);
            writeJSON(request, response, dataForChangeLogs);
        }
    }

    private void importBom(ResourceRequest request, ResourceResponse response) {
        final ComponentService.Iface componentClient = thriftClients.makeComponentClient();
        User user = UserCacheHolder.getUserFromRequest(request);
        String attachmentContentId = request.getParameter(ATTACHMENT_CONTENT_ID);

        try {
            final RequestSummary requestSummary = componentClient.importBomFromAttachmentContent(user, attachmentContentId);

            LiferayPortletURL releaseUrl = createDetailLinkTemplate(request);
            releaseUrl.setParameter(PortalConstants.PAGENAME, PortalConstants.PAGENAME_RELEASE_DETAIL);
            releaseUrl.setParameter(RELEASE_ID, requestSummary.getMessage());
            JSONObject jsonObject = JSONFactoryUtil.createJSONObject();
            jsonObject.put("redirectUrl", releaseUrl.toString());

            renderRequestSummary(request, response, requestSummary, jsonObject);
        } catch (TException e) {
            log.error("Failed to import BOM.", e);
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE, Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        }
    }


    @Override
    protected void dealWithFossologyAction(ResourceRequest request, ResourceResponse response, String action) throws IOException, PortletException {
        if (PortalConstants.FOSSOLOGY_ACTION_STATUS.equals(action)) {
            serveFossologyStatus(request, response);
        } else if (PortalConstants.FOSSOLOGY_ACTION_PROCESS.equals(action)) {
            serveFossologyProcess(request, response);
        } else if (PortalConstants.FOSSOLOGY_ACTION_OUTDATED.equals(action)) {
            serveFossologyOutdated(request, response);
        } else if (PortalConstants.FOSSOLOGY_ACTION_RELOAD_REPORT.equals(action)) {
            serveFossologyReloadReport(request, response);
        } else {
            log.error("Unknown action parameter <" + action + ">, so no action has been performed!");
            renderRequestStatus(request, response, RequestStatus.FAILURE);
        }
    }

    private void serveViewVendor(ResourceRequest request, ResourceResponse response) throws IOException, PortletException {
        String what = request.getParameter(PortalConstants.WHAT);
        String where = request.getParameter(PortalConstants.WHERE);

        if ("vendorSearch".equals(what)) {
            renderVendorSearch(request, response, where);
        }
    }

    private void renderVendorSearch(ResourceRequest request, ResourceResponse response, String searchText) throws IOException, PortletException {
        List<Vendor> vendors = null;
        try {
            VendorService.Iface client = thriftClients.makeVendorClient();
            if (isNullOrEmpty(searchText)) {
                vendors = client.getAllVendors();
            } else {
                vendors = client.searchVendors(searchText);
            }
        } catch (TException e) {
            log.error("Error searching vendors", e);
        }

        request.setAttribute("vendorsSearch", nullToEmptyList(vendors));
        include("/html/components/ajax/vendorSearch.jsp", request, response, PortletRequest.RESOURCE_PHASE);
    }

    private void serveAddVendor(ResourceRequest request, ResourceResponse response) throws IOException, PortletException {
        final Vendor vendor = new Vendor();
        ComponentPortletUtils.updateVendorFromRequest(request, vendor);

        try {
            VendorService.Iface client = thriftClients.makeVendorClient();
            String vendorId = client.addVendor(vendor);
            JSONObject jsonObject = JSONFactoryUtil.createJSONObject();
            jsonObject.put("id", vendorId);
            try {
                writeJSON(request, response, jsonObject);
            } catch (IOException e) {
                log.error("Problem rendering VendorId", e);
            }
        } catch (TException e) {
            log.error("Error adding vendor", e);
        }
    }

    private void serveCheckComponentName(ResourceRequest request, ResourceResponse response) throws IOException {
        List<Component> resultComponents = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        String cName = request.getParameter(PortalConstants.COMPONENT_NAME);

        if (cName != null && !cName.isEmpty()) {
            List<Component> similarComponents = new ArrayList<>();
            Map<String, Set<String>> filterMap = new HashMap<>();

            // to find tomcat even on a cName of tomcat-apr, split the cName of special
            // tokens:
            // \W = a non word character, so not in [a-zA-Z_0-9]
            Set<String> splitCName = Sets.newHashSet(cName.split("\\W"));
            // to find tomcat even on a cName of tomca, add * to the search term
            Set<String> splitExtendedCName = splitCName.stream().map(LuceneAwareDatabaseConnector::prepareWildcardQuery).collect(Collectors.toSet());

            try {
                // thrift service does not support OR queries at the moment, so we have to query
                // him twice
                ComponentService.Iface cClient = thriftClients.makeComponentClient();

                // first search for names
                filterMap.put(Component._Fields.NAME.getFieldName(), splitExtendedCName);
                similarComponents.addAll(cClient.refineSearch(null, filterMap)
                        .stream().limit(MAX_RESULT_LIMIT_CHECK_COMPONENT_NAME)
                        .collect(Collectors.toList()));

                // second search for vendors
                filterMap.remove(Component._Fields.NAME.getFieldName());
                filterMap.put(Component._Fields.VENDOR_NAMES.getFieldName(), splitExtendedCName);
                similarComponents.addAll(cClient.refineSearch(null, filterMap)
                        .stream().limit(MAX_RESULT_LIMIT_CHECK_COMPONENT_NAME)
                        .collect(Collectors.toList()));

                // remove duplicates and sort alphabetically
                resultComponents = similarComponents.stream().distinct().sorted(Comparator.comparing(Component::getName)).collect(Collectors.toList());
            } catch (TException e) {
                log.error("Error getting similar components from backend", e);
                errors.add(e.getMessage());
            }
        }

        respondSimilarComponentsResponseJson(request, response, resultComponents, errors);
    }

    private void respondSimilarComponentsResponseJson(ResourceRequest request, ResourceResponse response,
            List<Component> similarComponents, List<String> errors) throws IOException {
        response.setContentType(ContentTypes.APPLICATION_JSON);

        JsonGenerator jsonGenerator = JSON_FACTORY.createGenerator(response.getWriter());
        jsonGenerator.writeStartObject();

        // adding common title and description
        jsonGenerator.writeStringField("title",
                "Duplicate Component Check");
        jsonGenerator.writeStringField("description",
                "To avoid duplicate components, check these similar ones! Does yours already exist?");

        // adding errors or empty array if none occured
        jsonGenerator.writeFieldName("errors");
        jsonGenerator.writeStartArray();
        errors.stream().forEach(e -> {
            try {
                jsonGenerator.writeString(e);
            } catch (IOException e1) {
                log.error("Exception while writing errors list to simililar components json", e1);
            }
        });
        jsonGenerator.writeEndArray();

        // adding components or empty array if there are none
        LiferayPortletURL componentUrl = createDetailLinkTemplate(request);
        jsonGenerator.writeFieldName("links");
        jsonGenerator.writeStartArray();
        similarComponents.stream().forEach(c -> {
            componentUrl.setParameter(PortalConstants.COMPONENT_ID, c.getId());

            try {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("target", componentUrl.toString());
                jsonGenerator.writeStringField("text", c.getName());
                jsonGenerator.writeEndObject();
            } catch (IOException e1) {
                log.error("Exception while writing components list to simililar components json", e1);
            }
        });
        jsonGenerator.writeEndArray();

        jsonGenerator.writeEndObject();
        jsonGenerator.close();
    }

    private LiferayPortletURL createDetailLinkTemplate(PortletRequest request) {
        String portletId = (String) request.getAttribute(WebKeys.PORTLET_ID);
        ThemeDisplay tD = (ThemeDisplay) request.getAttribute(WebKeys.THEME_DISPLAY);
        long plid = tD.getPlid();

        LiferayPortletURL componentUrl = PortletURLFactoryUtil.create(request, portletId, plid,
                PortletRequest.RENDER_PHASE);
        componentUrl.setParameter(PortalConstants.PAGENAME, PortalConstants.PAGENAME_DETAIL);

        return componentUrl;
    }

    private void serveDeleteComponent(ResourceRequest request, ResourceResponse response) throws IOException {
        RequestStatus requestStatus = ComponentPortletUtils.deleteComponent(request, log);
        serveRequestStatus(request, response, requestStatus, "Problem removing component", log);

    }

    private void serveDeleteRelease(PortletRequest request, ResourceResponse response) throws IOException {
        final RequestStatus requestStatus = ComponentPortletUtils.deleteRelease(request, log);
        serveRequestStatus(request, response, requestStatus, "Problem removing release", log);
    }

    private void exportExcel(ResourceRequest request, ResourceResponse response) {
        final User user = UserCacheHolder.getUserFromRequest(request);

        try {
            boolean extendedByReleases = Boolean.valueOf(request.getParameter(PortalConstants.EXTENDED_EXCEL_EXPORT));
            List<Component> components = getFilteredComponentList(request);
            ComponentExporter exporter = new ComponentExporter(thriftClients.makeComponentClient(), components, user,
                    extendedByReleases);
            String filename = String.format("components-%s.xlsx", SW360Utils.getCreatedOn());
            PortletResponseUtil.sendFile(request, response, filename, exporter.makeExcelExport(components),
                    CONTENT_TYPE_OPENXML_SPREADSHEET);
        } catch (IOException | SW360Exception e) {
            log.error("An error occurred while generating the Excel export", e);
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE,
                    Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        }
    }

    private void serveSubscribe(ResourceRequest request, ResourceResponse response) {
        final RequestStatus requestStatus = ComponentPortletUtils.subscribeComponent(request, log);
        serveRequestStatus(request, response, requestStatus, "Problem subscribing component", log);
    }

    private void serveSubscribeRelease(ResourceRequest request, ResourceResponse response) {
        final RequestStatus requestStatus = ComponentPortletUtils.subscribeRelease(request, log);
        serveRequestStatus(request, response, requestStatus, "Problem subscribing release", log);
    }

    private void serveUnsubscribe(ResourceRequest request, ResourceResponse response) {
        final RequestStatus requestStatus = ComponentPortletUtils.unsubscribeComponent(request, log);
        serveRequestStatus(request, response, requestStatus, "Problem unsubscribing component", log);
    }

    private void serveUnsubscribeRelease(ResourceRequest request, ResourceResponse response) {
        final RequestStatus requestStatus = ComponentPortletUtils.unsubscribeRelease(request, log);
        serveRequestStatus(request, response, requestStatus, "Problem unsubscribing release", log);
    }

    private void serveLinkedReleases(ResourceRequest request, ResourceResponse response) throws IOException, PortletException {
        String what = request.getParameter(PortalConstants.WHAT);

        if (PortalConstants.LIST_NEW_LINKED_RELEASES.equals(what)) {
            String[] where = request.getParameterValues(PortalConstants.WHERE_ARRAY);
            serveNewTableRowLinkedRelease(request, response, where);
        } else if (PortalConstants.RELEASE_SEARCH.equals(what)) {
            String where = request.getParameter(PortalConstants.WHERE);
            serveReleaseSearchResults(request, response, where);
        }
    }

    private void serveProjectSearch(ResourceRequest request, ResourceResponse response) throws PortletException {
        ProjectSearchUtils utils = new ProjectSearchUtils(thriftClients);
        User user = UserCacheHolder.getUserFromRequest(request);
        String searchTerm = request.getParameter(PortalConstants.WHERE);

        List<Project> projects = utils.searchProjects(user, searchTerm);
        try {
            String serializedProjects = projects.stream()
                    .map(project -> wrapTException(() -> JSON_THRIFT_SERIALIZER.toString(project)))
                    .collect(Collectors.joining(",", "[", "]"));

            writeJSON(request, response, serializedProjects);
        } catch (IOException | WrappedTException exception) {
            log.error("cannot retrieve information about projects.", exception.getCause());
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE, "500");
        }
    }

    private void serveNewTableRowLinkedRelease(ResourceRequest request, ResourceResponse response, String[] linkedIds) throws IOException, PortletException {
        final User user = UserCacheHolder.getUserFromRequest(request);

        List<ReleaseLink> linkedReleases = new ArrayList<>();
        try {
            ComponentService.Iface client = thriftClients.makeComponentClient();
            for (Release release : client.getReleasesById(new HashSet<>(Arrays.asList(linkedIds)), user)) {
                final Vendor vendor = release.getVendor();

                final String vendorName = vendor != null ? vendor.getShortname() : "";
                ReleaseLink linkedRelease = new ReleaseLink(release.getId(), vendorName, release.getName(), release.getVersion(), SW360Utils.printFullname(release), !nullToEmptyMap(release.getReleaseIdToRelationship()).isEmpty());
                linkedRelease.setReleaseRelationship(ReleaseRelationship.CONTAINED);
                linkedReleases.add(linkedRelease);
            }
        } catch (TException e) {
            log.error("Error getting releases!", e);
            throw new PortletException("cannot get releases " + Arrays.toString(linkedIds), e);
        }

        request.setAttribute(RELEASE_LIST, linkedReleases);

        include("/html/utils/ajax/linkedReleasesRelationAjax.jsp", request, response, PortletRequest.RESOURCE_PHASE);
    }

    private void serveReleaseSearchResults(ResourceRequest request, ResourceResponse response, String searchText) throws IOException, PortletException {
        serveReleaseSearch(request, response, searchText);
    }

    private void writeJsonResponse(String json, ResourceResponse response) throws IOException {
        byte[] bytes = json.getBytes(Charset.forName("UTF-8"));
        response.setContentType(ContentTypes.APPLICATION_JSON);
        response.setContentLength(bytes.length);
        OutputStream outputStream = response.getPortletOutputStream();
        outputStream.write(bytes, 0, bytes.length);
        response.flushBuffer();
    }

    private void loadSpdxLicenseInfo(ResourceRequest request, ResourceResponse response) {
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());

        User user = UserCacheHolder.getUserFromRequest(request);
        String releaseId = request.getParameter(PortalConstants.RELEASE_ID);
        String attachmentContentId = request.getParameter(PortalConstants.ATTACHMENT_ID);
        String attachmentName = request.getParameter(PortalConstants.ATTACHMENT_NAME);
        boolean includeConcludedLicense = new Boolean(request.getParameter(PortalConstants.INCLUDE_CONCLUDED_LICENSE));

        ComponentService.Iface componentClient = thriftClients.makeComponentClient();
        LicenseInfoService.Iface licenseInfoClient = thriftClients.makeLicenseInfoClient();

        Set<String> concludedLicenseIds = new HashSet<>();
        Set<String> mainLicenseNames = new HashSet<String>();
        Set<String> otherLicenseNames = new HashSet<String>();
        try {
            Release release = componentClient.getReleaseById(releaseId, user);
            List<LicenseInfoParsingResult> licenseInfoResult = licenseInfoClient.getLicenseInfoForAttachment(release,
                    attachmentContentId, includeConcludedLicense, user);
            List<LicenseNameWithText> licenseWithTexts = licenseInfoResult.stream()
                    .flatMap(result -> result.getLicenseInfo().getLicenseNamesWithTexts().stream())
                    .filter(license -> !license.getLicenseName().equalsIgnoreCase(SW360Constants.LICENSE_NAME_UNKNOWN)
                            && !license.getLicenseName().equalsIgnoreCase(SW360Constants.NA)
                            && !license.getLicenseName().equalsIgnoreCase(SW360Constants.NO_ASSERTION)) // exclude unknown, n/a and noassertion
                    .collect(Collectors.toList());
            if (attachmentName.endsWith(".rdf")) {
                concludedLicenseIds = licenseInfoResult.stream()
                        .flatMap(singleResult -> singleResult.getLicenseInfo().getConcludedLicenseIds().stream())
                        .collect(Collectors.toSet());
                otherLicenseNames = licenseWithTexts.stream().map(LicenseNameWithText::getLicenseName).collect(Collectors.toSet());
                otherLicenseNames.removeAll(concludedLicenseIds);
            } else if (attachmentName.endsWith(".xml")) {
                mainLicenseNames = licenseWithTexts.stream().filter(license -> license.getType().equals(LICENSE_TYPE_GLOBAL)).map(LicenseNameWithText::getLicenseName).collect(Collectors.toSet());
                otherLicenseNames = licenseWithTexts.stream().filter(license -> !license.getType().equals(LICENSE_TYPE_GLOBAL)).map(LicenseNameWithText::getLicenseName).collect(Collectors.toSet());
            }

        } catch (TException e) {
            log.error("Cannot retrieve license information for attachment id " + attachmentContentId + " in release "
                    + releaseId + ".", e);
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE, "500");
        }

        try {
            JsonGenerator jsonGenerator = JSON_FACTORY.createGenerator(response.getWriter());
            jsonGenerator.writeStartObject();
            if (concludedLicenseIds.size() > 0) {
                jsonGenerator.writeStringField(LICENSE_PREFIX, LanguageUtil.get(resourceBundle,"concluded.license.ids"));
                jsonGenerator.writeArrayFieldStart(LICENSE_IDS);
                concludedLicenseIds.forEach(licenseId -> wrapException(() -> { jsonGenerator.writeString(licenseId); }));
                jsonGenerator.writeEndArray();
            } else if (CommonUtils.isNotEmpty(mainLicenseNames)) {
                jsonGenerator.writeStringField(LICENSE_PREFIX, LanguageUtil.get(resourceBundle,"main.license.id"));
                jsonGenerator.writeArrayFieldStart(LICENSE_IDS);
                mainLicenseNames.forEach(licenseId -> wrapException(() -> { jsonGenerator.writeString(licenseId); }));
                jsonGenerator.writeEndArray();
            }
            jsonGenerator.writeStringField("otherLicense", LanguageUtil.get(resourceBundle,"other.license.id"));
            jsonGenerator.writeArrayFieldStart("otherLicenseIds");
            otherLicenseNames.forEach(licenseId -> wrapException(() -> { jsonGenerator.writeString(licenseId); }));
            jsonGenerator.writeEndArray();

            jsonGenerator.writeEndObject();

            jsonGenerator.close();
        } catch (IOException | RuntimeException e) {
            log.error("Cannot write JSON response for attachment id " + attachmentContentId + " in release " + releaseId
                    + ".", e);
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE, "500");
        }
    }

    private void writeSpdxLicenseInfoIntoRelease(ResourceRequest request, ResourceResponse response) {
        User user = UserCacheHolder.getUserFromRequest(request);
        String releaseId = request.getParameter(PortalConstants.RELEASE_ID);
        ComponentService.Iface componentClient = thriftClients.makeComponentClient();

        RequestStatus result = null;
        try {
            Release release = componentClient.getReleaseById(releaseId, user);
            JsonNode input = OBJECT_MAPPER.readValue(request.getParameter(SPDX_LICENSE_INFO), JsonNode.class);
            JsonNode licenesIdsNode = input.get(LICENSE_IDS);
            if (null != licenesIdsNode) {
                if (licenesIdsNode.isArray()) {
                    for (JsonNode objNode : licenesIdsNode) {
                        release.addToMainLicenseIds(objNode.asText());
                    }
                } else {
                    release.addToMainLicenseIds(licenesIdsNode.asText());
                }
            }
            licenesIdsNode = input.get("otherLicenseIds");
            if (null != licenesIdsNode) {
                if (licenesIdsNode.isArray()) {
                    for (JsonNode objNode : licenesIdsNode) {
                        release.addToOtherLicenseIds(objNode.asText());
                    }
                } else {
                    release.addToOtherLicenseIds(licenesIdsNode.asText());
                }
            }
            result = componentClient.updateRelease(release, user);
        } catch (TException | IOException e) {
            log.error("Cannot write license info into release " + releaseId + ".", e);
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE, "500");
        }

        serveRequestStatus(request, response, result, "Cannot write license info into release " + releaseId, log);
    }

    //! VIEW and helpers
    @Override
    public void doView(RenderRequest request, RenderResponse response) throws IOException, PortletException {
        String pageName = request.getParameter(PAGENAME);

        if (PAGENAME_DETAIL.equals(pageName)) {
            prepareDetailView(request, response);
            include("/html/components/detail.jsp", request, response);
        } else if (PAGENAME_RELEASE_DETAIL.equals(pageName)) {
            prepareReleaseDetailView(request, response);
            include("/html/components/detailRelease.jsp", request, response);
        } else if (PAGENAME_EDIT.equals(pageName)) {
            prepareComponentEdit(request);
            include("/html/components/edit.jsp", request, response);
        } else if (PAGENAME_EDIT_RELEASE.equals(pageName)) {
            prepareReleaseEdit(request, response);
            include("/html/components/editRelease.jsp", request, response);
        } else if (PAGENAME_DUPLICATE_RELEASE.equals(pageName)) {
            prepareReleaseDuplicate(request, response);
            include("/html/components/editRelease.jsp", request, response);
        } else if (PAGENAME_MERGE_COMPONENT.equals(pageName)) {
            prepareComponentForMergeOrSplit(request, response, PortalConstants.PAGENAME_MERGE_COMPONENT);
            include("/html/components/mergeComponent.jsp", request, response);
        } else if (PAGENAME_SPLIT_COMPONENT.equals(pageName)) {
            prepareComponentForMergeOrSplit(request, response, PortalConstants.PAGENAME_SPLIT_COMPONENT);
            include("/html/components/splitComponent.jsp", request, response);
        } else if (PAGENAME_MERGE_RELEASE.equals(pageName)) {
            prepareReleaseMerge(request, response);
            include("/html/components/mergeRelease.jsp", request, response);
        } else {
            prepareStandardView(request);
            super.doView(request, response);
        }
    }

    private void prepareComponentEdit(RenderRequest request) {
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());

        String id = request.getParameter(COMPONENT_ID);
        final User user = UserCacheHolder.getUserFromRequest(request);
        request.setAttribute(DOCUMENT_TYPE, SW360Constants.TYPE_COMPONENT);
        if (id != null) {
            try {
                ComponentService.Iface client = thriftClients.makeComponentClient();
                Component component = client.getComponentByIdForEdit(id, user);
                Map<String, String> sortedAdditionalData = getSortedMap(component.getAdditionalData(), true);
                component.setAdditionalData(sortedAdditionalData);

                PortletUtils.setCustomFieldsEdit(request, user, component);

                request.setAttribute(COMPONENT, component);
                request.setAttribute(DOCUMENT_ID, id);

                setAttachmentsInRequest(request, component);
                Map<RequestedAction, Boolean> permissions = component.getPermissions();
                DocumentState documentState = component.getDocumentState();

                addEditDocumentMessage(request, permissions, documentState);
                Set<String> releaseIds = SW360Utils.getReleaseIds(component.getReleases());
                setUsingDocs(request, user, client, releaseIds);
            } catch (TException e) {
                log.error("Error fetching component from backend!", e);
                setSW360SessionError(request, ErrorMessages.ERROR_GETTING_COMPONENT);
            }
        } else {
            if(request.getAttribute(COMPONENT) == null) {
                Component component = new Component();
                request.setAttribute(COMPONENT, component);
                PortletUtils.setCustomFieldsEdit(request, user, component);
                setUsingDocs(request, user, null, component.getReleaseIds());
                setAttachmentsInRequest(request, component);
                SessionMessages.add(request, "request_processed", LanguageUtil.get(resourceBundle,"new.component"));
            }
        }
    }

    private void prepareReleaseEdit(RenderRequest request, RenderResponse response) throws PortletException {
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());

        String id = request.getParameter(COMPONENT_ID);
        String releaseId = request.getParameter(RELEASE_ID);
        final User user = UserCacheHolder.getUserFromRequest(request);
        request.setAttribute(DOCUMENT_TYPE, SW360Constants.TYPE_RELEASE);
        request.setAttribute(IS_USER_AT_LEAST_CLEARING_ADMIN, PermissionUtils.isUserAtLeast(UserGroup.CLEARING_ADMIN, user));

        if (isNullOrEmpty(id) && isNullOrEmpty(releaseId)) {
            throw new PortletException("Component or Release ID not set!");
        }

        try {
            ComponentService.Iface client = thriftClients.makeComponentClient();
            Component component;
            Release release;

            if (!isNullOrEmpty(releaseId)) {
                release = client.getReleaseByIdForEdit(releaseId, user);
                Map<String, String> sortedAdditionalData = getSortedMap(release.getAdditionalData(), true);
                release.setAdditionalData(sortedAdditionalData);
                request.setAttribute(RELEASE, release);
                request.setAttribute(DOCUMENT_ID, releaseId);
                setAttachmentsInRequest(request, release);

                putDirectlyLinkedReleaseRelationsInRequest(request, release);
                Map<RequestedAction, Boolean> permissions = release.getPermissions();
                DocumentState documentState = release.getDocumentState();
                setUsingDocs(request, releaseId, user, client);
                addEditDocumentMessage(request, permissions, documentState);

                if (isNullOrEmpty(id)) {
                    id = release.getComponentId();
                }
                component = client.getComponentById(id, user);

            } else {
                component = client.getComponentById(id, user);
                release = (Release) request.getAttribute(RELEASE);
                if(release == null) {
                    release = new Release();
                    release.setComponentId(id);
                    release.setClearingState(ClearingState.NEW_CLEARING);
                    release.setVendorId(component.getDefaultVendorId());
                    release.setVendor(component.getDefaultVendor());
                    request.setAttribute(RELEASE, release);
                    putDirectlyLinkedReleaseRelationsInRequest(request, release);
                    setAttachmentsInRequest(request, release);
                    setUsingDocs(request, null, user, client);
                    SessionMessages.add(request, "request_processed", LanguageUtil.get(resourceBundle,"new.license"));
                }
            }

            PortletUtils.setCustomFieldsEdit(request, user, release);
            addComponentBreadcrumb(request, response, component);
            if (!isNullOrEmpty(release.getId())) { //Otherwise the link is meaningless
                addReleaseBreadcrumb(request, response, release);
            }

            Map<String, String> externalIds = component.getExternalIds();
            if (externalIds != null && externalIds.containsKey("purl.id")) {
                request.setAttribute(COMPONENT_PURL, externalIds.get("purl.id"));
            } else {
                request.setAttribute(COMPONENT_PURL, "");
            }

            Set<UserGroup> allSecRoles = !CommonUtils.isNullOrEmptyMap(user.getSecondaryDepartmentsAndRoles())
                    ? user.getSecondaryDepartmentsAndRoles().entrySet().stream().flatMap(entry -> entry.getValue().stream()).collect(Collectors.toSet())
                    : new HashSet<UserGroup>();

            request.setAttribute(COMPONENT, component);
            request.setAttribute(IS_USER_AT_LEAST_ECC_ADMIN, PermissionUtils.isUserAtLeast(UserGroup.ECC_ADMIN, user)
                    || PermissionUtils.isUserAtLeastDesiredRoleInSecondaryGroup(UserGroup.ECC_ADMIN, allSecRoles) ? "Yes" : "No");

        } catch (TException e) {
            log.error("Error fetching release from backend!", e);
            setSW360SessionError(request, ErrorMessages.ERROR_GETTING_RELEASE);
        }
    }

    private void prepareReleaseDuplicate(RenderRequest request, RenderResponse response) throws PortletException {
        String id = request.getParameter(COMPONENT_ID);
        String releaseId = request.getParameter(RELEASE_ID);
        request.setAttribute(DOCUMENT_TYPE, SW360Constants.TYPE_RELEASE);
        final User user = UserCacheHolder.getUserFromRequest(request);
        request.setAttribute(IS_USER_AT_LEAST_CLEARING_ADMIN, PermissionUtils.isUserAtLeast(UserGroup.CLEARING_ADMIN, user));

        if (isNullOrEmpty(releaseId)) {
            throw new PortletException("Release ID not set!");
        }

        try {
            ComponentService.Iface client = thriftClients.makeComponentClient();
            String emailFromRequest = LifeRayUserSession.getEmailFromRequest(request);

            Release release = PortletUtils.cloneRelease(emailFromRequest, client.getReleaseById(releaseId, user));
            Map<String, String> sortedAdditionalData = getSortedMap(release.getAdditionalData(), true);
            release.setAdditionalData(sortedAdditionalData);

            PortletUtils.setCustomFieldsEdit(request, user, release);

            if (isNullOrEmpty(id)) {
                id = release.getComponentId();
            }
            Component component = client.getComponentById(id, user);
            addComponentBreadcrumb(request, response, component);
            request.setAttribute(COMPONENT, component);
            request.setAttribute(RELEASE_LIST, Collections.emptyList());
            setUsingDocs(request, null, user, client);
            request.setAttribute(RELEASE, release);
            request.setAttribute(PortalConstants.ATTACHMENTS, Collections.emptySet());

        } catch (TException e) {
            log.error("Error fetching release from backend!", e);
        }
    }

    private void prepareComponentForMergeOrSplit(RenderRequest request, RenderResponse response, String pageName) throws PortletException {
        final User user = UserCacheHolder.getUserFromRequest(request);
        String componentId = request.getParameter(COMPONENT_ID);

        if (isNullOrEmpty(componentId)) {
            throw new PortletException("Component ID not set!");
        }

        try {
            ComponentService.Iface client = thriftClients.makeComponentClient();

            Component component = client.getComponentById(componentId, user);
            request.setAttribute(COMPONENT, component);

            addComponentBreadcrumb(request, response, component);
            PortletURL mergeOrSplitUrl = response.createRenderURL();
            mergeOrSplitUrl.setParameter(PortalConstants.COMPONENT_ID, componentId);
            if (PortalConstants.PAGENAME_MERGE_COMPONENT.equals(pageName)) {
                mergeOrSplitUrl.setParameter(PortalConstants.PAGENAME, PortalConstants.PAGENAME_MERGE_COMPONENT);
                addBreadcrumbEntry(request, "Merge", mergeOrSplitUrl);
            } else {
                mergeOrSplitUrl.setParameter(PortalConstants.PAGENAME, PortalConstants.PAGENAME_SPLIT_COMPONENT);
                addBreadcrumbEntry(request, "Split", mergeOrSplitUrl);
            }
        } catch (TException e) {
            log.error("Error fetching component from backend!", e);
        }
    }

    @UsedAsLiferayAction
    public void componentMergeWizardStep(ActionRequest request, ActionResponse response) throws IOException, PortletException {
        int stepId = Integer.parseInt(request.getParameter("stepId"));
        try {
            HttpServletResponse httpServletResponse = PortalUtil.getHttpServletResponse(response);
            httpServletResponse.setContentType(ContentTypes.APPLICATION_JSON);
            JsonGenerator jsonGenerator = JSON_FACTORY.createGenerator(httpServletResponse.getWriter());

            if (stepId == 0) {
                generateComponentMergeWizardStep0Response(request, jsonGenerator);
            } else if (stepId == 1) {
                generateComponentMergeWizardStep1Response(request, jsonGenerator);
            } else if (stepId == 2) {
                generateComponentMergeWizardStep2Response(request, jsonGenerator);
            } else if (stepId == 3) {
                generateComponentMergeWizardStep3Response(request, jsonGenerator);
            } else {
                throw new SW360Exception("Step with id <" + stepId + "> not supported!");
            }

            jsonGenerator.close();
        } catch (Exception e) {
            log.error("An error occurred while generating a response to component merge wizard", e);
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE,
                    Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        }
    }

    @UsedAsLiferayAction
    public void componentSplitWizardStep(ActionRequest request, ActionResponse response)
            throws IOException, PortletException {
        int stepId = Integer.parseInt(request.getParameter("stepId"));
        try {
            HttpServletResponse httpServletResponse = PortalUtil.getHttpServletResponse(response);
            httpServletResponse.setContentType(ContentTypes.APPLICATION_JSON);
            JsonGenerator jsonGenerator = JSON_FACTORY.createGenerator(httpServletResponse.getWriter());

            if (stepId == 0) {
                generateComponentMergeWizardStep0Response(request, jsonGenerator);
            } else if (stepId == 1) {
                generateComponentMergeWizardStep1Response(request, jsonGenerator);
            } else if (stepId == 2) {
                generateComponentSplitWizardStep2Response(request, jsonGenerator);
            } else if (stepId == 3) {
                generateComponentSplitWizardStep3Response(request, jsonGenerator);
            } else {
                throw new SW360Exception("Step with id <" + stepId + "> not supported!");
            }

            jsonGenerator.close();
        } catch (Exception e) {
            log.error("An error occurred while generating a response to component split wizard", e);
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE,
                    Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        }
    }

    private void generateComponentMergeWizardStep0Response(ActionRequest request, JsonGenerator jsonGenerator) throws IOException, TException {
        User sessionUser = UserCacheHolder.getUserFromRequest(request);
        String srcId = request.getParameter(COMPONENT_SOURCE_ID);
        ComponentService.Iface cClient = thriftClients.makeComponentClient();
        List<Component> componentSummary = cClient.getComponentSummary(sessionUser);

        jsonGenerator.writeStartObject();

        jsonGenerator.writeArrayFieldStart("components");
        componentSummary.stream().filter( component -> !component.getId().equals(srcId)).forEach(component -> {
            try {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("id", component.getId());
                jsonGenerator.writeStringField("name", SW360Utils.printName(component));
                jsonGenerator.writeStringField("createdBy", component.getCreatedBy());
                jsonGenerator.writeNumberField("releases", component.getReleaseIdsSize());
                jsonGenerator.writeEndObject();
            } catch (IOException e) {
                log.error("An error occurred while generating wizard response", e);
            }
        });
        jsonGenerator.writeEndArray();

        jsonGenerator.writeEndObject();
    }

    private void generateComponentMergeWizardStep1Response(ActionRequest request, JsonGenerator jsonGenerator) throws IOException, TException {
        User sessionUser = UserCacheHolder.getUserFromRequest(request);
        String componentTargetId = request.getParameter(COMPONENT_TARGET_ID);
        String componentSourceId = request.getParameter(COMPONENT_SOURCE_ID);

        ComponentService.Iface cClient = thriftClients.makeComponentClient();
        Component componentTarget = cClient.getComponentById(componentTargetId, sessionUser);
        Component componentSource = cClient.getComponentById(componentSourceId, sessionUser);

        jsonGenerator.writeStartObject();

        // adding common title
        jsonGenerator.writeRaw("\"componentTarget\":" + JSON_THRIFT_SERIALIZER.toString(componentTarget) + ",");
        jsonGenerator.writeRaw("\"componentSource\":" + JSON_THRIFT_SERIALIZER.toString(componentSource));

        jsonGenerator.writeEndObject();
    }

    private void generateComponentMergeWizardStep2Response(ActionRequest request, JsonGenerator jsonGenerator)
            throws IOException, TException {
        Component componentSelection = OBJECT_MAPPER.readValue(request.getParameter(COMPONENT_SELECTION),
                Component.class);
        String componentSourceId = request.getParameter(COMPONENT_SOURCE_ID);

        // FIXME: maybe validate the component

        jsonGenerator.writeStartObject();

        // adding common title
        jsonGenerator.writeRaw("\""+ COMPONENT_SELECTION +"\":" + JSON_THRIFT_SERIALIZER.toString(componentSelection) + ",");
        jsonGenerator.writeStringField(COMPONENT_SOURCE_ID, componentSourceId);

        jsonGenerator.writeEndObject();
    }

    private void generateComponentMergeWizardStep3Response(ActionRequest request, JsonGenerator jsonGenerator)
            throws IOException, TException {
        ComponentService.Iface cClient = thriftClients.makeComponentClient();

        // extract request data
        User sessionUser = UserCacheHolder.getUserFromRequest(request);
        Component componentSelection = OBJECT_MAPPER.readValue(request.getParameter(COMPONENT_SELECTION),
                Component.class);
        String componentSourceId = request.getParameter(COMPONENT_SOURCE_ID);

        // perform the real merge, update merge target and delete merge source
        RequestStatus status = cClient.mergeComponents(componentSelection.getId(), componentSourceId, componentSelection, sessionUser);

        // generate redirect url
        LiferayPortletURL componentUrl = createDetailLinkTemplate(request);
        componentUrl.setParameter(PortalConstants.COMPONENT_ID, componentSelection.getId());

        // write response JSON
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("redirectUrl", componentUrl.toString());
        if (status == RequestStatus.IN_USE){
            jsonGenerator.writeStringField("error", "Cannot merge when one of the components has an active moderation request.");
        } else if (status == RequestStatus.ACCESS_DENIED) {
            jsonGenerator.writeStringField("error", "You do not have sufficient permissions.");
        } else if (status == RequestStatus.FAILURE) {
            jsonGenerator.writeStringField("error", "An unknown error occurred during merge.");
        }
        jsonGenerator.writeEndObject();
    }

    private void generateComponentSplitWizardStep2Response(ActionRequest request, JsonGenerator jsonGenerator)
            throws IOException, TException {
        Component srcComponent = OBJECT_MAPPER.readValue(request.getParameter(SOURCE_COMPONENT), Component.class);
        Component targetComponent = OBJECT_MAPPER.readValue(request.getParameter(TARGET_COMPONENT), Component.class);
        jsonGenerator.writeStartObject();
        jsonGenerator.writeRaw("\"" + SOURCE_COMPONENT + "\":" + JSON_THRIFT_SERIALIZER.toString(srcComponent) + ",");
        jsonGenerator.writeRaw("\"" + TARGET_COMPONENT + "\":" + JSON_THRIFT_SERIALIZER.toString(targetComponent));
        jsonGenerator.writeEndObject();
    }

    private void generateComponentSplitWizardStep3Response(ActionRequest request, JsonGenerator jsonGenerator)
            throws IOException, TException {
        ComponentService.Iface cClient = thriftClients.makeComponentClient();

        // extract request data
        User sessionUser = UserCacheHolder.getUserFromRequest(request);
        Component srcComponent = OBJECT_MAPPER.readValue(request.getParameter(SOURCE_COMPONENT), Component.class);
        Component targetComponent = OBJECT_MAPPER.readValue(request.getParameter(TARGET_COMPONENT), Component.class);
        RequestStatus status = cClient.splitComponent(srcComponent, targetComponent, sessionUser);

        // generate redirect url
        LiferayPortletURL componentUrl = createDetailLinkTemplate(request);
        componentUrl.setParameter(PortalConstants.COMPONENT_ID, srcComponent.getId());

        // write response JSON
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("redirectUrl", componentUrl.toString());
        if (status == RequestStatus.IN_USE) {
            jsonGenerator.writeStringField("error",
                    "Cannot split when one of the components has an active moderation request.");
        } else if (status == RequestStatus.ACCESS_DENIED) {
            jsonGenerator.writeStringField("error", "You do not have sufficient permissions.");
        } else if (status == RequestStatus.FAILURE) {
            jsonGenerator.writeStringField("error", "An unknown error occurred during merge.");
        }
        jsonGenerator.writeEndObject();
    }

    private void prepareReleaseMerge(RenderRequest request, RenderResponse response) throws PortletException {
        final User user = UserCacheHolder.getUserFromRequest(request);
        String releaseId = request.getParameter(RELEASE_ID);

        if (isNullOrEmpty(releaseId)) {
            throw new PortletException("Release ID not set!");
        }

        try {
            ComponentService.Iface client = thriftClients.makeComponentClient();

            Release release = client.getReleaseById(releaseId, user);
            request.setAttribute(RELEASE, release);

            addReleaseBreadcrumb(request, response, release);

            PortletURL mergeUrl = response.createRenderURL();
            mergeUrl.setParameter(PortalConstants.PAGENAME, PortalConstants.PAGENAME_MERGE_RELEASE);
            mergeUrl.setParameter(PortalConstants.RELEASE_ID, releaseId);
            addBreadcrumbEntry(request, "Merge", mergeUrl);
        } catch (TException e) {
            log.error("Error fetching release from backend!", e);
        }
    }

    @UsedAsLiferayAction
    public void releaseMergeWizardStep(ActionRequest request, ActionResponse response) throws IOException, PortletException {
        int stepId = Integer.parseInt(request.getParameter("stepId"));
        try {
            HttpServletResponse httpServletResponse = PortalUtil.getHttpServletResponse(response);
            httpServletResponse.setContentType(ContentTypes.APPLICATION_JSON);
            JsonGenerator jsonGenerator = JSON_FACTORY.createGenerator(httpServletResponse.getWriter());

            if (stepId == 0) {
                generateReleaseMergeWizardStep0Response(request, jsonGenerator);
            } else if (stepId == 1) {
                generateReleaseMergeWizardStep1Response(request, jsonGenerator);
            } else if (stepId == 2) {
                generateReleaseMergeWizardStep2Response(request, jsonGenerator);
            } else if (stepId == 3) {
                generateReleaseMergeWizardStep3Response(request, jsonGenerator);
            } else {
                throw new SW360Exception("Step with id <" + stepId + "> not supported!");
            }

            jsonGenerator.close();
        } catch (Exception e) {
            log.error("An error occurred while generating a response to release merge wizard", e);
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE,
                    Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        }
    }

    private void generateReleaseMergeWizardStep0Response(ActionRequest request, JsonGenerator jsonGenerator) throws IOException, TException {
        User sessionUser = UserCacheHolder.getUserFromRequest(request);
        String targetId = request.getParameter(RELEASE_TARGET_ID);
        String componentId = request.getParameter(COMPONENT_ID);

        ComponentService.Iface cClient = thriftClients.makeComponentClient();
        List<Release> releases = cClient.getReleasesByComponentId(componentId, sessionUser);
        
        jsonGenerator.writeStartObject();

        jsonGenerator.writeArrayFieldStart("releases");
        releases.stream().filter( release -> !release.getId().equals(targetId) ).forEach(release -> {
            try {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("id", release.getId());
                jsonGenerator.writeStringField("name", SW360Utils.printName(release));
                jsonGenerator.writeStringField("version", release.getVersion());
                jsonGenerator.writeStringField("createdBy", release.getCreatedBy());
                jsonGenerator.writeEndObject();
            } catch (IOException e) {
                log.error("An error occurred while generating wizard response", e);
            }
        });
        jsonGenerator.writeEndArray();

        jsonGenerator.writeEndObject();
    }

    private void generateReleaseMergeWizardStep1Response(ActionRequest request, JsonGenerator jsonGenerator) throws IOException, TException {
        User sessionUser = UserCacheHolder.getUserFromRequest(request);
        String releaseTargetId = request.getParameter(RELEASE_TARGET_ID);
        String releaseSourceId = request.getParameter(RELEASE_SOURCE_ID);

        ComponentService.Iface cClient = thriftClients.makeComponentClient();
        Release releaseTarget = cClient.getReleaseById(releaseTargetId, sessionUser);
        Release releaseSource = cClient.getReleaseById(releaseSourceId, sessionUser);

        // find matching source code attachment pair, otherwise we will not allow the merge
        boolean matchingPair = false;
        boolean foundSourceAttachments = false;
        Set<String> attachmentHashes = new HashSet<>();
        for(Attachment attachment : nullToEmptySet(releaseTarget.getAttachments())) {
            if(attachment.getAttachmentType().equals(AttachmentType.SOURCE) || attachment.getAttachmentType().equals(AttachmentType.SOURCE_SELF)) {
                attachmentHashes.add(attachment.getSha1());
                foundSourceAttachments = true;
            }
        }
        for(Attachment attachment : nullToEmptySet(releaseSource.getAttachments())) {
            if(attachment.getAttachmentType().equals(AttachmentType.SOURCE) || attachment.getAttachmentType().equals(AttachmentType.SOURCE_SELF)) {
                if(attachmentHashes.contains(attachment.getSha1())) {
                    matchingPair = true;
                    break;
                }
                foundSourceAttachments = true;
            }
        }

        if(foundSourceAttachments && !matchingPair) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("error", "Both releases must have at least one pair of same source attachments or no source attachments at all. Otherwise a merge is not possible.");
            jsonGenerator.writeEndObject();
            return;
        }

        Map<String, Map<String, String>> displayInformation = new HashMap<>();
        
        addToMap(displayInformation, "mainlineState", releaseTarget.getMainlineState());
        addToMap(displayInformation, "mainlineState", releaseSource.getMainlineState());
        addToMap(displayInformation, "repositorytype", releaseTarget.getRepository() != null ? releaseTarget.getRepository().getRepositorytype() : null);
        addToMap(displayInformation, "repositorytype", releaseSource.getRepository() != null ? releaseSource.getRepository().getRepositorytype() : null);
        addToMap(displayInformation, "eccStatus", releaseTarget.getEccInformation().getEccStatus());
        addToMap(displayInformation, "eccStatus", releaseSource.getEccInformation().getEccStatus());
        for(Attachment attachment : nullToEmptySet(releaseSource.getAttachments())) {
            addToMap(displayInformation, "attachmentType", attachment.getAttachmentType());
        }
        for(Attachment attachment : nullToEmptySet(releaseTarget.getAttachments())) {
            addToMap(displayInformation, "attachmentType", attachment.getAttachmentType());
        }

        Set<String> releaseIds = new HashSet<>();
        releaseIds.addAll(nullToEmptyMap(releaseSource.getReleaseIdToRelationship()).keySet());
        releaseIds.addAll(nullToEmptyMap(releaseTarget.getReleaseIdToRelationship()).keySet());
        List<Release> releases = cClient.getReleasesById(releaseIds, sessionUser);
        Map<String, String> releaseToNameMap = new HashMap<String, String>();
        for(Release release : releases) {
            releaseToNameMap.put(release.getId(), release.getName() + " (" + release.getVersion() + ")");
        }
        displayInformation.put("release", releaseToNameMap);
        
        jsonGenerator.writeStartObject();

        // adding common title
        jsonGenerator.writeRaw("\"releaseTarget\":" + JSON_THRIFT_SERIALIZER.toString(releaseTarget) + ",");
        jsonGenerator.writeRaw("\"releaseSource\":" + JSON_THRIFT_SERIALIZER.toString(releaseSource) + ",");
        jsonGenerator.writeRaw("\"displayInformation\":" + OBJECT_MAPPER.writeValueAsString(displayInformation) + ",");
        jsonGenerator.writeRaw("\"usageInformation\":" + OBJECT_MAPPER.writeValueAsString(getUsageInformationForReleaseMerge(releaseSourceId, sessionUser)));

        jsonGenerator.writeEndObject();
    }

    private <T> void addToMap(Map<String, Map<String, String>> map, String key, TEnum value) {
        Map<String, String> subMap = map.getOrDefault(key, new HashMap<String, String>());
        if(value != null) {
            subMap.put(value.getValue() + "", ThriftEnumUtils.enumToString(value));
        }
        map.put(key, subMap);
    }

    private Map<String, Integer> getUsageInformationForReleaseMerge(String releaseSourceId, User sessionUser) throws TException {
        Map<String, Integer> usageInformation = new HashMap<>();

        ProjectService.Iface projectClient = thriftClients.makeProjectClient();
        Set<Project> projects = projectClient.searchByReleaseId(releaseSourceId, sessionUser);
        usageInformation.put("projects", projects.size());

        AttachmentService.Iface attachmentClient = thriftClients.makeAttachmentClient();
        List<AttachmentUsage> attachmentUsages = attachmentClient.getAttachmentUsagesByReleaseId(releaseSourceId);
        usageInformation.put("attachmentUsages", attachmentUsages.size());

        ComponentService.Iface componentClient = thriftClients.makeComponentClient();
        List<Release> releases = componentClient.getReferencingReleases(releaseSourceId);
        usageInformation.put("releases", releases.size());

        VulnerabilityService.Iface vulnerabilityClient = thriftClients.makeVulnerabilityClient();
        List<ReleaseVulnerabilityRelation> releaseVulnerabilities = vulnerabilityClient.getReleaseVulnerabilityRelationsByReleaseId(releaseSourceId, sessionUser);
        usageInformation.put("releaseVulnerabilities", releaseVulnerabilities.size());
        List<ProjectVulnerabilityRating> projectRatings = vulnerabilityClient.getProjectVulnerabilityRatingsByReleaseId(releaseSourceId, sessionUser);
        usageInformation.put("projectRatings", projectRatings.size());
        
        return usageInformation;
    }

    private void generateReleaseMergeWizardStep2Response(ActionRequest request, JsonGenerator jsonGenerator)
            throws IOException, TException {
        Release releaseSelection = OBJECT_MAPPER.readValue(request.getParameter(RELEASE_SELECTION),
                Release.class);
        String releaseSourceId = request.getParameter(RELEASE_SOURCE_ID);

        // FIXME: maybe validate the component

        jsonGenerator.writeStartObject();

        // adding common title
        jsonGenerator.writeRaw("\""+ RELEASE_SELECTION +"\":" + JSON_THRIFT_SERIALIZER.toString(releaseSelection) + ",");
        jsonGenerator.writeStringField(RELEASE_SOURCE_ID, releaseSourceId);

        jsonGenerator.writeEndObject();
    }

    private void generateReleaseMergeWizardStep3Response(ActionRequest request, JsonGenerator jsonGenerator)
            throws IOException, TException {
        ComponentService.Iface cClient = thriftClients.makeComponentClient();

        // extract request data
        User sessionUser = UserCacheHolder.getUserFromRequest(request);
        Release releaseSelection = OBJECT_MAPPER.readValue(request.getParameter(RELEASE_SELECTION),
                Release.class);
        String releaseSourceId = request.getParameter(RELEASE_SOURCE_ID);

        // perform the real merge, update merge target and delete merge source
        RequestStatus status = cClient.mergeReleases(releaseSelection.getId(), releaseSourceId, releaseSelection, sessionUser);

        // generate redirect url
        LiferayPortletURL releaseUrl = createDetailLinkTemplate(request);
        releaseUrl.setParameter(PortalConstants.PAGENAME, PortalConstants.PAGENAME_RELEASE_DETAIL);
        releaseUrl.setParameter(PortalConstants.RELEASE_ID, releaseSelection.getId());

        // write response JSON
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("redirectUrl", releaseUrl.toString());
        if (status == RequestStatus.IN_USE){
            jsonGenerator.writeStringField("error", "Cannot merge when one of the releases has an active moderation request.");
        } else if (status == RequestStatus.ACCESS_DENIED) {
            jsonGenerator.writeStringField("error", "You do not have sufficient permissions.");
        } else if (status == RequestStatus.FAILURE) {
            jsonGenerator.writeStringField("error", "An unknown error occurred during merge.");
        }

        jsonGenerator.writeEndObject();
    }

    private void prepareDetailView(RenderRequest request, RenderResponse response) {
        String id = request.getParameter(COMPONENT_ID);
        final User user = UserCacheHolder.getUserFromRequest(request);

        if (!isNullOrEmpty(id)) {
            try {
                ComponentService.Iface client = thriftClients.makeComponentClient();
                Component component = client.getComponentById(id, user);
                Map<String, String> sortedAdditionalData = getSortedMap(component.getAdditionalData(), true);
                component.setAdditionalData(sortedAdditionalData);

                PortletUtils.setCustomFieldsDisplay(request, user, component);
                request.setAttribute(COMPONENT, component);
                request.setAttribute(DOCUMENT_ID, id);
                request.setAttribute(DOCUMENT_TYPE, SW360Constants.TYPE_COMPONENT);
                setAttachmentsInRequest(request, component);
                Set<String> releaseIds = SW360Utils.getReleaseIds(component.getReleases());

                setUsingDocs(request, user, client, releaseIds);

                request.setAttribute(IS_USER_ALLOWED_TO_MERGE, PermissionUtils.isUserAtLeast(USER_ROLE_ALLOWED_TO_MERGE_OR_SPLIT_COMPONENT, user));

                // get vulnerabilities
                Set<UserGroup> allSecRoles = !CommonUtils.isNullOrEmptyMap(user.getSecondaryDepartmentsAndRoles())
                        ? user.getSecondaryDepartmentsAndRoles().entrySet().stream().flatMap(entry -> entry.getValue().stream()).collect(Collectors.toSet())
                        : new HashSet<UserGroup>();
                boolean isVulEditable = PermissionUtils.isUserAtLeast(UserGroup.SECURITY_ADMIN, user)
                        || PermissionUtils.isUserAtLeastDesiredRoleInSecondaryGroup(UserGroup.SECURITY_ADMIN, allSecRoles);
                putVulnerabilitiesInRequestComponent(request, id, user, isVulEditable);
                request.setAttribute(VULNERABILITY_VERIFICATION_EDITABLE, isVulEditable);

                addComponentBreadcrumb(request, response, component);
            } catch (TException e) {
                log.error("Error fetching component from backend!", e);
                setSW360SessionError(request, ErrorMessages.ERROR_GETTING_COMPONENT);
            }
        }
    }

    private void setUsingDocs(RenderRequest request, User user, ComponentService.Iface client, Set<String> releaseIds) {
        Set<Project> usingProjects = null;
        Set<Component> usingComponentsForComponent = null;
        int allUsingProjectsCount = 0;

        if (releaseIds != null && releaseIds.size() > 0) {
            try {
                ProjectService.Iface projectClient = thriftClients.makeProjectClient();
                usingProjects = projectClient.searchByReleaseIds(releaseIds, user);
                allUsingProjectsCount = projectClient.getCountByReleaseIds(releaseIds);
                usingComponentsForComponent = client.getUsingComponentsForComponent(releaseIds);
            } catch (TException e) {
                log.error("Problem filling using docs", e);
            }
        }

        request.setAttribute(USING_PROJECTS, nullToEmptySet(usingProjects));
        request.setAttribute(USING_COMPONENTS, nullToEmptySet(usingComponentsForComponent));
        request.setAttribute(ALL_USING_PROJECTS_COUNT, allUsingProjectsCount);
    }

    private void prepareReleaseDetailView(RenderRequest request, RenderResponse response) throws PortletException {
        String id = request.getParameter(COMPONENT_ID);
        String releaseId = request.getParameter(RELEASE_ID);
        final User user = UserCacheHolder.getUserFromRequest(request);

        if (isNullOrEmpty(id) && isNullOrEmpty(releaseId)) {
            throw new PortletException("Component or Release ID not set!");
        }

        try {
            ComponentService.Iface client = thriftClients.makeComponentClient();
            FossologyService.Iface fossologyClient = thriftClients.makeFossologyClient();
            Component component;
            Release release = null;

            if (!isNullOrEmpty(releaseId)) {
                release = client.getReleaseById(releaseId, user);
                Map<String, String> sortedAdditionalData = getSortedMap(release.getAdditionalData(), true);
                release.setAdditionalData(sortedAdditionalData);

                ExternalToolProcessStep processStep = SW360Utils.getExternalToolProcessStepOfFirstProcessForTool(
                        release, ExternalTool.FOSSOLOGY, FossologyUtils.FOSSOLOGY_STEP_NAME_UPLOAD);
                ConfigContainer fossologyConfig = fossologyClient.getFossologyConfig();
                Map<String, Set<String>> configKeyToValues = fossologyConfig.getConfigKeyToValues();
                String fossologyJobsViewLink = null;
                if (!configKeyToValues.isEmpty()) {
                    fossologyJobsViewLink = createFossologyJobViewLink(processStep, configKeyToValues,
                            fossologyJobsViewLink);
                }

                PortletUtils.setCustomFieldsDisplay(request, user, release);

                request.setAttribute(FOSSOLOGY_JOB_VIEW_LINK, fossologyJobsViewLink);
                request.setAttribute(RELEASE_ID, releaseId);
                request.setAttribute(RELEASE, release);
                request.setAttribute(DOCUMENT_ID, releaseId);
                request.setAttribute(DOCUMENT_TYPE, SW360Constants.TYPE_RELEASE);
                setAttachmentsInRequest(request, release);
                setSpdxAttachmentsInRequest(request, release);

                setUsingDocs(request, releaseId, user, client);
                putDirectlyLinkedReleaseRelationsInRequest(request, release);
                request.setAttribute(IS_USER_ALLOWED_TO_MERGE, PermissionUtils.isUserAtLeast(USER_ROLE_ALLOWED_TO_MERGE_OR_SPLIT_COMPONENT, user));

                if (isNullOrEmpty(id)) {
                    id = release.getComponentId();
                }
                Set<UserGroup> allSecRoles = !CommonUtils.isNullOrEmptyMap(user.getSecondaryDepartmentsAndRoles())
                        ? user.getSecondaryDepartmentsAndRoles().entrySet().stream().flatMap(entry -> entry.getValue().stream()).collect(Collectors.toSet())
                        : new HashSet<UserGroup>();
                boolean isVulEditable = PermissionUtils.isUserAtLeast(UserGroup.SECURITY_ADMIN, user)
                        || PermissionUtils.isUserAtLeastDesiredRoleInSecondaryGroup(UserGroup.SECURITY_ADMIN, allSecRoles);
                putVulnerabilitiesInRequestRelease(request, releaseId, user, isVulEditable);
                request.setAttribute(VULNERABILITY_VERIFICATION_EDITABLE, isVulEditable);
            }

            component = client.getComponentById(id, user);
            request.setAttribute(COMPONENT, component);

            addComponentBreadcrumb(request, response, component);
            if (release != null) {
                addReleaseBreadcrumb(request, response, release);
            }

        } catch (TException e) {
            log.error("Error fetching release from backend!", e);
            setSW360SessionError(request, ErrorMessages.ERROR_GETTING_RELEASE);
        }

    }

    private String createFossologyJobViewLink(ExternalToolProcessStep processStep,
            Map<String, Set<String>> configKeyToValues, String fossologyJobsViewLink) {
        String uploadId = null;
        if (processStep != null) {
            uploadId = processStep.getResult();
        }
        String url = configKeyToValues.get(CONFIG_KEY_URL).stream().findFirst().orElse(StringUtils.EMPTY);
        String fossologyHostName = null;
        String fossologyPath = null;
        String protocol = null;
        String portStr = StringUtils.EMPTY;
        try {
            URI fossologyRestURI = new URI(url);
            fossologyHostName = fossologyRestURI.getHost();
            fossologyPath = fossologyRestURI.getPath();
            fossologyPath = fossologyPath.substring(0,fossologyPath.indexOf("/api/v"));
            protocol = fossologyRestURI.getScheme();
            int port = fossologyRestURI.getPort();
            portStr = port == -1 ? StringUtils.EMPTY : ":" + port;
        } catch (URISyntaxException e) {
            log.error("Error creating URI from fossology REST Link.." + url);
        }
        if (!CommonUtils.isNullEmptyOrWhitespace(fossologyHostName)
                && !CommonUtils.isNullEmptyOrWhitespace(uploadId)
                && !CommonUtils.isNullEmptyOrWhitespace(protocol)) {
            fossologyJobsViewLink = protocol + "://" + fossologyHostName + portStr + fossologyPath + "/"
                    + QUERY_PARAMS_FOSSOLOGY + uploadId;
        }
        return fossologyJobsViewLink;

    }

    private void setSpdxAttachmentsInRequest(RenderRequest request, Release release) {
        Set<Attachment> attachments = CommonUtils.nullToEmptySet(release.getAttachments());
        Set<Attachment> spdxAttachments = attachments.stream()
                .filter(a -> AttachmentType.COMPONENT_LICENSE_INFO_COMBINED.equals(a.getAttachmentType())
                        || AttachmentType.COMPONENT_LICENSE_INFO_XML.equals(a.getAttachmentType()))
                .collect(Collectors.toSet());
        request.setAttribute(PortalConstants.SPDX_ATTACHMENTS, spdxAttachments);
    }

    private String formatedMessageForVul(List<VerificationStateInfo> infoHistory){
        return CommonVulnerabilityPortletUtils.formatedMessageForVul(infoHistory,
                e -> e.getVerificationState().name(),
                e -> e.getCheckedOn(),
                e -> e.getCheckedBy(),
                e -> e.getComment(),
                e -> "");
    }

    private void putVulnerabilitiesInRequestRelease(RenderRequest request, String releaseId, User user, boolean isVulEditable) throws TException {
        VulnerabilityService.Iface vulClient = thriftClients.makeVulnerabilityClient();
        List<VulnerabilityDTO> vuls;
        if (isVulEditable) {
            vuls = vulClient.getVulnerabilitiesByReleaseId(releaseId, user);
        } else {
            vuls = vulClient.getVulnerabilitiesByReleaseIdWithoutIncorrect(releaseId, user);
        }

        putVulnerabilitiesInRequest(request, vuls, user);
    }

    private void putVulnerabilitiesInRequestComponent(RenderRequest request, String componentId, User user, boolean isVulEditable) throws TException{
        VulnerabilityService.Iface vulClient = thriftClients.makeVulnerabilityClient();
        List<VulnerabilityDTO> vuls;
        if (isVulEditable) {
            vuls = vulClient.getVulnerabilitiesByComponentId(componentId, user);
        } else {
            vuls = vulClient.getVulnerabilitiesByComponentIdWithoutIncorrect(componentId, user);
        }

        putVulnerabilitiesInRequest(request, vuls, user);
    }

    private void putVulnerabilitiesInRequest(RenderRequest request, List<VulnerabilityDTO> vuls, User user) {
        CommonVulnerabilityPortletUtils.putLatestVulnerabilitiesInRequest(request, vuls, user);
        CommonVulnerabilityPortletUtils.putMatchedByHistogramInRequest(request, vuls);
        putVulnerabilityMetadatasInRequest(request, vuls);
    }

    private void addToVulnerabilityVerifications(Map<String, Map<String, VerificationState>> vulnerabilityVerifications,
                                                 Map<String, Map<String, String>> vulnerabilityTooltips,
                                                 VulnerabilityDTO vulnerability){
        String vulnerabilityId = vulnerability.getExternalId();
        String releaseId = vulnerability.getIntReleaseId();
        Map<String, VerificationState> vulnerabilityVerification = vulnerabilityVerifications.computeIfAbsent(vulnerabilityId, k -> new HashMap<>());
        Map<String, String> vulnerabilityTooltip = vulnerabilityTooltips.computeIfAbsent(vulnerabilityId, k -> new HashMap<>());
        ReleaseVulnerabilityRelation relation = vulnerability.getReleaseVulnerabilityRelation();

        if (! relation.isSetVerificationStateInfo()) {
            vulnerabilityVerification.put(releaseId, VerificationState.NOT_CHECKED);
            vulnerabilityTooltip.put(releaseId, "Not checked yet.");
        } else {
            List<VerificationStateInfo> infoHistory = relation.getVerificationStateInfo();
            VerificationStateInfo info = infoHistory.get(infoHistory.size() - 1);
            vulnerabilityVerification.put(releaseId, info.getVerificationState());
            vulnerabilityTooltip.put(releaseId, formatedMessageForVul(infoHistory));
        }
    }

    private void putVulnerabilityMetadatasInRequest(RenderRequest request, List<VulnerabilityDTO> vuls) {
        Map<String, Map<String, String>> vulnerabilityTooltips = new HashMap<>();
        Map<String, Map<String, VerificationState>> vulnerabilityVerifications = new HashMap<>();
        for (VulnerabilityDTO vulnerability : vuls) {
            addToVulnerabilityVerifications(vulnerabilityVerifications, vulnerabilityTooltips, vulnerability);
        }

        long numberOfCorrectVuls = vuls.stream()
                .filter(vul -> ! VerificationState.INCORRECT.equals(getVerificationState(vul)))
                .map(VulnerabilityDTO::getExternalId)
                .collect(Collectors.toSet())
                .size();
        request.setAttribute(NUMBER_OF_CHECKED_OR_UNCHECKED_VULNERABILITIES, numberOfCorrectVuls);
        User userFromRequest = UserCacheHolder.getUserFromRequest(request);
        Set<UserGroup> allSecRoles = !CommonUtils.isNullOrEmptyMap(userFromRequest.getSecondaryDepartmentsAndRoles())
                ? userFromRequest.getSecondaryDepartmentsAndRoles().entrySet().stream().flatMap(entry -> entry.getValue().stream()).collect(Collectors.toSet())
                : new HashSet<UserGroup>();
        if (PermissionUtils.isAdmin(userFromRequest) || PermissionUtils.isAdminBySecondaryRoles(allSecRoles)) {
            long numberOfIncorrectVuls = vuls.stream()
                    .filter(v -> VerificationState.INCORRECT.equals(getVerificationState(v)))
                    .map(VulnerabilityDTO::getExternalId)
                    .collect(Collectors.toSet())
                    .size();
            request.setAttribute(NUMBER_OF_INCORRECT_VULNERABILITIES, numberOfIncorrectVuls);
        }

        request.setAttribute(PortalConstants.VULNERABILITY_VERIFICATIONS,vulnerabilityVerifications);
        request.setAttribute(PortalConstants.VULNERABILITY_VERIFICATION_TOOLTIPS,vulnerabilityTooltips);
    }


    private void setUsingDocs(RenderRequest request, String releaseId, User user, ComponentService.Iface client) throws TException {
        if (releaseId != null) {
            ProjectService.Iface projectClient = thriftClients.makeProjectClient();
            Set<Project> usingProjects = projectClient.searchByReleaseId(releaseId, user);
            request.setAttribute(USING_PROJECTS, nullToEmptySet(usingProjects));
            int allUsingProjectsCount = projectClient.getCountByReleaseIds(Collections.singleton(releaseId));
            request.setAttribute(ALL_USING_PROJECTS_COUNT, allUsingProjectsCount);
            final Set<Component> usingComponentsForRelease = client.getUsingComponentsForRelease(releaseId);
            request.setAttribute(USING_COMPONENTS, nullToEmptySet(usingComponentsForRelease));
        } else {
            request.setAttribute(USING_PROJECTS, Collections.emptySet());
            request.setAttribute(USING_COMPONENTS, Collections.emptySet());
            request.setAttribute(ALL_USING_PROJECTS_COUNT, 0);
        }
    }

    private void addComponentBreadcrumb(RenderRequest request, RenderResponse response, Component component) {
        PortletURL componentUrl = response.createRenderURL();
        componentUrl.setParameter(PAGENAME, PAGENAME_DETAIL);
        componentUrl.setParameter(COMPONENT_ID, component.getId());

        addBreadcrumbEntry(request, printName(component), componentUrl);
    }

    private void addReleaseBreadcrumb(RenderRequest request, RenderResponse response, Release release) {
        PortletURL releaseURL = response.createRenderURL();
        releaseURL.setParameter(PAGENAME, PAGENAME_RELEASE_DETAIL);
        releaseURL.setParameter(RELEASE_ID, release.getId());

        addBreadcrumbEntry(request, printName(release), releaseURL);
    }

    private void prepareStandardView(RenderRequest request) throws IOException {
        Set<String> vendorNames;

        try {
            vendorNames = thriftClients.makeVendorClient().getAllVendorNames();
        } catch (TException e) {
            log.error("Problem retrieving all the Vendor names", e);
            vendorNames = Collections.emptySet();
        }

        List<String> componentTypeNames = Arrays.asList(ComponentType.values())
                .stream()
                .map(ThriftEnumUtils::enumToString)
                .collect(Collectors.toList());

        request.setAttribute(VENDOR_LIST, new ThriftJsonSerializer().toJson(vendorNames));
        request.setAttribute(COMPONENT_TYPE_LIST, new ThriftJsonSerializer().toJson(componentTypeNames));
        setComponentViewFilterAttributes(request);
    }

    private void setComponentViewFilterAttributes(PortletRequest request) {
        for (Component._Fields filteredField : componentFilteredFields) {
            String parameter = request.getParameter(filteredField.toString());
            request.setAttribute(filteredField.getFieldName(), nullToEmpty(parameter));
        }
        request.setAttribute(PortalConstants.DATE_RANGE, nullToEmpty(request.getParameter(PortalConstants.DATE_RANGE)));
        request.setAttribute(PortalConstants.END_DATE, nullToEmpty(request.getParameter(PortalConstants.END_DATE)));
        try {
            final User user = UserCacheHolder.getUserFromRequest(request);
            ComponentService.Iface componentClient = thriftClients.makeComponentClient();
            request.setAttribute(PortalConstants.TOTAL_ROWS, componentClient.getTotalComponentsCount(user));
        } catch (TException e) {
            log.error("Could not get component total count in backend ", e);
        }
    }

    private Map<String, Set<String>> getComponentFilterMap(PortletRequest request) {
        Map<String, Set<String>> filterMap = new HashMap<>();
        for (Component._Fields filteredField : componentFilteredFields) {
            String parameter = request.getParameter(filteredField.toString());
            if (!isNullOrEmpty(parameter) && !(filteredField.equals(Component._Fields.COMPONENT_TYPE)
                    && parameter.equals(PortalConstants.NO_FILTER))) {

                if (filteredField.equals(Component._Fields.CREATED_ON) && isNotNullEmptyOrWhitespace(request.getParameter(PortalConstants.DATE_RANGE))) {
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
                if (filteredField.equals(Component._Fields.NAME)) {
                    values = values.stream().map(LuceneAwareDatabaseConnector::prepareWildcardQuery).collect(Collectors.toSet());
                }

                filterMap.put(filteredField.getFieldName(), values);
            }
        }
        return filterMap;
    }

    private List<Component> getFilteredComponentList(PortletRequest request) {
        Map<String, Set<String>> filterMap = getComponentFilterMap(request);
        List<Component> componentList;
        int limit = -1;

        try {
            final User user = UserCacheHolder.getUserFromRequest(request);
            ComponentService.Iface componentClient = thriftClients.makeComponentClient();
            if (filterMap.isEmpty()) {
                componentList = componentClient.getRecentComponentsSummary(limit, user);
            } else {
                componentList = componentClient.refineSearch(null, filterMap);
            }
        } catch (TException e) {
            log.error("Could not search components in backend ", e);
            componentList = Collections.emptyList();
        }

        return componentList;
    }

    //! Actions
    @UsedAsLiferayAction
    public void updateComponent(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        String id = request.getParameter(COMPONENT_ID);
        final User user = UserCacheHolder.getUserFromRequest(request);

        try {
            ComponentService.Iface client = thriftClients.makeComponentClient();

            if (id != null) {
                Component component = client.getComponentByIdForEdit(id, user);
                ComponentPortletUtils.updateComponentFromRequest(request, component);
                String ModerationRequestCommentMsg = request.getParameter(MODERATION_REQUEST_COMMENT);
                user.setCommentMadeDuringModerationRequest(ModerationRequestCommentMsg);
                RequestStatus requestStatus = client.updateComponent(component, user);
                setSessionMessage(request, requestStatus, "Component", "update", component.getName());
                if (RequestStatus.DUPLICATE.equals(requestStatus) || RequestStatus.DUPLICATE_ATTACHMENT.equals(requestStatus) ||
                        RequestStatus.NAMINGERROR.equals(requestStatus)) {
                    if(RequestStatus.DUPLICATE.equals(requestStatus))
                        setSW360SessionError(request, ErrorMessages.COMPONENT_DUPLICATE);
                    else if (RequestStatus.NAMINGERROR.equals(requestStatus))
                        setSW360SessionError(request, ErrorMessages.COMPONENT_NAMING_ERROR);
                    else
                        setSW360SessionError(request, ErrorMessages.DUPLICATE_ATTACHMENT);
                    response.setRenderParameter(PAGENAME, PAGENAME_EDIT);
                    request.setAttribute(DOCUMENT_TYPE, SW360Constants.TYPE_COMPONENT);
                    request.setAttribute(DOCUMENT_ID, id);
                    prepareRequestForEditAfterDuplicateOrNamingError(request, component);
                } else {
                    cleanUploadHistory(user.getEmail(), id);
                    response.setRenderParameter(PAGENAME, PAGENAME_DETAIL);
                    response.setRenderParameter(COMPONENT_ID, request.getParameter(COMPONENT_ID));
                }
            } else {
                Component component = new Component();
                ComponentPortletUtils.updateComponentFromRequest(request, component);
                AddDocumentRequestSummary summary = client.addComponent(component, user);

                AddDocumentRequestStatus status = summary.getRequestStatus();
                switch(status){
                    case SUCCESS:
                        String successMsg = "Component " + component.getName() + " added successfully";
                        SessionMessages.add(request, "request_processed", successMsg);
                        response.setRenderParameter(COMPONENT_ID, summary.getId());
                        response.setRenderParameter(PAGENAME, PAGENAME_EDIT);
                        break;
                    case DUPLICATE:
                        setSW360SessionError(request, ErrorMessages.COMPONENT_DUPLICATE);
                        response.setRenderParameter(PAGENAME, PAGENAME_EDIT);
                        prepareRequestForEditAfterDuplicateOrNamingError(request, component);
                        break;
                    case NAMINGERROR:
                        setSW360SessionError(request, ErrorMessages.COMPONENT_NAMING_ERROR);
                        response.setRenderParameter(PAGENAME, PAGENAME_EDIT);
                        prepareRequestForEditAfterDuplicateOrNamingError(request, component);
                        break;
                    default:
                        setSW360SessionError(request, ErrorMessages.COMPONENT_NOT_ADDED);
                        response.setRenderParameter(PAGENAME, PAGENAME_VIEW);
                }
            }

        } catch (TException e) {
            log.error("Error fetching component from backend!", e);
        }
    }

    private void prepareRequestForEditAfterDuplicateOrNamingError(ActionRequest request, Component component) throws TException {
        request.setAttribute(COMPONENT, component);
        setAttachmentsInRequest(request, component);
        request.setAttribute(USING_PROJECTS, Collections.emptySet());
        request.setAttribute(USING_COMPONENTS, Collections.emptySet());
        request.setAttribute(ALL_USING_PROJECTS_COUNT, 0);
    }

    @UsedAsLiferayAction
    public void updateRelease(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        String id = request.getParameter(COMPONENT_ID);
        final User user = UserCacheHolder.getUserFromRequest(request);

        if (id != null) {
            try {
                ComponentService.Iface client = thriftClients.makeComponentClient();
                Component component = client.getComponentById(id, user);

                Release release;
                String releaseId = request.getParameter(RELEASE_ID);
                if (releaseId != null) {
                    release = client.getReleaseByIdForEdit(releaseId, user);
                    ComponentPortletUtils.updateReleaseFromRequest(request, release);
                    String ModerationRequestCommentMsg = request.getParameter(MODERATION_REQUEST_COMMENT);
                    user.setCommentMadeDuringModerationRequest(ModerationRequestCommentMsg);

                    String cyclicLinkedReleasePath = client.getCyclicLinkedReleasePath(release, user);
                    if (!isNullEmptyOrWhitespace(cyclicLinkedReleasePath)) {
                        FossologyAwarePortlet.addCustomErrorMessage(CYCLIC_LINKED_RELEASE + cyclicLinkedReleasePath,
                                PAGENAME_EDIT_RELEASE, request, response);
                        prepareRequestForReleaseEditAfterDuplicateError(request, release);
                        request.setAttribute(DOCUMENT_TYPE, SW360Constants.TYPE_RELEASE);
                        response.setRenderParameter(COMPONENT_ID, id);
                        response.setRenderParameter(RELEASE_ID, releaseId);
                        return;
                    }

                    RequestStatus requestStatus = client.updateRelease(release, user);
                    setSessionMessage(request, requestStatus, "Release", "update", printName(release));
                    if (RequestStatus.DUPLICATE.equals(requestStatus) || RequestStatus.DUPLICATE_ATTACHMENT.equals(requestStatus) ||
                            RequestStatus.NAMINGERROR.equals(requestStatus)) {
                        if(RequestStatus.DUPLICATE.equals(requestStatus))
                            setSW360SessionError(request, ErrorMessages.RELEASE_DUPLICATE);
                        else if (RequestStatus.NAMINGERROR.equals(requestStatus))
                            setSW360SessionError(request, ErrorMessages.RELEASE_NAME_VERSION_ERROR);
                        else
                            setSW360SessionError(request, ErrorMessages.DUPLICATE_ATTACHMENT);
                        response.setRenderParameter(PAGENAME, PAGENAME_EDIT_RELEASE);
                        request.setAttribute(DOCUMENT_TYPE, SW360Constants.TYPE_RELEASE);
                        response.setRenderParameter(COMPONENT_ID, id);
                        response.setRenderParameter(RELEASE_ID, releaseId);
                        prepareRequestForReleaseEditAfterDuplicateError(request, release);
                    } else {
                        cleanUploadHistory(user.getEmail(), releaseId);

                        // successful update of release means we want to send a redirect to the detail
                        // view to make sure that no POST gets executed twice by some browser reload or
                        // back button click (POST-redirect-GET pattern)
                        String portletId = (String) request.getAttribute(WebKeys.PORTLET_ID);
                        ThemeDisplay tD = (ThemeDisplay) request.getAttribute(WebKeys.THEME_DISPLAY);
                        long plid = tD.getPlid();

                        LiferayPortletURL redirectUrl = PortletURLFactoryUtil.create(request, portletId, plid,
                                PortletRequest.RENDER_PHASE);
                        redirectUrl.setParameter(PAGENAME, PAGENAME_RELEASE_DETAIL);
                        redirectUrl.setParameter(COMPONENT_ID, id);
                        redirectUrl.setParameter(RELEASE_ID, releaseId);

                        request.setAttribute(WebKeys.REDIRECT, redirectUrl.toString());
                        sendRedirect(request, response);
                    }
                } else {
                    release = new Release();
                    release.setComponentId(component.getId());
                    release.setClearingState(ClearingState.NEW_CLEARING);
                    ComponentPortletUtils.updateReleaseFromRequest(request, release);

                    String cyclicLinkedReleasePath = client.getCyclicLinkedReleasePath(release, user);
                    if (!isNullEmptyOrWhitespace(cyclicLinkedReleasePath)) {
                        FossologyAwarePortlet.addCustomErrorMessage(CYCLIC_LINKED_RELEASE + cyclicLinkedReleasePath,
                                PAGENAME_EDIT_RELEASE, request, response);
                        prepareRequestForReleaseEditAfterDuplicateError(request, release);
                        response.setRenderParameter(COMPONENT_ID, request.getParameter(COMPONENT_ID));
                        return;
                    }

                    AddDocumentRequestSummary summary = client.addRelease(release, user);

                    AddDocumentRequestStatus status = summary.getRequestStatus();
                    switch(status){
                        case SUCCESS:
                            response.setRenderParameter(RELEASE_ID, summary.getId());
                            String successMsg = "Release " + printName(release) + " added successfully";
                            SessionMessages.add(request, "request_processed", successMsg);
                            response.setRenderParameter(PAGENAME, PAGENAME_EDIT_RELEASE);
                            break;
                        case DUPLICATE:
                            setSW360SessionError(request, ErrorMessages.RELEASE_DUPLICATE);
                            response.setRenderParameter(PAGENAME, PAGENAME_EDIT_RELEASE);
                            prepareRequestForReleaseEditAfterDuplicateError(request, release);
                            break;
                        case NAMINGERROR:
                            setSW360SessionError(request, ErrorMessages.RELEASE_NAME_VERSION_ERROR);
                            response.setRenderParameter(PAGENAME, PAGENAME_EDIT_RELEASE);
                            prepareRequestForReleaseEditAfterDuplicateError(request, release);
                            break;
                        default:
                            setSW360SessionError(request, ErrorMessages.RELEASE_NOT_ADDED);
                            response.setRenderParameter(PAGENAME, PAGENAME_DETAIL);
                    }

                    response.setRenderParameter(COMPONENT_ID, request.getParameter(COMPONENT_ID));
                }
            } catch (TException e) {
                log.error("Error fetching release from backend!", e);
            }
        }
    }

    private void prepareRequestForReleaseEditAfterDuplicateError(ActionRequest request, Release release) throws TException {
        fillVendor(release);
        request.setAttribute(RELEASE, release);
        setAttachmentsInRequest(request, release);
        putDirectlyLinkedReleaseRelationsInRequest(request, release);
        request.setAttribute(USING_PROJECTS, Collections.emptySet());
        request.setAttribute(USING_COMPONENTS, Collections.emptySet());
        request.setAttribute(ALL_USING_PROJECTS_COUNT, 0);
    }

    private void fillVendor(Release release) throws TException {
        if(!isNullOrEmpty(release.getVendorId()) && release.isSetVendorId()) {
            VendorService.Iface client = thriftClients.makeVendorClient();
            Vendor vendor = client.getByID(release.getVendorId());
            release.setVendor(vendor);
        }
    }

    @UsedAsLiferayAction
    public void deleteRelease(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        RequestStatus requestStatus = ComponentPortletUtils.deleteRelease(request, log);

        String userEmail = UserCacheHolder.getUserFromRequest(request).getEmail();
        String releaseId = request.getParameter(PortalConstants.RELEASE_ID);
        deleteUnneededAttachments(userEmail, releaseId);
        setSessionMessage(request, requestStatus, "Release", "delete");

        response.setRenderParameter(PAGENAME, PAGENAME_DETAIL);
        response.setRenderParameter(COMPONENT_ID, request.getParameter(COMPONENT_ID));
    }

    @UsedAsLiferayAction
    public void deleteComponent(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        RequestStatus requestStatus = ComponentPortletUtils.deleteComponent(request, log);

        String userEmail = UserCacheHolder.getUserFromRequest(request).getEmail();
        String id = request.getParameter(PortalConstants.COMPONENT_ID);
        deleteUnneededAttachments(userEmail, id);
        setSessionMessage(request, requestStatus, "Component", "delete");
    }

    @UsedAsLiferayAction
    public void applyFilters(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        for (Component._Fields componentFilteredField : componentFilteredFields) {
            response.setRenderParameter(componentFilteredField.toString(), nullToEmpty(request.getParameter(componentFilteredField.toString())));
        }
        response.setRenderParameter(PortalConstants.DATE_RANGE, nullToEmpty(request.getParameter(PortalConstants.DATE_RANGE)));
        response.setRenderParameter(PortalConstants.END_DATE, nullToEmpty(request.getParameter(PortalConstants.END_DATE)));
    }

    private void updateVulnerabilitiesRelease(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
        String releaseId = request.getParameter(PortalConstants.RELEASE_ID);
        CveSearchService.Iface cveClient = thriftClients.makeCvesearchClient();
        try {
            VulnerabilityUpdateStatus importStatus = cveClient.updateForRelease(releaseId);
            JSONObject responseData = PortletUtils.importStatusToJSON(importStatus);
            PrintWriter writer = response.getWriter();
            writer.write(responseData.toString());
        } catch (TException e){
            log.error("Error updating CVEs for release in backend.", e);
        }
    }

    private void updateVulnerabilitiesComponent(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
        String componentId = request.getParameter(PortalConstants.COMPONENT_ID);
        CveSearchService.Iface cveClient = thriftClients.makeCvesearchClient();
        try {
            VulnerabilityUpdateStatus importStatus = cveClient.updateForComponent(componentId);
            JSONObject responseData = PortletUtils.importStatusToJSON(importStatus);
            PrintWriter writer = response.getWriter();
            writer.write(responseData.toString());
        } catch (TException e) {
            log.error("Error updating CVEs for component in backend.", e);
        }
    }

    private void updateAllVulnerabilities(ResourceRequest request, ResourceResponse response) throws PortletException, IOException {
        CveSearchService.Iface cveClient = thriftClients.makeCvesearchClient();
        try {
            VulnerabilityUpdateStatus importStatus = cveClient.fullUpdate();
            JSONObject responseData = PortletUtils.importStatusToJSON(importStatus);
            PrintWriter writer = response.getWriter();
            writer.write(responseData.toString());
        } catch (TException e) {
            log.error("Error occurred with full update of CVEs in backend.", e);
        }
    }

    private void updateVulnerabilityVerification(ResourceRequest request, ResourceResponse response) throws IOException {
        String[] releaseIds = request.getParameterValues(PortalConstants.RELEASE_IDS + "[]");
        String[] vulnerabilityIds = request.getParameterValues(PortalConstants.VULNERABILITY_IDS + "[]");

        User user = UserCacheHolder.getUserFromRequest(request);
        VulnerabilityService.Iface vulClient = thriftClients.makeVulnerabilityClient();

        RequestStatus requestStatus = RequestStatus.SUCCESS;
        try {
            if (vulnerabilityIds.length != releaseIds.length) {
                throw new SW360Exception("Length of vulnerabilities (" + vulnerabilityIds.length + ") does not match the length of releases (" + releaseIds.length + ")!");
            }

            for (int i = 0; i < vulnerabilityIds.length; i++) {
                String vulnerabilityId = vulnerabilityIds[i];
                String releaseId = releaseIds[i];

                Vulnerability dbVulnerability = vulClient.getVulnerabilityByExternalId(vulnerabilityId, user);
                ReleaseVulnerabilityRelation dbRelation = vulClient.getRelationByIds(releaseId, dbVulnerability.getId(), user);
                ReleaseVulnerabilityRelation resultRelation = ComponentPortletUtils.updateReleaseVulnerabilityRelationFromRequest(dbRelation, request);
                requestStatus = vulClient.updateReleaseVulnerabilityRelation(resultRelation, user);

                if (requestStatus != RequestStatus.SUCCESS) {
                    break;
                }
            }
        } catch (TException e) {
            log.error("Error updating vulnerability verification in backend.", e);
            requestStatus = RequestStatus.FAILURE;
        }

        JSONObject responseData = JSONFactoryUtil.createJSONObject();
        responseData.put(PortalConstants.REQUEST_STATUS, requestStatus.toString());
        PrintWriter writer = response.getWriter();
        writer.write(responseData.toString());
    }

    private void serveComponentList(ResourceRequest request, ResourceResponse response) throws PortletException {
        HttpServletRequest originalServletRequest = PortalUtil.getOriginalServletRequest(PortalUtil.getHttpServletRequest(request));
        PaginationParameters paginationParameters = PaginationParser.parametersFrom(originalServletRequest);
        handlePaginationSortOrder(request, paginationParameters);
        PaginationData pageData = new PaginationData();
        pageData.setRowsPerPage(paginationParameters.getDisplayLength());
        pageData.setDisplayStart(paginationParameters.getDisplayStart());
        pageData.setAscending(paginationParameters.isAscending().get());
        int sortParam = -1;
        if (paginationParameters.getSortingColumn().isPresent()) {
            sortParam = paginationParameters.getSortingColumn().get();
            if (sortParam == 1 && Integer.valueOf(paginationParameters.getEcho()) == 1) {
                pageData.setSortColumnNumber(-1);
            }
        } else {
            pageData.setSortColumnNumber(sortParam);
        }

        Map<PaginationData, List<Component>> pageDataComponentList = getFilteredComponentList(request, pageData);

        JSONArray jsonComponents = getComponentData(pageDataComponentList.values().iterator().next(), paginationParameters);
        JSONObject jsonResult = createJSONObject();
        jsonResult.put(DATATABLE_RECORDS_TOTAL, pageDataComponentList.keySet().iterator().next().getTotalRowCount());
        jsonResult.put(DATATABLE_RECORDS_FILTERED, pageDataComponentList.keySet().iterator().next().getTotalRowCount());
        jsonResult.put(DATATABLE_DISPLAY_DATA, jsonComponents);

        try {
            writeJSON(request, response, jsonResult);
        } catch (IOException e) {
            log.error("Problem rendering RequestStatus", e);
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE, "500");
        }
    }

    private Map<PaginationData, List<Component>> getFilteredComponentList(PortletRequest request, PaginationData pageData) {
        Map<String, Set<String>> filterMap = getComponentFilterMap(request);
        List<Component> componentList;
        Map<PaginationData, List<Component>> pageDataComponents = Maps.newHashMap();

        try {
            final User user = UserCacheHolder.getUserFromRequest(request);
            ComponentService.Iface componentClient = thriftClients.makeComponentClient();
            if (filterMap.isEmpty()) {
                pageDataComponents = componentClient.getRecentComponentsSummaryWithPagination(user, pageData);
            } else {
                componentList = componentClient.refineSearch(null, filterMap);
                pageDataComponents.put(pageData.setTotalRowCount(componentList.size()), componentList);
            }
        } catch (TException e) {
            log.error("Could not search components in backend ", e);
            pageDataComponents = Collections.emptyMap();
        }

        return pageDataComponents;
    }


    private void linkReleaseToProject(ResourceRequest request, ResourceResponse response) throws IOException {
        User user = UserCacheHolder.getUserFromRequest(request);
        String projectId = request.getParameter(PortalConstants.PROJECT_ID);
        String releaseId = request.getParameter(PortalConstants.RELEASE_ID);


        try {
            log.debug("Link release [" + releaseId + "] to project [" + projectId + "]");

            ProjectService.Iface client = thriftClients.makeProjectClient();
            Project project = client.getProjectByIdForEdit(projectId, user);

            project.putToReleaseIdToUsage(releaseId,
                    new ProjectReleaseRelationship(ReleaseRelationship.CONTAINED, MainlineState.OPEN));
            client.updateProject(project, user);

            JSONObject jsonObject = JSONFactoryUtil.createJSONObject();
            jsonObject.put("success", true);
            jsonObject.put("releaseId", releaseId);
            jsonObject.put("projectId", projectId);
            writeJSON(request, response, jsonObject);
        } catch (TException exception) {
            log.error("Cannot link release [" + releaseId + "] to project [" + projectId + "].");
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE, "500");
        }
    }

    private void handlePaginationSortOrder(ResourceRequest request, PaginationParameters paginationParameters) {
        if (!paginationParameters.getSortingColumn().isPresent()) {
            for (Component._Fields filteredField : componentFilteredFields) {
                if (!isNullOrEmpty(request.getParameter(filteredField.toString()))) {
                    paginationParameters.setSortingColumn(Optional.of(COMPONENT_NO_SORT));
                    break;
                }
            }
        }
    }

    public JSONArray getComponentData(List<Component> componentList, PaginationParameters componentParameters) {
        List<Component> sortedComponents = sortComponentList(componentList, componentParameters);
        int count = getComponentDataCount(componentParameters, componentList.size());
        VendorService.Iface vendorClient = thriftClients.makeVendorClient();

        JSONArray componentData = createJSONArray();
        for (int i = 0; i < count; i++) {
            JSONObject jsonObject = JSONFactoryUtil.createJSONObject();
            Component comp = sortedComponents.get(i);
            jsonObject.put("id", comp.getId());
            jsonObject.put("DT_RowId", comp.getId());
            jsonObject.put("name", SW360Utils.printName(comp));
            jsonObject.put("cType", nullToEmptyString(comp.getComponentType()));
            jsonObject.put("lRelsSize", String.valueOf(comp.getReleaseIdsSize()));
            jsonObject.put("attsSize", String.valueOf(comp.getAttachmentsSize()));

            JSONArray vendorArray = createJSONArray();
            Set<String> vendorNames = new HashSet<>();
            if (comp.isSetDefaultVendorId()) {
                Vendor defaultVendor = null;
                try {
                    if(!isNullOrEmpty(comp.getDefaultVendorId())) {
                        defaultVendor = vendorClient.getByID(comp.getDefaultVendorId());
                    }
                } catch (TException e) {
                    log.error("Could not get vendor for id [" + comp.getDefaultVendorId() + "] in component with id ["
                            + comp.getId() + "] because of: ", e);
                }
                if (defaultVendor != null) {
                    vendorNames.add(defaultVendor.getShortname());
                }
            }
            if (comp.isSetVendorNames()) {
                vendorNames.addAll(comp.getVendorNames());
            }
            vendorNames.stream().sorted().forEach(vendorArray::put);
            jsonObject.put("vndrs", vendorArray);

            JSONArray licenseArray = createJSONArray();
            if (comp.isSetMainLicenseIds()) {
                comp.getMainLicenseIds().stream().sorted().forEach(licenseArray::put);
            }
            jsonObject.put("lics", licenseArray);

            componentData.put(jsonObject);
        }

        return componentData;
    }

    private int getComponentDataCount(PaginationParameters componentParameters, int maxSize) {
        if (componentParameters.getDisplayLength() == -1) {
            return maxSize;
        } else {
            return min(componentParameters.getDisplayStart() + componentParameters.getDisplayLength(), maxSize);
        }
    }

    private List<Component> sortComponentList(List<Component> componentList, PaginationParameters componentParameters) {
        boolean isAsc = componentParameters.isAscending().orElse(true);

        switch (componentParameters.getSortingColumn().orElse(COMPONENT_DT_ROW_NAME)) {
            case COMPONENT_DT_ROW_VENDOR:
                Collections.sort(componentList, compareByVendor(isAsc));
                break;
            case COMPONENT_DT_ROW_NAME:
                Collections.sort(componentList, compareByName(isAsc));
                break;
            case COMPONENT_DT_ROW_MAIN_LICENSES:
                Collections.sort(componentList, compareByMainLicenses(isAsc));
                break;
            case COMPONENT_DT_ROW_TYPE:
                Collections.sort(componentList, compareByComponentType(isAsc));
                break;
            case COMPONENT_DT_ROW_ACTION:
                Collections.sort(componentList, compareById(isAsc));
                break;
            default:
                break;
        }

        return componentList;
    }

    private Comparator<Component> compareByVendor(boolean isAscending) {
        Comparator<Component> comparator = Comparator.comparing(
                c -> sortAndConcat(c.getVendorNames()));
        return isAscending ? comparator : comparator.reversed();
    }

    private Comparator<Component> compareByName(boolean isAscending) {
        Comparator<Component> comparator = Comparator.comparing(
                c -> SW360Utils.printName(c).toLowerCase());
        return isAscending ? comparator : comparator.reversed();
    }

    private Comparator<Component> compareByMainLicenses(boolean isAscending) {
        Comparator<Component> comparator = Comparator.comparing(
                c -> sortAndConcat(c.getMainLicenseIds()));
        return isAscending ? comparator : comparator.reversed();
    }

    private Comparator<Component> compareByComponentType(boolean isAscending) {
        Comparator<Component> comparator = Comparator.comparing(
                c -> nullToEmptyString(c.getComponentType()));
        return isAscending ? comparator : comparator.reversed();
    }

    private Comparator<Component> compareById(boolean isAscending) {
        Comparator<Component> comparator = Comparator.comparing(c -> c.getId());
        return isAscending ? comparator : comparator.reversed();
    }

    private String sortAndConcat(Set<String> strings) {
        if (strings == null || strings.isEmpty()) {
            return "";
        } else {
            return CommonUtils.COMMA_JOINER.join(strings.stream().sorted().collect(Collectors.toList()));
        }
    }
}
