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
import org.eclipse.sw360.datahandler.thrift.licenses.License;
import org.eclipse.sw360.rest.resourceserver.TestHelper;
import org.eclipse.sw360.rest.resourceserver.license.Sw360LicenseService;
import org.eclipse.sw360.datahandler.thrift.licenses.Obligation;
import org.eclipse.sw360.datahandler.thrift.licenses.ObligationLevel;
import org.eclipse.sw360.datahandler.thrift.licenses.ObligationType;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.Quadratic;
import org.eclipse.sw360.rest.resourceserver.user.Sw360UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultHandler;

import java.util.*;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.eq;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.linkWithRel;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.links;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
public class LicenseSpecTest extends TestRestDocsSpecBase {

    @Value("${sw360.test-user-id}")
    private String testUserId;

    @Value("${sw360.test-user-password}")
    private String testUserPassword;

    @MockBean
    private Sw360UserService userServiceMock;

    @MockBean
    private Sw360LicenseService licenseServiceMock;

    private License license;
    private Obligation obligation1, obligation2;

    @Before
    public void before() throws TException {
        license = new License();
        license.setId("Apache-2.0");
        license.setFullname("Apache License 2.0");
        license.setShortname("Apache 2.0");
        license.setText("placeholder for the Apache 2.0 license text");
        Map<String,String> externalIds = new HashMap<>();
        externalIds.put("SPDX", "Apache-2.0");
        externalIds.put("Trove", "License :: OSI Approved :: Apache Software License");
        license.setExternalIds(externalIds);
        license.setAdditionalData(Collections.singletonMap("Key", "Value"));

        License license2 = new License();
        license2.setId("MIT");
        license2.setFullname("The MIT License (MIT)");
        license2.setShortname("MIT");
        license2.setText("placeholder for the MIT license text");

        List<License> licenseList = new ArrayList<>();
        licenseList.add(license);
        licenseList.add(license2);

        given(this.licenseServiceMock.getLicenses()).willReturn(licenseList);
        given(this.licenseServiceMock.getLicenseById(eq(license.getId()))).willReturn(license);

        obligation1 = new Obligation();
        obligation1.setId("0001");
        obligation1.setTitle("Obligation 1");
        obligation1.setText("This is text of Obligation 1");
        obligation1.setObligationType(ObligationType.PERMISSION);
        obligation1.setObligationLevel(ObligationLevel.LICENSE_OBLIGATION);

        obligation2 = new Obligation();
        obligation2.setId("0002");
        obligation2.setTitle("Obligation 2");
        obligation2.setText("This is text of Obligation 2");
        obligation2.setObligationType(ObligationType.OBLIGATION);
        obligation2.setObligationLevel(ObligationLevel.LICENSE_OBLIGATION);
    }

    @Test
    public void should_document_get_licenses() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/licenses")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(
                                linkWithRel("curies").description("Curies are used for online documentation")
                        ),
                        responseFields(
                                subsectionWithPath("_embedded.sw360:licenses").description("An array of <<resources-licenses, Licenses resources>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }

    @Test
    public void should_document_get_license() throws Exception {
        List<Obligation> obligationList = new ArrayList<>();
        obligationList.add(obligation1);
        obligationList.add(obligation2);
        license.setObligations(obligationList);

        Set<String> obligationIds = new HashSet<String>();
        obligationIds.add(obligation1.getId());
        obligationIds.add(obligation2.getId());
        license.setObligationDatabaseIds(obligationIds);

        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/licenses/" + license.getId())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(
                                linkWithRel("self").description("The <<resources-licenses,Licenses resource>>")
                        ),
                        responseFields(
                                fieldWithPath("fullName").description("The full name of the license"),
                                fieldWithPath("shortName").description("The short name of the license, optional"),
                                subsectionWithPath("externalIds").description("When releases are imported from other tools, the external ids can be stored here"),
                                subsectionWithPath("additionalData").description("A place to store additional data used by external tools"),
                                fieldWithPath("text").description("The license's original text"),
                                fieldWithPath("checked").description("The information, whether the license is already checked, optional and defaults to true"),
                                subsectionWithPath("OSIApproved").description("The OSI aprroved information, possible values are: " + Arrays.asList(Quadratic.values())),
                                fieldWithPath("FSFLibre").description("The FSF libre information, possible values are: " + Arrays.asList(Quadratic.values())),
                                subsectionWithPath("_embedded.sw360:obligations").description("An array of <<resources-obligations, Obligations obligations>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }

    @Test
    public void should_document_link_obligation() throws Exception {
        Set<String> obligationIds = new HashSet<String>();
        obligationIds.add(obligation1.getId());
        obligationIds.add(obligation2.getId());

        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(post("/api/licenses/" + license.getId() + "/obligations")
                .contentType(MediaTypes.HAL_JSON)
                .content(this.objectMapper.writeValueAsString(obligationIds))
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated());
    }

    @Test
    public void should_document_unlink_obligation() throws Exception {
        List<Obligation> obligationList = new ArrayList<>();
        obligationList.add(obligation1);
        obligationList.add(obligation2);
        license.setObligations(obligationList);

        Set<String> obligationLicenseIds = new HashSet<String>();
        obligationLicenseIds.add(obligation1.getId());
        obligationLicenseIds.add(obligation2.getId());
        license.setObligationDatabaseIds(obligationLicenseIds);

        Set<String> obligationIds = new HashSet<String>();
        obligationIds.add(obligation2.getId());

        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        this.mockMvc.perform(patch("/api/licenses/" + license.getId() + "/obligations")
                .contentType(MediaTypes.HAL_JSON)
                .content(this.objectMapper.writeValueAsString(obligationIds))
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }
}
