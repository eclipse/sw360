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
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.MalformedURLException;
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

    private static final String IS_DB_REACHABLE = "isDbReachable";
    private static final String IS_THRIFT_REACHABLE = "isThriftReachable";

    private DatabaseInstance databaseInstanceMock;

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
    public void health_should_return_503_with_missing_db() throws TException, MalformedURLException {
        databaseInstanceMock = mock(DatabaseInstance.class);
        when(databaseInstanceMock.checkIfDbExists(anyString()))
                .thenReturn(false);

        Health health = new Health().setStatus(Status.UP);

        final HealthService.Iface healthClient = mock(HealthService.Iface.class);
        when(healthClient.getHealth())
                .thenReturn(health);

        when(restHealthIndicatorMock.makeHealthClient())
                .thenReturn(healthClient);
        when(restHealthIndicatorMock.makeDatabaseInstance())
                .thenReturn(databaseInstanceMock);

        ResponseEntity<Map> entity = getMapResponseEntityForHealthEndpointRequest("/health");

        then(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        final LinkedHashMap body = (LinkedHashMap) entity.getBody();
        final LinkedHashMap bodyDetails =(LinkedHashMap<String, Object>) body.get("details");
        final LinkedHashMap sW360RestDetails =(LinkedHashMap<String, Object>) bodyDetails.get("SW360Rest");
        final LinkedHashMap sw360Rest =(LinkedHashMap<String, Object>) sW360RestDetails.get("details");
        final LinkedHashMap restStateDetails =(LinkedHashMap<String, Object>)sW360RestDetails.get("details");
        final LinkedHashMap restState = (LinkedHashMap<String, Object>) restStateDetails.get("Rest State");
        assertFalse((boolean) restState.get(IS_DB_REACHABLE));
        assertTrue((boolean) restState.get(IS_THRIFT_REACHABLE));
    }

    @Test
    public void health_should_return_503_with_unhealthy_thrift() throws TException, MalformedURLException {
        databaseInstanceMock = mock(DatabaseInstance.class);
        when(databaseInstanceMock.checkIfDbExists(anyString()))
                .thenReturn(true);

        when(restHealthIndicatorMock.makeDatabaseInstance())
                .thenReturn(databaseInstanceMock);

        Health health = new Health()
                .setStatus(Status.DOWN)
                .setDetails(Collections.singletonMap(DatabaseSettings.COUCH_DB_ATTACHMENTS, new Exception("").getMessage()));

        final HealthService.Iface healthClient = mock(HealthService.Iface.class);
        when(healthClient.getHealth())
                .thenReturn(health);

        when(restHealthIndicatorMock.makeHealthClient())
                .thenReturn(healthClient);

        ResponseEntity<Map> entity = getMapResponseEntityForHealthEndpointRequest("/health");

        then(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        final LinkedHashMap body = (LinkedHashMap) entity.getBody();
        final LinkedHashMap bodyDetails =(LinkedHashMap<String, Object>) body.get("details");
        final LinkedHashMap sW360RestDetails =(LinkedHashMap<String, Object>) bodyDetails.get("SW360Rest");
        final LinkedHashMap sw360Rest =(LinkedHashMap<String, Object>) sW360RestDetails.get("details");
        final LinkedHashMap restStateDetails =(LinkedHashMap<String, Object>)sW360RestDetails.get("details");
        final LinkedHashMap restState = (LinkedHashMap<String, Object>) restStateDetails.get("Rest State");
        assertTrue((boolean) restState.get(IS_DB_REACHABLE));
        assertFalse((boolean) restState.get(IS_THRIFT_REACHABLE));
        assertNotNull(sw360Rest.get("error"));
    }

    @Test
    public void health_should_return_503_with_unreachable_thrift() throws TException, MalformedURLException {
        databaseInstanceMock = mock(DatabaseInstance.class);
        when(databaseInstanceMock.checkIfDbExists(anyString()))
                .thenReturn(true);
        when(restHealthIndicatorMock.makeDatabaseInstance())
                .thenReturn(databaseInstanceMock);

        final HealthService.Iface healthClient = mock(HealthService.Iface.class);
        when(healthClient.getHealth())
                .thenThrow(new TTransportException());

        when(restHealthIndicatorMock.makeHealthClient())
                .thenReturn(healthClient);

        ResponseEntity<Map> entity = getMapResponseEntityForHealthEndpointRequest("/health");

        then(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        final LinkedHashMap body = (LinkedHashMap) entity.getBody();
        final LinkedHashMap bodyDetails =(LinkedHashMap<String, Object>) body.get("details");
        final LinkedHashMap sW360RestDetails =(LinkedHashMap<String, Object>) bodyDetails.get("SW360Rest");
        final LinkedHashMap sw360Rest =(LinkedHashMap<String, Object>) sW360RestDetails.get("details");
        final LinkedHashMap restStateDetails =(LinkedHashMap<String, Object>)sW360RestDetails.get("details");
        final LinkedHashMap restState = (LinkedHashMap<String, Object>) restStateDetails.get("Rest State");
        assertTrue((boolean) restState.get(IS_DB_REACHABLE));
        assertFalse((boolean) restState.get(IS_THRIFT_REACHABLE));
        assertNotNull(sw360Rest.get("error"));
    }

    @Test
    public void health_should_return_503_with_throwable() throws MalformedURLException {
        final DatabaseInstance databaseInstanceMock = mock(DatabaseInstance.class);
        when(databaseInstanceMock.checkIfDbExists(anyString()))
                .thenThrow(new RuntimeException());

        when(restHealthIndicatorMock.makeDatabaseInstance())
                .thenReturn(databaseInstanceMock);

        ResponseEntity<Map> entity = getMapResponseEntityForHealthEndpointRequest("/health");

        then(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        final LinkedHashMap body = (LinkedHashMap) entity.getBody();
        final LinkedHashMap bodyDetails =(LinkedHashMap<String, Object>) body.get("details");
        final LinkedHashMap sW360RestDetails =(LinkedHashMap<String, Object>) bodyDetails.get("SW360Rest");
        final LinkedHashMap sw360Rest =(LinkedHashMap<String, Object>) sW360RestDetails.get("details");
        final LinkedHashMap restStateDetails =(LinkedHashMap<String, Object>)sW360RestDetails.get("details");
        final LinkedHashMap restState = (LinkedHashMap<String, Object>) restStateDetails.get("Rest State");
        assertFalse((boolean) restState.get(IS_DB_REACHABLE));
        assertNotNull(sw360Rest.get("error"));
    }

    @Test
    public void health_should_return_200_when_healthy() throws TException, MalformedURLException {
        databaseInstanceMock = mock(DatabaseInstance.class);
        when(databaseInstanceMock.checkIfDbExists(anyString()))
                .thenReturn(true);

        when(restHealthIndicatorMock.makeDatabaseInstance())
                .thenReturn(databaseInstanceMock);

        Health health = new Health().setStatus(Status.UP);

        final HealthService.Iface healthClient = mock(HealthService.Iface.class);
        when(healthClient.getHealth())
                .thenReturn(health);

        when(restHealthIndicatorMock.makeHealthClient())
                .thenReturn(healthClient);

        ResponseEntity<Map> entity = getMapResponseEntityForHealthEndpointRequest("/health");

        then(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        final LinkedHashMap body = (LinkedHashMap) entity.getBody();
        final LinkedHashMap bodyDetails =(LinkedHashMap<String, Object>) body.get("details");
        final LinkedHashMap sW360RestDetails =(LinkedHashMap<String, Object>) bodyDetails.get("SW360Rest");
        final LinkedHashMap restStateDetails =(LinkedHashMap<String, Object>)sW360RestDetails.get("details");
        final LinkedHashMap restState = (LinkedHashMap<String, Object>) restStateDetails.get("Rest State");

        assertTrue((boolean) restState.get(IS_DB_REACHABLE));
    }
}
