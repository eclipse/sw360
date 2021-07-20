/*
 * Copyright Siemens AG, 2016-2017, 2019. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.portal.tags;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.util.ResourceBundleUtil;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.thrift.TException;
import org.apache.thrift.meta_data.FieldMetaData;
import org.eclipse.sw360.datahandler.thrift.ThriftClients;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectProjectRelationship;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectLink;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectRelationship;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectService;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.portal.common.PortalConstants;
import org.eclipse.sw360.portal.tags.urlutils.LinkedReleaseRenderer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import static org.eclipse.sw360.datahandler.common.CommonUtils.add;
import static org.eclipse.sw360.datahandler.common.CommonUtils.nullToEmptyMap;
import static org.eclipse.sw360.portal.tags.TagUtils.*;

/**
 * Display the fields that have changed in the project
 *
 * @author birgit.heydenreich@tngtech.com
 * @author alex.borodin@evosoft.com
 */
public class DisplayProjectChanges extends UserAwareTag {
    private Project actual;
    private Project additions;
    private Project deletions;
    private String tableClasses = "";
    private String idPrefix = "";
    private String defaultLicenseInfoHeaderText = PortalConstants.DEFAULT_LICENSE_INFO_HEADER_TEXT_FOR_DISPALY;
    private String defaultObligationsText = PortalConstants.DEFAULT_OBLIGATIONS_TEXT_FOR_DISPALY;

    public void setActual(Project actual) {
        this.actual = prepareLicenseInfoHeaderTextInProject(actual);
    }

    public void setAdditions(Project additions) {
        this.additions = prepareLicenseInfoHeaderTextInProject(additions);
    }

    public void setDeletions(Project deletions) {
        this.deletions = prepareLicenseInfoHeaderTextInProject(deletions);
    }

    public void setTableClasses(String tableClasses) {
        this.tableClasses = tableClasses;
    }

    public void setIdPrefix(String idPrefix) {
        this.idPrefix = idPrefix;
    }

    public void setDefaultLicenseInfoHeaderText(String defaultLicenseInfoHeaderText) {
        this.defaultLicenseInfoHeaderText = defaultLicenseInfoHeaderText;
    }

    public void setDefaultObligationsText(String defaultObligationsText) {
        this.defaultObligationsText = defaultObligationsText;
    }

    public int doStartTag() throws JspException {

        JspWriter jspWriter = pageContext.getOut();

        StringBuilder display = new StringBuilder();
        String namespace = getNamespace();

        if (additions == null || deletions == null) {
            return SKIP_BODY;
        }

        try {
            for (Project._Fields field : Project._Fields.values()) {
                switch (field) {
                    //ignored Fields
                    case ID:
                    case REVISION:
                    case TYPE:
                    case CREATED_BY:
                    case CREATED_ON:
                    case PERMISSIONS:
                    case RELEASE_CLEARING_STATE_SUMMARY:
                    case DOCUMENT_STATE:
                        //Taken care of externally
                    case ATTACHMENTS:

                        //Done in extra tables
                    case LINKED_PROJECTS:
                    case RELEASE_ID_TO_USAGE:
                        break;

                    default:
                        FieldMetaData fieldMetaData = Project.metaDataMap.get(field);
                        displaySimpleFieldOrSet(display, actual, additions, deletions, field, fieldMetaData, "");
                }
            }

            String renderString = display.toString();
            HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
            ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());

            if (Strings.isNullOrEmpty(renderString)) {
                renderString = "<div class=\"alert alert-info\">"+LanguageUtil.get(resourceBundle,"no.changes.in.basic.fields")+"</div>";
            } else {
                renderString = String.format("<table class=\"%s\" id=\"%schanges\" >", tableClasses, idPrefix)
                        + "<thead>"
                        + String.format("<tr><th>%s</th><th>%s</th><th>%s</th><th>%s</th></tr></thead><tbody>",
                        LanguageUtil.get(resourceBundle,"field.name"), LanguageUtil.get(resourceBundle,"current.value"), LanguageUtil.get(resourceBundle,"former.value"), LanguageUtil.get(resourceBundle,"suggested.value"))
                        + renderString + "</tbody></table>";
            }

            StringBuilder linkedProjectsDisplay = new StringBuilder();
            User user = getUserFromContext("Cannot render project changes without logged in user in request");
            renderLinkedProjects(linkedProjectsDisplay, user);

            StringBuilder releaseUsageDisplay = new StringBuilder();
            renderReleaseIdToUsage(releaseUsageDisplay, user);

            jspWriter.print(renderString + linkedProjectsDisplay.toString() + releaseUsageDisplay.toString());
        } catch (Exception e) {
            throw new JspException(e);
        }
        return SKIP_BODY;
    }

    private void renderLinkedProjects(StringBuilder display, User user) {
       HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
       ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());
       if (ensureSomethingTodoAndNoNullLinkedProjects()) {

            Set<String> changedProjectIds = Sets.intersection(additions.getLinkedProjects().keySet(),
                                                              deletions.getLinkedProjects().keySet());
            Set<String> linkedProjectsInDb = nullToEmptyMap(actual.getLinkedProjects()).keySet();
            //keep only projects that are still in the database
            changedProjectIds = Sets.intersection(changedProjectIds, linkedProjectsInDb );

            Set<String> removedProjectIds = Sets.difference(deletions.getLinkedProjects().keySet(), changedProjectIds);
            removedProjectIds = Sets.intersection(removedProjectIds, linkedProjectsInDb);

            Set<String> addedProjectIds = Sets.difference(additions.getLinkedProjects().keySet(), changedProjectIds);

            renderProjectLinkList(display, deletions.getLinkedProjects(), removedProjectIds, LanguageUtil.get(resourceBundle,"removed.project.links"), user);
            renderProjectLinkList(display, additions.getLinkedProjects(), addedProjectIds, LanguageUtil.get(resourceBundle,"added.project.links"), user);
            renderProjectLinkListCompare(
                    display,
                    actual.getLinkedProjects(),
                    deletions.getLinkedProjects(),
                    additions.getLinkedProjects(),
                    changedProjectIds, user);
        }
    }

    private boolean ensureSomethingTodoAndNoNullLinkedProjects() {
        if (!deletions.isSetLinkedProjects() && !additions.isSetLinkedProjects()) {
            return false;
        }
        if(!deletions.isSetLinkedProjects()){
            deletions.setLinkedProjects(new HashMap<>());
        }
        if(!additions.isSetLinkedProjects()){
            additions.setLinkedProjects(new HashMap<>());
        }
        return true;
    }

    private void renderProjectLinkList(StringBuilder display,
                                       Map<String, ProjectProjectRelationship> projectRelationshipMap,
                                       Set<String> projectIds,
                                       String msg,
                                       User user) {
        if (projectIds.isEmpty()) return;

        Map<String, ProjectProjectRelationship> filteredMap = new HashMap<>();
        for(String id : projectIds){
            filteredMap.put(id, projectRelationshipMap.get(id));
        }
        StringBuilder candidate = new StringBuilder();
        try {
            ProjectService.Iface client = new ThriftClients().makeProjectClient();
            for (ProjectLink projectLink : client.getLinkedProjects(filteredMap, user)) {
                candidate.append(
                        String.format("<tr><td>%s</td><td>%s</td><td>%s</td></tr>", projectLink.getName(), projectLink.getRelation(), projectLink.isEnableSvm()));
            }
        } catch (TException ignored) {
        }
        String tableContent = candidate.toString();
        HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());

        if (!tableContent.isEmpty()) {

            display.append(String.format("<table class=\"%s\" id=\"%s%s\" >", tableClasses, idPrefix, msg));
            display.append(String.format("<thead><tr><th colspan=\"3\">%s</th></tr>" +
                    "<tr><th>"+LanguageUtil.get(resourceBundle,"project.name")+"</th><th>"+LanguageUtil.get(resourceBundle,"project.relationship")+"</th>" +
                    "<th>"+LanguageUtil.get(resourceBundle,"enable.svm") + "</th>"+ "</tr></thead><tbody>", msg));
            display.append(tableContent);
            display.append("</tbody></table>");
        }
    }


    private void renderProjectLinkListCompare(StringBuilder display,
                                              Map<String, ProjectProjectRelationship> oldProjectRelationshipMap,
                                              Map<String, ProjectProjectRelationship> deleteProjectRelationshipMap,
                                              Map<String, ProjectProjectRelationship> updateProjectRelationshipMap,
                                              Set<String> projectIds, User user) {
        if (projectIds.isEmpty()) return;

        StringBuilder candidate = new StringBuilder();
        try {
            ProjectService.Iface client = new ThriftClients().makeProjectClient();

            Map<String, ProjectProjectRelationship> changeMap= new HashMap<>();

            for (String projectId : projectIds) {
                ProjectRelationship updateProjectRelationship = updateProjectRelationshipMap.get(projectId).getProjectRelationship();
                ProjectRelationship oldProjectRelationship = oldProjectRelationshipMap.get(projectId).getProjectRelationship();

                if (!updateProjectRelationship.equals(oldProjectRelationship)) {
                    changeMap.put(projectId, oldProjectRelationshipMap.get(projectId));
                }
            }
            //! This code doubling is done to reduce the database queries. I.e. one big query instead of multiple small ones
            for (ProjectLink projectLink : client.getLinkedProjects(changeMap, user)) {
                ProjectRelationship updateProjectRelationship = updateProjectRelationshipMap.get(projectLink.getId()).getProjectRelationship();
                ProjectRelationship deleteProjectRelationship = deleteProjectRelationshipMap.get(projectLink.getId()).getProjectRelationship();
                ProjectRelationship oldProjectRelationship = oldProjectRelationshipMap.get(projectLink.getId()).getProjectRelationship();
                boolean updateEnableSvm = updateProjectRelationshipMap.get(projectLink.getId()).isEnableSvm();
                boolean deleteEnableSvm = deleteProjectRelationshipMap.get(projectLink.getId()).isEnableSvm();
                boolean oldEnableSvm = oldProjectRelationshipMap.get(projectLink.getId()).isEnableSvm();

                candidate.append(String.format("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>",
                        projectLink.getName(),
                        oldProjectRelationship,
                        deleteProjectRelationship,
                        updateProjectRelationship,
                        oldEnableSvm,
                        deleteEnableSvm,
                        updateEnableSvm));
            }

        } catch (TException ignored) {
        }

        String tableContent = candidate.toString();
        HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
        ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());

        if (!tableContent.isEmpty()) {
            display.append(String.format("<table class=\"%s\" id=\"%sUpdated\" >", tableClasses, idPrefix));
            display.append("<thead><tr><th colspan=\"7\">"+LanguageUtil.get(resourceBundle,"updated.project.links")+"</th></tr>" +
                    "<tr><th>"+LanguageUtil.get(resourceBundle,"project.name")+"</th>" +
                    "<th>"+LanguageUtil.get(resourceBundle,"current.project.relationship")+"</th>" +
                    "<th>"+LanguageUtil.get(resourceBundle,"deleted.project.relationship")+"</th>" +
                    "<th>"+LanguageUtil.get(resourceBundle,"suggested.project.relationship")+"</th>" +
                    "<th>"+LanguageUtil.get(resourceBundle,"current.linked.project.enable.svm")+"</th>" +
                    "<th>"+LanguageUtil.get(resourceBundle,"deleted.linked.project.enable.svm")+"</th>" +
                    "<th>"+LanguageUtil.get(resourceBundle,"suggested.linked.project.enable.svm")+"</th></tr>" +
                    "</thead><tbody>");
            display.append(tableContent);
            display.append("</tbody></table>");
        }
    }

    private void renderReleaseIdToUsage(StringBuilder display, User user) {

       if (ensureSomethingTodoAndNoNullReleaseIdUsage()) {
           if (actual.getReleaseIdToUsage() == null) {
               actual.setReleaseIdToUsage(new HashMap<>());
           }
           Set<String> changedReleaseIds = Sets.intersection(
                   additions.getReleaseIdToUsage().keySet(),
                   deletions.getReleaseIdToUsage().keySet());
           changedReleaseIds = Sets.intersection(
                   changedReleaseIds,
                   actual.getReleaseIdToUsage().keySet());//remove projects already deleted in database
           Set<String> removedReleaseIds = Sets.difference(
                   deletions.getReleaseIdToUsage().keySet(),
                   changedReleaseIds);
           removedReleaseIds = Sets.intersection(
                   removedReleaseIds,
                   actual.getReleaseIdToUsage().keySet());
           Set<String> addedReleaseIds = Sets.difference(
                   additions.getReleaseIdToUsage().keySet(),
                   changedReleaseIds);

           LinkedReleaseRenderer renderer = new LinkedReleaseRenderer(display, tableClasses, idPrefix, user);
           HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
           ResourceBundle resourceBundle = ResourceBundleUtil.getBundle("content.Language", request.getLocale(), getClass());
           renderer.renderReleaseLinkList(display, deletions.getReleaseIdToUsage(), removedReleaseIds, LanguageUtil.get(resourceBundle,"removed.release.links"), request);
           renderer.renderReleaseLinkList(display, additions.getReleaseIdToUsage(), addedReleaseIds, LanguageUtil.get(resourceBundle,"added.release.links"), request);
           renderer.renderReleaseLinkListCompare(display,
                   actual.getReleaseIdToUsage(),
                   deletions.getReleaseIdToUsage(),
                   additions.getReleaseIdToUsage(), changedReleaseIds, request);
        }
    }

    private boolean ensureSomethingTodoAndNoNullReleaseIdUsage() {
        if (!deletions.isSetReleaseIdToUsage() && !additions.isSetReleaseIdToUsage()) {
            return false;
        }
        if(!deletions.isSetReleaseIdToUsage()){
            deletions.setReleaseIdToUsage(new HashMap<>());
        }
        if(!additions.isSetReleaseIdToUsage()){
            additions.setReleaseIdToUsage(new HashMap<>());
        }
        return true;
    }

    private Project prepareLicenseInfoHeaderTextInProject(Project project) {
        Project modifiedProject = project.deepCopy();

        String defaultTextAsHtmlForDisplay = "<span title=\"" + defaultLicenseInfoHeaderText + "\">" + PortalConstants.DEFAULT_LICENSE_INFO_HEADER_TEXT_FOR_DISPALY + "</span>";

        if(!modifiedProject.isSetLicenseInfoHeaderText()) {
            // if the project contains the default license info header text, we wrap it into an html span-element such that the default text is given as a hover text.
            // this is only done for displaying it in a three-way merge in a moderation request.
            modifiedProject.setLicenseInfoHeaderText(defaultTextAsHtmlForDisplay);
        } else {
            // for a custom text escape html properly
            modifiedProject.setLicenseInfoHeaderText(StringEscapeUtils.escapeHtml(modifiedProject.getLicenseInfoHeaderText()).replace("\n", "<br>") );
        }

        return modifiedProject;
    }

    private Project prepareObligationsTextInProject(Project project) {
        Project modifiedProject = project.deepCopy();

        String defaultTextAsHtmlForDisplay = "<span title=\"" + defaultObligationsText + "\">" + PortalConstants.DEFAULT_OBLIGATIONS_TEXT_FOR_DISPALY + "</span>";

        if(!modifiedProject.isSetObligationsText()) {
            // if the project contains the default clearing summary text, we wrap it into an html span-element such that the default text is given as a hover text.
            // this is only done for displaying it in a three-way merge in a moderation request.
            modifiedProject.setObligationsText(defaultTextAsHtmlForDisplay);
        } else {
            // for a custom text escape html properly
            modifiedProject.setObligationsText(StringEscapeUtils.escapeHtml(modifiedProject.getObligationsText()).replace("\n", "<br>") );
        }

        return modifiedProject;
    }
}
