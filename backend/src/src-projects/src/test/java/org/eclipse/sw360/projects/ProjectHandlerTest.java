/*
 * Copyright Siemens AG, 2013-2015. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.projects;

import com.google.common.collect.ImmutableMap;
import org.eclipse.sw360.datahandler.TestUtils;
import org.eclipse.sw360.datahandler.common.DatabaseSettingsTest;
import org.eclipse.sw360.datahandler.common.SW360Utils;
import org.eclipse.sw360.datahandler.couchdb.DatabaseConnector;
import org.eclipse.sw360.datahandler.db.AttachmentDatabaseHandler;
import org.eclipse.sw360.datahandler.db.ComponentDatabaseHandler;
import org.eclipse.sw360.datahandler.db.ProjectDatabaseHandler;
import org.eclipse.sw360.datahandler.entitlement.ProjectModerator;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.SW360Exception;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectProjectRelationship;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectRelationship;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.eclipse.sw360.datahandler.common.SW360Utils.getProjectIds;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;

public class ProjectHandlerTest {

    private static final String dbName = DatabaseSettingsTest.COUCH_DB_DATABASE;
    private static final String attachmentDbName = DatabaseSettingsTest.COUCH_DB_ATTACHMENTS;
    private static final String changeLogDbName = DatabaseSettingsTest.COUCH_CHANGELOGS;

    private static final User user1 = new User().setEmail("user1").setDepartment("AB CD EF");
    private static final User user2 = new User().setEmail("user2").setDepartment("AB CD FE");
    private static final User user3 = new User().setEmail("user3").setDepartment("AB CD EF");
    private static final Map<String, Boolean> userRoles = new HashMap<>();

    ProjectHandler handler;

    @Before
    public void setUp() throws Exception {
        List<Project> projects = new ArrayList<>();

        projects.add(new Project().setId("P1").setName("Project1").setBusinessUnit("AB CD EF").setCreatedBy("user1").setReleaseIdToUsage(Collections.emptyMap()));
        projects.add(new Project().setId("P2").setName("Project2").setBusinessUnit("AB CD FE").setCreatedBy("user2").setReleaseIdToUsage(Collections.emptyMap()));
        projects.get(1).addToContributors("user1");
        projects.add(new Project().setId("P3").setName("Project3").setBusinessUnit("AB CD EF").setCreatedBy("user3").setReleaseIdToUsage(Collections.emptyMap()));

        // Create the database
        TestUtils.createDatabase(DatabaseSettingsTest.getConfiguredClient(), dbName);

        // Prepare the database
        DatabaseConnector databaseConnector = new DatabaseConnector(DatabaseSettingsTest.getConfiguredHttpClient(), dbName);
        for (Project project : projects) {
            databaseConnector.add(project);
        }

        // Create the connector
        handler = new ProjectHandler(DatabaseSettingsTest.getConfiguredClient(), DatabaseSettingsTest.getConfiguredHttpClient(), dbName, changeLogDbName, attachmentDbName);
    }

    @After
    public void tearDown() throws Exception {
        // Delete the database
        TestUtils.deleteDatabase(DatabaseSettingsTest.getConfiguredClient(), dbName);
    }

    @Test
    public void testGetMyProjects() throws Exception {
        assertEquals(2, handler.getMyProjects(user1, userRoles).size());
        assertEquals(1, handler.getMyProjects(user2, userRoles).size());
        assertEquals(1, handler.getMyProjects(user3, userRoles).size());
    }


    @Test
    public void testGetAccessibleProjects() throws Exception {
        assertEquals(3, handler.getAccessibleProjectsSummary(user1).size());
        assertEquals(1, handler.getAccessibleProjectsSummary(user2).size());
        assertEquals(2, handler.getAccessibleProjectsSummary(user3).size());
    }


    @Test
    public void testGetProjectByIdUser1_1() throws Exception {
        Project project1 = handler.getProjectById("P1", user1);
        assertEquals("P1", project1.getId());
    }

    @Test(expected = SW360Exception.class)
    public void testGetProjectByIdUser1_2() throws Exception {
        handler.getProjectById("P1", user2);
    }

    @Test
    public void testGetProjectByIdUser1_3() throws Exception {
        Project project1 = handler.getProjectById("P1", user3);
        assertEquals("P1", project1.getId());
    }

    @Test
    public void testGetProjectByIdUser2_1() throws Exception {
        Project project2 = handler.getProjectById("P2", user1);
        assertEquals("P2", project2.getId());
    }

    @Test
    public void testGetProjectByIdUser2_2() throws Exception {
        Project project2 = handler.getProjectById("P2", user2);
        assertEquals("P2", project2.getId());
    }

    @Test(expected = SW360Exception.class)
    public void testGetProjectByIdUser2_3() throws Exception {
        handler.getProjectById("P2", user3);
    }


    @Test
    public void testGetProjectByIdUser3_1() throws Exception {
        Project project3 = handler.getProjectById("P3", user1);
        assertEquals("P3", project3.getId());
    }

    @Test(expected = SW360Exception.class)
    public void testGetProjectByIdUser3_2() throws Exception {
        handler.getProjectById("P3", user2);
    }

    @Test
    public void testGetProjectByIdUser3_3() throws Exception {
        Project project3 = handler.getProjectById("P3", user3);
        assertEquals("P3", project3.getId());
    }


    @Test
    public void testAddProject() throws Exception {
        Project project4 = new Project();
        project4.setName("Project4").setBusinessUnit("AB CD FE");

        String id = handler.addProject(project4, user2).getId();

        Project projectActual = handler.getProjectById(id, user2);
        assertEquals("Project4", projectActual.getName());
        assertEquals("user2", projectActual.getCreatedBy());
        assertEquals(SW360Utils.getCreatedOn(), projectActual.getCreatedOn());
        assertEquals("AB CD FE", projectActual.getBusinessUnit());

        assertEquals(2, handler.getMyProjects(user1, userRoles).size());
        assertEquals(2, handler.getMyProjects(user2, userRoles).size());
        assertEquals(1, handler.getMyProjects(user3, userRoles).size());

        assertEquals(3, handler.getAccessibleProjectsSummary(user1).size());
        assertEquals(2, handler.getAccessibleProjectsSummary(user2).size());
        assertEquals(2, handler.getAccessibleProjectsSummary(user3).size());
    }


//    @Test
//    public void testDuplicateProject() throws Exception {
//        String id = handler.duplicateProject("P1", "Project1a", user3);
//        Project projectNew = handler.getProjectById(id, user3);
//
//        assertEquals("Project1a", projectNew.getName());
//        assertEquals("user3", projectNew.getCreatedBy());
//        assertEquals(DataHandlerUtils.getCreatedOn(), projectNew.getCreatedOn());
//        assertEquals("AB CD EF", projectNew.getBusinessUnit());
//
//        assertEquals(2, handler.getMyProjectsSummary(user1.getEmail()).size());
//        assertEquals(1, handler.getMyProjectsSummary(user2.getEmail()).size());
//        assertEquals(2, handler.getMyProjectsSummary(user3.getEmail()).size());
//
//        assertEquals(3, handler.getBUProjectsSummarySummary(user1.getDepartment()).size());
//        assertEquals(1, handler.getBUProjectsSummarySummary(user2.getDepartment()).size());
//        assertEquals(3, handler.getBUProjectsSummarySummary(user3.getDepartment()).size());
//
//        assertEquals(4, handler.getAccessibleProjectsSummarySummary(user1).size());
//        assertEquals(1, handler.getAccessibleProjectsSummarySummary(user2).size());
//        assertEquals(3, handler.getAccessibleProjectsSummarySummary(user3).size());
//    }

    @Test
    public void testUpdateProject1_1() throws Exception {
        Project project1 = handler.getProjectById("P1", user1);
        project1.setName("Project1new");
        project1.setBusinessUnit("AB CD FE");
        RequestStatus status = handler.updateProject(project1, user1);

        assertEquals(RequestStatus.SUCCESS, status);
        assertEquals("Project1new", handler.getProjectById("P1", user1).getName());
        assertEquals("AB CD FE", handler.getProjectById("P1", user1).getBusinessUnit());

        assertEquals(2, handler.getMyProjects(user1, userRoles).size());
        assertEquals(1, handler.getMyProjects(user2, userRoles).size());
        assertEquals(1, handler.getMyProjects(user3, userRoles).size());

        assertEquals(3, handler.getAccessibleProjectsSummary(user1).size());
        assertEquals(2, handler.getAccessibleProjectsSummary(user2).size());
        assertEquals(1, handler.getAccessibleProjectsSummary(user3).size());

    }

    @Test
    public void testUpdateProject2_1() throws Exception {
        ProjectModerator moderator = Mockito.mock(ProjectModerator.class);

        ProjectDatabaseHandler handler = new ProjectDatabaseHandler(DatabaseSettingsTest.getConfiguredClient(), dbName, changeLogDbName, attachmentDbName, moderator,
                new ComponentDatabaseHandler(DatabaseSettingsTest.getConfiguredClient(), dbName, attachmentDbName),
                new AttachmentDatabaseHandler(DatabaseSettingsTest.getConfiguredClient(), dbName, attachmentDbName));
        Project project2 = handler.getProjectById("P2", user1);
        project2.setName("Project2new");

        Mockito.doReturn(RequestStatus.SENT_TO_MODERATOR).when(moderator).updateProject(project2, user1);

        RequestStatus status = handler.updateProject(project2, user1);

        // Now contributors can also change the project
        assertEquals(RequestStatus.SUCCESS, status);
//        assertEquals(RequestStatus.SENT_TO_MODERATOR, status);
//        assertEquals("Project2", handler.getProjectById("P2", user1).getName());

//        Mockito.verify(moderator, times(1)).updateProject(project2, user1.getEmail());
//        Mockito.verifyNoMoreInteractions(moderator);
    }

    @Test
    public void testUpdateProject2_2() throws Exception {
        Project project2 = handler.getProjectById("P2", user2);
        project2.setName("Project2new");
        project2.setBusinessUnit("AB CD EF");

        RequestStatus status = handler.updateProject(project2, user2);

        assertEquals(RequestStatus.SUCCESS, status);
        assertEquals("Project2new", handler.getProjectById("P2", user2).getName());
        assertEquals("AB CD EF", handler.getProjectById("P2", user2).getBusinessUnit());

        assertEquals(2, handler.getMyProjects(user1, userRoles).size());
        assertEquals(1, handler.getMyProjects(user2, userRoles).size());
        assertEquals(1, handler.getMyProjects(user3, userRoles).size());

        assertEquals(3, handler.getAccessibleProjectsSummary(user1).size());
        assertEquals(1, handler.getAccessibleProjectsSummary(user2).size());
        assertEquals(3, handler.getAccessibleProjectsSummary(user3).size());

    }

    @Test
    public void testUpdateProject3_3() throws Exception {
        Project project3 = handler.getProjectById("P3", user3);
        project3.setName("Project3new");
        project3.setBusinessUnit("AB CD FE");
        RequestStatus status = handler.updateProject(project3, user3);

        assertEquals(RequestStatus.SUCCESS, status);
        assertEquals("Project3new", handler.getProjectById("P3", user3).getName());
        assertEquals("AB CD FE", handler.getProjectById("P3", user3).getBusinessUnit());


        assertEquals(2, handler.getMyProjects(user1, userRoles).size());
        assertEquals(1, handler.getMyProjects(user2, userRoles).size());
        assertEquals(1, handler.getMyProjects(user3, userRoles).size());

        assertEquals(2, handler.getAccessibleProjectsSummary(user1).size());
        assertEquals(2, handler.getAccessibleProjectsSummary(user2).size());
        assertEquals(2, handler.getAccessibleProjectsSummary(user3).size());

    }
//////////////////////////////////////////////////////////////////////////////////////


    @Test(expected = Exception.class)
    public void testDeleteProject1_1() throws Exception {
        RequestStatus status = handler.deleteProject("P1", user1);

        assertEquals(RequestStatus.SUCCESS, status);

        assertEquals(1, handler.getMyProjects(user1, userRoles).size());
        assertEquals(1, handler.getMyProjects(user2, userRoles).size());
        assertEquals(1, handler.getMyProjects(user3, userRoles).size());

        assertEquals(2, handler.getAccessibleProjectsSummary(user1).size());
        assertEquals(1, handler.getAccessibleProjectsSummary(user2).size());
        assertEquals(1, handler.getAccessibleProjectsSummary(user3).size());

        boolean deleted = (handler.getProjectById("P1", user1) == null);
        assertEquals(true, deleted);
    }
    @Test
    public void testDontDeleteUsedProject1_1() throws Exception {

        final Project p1 = handler.getProjectById("P1", user1);
        p1.setLinkedProjects(ImmutableMap.of("P2", new ProjectProjectRelationship(ProjectRelationship.CONTAINED)));
        handler.updateProject(p1,user1);

        RequestStatus status = handler.deleteProject("P2", user2);

        assertEquals(RequestStatus.IN_USE, status);

        assertEquals(2, handler.getMyProjects(user1, userRoles).size());
        assertEquals(1, handler.getMyProjects(user2, userRoles).size());
        assertEquals(1, handler.getMyProjects(user3, userRoles).size());

        assertEquals(3, handler.getAccessibleProjectsSummary(user1).size());
        assertEquals(1, handler.getAccessibleProjectsSummary(user2).size());
        assertEquals(2, handler.getAccessibleProjectsSummary(user3).size());

        boolean deleted = (handler.getProjectById("P1", user1) == null);
        assertEquals(false, deleted);
    }
    @Test(expected = Exception.class)
    public void testDeleteProject2_2() throws Exception {
        RequestStatus status = handler.deleteProject("P2", user2);

        assertEquals(RequestStatus.SUCCESS, status);

        assertEquals(1, handler.getMyProjects(user1, userRoles).size());
        assertEquals(0, handler.getMyProjects(user2, userRoles).size());
        assertEquals(1, handler.getMyProjects(user3, userRoles).size());

        assertEquals(2, handler.getAccessibleProjectsSummary(user1).size());
        assertEquals(0, handler.getAccessibleProjectsSummary(user2).size());
        assertEquals(2, handler.getAccessibleProjectsSummary(user3).size());

        boolean deleted = (handler.getProjectById("P2", user2) == null);
        assertEquals(true, deleted);
    }

    @Test(expected = Exception.class)
    public void testDeleteProject3_3() throws Exception {
        RequestStatus status = handler.deleteProject("P3", user3);

        assertEquals(RequestStatus.SUCCESS, status);

        assertEquals(2, handler.getMyProjects(user1, userRoles).size());
        assertEquals(1, handler.getMyProjects(user2, userRoles).size());
        assertEquals(0, handler.getMyProjects(user3, userRoles).size());

        assertEquals(2, handler.getAccessibleProjectsSummary(user1).size());
        assertEquals(1, handler.getAccessibleProjectsSummary(user2).size());
        assertEquals(1, handler.getAccessibleProjectsSummary(user3).size());

        boolean deleted = (handler.getProjectById("P3", user3) == null);
        assertEquals(true, deleted);
    }


    @Test
    public void testSearchByName() throws Exception {
        List<Project> projects = handler.searchByName("Project1", user1);
        assertThat(getProjectIds(projects), containsInAnyOrder("P1"));
    }




}
