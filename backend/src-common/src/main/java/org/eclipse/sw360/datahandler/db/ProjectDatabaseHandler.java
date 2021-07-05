/*
 * Copyright Siemens AG, 2013-2019. Part of the SW360 Portal Project.
 * With contributions by Siemens Healthcare Diagnostics Inc, 2018.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.datahandler.db;

import com.cloudant.client.api.CloudantClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.*;

import org.eclipse.sw360.common.utils.BackendUtils;
import org.eclipse.sw360.components.summary.SummaryType;
import org.eclipse.sw360.datahandler.businessrules.ReleaseClearingStateSummaryComputer;
import org.eclipse.sw360.datahandler.cloudantclient.DatabaseConnectorCloudant;
import org.eclipse.sw360.datahandler.common.*;
import org.eclipse.sw360.datahandler.couchdb.AttachmentConnector;
import org.eclipse.sw360.datahandler.couchdb.AttachmentStreamConnector;
import org.eclipse.sw360.datahandler.entitlement.ProjectModerator;
import org.eclipse.sw360.datahandler.permissions.PermissionUtils;
import org.eclipse.sw360.datahandler.permissions.ProjectPermissions;
import org.eclipse.sw360.datahandler.thrift.*;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentContent;
import org.eclipse.sw360.datahandler.thrift.changelogs.ChangeLogs;
import org.eclipse.sw360.datahandler.thrift.changelogs.Operation;
import org.eclipse.sw360.datahandler.thrift.components.*;
import org.eclipse.sw360.datahandler.thrift.moderation.ModerationRequest;
import org.eclipse.sw360.datahandler.thrift.projects.*;
import org.eclipse.sw360.datahandler.thrift.users.RequestedAction;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.users.UserGroup;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.ProjectVulnerabilityRating;
import org.eclipse.sw360.mail.MailConstants;
import org.eclipse.sw360.mail.MailUtil;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.thrift.TException;
import org.eclipse.sw360.spdx.SpdxBOMImporter;
import org.eclipse.sw360.spdx.SpdxBOMImporterSink;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.eclipse.sw360.datahandler.common.CommonUtils.*;
import static org.eclipse.sw360.datahandler.common.SW360Assert.assertId;
import static org.eclipse.sw360.datahandler.common.SW360Assert.assertNotNull;
import static org.eclipse.sw360.datahandler.common.SW360Assert.fail;
import static org.eclipse.sw360.datahandler.common.SW360Utils.getBUFromOrganisation;
import static org.eclipse.sw360.datahandler.common.SW360Utils.getCreatedOn;
import static org.eclipse.sw360.datahandler.common.SW360Utils.printName;
import static org.eclipse.sw360.datahandler.common.WrappedException.wrapTException;
import static org.eclipse.sw360.datahandler.permissions.PermissionUtils.makePermission;

/**
 * Class for accessing the CouchDB database
 *
 * @author cedric.bodet@tngtech.com
 * @author daniele.fognini@tngtech.com
 * @author alex.borodin@evosoft.com
 * @author thomas.maier@evosoft.com
 * @author ksoranko@verifa.io
 */
public class ProjectDatabaseHandler extends AttachmentAwareDatabaseHandler {

    private static final Logger log = LogManager.getLogger(ProjectDatabaseHandler.class);
    private static final int DELETION_SANITY_CHECK_THRESHOLD = 5;
    private static final String DUMMY_NEW_PROJECT_ID = "newproject";

    private final ProjectRepository repository;
    private final ProjectVulnerabilityRatingRepository pvrRepository;
    private final ObligationListRepository obligationRepository;
    private final ProjectModerator moderator;
    private final AttachmentConnector attachmentConnector;
    private final ComponentDatabaseHandler componentDatabaseHandler;
    private final RelationsUsageRepository relUsageRepository;
    private final ReleaseRepository releaseRepository;
    private final VendorRepository vendorRepository;
    private DatabaseHandlerUtil dbHandlerUtil;
    private final MailUtil mailUtil = new MailUtil();

    // this caching structure is only used for filling clearing state summaries and
    // should be avoided anywhere else so that no other outdated information will be
    // provided
    // for the clearing state it is
    // 1. necessary because they take a long time to get calculated which might be a
    // good reason for service clients to query not for all projects at once, but to
    // divide the queries in subsets of project (and we do not want to load the
    // whole project map for each of this subset queries)
    // 2. okay because the data normally "only moves forward", meaning that already
    // approved clearing reports will not be revoked. So worst case of caching is
    // that outdated data is provided, not wrong one (as it would be okay to see a
    // cleared project that is displayed as not yet cleared - but it won't be okay
    // to see a uncleared project that is displayed as cleared but isn't anymore)
    private static final java.time.Duration ALL_PROJECTS_ID_MAP_CACHE_LIFETIME = java.time.Duration.ofMinutes(2);
    private static final ImmutableList<Project._Fields> listOfStringFieldsInProjToTrim = ImmutableList.of(
            Project._Fields.NAME, Project._Fields.DESCRIPTION, Project._Fields.VERSION, Project._Fields.DOMAIN,
            Project._Fields.BUSINESS_UNIT, Project._Fields.TAG, Project._Fields.PROJECT_RESPONSIBLE,
            Project._Fields.LEAD_ARCHITECT, Project._Fields.PROJECT_OWNER, Project._Fields.OWNER_ACCOUNTING_UNIT,
            Project._Fields.OWNER_GROUP, Project._Fields.OWNER_COUNTRY, Project._Fields.PREEVALUATION_DEADLINE,
            Project._Fields.SYSTEM_TEST_START, Project._Fields.SYSTEM_TEST_END, Project._Fields.DELIVERY_START,
            Project._Fields.CLEARING_SUMMARY, Project._Fields.SPECIAL_RISKS_OSS, Project._Fields.GENERAL_RISKS3RD_PARTY,
            Project._Fields.SPECIAL_RISKS3RD_PARTY, Project._Fields.DELIVERY_CHANNELS,
            Project._Fields.REMARKS_ADDITIONAL_REQUIREMENTS, Project._Fields.OBLIGATIONS_TEXT,
            Project._Fields.LICENSE_INFO_HEADER_TEXT);
    private Map<String, Project> cachedAllProjectsIdMap;
    private Instant cachedAllProjectsIdMapLoadingInstant;

    public ProjectDatabaseHandler(Supplier<CloudantClient> httpClient, String dbName, String attachmentDbName) throws MalformedURLException {
        this(httpClient, dbName, attachmentDbName, new ProjectModerator(),
                new ComponentDatabaseHandler(httpClient,dbName,attachmentDbName),
                new AttachmentDatabaseHandler(httpClient, dbName, attachmentDbName));
    }

    public ProjectDatabaseHandler(Supplier<CloudantClient> httpClient, String dbName, String changeLogDbName, String attachmentDbName) throws MalformedURLException {
        this(httpClient, dbName, changeLogDbName, attachmentDbName, new ProjectModerator(),
                new ComponentDatabaseHandler(httpClient,dbName,attachmentDbName),
                new AttachmentDatabaseHandler(httpClient, dbName, attachmentDbName));
    }

    @VisibleForTesting
    public ProjectDatabaseHandler(Supplier<CloudantClient> httpClient, String dbName, String changeLogsDbName, String attachmentDbName, ProjectModerator moderator,
                                  ComponentDatabaseHandler componentDatabaseHandler,
                                  AttachmentDatabaseHandler attachmentDatabaseHandler) throws MalformedURLException {
        this(httpClient, dbName, attachmentDbName, moderator, componentDatabaseHandler, attachmentDatabaseHandler);
        DatabaseConnectorCloudant db = new DatabaseConnectorCloudant(httpClient, changeLogsDbName);
        this.dbHandlerUtil = new DatabaseHandlerUtil(db);
    }

    @VisibleForTesting
    public ProjectDatabaseHandler(Supplier<CloudantClient> httpClient, String dbName, String attachmentDbName, ProjectModerator moderator,
                                  ComponentDatabaseHandler componentDatabaseHandler,
                                  AttachmentDatabaseHandler attachmentDatabaseHandler) throws MalformedURLException {
        super(attachmentDatabaseHandler);
        DatabaseConnectorCloudant db = new DatabaseConnectorCloudant(httpClient, dbName);

        // Create the repositories
        repository = new ProjectRepository(db);
        pvrRepository = new ProjectVulnerabilityRatingRepository(db);
        obligationRepository = new ObligationListRepository(db);
        relUsageRepository = new RelationsUsageRepository(db);
        vendorRepository = new VendorRepository(db);
        releaseRepository = new ReleaseRepository(db, vendorRepository);

        // Create the moderator
        this.moderator = moderator;

        // Create the attachment connector
        attachmentConnector = new AttachmentConnector(httpClient, attachmentDbName, Duration.durationOf(30, TimeUnit.SECONDS));

        this.componentDatabaseHandler = componentDatabaseHandler;
        DatabaseConnectorCloudant dbChangelogs = new DatabaseConnectorCloudant(httpClient, DatabaseSettings.COUCH_DB_CHANGE_LOGS);
        this.dbHandlerUtil = new DatabaseHandlerUtil(dbChangelogs);
    }

    /////////////////////
    // SUMMARY GETTERS //
    /////////////////////

    public List<Project> getMyProjectsSummary(String user) {
        return repository.getMyProjectsSummary(user);
    }

    public List<Project> getBUProjectsSummary(String organisation) {
        return repository.getBUProjectsSummary(organisation);
    }

    public List<Project> getAccessibleProjectsSummary(User user) {
        return repository.getAccessibleProjectsSummary(user);
    }

    public List<Project> searchByName(String name, User user) {
        return repository.searchByName(name, user);
    }

    /////////////////////////////
    // CREATE CLEARING REQUEST //
    /////////////////////////////

    public AddDocumentRequestSummary createClearingRequest(ClearingRequest clearingRequest, User user, String projectUrl) throws SW360Exception {
        Project project = getProjectById(clearingRequest.getProjectId(), user);
        AddDocumentRequestSummary requestSummary = new AddDocumentRequestSummary().setRequestStatus(AddDocumentRequestStatus.FAILURE);

        if (!isWriteActionAllowedOnProject(project, user)) {
            return requestSummary.setMessage("You do not have WRITE access to the project");
        }

        if (CommonUtils.isNotNullEmptyOrWhitespace(project.getClearingRequestId())) {
            log.warn("Clearing request is already present for the project: " + project.getId());
            return requestSummary.setRequestStatus(AddDocumentRequestStatus.DUPLICATE)
                    .setId(project.getClearingRequestId()).setMessage("Clearing request already present for project");
        }

        if (!(ProjectClearingState.CLOSED.equals(project.getClearingState()) || Visibility.PRIVATE.equals(project.getVisbility()))) {
            clearingRequest.setProjectBU(project.getBusinessUnit());
            String crId = moderator.createClearingRequest(clearingRequest, user);
            if (CommonUtils.isNotNullEmptyOrWhitespace(crId)) {
                project.setClearingRequestId(crId);
                clearingRequest.setId(crId);
                updateProject(project, user);
                sendMailForNewClearing(project, projectUrl, clearingRequest, user);
                return requestSummary.setRequestStatus(AddDocumentRequestStatus.SUCCESS).setId(project.getClearingRequestId());
            } else {
                log.error("Failed to create clearing request for project: " + project.getId());
            }
        } else {
            log.error("Cannot create clearing request for closed or private project: " + project.getId());
        }
        return requestSummary.setMessage("Failed to create clearing request");
    }

    private boolean isWriteActionAllowedOnProject(Project project, User user) {
        return makePermission(project, user).isActionAllowed(RequestedAction.WRITE);
    }

    public RequestStatus sendEmailForClearingRequestUpdate(ClearingRequest clearingRequest, String projectUrl, User user) throws SW360Exception {
        Project project = getProjectById(clearingRequest.getProjectId(), user);
        if (CommonUtils.isNotNullEmptyOrWhitespace(project.getClearingRequestId())) {
            switch (clearingRequest.getClearingState()) {
            case IN_PROGRESS:
            case IN_QUEUE:
            case ACCEPTED:
            case ON_HOLD:
                sendMailForUpdatedCR(project, projectUrl, clearingRequest, user);
                break;

            case CLOSED:
                sendMailForClosedOrRejectedCR(project, clearingRequest, user, true);
                break;

            case NEW:
                sendMailForUpdatedCR(project, projectUrl, clearingRequest, user);
                break;

            case REJECTED:
                sendMailForClosedOrRejectedCR(project, clearingRequest, user, false);
                break;

            default:
                break;
            }
            return RequestStatus.SUCCESS;
        }
        log.error("Failed to send email for change in clearing request, projectId: " + project.getId());
        return RequestStatus.FAILURE;
    }

    ////////////////////////////
    // GET INDIVIDUAL OBJECTS //
    ////////////////////////////

    public Project getProjectById(String id, User user) throws SW360Exception {
        Project project = repository.get(id);
        assertNotNull(project);

        if(!makePermission(project, user).isActionAllowed(RequestedAction.READ)) {
            throw fail(403, "User: %s is not allowed to view the requested project: %s", user.getEmail(), project.getId());
        }

        return project;
    }

    ////////////////////////////
    // ADD INDIVIDUAL OBJECTS //
    ////////////////////////////
    public List<Project> getMyProjectsFull(User user, Map<String, Boolean> userRoles) {
        String userEmail = user.getEmail();
        List<Project> myProjectsFull = repository.getMyProjectsFull(userEmail);
        if (userRoles != null && !userRoles.isEmpty()) {
            Boolean creator = userRoles.get(Project._Fields.CREATED_BY.toString());
            Boolean moderator = userRoles.get(Project._Fields.MODERATORS.toString());
            Boolean contributor = userRoles.get(Project._Fields.CONTRIBUTORS.toString());
            Boolean projectOwner = userRoles.get(Project._Fields.PROJECT_OWNER.toString());
            Boolean leadArchitect = userRoles.get(Project._Fields.LEAD_ARCHITECT.toString());
            Boolean projectResponsible = userRoles.get(Project._Fields.PROJECT_RESPONSIBLE.toString());
            Boolean securityResponsible = userRoles.get(Project._Fields.SECURITY_RESPONSIBLES.toString());

            myProjectsFull = myProjectsFull.stream().filter(ProjectPermissions.isVisible(user)::test)
                    .filter(project -> {
                        if (creator != null && creator && project.getCreatedBy().equals(userEmail)) {
                            return true;
                        } else if (moderator != null && moderator && project.getModerators().contains(userEmail)) {
                            return true;
                        } else if (contributor != null && contributor
                                && project.getContributors().contains(userEmail)) {
                            return true;
                        } else if (projectOwner != null && projectOwner
                                && project.getProjectOwner().equals(userEmail)) {
                            return true;
                        } else if (leadArchitect != null && leadArchitect
                                && project.getLeadArchitect().equals(userEmail)) {
                            return true;
                        } else if (projectResponsible != null && projectResponsible
                                && project.getProjectResponsible().equals(userEmail)) {
                            return true;
                        } else if (securityResponsible != null && securityResponsible
                                && project.getSecurityResponsibles().contains(userEmail)) {
                            return true;
                        }

                        return false;
                    }).collect(Collectors.toList());
        }
        return myProjectsFull;
    }

    public AddDocumentRequestSummary addProject(Project project, User user) throws SW360Exception {
        removeLeadingTrailingWhitespace(project);
        String name = project.getName();
        if (name == null || name.isEmpty()) {
            return new AddDocumentRequestSummary().setRequestStatus(AddDocumentRequestStatus.NAMINGERROR);
        }

        // Prepare project for database
        prepareProject(project);
        if(isDuplicate(project)) {
            final AddDocumentRequestSummary addDocumentRequestSummary = new AddDocumentRequestSummary()
                    .setRequestStatus(AddDocumentRequestStatus.DUPLICATE);
            List<Project> duplicates = repository.searchByNameAndVersion(project.getName(), project.getVersion());
            if (duplicates.size() == 1) {
                duplicates.stream()
                        .map(Project::getId)
                        .forEach(addDocumentRequestSummary::setId);
            }
            return addDocumentRequestSummary;
        }

        if (!isDependenciesExists(project, user)) {
            return new AddDocumentRequestSummary()
                    .setRequestStatus(AddDocumentRequestStatus.INVALID_INPUT);
        }

        // Save creating user
        project.createdBy = user.getEmail();
        project.createdOn = getCreatedOn();
        project.businessUnit = getBUFromOrganisation(user.getDepartment());
        setRequestedDateAndTrimComment(project, null, user);

        // Add project to database and return ID
        repository.add(project);

        dbHandlerUtil.addChangeLogs(project, null, user.getEmail(), Operation.CREATE, null, Lists.newArrayList(),
                null, null);
        sendMailNotificationsForNewProject(project, user.getEmail());
        return new AddDocumentRequestSummary().setId(project.getId()).setRequestStatus(AddDocumentRequestStatus.SUCCESS);
    }

    private boolean isDuplicate(Project project){
        List<Project> duplicates = repository.searchByNameAndVersion(project.getName(), project.getVersion());
        return duplicates.size()>0;
    }
    ///////////////////////////////
    // UPDATE INDIVIDUAL OBJECTS //
    ///////////////////////////////

    public RequestStatus updateProject(Project project, User user) throws SW360Exception {
        removeLeadingTrailingWhitespace(project);
        String name = project.getName();
        if (name == null || name.isEmpty()) {
            return RequestStatus.NAMINGERROR;
        }

        // Prepare project for database
        prepareProject(project);

        Project actual = repository.get(project.getId());

        assertNotNull(project);

        DatabaseHandlerUtil.saveAttachmentInFileSystem(attachmentConnector, actual.getAttachments(),
                project.getAttachments(), user.getEmail(), project.getId());
        if (changeWouldResultInDuplicate(actual, project)) {
            return RequestStatus.DUPLICATE;
        } else if (duplicateAttachmentExist(project)) {
            return RequestStatus.DUPLICATE_ATTACHMENT;
        } else if (!updateProjectAllowed(actual, user)) {
            return RequestStatus.CLOSED_UPDATE_NOT_ALLOWED;
        } else if (!changePassesSanityCheck(project, actual)){
            return RequestStatus.FAILED_SANITY_CHECK;
        } else if (!isDependenciesExists(project, user)) {
            return RequestStatus.INVALID_INPUT;
        } else if (isWriteActionAllowedOnProject(actual, user)) {
            copyImmutableFields(project,actual);
            setRequestedDateAndTrimComment(project, actual, user);
            project.setAttachments( getAllAttachmentsToKeep(toSource(actual), actual.getAttachments(), project.getAttachments()) );
            setReleaseRelations(project, user, actual);
            updateProjectDependentLinkedFields(project, actual);
            repository.update(project);

            List<ChangeLogs> referenceDocLogList=new LinkedList<>();
            Set<Attachment> attachmentsAfter = project.getAttachments();
            Set<Attachment> attachmentsBefore = actual.getAttachments();
            DatabaseHandlerUtil.populateChangeLogsForAttachmentsDeleted(attachmentsBefore, attachmentsAfter,
                    referenceDocLogList, user.getEmail(), project.getId(), Operation.PROJECT_UPDATE,
                    attachmentConnector, false);

            //clean up attachments in database
            attachmentConnector.deleteAttachmentDifference(actual.getAttachments(), project.getAttachments());

            if (CommonUtils.isNotNullEmptyOrWhitespace(actual.getClearingRequestId()) && isLinkedReleaseUpdated(project, actual)) {
                addCommentToClearingRequest(project, actual, user);
            }
            sendMailNotificationsForProjectUpdate(project, user.getEmail());
            dbHandlerUtil.addChangeLogs(project, actual, user.getEmail(), Operation.UPDATE, attachmentConnector,
                    referenceDocLogList, null, null);
            return RequestStatus.SUCCESS;
        } else {
            return moderator.updateProject(project, user);
        }
    }

    private void setRequestedDateAndTrimComment(Project project, Project actual, User user) {
        Set<String> actualReleaseIds = null;
        if (Objects.nonNull(actual) && Objects.nonNull(actual.getReleaseIdToUsage())) {
            actualReleaseIds = CommonUtils.nullToEmptySet(actual.getReleaseIdToUsage().keySet());
        }
        final Set<String> actualReleaseIdsFinal = CommonUtils.nullToEmptySet(actualReleaseIds);
        Set<String> updatedReleaseIds = null;
        Map<String, ProjectReleaseRelationship> updatedProjectReleaseIdToUsage = project.getReleaseIdToUsage();
        if (Objects.nonNull(updatedProjectReleaseIdToUsage)) {
            updatedReleaseIds = CommonUtils.nullToEmptySet(updatedProjectReleaseIdToUsage.keySet());
        } else {
            updatedReleaseIds = new HashSet<>();
        }

        updatedReleaseIds.stream().filter(updatedReleaseId -> !actualReleaseIdsFinal.contains(updatedReleaseId))
                .forEach(updatedReleaseId -> {
                    ProjectReleaseRelationship projectReleaseRelationship = updatedProjectReleaseIdToUsage
                            .get(updatedReleaseId);
                    if (Objects.nonNull(projectReleaseRelationship)) {
                        projectReleaseRelationship.setCreatedOn(SW360Utils.getCreatedOn());
                        projectReleaseRelationship.setCreatedBy(user.getEmail());
                    }
                });

        updatedReleaseIds.stream().filter(commonReleaseId -> actualReleaseIdsFinal.contains(commonReleaseId))
                .forEach(commonReleaseId -> {
                    ProjectReleaseRelationship projectReleaseRelationship = updatedProjectReleaseIdToUsage
                            .get(commonReleaseId);
                    ProjectReleaseRelationship actualProjectReleaseRelationship = actual.getReleaseIdToUsage()
                            .get(commonReleaseId);
                    if (Objects.nonNull(projectReleaseRelationship)
                            && Objects.nonNull(actualProjectReleaseRelationship)) {
                        projectReleaseRelationship.setCreatedOn(actualProjectReleaseRelationship.getCreatedOn());
                        projectReleaseRelationship.setCreatedBy(actualProjectReleaseRelationship.getCreatedBy());
                    }
                });

        if (Objects.nonNull(updatedProjectReleaseIdToUsage)) {
            project.getReleaseIdToUsage().entrySet().stream().forEach(entry -> {
                if (Objects.nonNull(entry.getValue()) && Objects.nonNull(entry.getValue().getComment())) {
                    entry.getValue().setComment(entry.getValue().getComment().trim());
                }
            });
        }
    }

    private boolean isDependenciesExists(Project project, User user) {
        boolean isValidDependentIds = true;
        if (project.isSetReleaseIdToUsage()) {
            Set<String> releaseIds = project.getReleaseIdToUsage().keySet();
            isValidDependentIds = DatabaseHandlerUtil.isAllIdInSetExists(releaseIds, releaseRepository);
        }

        if (isValidDependentIds && project.isSetLinkedProjects()) {
            Set<String> projectIds = project.getLinkedProjects().keySet();
            isValidDependentIds =  DatabaseHandlerUtil.isAllIdInSetExists(projectIds, repository) && verifyLinkedProjectsAreAccessible(projectIds, user);
        }

        if (isValidDependentIds && project.isSetLinkedObligationId()) {
            String obligationId = project.getLinkedObligationId();
            isValidDependentIds = DatabaseHandlerUtil.isAllIdInSetExists(Sets.newHashSet(obligationId), obligationRepository);
        }
        return isValidDependentIds;
    }

    private boolean verifyLinkedProjectsAreAccessible(Set<String> linkedProjectIds, User user) {
        long nonAccessibleProjectIdsCount = 0;
        if (linkedProjectIds != null) {
            nonAccessibleProjectIdsCount = linkedProjectIds.stream().filter(id -> {
                Project project = repository.get(id);
                return !PermissionUtils.makePermission(project, user).isActionAllowed(RequestedAction.READ);
            }).count();
        }

        if (nonAccessibleProjectIdsCount > 0)
            return false;

        return true;

    }

    private void addCommentToClearingRequest(Project updated, Project current, User user) {
        Set<String> currentReleaseIds = CommonUtils.getNullToEmptyKeyset(current.getReleaseIdToUsage());
        Set<String> updatedReleaseIds = CommonUtils.getNullToEmptyKeyset(updated.getReleaseIdToUsage());
        Set<String> allReleaseIds = Sets.newHashSet(currentReleaseIds);
        allReleaseIds.addAll(updatedReleaseIds);
        Set<String> added = Sets.difference(updatedReleaseIds, currentReleaseIds);
        Set<String> removed = Sets.difference(currentReleaseIds, updatedReleaseIds);
        Collection<Release> releases = CommonUtils.nullToEmptyCollection(componentDatabaseHandler.getReleasesForClearingStateSummary(allReleaseIds));
        StringBuilder commentText = new StringBuilder("Linked release(s) are updated for the project.");
        if (CommonUtils.isNotEmpty(added)) {
            Set<String> releaseNames = extractReleaseNameWithId(releases, added);
            commentText.append(System.lineSeparator()).append("Added Release Ids: ").append(SW360Utils.spaceJoiner.join(releaseNames));
        }
        if (CommonUtils.isNotEmpty(removed)) {
            Set<String> releaseNames = extractReleaseNameWithId(releases, removed);
            commentText.append(System.lineSeparator()).append("Removed Release Ids: ").append(SW360Utils.spaceJoiner.join(releaseNames));
        }
        // filter all current releases
        releases = releases.stream().filter(rel -> updatedReleaseIds.contains(rel.getId())).collect(Collectors.toSet());
        Set<String> cotsCompIds = getCotsComponentIdsFromRelease(releases);
        String cotsCompCount = "0";
        if (cotsCompIds.size() > 0) {
            cotsCompCount = appendCompTypeToReleaseVersion(releases, cotsCompIds);
        }
        commentText.append(System.lineSeparator()).append("Total number of COTS components: ").append(cotsCompCount);
        Comment comment = new Comment().setText(commentText.toString()).setCommentedBy(user.getEmail()).setAutoGenerated(true);
        moderator.addCommentToClearingRequest(current.getClearingRequestId(), comment, user);
        sendMailForUpdatedProjectWithClearingRequest(updated, releases, cotsCompCount, user);
    }

    private Set<String> extractReleaseNameWithId(Collection<Release> releases, Collection<String> filterCriteria) {
        return releases.stream().filter(rel -> filterCriteria.contains(rel.getId()))
                .map(rel -> new StringBuilder(System.lineSeparator()).append("\t").append(SW360Utils.printFullname(rel)).append(" (")
                        .append(rel.getId()).append(")").toString())
                .collect(Collectors.toSet());
    }

    public ObligationList getLinkedObligations(String obligationId, User user) throws TException {
        ObligationList obligation = obligationRepository.get(obligationId);
        assertNotNull(obligation);
        assertId(obligation.getProjectId());
        return obligation;
    }

    public RequestStatus addLinkedObligations(ObligationList obligation, User user) throws TException {
        ThriftValidate.prepareProjectObligation(obligation);
        obligationRepository.add(obligation);
        Project project = getProjectById(obligation.getProjectId(), user);
        project.setLinkedObligationId(obligation.getId());
        repository.update(project);
        project.unsetLinkedObligationId();
        dbHandlerUtil.addChangeLogs(obligation, null, user.getEmail(), Operation.CREATE, attachmentConnector,
                Lists.newArrayList(), obligation.getProjectId(), Operation.PROJECT_UPDATE);
        dbHandlerUtil.addChangeLogs(getProjectById(obligation.getProjectId(), user), project, user.getEmail(),
                Operation.UPDATE, attachmentConnector, Lists.newArrayList(), null, Operation.OBLIGATION_ADD);

        return RequestStatus.SUCCESS;
    }

    public RequestStatus updateLinkedObligations(ObligationList obligation, User user) throws TException {
        Project project = getProjectById(obligation.getProjectId(), user);
        ObligationList projectObligationbefore = obligationRepository.get(obligation.getId());
        if (isWriteActionAllowedOnProject(project, user)) {
            obligationRepository.update(obligation);
            dbHandlerUtil.addChangeLogs(obligation, projectObligationbefore, user.getEmail(), Operation.UPDATE,
                    attachmentConnector, Lists.newArrayList(), obligation.getProjectId(), Operation.PROJECT_UPDATE);
            return RequestStatus.SUCCESS;
        }
        return RequestStatus.FAILURE;
    }

    private boolean isLinkedReleaseUpdated(Project updated, Project current) {
        Set<String> updatedReleaseIds = CommonUtils.getNullToEmptyKeyset(updated.getReleaseIdToUsage());
        Set<String> currentReleaseIds = CommonUtils.getNullToEmptyKeyset(current.getReleaseIdToUsage());
        if (updatedReleaseIds.equals(currentReleaseIds)) {
            return false;
        }
        return true;
    }

    private void setReleaseRelations(Project updated, User user, Project current) {
        boolean isMainlineStateDisabled = !(BackendUtils.MAINLINE_STATE_ENABLED_FOR_USER
                || PermissionUtils.isUserAtLeast(UserGroup.CLEARING_ADMIN, user))
                && updated.getReleaseIdToUsageSize() > 0;

        Map<String, ProjectReleaseRelationship> updatedReleaseIdToUsage = updated.getReleaseIdToUsage();

        if ((null == current || current.getReleaseIdToUsageSize() == 0) && isMainlineStateDisabled) {
            updatedReleaseIdToUsage.forEach((k, v) -> v.setMainlineState(MainlineState.OPEN));
        } else if (isMainlineStateDisabled) {
            Map<String, ProjectReleaseRelationship> currentReleaseIdToUsage = current.getReleaseIdToUsage();

            for (Map.Entry<String, ProjectReleaseRelationship> entry : updatedReleaseIdToUsage.entrySet()) {
                ProjectReleaseRelationship prr = currentReleaseIdToUsage.get(entry.getKey());
                if (null != prr) {
                    entry.getValue().setMainlineState(prr.getMainlineState());
                } else {
                    entry.getValue().setMainlineState(MainlineState.OPEN);
                }
            }
        }
    }

    private boolean changeWouldResultInDuplicate(Project before, Project after) {
        if (before.getName().equals(after.getName()) &&
            (
                  (Strings.isNullOrEmpty(before.getVersion()) && Strings.isNullOrEmpty(after.getVersion()))
              ||  (before.getVersion() != null && before.getVersion().equals(after.getVersion()))
            )
        ) {
            // sth else was changed, not one of the duplication relevant properties
            return false;
        }

        return isDuplicate(after);
    }

    private boolean duplicateAttachmentExist(Project project) {
        if(project.attachments != null && !project.attachments.isEmpty()) {
            return AttachmentConnector.isDuplicateAttachment(project.attachments);
        }
        return false;
    }

    private boolean updateProjectAllowed(Project project, User user) {
        if (project.clearingState != null && project.clearingState.equals(ProjectClearingState.CLOSED)
                && !PermissionUtils.isUserAtLeast(UserGroup.SW360_ADMIN, user) && !SW360Utils.isModeratorOrCreator(project, user)) {
            return false;
        }
        return true;
    }

    private ObligationList deleteObligationsOfUnlinkedReleases(Project updated) {
        ObligationList obligation = obligationRepository.get(updated.getLinkedObligationId());
        Set<String> updatedLinkedReleaseIds = nullToEmptyMap(updated.getReleaseIdToUsage()).keySet();

        Map<String, ObligationStatusInfo> updatedOsInfoMap = nullToEmptyMap(obligation.getLinkedObligationStatus());
        for (Iterator<Map.Entry<String, ObligationStatusInfo>> it = updatedOsInfoMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, ObligationStatusInfo> entry = it.next();
            Map<String, String> releaseIdToAcceptedCLI = entry.getValue().getReleaseIdToAcceptedCLI();
            if (releaseIdToAcceptedCLI != null) {
                releaseIdToAcceptedCLI.keySet().retainAll(updatedLinkedReleaseIds);
                if (releaseIdToAcceptedCLI.isEmpty()) {
                    it.remove();
                }
            }
        }
        if(CommonUtils.isNullOrEmptyMap(updatedOsInfoMap)) {
            obligation.unsetLinkedObligationStatus();
            return obligation;
        }
        obligation.setLinkedObligationStatus(updatedOsInfoMap);
        return obligation;
    }

    private void updateProjectDependentLinkedFields(Project updated, Project actual) throws SW360Exception {
        Source usedBy = Source.projectId(updated.getId());
        Set<String> updatedLinkedReleaseIds = nullToEmptyMap(updated.getReleaseIdToUsage()).keySet();
        Set<String> actualLinkedReleaseIds = nullToEmptyMap(actual.getReleaseIdToUsage()).keySet();
        deleteAttachmentUsagesOfUnlinkedReleases(usedBy, updatedLinkedReleaseIds, actualLinkedReleaseIds);

        // update the obligations only if linked obligations were present in current project,
        // and there is change in linked releases in updated project
        if (CommonUtils.isNotNullEmptyOrWhitespace(actual.getLinkedObligationId()) && !actualLinkedReleaseIds.equals(updatedLinkedReleaseIds)) {
            obligationRepository.update(deleteObligationsOfUnlinkedReleases(updated));
        }
    }

    private boolean changePassesSanityCheck(Project updated, Project current) {
        return !(nullToEmptyMap(current.getReleaseIdToUsage()).size() > DELETION_SANITY_CHECK_THRESHOLD &&
                nullToEmptyMap(updated.getReleaseIdToUsage()).size() == 0 ||
                nullToEmptyMap(current.getLinkedProjects()).size() > DELETION_SANITY_CHECK_THRESHOLD &&
                nullToEmptyMap(updated.getLinkedProjects()).size() == 0);
    }

    private void prepareProject(Project project) throws SW360Exception {
        // Prepare project for database
        ThriftValidate.prepareProject(project);

        //add sha1 to attachments if necessary
        if(project.isSetAttachments()) {
            attachmentConnector.setSha1ForAttachments(project.getAttachments());
        }
    }

    public RequestStatus updateProjectFromAdditionsAndDeletions(Project projectAdditions, Project projectDeletions, User user){

        try {
            Project project = getProjectById(projectAdditions.getId(), user);
            project = moderator.updateProjectFromModerationRequest(project, projectAdditions, projectDeletions);
            return updateProject(project, user);
        } catch (SW360Exception e) {
            log.error("Could not get original project when updating from moderation request.");
            return RequestStatus.FAILURE;
        }

    }

    private void copyImmutableFields(Project destination, Project source) {
        ThriftUtils.copyField(source, destination, Project._Fields.CREATED_ON);
        ThriftUtils.copyField(source, destination, Project._Fields.CREATED_BY);
    }

    ///////////////////////////////
    // DELETE INDIVIDUAL OBJECTS //
    ///////////////////////////////

    public RequestStatus deleteProject(String id, User user) throws SW360Exception {
        Project project = repository.get(id);
        assertNotNull(project);

        if (checkIfInUse(id)) {
            return RequestStatus.IN_USE;
        }

        // Remove the project if the user is allowed to do it by himself
        if (makePermission(project, user).isActionAllowed(RequestedAction.DELETE)) {
            removeProjectAndCleanUp(project, user);
            dbHandlerUtil.addChangeLogs(null, project, user.getEmail(), Operation.DELETE, attachmentConnector,
                    Lists.newArrayList(), null, null);
            return RequestStatus.SUCCESS;
        } else {
            return moderator.deleteProject(project, user);
        }
    }

    public boolean checkIfInUse(String projectId) {
        final Set<Project> usingProjects = repository.searchByLinkingProjectId(projectId);
       return !usingProjects.isEmpty();
    }

    private void removeProjectAndCleanUp(Project project, User user) throws SW360Exception {
        attachmentConnector.deleteAttachments(project.getAttachments());
        attachmentDatabaseHandler.deleteUsagesBy(Source.projectId(project.getId()));
        repository.remove(project);
        if (project.isSetLinkedObligationId()) {
            obligationRepository.remove(project.getLinkedObligationId());
        }
        moderator.notifyModeratorOnDelete(project.getId());
        deleteUsedReleaseRelations(project.getId());
        if (CommonUtils.isNotNullEmptyOrWhitespace(project.getClearingRequestId())) {
            moderator.unlinkClearingRequestForProjectDeletion(project, user);
        }
    }

    private void deleteUsedReleaseRelations(String projectId) throws SW360Exception {
        List<UsedReleaseRelations> usedReleaseRelations;
        try {
            usedReleaseRelations = nullToEmptyList(getUsedReleaseRelationsByProjectId(projectId));
            if (CommonUtils.isNotEmpty(usedReleaseRelations)) {
                for (UsedReleaseRelations usedReleaseRelation : usedReleaseRelations) {
                    deleteReleaseRelationsUsage(usedReleaseRelation);
                }
            }
        } catch (TException e) {
            throw new SW360Exception(e.getMessage());
        }
    }

    //////////////////////
    // HELPER FUNCTIONS //
    //////////////////////

    public List<ProjectLink> getLinkedProjects(Project project, boolean deep, User user) {
        Deque<String> visitedIds = new ArrayDeque<>();

        Map<String, ProjectProjectRelationship> fakeRelations = new HashMap<>();
        fakeRelations.put(project.isSetId() ? project.getId() : DUMMY_NEW_PROJECT_ID, new ProjectProjectRelationship(ProjectRelationship.UNKNOWN));
        List<ProjectLink> out = iterateProjectRelationShips(fakeRelations, null, visitedIds, deep ? -1 : 2, user);
        return out;
    }

    public List<ProjectLink> getLinkedProjects(Map<String, ProjectProjectRelationship> relations, User user) {
        List<ProjectLink> out;

        Deque<String> visitedIds = new ArrayDeque<>();
        out = iterateProjectRelationShips(relations, null, visitedIds, -1, user);

        return out;
    }

    private List<ProjectLink> iterateProjectRelationShips(Map<String, ProjectProjectRelationship> relations,
            String parentNodeId, Deque<String> visitedIds, int maxDepth, User user) {
        List<ProjectLink> out = new ArrayList<>();
        for (Map.Entry<String, ProjectProjectRelationship> entry : relations.entrySet()) {
            Optional<ProjectLink> projectLinkOptional = createProjectLink(entry.getKey(), entry.getValue(),
                    parentNodeId, visitedIds, maxDepth, user);
            projectLinkOptional.ifPresent(out::add);
        }
        out.sort(Comparator.comparing(ProjectLink::getName).thenComparing(ProjectLink::getVersion));
        return out;
    }

    private Optional<ProjectLink> createProjectLink(String id, ProjectProjectRelationship projectProjectRelationship, String parentNodeId,
            Deque<String> visitedIds, int maxDepth, User user) {
        ProjectLink projectLink = null;
        if (!visitedIds.contains(id) && (maxDepth < 0 || visitedIds.size() < maxDepth)) {
            visitedIds.push(id);
            Project project = repository.get(id);
            if (project != null
                    && (user == null || !makePermission(project, user).isActionAllowed(RequestedAction.READ))) {
                log.error("User " + user == null ? ""
                        : user.getEmail() + " requested not accessible project " + printName(project));
                project = null;
            }
            if (project != null) {
                projectLink = new ProjectLink(id, project.name);
                if (project.isSetReleaseIdToUsage() && (maxDepth < 0 || visitedIds.size() < maxDepth)){ // ProjectLink on the last level does not get children added
                    List<ReleaseLink> linkedReleases = componentDatabaseHandler.getLinkedReleases(project, visitedIds);
                    fillMainlineStates(linkedReleases, project.getReleaseIdToUsage());
                    projectLink.setLinkedReleases(nullToEmptyList(linkedReleases));
                }

                projectLink
                        .setNodeId(generateNodeId(id))
                        .setParentNodeId(parentNodeId)
                        .setRelation(projectProjectRelationship.getProjectRelationship())
                        .setEnableSvm(projectProjectRelationship.isEnableSvm())
                        .setVersion(project.getVersion())
                        .setState(project.getState())
                        .setProjectType(project.getProjectType())
                        .setClearingState(project.getClearingState())
                        .setTreeLevel(visitedIds.size() - 1);
                if (project.isSetLinkedProjects()) {
                    List<ProjectLink> subprojectLinks = iterateProjectRelationShips(project.getLinkedProjects(),
                            projectLink.getNodeId(), visitedIds, maxDepth, user);
                    projectLink.setSubprojects(subprojectLinks);
                }
            } else {
                log.error("Broken ProjectLink in project with id: " + parentNodeId + ". Linked project with id " + id + " was not found");
            }
            visitedIds.pop();
        }
        return Optional.ofNullable(projectLink);
    }

    private void fillMainlineStates(List<ReleaseLink> linkedReleases, Map<String, ProjectReleaseRelationship> releaseIdToUsage) {
        for (ReleaseLink releaseLink : linkedReleases) {
            releaseLink.setMainlineState(releaseIdToUsage.get(releaseLink.getId()).getMainlineState());
        }
    }

    private String generateNodeId(String id) {
        return id == null ? null : id + "_" + UUID.randomUUID();
    }

    public Set<Project> searchByReleaseId(String id, User user) {
        return repository.searchByReleaseId(id, user);
    }

    public Set<Project> searchByReleaseId(Set<String> ids, User user) {
        return repository.searchByReleaseId(ids, user);
    }

    public Set<Project> searchLinkingProjects(String id, User user) {
        return repository.searchByLinkingProjectId(id, user);
    }

    public Project getProjectForEdit(String id, User user) throws SW360Exception {

        List<ModerationRequest> moderationRequestsForDocumentId = moderator.getModerationRequestsForDocumentId(id);

        Project project = getProjectById(id,user);
        Visibility actualVisbility = project.getVisbility();
        DocumentState documentState;
        if (moderationRequestsForDocumentId.isEmpty()) {
            documentState = CommonUtils.getOriginalDocumentState();
        } else {
            final String email = user.getEmail();
            Optional<ModerationRequest> moderationRequestOptional = CommonUtils.getFirstModerationRequestOfUser(moderationRequestsForDocumentId, email);
            if (moderationRequestOptional.isPresent()
                    && isInProgressOrPending(moderationRequestOptional.get())){
                ModerationRequest moderationRequest = moderationRequestOptional.get();
                project = moderator.updateProjectFromModerationRequest(project,
                        moderationRequest.getProjectAdditions(),
                        moderationRequest.getProjectDeletions());

                if (moderationRequest.getProjectAdditions() != null && moderationRequest.getProjectDeletions() != null
                        && moderationRequest.getProjectAdditions().getVisbility() == moderationRequest
                                .getProjectDeletions().getVisbility()) {
                    project.setVisbility(actualVisbility);
                }
                documentState = CommonUtils.getModeratedDocumentState(moderationRequest);
            } else {
                documentState = new DocumentState().setIsOriginalDocument(true).setModerationState(moderationRequestsForDocumentId.get(0).getModerationState());
            }
        }
        project.setPermissions(makePermission(project, user).getPermissionMap());
        project.setDocumentState(documentState);
        return project;
    }

    public List<Project> getProjectsById(List<String> ids, User user) {

        List<Project> projects = repository.makeSummaryFromFullDocs(SummaryType.SUMMARY, repository.get(ids, true));

        List<Project> output = new ArrayList<>();
        for (Project project : projects) {
            if (makePermission(project, user).isActionAllowed(RequestedAction.READ)) {
                output.add(project);
            } else {
                log.error("User " + user.getEmail() + " requested not accessible project " + printName(project));
            }
        }

        return output;
    }

    public int getCountByReleaseIds(Set<String> ids) {
        return repository.getCountByReleaseIds(ids);
    }

    public int getCountByProjectId(String id) {
        return repository.getCountByProjectId(id);
    }

    public Set<Project> searchByExternalIds(Map<String, Set<String>> externalIds, User user) {
        return repository.searchByExternalIds(externalIds, user);
    }

    public Set<Project> getAccessibleProjects(User user) {
        return repository.getAccessibleProjects(user);
    }

    public Map<String, List<String>> getDuplicateProjects() {
        ListMultimap<String, String> projectIdentifierToReleaseId = ArrayListMultimap.create();

        for (Project project : repository.getAll()) {
            projectIdentifierToReleaseId.put(SW360Utils.printName(project), project.getId());
        }

        return CommonUtils.getIdentifierToListOfDuplicates(projectIdentifierToReleaseId);
    }

    public List<ProjectVulnerabilityRating> getProjectVulnerabilityRatingByProjectId(String projectId){
        return pvrRepository.getProjectVulnerabilityRating(projectId);
    }

    public RequestStatus updateProjectVulnerabilityRating(ProjectVulnerabilityRating link) {
        if( ! link.isSetId()){
            link.setId(SW360Constants.PROJECT_VULNERABILITY_RATING_ID_PREFIX + link.getProjectId());
            pvrRepository.add(link);
        } else {
            pvrRepository.update(link);
        }
        return RequestStatus.SUCCESS;
    }

    public List<ProjectVulnerabilityRating> getProjectVulnerabilityRatingsByReleaseId(String releaseId) {
        return pvrRepository.getProjectVulnerabilityRatingsByReleaseId(releaseId);
    }

    public List<Project> fillClearingStateSummary(List<Project> projects, User user) {
        Function<Project, Set<String>> extractReleaseIds = project -> CommonUtils.nullToEmptyMap(project.getReleaseIdToUsage()).keySet();

        Set<String> allReleaseIds = projects.stream().map(extractReleaseIds).reduce(Sets.newHashSet(), Sets::union);
        if (!allReleaseIds.isEmpty()) {
            Map<String, Release> releasesById = ThriftUtils.getIdMap(componentDatabaseHandler.getReleasesForClearingStateSummary(allReleaseIds));
            for (Project project : projects) {
                final Set<String> releaseIds = extractReleaseIds.apply(project);
                List<Release> releases = releaseIds.stream().map(releasesById::get).collect(Collectors.toList());
                final ReleaseClearingStateSummary releaseClearingStateSummary = ReleaseClearingStateSummaryComputer.computeReleaseClearingStateSummary(releases, project
                        .getClearingTeam());
                project.setReleaseClearingStateSummary(releaseClearingStateSummary);
            }
        }
        return projects;
    }

    private List<Release> getDirectlyLinkedReleasesInNewState(Project project) {
        Set<String> releaseIds = CommonUtils.nullToEmptyMap(project.getReleaseIdToUsage()).keySet();
        List<Release> releases = componentDatabaseHandler.getReleasesForClearingStateSummary(releaseIds);
        return getDirectlyLinkedReleasesInNewState(releases);
    }

    private List<Release> getDirectlyLinkedReleasesInNewState(Collection<Release> releases) {
        return releases.stream().filter(release -> null == release.getClearingState()
                || ClearingState.NEW_CLEARING.equals(release.getClearingState())).collect(Collectors.toList());
    }

    public List<ReleaseClearingStatusData> getReleaseClearingStatuses(String projectId, User user) throws SW360Exception {
        Project project = getProjectById(projectId, user);
        SetMultimap<String, ProjectWithReleaseRelationTuple> releaseIdsToProject = releaseIdToProjects(project, user);
        List<Release> releasesById = componentDatabaseHandler.getDetailedReleasesForExport(releaseIdsToProject.keySet());
        Map<String, Component> componentsById = ThriftUtils.getIdMap(
                componentDatabaseHandler.getComponentsShort(
                        releasesById.stream().map(Release::getComponentId).collect(Collectors.toSet())));

        List<ReleaseClearingStatusData> releaseClearingStatuses = new ArrayList<>();
        for (Release release : releasesById) {
            List<String> projectNames = new ArrayList<>();
            List<String> mainlineStates = new ArrayList<>();

            for (ProjectWithReleaseRelationTuple projectWithReleaseRelation : releaseIdsToProject.get(release.getId())) {
                projectNames.add(printName(projectWithReleaseRelation.getProject()));
                mainlineStates.add(ThriftEnumUtils.enumToString(projectWithReleaseRelation.getRelation().getMainlineState()));
                if (projectNames.size() > 3) {
                    projectNames.add("...");
                    mainlineStates.add("...");
                    break;
                }

            }
            releaseClearingStatuses.add(new ReleaseClearingStatusData(release)
                    .setProjectNames(joinStrings(projectNames))
                    .setMainlineStates(joinStrings(mainlineStates))
                    .setComponentType(componentsById.get(release.getComponentId()).getComponentType()));
        }
        return releaseClearingStatuses;
    }

    SetMultimap<String, ProjectWithReleaseRelationTuple> releaseIdToProjects(Project project, User user) throws SW360Exception {
        Set<String> visitedProjectIds = new HashSet<>();
        SetMultimap<String, ProjectWithReleaseRelationTuple> releaseIdToProjects = HashMultimap.create();

        releaseIdToProjects(project, user, visitedProjectIds, releaseIdToProjects);
        return releaseIdToProjects;
    }

    public String getCyclicLinkedProjectPath(Project project, User user) throws TException {
        return DatabaseHandlerUtil.getCyclicLinkedPath(project, this, user);
    }

    private void releaseIdToProjects(Project project, User user, Set<String> visitedProjectIds, Multimap<String, ProjectWithReleaseRelationTuple> releaseIdToProjects) throws SW360Exception {

        if (nothingTodo(project, visitedProjectIds)) return;

        nullToEmptyMap(project.getReleaseIdToUsage()).forEach((releaseId, relation) -> {
            releaseIdToProjects.put(releaseId, new ProjectWithReleaseRelationTuple(project, relation));
        });

        Map<String, ProjectProjectRelationship> linkedProjects = project.getLinkedProjects();
        if (linkedProjects != null) {

                for (String projectId : linkedProjects.keySet()) {
                    if (visitedProjectIds.contains(projectId)) continue;

                    Project linkedProject = getProjectById(projectId, user);
                    releaseIdToProjects(linkedProject, user, visitedProjectIds, releaseIdToProjects);
                }
        }
    }

    private boolean nothingTodo(Project project, Set<String> visitedProjectIds) {
        if (project == null) {
            return true;
        }
        return alreadyBeenHere(project.getId(), visitedProjectIds);
    }

    private boolean alreadyBeenHere(String id, Set<String> visitedProjectIds) {
        if (visitedProjectIds.contains(id)) {
            return true;
        }
        visitedProjectIds.add(id);
        return false;
    }

    public List<Project> fillClearingStateSummaryIncludingSubprojects(List<Project> projects, User user) {
        final Map<String, Project> allProjectsIdMap = getRefreshedAllProjectsIdMap();

        projects.stream().forEach(project -> {
            // build project tree, get all linked release ids and fetch the releases
            // current decision is to not check any permissions for subproject visibility
            Set<String> releaseIdsOfProjectTree = getReleaseIdsOfProjectTree(project, Sets.newHashSet(),
                    allProjectsIdMap, user, null);
            List<Release> releasesForClearingStateSummary = componentDatabaseHandler
                    .getReleasesForClearingStateSummary(releaseIdsOfProjectTree);
            // compute the summaries
            final ReleaseClearingStateSummary releaseClearingStateSummary = ReleaseClearingStateSummaryComputer
                    .computeReleaseClearingStateSummary(releasesForClearingStateSummary, project.getClearingTeam());

            project.setReleaseClearingStateSummary(releaseClearingStateSummary);
        });

        return projects;
    }

    /**
     * Synchronization is not really necessary, we could also remove it. Worst case
     * would then be that the projects map would be loaded twice if one thread just
     * loaded it but has not yet updated the loadingInstant field while another
     * thread queries already (if we change the order and set the instant first and
     * then load the new map, worst case would be that the other thread would get an
     * older map as the new one is not yet set).
     */
    private synchronized Map<String, Project> getRefreshedAllProjectsIdMap() {
        if (cachedAllProjectsIdMap != null && Instant.now()
                .isBefore(cachedAllProjectsIdMapLoadingInstant.plus(ALL_PROJECTS_ID_MAP_CACHE_LIFETIME))) {
            return cachedAllProjectsIdMap;
        }

        cachedAllProjectsIdMap = ThriftUtils.getIdMap(repository.getAll());
        cachedAllProjectsIdMapLoadingInstant = Instant.now();

        return cachedAllProjectsIdMap;
    }

    private Set<String> getReleaseIdsOfProjectTree(Project project, Set<String> visitedProjectIds,
            Map<String, Project> allProjectsIdMap, User user, List<RequestedAction> permissionsFilter) {
        // no need to visit a project twice
        if (visitedProjectIds.contains(project.getId())) {
            return Collections.emptySet();
        }

        // we are now checking this project so no need for further examination in
        // recursion
        visitedProjectIds.add(project.getId());

        // container to aggregate results
        Set<String> releaseIds = Sets.newHashSet();

        // traverse linked projects with relation type other than "REFERRED" and
        // "DUPLICATE" and add the result to this result
        if (project.isSetLinkedProjects()) {
            project.getLinkedProjects().entrySet().stream().forEach(e -> {
                if (!ProjectRelationship.REFERRED.equals(e.getValue())
                        && !ProjectRelationship.DUPLICATE.equals(e.getValue())) {
                    Project childProject = allProjectsIdMap.get(e.getKey());
                    if (childProject != null) {
                        // since we fetched all project up front we did not yet check any permission -
                        // so do it now
                        if (permissionsFilter == null || permissionsFilter.isEmpty()
                                || makePermission(childProject, user).areActionsAllowed(permissionsFilter)) {
                            // recursion inside :-)
                            releaseIds.addAll(getReleaseIdsOfProjectTree(childProject, visitedProjectIds,
                                    allProjectsIdMap, user, permissionsFilter));
                        }
                    }
                }
            });
        }

        // add own releases to result if they are not just "REFERRED"
        if (project.isSetReleaseIdToUsage()) {
            project.getReleaseIdToUsage().entrySet().stream().forEach(e -> {
                if (!ReleaseRelationship.REFERRED.equals(e.getValue().getReleaseRelation())) {
                    releaseIds.add(e.getKey());
                }
            });
        }

        return releaseIds;
    }

    private Set<String> getCotsComponentIdsFromRelease(Collection<Release> releases) {
        if (CommonUtils.isNullOrEmptyCollection(releases)) {
            return Collections.emptySet();
        }
        Set<String> compIds = releases.stream().map(Release::getComponentId).collect(Collectors.toSet());
        List<Component> components = CommonUtils.nullToEmptyList(componentDatabaseHandler.getComponentsShort(compIds));
        Predicate<Component> cotsFilter = c -> (null != c && ComponentType.COTS.equals(c.getComponentType()));
        return components.stream().filter(cotsFilter).map(Component::getId).collect(Collectors.toSet());
    }

    private String extractReleaseNameForClearingEmail(Collection<Release> releases) {
        if (releases.size() < 1) {
            return "<li>No linked release(s) with clearing state new.</li>";
        }

        StringBuilder builder = new StringBuilder();
        for (Release release : releases) {
            builder.append("<li>").append(SW360Utils.printFullname(release)).append("</li>");
        }
        return builder.toString();
    }

    private String appendCompTypeToReleaseVersion(Collection<Release> releases, Set<String> cotsCompIds) {
        int count = 0;
        for (Release release : releases) {
            if (cotsCompIds.contains(release.getComponentId())) {
                count++;
                release.setVersion(new StringBuilder(CommonUtils.nullToEmptyString(release.getVersion())).append(DatabaseHandlerUtil.SEPARATOR).append(ComponentType.COTS).toString());
            }
        }
        return String.valueOf(count);
    }

    private Map<String, String> getRecipients(ClearingRequest cr) {
        Map<String, String> recipients = Maps.newHashMap();
        recipients.put(ClearingRequest._Fields.REQUESTING_USER.toString(), cr.getRequestingUser());
        recipients.put(ClearingRequest._Fields.CLEARING_TEAM.toString(), cr.getClearingTeam());
        return recipients;
    }

    private String getUserDetails(User user) {
        return new StringBuilder(CommonUtils.nullToEmptyString(user.getUserGroup())).append(MailConstants.DASH).append(SW360Utils.printFullname(user)).toString();
    }

    private void sendMailForNewClearing(Project project, String projectUrl, ClearingRequest clearingRequest, User user) {
        project = fillClearingStateSummary(Arrays.asList(project), user).get(0);
        Set<String> releaseIds = CommonUtils.nullToEmptyMap(project.getReleaseIdToUsage()).keySet();
        Collection<Release> releases = componentDatabaseHandler.getReleasesForClearingStateSummary(releaseIds);

        Set<String> cotsCompIds = getCotsComponentIdsFromRelease(releases);
        String cotsCompCount = "0";
        if (cotsCompIds.size() > 0) {
            cotsCompCount = appendCompTypeToReleaseVersion(releases, cotsCompIds);
        }

        String userDetails = getUserDetails(user);
        int totalCount, approvedCount;
        totalCount = approvedCount = 0;
        if (project.isSetReleaseClearingStateSummary()) {
            ReleaseClearingStateSummary clearingSummary = project.getReleaseClearingStateSummary();
            approvedCount = clearingSummary.getApproved();
            totalCount = SW360Utils.getTotalReleaseCount(clearingSummary);
        }
        releases = CommonUtils.nullToEmptyCollection(getDirectlyLinkedReleasesInNewState(releases));
        StringBuilder commentText = new StringBuilder(extractReleaseNameForClearingEmail(releases));
        mailUtil.sendClearingMail(ClearingRequestEmailTemplate.NEW, MailConstants.SUBJECT_FOR_NEW_CLEARING_REQUEST, getRecipients(clearingRequest),
                userDetails, CommonUtils.nullToEmptyString(clearingRequest.getId()), CommonUtils.nullToEmptyString(projectUrl), SW360Utils.printName(project),
                String.valueOf(project.getLinkedProjectsSize()), String.valueOf(project.getReleaseIdToUsageSize()), String.valueOf(totalCount),
                String.valueOf(approvedCount), clearingRequest.getRequestedClearingDate(), cotsCompCount, commentText.toString());
        if (releases.size() > 0) {
            commentText = new StringBuilder("Linked release(s) with clearing state new:").append(System.lineSeparator()).append(commentText);
        }
        commentText.append(System.lineSeparator()).append("Total number of COTS components: ").append(cotsCompCount);
        Comment comment = new Comment(commentText.toString(), user.getEmail()).setAutoGenerated(true);
        moderator.addCommentToClearingRequest(clearingRequest.getId(), comment, user);
    }

    private void sendMailForUpdatedCR(Project project, String projectUrl, ClearingRequest clearingRequest, User user) {
        List<Release> releases = getDirectlyLinkedReleasesInNewState(project);
        String userDetails = getUserDetails(user);
        mailUtil.sendClearingMail(ClearingRequestEmailTemplate.UPDATED, MailConstants.SUBJECT_FOR_UPDATED_CLEARING_REQUEST, getRecipients(clearingRequest),
                userDetails, CommonUtils.nullToEmptyString(clearingRequest.getId()), CommonUtils.nullToEmptyString(projectUrl), SW360Utils.printName(project),
                CommonUtils.getEnumStringOrNull(clearingRequest.getClearingState()), clearingRequest.getRequestedClearingDate(),
                CommonUtils.nullToEmptyString(clearingRequest.getAgreedClearingDate()), extractReleaseNameForClearingEmail(releases));
    }

    private void sendMailForUpdatedProjectWithClearingRequest(Project updated, Collection<Release> releases, String cotsCompCount, User user) {
        updated = fillClearingStateSummary(Arrays.asList(updated), user).get(0);
        ClearingRequest clearingRequest = moderator.getClearingRequestByProjectId(updated.getId(), user);
        String userDetails = getUserDetails(user);
        int totalCount, approvedCount;
        totalCount = approvedCount = 0;
        if (updated.isSetReleaseClearingStateSummary()) {
            ReleaseClearingStateSummary clearingSummary = updated.getReleaseClearingStateSummary();
            approvedCount = clearingSummary.getApproved();
            totalCount = SW360Utils.getTotalReleaseCount(clearingSummary);
        }
        releases = getDirectlyLinkedReleasesInNewState(releases);
        mailUtil.sendClearingMail(ClearingRequestEmailTemplate.PROJECT_UPDATED, MailConstants.SUBJECT_FOR_UPDATED_PROJECT_WITH_CLEARING_REQUEST,
                getRecipients(clearingRequest), userDetails, SW360Utils.printName(updated), updated.getClearingRequestId(),
                String.valueOf(updated.getLinkedProjectsSize()), String.valueOf(updated.getReleaseIdToUsageSize()), String.valueOf(totalCount),
                String.valueOf(approvedCount), CommonUtils.getEnumStringOrNull(clearingRequest.getClearingState()),
                clearingRequest.getRequestedClearingDate(), CommonUtils.nullToEmptyString(clearingRequest.getAgreedClearingDate()),
                cotsCompCount, extractReleaseNameForClearingEmail(releases));
    }

    private void sendMailForClosedOrRejectedCR(Project project, ClearingRequest clearingRequest, User user, boolean isApproved) {
        mailUtil.sendClearingMail(isApproved ? ClearingRequestEmailTemplate.CLOSED : ClearingRequestEmailTemplate.REJECTED,
                isApproved ? MailConstants.SUBJECT_FOR_CLOSED_CLEARING_REQUEST : MailConstants.SUBJECT_FOR_REJECTED_CLEARING_REQUEST,
                getRecipients(clearingRequest), project.getClearingRequestId(), SW360Utils.printName(project), isApproved ? "closed" : "rejected");
    }

    private void sendMailNotificationsForNewProject(Project project, String user) {
        mailUtil.sendMail(project.getProjectResponsible(),
                MailConstants.SUBJECT_FOR_NEW_PROJECT,
                MailConstants.TEXT_FOR_NEW_PROJECT,
                SW360Constants.NOTIFICATION_CLASS_PROJECT, Project._Fields.PROJECT_RESPONSIBLE.toString(),
                project.getName(), project.getVersion());
        mailUtil.sendMail(project.getProjectOwner(),
                MailConstants.SUBJECT_FOR_NEW_PROJECT,
                MailConstants.TEXT_FOR_NEW_PROJECT,
                SW360Constants.NOTIFICATION_CLASS_PROJECT, Project._Fields.PROJECT_OWNER.toString(),
                project.getName(), project.getVersion());
        mailUtil.sendMail(project.getLeadArchitect(),
                MailConstants.SUBJECT_FOR_NEW_PROJECT,
                MailConstants.TEXT_FOR_NEW_PROJECT,
                SW360Constants.NOTIFICATION_CLASS_PROJECT, Project._Fields.LEAD_ARCHITECT.toString(),
                project.getName(), project.getVersion());
        mailUtil.sendMail(project.getModerators(), user,
                MailConstants.SUBJECT_FOR_NEW_PROJECT,
                MailConstants.TEXT_FOR_NEW_PROJECT,
                SW360Constants.NOTIFICATION_CLASS_PROJECT, Project._Fields.MODERATORS.toString(),
                project.getName(), project.getVersion());
        mailUtil.sendMail(project.getContributors(), user,
                MailConstants.SUBJECT_FOR_NEW_PROJECT,
                MailConstants.TEXT_FOR_NEW_PROJECT,
                SW360Constants.NOTIFICATION_CLASS_PROJECT, Project._Fields.CONTRIBUTORS.toString(),
                project.getName(), project.getVersion());
        mailUtil.sendMail(project.getSecurityResponsibles(), user,
                MailConstants.SUBJECT_FOR_NEW_PROJECT,
                MailConstants.TEXT_FOR_NEW_PROJECT,
                SW360Constants.NOTIFICATION_CLASS_PROJECT, Project._Fields.SECURITY_RESPONSIBLES.toString(),
                project.getName(), project.getVersion());
        mailUtil.sendMail(SW360Utils.unionValues(project.getRoles()), user,
                MailConstants.SUBJECT_FOR_NEW_PROJECT,
                MailConstants.TEXT_FOR_NEW_PROJECT,
                SW360Constants.NOTIFICATION_CLASS_PROJECT, Project._Fields.ROLES.toString(),
                project.getName(), project.getVersion());
    }

    private void sendMailNotificationsForProjectUpdate(Project project, String user) {
        mailUtil.sendMail(project.getProjectResponsible(),
                MailConstants.SUBJECT_FOR_UPDATE_PROJECT,
                MailConstants.TEXT_FOR_UPDATE_PROJECT,
                SW360Constants.NOTIFICATION_CLASS_PROJECT, Project._Fields.PROJECT_RESPONSIBLE.toString(),
                project.getName(), project.getVersion());
        mailUtil.sendMail(project.getProjectOwner(),
                MailConstants.SUBJECT_FOR_UPDATE_PROJECT,
                MailConstants.TEXT_FOR_UPDATE_PROJECT,
                SW360Constants.NOTIFICATION_CLASS_PROJECT, Project._Fields.PROJECT_OWNER.toString(),
                project.getName(), project.getVersion());
        mailUtil.sendMail(project.getLeadArchitect(),
                MailConstants.SUBJECT_FOR_UPDATE_PROJECT,
                MailConstants.TEXT_FOR_UPDATE_PROJECT,
                SW360Constants.NOTIFICATION_CLASS_PROJECT, Project._Fields.LEAD_ARCHITECT.toString(),
                project.getName(), project.getVersion());
        mailUtil.sendMail(project.getModerators(), user,
                MailConstants.SUBJECT_FOR_UPDATE_PROJECT,
                MailConstants.TEXT_FOR_UPDATE_PROJECT,
                SW360Constants.NOTIFICATION_CLASS_PROJECT, Project._Fields.MODERATORS.toString(),
                project.getName(), project.getVersion());
        mailUtil.sendMail(project.getContributors(), user,
                MailConstants.SUBJECT_FOR_UPDATE_PROJECT,
                MailConstants.TEXT_FOR_UPDATE_PROJECT,
                SW360Constants.NOTIFICATION_CLASS_PROJECT, Project._Fields.CONTRIBUTORS.toString(),
                project.getName(), project.getVersion());
        mailUtil.sendMail(project.getSecurityResponsibles(), user,
                MailConstants.SUBJECT_FOR_UPDATE_PROJECT,
                MailConstants.TEXT_FOR_UPDATE_PROJECT,
                SW360Constants.NOTIFICATION_CLASS_PROJECT, Project._Fields.SECURITY_RESPONSIBLES.toString(),
                project.getName(), project.getVersion());
        mailUtil.sendMail(SW360Utils.unionValues(project.getRoles()), user,
                MailConstants.SUBJECT_FOR_UPDATE_PROJECT,
                MailConstants.TEXT_FOR_UPDATE_PROJECT,
                SW360Constants.NOTIFICATION_CLASS_PROJECT, Project._Fields.ROLES.toString(),
                project.getName(), project.getVersion());
    }

    public List<UsedReleaseRelations> getUsedReleaseRelationsByProjectId(String projectId) throws TException {
        return relUsageRepository.getUsedRelationsByProjectId(projectId);
    }

    public void deleteReleaseRelationsUsage(UsedReleaseRelations usedReleaseRelations) throws TException {
        String usedReleaseRelationsId = usedReleaseRelations.getId();
        relUsageRepository.remove(usedReleaseRelationsId);
    }

    public void addReleaseRelationsUsage(UsedReleaseRelations usedReleaseRelations) throws TException {
        relUsageRepository.add(usedReleaseRelations);
    }

    public void updateReleaseRelationsUsage(UsedReleaseRelations usedReleaseRelations) throws TException {
        relUsageRepository.update(usedReleaseRelations);
    }

    public RequestSummary importBomFromAttachmentContent(User user, String attachmentContentId) throws SW360Exception {
        final AttachmentContent attachmentContent = attachmentConnector.getAttachmentContent(attachmentContentId);
        final Duration timeout = Duration.durationOf(30, TimeUnit.SECONDS);
        try {
            final AttachmentStreamConnector attachmentStreamConnector = new AttachmentStreamConnector(timeout);
            try (final InputStream inputStream = attachmentStreamConnector.unsafeGetAttachmentStream(attachmentContent)) {
                final SpdxBOMImporterSink spdxBOMImporterSink = new SpdxBOMImporterSink(user, this, componentDatabaseHandler);
                final SpdxBOMImporter spdxBOMImporter = new SpdxBOMImporter(spdxBOMImporterSink);
                return spdxBOMImporter.importSpdxBOMAsProject(inputStream, attachmentContent);
            }
        } catch (InvalidSPDXAnalysisException | IOException e) {
            throw new SW360Exception(e.getMessage());
        }
    }

    private void removeLeadingTrailingWhitespace(Project project) {
        DatabaseHandlerUtil.trimStringFields(project, listOfStringFieldsInProjToTrim);

        project.setAttachments(DatabaseHandlerUtil.trimSetOfAttachement(project.getAttachments()));

        project.setContributors(DatabaseHandlerUtil.trimSetOfString(project.getContributors()));

        project.setSecurityResponsibles(DatabaseHandlerUtil.trimSetOfString(project.getSecurityResponsibles()));

        project.setModerators(DatabaseHandlerUtil.trimSetOfString(project.getModerators()));

        project.setExternalIds(DatabaseHandlerUtil.trimMapOfStringKeyStringValue(project.getExternalIds()));

        project.setAdditionalData(DatabaseHandlerUtil.trimMapOfStringKeyStringValue(project.getAdditionalData()));

        project.setRoles(DatabaseHandlerUtil.trimMapOfStringKeySetValue(project.getRoles()));

        project.setTag(DatabaseHandlerUtil.trimProjectTag(project.getTag()));
    }

    public List<Map<String, String>> getClearingStateInformationForListView(String projectId, User user)
            throws SW360Exception {
        Project projectById = getProjectById(projectId, user);
        List<Map<String, String>> clearingStatusList = new ArrayList<Map<String, String>>();
        LinkedHashMap<String, String> projectOrigin = new LinkedHashMap<>();
        projectOrigin.put(projectId, SW360Utils.printName(projectById));
        LinkedHashMap<String, String> releaseOrigin = new LinkedHashMap<>();
        Map<String, ProjectProjectRelationship> linkedProjects = projectById.getLinkedProjects();
        Map<String, ProjectReleaseRelationship> releaseIdToUsage = projectById.getReleaseIdToUsage();
        if (linkedProjects != null && !linkedProjects.isEmpty()) {
            flattenClearingStatusForLinkedProject(linkedProjects, projectOrigin, releaseOrigin, clearingStatusList,
                    user);
        }
        if (releaseIdToUsage != null && !releaseIdToUsage.isEmpty()) {
            flattenClearingStatusForReleases(releaseIdToUsage, projectOrigin, releaseOrigin, clearingStatusList, user);
        }

        return clearingStatusList;
    }

    private void flattenClearingStatusForLinkedProject(Map<String, ProjectProjectRelationship> linkedProjects,
            LinkedHashMap<String, String> projectOrigin, LinkedHashMap<String, String> releaseOrigin,
            List<Map<String, String>> clearingStatusList, User user) {

        linkedProjects.entrySet().stream().forEach(lp -> wrapTException(() -> {
            String projId = lp.getKey();
            String relation = ThriftEnumUtils.enumToString(lp.getValue().getProjectRelationship());
            if (projectOrigin.containsKey(projId))
                return;
            Project linkedProjectById = getProjectById(projId, user);
            projectOrigin.put(projId, SW360Utils.printName(linkedProjectById));
            Map<String, String> row = createProjectCSRow(relation, linkedProjectById, clearingStatusList);
            Map<String, ProjectProjectRelationship> subprojects = linkedProjectById.getLinkedProjects();
            Map<String, ProjectReleaseRelationship> linkedReleases = linkedProjectById.getReleaseIdToUsage();

            if (linkedReleases != null && !linkedReleases.isEmpty()) {
                flattenClearingStatusForReleases(linkedReleases, projectOrigin, releaseOrigin, clearingStatusList,
                        user);
            }

            if (subprojects != null && !subprojects.isEmpty()) {
                flattenClearingStatusForLinkedProject(subprojects, projectOrigin, releaseOrigin, clearingStatusList,
                        user);
            }

            projectOrigin.remove(projId);
            row.put("projectOrigin", String.join(" -> ", projectOrigin.values()));
        }));
    }

    private void flattenClearingStatusForReleases(Map<String, ProjectReleaseRelationship> linkedReleases,
            LinkedHashMap<String, String> projectOrigin, LinkedHashMap<String, String> releaseOrigin,
            List<Map<String, String>> clearingStatusList, User user) {

        linkedReleases.entrySet().stream().forEach(rl -> wrapTException(() -> {
            String relation = ThriftEnumUtils.enumToString(rl.getValue().getReleaseRelation());
            String projectMailLineState = ThriftEnumUtils.enumToString(rl.getValue().getMainlineState());
            String comment = rl.getValue().getComment();
            String releaseId = rl.getKey();
            if (releaseOrigin.containsKey(releaseId))
                return;
            Release rel = componentDatabaseHandler.getRelease(releaseId, user);
            Map<String, ReleaseRelationship> releaseIdToRelationship = rel.getReleaseIdToRelationship();
            releaseOrigin.put(releaseId, SW360Utils.printName(rel));
            Map<String, String> row = createReleaseCSRow(relation, projectMailLineState, rel, clearingStatusList, user, comment);
            if (releaseIdToRelationship != null && !releaseIdToRelationship.isEmpty()) {
                flattenlinkedReleaseOfRelease(releaseIdToRelationship, projectOrigin, releaseOrigin, clearingStatusList,
                        user);
            }
            releaseOrigin.remove(releaseId);
            row.put("projectOrigin", String.join(" -> ", projectOrigin.values()));
            row.put("releaseOrigin", String.join(" -> ", releaseOrigin.values()));
        }));
    }

    private void flattenlinkedReleaseOfRelease(Map<String, ReleaseRelationship> releaseIdToRelationship,
            LinkedHashMap<String, String> projectOrigin, LinkedHashMap<String, String> releaseOrigin,
            List<Map<String, String>> clearingStatusList, User user) {
        releaseIdToRelationship.entrySet().stream().forEach(rl -> wrapTException(() -> {
            String relation = ThriftEnumUtils.enumToString(rl.getValue());
            String projectMailLineState = "";
            String releaseId = rl.getKey();
            if (releaseOrigin.containsKey(releaseId))
                return;
            Release rel = componentDatabaseHandler.getRelease(releaseId, user);
            Map<String, ReleaseRelationship> subReleaseIdToRelationship = rel.getReleaseIdToRelationship();
            releaseOrigin.put(releaseId, SW360Utils.printName(rel));
            Map<String, String> row = createReleaseCSRow(relation, projectMailLineState, rel, clearingStatusList, user, "");
            if (subReleaseIdToRelationship != null && !subReleaseIdToRelationship.isEmpty()) {
                flattenlinkedReleaseOfRelease(subReleaseIdToRelationship, projectOrigin, releaseOrigin,
                        clearingStatusList, user);
            }
            releaseOrigin.remove(releaseId);
            row.put("projectOrigin", String.join(" -> ", projectOrigin.values()));
            row.put("releaseOrigin", String.join(" -> ", releaseOrigin.values()));
        }));
    }

    private Map<String, String> createProjectCSRow(String relation, Project prj,
            List<Map<String, String>> clearingStatusList) {
        String projectId = prj.getId();
        Map<String, String> row = new HashMap<>();
        row.put("id", projectId);
        row.put("name", SW360Utils.printName(prj));
        row.put("type", ThriftEnumUtils.enumToString(prj.getProjectType()));
        row.put("relation", relation);
        row.put("isRelease", "false");
        row.put("clearingState", ThriftEnumUtils.enumToString(prj.getClearingState()));
        row.put("projectState", ThriftEnumUtils.enumToString(prj.getState()));
        clearingStatusList.add(row);
        return row;
    }

    private Map<String, String> createReleaseCSRow(String relation, String projectMailLineState, Release rl,
            List<Map<String, String>> clearingStatusList, User user, String comment) throws SW360Exception {
        Map<String, String> row = new HashMap<>();
        Component component = componentDatabaseHandler.getComponent(rl.getComponentId(), user);
        String releaseId = rl.getId();
        row.put("id", releaseId);
        row.put("name", SW360Utils.printName(rl));
        row.put("type", ThriftEnumUtils.enumToString(component.getComponentType()));
        Set<String> collectedLicIds = CommonUtils.nullToEmptySet(rl.getMainLicenseIds());
        row.put("relation", relation);
        row.put("mainLicenses", String.join(",", collectedLicIds));
        row.put("isRelease", "true");
        row.put("releaseMainlineState", ThriftEnumUtils.enumToString(rl.getMainlineState()));
        row.put("clearingState", ThriftEnumUtils.enumToString(rl.getClearingState()));
        row.put("projectMainlineState", projectMailLineState);
        row.put("comment", CommonUtils.nullToEmptyString(comment));
        clearingStatusList.add(row);
        return row;
    }
}
