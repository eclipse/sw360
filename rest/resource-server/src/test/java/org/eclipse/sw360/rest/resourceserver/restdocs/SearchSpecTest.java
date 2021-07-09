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
import java.util.List;

import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.thrift.search.SearchResult;
import org.eclipse.sw360.rest.resourceserver.TestHelper;
import org.eclipse.sw360.rest.resourceserver.search.Sw360SearchService;
import org.eclipse.sw360.rest.resourceserver.user.Sw360UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
public class SearchSpecTest extends TestRestDocsSpecBase {

    @Value("${sw360.test-user-id}")
    private String testUserId;

    @Value("${sw360.test-user-password}")
    private String testUserPassword;

    @MockBean
    private Sw360UserService userServiceMock;

    @MockBean
    private Sw360SearchService searchServiceMock;

    @Before
    public void before() throws TException, IOException {
        SearchResult sr = new SearchResult();
        sr.setId("376570");
        sr.setType("project");
        sr.setName("Orange Web");

        SearchResult sr1 = new SearchResult();
        sr1.setId("5578999");
        sr1.setType("release");
        sr1.setName("Spring 1.4.0");

        List<SearchResult> srs = new ArrayList<SearchResult>();
        srs.add(sr);
        srs.add(sr1);

        given(this.searchServiceMock.search(anyObject(), anyObject(), anyObject())).willReturn(srs);
    }

    @Test
    public void should_document_get_searchresult() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/search")
                .header("Authorization", "Bearer " + accessToken)
                .param("searchText", "376570")
                .param("typeMasks", "project")
                .param("page", "0")
                .param("page_entries", "5")
                .param("sort", "name,asc")
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        requestParameters(
                                parameterWithName("searchText").description("The search text"),
                                parameterWithName("typeMasks").description("The type of resource. Possible values are " +List.of("project", "component", "license", "release", "obligation", "user", "vendor")),
                                parameterWithName("page").description("Page of search results"),
                                parameterWithName("page_entries").description("Amount of search results per page"),
                                parameterWithName("sort").description("Defines order of the search results")
                        ),
                        links(
                                linkWithRel("curies").description("Curies are used for online documentation"),
                                linkWithRel("first").description("Link to first page"),
                                linkWithRel("last").description("Link to last page")
                        ),
                        responseFields(
                                subsectionWithPath("_embedded.sw360:searchResults.[]id").description("The id of the resource"),
                                subsectionWithPath("_embedded.sw360:searchResults.[]type").description("The type of the resource"),
                                subsectionWithPath("_embedded.sw360:searchResults.[]name").description("The name of the resource"),
                                subsectionWithPath("_embedded.sw360:searchResults").description("An array of <<resources-search, Search resources>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"),
                                fieldWithPath("page").description("Additional paging information"),
                                fieldWithPath("page.size").description("Number of search results per page"),
                                fieldWithPath("page.totalElements").description("Total number of all existing search results"),
                                fieldWithPath("page.totalPages").description("Total number of pages"),
                                fieldWithPath("page.number").description("Number of the current page")
                        )));
    }
}
