/*
 * Copyright Siemens AG, 2017-2018. Part of the SW360 Portal Project.
 * Copyright Bosch Software Innovations GmbH, 2018.
 *
  * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.rest.resourceserver.restdocs;

import com.google.common.collect.ImmutableSet;
import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentContent;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentType;
import org.eclipse.sw360.datahandler.thrift.attachments.CheckStatus;
import org.eclipse.sw360.datahandler.thrift.components.Component;
import org.eclipse.sw360.datahandler.thrift.components.ComponentType;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectType;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.rest.resourceserver.TestHelper;
import org.eclipse.sw360.rest.resourceserver.attachment.Sw360AttachmentService;
import org.eclipse.sw360.rest.resourceserver.component.Sw360ComponentService;
import org.eclipse.sw360.rest.resourceserver.user.Sw360UserService;
import org.hamcrest.Matchers;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
public class ComponentSpecTest extends TestRestDocsSpecBase {

    @Value("${sw360.test-user-id}")
    private String testUserId;

    @Value("${sw360.test-user-password}")
    private String testUserPassword;

    @MockBean
    private Sw360UserService userServiceMock;

    @MockBean
    private Sw360ComponentService componentServiceMock;

    @MockBean
    private Sw360AttachmentService attachmentServiceMock;

    private Component angularComponent;

    private Attachment attachment;

    private Project project;

    @Before
    public void before() throws TException, IOException {
        Set<Attachment> attachmentList = new HashSet<>();
        List<Resource<Attachment>> attachmentResources = new ArrayList<>();
        attachment = new Attachment("1231231254", "spring-core-4.3.4.RELEASE.jar");
        attachment.setSha1("da373e491d3863477568896089ee9457bc316783");
        attachmentList.add(attachment);
        attachmentResources.add(new Resource<>(attachment));
        Attachment attachment2 = new Attachment("1231231255", "spring-mvc-4.3.4.RELEASE.jar");
        attachment2.setSha1("da373e491d3863477568896089ee9457bc316784");
        attachmentList.add(attachment2);
        attachmentResources.add(new Resource<>(attachment2));

        Set<Attachment> setOfAttachment = new HashSet<Attachment>();
        Attachment att1 = new Attachment("1234", "test.zip").setAttachmentType(AttachmentType.SOURCE)
                .setCreatedBy("user@sw360.org").setSha1("da373e491d312365483589ee9457bc316783").setCreatedOn("2021-04-27")
                .setCreatedTeam("DEPARTMENT");
        Attachment att2 = att1.deepCopy().setAttachmentType(AttachmentType.BINARY).setCreatedComment("Created Comment")
                .setCheckStatus(CheckStatus.ACCEPTED).setCheckedComment("Checked Comment").setCheckedOn("2021-04-27")
                .setCheckedBy("admin@sw360.org").setCheckedTeam("DEPARTMENT1");

        given(this.attachmentServiceMock.getAttachmentContent(anyObject())).willReturn(new AttachmentContent().setId("1231231254").setFilename("spring-core-4.3.4.RELEASE.jar").setContentType("binary"));
        given(this.attachmentServiceMock.getResourcesFromList(anyObject())).willReturn(new Resources<>(attachmentResources));
        given(this.attachmentServiceMock.uploadAttachment(anyObject(), anyObject(), anyObject())).willReturn(attachment);
        given(this.attachmentServiceMock.filterAttachmentsToRemove(any(), any(), any())).willReturn(Collections.singleton(attachment));
        given(this.attachmentServiceMock.updateAttachment(anyObject(), anyObject(), anyObject(), anyObject())).willReturn(att2);

        Map<String, Set<String>> externalIds = new HashMap<>();
        externalIds.put("component-id-key", ImmutableSet.of("1831A3", "c77321"));

        Component testComponent = new Component().setAttachments(setOfAttachment).setId("98745")
                .setName("Test Component").setComponentType(ComponentType.CODE_SNIPPET)
                .setCreatedOn("2021-04-27").setCreatedBy("admin@sw360.org")
                .setCategories(ImmutableSet.of("java", "javascript", "sql"));

        List<Component> componentList = new ArrayList<>();
        Set<Component> usedByComponent = new HashSet<>();
        List<Component> componentListByName = new ArrayList<>();
        Map<String, String> angularComponentExternalIds = new HashMap<>();
        angularComponentExternalIds.put("component-id-key", "1831A3");
        angularComponentExternalIds.put("ws-component-id", "[\"123\",\"598752\"]");
        angularComponent = new Component();
        angularComponent.setId("17653524");
        angularComponent.setName("Angular");
        angularComponent.setComponentOwner("John");
        angularComponent.setDescription("Angular is a development platform for building mobile and desktop web applications.");
        angularComponent.setCreatedOn("2016-12-15");
        angularComponent.setCreatedBy("admin@sw360.org");
        angularComponent.setComponentType(ComponentType.OSS);
        angularComponent.setVendorNames(new HashSet<>(Collections.singletonList("Google")));
        angularComponent.setModerators(new HashSet<>(Arrays.asList("admin@sw360.org", "john@sw360.org")));
        angularComponent.setOwnerAccountingUnit("4822");
        angularComponent.setOwnerCountry("DE");
        angularComponent.setOwnerGroup("AA BB 123 GHV2-DE");
        angularComponent.setCategories(ImmutableSet.of("java", "javascript", "sql"));
        angularComponent.setLanguages(ImmutableSet.of("EN", "DE"));
        angularComponent.setOperatingSystems(ImmutableSet.of("Windows", "Linux"));
        angularComponent.setAttachments(attachmentList);
        angularComponent.setExternalIds(angularComponentExternalIds);
        angularComponent.setMailinglist("test@liferay.com");
        angularComponent.setAdditionalData(Collections.singletonMap("Key", "Value"));
        angularComponent.setHomepage("https://angular.io");
        componentList.add(angularComponent);
        componentListByName.add(angularComponent);

        Component springComponent = new Component();
        Map<String, String> springComponentExternalIds = new HashMap<>();
        springComponentExternalIds.put("component-id-key", "c77321");
        springComponentExternalIds.put("ws-component-id", "[\"125\",\"698452\"]");

        springComponent.setId("678dstzd8");
        springComponent.setName("Spring Framework");
        springComponent.setComponentOwner("Jane");
        springComponent.setDescription("The Spring Framework provides a comprehensive programming and configuration model for modern Java-based enterprise applications.");
        springComponent.setCreatedOn("2016-12-18");
        springComponent.setCreatedBy("jane@sw360.org");
        springComponent.setComponentType(ComponentType.OSS);
        springComponent.setVendorNames(new HashSet<>(Collections.singletonList("Pivotal")));
        springComponent.setModerators(new HashSet<>(Arrays.asList("admin@sw360.org", "jane@sw360.org")));
        springComponent.setOwnerAccountingUnit("5661");
        springComponent.setOwnerCountry("FR");
        springComponent.setOwnerGroup("SIM-KA12");
        springComponent.setCategories(ImmutableSet.of("jdbc", "java"));
        springComponent.setLanguages(ImmutableSet.of("EN", "DE"));
        springComponent.setOperatingSystems(ImmutableSet.of("Windows", "Linux"));
        springComponent.setExternalIds(springComponentExternalIds);
        springComponent.setMailinglist("test@liferay.com");
        componentList.add(springComponent);
        usedByComponent.add(springComponent);

        Set<Project> projectList = new HashSet<>();
        project = new Project();
        project.setId("376576");
        project.setName("Emerald Web");
        project.setProjectType(ProjectType.PRODUCT);
        project.setVersion("1.0.2");
        projectList.add(project);

        when(this.componentServiceMock.createComponent(anyObject(), anyObject())).then(invocation ->
                new Component("Spring Framework")
                        .setDescription("The Spring Framework provides a comprehensive programming and configuration model for modern Java-based enterprise applications.")
                        .setComponentType(ComponentType.OSS)
                        .setId("1234567890")
                        .setCreatedBy("admin@sw360.org")
                        .setCreatedOn(new SimpleDateFormat("yyyy-MM-dd").format(new Date())));

        given(this.componentServiceMock.getComponentsForUser(anyObject())).willReturn(componentList);
        given(this.componentServiceMock.getComponentForUserById(eq("17653524"), anyObject())).willReturn(angularComponent);
        given(this.componentServiceMock.getComponentForUserById(eq("98745"), anyObject())).willReturn(testComponent);
        given(this.componentServiceMock.getProjectsByComponentId(eq("17653524"), anyObject())).willReturn(projectList);
        given(this.componentServiceMock.getUsingComponentsForComponent(eq("17653524"), anyObject())).willReturn(usedByComponent);
        given(this.componentServiceMock.searchComponentByName(eq(angularComponent.getName()))).willReturn(componentListByName);
        given(this.componentServiceMock.deleteComponent(eq(angularComponent.getId()), anyObject())).willReturn(RequestStatus.SUCCESS);
        given(this.componentServiceMock.searchByExternalIds(eq(externalIds), anyObject())).willReturn((new HashSet<>(componentList)));
        given(this.componentServiceMock.convertToEmbeddedWithExternalIds(eq(angularComponent))).willReturn(
                new Component("Angular")
                        .setId("17653524")
                        .setComponentType(ComponentType.OSS)
                        .setExternalIds(Collections.singletonMap("component-id-key", "1831A3"))
        );
        given(this.componentServiceMock.convertToEmbeddedWithExternalIds(eq(springComponent))).willReturn(
                new Component("Spring Framework")
                        .setId("678dstzd8")
                        .setComponentType(ComponentType.OSS)
                        .setExternalIds(Collections.singletonMap("component-id-key", "c77321"))
        );

        given(this.userServiceMock.getUserByEmailOrExternalId("admin@sw360.org")).willReturn(
                new User("admin@sw360.org", "sw360").setId("123456789"));
        given(this.userServiceMock.getUserByEmail("admin@sw360.org")).willReturn(
                new User("admin@sw360.org", "sw360").setId("123456789"));
        given(this.userServiceMock.getUserByEmail("john@sw360.org")).willReturn(
                new User("john@sw360.org", "sw360").setId("74427996"));

        List<Release> releaseList = new ArrayList<>();
        Release release = new Release();
        release.setId("3765276512");
        release.setName("Angular 2.3.0");
        release.setCpeid("cpe:/a:Google:Angular:2.3.0:");
        release.setReleaseDate("2016-12-07");
        release.setVersion("2.3.0");
        release.setCreatedOn("2016-12-18");
        release.setCreatedBy("admin@sw360.org");
        release.setModerators(new HashSet<>(Arrays.asList("admin@sw360.org", "jane@sw360.org")));
        release.setComponentId(springComponent.getId());
        releaseList.add(release);

        Release release2 = new Release();
        release2.setId("3765276512");
        release2.setName("Angular 2.3.1");
        release2.setCpeid("cpe:/a:Google:Angular:2.3.1:");
        release2.setReleaseDate("2016-12-15");
        release2.setVersion("2.3.1");
        release2.setCreatedOn("2016-12-18");
        release2.setCreatedBy("admin@sw360.org");
        release2.setModerators(new HashSet<>(Arrays.asList("admin@sw360.org", "jane@sw360.org")));
        release2.setComponentId(springComponent.getId());
        releaseList.add(release2);

        angularComponent.setReleases(releaseList);
    }

    @Test
    public void should_document_get_components() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/components")
                .header("Authorization", "Bearer " + accessToken)
                .param("page", "0")
                .param("page_entries", "5")
                .param("sort", "name,desc")
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        requestParameters(
                                parameterWithName("page").description("Page of components"),
                                parameterWithName("page_entries").description("Amount of components per page"),
                                parameterWithName("sort").description("Defines order of the components")
                        ),
                        links(
                                linkWithRel("curies").description("Curies are used for online documentation"),
                                linkWithRel("first").description("Link to first page"),
                                linkWithRel("last").description("Link to last page")
                        ),
                        responseFields(
                                subsectionWithPath("_embedded.sw360:components.[]name").description("The name of the component"),
                                subsectionWithPath("_embedded.sw360:components.[]componentType").description("The component type, possible values are: " + Arrays.asList(ComponentType.values())),
                                subsectionWithPath("_embedded.sw360:components").description("An array of <<resources-components, Components resources>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"),
                                fieldWithPath("page").description("Additional paging information"),
                                fieldWithPath("page.size").description("Number of components per page"),
                                fieldWithPath("page.totalElements").description("Total number of all existing components"),
                                fieldWithPath("page.totalPages").description("Total number of pages"),
                                fieldWithPath("page.number").description("Number of the current page")
                        )));
    }

    @Test
    public void should_document_get_usedbyresource_for_components() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/components/usedBy/17653524")
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
    public void should_document_get_components_no_paging_params() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/components")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(
                                linkWithRel("curies").description("Curies are used for online documentation")
                        ),
                        responseFields(
                                subsectionWithPath("_embedded.sw360:components.[]name").description("The name of the component"),
                                subsectionWithPath("_embedded.sw360:components.[]componentType").description("The component type, possible values are: " + Arrays.asList(ComponentType.values())),
                                subsectionWithPath("_embedded.sw360:components").description("An array of <<resources-components, Components resources>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }

    @Test
    public void should_document_get_components_with_fields() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/components")
                .header("Authorization", "Bearer " + accessToken)
                .param("fields", "ownerGroup,ownerCountry")
                .param("page", "0")
                .param("page_entries", "5")
                .param("sort", "name,desc")
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        requestParameters(
                                parameterWithName("fields").description("Properties which should be present for each component in the result"),
                                parameterWithName("page").description("Page of components"),
                                parameterWithName("page_entries").description("Amount of components per page"),
                                parameterWithName("sort").description("Defines order of the components")
                        ),
                        links(
                                linkWithRel("curies").description("Curies are used for online documentation"),
                                linkWithRel("first").description("Link to first page"),
                                linkWithRel("last").description("Link to last page")
                        ),
                        responseFields(
                                subsectionWithPath("_embedded.sw360:components.[]name").description("The name of the component"),
                                subsectionWithPath("_embedded.sw360:components.[]ownerGroup").description("The ownerGroup of the component"),
                                subsectionWithPath("_embedded.sw360:components.[]ownerCountry").description("The ownerCountry of the component"),
                                subsectionWithPath("_embedded.sw360:components.[]componentType").description("The component type, possible values are: " + Arrays.asList(ComponentType.values())),
                                subsectionWithPath("_embedded.sw360:components").description("An array of <<resources-components, Components resources>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"),
                                fieldWithPath("page").description("Additional paging information"),
                                fieldWithPath("page.size").description("Number of components per page"),
                                fieldWithPath("page.totalElements").description("Total number of all existing components"),
                                fieldWithPath("page.totalPages").description("Total number of pages"),
                                fieldWithPath("page.number").description("Number of the current page")
                        )));
    }

    @Test
    public void should_document_get_component() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/components/17653524")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(
                                linkWithRel("self").description("The <<resources-components,Component resource>>")
                        ),
                        responseFields(
                                fieldWithPath("name").description("The name of the component"),
                                fieldWithPath("componentType").description("The component type, possible values are: " + Arrays.asList(ComponentType.values())),
                                fieldWithPath("description").description("The component description"),
                                fieldWithPath("createdOn").description("The date the component was created"),
                                fieldWithPath("componentOwner").description("The owner name of the component"),
                                fieldWithPath("ownerAccountingUnit").description("The owner accounting unit of the component"),
                                fieldWithPath("ownerGroup").description("The owner group of the component"),
                                fieldWithPath("ownerCountry").description("The owner country of the component"),
                                fieldWithPath("categories").description("The component categories"),
                                fieldWithPath("languages").description("The language of the component"),
                                subsectionWithPath("externalIds").description("When components are imported from other tools, the external ids can be stored here. Store as 'Single String' when single value, or 'Array of String' when multi-values"),
                                subsectionWithPath("additionalData").description("A place to store additional data used by external tools"),
                                fieldWithPath("operatingSystems").description("The OS on which the component operates"),
                                fieldWithPath("mailinglist").description("Component mailing lists"),
                                fieldWithPath("homepage").description("The homepage url of the component"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"),
                                subsectionWithPath("_embedded.createdBy").description("The user who created this component"),
                                subsectionWithPath("_embedded.sw360:releases").description("An array of all component releases with version and link to their <<resources-releases,Releases resource>>"),
                                subsectionWithPath("_embedded.sw360:moderators").description("An array of all component moderators with email and link to their <<resources-user-get,User resource>>"),
                                subsectionWithPath("_embedded.sw360:vendors").description("An array of all component vendors with full name and link to their <<resources-vendor-get,Vendor resource>>"),
                                subsectionWithPath("_embedded.sw360:attachments").description("An array of all component attachments and link to their <<resources-attachment-get,Attachment resource>>")
                        )));
    }

    @Test
    public void should_document_create_component() throws Exception {
        Map<String, String> component = new HashMap<>();
        component.put("name", "Spring Framework");
        component.put("description", "The Spring Framework provides a comprehensive programming and configuration model for modern Java-based enterprise applications.");
        component.put("componentType", ComponentType.OSS.toString());
        component.put("homepage", "https://angular.io");

        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        this.mockMvc.perform(
                post("/api/components")
                        .contentType(MediaTypes.HAL_JSON)
                        .content(this.objectMapper.writeValueAsString(component))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("_embedded.createdBy.email", Matchers.is("admin@sw360.org")))
                .andDo(this.documentationHandler.document(
                        requestFields(
                                fieldWithPath("name").description("The name of the component"),
                                fieldWithPath("description").description("The component description"),
                                fieldWithPath("componentType").description("The component type, possible values are: " + Arrays.asList(ComponentType.values())),
                                fieldWithPath("homepage").description("The homepage url of the component")
                        ),
                        responseFields(
                                fieldWithPath("name").description("The name of the component"),
                                fieldWithPath("componentType").description("The component type, possible values are: " + Arrays.asList(ComponentType.values())),
                                fieldWithPath("description").description("The component description"),
                                fieldWithPath("createdOn").description("The date the component was created"),
                                subsectionWithPath("_embedded.createdBy").description("The user who created this component"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }

    @Test
    public void should_document_get_components_by_type() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/components?type=" + angularComponent.getComponentType())
                .header("Authorization", "Bearer " + accessToken)
                .param("page", "0")
                .param("page_entries", "5")
                .param("sort", "name,desc")
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        requestParameters(
                                parameterWithName("type").description("Filter for type"),
                                parameterWithName("page").description("Page of components"),
                                parameterWithName("page_entries").description("Amount of components per page"),
                                parameterWithName("sort").description("Defines order of the components")
                        ),
                        links(
                                linkWithRel("curies").description("Curies are used for online documentation"),
                                linkWithRel("first").description("Link to first page"),
                                linkWithRel("last").description("Link to last page")
                        ),
                        responseFields(
                                subsectionWithPath("_embedded.sw360:components.[]name").description("The name of the component"),
                                subsectionWithPath("_embedded.sw360:components.[]componentType").description("The component type, possible values are: " + Arrays.asList(ComponentType.values())),
                                subsectionWithPath("_embedded.sw360:components").description("An array of <<resources-components, Components resources>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"),
                                fieldWithPath("page").description("Additional paging information"),
                                fieldWithPath("page.size").description("Number of components per page"),
                                fieldWithPath("page.totalElements").description("Total number of all existing components"),
                                fieldWithPath("page.totalPages").description("Total number of pages"),
                                fieldWithPath("page.number").description("Number of the current page")
                        )));
    }

    @Test
    public void should_document_get_components_by_name() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/components?name=" + angularComponent.getName())
                .header("Authorization", "Bearer " + accessToken)
                .param("page", "0")
                .param("page_entries", "5")
                .param("sort", "name,desc")
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        requestParameters(
                                parameterWithName("name").description("Filter for name"),
                                parameterWithName("page").description("Page of components"),
                                parameterWithName("page_entries").description("Amount of components per page"),
                                parameterWithName("sort").description("Defines order of the components")
                        ),
                        links(
                                linkWithRel("curies").description("Curies are used for online documentation"),
                                linkWithRel("first").description("Link to first page"),
                                linkWithRel("last").description("Link to last page")
                        ),
                        responseFields(
                                subsectionWithPath("_embedded.sw360:components.[]name").description("The name of the component"),
                                subsectionWithPath("_embedded.sw360:components.[]componentType").description("The component type, possible values are: " + Arrays.asList(ComponentType.values())),
                                subsectionWithPath("_embedded.sw360:components").description("An array of <<resources-components, Components resources>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"),
                                fieldWithPath("page").description("Additional paging information"),
                                fieldWithPath("page.size").description("Number of components per page"),
                                fieldWithPath("page.totalElements").description("Total number of all existing components"),
                                fieldWithPath("page.totalPages").description("Total number of pages"),
                                fieldWithPath("page.number").description("Number of the current page")
                        )));
    }

    @Test
    public void should_document_update_component() throws Exception {
        Component updateComponent = new Component();
        updateComponent.setName("Updated Component");

        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(patch("/api/components/17653524")
                .contentType(MediaTypes.HAL_JSON)
                .content(this.objectMapper.writeValueAsString(updateComponent))
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(documentComponentProperties());
    }

    @Test
    public void should_document_delete_components() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(delete("/api/components/" + angularComponent.getId())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isMultiStatus())
                .andDo(this.documentationHandler.document(
                        responseFields(
                                fieldWithPath("[].resourceId").description("id of the deleted resource"),
                                fieldWithPath("[].status").description("status of the delete operation")
                        )));
    }

    @Test
    public void should_document_get_component_attachment_info() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/components/" + angularComponent.getId() + "/attachments")
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
    public void should_document_update_component_attachment_info() throws Exception {
        Attachment updateAttachment = new Attachment().setAttachmentType(AttachmentType.BINARY)
                .setCreatedComment("Created Comment").setCheckStatus(CheckStatus.ACCEPTED)
                .setCheckedComment("Checked Comment");
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        this.mockMvc
                .perform(patch("/api/components/98745/attachment/1234").contentType(MediaTypes.HAL_JSON)
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
    public void should_document_get_component_attachment() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/components/" + angularComponent.getId() + "/attachments/" + attachment.getAttachmentContentId())
                .header("Authorization", "Bearer " + accessToken)
                .accept("application/*"))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document());
    }

    @Test
    public void should_document_get_components_by_externalIds() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/components/searchByExternalIds?component-id-key=1831A3&component-id-key=c77321")
                .contentType(MediaTypes.HAL_JSON)
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        responseFields(
                                subsectionWithPath("_embedded.sw360:components").description("An array of <<resources-components, Components resources>>"),
                                subsectionWithPath("_embedded.sw360:components.[]componentType").description("The component type, possible values are: " + Arrays.asList(ComponentType.values())),
                                subsectionWithPath("_embedded.sw360:components.[]name").description("The name of the component, optional"),
                                subsectionWithPath("_embedded.sw360:components.[]externalIds").description("External Ids of the component. Return as 'Single String' when single value, or 'Array of String' when multi-values"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }

    @Test
    public void should_document_upload_attachment_to_component() throws Exception {
        testAttachmentUpload("/api/components/", angularComponent.getId());
    }

    @Test
    public void should_document_delete_component_attachment() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(delete("/api/components/" + angularComponent.getId() + "/attachments/" + attachment.getAttachmentContentId())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(documentComponentProperties());
    }

    private RestDocumentationResultHandler documentComponentProperties() {
        return this.documentationHandler.document(
                links(
                        linkWithRel("self").description("The <<resources-components,Component resource>>")
                ),
                responseFields(
                        fieldWithPath("name").description("The name of the component"),
                        fieldWithPath("componentType").description("The component type, possible values are: " + Arrays.asList(ComponentType.values())),
                        fieldWithPath("description").description("The component description"),
                        fieldWithPath("createdOn").description("The date the component was created"),
                        fieldWithPath("componentOwner").description("The owner name of the component"),
                        fieldWithPath("ownerAccountingUnit").description("The owner accounting unit of the component"),
                        fieldWithPath("ownerGroup").description("The owner group of the component"),
                        fieldWithPath("ownerCountry").description("The owner country of the component"),
                        subsectionWithPath("externalIds").description("When projects are imported from other tools, the external ids can be stored here. Store as 'Single String' when single value, or 'Array of String' when multi-values"),
                        subsectionWithPath("additionalData").description("A place to store additional data used by external tools"),
                        fieldWithPath("categories").description("The component categories"),
                        fieldWithPath("languages").description("The language of the component"),
                        fieldWithPath("mailinglist").description("Component mailing lists"),
                        fieldWithPath("operatingSystems").description("The OS on which the component operates"),
                        fieldWithPath("homepage").description("The homepage url of the component"),
                        subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"),
                        subsectionWithPath("_embedded.createdBy").description("The user who created this component"),
                        subsectionWithPath("_embedded.sw360:releases").description("An array of all component releases with version and link to their <<resources-releases,Releases resource>>"),
                        subsectionWithPath("_embedded.sw360:moderators").description("An array of all component moderators with email and link to their <<resources-user-get,User resource>>"),
                        subsectionWithPath("_embedded.sw360:vendors").description("An array of all component vendors with ful name and link to their <<resources-vendor-get,Vendor resource>>"),
                        subsectionWithPath("_embedded.sw360:attachments").description("An array of all component attachments and link to their <<resources-attachment-get,Attachment resource>>")
                ));
    }
}
