/*
 * Copyright Siemens AG, 2013-2015. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.portal.portlets.admin;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.liferay.portal.kernel.portlet.LiferayPortletURL;
import com.liferay.portal.kernel.portlet.PortletResponseUtil;
import com.liferay.portal.kernel.portlet.PortletURLFactoryUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.WebKeys;

import org.eclipse.sw360.datahandler.common.SW360Utils;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.SW360Exception;
import org.eclipse.sw360.datahandler.thrift.components.Component;
import org.eclipse.sw360.datahandler.thrift.components.ComponentService;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.eclipse.sw360.datahandler.thrift.vendors.VendorService;
import org.eclipse.sw360.exporter.VendorExporter;
import org.eclipse.sw360.portal.common.PortalConstants;
import org.eclipse.sw360.portal.common.UsedAsLiferayAction;
import org.eclipse.sw360.portal.portlets.Sw360Portlet;
import org.eclipse.sw360.portal.portlets.components.ComponentPortletUtils;
import org.eclipse.sw360.portal.users.UserCacheHolder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.portlet.*;
import javax.servlet.http.HttpServletResponse;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.eclipse.sw360.datahandler.common.SW360Constants.CONTENT_TYPE_OPENXML_SPREADSHEET;
import static org.eclipse.sw360.portal.common.PortalConstants.*;

@org.osgi.service.component.annotations.Component(
    immediate = true,
    properties = {
            "/org/eclipse/sw360/portal/portlets/base.properties",
            "/org/eclipse/sw360/portal/portlets/admin.properties"
    },
    property = {
        "javax.portlet.name=" + VENDOR_PORTLET_NAME,

        "javax.portlet.display-name=Vendor Administration",
        "javax.portlet.info.short-title=Vendors",
        "javax.portlet.info.title=Vendor Administration",
        "javax.portlet.resource-bundle=content.Language",
        "javax.portlet.init-param.view-template=/html/admin/vendors/view.jsp",
    },
    service = Portlet.class,
    configurationPolicy = ConfigurationPolicy.REQUIRE
)
public class VendorPortlet extends Sw360Portlet {

    private static final Logger log = LogManager.getLogger(VendorPortlet.class);

    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final TSerializer JSON_THRIFT_SERIALIZER = getJsonSerializer();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Excel exporter
     */
    private final VendorExporter exporter = new VendorExporter();

    //! Serve resource and helpers
    @Override
    public void serveResource(ResourceRequest request, ResourceResponse response) throws IOException, PortletException {
        String action = request.getParameter(ACTION);

        if (EXPORT_TO_EXCEL.equals(action)) {
            exportExcel(request, response);
        } else if (REMOVE_VENDOR.equals(action)) {
            removeVendor(request, response);
        }
    }

    private void removeVendor(PortletRequest request, ResourceResponse response) throws IOException {
        final RequestStatus requestStatus = ComponentPortletUtils.deleteVendor(request, log);
        serveRequestStatus(request, response, requestStatus, "Problem removing vendor", log);

    }

    private void exportExcel(ResourceRequest request, ResourceResponse response) {
        try {
            VendorService.Iface client = thriftClients.makeVendorClient();
            List<Vendor> vendors = client.getAllVendors();
            String filename = String.format("vendors-%s.xlsx", SW360Utils.getCreatedOn());
            PortletResponseUtil.sendFile(request, response, filename, exporter.makeExcelExport(vendors), CONTENT_TYPE_OPENXML_SPREADSHEET);
        } catch (IOException | TException e) {
            log.error("An error occurred while generating the Excel export", e);
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE,
                    Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        }
    }

    //! VIEW and helpers
    @Override
    public void doView(RenderRequest request, RenderResponse response) throws IOException, PortletException {
        String pageName = request.getParameter(PAGENAME);
        if (PAGENAME_EDIT.equals(pageName)) {
            prepareVendorEdit(request);
            include("/html/admin/vendors/edit.jsp", request, response);
        }  else if (PAGENAME_MERGE_VENDOR.equals(pageName)) {
            prepareVendorMerge(request, response);
            include("/html/admin/vendors/mergeVendor.jsp", request, response);
        } else {
            prepareStandardView(request);
            super.doView(request, response);
        }
    }

    private void prepareVendorEdit(RenderRequest request) throws PortletException {
        String id = request.getParameter(VENDOR_ID);

        if (!isNullOrEmpty(id)) {
            try {
                VendorService.Iface vendorClient = thriftClients.makeVendorClient();
                Vendor vendor = vendorClient.getByID(id);
                request.setAttribute(VENDOR, vendor);


                final ComponentService.Iface componentClient = thriftClients.makeComponentClient();
                final List<Release> releasesFromVendorIds = componentClient.getReleasesFromVendorIds(Sets.newHashSet(id));

                request.setAttribute(RELEASE_LIST, releasesFromVendorIds);
            } catch (TException e) {
                log.error("Problem retrieving vendor");
            }
        }
        else{
            request.setAttribute(RELEASE_LIST, Collections.emptyList());
        }
    }

    private void prepareVendorMerge(RenderRequest request, RenderResponse response) throws PortletException {
        String vendorId = request.getParameter(VENDOR_ID);

        if (isNullOrEmpty(vendorId)) {
            throw new PortletException("Vendor ID not set!");
        }

        try {
            VendorService.Iface client = thriftClients.makeVendorClient();
            Vendor vendor = client.getByID(vendorId);
            request.setAttribute(VENDOR, vendor);
            addVendorBreadcrumb(request, response, vendor);

            PortletURL mergeUrl = response.createRenderURL();
            mergeUrl.setParameter(PortalConstants.PAGENAME, PortalConstants.PAGENAME_MERGE_VENDOR);
            mergeUrl.setParameter(PortalConstants.VENDOR_ID, vendorId);
            addBreadcrumbEntry(request, "Merge", mergeUrl);
        } catch (TException e) {
            log.error("Error fetching release from backend!", e);
        }
    }

    private void prepareStandardView(RenderRequest request) throws IOException {
        List<Vendor> vendorList;
        try {
            final User user = UserCacheHolder.getUserFromRequest(request);
            VendorService.Iface vendorClient = thriftClients.makeVendorClient();

            vendorList = vendorClient.getAllVendors();

        } catch (TException e) {
            log.error("Could not get Vendors from backend ", e);
            vendorList = Collections.emptyList();
        }

        request.setAttribute(VENDOR_LIST, vendorList);
    }

    @UsedAsLiferayAction
    public void updateVendor(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        String id = request.getParameter(VENDOR_ID);
        final User user = UserCacheHolder.getUserFromRequest(request);

        if (id != null) {
            try {
                VendorService.Iface vendorClient = thriftClients.makeVendorClient();
                Vendor vendor = vendorClient.getByID(id);
                ComponentPortletUtils.updateVendorFromRequest(request, vendor);
                RequestStatus requestStatus = vendorClient.updateVendor(vendor, user);
                setSessionMessage(request, requestStatus, "Vendor", "update", vendor.getShortname());
            } catch (TException e) {
                log.error("Error fetching vendor from backend!", e);
            }
        }
        else{
            addVendor(request);
        }
    }

    @UsedAsLiferayAction
    public void removeVendor(ActionRequest request, ActionResponse response) throws IOException, PortletException {
        final RequestStatus requestStatus = ComponentPortletUtils.deleteVendor(request, log);
        setSessionMessage(request, requestStatus, "Vendor", "delete");
        response.setRenderParameter(PAGENAME, PAGENAME_VIEW);
    }

    private void addVendor(ActionRequest request)  {
        final Vendor vendor = new Vendor();
        ComponentPortletUtils.updateVendorFromRequest(request, vendor);

        try {
            VendorService.Iface vendorClient = thriftClients.makeVendorClient();
            String vendorId = vendorClient.addVendor(vendor);
        } catch (TException e) {
            log.error("Error adding vendor", e);
        }
    }

    private void addVendorBreadcrumb(RenderRequest request, RenderResponse response, Vendor vendor) {
        PortletURL vendorUrl = response.createRenderURL();
        vendorUrl.setParameter(PAGENAME, PAGENAME_VIEW);

        PortalUtil.addPortletBreadcrumbEntry(PortalUtil.getOriginalServletRequest(PortalUtil.getHttpServletRequest(request)),
            vendor.getFullname(), vendorUrl.toString());
    }

    @UsedAsLiferayAction
    public void vendorMergeWizardStep(ActionRequest request, ActionResponse response) throws IOException, PortletException {
        int stepId = Integer.parseInt(request.getParameter("stepId"));
        try {
            HttpServletResponse httpServletResponse = PortalUtil.getHttpServletResponse(response);
            httpServletResponse.setContentType(ContentTypes.APPLICATION_JSON);
            JsonGenerator jsonGenerator = JSON_FACTORY.createGenerator(httpServletResponse.getWriter());

            switch(stepId) {
                case 0:
                    generateSourceVendorsForMergeForWizardStep0(request, jsonGenerator);
                    break;
                case 1:
                    generateCompareEditorForWizardStep1(request, jsonGenerator);
                    break;
                case 2:
                    generateResultPreviewForWizardStep2(request, jsonGenerator);
                    break;
                case 3:
                    mergeVendorsForWizardStep3(request, jsonGenerator);
                    break;
                default:
                throw new SW360Exception("Step with id <" + stepId + "> not supported!");
            }

            jsonGenerator.close();
        } catch (Exception e) {
            log.error("An error occurred while generating a response to vendor merge wizard", e);
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE,
                    Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        }
    }

    private void generateSourceVendorsForMergeForWizardStep0(ActionRequest request, JsonGenerator jsonGenerator) throws IOException, TException {
        String targetId = request.getParameter(VENDOR_TARGET_ID);
        VendorService.Iface client = thriftClients.makeVendorClient();
        List<Vendor> vendors = client.getAllVendors();

        jsonGenerator.writeStartObject();

        jsonGenerator.writeArrayFieldStart("vendors");
        vendors.stream().filter(vendor -> !vendor.getId().equals(targetId)).forEach(vendor -> {
            try {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("id", vendor.getId());
                jsonGenerator.writeStringField("fullname", vendor.getFullname());
                jsonGenerator.writeStringField("shortname", vendor.getShortname());
                jsonGenerator.writeStringField("url", vendor.getUrl());
                jsonGenerator.writeEndObject();
            } catch (IOException e) {
                log.error("An error occurred while generating wizard response", e);
            }
        });
        jsonGenerator.writeEndArray();

        jsonGenerator.writeEndObject();
    }

    private void generateCompareEditorForWizardStep1(ActionRequest request, JsonGenerator jsonGenerator) throws IOException, TException  {
        String vendorTargetId = request.getParameter(VENDOR_TARGET_ID);
        String vendorSourceId = request.getParameter(VENDOR_SOURCE_ID);
        VendorService.Iface client = thriftClients.makeVendorClient();
        Vendor vendorTarget = client.getByID(vendorTargetId);
        Vendor vendorSource = client.getByID(vendorSourceId);

        User user = UserCacheHolder.getUserFromRequest(request);
        ComponentService.Iface componentClient = thriftClients.makeComponentClient();
        Set<Component> components = componentClient.getComponentsByDefaultVendorId(vendorSourceId);
        Set<Release> releases = componentClient.getReleasesByVendorId(vendorSourceId);

        jsonGenerator.writeStartObject();
        jsonGenerator.writeRaw("\"vendorTarget\":" + JSON_THRIFT_SERIALIZER.toString(vendorTarget) + ",");
        jsonGenerator.writeRaw("\"vendorSource\":" + JSON_THRIFT_SERIALIZER.toString(vendorSource) + ",");
        jsonGenerator.writeNumberField("affectedComponents",  components.size());
        jsonGenerator.writeNumberField("affectedReleases",  releases.size());
        jsonGenerator.writeEndObject();
    }

    private void generateResultPreviewForWizardStep2(ActionRequest request, JsonGenerator jsonGenerator)
            throws IOException, TException {
        Vendor vendorSelection = OBJECT_MAPPER.readValue(request.getParameter(VENDOR_SELECTION), Vendor.class);
        String vendorSourceId = request.getParameter(VENDOR_SOURCE_ID);
        jsonGenerator.writeStartObject();

        // adding common title
        jsonGenerator.writeRaw("\""+ VENDOR_SELECTION +"\":" + JSON_THRIFT_SERIALIZER.toString(vendorSelection) + ",");
        jsonGenerator.writeStringField(VENDOR_SOURCE_ID, vendorSourceId);

        jsonGenerator.writeEndObject();
    }

    private void mergeVendorsForWizardStep3(ActionRequest request, JsonGenerator jsonGenerator)
            throws IOException, TException {
        VendorService.Iface vendorClient = thriftClients.makeVendorClient();

        // extract request data
        Vendor vendorSelection = OBJECT_MAPPER.readValue(request.getParameter(VENDOR_SELECTION), Vendor.class);
        String vendorSourceId = request.getParameter(VENDOR_SOURCE_ID);

        // perform the real merge, update merge target and delete merge source
        User sessionUser = UserCacheHolder.getUserFromRequest(request);
        RequestStatus status = vendorClient.mergeVendors(vendorSelection.getId(), vendorSourceId, vendorSelection, sessionUser);
        
        // write response JSON
        jsonGenerator.writeStartObject();

        jsonGenerator.writeStringField("redirectUrl", createViewUrl(request).toString());
        if (status == RequestStatus.IN_USE){
            jsonGenerator.writeStringField("error", "Cannot merge when one of the vendors has an active moderation request.");
        } else if (status == RequestStatus.ACCESS_DENIED) {
            jsonGenerator.writeStringField("error", "You do not have sufficient permissions.");
        } else if (status == RequestStatus.FAILURE) {
            jsonGenerator.writeStringField("error", "An unknown error occurred during merge.");
        }

        jsonGenerator.writeEndObject();
    }


    private LiferayPortletURL createViewUrl(PortletRequest request) {
        String portletId = (String) request.getAttribute(WebKeys.PORTLET_ID);
        ThemeDisplay tD = (ThemeDisplay) request.getAttribute(WebKeys.THEME_DISPLAY);
        long plid = tD.getPlid();

        LiferayPortletURL vendorUrl = PortletURLFactoryUtil.create(request, portletId, plid,
                PortletRequest.RENDER_PHASE);
        vendorUrl.setParameter(PortalConstants.PAGENAME, PortalConstants.PAGENAME_VIEW);

        return vendorUrl;
    }
}
