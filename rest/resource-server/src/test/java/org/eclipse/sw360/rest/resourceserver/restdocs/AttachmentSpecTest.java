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
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentType;
import org.eclipse.sw360.datahandler.thrift.attachments.CheckStatus;
import org.eclipse.sw360.datahandler.thrift.components.ClearingState;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.rest.resourceserver.TestHelper;
import org.eclipse.sw360.rest.resourceserver.attachment.AttachmentInfo;
import org.eclipse.sw360.rest.resourceserver.attachment.Sw360AttachmentService;
import org.eclipse.sw360.rest.resourceserver.release.Sw360ReleaseService;
import org.eclipse.sw360.rest.resourceserver.user.Sw360UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.linkWithRel;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.links;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
public class AttachmentSpecTest extends TestRestDocsSpecBase {

    @Value("${sw360.test-user-id}")
    private String testUserId;

    @Value("${sw360.test-user-password}")
    private String testUserPassword;

    @MockBean
    private Sw360UserService userServiceMock;

    @MockBean
    private Sw360AttachmentService attachmentServiceMock;

    @MockBean
    private Sw360ReleaseService releaseServiceMock;

    private Attachment attachment;

    @Before
    public void before() throws TException {
        List<Attachment> attachments = new ArrayList<>();
        attachment = new Attachment();

        attachment.setAttachmentContentId("76537653");
        attachment.setFilename("spring-core-4.3.4.RELEASE.jar");
        attachment.setSha1("da373e491d3863477568896089ee9457bc316783");
        attachment.setAttachmentType(AttachmentType.BINARY_SELF);
        attachment.setCreatedTeam("Clearing Team 1");
        attachment.setCreatedComment("please check before Christmas :)");
        attachment.setCreatedOn("2016-12-18");
        attachment.setCreatedBy("admin@sw360.org");
        attachment.setCheckedTeam("Clearing Team 2");
        attachment.setCheckedComment("everything looks good");
        attachment.setCheckedOn("2016-12-18");
        attachment.setCheckStatus(CheckStatus.ACCEPTED);

        attachments.add(attachment);

        Release release = new Release();
        release.setId("874687");
        release.setName("Spring Core 4.3.4");
        release.setCpeid("cpe:/a:pivotal:spring-core:4.3.4:");
        release.setReleaseDate("2016-12-07");
        release.setVersion("4.3.4");
        release.setCreatedOn("2016-12-18");
        release.setCreatedBy("admin@sw360.org");
        release.setModerators(new HashSet<>(Arrays.asList(testUserId, testUserPassword)));
        release.setComponentId("678dstzd8");
        release.setClearingState(ClearingState.APPROVED);

        AttachmentInfo attachmentInfo = new AttachmentInfo(attachment);
        List<AttachmentInfo> attachmentInfos = new ArrayList<>();
        attachmentInfos.add(attachmentInfo);
        Source owner = new Source();
        owner.setReleaseId(release.getId());
        attachmentInfo.setOwner(owner);

        given(this.attachmentServiceMock.getAttachmentById(eq(attachment.getAttachmentContentId()))).willReturn(attachmentInfo);
        given(this.attachmentServiceMock.getAttachmentsBySha1(eq(attachment.getSha1()))).willReturn(attachmentInfos);
        given(this.releaseServiceMock.getReleaseForUserById(eq(release.getId()), anyObject())).willReturn(release);

        User user = new User();
        user.setId("123456789");
        user.setEmail("admin@sw360.org");
        user.setFullname("John Doe");

        given(this.userServiceMock.getUserByEmailOrExternalId("admin@sw360.org")).willReturn(user);
    }

    @Test
    public void should_document_get_attachment() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/attachments/" + attachment.getAttachmentContentId())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(
                                linkWithRel("self").description("The <<resources-projects,Projects resource>>"),
                                linkWithRel("sw360:downloadLink").description("The download link (URL) of the resource"),
                                linkWithRel("curies").description("Curies are used for online documentation")
                        ),
                        responseFields(
                                fieldWithPath("filename").description("The filename of the attachment"),
                                fieldWithPath("sha1").description("The attachment's file contents sha1 hash"),
                                fieldWithPath("attachmentType").description("The attachment type, possible values are " + Arrays.asList(AttachmentType.values())),
                                fieldWithPath("createdTeam").description("The team who created this attachment"),
                                fieldWithPath("createdComment").description("Comment of the creating team"),
                                fieldWithPath("createdOn").description("The date the attachment was created"),
                                fieldWithPath("createdBy").description("The creator of the attachment"),
                                fieldWithPath("checkedTeam").description("The team who checked this attachment"),
                                fieldWithPath("checkedComment").description("Comment of the checking team"),
                                fieldWithPath("checkedOn").description("The date the attachment was checked"),
                                fieldWithPath("checkStatus").description("The checking status. possible values are " + Arrays.asList(CheckStatus.values())),
                                subsectionWithPath("_embedded.createdBy").description("The user who created this attachment"),
                                subsectionWithPath("_embedded.sw360:releases").description("The release this attachment belongs to"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }

    @Test
    public void should_document_get_attachments_by_sha1() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/attachments?sha1=da373e491d3863477568896089ee9457bc316783")
                        .header("Authorization", "Bearer " + accessToken).accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(linkWithRel("curies").description("Curies are used for online documentation")),
                        responseFields(
                                subsectionWithPath("_links")
                                        .description("<<resources-index-links,Links>> to other resources"),
                                        subsectionWithPath("_embedded.sw360:attachments").description(
                                        "The collection of <<resources-attachments,Attachment resources>>. In most cases the result should contain either one element or an empty collection. If the same binary file is uploaded and attached to multiple sw360 resources, the collection will contain all the attachments with matching sha1 hash."))))
                .andReturn();
    }
}
