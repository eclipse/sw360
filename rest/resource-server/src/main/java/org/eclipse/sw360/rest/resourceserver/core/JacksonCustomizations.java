/*
 * Copyright Siemens AG, 2017-2019.
 * Copyright Bosch Software Innovations GmbH, 2017-2018.
 * Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.rest.resourceserver.core;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.eclipse.sw360.datahandler.thrift.Comment;
import org.eclipse.sw360.datahandler.thrift.ProjectReleaseRelationship;
import org.eclipse.sw360.datahandler.thrift.VerificationStateInfo;
import org.eclipse.sw360.datahandler.thrift.Visibility;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.changelogs.ChangeLogs;
import org.eclipse.sw360.datahandler.thrift.changelogs.ChangedFields;
import org.eclipse.sw360.datahandler.thrift.changelogs.ReferenceDocData;
import org.eclipse.sw360.datahandler.thrift.components.*;
import org.eclipse.sw360.datahandler.thrift.licenses.License;
import org.eclipse.sw360.datahandler.thrift.licenses.Obligation;
import org.eclipse.sw360.datahandler.thrift.projects.ClearingRequest;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectProjectRelationship;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectState;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectType;
import org.eclipse.sw360.datahandler.thrift.search.SearchResult;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.CVEReference;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.ReleaseVulnerabilityRelation;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.VendorAdvisory;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.Vulnerability;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.VulnerabilityDTO;
import org.eclipse.sw360.rest.resourceserver.core.serializer.JsonProjectRelationSerializer;
import org.eclipse.sw360.rest.resourceserver.core.serializer.JsonReleaseRelationSerializer;
import org.eclipse.sw360.rest.resourceserver.project.EmbeddedProject;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;

@Configuration
public class JacksonCustomizations {
    @Bean
    public Module sw360Module() {
        return new Sw360Module();
    }

    @SuppressWarnings("serial")
    public static class Sw360Module extends SimpleModule {
        public Sw360Module() {
            setMixInAnnotation(MultiStatus.class, MultiStatusMixin.class);
            setMixInAnnotation(Project.class, Sw360Module.ProjectMixin.class);
            setMixInAnnotation(User.class, Sw360Module.UserMixin.class);
            setMixInAnnotation(Component.class, Sw360Module.ComponentMixin.class);
            setMixInAnnotation(Release.class, Sw360Module.ReleaseMixin.class);
            setMixInAnnotation(Attachment.class, Sw360Module.AttachmentMixin.class);
            setMixInAnnotation(Vendor.class, Sw360Module.VendorMixin.class);
            setMixInAnnotation(License.class, Sw360Module.LicenseMixin.class);
            setMixInAnnotation(Obligation.class, Sw360Module.ObligationMixin.class);
            setMixInAnnotation(Vulnerability.class, Sw360Module.VulnerabilityMixin.class);
            setMixInAnnotation(VulnerabilityDTO.class, Sw360Module.VulnerabilityDTOMixin.class);
            setMixInAnnotation(EccInformation.class, Sw360Module.EccInformationMixin.class);
            setMixInAnnotation(EmbeddedProject.class, Sw360Module.EmbeddedProjectMixin.class);
            setMixInAnnotation(ExternalToolProcess.class, Sw360Module.ExternalToolProcessMixin.class);
            setMixInAnnotation(ExternalToolProcessStep.class, Sw360Module.ExternalToolProcessStepMixin.class);
            setMixInAnnotation(COTSDetails.class, Sw360Module.COTSDetailsMixin.class);
            setMixInAnnotation(ClearingInformation.class, Sw360Module.ClearingInformationMixin.class);
            setMixInAnnotation(Repository.class, Sw360Module.RepositoryMixin.class);
            setMixInAnnotation(SearchResult.class, Sw360Module.SearchResultMixin.class);
            setMixInAnnotation(ChangeLogs.class, Sw360Module.ChangeLogsMixin.class);
            setMixInAnnotation(ChangedFields.class, Sw360Module.ChangedFieldsMixin.class);
            setMixInAnnotation(ReferenceDocData.class, Sw360Module.ReferenceDocDataMixin.class);
            setMixInAnnotation(ClearingRequest.class, Sw360Module.ClearingRequestMixin.class);
            setMixInAnnotation(Comment.class, Sw360Module.CommentMixin.class);
            setMixInAnnotation(ProjectReleaseRelationship.class, Sw360Module.ProjectReleaseRelationshipMixin.class);
            setMixInAnnotation(ReleaseVulnerabilityRelation.class, Sw360Module.ReleaseVulnerabilityRelationMixin.class);
            setMixInAnnotation(VerificationStateInfo.class, Sw360Module.VerificationStateInfoMixin.class);
            setMixInAnnotation(ProjectProjectRelationship.class, Sw360Module.ProjectProjectRelationshipMixin.class);
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        static abstract class MultiStatusMixin extends MultiStatus {
            @JsonProperty("status")
            abstract public int getStatusCode();
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
                "id",
                "revision",
                "attachments",
                "createdBy",
                "visbility",
                "clearingTeam",
                "homepage",
                "wiki",
                "documentState",
                "releaseClearingStateSummary",
                "permissions",
                "attachmentsIterator",
                "moderatorsIterator",
                "contributorsIterator",
                "releaseIdsIterator",
                "setId",
                "setRevision",
                "setType",
                "setName",
                "setDescription",
                "setDomain",
                "setVersion",
                "setExternalIds",
                "setAttachments",
                "setCreatedOn",
                "setState",
                "setProjectType",
                "setTag",
                "setCreatedBy",
                "setModerators",
                "setVisbility",
                "setHomepage",
                "externalIdsSize",
                "attachmentsSize",
                "setBusinessUnit",
                "setProjectResponsible",
                "setLeadArchitect",
                "moderatorsSize",
                "contributorsSize",
                "setContributors",
                "linkedProjectsSize",
                "setLinkedProjects",
                "releaseIdToUsageSize",
                "setReleaseIdToUsage",
                "setClearingTeam",
                "setPreevaluationDeadline",
                "setSystemTestStart",
                "setClearingSummary",
                "setObligationsText",
                "setSpecialRisksOSS",
                "setGeneralRisks3rdParty",
                "setSpecialRisks3rdParty",
                "setDeliveryChannels",
                "setRemarksAdditionalRequirements",
                "setSystemTestEnd",
                "setDeliveryStart",
                "setPhaseOutSince",
                "setDocumentState",
                "releaseIdsSize",
                "setReleaseClearingStateSummary",
                "permissionsSize",
                "setWiki",
                "setReleaseIds",
                "setPermissions",
                "setClearingState",
                "securityResponsiblesSize",
                "securityResponsiblesIterator",
                "setSecurityResponsibles",
                "setOwnerGroup",
                "setOwnerCountry",
                "rolesSize",
                "setRoles",
                "setOwnerAccountingUnit",
                "setLicenseInfoHeaderText",
                "setProjectOwner",
                "setEnableSvm",
                "setEnableVulnerabilitiesDisplay",
                "additionalDataSize",
                "setAdditionalData",
                "setLinkedObligationId",
                "linkedObligationId",
                "setClearingRequestId",
                "externalUrlsSize",
                "setExternalUrls",
                "externalUrls"
        })
        static abstract class ProjectMixin extends Project {

            @Override
            @JsonProperty("projectType")
            abstract public ProjectType getProjectType();

            @Override
            @JsonSerialize(using = JsonProjectRelationSerializer.class)
            @JsonProperty("linkedProjects")
            abstract public Map<String, ProjectProjectRelationship> getLinkedProjects();

            @Override
            @JsonSerialize(using = JsonReleaseRelationSerializer.class)
            @JsonProperty("linkedReleases")
            abstract public Map<String, ProjectReleaseRelationship> getReleaseIdToUsage();

            @Override
            @JsonProperty("visibility")
            abstract public Visibility getVisbility();

            @Override
            @JsonProperty("id")
            abstract public String getId();

            @Override
            @JsonProperty(access = Access.WRITE_ONLY)
            abstract public Set<String> getContributors();

            @Override
            @JsonProperty(access = Access.WRITE_ONLY)
            abstract public Set<String> getModerators();

            @Override
            @JsonProperty(access = Access.WRITE_ONLY)
            abstract public String getLeadArchitect();

            @Override
            @JsonProperty(access = Access.READ_ONLY)
            abstract public String getClearingRequestId();
        }

	static abstract class EmbeddedProjectMixin extends ProjectMixin {
            @Override
            @JsonIgnore
            abstract public boolean isEnableSvm();

            @Override
            @JsonIgnore
            abstract public boolean isEnableVulnerabilitiesDisplay();

            @Override
            @JsonIgnore
            abstract public ProjectState getState();
	}

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
                "id",
                "revision",
                "externalid",
                "wantsMailNotification",
                "setWantsMailNotification",
                "setId",
                "setRevision",
                "setType",
                "setEmail",
                "setUserGroup",
                "setExternalid",
                "setFullname",
                "setGivenname",
                "setLastname",
                "setDepartment",
                "notificationPreferencesSize",
                "setNotificationPreferences",
                "formerEmailAddressesSize",
                "formerEmailAddressesIterator",
                "setFormerEmailAddresses",
                "setCommentMadeDuringModerationRequest",
                "restApiTokens",
                "restApiTokensSize",
                "setRestApiTokens",
                "restApiTokensIterator",
                "myProjectsPreferenceSelection",
                "myProjectsPreferenceSelectionSize",
                "setMyProjectsPreferenceSelection",
                "secondaryDepartmentsAndRolesSize",
                "setSecondaryDepartmentsAndRoles",
                "primaryRoles",
                "primaryRolesSize",
                "setPrimaryRoles",
                "setDeactivated"
        })
        static abstract class UserMixin extends User {
            @Override
            @JsonProperty("fullName")
            abstract public String getFullname();

            @Override
            @JsonProperty("givenName")
            abstract public String getGivenname();

            @Override
            @JsonProperty("lastName")
            abstract public String getLastname();
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(value = {
                "id",
                "revision",
                "attachments",
                "createdBy",
                "subscribers",
                "moderators",
                "releases",
                "mainLicenseIds",
                "softwarePlatforms",
                "wiki",
                "blog",
                "wikipedia",
                "openHub",
                "documentState",
                "permissions",
                "setId",
                "setRevision",
                "setType",
                "setName",
                "setDescription",
                "setAttachments",
                "setCreatedOn",
                "setCreatedBy",
                "setSubscribers",
                "setModerators",
                "releasesSize",
                "setReleases",
                "setReleaseIds",
                "setDefaultVendor",
                "setDefaultVendorId",
                "setCategories",
                "languagesSize",
                "setLanguages",
                "setVendorNames",
                "setHomepage",
                "setMailinglist",
                "setWiki",
                "setBlog",
                "setWikipedia",
                "setOpenHub",
                "setPermissions",
                "attachmentsSize",
                "attachmentsIterator",
                "setComponentType",
                "subscribersSize",
                "subscribersIterator",
                "moderatorsSize",
                "moderatorsIterator",
                "releasesIterator",
                "releaseIdsSize",
                "releaseIdsIterator",
                "mainLicenseIdsSize",
                "mainLicenseIdsIterator",
                "setMainLicenseIds",
                "categoriesSize",
                "categoriesIterator",
                "languagesIterator",
                "softwarePlatformsSize",
                "softwarePlatformsIterator",
                "setExternalIds",
                "externalIdsSize",
                "setSoftwarePlatforms",
                "operatingSystemsSize",
                "operatingSystemsIterator",
                "setOperatingSystems",
                "vendorNamesSize",
                "vendorNamesIterator",
                "setDocumentState",
                "permissionsSize",
                "setComponentOwner",
                "setOwnerAccountingUnit",
                "setOwnerGroup",
                "setOwnerCountry",
                "rolesSize",
                "setRoles",
                "additionalDataSize",
                "setAdditionalData",
        })
        static abstract class ComponentMixin extends Component {
            @Override
            @JsonProperty(PropertyKeyMapping.COMPONENT_VENDOR_KEY_JSON)
            abstract public Set<String> getVendorNames();
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
                "id",
                "revision",
                "attachments",
                "permissions",
                "moderators",
                "clearingInformation",
                "setAttachments",
                "setCreatedOn",
                "setRepository",
                "setCreatedBy",
                "setModerators",
                "setSubscribers",
                "setVendor",
                "setVendorId",
                "languagesSize",
                "setLanguages",
                "setCotsDetails",
                "setSourceCodeDownloadurl",
                "setPermissions",
                "externalIdsSize",
                "attachmentsIterator",
                "attachmentsSize",
                "setMainlineState",
                "setClearingState",
                "contributorsSize",
                "setContributors",
                "moderatorsSize",
                "moderatorsIterator",
                "subscribersSize",
                "setClearingInformation",
                "operatingSystemsSize",
                "setOperatingSystems",
                "mainLicenseIdsSize",
                "setMainLicenseIds",
                "releaseIdToRelationshipSize",
                "setReleaseIdToRelationship",
                "setDocumentState",
                "permissionsSize",
                "setId",
                "setRevision",
                "setType",
                "setCpeid",
                "setName",
                "setVersion",
                "setComponentId",
                "setReleaseDate",
                "setExternalIds",
                "externalToolProcessesSize",
                "setExternalToolProcesses",
                "setEccInformation",
                "languagesIterator",
                "operatingSystemsIterator",
                "cotsDetails",
                "releaseIdToRelationship",
                "documentState",
                "contributorsIterator",
                "rolesSize",
                "setRoles",
                "setCreatorDepartment",
                "setSoftwarePlatforms",
                "softwarePlatformsSize",
                "softwarePlatformsIterator",
                "additionalDataSize",
                "setAdditionalData",
                "mainLicenseIdsIterator",
                "setBinaryDownloadurl",
                "setBinaryDownloadurl",
                "otherLicenseIds",
                "otherLicenseIdsSize",
                "setOtherLicenseIds"
        })
        static abstract class ReleaseMixin extends Release {
            @Override
            @JsonProperty("cpeId")
            abstract public String getCpeid();

            @Override
            @JsonProperty("eccInformation")
            abstract public EccInformation getEccInformation();
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
                "attachmentContentId",
                "setAttachmentContentId",
                "setAttachmentType",
                "setCreatedComment",
                "setCheckedComment",
                "uploadHistory",
                "uploadHistorySize",
                "uploadHistoryIterator",
                "setUploadHistory",
                "setFilename",
                "setSha1",
                "setCreatedBy",
                "setCreatedTeam",
                "setCreatedOn",
                "setCheckedBy",
                "setCheckedTeam",
                "setCheckedOn",
                "setCheckStatus"
        })
        static abstract class AttachmentMixin {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
                "id",
                "revision",
                "permissionsSize",
                "setId",
                "setRevision",
                "setType",
                "setPermissions",
                "setFullname",
                "setShortname",
                "setUrl"
        })
        static abstract class VendorMixin extends Vendor {
            @Override
            @JsonProperty("fullName")
            abstract public String getFullname();

            @Override
            @JsonProperty("shortName")
            abstract public String getShortname();
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
                "id",
                "revision",
                "licenseType",
                "licenseTypeDatabaseId",
                "externalLicenseLink",
                "reviewdate",
                "obligations",
                "obligationDatabaseIds",
                "osiapproved",
                "fsflibre",
                "documentState",
                "permissions",
                "setId",
                "setRevision",
                "setType",
                "setExternalIds",
                "externalIdsSize",
                "setDocumentState",
                "permissionsSize",
                "setLicenseTypeDatabaseId",
                "setExternalLicenseLink",
                "obligationDatabaseIdsSize",
                "obligationDatabaseIdsIterator",
                "setObligationDatabaseIds",
                "setPermissions",
                "setFullname",
                "setShortname",
                "setLicenseType",
                "setOSIApproved",
                "setFSFLibre",
                "setReviewdate",
                "obligationsSize",
                "obligationsIterator",
                "setObligations",
                "risksSize",
                "risksIterator",
                "setText",
                "mainLicenseIdsIterator",
                "setChecked",
                "additionalDataSize",
                "setAdditionalData",
        })
        static abstract class LicenseMixin extends License {
            @Override
            @JsonProperty("fullName")
            abstract public String getFullname();

            @Override
            @JsonProperty("shortName")
            abstract public String getShortname();
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
                "id",
                "revision",
                "whitelist",
                "whitelistSize",
                "whitelistIterator",
                "development",
                "distribution",
                "customPropertyToValueSize",
                "developmentString",
                "distributionString",
                "externalIds",
                "externalIdsSize",
                "comments",
                "additionalData",
                "additionalDataSize",
                "setId",
                "setRevision",
                "setType",
                "setText",
                "setWhitelist",
                "setDevelopment",
                "setDistribution",
                "setTitle",
                "setCustomPropertyToValue",
                "setDevelopmentString",
                "setDistributionString",
                "setExternalIds",
                "setComments",
                "setObligationLevel",
                "setObligationType",
                "setAdditionalData"
        })
        static abstract class ObligationMixin extends Obligation {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
                "id",
                "revision",
                "type",
                "title",
                "description",
                "publishDate",
                "lastExternalUpdate",
                "priorityToolTip",
                "impact",
                "legalNotice",
                "cveReferences",
                "references",
                "intComponentId",
                "intComponentName",
                "releaseVulnerabilityRelation",
                "matchedBy",
                "usedNeedle",
                "setType",
                "setId",
                "setRevision",
                "setIntReleaseName",
                "setLastExternalUpdate",
                "setIntComponentId",
                "setIntComponentName",
                "referencesSize",
                "setPriorityToolTip",
                "setCveReferences",
                "assignedExtComponentIdsIterator",
                "vendorAdvisoriesIterator",
                "setIntReleaseId",
                "cveReferencesSize",
                "setDescription",
                "setReleaseVulnerabilityRelation",
                "setImpact",
                "setMatchedBy",
                "setLegalNotice",
                "setUsedNeedle",
                "setReferences",
                "setPriority",
                "setAction",
                "impactSize",
                "setExternalId",
                "setPublishDate",
                "setTitle",
                "referencesIterator",
                "cveReferencesIterator",
                "setProjectRelevance",
                "setComment"
        })
        static abstract class VulnerabilityDTOMixin extends VulnerabilityDTO {
            @Override
            @JsonProperty("id")
            abstract public String getId();

            @Override
            @JsonProperty("intReleaseId")
            abstract public String getIntReleaseId();

            @Override
            @JsonProperty("intReleaseName")
            abstract public String getIntReleaseName();

            @Override
            @JsonProperty(access = Access.WRITE_ONLY)
            abstract public String getExternalId();
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
                "id",
                "revision",
                "type",
                "publishDate",
                "lastExternalUpdate",
                "priority",
                "priorityToolTip",
                "action",
                "impact",
                "legalNotice",
                "cveReferences",
                "references",
                "intComponentId",
                "intComponentName",
                "releaseVulnerabilityRelation",
                "matchedBy",
                "usedNeedle",
                "setType",
                "setId",
                "setRevision",
                "setIntReleaseName",
                "setLastExternalUpdate",
                "setIntComponentId",
                "setIntComponentName",
                "referencesSize",
                "setPriorityToolTip",
                "setCveReferences",
                "setIntReleaseId",
                "cveReferencesSize",
                "setDescription",
                "setReleaseVulnerabilityRelation",
                "setImpact",
                "setMatchedBy",
                "setLegalNotice",
                "setUsedNeedle",
                "setReferences",
                "setPriority",
                "setAction",
                "impactSize",
                "setExternalId",
                "setPublishDate",
                "setTitle",
                "cvss",
                "isSetCvss",
                "cvssTime",
                "vulnerableConfiguration",
                "access",
                "cveFurtherMetaDataPerSource",
                "setLastUpdateDate",
                "setPriorityText",
                "cveReferencesIterator",
                "setCveFurtherMetaDataPerSource",
                "setAssignedExtComponentIds",
                "referencesIterator",
                "setVulnerableConfiguration",
                "setExtendedDescription",
                "vulnerableConfigurationSize",
                "assignedExtComponentIdsSize",
                "assignedExtComponentIdsIterator",
                "vendorAdvisoriesIterator",
                "vendorAdvisoriesSize",
                "setVendorAdvisories",
                "cveFurtherMetaDataPerSourceSize",
                "setCvss",
                "setCwe",
                "setIsSetCvss",
                "setCvssTime",
                "setAccess",
                "accessSize",
        })
        static abstract class VulnerabilityMixin extends Vulnerability {
            @Override
            @JsonProperty("id")
            abstract public String getId();
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonIgnoreProperties({
                "assessorContactPerson",
                "assessorDepartment",
                "eccComment",
                "materialIndexNumber",
                "assessmentDate",
                "setEccComment",
                "setECCN",
                "setEccStatus",
                "setAL",
                "setAssessorContactPerson",
                "setAssessmentDate",
                "setAssessorDepartment",
                "setMaterialIndexNumber"
        })
        static abstract class EccInformationMixin extends EccInformation {
            @Override
            @JsonProperty("eccStatus")
            abstract public ECCStatus getEccStatus();
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonIgnoreProperties({
            "id",
            "processIdInTool",
            "setProcessIdInTool",
            "setAttachmentId",
            "setAttachmentHash",
            "setId",
            "setExternalTool",
            "setProcessStatus",
            "processStepsIterator",
            "setProcessSteps",
            "processStepsSize"
        })
        static abstract class ExternalToolProcessMixin extends ExternalToolProcess {
        }

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonIgnoreProperties({
            "id",
            "linkToStep",
            "setProcessIdInTool",
            "userIdInTool",
            "userCredentialsInTool",
            "userGroupInTool",
            "setId",
            "setStepName",
            "setStepStatus",
            "setLinkToStep",
            "setStartedBy",
            "setStartedByGroup",
            "setStartedOn",
            "setProcessStepIdInTool",
            "setUserIdInTool",
            "setUserCredentialsInTool",
            "setUserGroupInTool",
            "setFinishedOn",
            "setResult"
        })
        static abstract class ExternalToolProcessStepMixin extends ExternalToolProcessStep {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "setExternalSupplierID",
            "setAdditionalRequestInfo",
            "setEvaluated",
            "setProcStart",
            "setRequestID",
            "setClearingTeam",
            "setRequestorPerson",
            "setBinariesOriginalFromCommunity",
            "setBinariesSelfMade",
            "setComponentLicenseInformation",
            "setSourceCodeDelivery",
            "setSourceCodeOriginalFromCommunity",
            "setSourceCodeToolMade",
            "setSourceCodeSelfMade",
            "setSourceCodeCotsAvailable",
            "setScreenshotOfWebSite",
            "setFinalizedLicenseScanReport",
            "setLicenseScanReportResult",
            "setLegalEvaluation",
            "setLicenseAgreement",
            "setScanned",
            "setComponentClearingReport",
            "setClearingStandard",
            "setReadmeOssAvailable",
            "setCountOfSecurityVn",
            "setExternalUrl",
            "setComment"
        })
        public static abstract class ClearingInformationMixin extends ClearingInformation {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "setUsedLicense",
            "setLicenseClearingReportURL",
            "setContainsOSS",
            "setOssContractSigned",
            "setOssInformationURL",
            "setUsageRightAvailable",
            "setCotsResponsible",
            "setClearingDeadline",
            "setSourceCodeAvailable"
        })
        public static abstract class COTSDetailsMixin extends COTSDetails {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "setUrl",
            "setRepositorytype"
        })
        public static abstract class RepositoryMixin extends Repository {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "score",
            "details",
            "setScore",
            "detailsSize",
            "detailsIterator",
            "setDetails",
            "setId",
            "setType",
            "setName"
        })
        public static abstract class SearchResultMixin extends SearchResult {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "revision",
            "dbName",
            "setId",
            "setType",
            "setDocumentId",
            "setParentDocId",
            "setDbName",
            "changesSize",
            "changesIterator",
            "setChanges",
            "setOperation",
            "setUserEdited",
            "setChangeTimestamp",
            "referenceDocSize",
            "referenceDocIterator",
            "setReferenceDoc",
            "infoSize",
            "setInfo",
            "setRevision",
            "setDocumentType"
        })
        @JsonRootName(value = "changeLog")
        public static abstract class ChangeLogsMixin extends ChangeLogs {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "setFieldName",
            "setFieldValueOld",
            "setFieldValueNew"
        })
        public static abstract class ChangedFieldsMixin extends ChangedFields {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "setDbName",
            "setRefDocId",
            "setRefDocType",
            "setRefDocOperation",
            "dbName"
        })
        public static abstract class ReferenceDocDataMixin extends ReferenceDocData {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "revision",
            "setId",
            "type",
            "setType",
            "timestamp",
            "timestampOfDecision",
            "reOpenOnSize",
            "reOpenOnIterator",
            "setReOpenOn",
            "setClearingState",
            "setRevision",
            "setProjectId",
            "setClearingTeam",
            "setTimestamp",
            "setTimestampOfDecision",
            "setRequestingUser",
            "setComments",
            "setModifiedOn",
            "setRequestedClearingDate",
            "setProjectBU",
            "setRequestingUserComment",
            "setAgreedClearingDate",
            "commentsIterator",
            "modifiedOn",
            "commentsSize",
            "setPriority"
        })
        @JsonRootName(value = "clearingRequest")
        public static abstract class ClearingRequestMixin extends ClearingRequest {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "setText",
            "setCommentedBy",
            "commentedOn",
            "setCommentedOn",
            "autoGenerated",
            "setAutoGenerated"
        })
        public static abstract class CommentMixin extends Comment {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "setComment",
            "setMainlineState",
            "setReleaseRelation",
            "setCreatedOn",
            "setCreatedBy"
        })
        public static abstract class ProjectReleaseRelationshipMixin extends ProjectReleaseRelationship {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "setId",
            "setType",
            "setRevision",
            "setYear",
            "setNumber",
            "type"
        })
        public static abstract class CVEReferenceMixin extends CVEReference {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "setId",
            "setType",
            "setRevision",
            "setName",
            "setVendor",
            "setUrl",
            "type"
        })
        public static abstract class VendorAdvisoryMixin extends VendorAdvisory {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
                "revision",
                "id",
                "type",
                "setType",
                "setId",
                "setRevision",
                "setLastExternalUpdate",
                "referencesSize",
                "setCveReferences",
                "cveReferencesSize",
                "setDescription",
                "setImpact",
                "setMatchedBy",
                "setLegalNotice",
                "setUsedNeedle",
                "setReferences",
                "setPriority",
                "setAction",
                "impactSize",
                "setExternalId",
                "setPublishDate",
                "setTitle",
                "setLastUpdateDate",
                "setPriorityText",
                "cveReferencesIterator",
                "setCveFurtherMetaDataPerSource",
                "setAssignedExtComponentIds",
                "referencesIterator",
                "setVulnerableConfiguration",
                "setExtendedDescription",
                "vulnerableConfigurationSize",
                "assignedExtComponentIdsSize",
                "assignedExtComponentIdsIterator",
                "vendorAdvisoriesIterator",
                "vendorAdvisoriesSize",
                "setVendorAdvisories",
                "cveFurtherMetaDataPerSourceSize",
                "setCvss",
                "setCwe",
                "setIsSetCvss",
                "setCvssTime",
                "setAccess",
                "accessSize",
                "isSetCvss"
        })
        public static abstract class VulnerabilityMixinForCreateUpdate extends Vulnerability {
            @Override
            @JsonProperty("id")
            abstract public String getId();
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "id",
            "revision",
            "type",
            "setId",
            "setRevision",
            "setType",
            "setVerificationStateInfo",
            "setVulnerabilityId",
            "verificationStateInfoSize",
            "verificationStateInfoIterator",
            "setMatchedBy",
            "setUsedNeedle",
            "setReleaseId"
        })
        public static abstract class ReleaseVulnerabilityRelationMixin extends ReleaseVulnerabilityRelation {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "setCheckedBy",
            "setCheckedOn",
            "setComment",
            "setVerificationState"
        })
        public static abstract class VerificationStateInfoMixin extends VerificationStateInfo {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties({
            "setEnableSvm",
            "setProjectRelationship"
        })
        public static abstract class ProjectProjectRelationshipMixin extends ProjectProjectRelationship {
        }
    }
}
