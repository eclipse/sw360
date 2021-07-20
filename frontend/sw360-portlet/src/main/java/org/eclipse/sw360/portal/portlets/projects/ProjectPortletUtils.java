/*
 * Copyright Siemens AG, 2013-2017, 2019. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.portal.portlets.projects;

import com.google.common.collect.*;

import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.common.SW360Utils;
import org.eclipse.sw360.datahandler.thrift.*;
import org.eclipse.sw360.datahandler.thrift.attachments.*;
import org.eclipse.sw360.datahandler.thrift.components.ReleaseLink;
import org.eclipse.sw360.datahandler.thrift.licenseinfo.LicenseNameWithText;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectLink;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectRelationship;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.ProjectVulnerabilityRating;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.VulnerabilityCheckStatus;
import org.eclipse.sw360.datahandler.thrift.vulnerabilities.VulnerabilityRatingForProject;
import org.eclipse.sw360.portal.common.CustomFieldHelper;
import org.eclipse.sw360.portal.common.PortalConstants;
import org.eclipse.sw360.portal.common.PortletUtils;
import org.eclipse.sw360.portal.users.UserCacheHolder;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.portlet.PortletRequest;
import javax.portlet.ResourceRequest;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.eclipse.sw360.datahandler.common.CommonUtils.arrayToList;
import static org.eclipse.sw360.portal.common.PortalConstants.*;

/**
 * Component portlet implementation
 *
 * @author cedric.bodet@tngtech.com
 * @author Johannes.Najjar@tngtech.com
 * @author alex.borodin@evosoft.com
 * @author thomas.maier@evosoft.com
 */
public class ProjectPortletUtils {

    private static final Logger log = LogManager.getLogger(ProjectPortletUtils.class);

    private ProjectPortletUtils() {
        // Utility class with only static functions
    }

    public static void updateProjectFromRequest(PortletRequest request, Project project) {
        for (Project._Fields field : Project._Fields.values()) {
            switch (field) {
                case LINKED_PROJECTS:
                    if (!project.isSetLinkedProjects()) {
                        project.setLinkedProjects(new HashMap<>());
                    }
                    updateLinkedProjectsFromRequest(request, project.linkedProjects);
                    break;
                case RELEASE_ID_TO_USAGE:
                    if (!project.isSetReleaseIdToUsage()) {
                        project.setReleaseIdToUsage(new HashMap<>());
                    }
                    updateLinkedReleasesFromRequest(request, project.releaseIdToUsage);
                    break;

                case ATTACHMENTS:
                    project.setAttachments(PortletUtils.updateAttachmentsFromRequest(request, project.getAttachments()));
                    break;

                case LICENSE_INFO_HEADER_TEXT:
                    // if `LICENSE_INFO_HEADER_TEXT` is not in the request then we want this to be unset in the `project`
                    String licenseInfoHeader = request.getParameter(field.toString());
                    if(licenseInfoHeader == null) {
                        project.unsetLicenseInfoHeaderText();
                    } else {
                        project.setLicenseInfoHeaderText(StringEscapeUtils.unescapeHtml(licenseInfoHeader));
                    }
                    break;

                case OBLIGATIONS_TEXT:
                    // if `OBLIGATIONS_TEXT` is not in the request then we want this to be unset in the `project`
                    String obligationsText = request.getParameter(field.toString());
                    if(obligationsText == null) {
                        project.unsetObligationsText();
                    } else {
                        project.setObligationsText(StringEscapeUtils.unescapeHtml(obligationsText));
                    }
                    break;

                case ROLES:
                    project.setRoles(PortletUtils.getCustomMapFromRequest(request));
                    break;
                case EXTERNAL_IDS:
                    project.setExternalIds(PortletUtils.getExternalIdMapFromRequest(request));
                    break;
                case ADDITIONAL_DATA:
                    project.setAdditionalData(PortletUtils.getAdditionalDataMapFromRequest(request));
                    break;
                case EXTERNAL_URLS:
                    project.setExternalUrls(PortletUtils.getExternalUrlMapFromRequest(request));
                    break;
                default:
                    setFieldValue(request, project, field);
            }
        }
    }

    public static String getCommentsByTodoId(PortletRequest request, String id) {
        return request.getParameter("projectobligation:" + id);
    }

    private static void updateLinkedReleasesFromRequest(PortletRequest request, Map<String, ProjectReleaseRelationship> releaseUsage) {
        releaseUsage.clear();
        String[] ids = request.getParameterValues(Project._Fields.RELEASE_ID_TO_USAGE.toString() + ReleaseLink._Fields.ID.toString());
        String[] relations = request.getParameterValues(Project._Fields.RELEASE_ID_TO_USAGE.toString() + ProjectReleaseRelationship._Fields.RELEASE_RELATION.toString());
        String[] mainlStates = request.getParameterValues(Project._Fields.RELEASE_ID_TO_USAGE.toString() + ProjectReleaseRelationship._Fields.MAINLINE_STATE.toString());
        String[] comments = request.getParameterValues(Project._Fields.RELEASE_ID_TO_USAGE.toString() + ProjectReleaseRelationship._Fields.COMMENT.toString());
        if (ids != null && relations != null && mainlStates != null && ids.length == relations.length && ids.length == mainlStates.length) {
            for (int k = 0; k < ids.length; ++k) {
                ReleaseRelationship relation = ReleaseRelationship.findByValue(Integer.parseInt(relations[k]));
                MainlineState mainlState = MainlineState.findByValue(Integer.parseInt(mainlStates[k]));
                releaseUsage.put(ids[k], new ProjectReleaseRelationship(relation, mainlState).setComment(comments[k]));
            }
        }
    }

    private static void updateLinkedProjectsFromRequest(PortletRequest request, Map<String, ProjectRelationship> linkedProjects) {
        linkedProjects.clear();
        String[] ids = request.getParameterValues(Project._Fields.LINKED_PROJECTS.toString() + ProjectLink._Fields.ID.toString());
        String[] relations = request.getParameterValues(Project._Fields.LINKED_PROJECTS.toString() + ProjectLink._Fields.RELATION.toString());
        if (ids != null && relations != null && ids.length == relations.length)
            for (int k = 0; k < ids.length; ++k) {
                linkedProjects.put(ids[k], ProjectRelationship.findByValue(Integer.parseInt(relations[k])));
            }
    }

    private static void setFieldValue(PortletRequest request, Project project, Project._Fields field) {
        PortletUtils.setFieldValue(request, project, field, Project.metaDataMap.get(field), "");
    }

    public static ProjectVulnerabilityRating updateProjectVulnerabilityRatingFromRequest(Optional<ProjectVulnerabilityRating> projectVulnerabilityRatings, ResourceRequest request) throws SW360Exception {
        String projectId = request.getParameter(PortalConstants.PROJECT_ID);
        ProjectVulnerabilityRating projectVulnerabilityRating = projectVulnerabilityRatings.orElse(
                new ProjectVulnerabilityRating()
                        .setProjectId(projectId)
                        .setVulnerabilityIdToReleaseIdToStatus(new HashMap<>()));

        if (!projectVulnerabilityRating.isSetVulnerabilityIdToReleaseIdToStatus()) {
            projectVulnerabilityRating.setVulnerabilityIdToReleaseIdToStatus(new HashMap<>());
        }
        Map<String, Map<String, List<VulnerabilityCheckStatus>>> vulnerabilityIdToReleaseIdToStatus = projectVulnerabilityRating.getVulnerabilityIdToReleaseIdToStatus();

        String[] vulnerabilityIds = request.getParameterValues(PortalConstants.VULNERABILITY_IDS + "[]");
        String[] releaseIds = request.getParameterValues(PortalConstants.RELEASE_IDS + "[]");

        if (vulnerabilityIds.length != releaseIds.length) {
            String message = "Length of vulnerabilities (" + vulnerabilityIds.length + ") does not match the length of releases (" + releaseIds.length + ")!";
            log.error(message);
            throw new SW360Exception(message);
        }

        for (int i = 0; i < vulnerabilityIds.length; i++) {
            String vulnerabilityId = vulnerabilityIds[i];
            String releaseId = releaseIds[i];

            Map<String, List<VulnerabilityCheckStatus>> releaseIdToStatus = vulnerabilityIdToReleaseIdToStatus.computeIfAbsent(vulnerabilityId, k -> new HashMap<>());
            List<VulnerabilityCheckStatus> vulnerabilityCheckStatusHistory = releaseIdToStatus.computeIfAbsent(releaseId, k -> new ArrayList<>());
            VulnerabilityCheckStatus vulnerabilityCheckStatus = newVulnerabilityCheckStatusFromRequest(request);
            vulnerabilityCheckStatusHistory.add(vulnerabilityCheckStatus);
        }

        return projectVulnerabilityRating;
    }

    private static VulnerabilityCheckStatus newVulnerabilityCheckStatusFromRequest(ResourceRequest request) {
        VulnerabilityRatingForProject vulnerabilityRatingForProject = VulnerabilityRatingForProject.findByValue(
                Integer.parseInt(request.getParameter(PortalConstants.VULNERABILITY_RATING_VALUE)));

        return new VulnerabilityCheckStatus()
                .setCheckedBy(UserCacheHolder.getUserFromRequest(request).getEmail())
                .setCheckedOn(SW360Utils.getCreatedOn())
                .setComment(request.getParameter(PortalConstants.VULNERABILITY_RATING_COMMENT))
                .setVulnerabilityRating(vulnerabilityRatingForProject)
                .setProjectAction(request.getParameter(PortalConstants.VULNERABILITY_RATING_ACTION));
    }

    static void saveStickyProjectGroup(PortletRequest request, User user, String groupFilterValue) {
        CustomFieldHelper.saveField(request, user, CUSTOM_FIELD_PROJECT_GROUP_FILTER, groupFilterValue);
    }

    static String loadStickyProjectGroup(PortletRequest request, User user) {
        return CustomFieldHelper.loadField(String.class, request, user, CUSTOM_FIELD_PROJECT_GROUP_FILTER).orElse(null);
    }

    public static Map<String, Set<String>> getSelectedReleaseAndAttachmentIdsFromRequest(ResourceRequest request, boolean withPath) {
        Map<String, Set<String>> releaseIdToAttachmentIds = new HashMap<>();
        String[] checkboxes = request.getParameterValues(PortalConstants.LICENSE_INFO_RELEASE_TO_ATTACHMENT);
        if (checkboxes == null) {
            return ImmutableMap.of();
        }

        Arrays.stream(checkboxes).forEach(s -> {
            String[] split = s.split(":");
            if (split.length >= 2) {
                String attachmentId = split[split.length - 1];
                String releaseIdMaybeWithPath;
                if (withPath) {
                    releaseIdMaybeWithPath = Arrays.stream(Arrays.copyOf(split, split.length - 1))
                            .collect(Collectors.joining(":"));
                } else {
                    releaseIdMaybeWithPath = split[split.length - 2];
                }
                if (!releaseIdToAttachmentIds.containsKey(releaseIdMaybeWithPath)) {
                    releaseIdToAttachmentIds.put(releaseIdMaybeWithPath, new HashSet<>());
                }
                releaseIdToAttachmentIds.get(releaseIdMaybeWithPath).add(attachmentId);
            }
        });
        return releaseIdToAttachmentIds;
    }

    /**
     * Returns a map of excluded licenses. They key is an attachment content id, the
     * value is a list of excluded licenses.
     * <p>
     * For this method to work it is crucial that there is a so called
     * "license-store-&lt;attachmentContentId&gt;" map in the session. This map must
     * contain a mapping from a key to a {@link LicenseNameWithText} object.
     *
     * @param attachmentContentIdsWithPath list of attachment content id to check for exclusions in the
     *                             request
     * @param request              the request containing the excluded licenses as parameters
     * @return a map containing the licenses to exclude
     * @see ProjectPortletUtilsTest for a better understanding
     */
    public static Map<String, Set<LicenseNameWithText>> getExcludedLicensesPerAttachmentIdFromRequest(Set<String> attachmentContentIdsWithPath,
                                                                                                      ResourceRequest request) {
        Map<String, Set<LicenseNameWithText>> excludedLicenses = Maps.newHashMap();

        for (String attachmentContentIdWithPath : attachmentContentIdsWithPath) {
            String[] checkboxes = request.getParameterValues(attachmentContentIdWithPath);
            String[] keys = request.getParameterValues(attachmentContentIdWithPath + "_key");
            boolean isOnlyApprovedAttachmentSelected = Boolean.parseBoolean(request.getParameter(PortalConstants.ONLY_APPROVED));
            if (checkboxes == null) {
                // no details present
                continue;
            }

            List<String> pathParts = new ArrayList<String>(Arrays.asList(attachmentContentIdWithPath.split(":")));
            String attachmentCheckStatus = pathParts.get(pathParts.size()-1);
            if (isOnlyApprovedAttachmentSelected && (CommonUtils.isNullEmptyOrWhitespace(attachmentCheckStatus)
                    || CheckStatus.valueOf(attachmentCheckStatus) != CheckStatus.ACCEPTED)) {
                continue;
            }
            pathParts.remove(pathParts.size() - 1);
            if (pathParts.size() > 2) {
                pathParts.remove(pathParts.size() - 2);
            }
            String attachmentContentIdWithPathWithoutRel = pathParts.stream().collect(Collectors.joining(":"));

            @SuppressWarnings("unchecked")
            Map<String, LicenseNameWithText> licenseStore = (Map<String, LicenseNameWithText>) request.getPortletSession()
                    .getAttribute(ProjectPortlet.LICENSE_STORE_KEY_PREFIX + attachmentContentIdWithPathWithoutRel);
            if (licenseStore == null) {
                throw new IllegalStateException(
                        "No license store found for attachment content id with path " + attachmentContentIdWithPathWithoutRel);
            }

            Set<Integer> includedIds = Arrays.stream(checkboxes).map(s -> Integer.valueOf(s)).collect(Collectors.toSet());
            Set<LicenseNameWithText> licenseNameWithTexts = Sets.newHashSet();
            for (int index = 0; index < keys.length; index++) {
                if (includedIds.contains(index)) {
                    // a request will only contain selected ids because unselected checkboxes are
                    // not transferred. Due to that we have to exclude everything that was NOT
                    // transferred
                    continue;
                }

                LicenseNameWithText licenseNameWithText = licenseStore.get(keys[index]);
                if (licenseNameWithText == null) {
                    throw new IllegalStateException("No license found for key " + keys[index]);
                }

                licenseNameWithTexts.add(licenseNameWithText);
            }

            excludedLicenses.put(attachmentContentIdWithPath, licenseNameWithTexts);
        }

        return excludedLicenses;
    }

    public static List<AttachmentUsage> makeLicenseInfoAttachmentUsages(Project project,
            Set<String> selectedAttachmentIdsWithPath, Function<String, UsageData> usageDataGenerator) {
        List<AttachmentUsage> attachmentUsages = Lists.newArrayList();

        for (String attachmentIdWithPath : selectedAttachmentIdsWithPath) {
            AttachmentUsage usage = new AttachmentUsage();
            String[] pathParts = attachmentIdWithPath.split(":");
            usage.setUsedBy(Source.projectId(pathParts[0]));
            usage.setOwner(Source.releaseId(pathParts[pathParts.length - 4]));
            usage.setAttachmentContentId(pathParts[pathParts.length - 2]);

            UsageData usageData = usageDataGenerator.apply(attachmentIdWithPath);
            usage.setUsageData(usageData);

            attachmentUsages.add(usage);
        }

        return attachmentUsages;
    }

    public static List<AttachmentUsage> makeAttachmentUsages(Project project,
            Map<String, Set<String>> selectedReleaseAndAttachmentIds, Function<String, UsageData> usageDataGenerator) {
        List<AttachmentUsage> attachmentUsages = Lists.newArrayList();

        for(String releaseId : selectedReleaseAndAttachmentIds.keySet()) {
            for(String attachmentContentId : selectedReleaseAndAttachmentIds.get(releaseId)) {
                AttachmentUsage usage = new AttachmentUsage();
                usage.setUsedBy(Source.projectId(project.getId()));
                usage.setOwner(Source.releaseId(releaseId));
                usage.setAttachmentContentId(attachmentContentId);

                UsageData usageData = usageDataGenerator.apply(attachmentContentId);
                usage.setUsageData(usageData);

                attachmentUsages.add(usage);
            }
        }

        return attachmentUsages;
    }

    /**
     * Walks through a list of project links and extracts all release attachments
     * with their owner. The returned map is a mapping from a release to its
     * attachment content ids.
     *
     * @param projectLinks
     *            list of project links to walk through
     *
     * @return map of releases and their attachment content ids
     */
    public static Map<Source, Set<String>> extractContainedAttachments(Collection<ProjectLink> projectLinks) {
        Map<Source, Set<String>> attachments = Maps.newHashMap();

        for (ProjectLink projectLink : projectLinks) {
            for (ReleaseLink releaseLink : projectLink.linkedReleases) {
                Set<String> attachmentIds = attachments.getOrDefault(Source.releaseId(releaseLink.getId()), Sets.newHashSet());
                attachmentIds
                        .addAll(releaseLink.getAttachments().stream().map(a -> a.getAttachmentContentId()).collect(Collectors.toList()));
                attachments.put(Source.releaseId(releaseLink.getId()), attachmentIds);
            }
        }

        return attachments;
    }

    public static List<AttachmentUsage> deselectedAttachmentUsagesFromRequest(ResourceRequest request) {
        return makeAttachmentUsagesFromRequestParameters(request, Sets::difference, true);
    }

    public static List<AttachmentUsage> selectedAttachmentUsagesFromRequest(ResourceRequest request) {
        return makeAttachmentUsagesFromRequestParameters(request, Sets::intersection, false);
    }

    private static List<AttachmentUsage> makeAttachmentUsagesFromRequestParameters(ResourceRequest request,
            BiFunction<Set<String>, Set<String>, Set<String>> computeUsagesFromCheckboxes, boolean deselectUsage) {
        final String projectId = request.getParameter(PROJECT_ID);
        Set<String> selectedUsages = new HashSet<>(arrayToList(request.getParameterValues(PROJECT_SELECTED_ATTACHMENT_USAGES)));
        Set<String> changedUsages = new HashSet<>(arrayToList(request.getParameterValues(PROJECT_SELECTED_ATTACHMENT_USAGES_SHADOWS)));
        Set<String> changedIncludeConludedLicenses = new HashSet<>(
                arrayToList(request.getParameterValues(INCLUDE_CONCLUDED_LICENSE_SHADOWS)));
        changedUsages = Sets.union(changedUsages, new HashSet(changedIncludeConludedLicenses));
        List<String> includeConludedLicenses = arrayToList(request.getParameterValues(INCLUDE_CONCLUDED_LICENSE));
        Set<String> usagesSubset = computeUsagesFromCheckboxes.apply(changedUsages, selectedUsages);
        if (deselectUsage) {
            usagesSubset = Sets.union(usagesSubset, new HashSet(changedIncludeConludedLicenses));
        }
        return usagesSubset.stream()
                .map(s -> parseAttachmentUsageFromString(projectId, s, includeConludedLicenses))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static AttachmentUsage parseAttachmentUsageFromString(String projectId, String s, List<String> includeConludedLicense) {
        String[] split = s.split("_");
        if (split.length != 3) {
            log.warn(String.format("cannot parse attachment usage from %s for project id %s", s, projectId));
            return null;
        }

        String releaseId = split[0];
        String type = split[1];
        String attachmentContentId = split[2];
        String projectPath = null;
        if (UsageData._Fields.findByName(type).equals(UsageData._Fields.LICENSE_INFO)) {
            String[] projectPath_releaseId = split[0].split("-");
            if (projectPath_releaseId.length == 2) {
                releaseId = projectPath_releaseId[1];
                projectPath = projectPath_releaseId[0];
            }
        }

        AttachmentUsage usage = new AttachmentUsage(Source.releaseId(releaseId), attachmentContentId, Source.projectId(projectId));
        final UsageData usageData;
        switch (UsageData._Fields.findByName(type)) {
            case LICENSE_INFO:
                LicenseInfoUsage licenseInfoUsage = new LicenseInfoUsage(Collections.emptySet());
                licenseInfoUsage.setIncludeConcludedLicense(includeConludedLicense.contains(s));
                if (projectPath != null) {
                    licenseInfoUsage.setProjectPath(projectPath);
                }
                usageData = UsageData.licenseInfo(licenseInfoUsage);
                break;
            case SOURCE_PACKAGE:
                usageData = UsageData.sourcePackage(new SourcePackageUsage());
                break;
            case MANUALLY_SET:
                usageData = UsageData.manuallySet(new ManuallySetUsage());
                break;
            default:
                throw new IllegalArgumentException("Unexpected UsageData type: " + type);
        }
        usage.setUsageData(usageData);
        return usage;
    }

    /**
     * Here, "equivalent" means an AttachmentUsage should replace another one in the DB, not that they are equal.
     * I.e, they have the same attachmentContentId, owner, usedBy, and same UsageData type.
     */
    @NotNull
    static Predicate<AttachmentUsage> isUsageEquivalent(AttachmentUsage usage) {
        return equivalentUsage -> usage.getAttachmentContentId().equals(equivalentUsage.getAttachmentContentId()) &&
                usage.getOwner().equals(equivalentUsage.getOwner()) &&
                usage.getUsedBy().equals(equivalentUsage.getUsedBy()) &&
                usage.getUsageData().getSetField().equals(equivalentUsage.getUsageData().getSetField());
    }

    static AttachmentUsage mergeAttachmentUsages(AttachmentUsage u1, AttachmentUsage u2) {
        if (u1.getUsageData() == null) {
            if (u2.getUsageData() == null) {
                return u1;
            } else {
                throw new IllegalArgumentException("Cannot merge attachment usages of different usage types");
            }
        } else {
            if (!u1.getUsageData().getSetField().equals(u2.getUsageData().getSetField())) {
                throw new IllegalArgumentException("Cannot merge attachment usages of different usage types");
            }
        }
        AttachmentUsage mergedUsage = u1.deepCopy();
        switch (u1.getUsageData().getSetField()) {
            case LICENSE_INFO:
                mergedUsage.getUsageData().getLicenseInfo().setExcludedLicenseIds(
                        Sets.union(Optional.of(u1)
                                        .map(AttachmentUsage::getUsageData)
                                        .map(UsageData::getLicenseInfo)
                                        .map(LicenseInfoUsage::getExcludedLicenseIds)
                                        .orElse(Collections.emptySet()),
                                Optional.of(u2)
                                        .map(AttachmentUsage::getUsageData)
                                        .map(UsageData::getLicenseInfo)
                                        .map(LicenseInfoUsage::getExcludedLicenseIds)
                                        .orElse(Collections.emptySet())));
                break;
            case SOURCE_PACKAGE:
            case MANUALLY_SET:
                // do nothing
                // source package and manual usages do not have any information to be merged
                break;
            default:
                throw new IllegalArgumentException("Unexpected UsageData type: " + u1.getUsageData().getSetField());
        }

        return mergedUsage;
    }
}
