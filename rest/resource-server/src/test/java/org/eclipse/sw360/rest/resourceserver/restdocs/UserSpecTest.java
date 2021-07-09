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

import com.google.common.collect.Sets;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.users.UserGroup;
import org.eclipse.sw360.rest.resourceserver.TestHelper;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.linkWithRel;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.links;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
public class UserSpecTest extends TestRestDocsSpecBase {

    @Value("${sw360.test-user-id}")
    private String testUserId;

    @Value("${sw360.test-user-password}")
    private String testUserPassword;

    @MockBean
    private Sw360UserService userServiceMock;

    private User user;

    @Before
    public void before() {
        List<User> userList = new ArrayList<>();

        Map<String,Set<UserGroup>> secondaryDepartmentsAndRoles = new HashMap<String, Set<UserGroup>>();
        Set<UserGroup> userGroups1 = new HashSet<>();
        userGroups1.add(UserGroup.CLEARING_EXPERT);
        userGroups1.add(UserGroup.ECC_ADMIN);
        Set<UserGroup> userGroups2 = new HashSet<>();
        userGroups2.add(UserGroup.SW360_ADMIN);
        userGroups2.add(UserGroup.SECURITY_ADMIN);
        secondaryDepartmentsAndRoles.put("DEPARTMENT1", userGroups1);
        secondaryDepartmentsAndRoles.put("DEPARTMENT2", userGroups2);
        user = new User();
        user.setEmail("admin@sw360.org");
        user.setId("4784587578e87989");
        user.setUserGroup(UserGroup.ADMIN);
        user.setFullname("John Doe");
        user.setGivenname("John");
        user.setLastname("Doe");
        user.setDepartment("SW360 Administration");
        user.setWantsMailNotification(true);
        user.setFormerEmailAddresses(Sets.newHashSet("admin_bachelor@sw360.org"));
        user.setSecondaryDepartmentsAndRoles(secondaryDepartmentsAndRoles);
        userList.add(user);

        given(this.userServiceMock.getUserByEmail("admin@sw360.org")).willReturn(user);
        given(this.userServiceMock.getUser("4784587578e87989")).willReturn(user);

        User user2 = new User();
        user2.setEmail("jane@sw360.org");
        user2.setId("frwey45786rwe");
        user2.setUserGroup(UserGroup.USER);
        user2.setFullname("Jane Doe");
        user2.setGivenname("Jane");
        user2.setLastname("Doe");
        user2.setDepartment("SW360 BA");
        user2.setWantsMailNotification(false);
        user.setSecondaryDepartmentsAndRoles(secondaryDepartmentsAndRoles);
        userList.add(user2);

        given(this.userServiceMock.getAllUsers()).willReturn(userList);
    }

    @Test
    public void should_document_get_users() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(
                                linkWithRel("curies").description("Curies are used for online documentation")
                        ),
                        responseFields(
                                subsectionWithPath("_embedded.sw360:users[]email").description("The user's email"),
                                subsectionWithPath("_embedded.sw360:users").description("An array of <<resources-users, User resources>>"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }

    @Test
    public void should_document_get_user_by_id() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/users/byid/" + user.getId())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(
                                linkWithRel("self").description("The <<resources-users,User resource>>")
                        ),
                        responseFields(
                                fieldWithPath("email").description("The user's email"),
                                fieldWithPath("userGroup").description("The user group, possible values are: " + Arrays.asList(UserGroup.values())),
                                fieldWithPath("fullName").description("The users's full name"),
                                fieldWithPath("givenName").description("The user's given name"),
                                fieldWithPath("lastName").description("The user's last name"),
                                fieldWithPath("department").description("The user's company department"),
                                subsectionWithPath("secondaryDepartmentsAndRoles").description("The user's secondary departments and roles"),
                                fieldWithPath("formerEmailAddresses").description("The user's former email addresses"),
                                fieldWithPath("deactivated").description("Is user deactivated"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }

    @Test
    public void should_document_get_user() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/users/" + user.getEmail())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(
                                linkWithRel("self").description("The <<resources-users,User resource>>")
                        ),
                        responseFields(
                                fieldWithPath("email").description("The user's email"),
                                fieldWithPath("userGroup").description("The user group, possible values are: " + Arrays.asList(UserGroup.values())),
                                fieldWithPath("fullName").description("The users's full name"),
                                fieldWithPath("givenName").description("The user's given name"),
                                fieldWithPath("lastName").description("The user's last name"),
                                fieldWithPath("department").description("The user's company department"),
                                subsectionWithPath("secondaryDepartmentsAndRoles").description("The user's secondary departments and roles"),
                                fieldWithPath("formerEmailAddresses").description("The user's former email addresses"),
                                fieldWithPath("deactivated").description("Is user deactivated"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources")
                        )));
    }
}
