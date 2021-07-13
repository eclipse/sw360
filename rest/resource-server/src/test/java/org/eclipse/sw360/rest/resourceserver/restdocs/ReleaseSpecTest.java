/*
 * Copyright Siemens AG, 2017-2018. Part of the SW360 Portal Project.
 *
  * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.rest.resourceserver.restdocs;

import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.thrift.Source;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentType;
import org.eclipse.sw360.datahandler.thrift.attachments.CheckStatus;
import org.eclipse.sw360.rest.resourceserver.attachment.AttachmentInfo;
import org.eclipse.sw360.datahandler.thrift.MainlineState;
import org.eclipse.sw360.datahandler.thrift.ReleaseRelationship;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentContent;
import org.eclipse.sw360.datahandler.thrift.components.COTSDetails;
import org.eclipse.sw360.datahandler.thrift.components.ClearingInformation;
import org.eclipse.sw360.datahandler.thrift.components.ClearingState;
import org.eclipse.sw360.datahandler.thrift.components.Component;
import org.eclipse.sw360.datahandler.thrift.components.ComponentType;
import org.eclipse.sw360.datahandler.thrift.components.ExternalTool;
import org.eclipse.sw360.datahandler.thrift.components.ExternalToolProcess;
import org.eclipse.sw360.datahandler.thrift.components.ExternalToolProcessStatus;
import org.eclipse.sw360.datahandler.thrift.components.ExternalToolProcessStep;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.licenses.License;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectType;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.eclipse.sw360.rest.resourceserver.TestHelper;
import org.eclipse.sw360.rest.resourceserver.attachment.Sw360AttachmentService;
import org.eclipse.sw360.rest.resourceserver.license.Sw360LicenseService;
import org.eclipse.sw360.rest.resourceserver.release.Sw360ReleaseService;
import org.eclipse.sw360.rest.resourceserver.user.Sw360UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.linkWithRel;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.links;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.requestParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
public class ReleaseSpecTest extends TestRestDocsSpecBase {

    @Value("${sw360.test-user-id}")
    private String testUserId;

    @Value("${sw360.test-user-password}")
    private String testUserPassword;

    @MockBean
    private Sw360UserService userServiceMock;

    @MockBean
    private Sw360ReleaseService releaseServiceMock;

    @MockBean
    private Sw360AttachmentService attachmentServiceMock;

    @MockBean
    private Sw360LicenseService licenseServiceMock;

    private Release release, release3;
    private Attachment attachment;
    Component component;
    private Project project;

    private final String releaseId = "3765276512";
    private final String attachmentSha1 = "da373e491d3863477568896089ee9457bc316783";

    @Before
    public void before() throws TException, IOException {
        Set<Attachment> attachments = new HashSet<>();
        Set<Component> usedByComponent = new HashSet<>();
        List<Resource<Attachment>> attachmentResources = new ArrayList<>();
        attachment = new Attachment("1231231254", "spring-core-4.3.4.RELEASE.jar");
        attachment.setSha1(attachmentSha1);
        attachments.add(attachment);
        attachmentResources.add(new Resource<>(attachment));

        attachment.setSha1(attachmentSha1);
        attachment.setAttachmentType(AttachmentType.BINARY_SELF);
        attachment.setCreatedTeam("Clearing Team 1");
        attachment.setCreatedComment("please check asap");
        attachment.setCreatedOn("2016-12-18");
        attachment.setCreatedBy("admin@sw360.org");
        attachment.setCheckedTeam("Clearing Team 2");
        attachment.setCheckedComment("everything looks good");
        attachment.setCheckedOn("2016-12-18");
        attachment.setCheckStatus(CheckStatus.ACCEPTED);
        attachments.add(attachment);
        AttachmentInfo attachmentInfo = new AttachmentInfo(attachment);
        List<AttachmentInfo> attachmentInfos = new ArrayList<>();
        attachmentInfos.add(attachmentInfo);

        Set<Attachment> setOfAttachment = new HashSet<Attachment>();
        Attachment att1 = new Attachment("1234", "test.zip").setAttachmentType(AttachmentType.SOURCE)
                .setCreatedBy("user@sw360.org").setSha1("da373e491d312365483589ee9457bc316783").setCreatedOn("2021-04-27")
                .setCreatedTeam("DEPARTMENT");
        Attachment att2 = att1.deepCopy().setAttachmentType(AttachmentType.BINARY).setCreatedComment("Created Comment")
                .setCheckStatus(CheckStatus.ACCEPTED).setCheckedComment("Checked Comment").setCheckedOn("2021-04-27")
                .setCheckedBy("admin@sw360.org").setCheckedTeam("DEPARTMENT1");

        Source owner = new Source();
        attachmentInfo.setOwner(owner);

        given(this.attachmentServiceMock.getAttachmentById(eq(attachment.getAttachmentContentId())))
                .willReturn(attachmentInfo);
        given(this.attachmentServiceMock.getAttachmentsBySha1(eq(attachment.getSha1()))).willReturn(attachmentInfos);
        given(this.attachmentServiceMock.getAttachmentContent(anyObject())).willReturn(new AttachmentContent().setId("1231231254").setFilename("spring-core-4.3.4.RELEASE.jar").setContentType("binary"));
        given(this.attachmentServiceMock.getResourcesFromList(anyObject())).willReturn(new Resources<>(attachmentResources));
        given(this.attachmentServiceMock.uploadAttachment(anyObject(), anyObject(), anyObject())).willReturn(attachment);
        given(this.attachmentServiceMock.filterAttachmentsToRemove(any(), any(), any())).willReturn(Collections.singleton(attachment));
        given(this.attachmentServiceMock.updateAttachment(anyObject(), anyObject(), anyObject(), anyObject())).willReturn(att2);

        Map<String, Set<String>> externalIds = new HashMap<>();
        externalIds.put("mainline-id-component", new HashSet<>(Arrays.asList("1432", "4876")));
        COTSDetails cotsDetails = new COTSDetails().setClearingDeadline("2016-12-18").setContainsOSS(true)
                .setCotsResponsible("admin").setLicenseClearingReportURL("http://licenseclearingreporturl.com")
                .setOssInformationURL("http://ossinformationurl.com").setUsedLicense("MIT");
        Map<String, ReleaseRelationship> releaseIdToRelationship = ImmutableMap.of("90876",
                ReleaseRelationship.DYNAMICALLY_LINKED);
        ClearingInformation clearingInfo = new ClearingInformation().setComment("Approved").setEvaluated("yes");

        component = new Component();
        component.setId("17653524");
        component.setName("Angular");
        component.setDescription("Angular is a development platform for building mobile and desktop web applications.");
        component.setCreatedOn("2016-12-15");
        component.setCreatedBy("admin@sw360.org");
        component.setComponentType(ComponentType.OSS);
        component.setVendorNames(new HashSet<>(Collections.singletonList("Google")));
        component.setModerators(new HashSet<>(Arrays.asList("admin@sw360.org", "john@sw360.org")));
        usedByComponent.add(component);

        Release testRelease = new Release().setAttachments(setOfAttachment).setId("98745").setName("Test Release")
                .setVersion("2").setComponentId("17653524").setCreatedOn("2021-04-27").setCreatedBy("admin@sw360.org");

        List<Release> releaseList = new ArrayList<>();
        release = new Release();
        Map<String, String> releaseExternalIds = new HashMap<>();
        releaseExternalIds.put("mainline-id-component", "1432");
        releaseExternalIds.put("ws-component-id", "[\"2365\",\"5487923\"]");

        release.setId(releaseId);
        owner.setReleaseId(release.getId());
        release.setName("Spring Core 4.3.4");
        release.setCpeid("cpe:/a:pivotal:spring-core:4.3.4:");
        release.setReleaseDate("2016-12-07");
        release.setVersion("4.3.4");
        release.setCreatedOn("2016-12-18");
        release.setCreatedBy("admin@sw360.org");
        release.setModerators(new HashSet<>(Arrays.asList("admin@sw360.org", "jane@sw360.org")));
        release.setCreatedBy("admin@sw360.org");
        release.setSourceCodeDownloadurl("http://www.google.com");
        release.setBinaryDownloadurl("http://www.google.com/binaries");
        release.setComponentId(component.getId());
        release.setClearingState(ClearingState.APPROVED);
        release.setMainlineState(MainlineState.SPECIFIC);
        release.setExternalIds(releaseExternalIds);
        release.setAdditionalData(Collections.singletonMap("Key", "Value"));
        release.setAttachments(attachments);
        release.setLanguages(new HashSet<>(Arrays.asList("C++", "Java")));
        release.setMainLicenseIds(new HashSet<>(Arrays.asList("GPL-2.0-or-later", "Apache-2.0")));
        release.setOperatingSystems(ImmutableSet.of("Windows", "Linux"));
        release.setSoftwarePlatforms(new HashSet<>(Arrays.asList("Java SE", ".NET")));
        releaseList.add(release);

        Release release2 = new Release();
        Map<String, String> release2ExternalIds = new HashMap<>();
        release2ExternalIds.put("mainline-id-component", "4876");
        release2ExternalIds.put("ws-component-id", "[\"589211\",\"987135\"]");
        release2.setId("3765276512");
        release2.setName("Angular");
        release2.setCpeid("cpe:/a:Google:Angular:2.3.1:");
        release2.setReleaseDate("2016-12-15");
        release2.setVersion("2.3.1");
        release2.setCreatedOn("2016-12-18");
        release2.setCreatedBy("admin@sw360.org");
        release2.setSourceCodeDownloadurl("http://www.google.com");
        release2.setBinaryDownloadurl("http://www.google.com/binaries");
        release2.setModerators(new HashSet<>(Arrays.asList("admin@sw360.org", "jane@sw360.org")));
        release2.setComponentId(component.getId());
        release2.setClearingState(ClearingState.APPROVED);
        release2.setMainlineState(MainlineState.MAINLINE);
        release2.setExternalIds(release2ExternalIds);
        release2.setLanguages(new HashSet<>(Arrays.asList("C++", "Java")));
        release2.setOperatingSystems(ImmutableSet.of("Windows", "Linux"));
        release2.setSoftwarePlatforms(new HashSet<>(Arrays.asList("Java SE", ".NET")));
        release2.setContributors(new HashSet<>(Arrays.asList("admin@sw360.org", "jane@sw360.org")));
        release2.setVendor(new Vendor("TV", "Test Vendor", "http://testvendor.com"));
        release2.setReleaseIdToRelationship(releaseIdToRelationship);
        release2.setClearingInformation(clearingInfo);
        release2.setCotsDetails(cotsDetails);
        releaseList.add(release2);

        release3 = new Release();
        release3.setId("987456");
        release3.setName("Angular");
        release3.setVersion("2.3.1");
        release3.setCreatedOn("2016-12-18");
        release3.setCreatedBy("admin@sw360.org");
        release3.setComponentId("1234");
        Attachment attachment3 = new Attachment(attachment);
        attachment3.setAttachmentContentId("34535345");
        attachment3.setAttachmentType(AttachmentType.SOURCE);
        release3.setAttachments(ImmutableSet.of(attachment3));

        Set<Project> projectList = new HashSet<>();
        project = new Project();
        project.setId("376576");
        project.setName("Emerald Web");
        project.setProjectType(ProjectType.PRODUCT);
        project.setVersion("1.0.2");
        projectList.add(project);

        given(this.releaseServiceMock.getReleasesForUser(anyObject())).willReturn(releaseList);
        given(this.releaseServiceMock.getReleaseForUserById(eq(release.getId()), anyObject())).willReturn(release);
        given(this.releaseServiceMock.getReleaseForUserById(eq(testRelease.getId()), anyObject())).willReturn(testRelease);
        given(this.releaseServiceMock.getProjectsByRelease(eq(release.getId()), anyObject())).willReturn(projectList);
        given(this.releaseServiceMock.getUsingComponentsForRelease(eq(release.getId()), anyObject())).willReturn(usedByComponent);
        given(this.releaseServiceMock.deleteRelease(eq(release.getId()), anyObject())).willReturn(RequestStatus.SUCCESS);
        given(this.releaseServiceMock.searchByExternalIds(eq(externalIds), anyObject())).willReturn((new HashSet<>(releaseList)));
        given(this.releaseServiceMock.convertToEmbeddedWithExternalIds(eq(release))).willReturn(
                new Release("Angular", "2.3.0", component.getId())
                        .setId(releaseId)
                        .setExternalIds(Collections.singletonMap("mainline-id-component", "1432")));
        given(this.releaseServiceMock.convertToEmbeddedWithExternalIds(eq(release2))).willReturn(
                new Release("Angular", "2.3.1", component.getId())
                        .setId("3765276512")
                        .setExternalIds(Collections.singletonMap("mainline-id-component", "4876")));
        when(this.releaseServiceMock.createRelease(anyObject(), anyObject())).then(invocation ->
                new Release("Test Release", "1.0", component.getId())
                        .setId("1234567890"));

        given(this.userServiceMock.getUserByEmailOrExternalId("admin@sw360.org")).willReturn(
                new User("admin@sw360.org", "sw360").setId("123456789"));
        given(this.userServiceMock.getUserByEmail("admin@sw360.org")).willReturn(
                new User("admin@sw360.org", "sw360").setId("123456789"));
        given(this.userServiceMock.getUserByEmail("jane@sw360.org")).willReturn(
                new User("jane@sw360.org", "sw360").setId("209582812"));
        given(this.licenseServiceMock.getLicenseById("Apache-2.0")).willReturn(
                new License("Apache 2.0 License")
                        .setText("Dummy License Text")
                        .setShortname("Apache-2.0")
                        .setId(UUID.randomUUID().toString()));
        given(this.licenseServiceMock.getLicenseById("GPL-2.0-or-later")).willReturn(
                new License("GNU General Public License 2.0").setText("GNU General Public License 2.0 Text")
                        .setShortname("GPL-2.0-or-later")
                        .setId(UUID.randomUUID().toString()));

        ExternalToolProcess fossologyProcess = new ExternalToolProcess();
        fossologyProcess.setAttachmentId("5345ab789");
        fossologyProcess.setAttachmentHash("535434657567");
        fossologyProcess.setExternalTool(ExternalTool.FOSSOLOGY);
        fossologyProcess.setProcessStatus(ExternalToolProcessStatus.DONE);
        List<ExternalToolProcessStep> processSteps = new ArrayList<>();
        ExternalToolProcessStep uploadStep = new ExternalToolProcessStep();
        uploadStep.setStepName("01_upload");
        uploadStep.setProcessStepIdInTool("2");
        uploadStep.setResult("12");
        uploadStep.setStartedBy("abc@sw360.org");
        uploadStep.setStartedByGroup("DEPARTMENT");
        uploadStep.setStartedOn("2020-02-27T08:18:51.393Z");
        uploadStep.setFinishedOn("2020-02-27T08:18:55.696Z");
        uploadStep.setStepStatus(ExternalToolProcessStatus.DONE);

        ExternalToolProcessStep scanStep = uploadStep.deepCopy();
        scanStep.setStepName("02_scan");
        scanStep.setProcessStepIdInTool("3");
        scanStep.setResult("14");
        scanStep.setStartedOn("2020-02-27T08:41:26.882Z");
        scanStep.setFinishedOn("2020-02-27T08:42:29.445Z");

        ExternalToolProcessStep reportStep = uploadStep.deepCopy();
        reportStep.setStepName("03_report");
        reportStep.setProcessStepIdInTool("4");
        reportStep.setResult(attachment.getAttachmentContentId());
        reportStep.setStartedOn("2020-02-27T08:46:50.155Z");
        reportStep.setFinishedOn("2020-02-27T08:47:15.708Z");

        processSteps.add(uploadStep);
        processSteps.add(scanStep);
        processSteps.add(reportStep);
        fossologyProcess.setProcessSteps(processSteps);
        release3.setExternalToolProcesses(ImmutableSet.of(fossologyProcess));
        when(releaseServiceMock.getReleaseForUserById(eq(release3.getId()), anyObject())).thenReturn(release3);
        when(releaseServiceMock.getExternalToolProcess(release3)).thenReturn(fossologyProcess);
    }

    @Test
    public void should_document_get_releases() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/releases")
                .header("Authorization", "Bearer " + accessToken)
                .param("page", "0")
                .param("page_entries", "5")
                .param("sort", "name,desc")
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        requestParameters(
                                parameterWithName("page").description("Page of release"),
                                parameterWithName("page_entries").description("Amount of releases per page"),
                                parameterWithName("sort").description("Defines order of the releases")
                        ),
                        links(
                                linkWithRel("curies").description("Curies are used for online documentation"),
                                linkWithRel("first").description("Link to first page"),
                                linkWithRel("last").description("Link to last page")
                        ),
                        responseFields(
                                subsectionWithPath("_embedded.sw360:releases.[]name").description("The name of the release, optional"),
                                subsectionWithPath("_embedded.sw360:releases.[]version").description("The version of the release"),
                                subsectionWithPath("_embedded.sw360:releases").description("An array of <<resources-releases, Releases resources>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"),
                                fieldWithPath("page").description("Additional paging information"),
                                fieldWithPath("page.size").description("Number of projects per page"),
                                fieldWithPath("page.totalElements").description("Total number of all existing projects"),
                                fieldWithPath("page.totalPages").description("Total number of pages"),
                                fieldWithPath("page.number").description("Number of the current page")
                        )));
    }

    @Test
    public void should_document_get_release_all_details() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/releases?allDetails=true")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(
                                linkWithRel("curies").description("The curies for documentation")
                        ),
                        responseFields(
                                subsectionWithPath("_embedded.sw360:releases.[]name").description("The name of the release, optional"),
                                subsectionWithPath("_embedded.sw360:releases.[]version").description("The version of the release"),
                                subsectionWithPath("_embedded.sw360:releases.[]createdBy").description("Email of the release creator"),
                                subsectionWithPath("_embedded.sw360:releases.[]cpeId").description("CpeId of the release"),
                                subsectionWithPath("_embedded.sw360:releases.[]clearingState").description("The clearing of the release, possible values are " + Arrays.asList(ClearingState.values())),
                                subsectionWithPath("_embedded.sw360:releases.[]releaseDate").description("The date of this release"),
                                subsectionWithPath("_embedded.sw360:releases.[]createdOn").description("The creation date of the internal sw360 release"),
                                subsectionWithPath("_embedded.sw360:releases.[]mainlineState").description("the mainline state of the release, possible values are: " + Arrays.asList(MainlineState.values())),
                                subsectionWithPath("_embedded.sw360:releases.[]sourceCodeDownloadurl").description("the source code download url of the release"),
                                subsectionWithPath("_embedded.sw360:releases.[]binaryDownloadurl").description("the binary download url of the release"),
                                subsectionWithPath("_embedded.sw360:releases.[]externalIds").description("When releases are imported from other tools, the external ids can be stored here. Store as 'Single String' when single value, or 'Array of String' when multi-values"),
                                subsectionWithPath("_embedded.sw360:releases.[]additionalData").description("A place to store additional data used by external tools").optional(),
                                subsectionWithPath("_embedded.sw360:releases.[]languages").description("The language of the component"),
                                subsectionWithPath("_embedded.sw360:releases.[]contributors").description("An array of all project contributors with email").optional(),
                                subsectionWithPath("_embedded.sw360:releases.[]mainLicenseIds").description("An array of all main licenses").optional(),
                                subsectionWithPath("_embedded.sw360:releases.[]operatingSystems").description("The OS on which the release operates"),
                                subsectionWithPath("_embedded.sw360:releases.[]softwarePlatforms").description("The software platforms of the component"),
                                subsectionWithPath("_embedded.sw360:releases.[]vendor").description("The Id of the vendor").optional(),
                                subsectionWithPath("_embedded.sw360:releases.[]_embedded.sw360:moderators").description("An array of all release moderators with email"),
                                subsectionWithPath("_embedded.sw360:releases.[]_embedded.sw360:attachments").description("An array of all release attachments").optional(),
                                subsectionWithPath("_embedded.sw360:releases.[]_embedded.sw360:cotsDetails").description("An cotsDetails of the release").optional(),
                                subsectionWithPath("_embedded.sw360:releases.[]_embedded.sw360:releaseIdToRelationship").description("An linked release Id with relation").optional(),
                                subsectionWithPath("_embedded.sw360:releases.[]_embedded.sw360:clearingInformation").description("An Clearing Information of the release").optional(),
                                subsectionWithPath("_embedded.sw360:releases.[]_links").description("Self <<resources-index-links,Links>> to Release resource"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }

    @Test
    public void should_document_get_releases_with_fields() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/releases?fields=cpeId,releaseDate")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(
                                linkWithRel("curies").description("Curies are used for online documentation")
                        ),
                        responseFields(
                                subsectionWithPath("_embedded.sw360:releases.[]name").description("The name of the release, optional"),
                                subsectionWithPath("_embedded.sw360:releases.[]version").description("The version of the release"),
                                subsectionWithPath("_embedded.sw360:releases.[]cpeId").description("The cpeId of the release, optional"),
                                subsectionWithPath("_embedded.sw360:releases.[]releaseDate").description("The releaseDate of the release, optional"),
                                subsectionWithPath("_embedded.sw360:releases").description("An array of <<resources-releases, Releases resources>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }

    @Test
    public void should_document_get_releases_by_name() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/releases?name=" + release.getName())
                .header("Authorization", "Bearer " + accessToken).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(linkWithRel("curies").description("Curies are used for online documentation")),
                        responseFields(
                                subsectionWithPath("_embedded.sw360:releases.[]name").description("The name of the release, optional"),
                                subsectionWithPath("_embedded.sw360:releases.[]version").description("The version of the release"),
                                subsectionWithPath("_embedded.sw360:releases").description("An array of <<resources-releases, Releases resources>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")))
                        );
    }

    @Test
    public void should_document_get_release() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/releases/" + release.getId())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(
                                linkWithRel("self").description("The <<resources-releases,Releases resource>>"),
                                linkWithRel("sw360:component").description("The link to the corresponding component"),
                                linkWithRel("curies").description("The curies for documentation")
                        ),
                        responseFields(
                                fieldWithPath("name").description("The name of the release, optional"),
                                fieldWithPath("version").description("The version of the release"),
                                fieldWithPath("createdBy").description("Email of the release creator"),
                                fieldWithPath("cpeId").description("CpeId of the release"),
                                fieldWithPath("clearingState").description("The clearing of the release, possible values are " + Arrays.asList(ClearingState.values())),
                                fieldWithPath("cpeId").description("The CPE id"),
                                fieldWithPath("releaseDate").description("The date of this release"),
                                fieldWithPath("createdOn").description("The creation date of the internal sw360 release"),
                                fieldWithPath("mainlineState").description("the mainline state of the release, possible values are: " + Arrays.asList(MainlineState.values())),
                                fieldWithPath("sourceCodeDownloadurl").description("the source code download url of the release"),
                                fieldWithPath("binaryDownloadurl").description("the binary download url of the release"),
                                subsectionWithPath("externalIds").description("When releases are imported from other tools, the external ids can be stored here. Store as 'Single String' when single value, or 'Array of String' when multi-values"),
                                subsectionWithPath("additionalData").description("A place to store additional data used by external tools"),
                                fieldWithPath("languages").description("The language of the component"),
                                subsectionWithPath("_embedded.sw360:licenses").description("An array of all main licenses with their fullName and link to their <<resources-license-get,License resource>>"),
                                fieldWithPath("operatingSystems").description("The OS on which the release operates"),
                                fieldWithPath("softwarePlatforms").description("The software platforms of the component"),
                                subsectionWithPath("_embedded.sw360:moderators").description("An array of all release moderators with email and link to their <<resources-user-get,User resource>>"),
                                subsectionWithPath("_embedded.sw360:attachments").description("An array of all release attachments and link to their <<resources-attachment-get,Attachment resource>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }

    @Test
    public void should_document_delete_releases() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(delete("/api/releases/" + release.getId())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isMultiStatus())
                .andDo(this.documentationHandler.document(
                        responseFields(
                                fieldWithPath("[].resourceId").description("id of the deleted resource"),
                                fieldWithPath("[].status").description("status of the delete operation")
                        )
                ));
    }

    @Test
    public void should_document_get_usedbyresource_for_release() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/releases/usedBy/" + release.getId())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        responseFields(
                                subsectionWithPath("_embedded.sw360:components.[]name").description("The name of the component"),
                                subsectionWithPath("_embedded.sw360:components.[]componentType").description("The component type, possible values are: " + Arrays.asList(ComponentType.values())),
                                subsectionWithPath("_embedded.sw360:components").description("An array of <<resources-components, Components resources>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"),
                                subsectionWithPath("_embedded.sw360:projects.[]name").description("The name of the project"),
                                subsectionWithPath("_embedded.sw360:projects.[]version").description("The project version"),
                                subsectionWithPath("_embedded.sw360:projects.[]projectType").description("The project type, possible values are: " + Arrays.asList(ProjectType.values())),
                                subsectionWithPath("_embedded.sw360:projects").description("An array of <<resources-projects, Projects resources>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }


    @Test
    public void should_document_update_release() throws Exception {
        Release updateRelease = new Release();
        release.setName("Updated release");

        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(patch("/api/releases/" + releaseId)
                .contentType(MediaTypes.HAL_JSON)
                .content(this.objectMapper.writeValueAsString(updateRelease))
                .header("Authorization", "Bearer" + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(documentReleaseProperties());
    }

    @Test
    public void should_document_get_release_attachment_info() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/releases/" + release.getId() + "/attachments")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        responseFields(
                                subsectionWithPath("_embedded.sw360:attachments").description("An array of <<resources-attachment, Attachments resources>>"),
                                subsectionWithPath("_embedded.sw360:attachments.[]filename").description("The attachment filename"),
                                subsectionWithPath("_embedded.sw360:attachments.[]sha1").description("The attachment sha1 value"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }

    @Test
    public void should_document_update_release_attachment_info() throws Exception {
        Attachment updateAttachment = new Attachment().setAttachmentType(AttachmentType.BINARY)
                .setCreatedComment("Created Comment").setCheckStatus(CheckStatus.ACCEPTED)
                .setCheckedComment("Checked Comment");
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        this.mockMvc
                .perform(patch("/api/releases/98745/attachment/1234").contentType(MediaTypes.HAL_JSON)
                        .content(this.objectMapper.writeValueAsString(updateAttachment))
                        .header("Authorization", "Bearer " + accessToken).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                requestFields(
                        fieldWithPath("attachmentType").description("The type of Attachment. Possible Values are: "+Arrays.asList(AttachmentType.values())),
                        fieldWithPath("createdComment").description("The upload Comment of Attachment"),
                        fieldWithPath("checkStatus").description("The checkStatus of Attachment. Possible Values are: "+Arrays.asList(CheckStatus.values())),
                        fieldWithPath("checkedComment").description("The checked Comment of Attachment")),
                responseFields(
                        fieldWithPath("filename").description("The attachment filename"),
                        fieldWithPath("sha1").description("The attachment sha1 value"),
                        fieldWithPath("attachmentType").description("The type of attachment. Possible Values are: "+Arrays.asList(AttachmentType.values())),
                        fieldWithPath("createdBy").description("The email of user who uploaded the attachment"),
                        fieldWithPath("createdTeam").description("The department of user who uploaded the attachment"),
                        fieldWithPath("createdOn").description("The date when attachment was uploaded"),
                        fieldWithPath("createdComment").description("The upload Comment of attachment"),
                        fieldWithPath("checkStatus").description("The checkStatus of attachment. Possible Values are: "+Arrays.asList(CheckStatus.values())),
                        fieldWithPath("checkedComment").description("The checked comment of attachment"),
                        fieldWithPath("checkedBy").description("The email of user who checked the attachment"),
                        fieldWithPath("checkedTeam").description("The department of user who checked the attachment"),
                        fieldWithPath("checkedOn").description("The date when attachment was checked"),
                        subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                )));
    }

    @Test
    public void should_document_get_release_attachment() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/releases/" + release.getId() + "/attachments/" + attachment.getAttachmentContentId())
                .header("Authorization", "Bearer " + accessToken)
                .accept("application/*"))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document());
    }

    @Test
    public void should_document_get_releases_by_externalIds() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/releases/searchByExternalIds?mainline-id-component=1432&mainline-id-component=4876")
                .contentType(MediaTypes.HAL_JSON)
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        responseFields(
                                subsectionWithPath("_embedded.sw360:releases").description("An array of <<resources-releases, Releases resources>>"),
                                subsectionWithPath("_embedded.sw360:releases.[]name").description("The name of the release, optional"),
                                subsectionWithPath("_embedded.sw360:releases.[]version").description("The version of the release"),
                                subsectionWithPath("_embedded.sw360:releases.[]externalIds").description("External Ids of the release. Return as 'Single String' when single value, or 'Array of String' when multi-values"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }

    @Test
    public void should_document_trigger_fossology_process() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(
                get("/api/releases/" + release3.getId() + "/triggerFossologyProcess?markFossologyProcessOutdated=false")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(responseFields(
                        fieldWithPath("content.message").description(
                                "Message indicating whether FOSSology Process for Release triggered or not"),
                        subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"))));
    }

    @Test
    public void should_document_check_fossology_process_status() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/releases/" + release3.getId() + "/checkFossologyProcessStatus").header("Authorization",
                "Bearer " + accessToken)).andExpect(status().isOk())
                .andDo(this.documentationHandler.document(responseFields(
                        fieldWithPath("status").description("The status of triggered FOSSology, possible values are: " + Arrays.asList(RequestStatus.SUCCESS, RequestStatus.FAILURE, RequestStatus.PROCESSING)),
                        fieldWithPath("fossologyProcessInfo")
                                .description("The information about triggered FOSSology process."),
                        fieldWithPath("fossologyProcessInfo.externalTool")
                                .description("The name of external tool ,possible values are: " + Arrays.asList(ExternalTool.values())),
                        fieldWithPath("fossologyProcessInfo.processStatus")
                                .description("The status of process, possible values are: " + Arrays.asList(ExternalToolProcessStatus.values())),
                        fieldWithPath("fossologyProcessInfo.attachmentId")
                                .description("The attachement Id of the source."),
                        fieldWithPath("fossologyProcessInfo.attachmentHash")
                                .description("The attachement hash of the source."),
                        fieldWithPath("fossologyProcessInfo.processSteps")
                                .description("An array of ExternalToolProcessStep"),
                        fieldWithPath("fossologyProcessInfo.processSteps[]stepName")
                                .description("The name of step in FOSSology process, possible values are: " + Arrays.asList("01_upload", "02_scan", "03_report")),
                        fieldWithPath("fossologyProcessInfo.processSteps[]stepStatus")
                                .description("The status of step, possible values are: " + Arrays.asList(ExternalToolProcessStatus.DONE, ExternalToolProcessStatus.IN_WORK, ExternalToolProcessStatus.NEW)),
                        fieldWithPath("fossologyProcessInfo.processSteps[]startedBy")
                                .description("The email of user ,triggering the step."),
                        fieldWithPath("fossologyProcessInfo.processSteps[]startedByGroup")
                                .description("The group of user ,triggering the step."),
                        fieldWithPath("fossologyProcessInfo.processSteps[]startedOn")
                                .description("The start time of step."),
                        fieldWithPath("fossologyProcessInfo.processSteps[]processStepIdInTool")
                                .description("The upload id, scan id or report id in FOSSology."),
                        fieldWithPath("fossologyProcessInfo.processSteps[]finishedOn")
                                .description("The finished time of step."),
                        fieldWithPath("fossologyProcessInfo.processSteps[]result")
                                .description("The result of step , -1 or null indicates failure."))));
    }

    @Test
    public void should_document_create_release() throws Exception {
        Map<String, String> release = new HashMap<>();
        release.put("version", "1.0");
        release.put("componentId", component.getId());

        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        this.mockMvc.perform(post("/api/releases")
                .contentType(MediaTypes.HAL_JSON)
                .content(this.objectMapper.writeValueAsString(release))
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andDo(this.documentationHandler.document(
                        requestFields(
                                fieldWithPath("version").description("The version of the new release"),
                                fieldWithPath("componentId").description("The componentId of the origin component")
                        ),
                        responseFields(
                                fieldWithPath("name").description("The name of the release, optional"),
                                fieldWithPath("version").description("The version of the release"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }

    @Test
    public void should_document_upload_attachment_to_release() throws Exception {
        testAttachmentUpload("/api/releases/", releaseId);
    }

    @Test
    public void should_document_get_releases_by_sha1() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/releases?sha1=" + attachmentSha1)
                        .header("Authorization", "Bearer " + accessToken).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(linkWithRel("curies").description("Curies are used for online documentation")),
                        responseFields(
                                subsectionWithPath("_links")
                                        .description("<<resources-index-links,Links>> to other resources"),
                                subsectionWithPath("_embedded.sw360:releases").description(
                                        "The collection of <<resources-releases,Releases resources>>. In most cases the result should contain either one element or an empty response. If the same binary file is uploaded and attached to multiple sw360 resources, the collection will contain all the releases that have attachments with matching sha1 hash."))))
                .andReturn();
    }

    @Test
    public void should_document_delete_release_attachment() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(delete("/api/releases/" + release.getId() + "/attachments/" + attachment.getAttachmentContentId())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(documentReleaseProperties());
    }

    private RestDocumentationResultHandler documentReleaseProperties() {
        return this.documentationHandler.document(
                links(
                        linkWithRel("self").description("The <<resources-release,Release resource>>"),
                        linkWithRel("sw360:component").description("The link to the corresponding component"),
                        linkWithRel("curies").description("The curies for documentation")
                ),
                responseFields(
                        fieldWithPath("name").description("The name of the release, optional"),
                        fieldWithPath("version").description("The version of the release"),
                        fieldWithPath("createdBy").description("Email of the release creator"),
                        fieldWithPath("cpeId").description("CpeId of the release"),
                        fieldWithPath("clearingState").description("The clearing of the release, possible values are " + Arrays.asList(ClearingState.values())),
                        fieldWithPath("cpeId").description("The CPE id"),
                        fieldWithPath("releaseDate").description("The date of this release"),
                        fieldWithPath("createdOn").description("The creation date of the internal sw360 release"),
                        fieldWithPath("mainlineState").description("the mainline state of the release, possible values are: " + Arrays.asList(MainlineState.values())),
                        fieldWithPath("sourceCodeDownloadurl").description("the source code download url of the release"),
                        fieldWithPath("binaryDownloadurl").description("the binary download url of the release"),
                        subsectionWithPath("externalIds").description("When releases are imported from other tools, the external ids can be stored here. Store as 'Single String' when single value, or 'Array of String' when multi-values"),
                        subsectionWithPath("additionalData").description("A place to store additional data used by external tools"),
                        fieldWithPath("languages").description("The language of the component"),
                        subsectionWithPath("_embedded.sw360:licenses").description("An array of all main licenses with their fullName and link to their <<resources-license-get,License resource>>"),
                        fieldWithPath("operatingSystems").description("The OS on which the release operates"),
                        fieldWithPath("softwarePlatforms").description("The software platforms of the component"),
                        subsectionWithPath("_embedded.sw360:moderators").description("An array of all release moderators with email and link to their <<resources-user-get,User resource>>"),
                        subsectionWithPath("_embedded.sw360:attachments").description("An array of all release attachments and link to their <<resources-attachment-get,Attachment resource>>"),
                        subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                )
        );
    }
}
