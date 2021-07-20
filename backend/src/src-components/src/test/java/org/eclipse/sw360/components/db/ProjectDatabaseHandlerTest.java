/*
 * Copyright Siemens AG, 2016-2017. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.components.db;

import com.google.common.collect.ImmutableMap;
import org.eclipse.sw360.datahandler.TestUtils;
import org.eclipse.sw360.datahandler.common.DatabaseSettingsTest;
import org.eclipse.sw360.datahandler.cloudantclient.DatabaseConnectorCloudant;
import org.eclipse.sw360.datahandler.common.DatabaseSettings;
import org.eclipse.sw360.datahandler.couchdb.DatabaseConnector;
import org.eclipse.sw360.datahandler.db.AttachmentDatabaseHandler;
import org.eclipse.sw360.datahandler.db.ComponentDatabaseHandler;
import org.eclipse.sw360.datahandler.db.ProjectDatabaseHandler;
import org.eclipse.sw360.datahandler.entitlement.ProjectModerator;
import org.eclipse.sw360.datahandler.thrift.MainlineState;
import org.eclipse.sw360.datahandler.thrift.ProjectReleaseRelationship;
import org.eclipse.sw360.datahandler.thrift.ReleaseRelationship;
import org.eclipse.sw360.datahandler.thrift.Visibility;
import org.eclipse.sw360.datahandler.thrift.components.Component;
import org.eclipse.sw360.datahandler.thrift.components.ComponentType;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.components.ReleaseLink;
import org.eclipse.sw360.datahandler.thrift.projects.*;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.users.UserGroup;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import javax.xml.crypto.Data;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.eclipse.sw360.datahandler.TestUtils.assertTestString;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ProjectDatabaseHandlerTest {

    private static final String dbName = DatabaseSettingsTest.COUCH_DB_DATABASE;
    private static final String attachmentsDbName = DatabaseSettingsTest.COUCH_DB_ATTACHMENTS;

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    private List<Project> projects;
    private List<Vendor> vendors;
    private List<Release> releases;
    private List<Component> components;
    private ProjectDatabaseHandler handler;

    @Mock
    private ProjectModerator moderator;

    @Mock
    private User user;

    @Before
    public void setUp() throws Exception {
        assertTestString(dbName);
        assertTestString(attachmentsDbName);

        vendors = new ArrayList<>();
        vendors.add(new Vendor().setId("V1").setShortname("vendor").setFullname("vendor").setUrl("http://vendor.example.com"));

        releases = new ArrayList<>();
        Release release1a = new Release().setId("R1A").setComponentId("C1").setName("component1").setVersion("releaseA").setVendorId("V1");
        releases.add(release1a);
        Release release1b = new Release().setId("R1B").setComponentId("C1").setName("component1").setVersion("releaseB").setVendorId("V1");
        releases.add(release1b);
        Release release2a = new Release().setId("R2A").setComponentId("C2").setName("component2").setVersion("releaseA").setVendorId("V1");
        releases.add(release2a);
        Release release2b = new Release().setId("R2B").setComponentId("C2").setName("component2").setVersion("releaseB").setVendorId("V1");
        releases.add(release2b);

        components = new ArrayList<>();
        Component component1 = new Component().setId("C1").setName("component1").setDescription("d1").setComponentType(ComponentType.OSS);
        components.add(component1);
        Component component2 = new Component().setId("C2").setName("component2").setDescription("d2").setComponentType(ComponentType.COTS);
        components.add(component2);

        projects = new ArrayList<>();
        Project project1 = new Project().setId("P1").setName("project1").setLinkedProjects(ImmutableMap.of("P2", new ProjectProjectRelationship(ProjectRelationship.CONTAINED))).setVisbility(Visibility.EVERYONE);
        projects.add(project1);
        Project project2 = new Project().setId("P2").setName("project2").setLinkedProjects(ImmutableMap.of("P3", new ProjectProjectRelationship(ProjectRelationship.REFERRED), "P4", new ProjectProjectRelationship(ProjectRelationship.CONTAINED))).setReleaseIdToUsage(ImmutableMap.of("R1A", newDefaultProjectReleaseRelationship(), "R1B", newDefaultProjectReleaseRelationship())).setVisbility(Visibility.EVERYONE);
        projects.add(project2);
        Project project3 = new Project().setId("P3").setName("project3").setLinkedProjects(ImmutableMap.of("P2", new ProjectProjectRelationship(ProjectRelationship.UNKNOWN))).setReleaseIdToUsage(ImmutableMap.of("R2A", newDefaultProjectReleaseRelationship(), "R2B", newDefaultProjectReleaseRelationship())).setVisbility(Visibility.EVERYONE);
        projects.add(project3);
        Project project4 = new Project().setId("P4").setName("project4").setLinkedProjects(ImmutableMap.of("P1", new ProjectProjectRelationship(ProjectRelationship.UNKNOWN))).setVisbility(Visibility.EVERYONE);
        projects.add(project4);
        Project project5 = new Project().setId("P5").setName("project5").setLinkedProjects(ImmutableMap.of("P6", new ProjectProjectRelationship(ProjectRelationship.CONTAINED), "P7", new ProjectProjectRelationship(ProjectRelationship.CONTAINED))).setVisbility(Visibility.EVERYONE);
        projects.add(project5);
        Project project6 = new Project().setId("P6").setName("project6").setLinkedProjects(ImmutableMap.of("P7", new ProjectProjectRelationship(ProjectRelationship.CONTAINED))).setVisbility(Visibility.EVERYONE);
        projects.add(project6);
        Project project7 = new Project().setId("P7").setName("project7").setVisbility(Visibility.EVERYONE);
        projects.add(project7);

        // Create the database
        TestUtils.createDatabase(DatabaseSettingsTest.getConfiguredClient(), dbName);

        // Prepare the database
        DatabaseConnectorCloudant databaseConnector = new DatabaseConnectorCloudant(DatabaseSettingsTest.getConfiguredClient(), dbName);

        for (Vendor vendor : vendors) {
            databaseConnector.add(vendor);
        }
        for (Release release : releases) {
            databaseConnector.add(release);
        }
        for (Component component : components) {
            databaseConnector.add(component);
        }
        for (Project project : projects) {
            databaseConnector.add(project);
        }

        ComponentDatabaseHandler componentHandler = new ComponentDatabaseHandler(DatabaseSettingsTest.getConfiguredClient(), dbName, attachmentsDbName);
        AttachmentDatabaseHandler attachmentDatabaseHandler = new AttachmentDatabaseHandler(DatabaseSettingsTest.getConfiguredClient(), dbName, attachmentsDbName);
        handler = new ProjectDatabaseHandler(DatabaseSettingsTest.getConfiguredClient(), dbName, attachmentsDbName, moderator, componentHandler, attachmentDatabaseHandler);
    }

    private ProjectReleaseRelationship newDefaultProjectReleaseRelationship() {
        return new ProjectReleaseRelationship(ReleaseRelationship.REFERRED, MainlineState.MAINLINE);
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.deleteDatabase(DatabaseSettingsTest.getConfiguredClient(), dbName);
    }

    @Test
    public void testGetLinkedProjects() throws Exception {

        // we wrap the potentially infinite loop in an executor
        final ExecutorService service = Executors.newSingleThreadExecutor();

        Project project = handler.getProjectById("P1", user);
        final Future<List<ProjectLink>> completionFuture = service.submit(() -> handler.getLinkedProjects(project, true, user));

        service.shutdown();
        service.awaitTermination(10, TimeUnit.SECONDS);

        final List<ProjectLink> linkedProjects = completionFuture.get();

        ReleaseLink releaseLinkR1A = new ReleaseLink("R1A", "vendor", "component1", "releaseA", "vendor component1 releaseA", false).setReleaseRelationship(ReleaseRelationship.REFERRED).setMainlineState(MainlineState.MAINLINE).setNodeId("R1A").setComponentType(ComponentType.OSS);
        ReleaseLink releaseLinkR1B = new ReleaseLink("R1B", "vendor", "component1", "releaseB", "vendor component1 releaseB", false).setReleaseRelationship(ReleaseRelationship.REFERRED).setMainlineState(MainlineState.MAINLINE).setNodeId("R1B").setComponentType(ComponentType.OSS);
        ReleaseLink releaseLinkR2A = new ReleaseLink("R2A", "vendor", "component2", "releaseA", "vendor component2 releaseA", false).setReleaseRelationship(ReleaseRelationship.REFERRED).setMainlineState(MainlineState.MAINLINE).setNodeId("R2A").setComponentType(ComponentType.COTS);
        ReleaseLink releaseLinkR2B = new ReleaseLink("R2B", "vendor", "component2", "releaseB", "vendor component2 releaseB", false).setReleaseRelationship(ReleaseRelationship.REFERRED).setMainlineState(MainlineState.MAINLINE).setNodeId("R2B").setComponentType(ComponentType.COTS);

        ProjectLink link3 = new ProjectLink("P3", "project3")
                .setRelation(ProjectRelationship.REFERRED)
                .setEnableSvm(true)
                .setNodeId("P3")
                .setParentNodeId("P2")
                .setProjectType(ProjectType.CUSTOMER)
                .setState(ProjectState.ACTIVE)
                .setTreeLevel(2)
                .setLinkedReleases(Arrays.asList(releaseLinkR2A, releaseLinkR2B))
                .setSubprojects(Collections.emptyList());
        ProjectLink link4 = new ProjectLink("P4", "project4")
                .setRelation(ProjectRelationship.CONTAINED)
                .setEnableSvm(true)
                .setNodeId("P4")
                .setParentNodeId("P2")
                .setProjectType(ProjectType.CUSTOMER)
                .setState(ProjectState.ACTIVE)
                .setTreeLevel(2)
                .setSubprojects(Collections.emptyList());
        ProjectLink link2 = new ProjectLink("P2", "project2")
                .setRelation(ProjectRelationship.CONTAINED)
                .setEnableSvm(true)
                .setNodeId("P2")
                .setParentNodeId("P1")
                .setProjectType(ProjectType.CUSTOMER)
                .setState(ProjectState.ACTIVE)
                .setTreeLevel(1)
                .setLinkedReleases(Arrays.asList(releaseLinkR1A, releaseLinkR1B))
                .setSubprojects(Arrays.asList(link3, link4));
        ProjectLink link1 = new ProjectLink("P1", "project1")
                .setRelation(ProjectRelationship.UNKNOWN)
                .setEnableSvm(true)
                .setNodeId("P1")
                .setProjectType(ProjectType.CUSTOMER)
                .setState(ProjectState.ACTIVE)
                .setTreeLevel(0)
                .setSubprojects(Arrays.asList(link2));

        stripRandomPartsOfNodeIds(linkedProjects);
        assertThat(linkedProjects, contains(link1));
    }

    @Test
    public void testGetLinkedProjects2Deep() throws Exception {

        // we wrap the potentially infinite loop in an executor
        final ExecutorService service = Executors.newSingleThreadExecutor();
        
        Project project = handler.getProjectById("P5", user); 

        final Future<List<ProjectLink>> completionFuture = service.submit(() -> handler.getLinkedProjects(project, true, user));

        service.shutdown();
        service.awaitTermination(10, TimeUnit.SECONDS);

        final List<ProjectLink> linkedProjects = completionFuture.get();

        ProjectLink link7_5 = new ProjectLink("P7", "project7")
                .setRelation(ProjectRelationship.CONTAINED)
                .setEnableSvm(true)
                .setNodeId("P7")
                .setProjectType(ProjectType.CUSTOMER)
                .setState(ProjectState.ACTIVE)
                .setTreeLevel(1)
                .setParentNodeId("P5");
        ProjectLink link7_6 = new ProjectLink("P7", "project7")
                .setRelation(ProjectRelationship.CONTAINED)
                .setEnableSvm(true)
                .setNodeId("P7")
                .setProjectType(ProjectType.CUSTOMER)
                .setState(ProjectState.ACTIVE)
                .setTreeLevel(2)
                .setParentNodeId("P6");

        ProjectLink link6 = new ProjectLink("P6", "project6")
                .setRelation(ProjectRelationship.CONTAINED)
                .setEnableSvm(true)
                .setNodeId("P6")
                .setParentNodeId("P5")
                .setProjectType(ProjectType.CUSTOMER)
                .setState(ProjectState.ACTIVE)
                .setTreeLevel(1)
                .setSubprojects(Arrays.asList(link7_6));
        ProjectLink link5 = new ProjectLink("P5", "project5")
                .setRelation(ProjectRelationship.UNKNOWN)
                .setEnableSvm(true)
                .setNodeId("P5")
                .setProjectType(ProjectType.CUSTOMER)
                .setState(ProjectState.ACTIVE)
                .setTreeLevel(0)
                .setSubprojects(Arrays.asList(link6, link7_5));

        stripRandomPartsOfNodeIds(linkedProjects);
        assertThat(linkedProjects, contains(link5));
    }

    @Test
    public void testGetLinkedProjects2Shallow() throws Exception {

        // we wrap the potentially infinite loop in an executor
        final ExecutorService service = Executors.newSingleThreadExecutor();

        Project project = handler.getProjectById("P5", user);

        final Future<List<ProjectLink>> completionFuture = service.submit(() -> handler.getLinkedProjects(project, false, user));

        service.shutdown();
        service.awaitTermination(10, TimeUnit.SECONDS);

        final List<ProjectLink> linkedProjects = completionFuture.get();

        ProjectLink link7_5 = new ProjectLink("P7", "project7")
                .setRelation(ProjectRelationship.CONTAINED)
                .setNodeId("P7")
                .setProjectType(ProjectType.CUSTOMER)
                .setEnableSvm(true)
                .setState(ProjectState.ACTIVE)
                .setTreeLevel(1)
                .setParentNodeId("P5");

        ProjectLink link6 = new ProjectLink("P6", "project6")
                .setRelation(ProjectRelationship.CONTAINED)
                .setNodeId("P6")
                .setParentNodeId("P5")
                .setProjectType(ProjectType.CUSTOMER)
                .setEnableSvm(true)
                .setState(ProjectState.ACTIVE)
                .setTreeLevel(1)
                .setSubprojects(Collections.emptyList());
        ProjectLink link5 = new ProjectLink("P5", "project5")
                .setRelation(ProjectRelationship.UNKNOWN)
                .setEnableSvm(true)
                .setNodeId("P5")
                .setProjectType(ProjectType.CUSTOMER)
                .setState(ProjectState.ACTIVE)
                .setTreeLevel(0)
                .setSubprojects(Arrays.asList(link6, link7_5));

        stripRandomPartsOfNodeIds(linkedProjects);
        assertThat(linkedProjects, contains(link5));
    }

    private void stripRandomPartsOfNodeIds(List<ProjectLink> linkedProjects) {
        linkedProjects.forEach(pl -> {
            if (pl.isSetNodeId()){
                pl.setNodeId(pl.getNodeId().split("_")[0]);
            }
            if (pl.isSetParentNodeId()){
                pl.setParentNodeId(pl.getParentNodeId().split("_")[0]);
            }
            if (pl.isSetSubprojects()) {
                stripRandomPartsOfNodeIds(pl.getSubprojects());
            }
            if (pl.isSetLinkedReleases()){
                pl.getLinkedReleases().forEach(rl -> {
                    if (rl.isSetNodeId()){
                        rl.setNodeId(rl.getNodeId().split("_")[0]);
                    }
                    if (rl.isSetParentNodeId()){
                        rl.setParentNodeId(rl.getParentNodeId().split("_")[0]);
                    }
                });
            }
        });
    }


}
