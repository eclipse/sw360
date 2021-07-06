/*
 * Copyright Siemens AG, 2017, 2019. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.rest.authserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;

import org.eclipse.sw360.datahandler.thrift.ThriftClients;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.users.UserGroup;
import org.eclipse.sw360.datahandler.thrift.users.UserService;
import org.eclipse.sw360.rest.authserver.client.persistence.OAuthClientRepository;
import org.eclipse.sw360.rest.authserver.client.service.Sw360ClientDetailsService;
import org.eclipse.sw360.rest.authserver.security.Sw360GrantedAuthority;
import org.eclipse.sw360.rest.authserver.security.basicauth.Sw360LiferayAuthenticationProvider;
import org.apache.commons.lang.StringUtils;
import org.apache.thrift.TException;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.jwt.Jwt;
import org.springframework.security.jwt.JwtHelper;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Sw360AuthorizationServer.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
public abstract class IntegrationTestBase {

    @Value("${local.server.port}")
    protected int port;

    @Autowired
    protected FilterChainProxy springSecurityFilterChain;

    @MockBean
    protected ThriftClients thriftClients;

    @MockBean
    protected Sw360ClientDetailsService sw360ClientDetailsService;

    @MockBean
    protected OAuthClientRepository clientRepo;

    @SpyBean
    protected RestTemplateBuilder restTemplateBuilder;

    @Autowired
    @InjectMocks
    protected Sw360LiferayAuthenticationProvider provider;

    protected User adminTestUser;

    protected User normalTestUser;

    protected ClientDetails testClient;

    protected ResponseEntity<String> responseEntity;

    @Before
    public void setup() throws TException {
        setupTestUser();
        UserService.Client mockedUserService = mock(UserService.Client.class);
        when(mockedUserService.getByEmailOrExternalId(eq(adminTestUser.email), anyString())).thenReturn(adminTestUser);
        when(mockedUserService.getByEmailOrExternalId(eq(normalTestUser.email), anyString()))
                .thenReturn(normalTestUser);
        when(thriftClients.makeUserClient()).thenReturn(mockedUserService);

        setupTestClient();
        when(sw360ClientDetailsService.loadClientByClientId(anyString())).thenReturn(testClient);

        setupLiferayMocks();
    }

    private void setupTestUser() {
        adminTestUser = new User("mockedserviceadminuser@sw360.org", "qa-admin");
        adminTestUser.externalid = "service-mocked-by-mockito-admin";
        adminTestUser.fullname = "Mocked Service Admin User";
        adminTestUser.givenname = "Mocked";
        adminTestUser.lastname = "Service Admin User";
        adminTestUser.userGroup = UserGroup.SW360_ADMIN;

        normalTestUser = new User("mockedservicenormaluser@sw360.org", "qa-normal");
        normalTestUser.externalid = "service-mocked-by-mockito-normal";
        normalTestUser.fullname = "Mocked Service Normal User";
        normalTestUser.givenname = "Mocked";
        normalTestUser.lastname = "Service Normal User";
        normalTestUser.userGroup = UserGroup.USER;
    }

    private void setupTestClient() {
        testClient = new BaseClientDetails("trusted-sw360-client", "sw360-REST-API",
                Sw360GrantedAuthority.READ.getAuthority(), "client_credentials,password",
                Sw360GrantedAuthority.BASIC.getAuthority());
        ((BaseClientDetails) testClient).setClientSecret("sw360-secret");
        ((BaseClientDetails) testClient).setAutoApproveScopes(Sets.newHashSet("true"));
    }

    @SuppressWarnings("unchecked")
    private void setupLiferayMocks() {
        // this setup is more complex! Since we can only mock the RestTemplateBuilder,
        // which is used in other parts of the application and tests as well, we have to
        // create a mock and inject that one only in the correct class and cannot just
        // use @MockBean which would exchange it in the application context. In addition
        // we want two different responses (happy case and error case) which is why we
        // need to exchange the builder after a call to withBasicAuth() because we only
        // know at this location if we are in the happy case or in the error case

        // preparation for good case
        ResponseEntity<String> mockedResponseEntity = mock(ResponseEntity.class);
        when(mockedResponseEntity.getBody()).thenReturn("4711");

        RestTemplate mockedRestTemplate = mock(RestTemplate.class);
        when(mockedRestTemplate.postForEntity(anyString(), anyObject(), eq(String.class)))
                .thenReturn(mockedResponseEntity);

        RestTemplateBuilder mockedRTB = mock(RestTemplateBuilder.class);
        when(restTemplateBuilder.basicAuthorization(eq(adminTestUser.email), anyString())).thenReturn(mockedRTB);
        when(restTemplateBuilder.basicAuthorization(eq(normalTestUser.email), anyString())).thenReturn(mockedRTB);
        when(mockedRTB.build()).thenReturn(mockedRestTemplate);

        // preparation for bad case
        ResponseEntity<String> mockedResponseEntityFail = mock(ResponseEntity.class);
        when(mockedResponseEntityFail.getBody()).thenReturn("Some auth exception");

        RestTemplate mockedRestTemplateFail = mock(RestTemplate.class);
        when(mockedRestTemplateFail.postForEntity(anyString(), anyObject(), eq(String.class)))
                .thenReturn(mockedResponseEntityFail);

        RestTemplateBuilder mockedRTBFail = mock(RestTemplateBuilder.class);
        when(restTemplateBuilder.basicAuthorization(eq("my-unknown-user"), anyString())).thenReturn(mockedRTBFail);
        when(mockedRTBFail.build()).thenReturn(mockedRestTemplateFail);
    }

    protected void checkResponseBody() throws IOException {
        String responseBody = responseEntity.getBody();

        assertThat(responseEntity.getStatusCode(), is(HttpStatus.OK));

        JsonNode responseBodyJsonNode = new ObjectMapper().readTree(responseBody);

        assertThat(responseBodyJsonNode.get("token_type").asText(), is("bearer"));
        assertThat(responseBodyJsonNode.get("scope").asText(), is(Sw360GrantedAuthority.READ.toString()));
        assertThat(responseBodyJsonNode.has("access_token"), is(true));
        assertThat(responseBodyJsonNode.has("expires_in"), is(true));
        assertThat(responseBodyJsonNode.has("jti"), is(true));
    }

    protected JsonNode checkJwtClaims(String... expectedAuthority) throws IOException {
        String responseBody = responseEntity.getBody();

        assertThat(responseEntity.getStatusCode(), is(HttpStatus.OK));

        JsonNode responseBodyJsonNode = new ObjectMapper().readTree(responseBody);
        assertThat(responseBodyJsonNode.has("access_token"), is(true));

        String accessToken = responseBodyJsonNode.get("access_token").asText();
        Jwt jwt = JwtHelper.decode(accessToken);
        String jwtClaims = jwt.getClaims();
        JsonNode jwtClaimsJsonNode = new ObjectMapper().readTree(jwtClaims);
        assertThat(jwtClaimsJsonNode.get("aud").get(0).asText(), is("sw360-REST-API"));
        assertThat(jwtClaimsJsonNode.get("client_id").asText(), is("trusted-sw360-client"));

        JsonNode scopesNode = jwtClaimsJsonNode.get("scope");
        List<String> actualScopes = new ArrayList<>();
        if (scopesNode.isArray()) {
            for (final JsonNode scopeNode : scopesNode) {
                actualScopes.add(scopeNode.asText());
            }
        } else {
            actualScopes.add(scopesNode.asText());
        }
        assertThat(actualScopes, containsInAnyOrder(Sw360GrantedAuthority.READ.toString()));

        JsonNode authoritiesJsonNode = jwtClaimsJsonNode.get("authorities");
        List<String> actualAuthorities = new ArrayList<>();
        if (authoritiesJsonNode.isArray()) {
            for (final JsonNode authorityTextNode : authoritiesJsonNode) {
                actualAuthorities.add(authorityTextNode.asText());
            }
        } else {
            actualAuthorities.add(authoritiesJsonNode.asText());
        }
        System.out.println("ACTUAL: " + actualAuthorities);
        System.out.println("EXPECTED: " + StringUtils.join(expectedAuthority, ", "));
        assertThat(actualAuthorities, containsInAnyOrder(expectedAuthority));

        return jwtClaimsJsonNode;
    }
}
