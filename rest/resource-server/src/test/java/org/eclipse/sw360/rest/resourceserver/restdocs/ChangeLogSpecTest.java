/*
 * Copyright Siemens AG, 2020. Part of the SW360 Portal Project.
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
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.linkWithRel;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.links;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.requestParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentContent;
import org.eclipse.sw360.datahandler.thrift.changelogs.ChangeLogs;
import org.eclipse.sw360.datahandler.thrift.changelogs.ChangedFields;
import org.eclipse.sw360.datahandler.thrift.changelogs.Operation;
import org.eclipse.sw360.datahandler.thrift.changelogs.ReferenceDocData;
import org.eclipse.sw360.datahandler.thrift.components.Component;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.moderation.ModerationRequest;
import org.eclipse.sw360.datahandler.thrift.projects.ObligationList;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.rest.resourceserver.TestHelper;
import org.eclipse.sw360.rest.resourceserver.changelog.Sw360ChangeLogService;
import org.eclipse.sw360.rest.resourceserver.user.Sw360UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
public class ChangeLogSpecTest extends TestRestDocsSpecBase {

    @Value("${sw360.test-user-id}")
    private String testUserId;

    @Value("${sw360.test-user-password}")
    private String testUserPassword;

    @MockBean
    private Sw360UserService userServiceMock;

    @MockBean
    private Sw360ChangeLogService changeLogServiceMock;

    private final List<String> docType = List.of(new Project().getType(), new Component().getType(),
            new Release().getType(), new ObligationList().getType(), new AttachmentContent().getType(),
            new ModerationRequest().getType());

    @Before
    public void before() throws TException, IOException {
        ChangeLogs changeLog = new ChangeLogs();
        changeLog.setId("1234");
        changeLog.setDocumentId("4567");
        changeLog.setUserEdited("admin@sw360.org");
        changeLog.setChangeTimestamp("2021-01-08");
        changeLog.setOperation(Operation.UPDATE);
        changeLog.setDocumentType("project");

        HashSet<ChangedFields> changes = new HashSet<ChangedFields>();
        ChangedFields changedFields = new ChangedFields();
        changedFields.setFieldName("version");
        changedFields.setFieldValueOld("\"2\"");
        changedFields.setFieldValueNew("\"25\"");
        ChangedFields changedFields1 = new ChangedFields();
        changedFields1.setFieldName("name");
        changedFields1.setFieldValueOld("\"TestProj\"");
        changedFields1.setFieldValueNew("\"TestProject\"");
        changes.add(changedFields);
        changes.add(changedFields1);
        HashSet<ReferenceDocData> referenceDoc = new HashSet<ReferenceDocData>();

        ReferenceDocData referenceDocData = new ReferenceDocData();
        referenceDocData.setRefDocId("98765");
        referenceDocData.setRefDocOperation(Operation.CREATE);
        referenceDocData.setRefDocType("attachment");
        referenceDoc.add(referenceDocData);
        changeLog.setReferenceDoc(referenceDoc);
        changeLog.setChanges(changes);

        ChangeLogs changeLog2 = new ChangeLogs();
        changeLog2.setId("2345");
        changeLog2.setDocumentId("56789");
        changeLog2.setUserEdited("admin@sw360.org");
        changeLog2.setChangeTimestamp("2021-01-08");
        changeLog2.setOperation(Operation.CREATE);
        changeLog2.setDocumentType("attachment");
        changeLog2.setParentDocId("4567");
        HashMap<String, String> info = new HashMap<String, String>();
        info.put("FILENAME", "abc.xml");
        info.put("CONTENT_TYPE", "application/rdf+xml");
        info.put("PARENT_OPERATION", "PROJECT_UPDATE");
        changeLog2.setInfo(info);

        List<ChangeLogs> changeLogs = new ArrayList<ChangeLogs>();
        changeLogs.add(changeLog);
        changeLogs.add(changeLog2);

        given(this.changeLogServiceMock.getChangeLogsByDocumentId(anyObject(), anyObject())).willReturn(changeLogs);
    }

    @Test
    public void should_document_get_changelog_by_documentid() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/changelog/document/4567")
                .header("Authorization", "Bearer " + accessToken)
                .param("page", "0")
                .param("page_entries", "5")
                .param("sort", "changeTimestamp,asc")
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        requestParameters(
                                parameterWithName("page").description("Page of changelogs"),
                                parameterWithName("page_entries").description("Amount of changelogs per page"),
                                parameterWithName("sort").description("Defines order of the changelogs")
                        ),
                        links(
                                linkWithRel("curies").description("Curies are used for online documentation"),
                                linkWithRel("first").description("Link to first page"),
                                linkWithRel("last").description("Link to last page")
                        ),
                        responseFields(
                                subsectionWithPath("_embedded.sw360:changeLogs[]id").description("The id of the resource"),
                                subsectionWithPath("_embedded.sw360:changeLogs[]documentId").description("The id of the document"),
                                subsectionWithPath("_embedded.sw360:changeLogs[]documentType").description("The type of the document. Possible values are " + docType),
                                subsectionWithPath("_embedded.sw360:changeLogs[]operation").description("The type of the operation. Possible values are " + Arrays.asList(Operation.values())),
                                subsectionWithPath("_embedded.sw360:changeLogs[]userEdited").description("The email id of user who made the changes"),
                                subsectionWithPath("_embedded.sw360:changeLogs[]changeTimestamp").description("The date of the changelog"),
                                subsectionWithPath("_embedded.sw360:changeLogs[]parentDocId").description("The id of the parent document which caused this change").optional(),
                                subsectionWithPath("_embedded.sw360:changeLogs[]info").description("Miscellaneous information like attachment filename, content type, parent doc operation").optional(),
                                subsectionWithPath("_embedded.sw360:changeLogs[]changes[]fieldName").description("The name of the changed field").optional(),
                                subsectionWithPath("_embedded.sw360:changeLogs[]changes[]fieldValueOld").description("The old value of changed field").optional(),
                                subsectionWithPath("_embedded.sw360:changeLogs[]changes[]fieldValueNew").description("The new value of changed field").optional(),
                                subsectionWithPath("_embedded.sw360:changeLogs[]changes").description("An array of changes").optional(),
                                subsectionWithPath("_embedded.sw360:changeLogs[]referenceDoc[]refDocId").description("The id of the document created along with current changes like Attachments").optional(),
                                subsectionWithPath("_embedded.sw360:changeLogs[]referenceDoc[]refDocType").description("The type of the reference document. Possible values are " + docType).optional(),
                                subsectionWithPath("_embedded.sw360:changeLogs[]referenceDoc[]refDocOperation").description("The type of the operation. Possible values are " + Arrays.asList(Operation.values())).optional(),
                                subsectionWithPath("_embedded.sw360:changeLogs[]referenceDoc[]").description("An array of refernce doc").optional(),
                                subsectionWithPath("_embedded.sw360:changeLogs").description("An array of <<resources-changelog, ChangeLog resources>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"),
                                fieldWithPath("page").description("Additional paging information"),
                                fieldWithPath("page.size").description("Number of changelogs per page"),
                                fieldWithPath("page.totalElements").description("Total number of all existing changelogs"),
                                fieldWithPath("page.totalPages").description("Total number of pages"),
                                fieldWithPath("page.number").description("Number of the current page")
                        )));
    }
}
