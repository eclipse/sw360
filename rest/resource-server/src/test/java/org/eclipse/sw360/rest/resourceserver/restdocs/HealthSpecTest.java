/*
 * Copyright Bosch.IO GmbH 2020..
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.rest.resourceserver.restdocs;

import org.eclipse.sw360.rest.resourceserver.SW360RestHealthIndicator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class HealthSpecTest extends TestRestDocsSpecBase{

    @MockBean
    private SW360RestHealthIndicator restHealthIndicatorMock;

    @Test
    public void should_document_get_health() throws Exception {
        SW360RestHealthIndicator.RestState restState = new SW360RestHealthIndicator.RestState();
        restState.isThriftReachable = true;
        restState.isDbReachable = true;

        Health spring_health = Health.up()
                .withDetail("Rest State", restState)
                .build();
        given(this.restHealthIndicatorMock.health()).willReturn(spring_health);


        mockMvc.perform(get("/health")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        responseFields(
                                fieldWithPath("status").description("The overall status of the health."),
                                fieldWithPath("details.diskSpace.status").description("The status of the health of the diskspace."),
                                fieldWithPath("details.diskSpace.details.total").description("The total volume of the diskspace."),
                                fieldWithPath("details.diskSpace.details.free").description("The free space of the diskspace."),
                                fieldWithPath("details.diskSpace.details.threshold").description("The threshold of the diskspace."),
                                fieldWithPath("details.SW360Rest.status").description("The status of the health of the specific health indicator 'SW360 Rest'."),
                                fieldWithPath("details.SW360Rest.details.Rest State").description("The details of the rest state in regards to the thrift clients and database."),
                                fieldWithPath("details.SW360Rest.details.Rest State.isDbReachable").description("The details of the rest state in regards to the thrift clients reachablity represented by a boolean value."),
                                fieldWithPath("details.SW360Rest.details.Rest State.isThriftReachable").description("The details of the rest state in regards to the databases reachablity represented by a boolean value.")
                        )
                ));
    }

    @Test
    public void should_document_get_health_unhealthy() throws Exception {
        SW360RestHealthIndicator.RestState restState = new SW360RestHealthIndicator.RestState();
        restState.isThriftReachable = false;
        restState.isDbReachable = true;

        Health spring_health = Health.down()
                .withDetail("Rest State", restState)
                .withException(new Exception("Fake"))
                .build();
        given(this.restHealthIndicatorMock.health()).willReturn(spring_health);

        mockMvc.perform(get("/health")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andDo(this.documentationHandler.document(
                        responseFields(
                                fieldWithPath("status").description("The overall status of the health."),
                                fieldWithPath("details.diskSpace.status").description("The status of the health of the diskspace."),
                                fieldWithPath("details.diskSpace.details.total").description("The total volume of the diskspace."),
                                fieldWithPath("details.diskSpace.details.free").description("The free space of the diskspace."),
                                fieldWithPath("details.diskSpace.details.threshold").description("The threshold of the diskspace."),
                                fieldWithPath("details.SW360Rest.status").description("The status of the health of the specific health indicator 'SW360 Rest', which in case of error will not be \"UP\"."),
                                fieldWithPath("details.SW360Rest.details.Rest State").description("The details of the rest state in regards to the thrift clients and database."),
                                fieldWithPath("details.SW360Rest.details.Rest State.isDbReachable").description("The details of the rest state in regards to the thrift clients reachablity represented by a boolean value."),
                                fieldWithPath("details.SW360Rest.details.Rest State.isThriftReachable").description("The details of the rest state in regards to the databases reachablity represented by a boolean value."),
                                fieldWithPath("details.SW360Rest.details.error").description("The throwables that can cause the health status to be not \"UP\".")
                        )
                ));
    }
}
