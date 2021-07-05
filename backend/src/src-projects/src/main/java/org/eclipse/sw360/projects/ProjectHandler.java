/*
 * Copyright Siemens AG, 2013-2018. Part of the SW360 Portal Project.
 * With contributions by Siemens Healthcare Diagnostics Inc, 2018.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.projects;

import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.common.DatabaseSettings;
import org.eclipse.sw360.datahandler.db.ProjectDatabaseHandler;
import org.eclipse.sw360.datahandler.db.ProjectSearchHandler;
import org.eclipse.sw360.datahandler.thrift.AddDocumentRequestSummary;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.SW360Exception;
import org.eclipse.sw360.datahandler.thrift.RequestSummary;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.components.ReleaseClearingStatusData;
import org.eclipse.sw360.datahandler.thrift.projects.ClearingRequest;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectProjectRelationship;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectLink;
import org.eclipse.sw360.datahandler.thrift.projects.ObligationList;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectRelationship;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectService;
import org.eclipse.sw360.datahandler.thrift.projects.UsedReleaseRelations;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.ektorp.http.HttpClient;

import com.cloudant.client.api.CloudantClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.eclipse.sw360.datahandler.common.SW360Assert.*;

/**
 * Implementation of the Thrift service
 *
 * @author cedric.bodet@tngtech.com
 * @author Johannes.Najjar@tngtech.com
 * @author alex.borodin@evosoft.com
 * @author thomas.maier@evosoft.com
 * @author ksoranko@verifa.io
 */
public class ProjectHandler implements ProjectService.Iface {

    private final ProjectDatabaseHandler handler;
    private final ProjectSearchHandler searchHandler;

    ProjectHandler() throws IOException {
        handler = new ProjectDatabaseHandler(DatabaseSettings.getConfiguredClient(), DatabaseSettings.COUCH_DB_DATABASE, DatabaseSettings.COUCH_DB_ATTACHMENTS);
        searchHandler = new ProjectSearchHandler(DatabaseSettings.getConfiguredHttpClient(), DatabaseSettings.getConfiguredClient(), DatabaseSettings.COUCH_DB_DATABASE);
    }

    ProjectHandler(Supplier<CloudantClient> httpClient, String dbName, String attchmntDbName) throws IOException {
        handler = new ProjectDatabaseHandler(httpClient, dbName, attchmntDbName);
        searchHandler = new ProjectSearchHandler(DatabaseSettings.getConfiguredHttpClient(), DatabaseSettings.getConfiguredClient(), dbName);
    }

    ProjectHandler(Supplier<CloudantClient> cClient,Supplier<HttpClient> hClient, String dbName, String changeLogsDbName, String attchmntDbName) throws IOException {
        handler = new ProjectDatabaseHandler(cClient, dbName, changeLogsDbName, attchmntDbName);
        searchHandler = new ProjectSearchHandler(hClient, cClient, dbName);
    }

    /////////////////////
    // SUMMARY GETTERS //
    /////////////////////

    @Override
    public List<Project> search(String text) throws TException {
        return searchHandler.search(text);
    }

    @Override
    public List<Project> refineSearch(String text, Map<String, Set<String>> subQueryRestrictions, User user) throws TException {
        return searchHandler.search(text, subQueryRestrictions, user);
    }

    @Override
    public List<Project> getMyProjects(User user, Map<String, Boolean> userRoles) throws TException {
        assertNotNull(user);
        assertNotEmpty(user.getEmail());

        return handler.getMyProjectsFull(user, userRoles);
    }

    @Override
    public List<Project> getAccessibleProjectsSummary(User user) throws TException {
        assertUser(user);

        return handler.getAccessibleProjectsSummary(user);
    }

    @Override
    public Set<Project> getAccessibleProjects(User user) throws TException {
        assertUser(user);

        return handler.getAccessibleProjects(user);
    }

    @Override
    public List<Project> searchByName(String name, User user) throws TException {
        assertNotEmpty(name);
        assertUser(user);

        return handler.searchByName(name, user);
    }

    @Override
    public Set<Project> searchByReleaseId(String id, User user) throws TException {
        return handler.searchByReleaseId(id, user);
    }

    @Override
    public Set<Project> searchByReleaseIds(Set<String> ids, User user) throws TException {
        assertNotEmpty(ids);
        return handler.searchByReleaseId(ids, user);
    }

    @Override
    public Set<Project> searchLinkingProjects(String id, User user) throws TException {
        assertId(id);
        return handler.searchLinkingProjects(id, user);
    }

    ////////////////////////////
    // CLEARING REQUEST EMAIL //
    ////////////////////////////
    @Override
    public AddDocumentRequestSummary createClearingRequest(ClearingRequest clearingRequest, User user, String projectUrl) throws TException {
        assertNotNull(clearingRequest);
        assertNotEmpty(projectUrl);
        assertUser(user);
        return handler.createClearingRequest(clearingRequest, user, projectUrl);
    }

    ////////////////////////////
    // GET INDIVIDUAL OBJECTS //
    ////////////////////////////

    @Override
    public Project getProjectById(String id, User user) throws SW360Exception {
        assertUser(user);
        assertId(id);

        Project project = handler.getProjectById(id, user);
        assertNotNull(project);

        return project;
    }

    @Override
    public List<Project> getProjectsById(List<String> id, User user) throws TException {
        assertUser(user);
        assertNotNull(id);
        return handler.getProjectsById(id, user);
    }

    @Override
    public Project getProjectByIdForEdit(String id, User user) throws SW360Exception {
        assertUser(user);
        assertId(id);

        Project project = handler.getProjectForEdit(id, user);
        assertNotNull(project);

        return project;
    }

    @Override
    public int getCountByReleaseIds(Set<String> ids) throws TException {
        assertNotEmpty(ids);
        return handler.getCountByReleaseIds(ids);
    }

    @Override
    public int getCountByProjectId(String id) throws TException {
        assertNotEmpty(id);
        return handler.getCountByProjectId(id);
    }

    @Override
    public Set<Project> searchByExternalIds(Map<String, Set<String>> externalIds, User user) throws TException {
        assertNotNull(externalIds);
        assertUser(user);

        return handler.searchByExternalIds(externalIds, user);
    }

    @Override
    public RequestSummary importBomFromAttachmentContent(User user, String attachmentContentId) throws TException {
        assertNotNull(attachmentContentId);
        assertUser(user);
        return handler.importBomFromAttachmentContent(user, attachmentContentId);
    }

    ////////////////////////////
    // ADD INDIVIDUAL OBJECTS //
    ////////////////////////////

    @Override
    public AddDocumentRequestSummary addProject(Project project, User user) throws TException {
        assertNotNull(project);
        assertIdUnset(project.getId());
        assertUser(user);

        return handler.addProject(project, user);
    }

    ///////////////////////////////
    // UPDATE INDIVIDUAL OBJECTS //
    ///////////////////////////////

    @Override
    public RequestStatus updateProject(Project project, User user) throws TException {
        assertNotNull(project);
        assertId(project.getId());
        assertUser(user);

        return handler.updateProject(project, user);
    }

    public RequestStatus updateProjectFromModerationRequest(Project projectAdditions, Project projectDeletions, User user) {
        return handler.updateProjectFromAdditionsAndDeletions(projectAdditions, projectDeletions, user);
    }

    ///////////////////////////////
    // DELETE INDIVIDUAL OBJECTS //
    ///////////////////////////////

    @Override
    public RequestStatus deleteProject(String id, User user) throws TException {
        assertId(id);
        assertUser(user);

        return handler.deleteProject(id, user);
    }


    //////////////////////
    // HELPER FUNCTIONS //
    //////////////////////

    @Override
    public List<ProjectLink> getLinkedProjectsOfProject(Project project, boolean deep, User user) throws TException {
        assertNotNull(project);
        return handler.getLinkedProjects(project, deep, user);
    }

    @Override
    public List<ProjectLink> getLinkedProjectsById(String id, boolean deep, User user) throws TException {
        assertId(id);

        Project project = getProjectById(id, user);
        return getLinkedProjectsOfProject(project, deep, user);
    }

    @Override
    public List<ProjectLink> getLinkedProjects(Map<String, ProjectProjectRelationship> relations, User user) throws TException {
        assertNotNull(relations);
        assertUser(user);

        return handler.getLinkedProjects(relations, user);
    }

    @Override
    public Map<String, List<String>> getDuplicateProjects() throws TException {
        return handler.getDuplicateProjects();
    }

    @Override
    public List<Project> fillClearingStateSummary(List<Project> projects, User user) throws TException {
        return handler.fillClearingStateSummary(projects, user);
    }

    @Override
    public List<Project> fillClearingStateSummaryIncludingSubprojects(List<Project> projects, User user)
            throws TException {
        return handler.fillClearingStateSummaryIncludingSubprojects(projects, user);
    }

    @Override
    public List<ReleaseClearingStatusData> getReleaseClearingStatuses(String projectId, User user) throws SW360Exception {
        return handler.getReleaseClearingStatuses(projectId, user);
    }

    @Override
    public RequestStatus removeAttachmentFromProject(String projectId, User user, String attachmentContentId) throws TException {
        Project projectByIdForEdit = getProjectByIdForEdit(projectId, user);

        Set<Attachment> attachments = projectByIdForEdit.getAttachments();

        Optional<Attachment> attachmentOptional = CommonUtils.getAttachmentOptional(attachmentContentId, attachments);
        if (attachmentOptional.isPresent()) {
            attachments.remove(attachmentOptional.get());
            return updateProject(projectByIdForEdit, user);
        } else {
            return RequestStatus.SUCCESS;
        }
    }

    @Override
    public boolean projectIsUsed(String projectId) throws TException {
        return handler.checkIfInUse(projectId);
    }

    @Override
    public String getCyclicLinkedProjectPath(Project project, User user) throws TException {
        assertNotNull(project);
        assertUser(user);

        return handler.getCyclicLinkedProjectPath(project, user);
    }

    @Override
    public ObligationList getLinkedObligations(String obligationId, User user) throws TException {
        assertId(obligationId);
        assertUser(user);
        return handler.getLinkedObligations(obligationId, user);
    }

    @Override
    public RequestStatus addLinkedObligations(ObligationList obligation, User user) throws TException {
        assertUser(user);
        assertNotNull(obligation);
        assertIdUnset(obligation.getId());
        return handler.addLinkedObligations(obligation, user);
    }

    @Override
    public RequestStatus updateLinkedObligations(ObligationList obligation, User user) throws TException {
        assertNotNull(obligation);
        assertId(obligation.getId());
        assertId(obligation.getProjectId());
        assertUser(user);
        return handler.updateLinkedObligations(obligation, user);
    }

    public void deleteReleaseRelationsUsage(UsedReleaseRelations usedReleaseRelations) throws TException {
        assertNotNull(usedReleaseRelations);
        handler.deleteReleaseRelationsUsage(usedReleaseRelations);
    }

    @Override
    public void addReleaseRelationsUsage(UsedReleaseRelations usedReleaseRelations) throws TException {
        assertNotNull(usedReleaseRelations);
        handler.addReleaseRelationsUsage(usedReleaseRelations);

    }

    @Override
    public void updateReleaseRelationsUsage(UsedReleaseRelations usedReleaseRelations) throws TException {
        assertNotNull(usedReleaseRelations);
        handler.updateReleaseRelationsUsage(usedReleaseRelations);
    }

    @Override
    public List<UsedReleaseRelations> getUsedReleaseRelationsByProjectId(String projectId) throws TException {
        assertNotNull(projectId);
        return handler.getUsedReleaseRelationsByProjectId(projectId);
    }

    @Override
    public List<Map<String, String>> getClearingStateInformationForListView(String projectId,User user) throws SW360Exception {
        assertNotNull(projectId);
        return handler.getClearingStateInformationForListView(projectId,user);
    }
}
