/*
 * Copyright Bosch.IO GmbH 2020
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.rest.resourceserver;

import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.eclipse.sw360.datahandler.common.DatabaseSettings;
import org.eclipse.sw360.datahandler.couchdb.DatabaseInstance;
import org.eclipse.sw360.datahandler.thrift.health.Health;
import org.eclipse.sw360.datahandler.thrift.health.HealthService;
import org.eclipse.sw360.datahandler.thrift.health.Status;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.BDDAssertions.then;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/* @DirtiesContext is necessary because the context needs to be reloaded inbetween the tests
    otherwise the responses of previous tests are taken. NoOpCacheManager through @AutoConfigureCache
    was not enough to avoid this bug.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Sw360ResourceServer.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SW360RestHealthIndicatorTest {

    @LocalServerPort
    private int port;

    @SpyBean
    private SW360RestHealthIndicator restHealthIndicatorMock;

    @Autowired
    private TestRestTemplate testRestTemplate;

    private DatabaseInstance databaseInstanceMock;
    private String isDbReachable = "isDbReachable";
    private String isThriftReachable = "isThriftReachable";

    /**
     * Makes a request to localhost with the default server port and returns
     * the response as a response entity with type Map
     * @param endpoint endpoint that will be called
     * @return response of request
     */
    private ResponseEntity<Map> getMapResponseEntityForHealthEndpointRequest(String endpoint) {
        return this.testRestTemplate.getForEntity(
                "http://localhost:" + this.port + endpoint, Map.class);
    }

    @Test
    public void info_should_return_200() {
        ResponseEntity<Map> entity = getMapResponseEntityForHealthEndpointRequest("/info");

        then(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void health_should_return_503_with_missing_db() throws TException {

        Health health = new Health().setStatus(Status.UP);

        final HealthService.Iface healthClient = mock(HealthService.Iface.class);
        when(healthClient.getHealth())
                .thenReturn(health);

        restHealthIndicatorMock.setHealthClient(healthClient);

        ResponseEntity<Map> entity = getMapResponseEntityForHealthEndpointRequest("/health");

        then(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        final LinkedHashMap sw360Rest = (LinkedHashMap<String, Object>) entity.getBody().get("SW360Rest");
        final LinkedHashMap restState = (LinkedHashMap<String, Object>) sw360Rest.get("Rest State");
        assertFalse((boolean) restState.get(isDbReachable));
        assertTrue((boolean) restState.get(isThriftReachable));
    }

    @Test
    public void health_should_return_503_with_unhealthy_thrift() throws TException {
        databaseInstanceMock = mock(DatabaseInstance.class);
        when(databaseInstanceMock.checkIfDbExists(anyString()))
                .thenReturn(true);

        restHealthIndicatorMock.setDatabaseInstance(databaseInstanceMock);
        Health health = new Health()
                .setStatus(Status.DOWN)
                .setDetails(Collections.singletonMap(DatabaseSettings.COUCH_DB_ATTACHMENTS, new Exception("").getMessage()));

        final HealthService.Iface healthClient = mock(HealthService.Iface.class);
        when(healthClient.getHealth())
                .thenReturn(health);

        restHealthIndicatorMock.setHealthClient(healthClient);

        ResponseEntity<Map> entity = getMapResponseEntityForHealthEndpointRequest("/health");

        then(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        final LinkedHashMap sw360Rest = (LinkedHashMap<String, Object>) entity.getBody().get("SW360Rest");
        final LinkedHashMap restState = (LinkedHashMap<String, Object>) sw360Rest.get("Rest State");
        assertTrue((boolean) restState.get(isDbReachable));
        assertFalse((boolean) restState.get(isThriftReachable));
        assertNotNull(sw360Rest.get("error"));
    }

    @Test
    public void health_should_return_503_with_unreachable_thrift() throws TException {
        databaseInstanceMock = mock(DatabaseInstance.class);
        when(databaseInstanceMock.checkIfDbExists(anyString()))
                .thenReturn(true);
        restHealthIndicatorMock.setDatabaseInstance(databaseInstanceMock);

        final HealthService.Iface healthClient = mock(HealthService.Iface.class);
        when(healthClient.getHealth())
                .thenThrow(new TTransportException());

        restHealthIndicatorMock.setHealthClient(healthClient);

        ResponseEntity<Map> entity = getMapResponseEntityForHealthEndpointRequest("/health");

        then(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        final LinkedHashMap sw360Rest = (LinkedHashMap<String, Object>) entity.getBody().get("SW360Rest");
        final LinkedHashMap restState = (LinkedHashMap<String, Object>) sw360Rest.get("Rest State");
        assertTrue((boolean) restState.get(isDbReachable));
        assertFalse((boolean) restState.get(isThriftReachable));
        assertNotNull(sw360Rest.get("error"));
    }

    @Test
    public void health_should_return_503_with_throwable() {
        final DatabaseInstance databaseInstanceMock = mock(DatabaseInstance.class);
        when(databaseInstanceMock.checkIfDbExists(anyString()))
                .thenThrow(new RuntimeException());

        restHealthIndicatorMock.setDatabaseInstance(databaseInstanceMock);

        ResponseEntity<Map> entity = getMapResponseEntityForHealthEndpointRequest("/health");

        then(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        final LinkedHashMap sw360Rest = (LinkedHashMap<String, Object>) entity.getBody().get("SW360Rest");
        final LinkedHashMap restState = (LinkedHashMap<String, Object>) sw360Rest.get("Rest State");
        assertFalse((boolean) restState.get("isDbReachable"));
        assertNotNull(sw360Rest.get("error"));
    }

    @Test
    public void health_should_return_200_when_healthy() throws TException {
        databaseInstanceMock = mock(DatabaseInstance.class);
        when(databaseInstanceMock.checkIfDbExists(anyString()))
                .thenReturn(true);

        restHealthIndicatorMock.setDatabaseInstance(databaseInstanceMock);

        Health health = new Health().setStatus(Status.UP);

        final HealthService.Iface healthClient = mock(HealthService.Iface.class);
        when(healthClient.getHealth())
                .thenReturn(health);

        restHealthIndicatorMock.setHealthClient(healthClient);

        ResponseEntity<Map> entity = getMapResponseEntityForHealthEndpointRequest("/health");

        then(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        final LinkedHashMap sw360Rest = (LinkedHashMap<String, Object>) entity.getBody().get("SW360Rest");
        final LinkedHashMap restState = (LinkedHashMap<String, Object>) sw360Rest.get("Rest State");
        assertTrue((boolean) restState.get("isDbReachable"));
    }
}