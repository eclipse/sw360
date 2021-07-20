/*
 * Copyright Siemens AG, 2021. Part of the SW360 Portal Project.
 *
  * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.rest.resourceserver.restdocs;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyObject;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.thrift.ClearingRequestPriority;
import org.eclipse.sw360.datahandler.thrift.ClearingRequestState;
import org.eclipse.sw360.datahandler.thrift.Comment;
import org.eclipse.sw360.datahandler.thrift.projects.ClearingRequest;
import org.eclipse.sw360.rest.resourceserver.TestHelper;
import org.eclipse.sw360.rest.resourceserver.clearingrequest.Sw360ClearingRequestService;
import org.eclipse.sw360.rest.resourceserver.user.Sw360UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
public class ClearingRequestSpecTest extends TestRestDocsSpecBase {

    @Value("${sw360.test-user-id}")
    private String testUserId;

    @Value("${sw360.test-user-password}")
    private String testUserPassword;

    @MockBean
    private Sw360UserService userServiceMock;

    @MockBean
    private Sw360ClearingRequestService clearingRequestServiceMock;

    @Before
    public void before() throws TException, IOException {
        ClearingRequest clearingRequest = new ClearingRequest();
        clearingRequest.setId("CR-1");
        clearingRequest.setAgreedClearingDate("12-07-2020");
        clearingRequest.setClearingState(ClearingRequestState.NEW);
        clearingRequest.setClearingTeam("clearing.team@sw60.org");
        clearingRequest.setProjectBU("DEPT");
        clearingRequest.setProjectId("54321");
        clearingRequest.setRequestedClearingDate("10-07-2020");
        clearingRequest.setRequestingUser("test.admin@sw60.org");
        clearingRequest.setRequestingUserComment("testing comment");
        clearingRequest.setPriority(ClearingRequestPriority.LOW);

        List<Comment> comments = new ArrayList<Comment>();
        Comment comment = new Comment();
        comment.setText("comment text 1");
        comment.setCommentedBy("test.user@sw360.org");

        comments.add(comment);
        clearingRequest.setComments(comments);

        given(this.clearingRequestServiceMock.getClearingRequestById(anyObject(), anyObject())).willReturn(clearingRequest);
        given(this.clearingRequestServiceMock.getClearingRequestByProjectId(anyObject(), anyObject())).willReturn(clearingRequest);
    }

    @Test
    public void should_document_get_clearingrequest() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/clearingrequest/CR-1")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        responseFields(
                                fieldWithPath("id").description("The id of the clearing request"),
                                fieldWithPath("agreedClearingDate").description("The agreed clearing date of the request"),
                                fieldWithPath("clearingState").description("The clearing state of request. Possible values are:  " + Arrays.asList(ClearingRequestState.values())),
                                fieldWithPath("clearingTeam").description("The clearing team email id."),
                                fieldWithPath("projectBU").description("The Business Unit / Group of the Project, for which clearing request is created"),
                                fieldWithPath("projectId").description("The id of the Project, for which clearing request is created"),
                                fieldWithPath("requestedClearingDate").description("The requested clearing date of releases"),
                                fieldWithPath("requestingUser").description("The user who created the clearing request"),
                                fieldWithPath("requestingUserComment").description("The comment from requesting user"),
                                fieldWithPath("priority").description("The priorityof clearing request. Possible values are:  " + Arrays.asList(ClearingRequestPriority.values())),
                                subsectionWithPath("comments.[]").description("The clearing request comment"),
                                subsectionWithPath("comments.[]text").description("The clearing request comment text"),
                                subsectionWithPath("comments.[]commentedBy").description("The user who added the comment on clearing request")
                        )));
    }


    @Test
    public void should_document_get_clearingrequest_by_projectid() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/clearingrequest/project/54321")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        responseFields(
                                fieldWithPath("id").description("The id of the clearing request"),
                                fieldWithPath("agreedClearingDate").description("The agreed clearing date of the request"),
                                fieldWithPath("clearingState").description("The clearing state of request. Possible values are:  " + Arrays.asList(ClearingRequestState.values())),
                                fieldWithPath("clearingTeam").description("The clearing team email id."),
                                fieldWithPath("projectBU").description("The Business Unit / Group of the Project, for which clearing request is created"),
                                fieldWithPath("projectId").description("The id of the Project, for which clearing request is created"),
                                fieldWithPath("requestedClearingDate").description("The requested clearing date of releases"),
                                fieldWithPath("requestingUser").description("The user who created the clearing request"),
                                fieldWithPath("requestingUserComment").description("The comment from requesting user"),
                                fieldWithPath("priority").description("The priorityof clearing request. Possible values are:  " + Arrays.asList(ClearingRequestPriority.values())),
                                subsectionWithPath("comments.[]").description("The clearing request comment"),
                                subsectionWithPath("comments.[]text").description("The clearing request comment text"),
                                subsectionWithPath("comments.[]commentedBy").description("The user who added the comment on clearing request")
                        )));
    }
}
