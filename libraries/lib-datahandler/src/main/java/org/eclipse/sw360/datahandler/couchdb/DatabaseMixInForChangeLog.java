/*
 * Copyright Siemens AG, 2017. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.datahandler.couchdb;

import org.eclipse.sw360.datahandler.thrift.ProjectReleaseRelationship;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.changelogs.ChangeLogs;
import org.eclipse.sw360.datahandler.thrift.changelogs.ChangedFields;
import org.eclipse.sw360.datahandler.thrift.changelogs.ReferenceDocData;
import org.eclipse.sw360.datahandler.thrift.components.COTSDetails;
import org.eclipse.sw360.datahandler.thrift.components.ClearingInformation;
import org.eclipse.sw360.datahandler.thrift.components.EccInformation;
import org.eclipse.sw360.datahandler.thrift.components.Repository;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectProjectRelationship;
import org.eclipse.sw360.datahandler.thrift.projects.ObligationStatusInfo;
import org.eclipse.sw360.datahandler.thrift.vendors.Vendor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Mixin class for storing objects into DB and sending over portlet
 *
 * @author jaideep.palit@siemens.com
 */
public class DatabaseMixInForChangeLog {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties({
        "id",
        "revision",
        "dbName",
        "setChangeTimestamp",
        "setChanges",
        "setDbName",
        "setDocumentId",
        "setDocumentType",
        "setId",
        "setInfo",
        "setOperation",
        "setParentDocId",
        "setReferenceDoc",
        "setRevision",
        "setType",
        "setUserApproved",
        "setUserEdited",
        "type",
        "referenceDocIterator",
        "referenceDocSize",
        "infoSize",
        "changesSize",
        "changesIterator"
    })
    public static abstract class ChangeLogsMixin extends ChangeLogs {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties({
        "setFieldName",
        "setFieldValueNew",
        "setFieldValueOld"
    })
    public static abstract class ChangedFieldsMixin extends ChangedFields {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties({
        "setRefDocOperation",
        "setDbName",
        "dbName",
        "setRefDocId",
        "setRefDocType"
    })
    public static abstract class ReferenceDocDataMixin extends ReferenceDocData {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties({
        "setCheckStatus",
        "setUploadHistory",
        "uploadHistoryIterator",
        "uploadHistorySize",
        "setCheckedOn",
        "setCheckedComment",
        "setCheckedTeam",
        "setCheckedBy",
        "setCreatedTeam",
        "setSha1",
        "setFilename",
        "setCreatedComment",
        "setAttachmentType",
        "setCreatedBy",
        "setCreatedOn",
        "setAttachmentContentId",
        "setUserEdited",
        "type",
        "referenceDocIterator",
        "referenceDocSize",
        "infoSize",
        "changesSize",
        "changesIterator"
    })
    public static abstract class AttachmentMixin extends Attachment {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties({
        "releasesSize",
        "releasesIterator",
        "setReleases",
        "setStatus",
        "setAction",
        "setComment",
        "setText",
        "setModifiedBy",
        "licenseIdsSize",
        "licenseIdsIterator",
        "setLicenseIds",
        "setReleaseIdToAcceptedCLI",
        "releaseIdToAcceptedCLISize",
        "setModifiedOn",
        "setObligationLevel",
        "setObligationType",
        "setId"
    })
    public static abstract class ObligationStatusInfoMixin extends ObligationStatusInfo {
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
        "setAL",
        "setEccStatus",
        "setAssessorContactPerson",
        "setAssessorDepartment",
        "setEccComment",
        "setMaterialIndexNumber",
        "setAssessmentDate",
        "setECCN"
    })
    public static abstract class EccInformationMixin extends EccInformation {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties({
        "setFullname",
        "setId",
        "setRevision",
        "setType",
        "setUrl",
        "setShortname",
        "permissionsSize",
        "setPermissions"
    })
    public static abstract class VendorMixin extends Vendor {
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
        "setEnableSvm",
        "setProjectRelationship"
    })
    public static abstract class ProjectProjectRelationshipMixin extends ProjectProjectRelationship {
    }
}
