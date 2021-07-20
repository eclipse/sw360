<%--
  ~ Copyright Siemens AG, 2019. Part of the SW360 Portal Project.
  ~
  ~ This program and the accompanying materials are made
  ~ available under the terms of the Eclipse Public License 2.0
  ~ which is available at https://www.eclipse.org/legal/epl-2.0/
  ~
  ~ SPDX-License-Identifier: EPL-2.0
  --%>

<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<%@ include file="/html/init.jsp"%>
<%@ include file="/html/utils/includes/logError.jspf" %>
<%@ include file="/html/utils/includes/requirejs.jspf" %>
<%@ include file="/html/utils/includes/errorKeyToMessage.jspf"%>

<%@page import="org.eclipse.sw360.datahandler.thrift.projects.ClearingRequest"%>
<%@page import="org.eclipse.sw360.datahandler.thrift.ClearingRequestState"%>
<%@page import="org.eclipse.sw360.datahandler.thrift.ClearingRequestPriority"%>
<%@page import="org.eclipse.sw360.portal.common.PortalConstants"%>

<portlet:defineObjects/>
<liferay-theme:defineObjects/>

<jsp:useBean id="clearingRequest" class="org.eclipse.sw360.datahandler.thrift.projects.ClearingRequest" scope="request"/>
<jsp:useBean id="project" class="org.eclipse.sw360.datahandler.thrift.projects.Project" scope="request"/>
<jsp:useBean id="writeAccessUser" type="java.lang.Boolean" scope="request"/>
<jsp:useBean id="isClearingExpert" type="java.lang.Boolean" scope="request"/>
<jsp:useBean id="printDate" class="java.util.Date"/>
<jsp:useBean id="approvedReleaseCount" class="java.lang.Integer" scope="request" />
<jsp:useBean id="PreferredClearingDateLimit" class="java.lang.String" scope="request" />


<core_rt:if test="${not empty clearingRequest.id}">

<core_rt:set var="clearingRequestId"  value="${clearingRequest.id}" />
<core_rt:set var="pageName"  value="<%=request.getParameter("pagename") %>" />
<core_rt:set var="user" value="<%=themeDisplay.getUser()%>"/>
<core_rt:set var="isProjectPresent" value='${not empty clearingRequest.projectId}'/>
<core_rt:set var="isRequestingUser" value='${clearingRequest.requestingUser eq user.emailAddress}'/>
<core_rt:set var="isClearingTeam" value='${clearingRequest.clearingTeam eq user.emailAddress or (isClearingExpert and writeAccessUser)}'/>
<core_rt:set var="isClosedOrRejected" value="${clearingRequest.clearingState eq 'CLOSED' or clearingRequest.clearingState eq 'REJECTED' or empty clearingRequest.projectId}"/>
<core_rt:set var="isEditable" value="${not isClosedOrRejected and pageName eq 'editClearingRequest'}"/>
<core_rt:set var="isEditableForRequestingUser" value="${isRequestingUser and isEditable}"/>
<core_rt:set var="isEditableForClearingTeam" value="${isClearingTeam and isEditable}"/>
<core_rt:set var="isProgressBarVisible" value="${not isClosedOrRejected and clearingRequest.clearingState ne 'NEW' and project.releaseIdToUsageSize gt 0}"/>
<core_rt:set var="isCRreOpened" value="${not empty clearingRequest.reOpenOn and clearingRequest.reOpenOn.size() gt 0}"/>
<core_rt:set var="isReOpenButtonVisible" value="${isProjectPresent and isClosedOrRejected and (isClearingTeam or isRequestingUser)}" />
<core_rt:set var="criticalCount" value="3"/>

<core_rt:if test="${clearingRequest.isSetTimestampOfDecision() and clearingRequest.timestampOfDecision > 0}">
<jsp:useBean id="criticalCrCount" class="java.lang.Integer" scope="request" />
    <core_rt:set var="criticalCount" value="${criticalCrCount}"/>
</core_rt:if>

<portlet:actionURL var="updateClearingRequestURL" name="updateClearingRequest">
    <portlet:param name="<%=PortalConstants.CLEARING_REQUEST_ID%>" value="${clearingRequestId}"/>
    <portlet:param name="<%=PortalConstants.IS_CLEARING_EXPERT%>" value="${isEditableForClearingTeam}"/>
</portlet:actionURL>

<portlet:renderURL var="cancelURL">
    <portlet:param name="<%=PortalConstants.PAGENAME%>" value="<%=PortalConstants.PAGENAME_DETAIL_CLEARING_REQUEST%>" />
    <portlet:param name="<%=PortalConstants.CLEARING_REQUEST_ID%>" value="${clearingRequestId}"/>
</portlet:renderURL>

<portlet:renderURL var="editlURL">
    <portlet:param name="<%=PortalConstants.PAGENAME%>" value="<%=PortalConstants.PAGENAME_EDIT_CLEARING_REQUEST%>" />
    <portlet:param name="<%=PortalConstants.CLEARING_REQUEST_ID%>" value="${clearingRequestId}"/>
</portlet:renderURL>

<portlet:resourceURL var="addCommentUrl">
    <portlet:param name="<%=PortalConstants.ACTION%>" value='<%=PortalConstants.ADD_COMMENT%>'/>
    <portlet:param name="<%=PortalConstants.CLEARING_REQUEST_ID%>" value="${clearingRequestId}"/>
</portlet:resourceURL>

<div class="container" id="clearing-request">
<div class="row portlet-toolbar">
    <div class="col-auto">
        <div class="btn-toolbar" role="toolbar">
            <core_rt:if test="${pageName eq 'detailClearingRequest' and isProjectPresent and not isClosedOrRejected and (isClearingTeam or isRequestingUser)}">
                <div class="btn-group" role="group">
                    <button type="button" class="btn btn-primary" onclick="window.location.href='<%=editlURL%>' + window.location.hash"><liferay-ui:message key="edit.request" /></button>
                </div>
            </core_rt:if>
            <core_rt:if test="${isReOpenButtonVisible}">
                <div class="btn-group" role="group">
                    <button type="button" id="reOpenRequest" class="btn btn-primary"><liferay-ui:message key="reopen.request" /></button>
                </div>
            </core_rt:if>
            <core_rt:if test="${isEditableForClearingTeam or isEditableForRequestingUser}">
	           <div class="btn-group" role="group">
	               <button type="button" id="formSubmit" class="btn btn-primary"><liferay-ui:message key="update.request" /></button>
	           </div>
	           <div class="btn-group" role="group">
                   <button type="button" class="btn btn-light" onclick="window.location.href='<%=cancelURL%>' + window.location.hash"><liferay-ui:message key="cancel" /></button>
               </div>
	        </core_rt:if>
        </div>
    </div>
    <div class="col portlet-title text-truncate" title="${clearingRequestId}">
        ${clearingRequestId}
    </div>
</div>

<div class="row">
    <div class="col">
        <div id="clearing-wizard" class="accordion">
            <div class="card">
                <form id="updateCRForm" name="updateCRForm" action="<%=updateClearingRequestURL%>" class="needs-validation" method="post" novalidate>
                    <div id="clearing-header-heading" class="card-header">
                        <h2 class="mb-0">
                            <button class="btn btn-secondary btn-block" type="button" data-toggle="collapse" data-target="#clearing-header" aria-expanded="true" aria-controls="clearing-header">
                                <svg class="lexicon-icon"><use href="/o/org.eclipse.sw360.liferay-theme/images/clay/icons.svg#info-circle"/></svg>
                                <core_rt:choose>
                                    <core_rt:when test="${isProjectPresent}">
                                        <liferay-ui:message key="clearing.request.information.for.project" />: <sw360:DisplayProjectLink project="${project}"/>
                                    </core_rt:when>
                                    <core_rt:otherwise>
                                        <liferay-ui:message key="clearing.request.information.for.deleted.project" />:
                                    </core_rt:otherwise>
                                </core_rt:choose>
                            </button>
                        </h2>
                    </div>
                    <div id="clearing-header" class="collapse show" aria-labelledby="clearing-header-heading" >
                        <div class="card-body">
                            <div class="row">
                                <div class="col-6">
                                    <table class="table label-value-table mt-2" id="clearingRequestData">
                                        <thead>
                                            <tr>
                                                <th colspan="2"><liferay-ui:message key="clearing.request" /></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <tr>
                                                <td><label class="form-group"><liferay-ui:message key="requesting.user" />:</label></td>
                                                <td><sw360:DisplayUserEmail email="${clearingRequest.requestingUser}" /></td>
                                            </tr>
                                            <tr>
                                                <td><label class="form-group"><liferay-ui:message key="created.on" />:</label></td>
                                                <td>
                                                    <jsp:setProperty name="printDate" property="time" value="${clearingRequest.timestamp}"/>
                                                    <fmt:formatDate value="${printDate}" pattern="yyyy-MM-dd"/>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td><label class="form-group"><liferay-ui:message key="preferred.clearing.date" />:</label></td>
                                                <td><sw360:out value="${clearingRequest.requestedClearingDate}" bare="true"/></td>
                                            </tr>
                                            <tr>
                                                <td><label class="form-group"><liferay-ui:message key="business.area.line" />:</label></td>
                                                <td>
                                                <core_rt:choose>
                                                    <core_rt:when test="${isProjectPresent}">
                                                        <sw360:out value="${project.businessUnit}" bare="true"/>
                                                    </core_rt:when>
                                                    <core_rt:otherwise>
                                                        <sw360:out value="${clearingRequest.projectBU}" bare="true"/>
                                                    </core_rt:otherwise>
                                                </core_rt:choose>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td><label class="form-group"><liferay-ui:message key="requester.comment" />:</label></td>
                                                <td>
                                                    <div class="comment-text"><sw360:out value="${clearingRequest.requestingUserComment}" stripNewlines="false"/></div>
                                                </td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                                <div class="col-6">
                                    <table class="table label-value-table mt-2" id="clearingDecisionData">
                                        <thead>
                                            <tr>
                                                <th colspan="2"><liferay-ui:message key="clearing.decision" /></th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <tr>
                                                <td><label class="form-group"><liferay-ui:message key="request.status" />:</label></td>
                                                <td>
                                                <core_rt:choose>
                                                    <core_rt:when test="${isEditableForClearingTeam}">
                                                        <select class="form-control"
                                                            name="<portlet:namespace/><%=ClearingRequest._Fields.CLEARING_STATE%>">
                                                            <sw360:DisplayEnumOptions type="<%=ClearingRequestState.class%>" selected="${clearingRequest.clearingState}"/>
                                                        </select>
                                                        <small class="form-text">
                                                            <sw360:DisplayEnumInfo type="<%=ClearingRequestState.class%>"/>
                                                            <liferay-ui:message key="learn.more.about.clearing.request.status" />
                                                        </small>
                                                    </core_rt:when>
                                                    <core_rt:otherwise>
                                                        <sw360:DisplayEnum value="${clearingRequest.clearingState}"/>
                                                    </core_rt:otherwise>
                                                </core_rt:choose>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td><label class="form-group"><liferay-ui:message key="priority" />:</label></td>
                                                <td>
                                                <core_rt:choose>
                                                    <core_rt:when test="${isEditableForClearingTeam}">
                                                        <select class="form-control"
                                                            name="<portlet:namespace/><%=ClearingRequest._Fields.PRIORITY%>">
                                                            <sw360:DisplayEnumOptions type="<%=ClearingRequestPriority.class%>" selected="${clearingRequest.priority}"/>
                                                        </select>
                                                        <small class="form-text">
                                                            <sw360:DisplayEnumInfo type="<%=ClearingRequestPriority.class%>"/>
                                                            <liferay-ui:message key="learn.more.about.clearing.request.priority" />
                                                        </small>
                                                    </core_rt:when>
                                                    <core_rt:otherwise>
                                                        <sw360:DisplayEnum value="${clearingRequest.priority}"/>
                                                    </core_rt:otherwise>
                                                </core_rt:choose>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td><label class="form-group <core_rt:if test='${isEditableForClearingTeam or isEditableForRequestingUser}'>mandatory</core_rt:if>"><liferay-ui:message key="clearing.team" />:</label></td>
                                                <td>
                                                <core_rt:choose>
                                                    <core_rt:when test="${isEditableForClearingTeam or isEditableForRequestingUser}">
                                                        <sw360:DisplayUserEdit id="CLEARING_TEAM" email="${clearingRequest.clearingTeam}" description="" multiUsers="false" />
                                                        <div class="invalid-feedback" id="clearingTeamEmailErrorMsg">
                                                             <liferay-ui:message key="email.should.be.in.valid.format" />
                                                        </div>
                                                    </core_rt:when>
                                                    <core_rt:otherwise>
                                                        <sw360:DisplayUserEmail email="${clearingRequest.clearingTeam}" />
                                                    </core_rt:otherwise>
                                                </core_rt:choose>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <label class="form-group"><liferay-ui:message key="agreed.clearing.date" />:</label>
                                                </td>
                                                <td>
                                                <core_rt:choose>
                                                    <core_rt:when test="${isEditableForClearingTeam}">
                                                        <input class="form-control datepicker" id="agreedClearingDate"
                                                            name="<portlet:namespace/><%=ClearingRequest._Fields.AGREED_CLEARING_DATE%>" type="text" pattern="\d{4}-\d{2}-\d{2}"
                                                            value="<sw360:out value="${clearingRequest.agreedClearingDate}"/>" placeholder="<liferay-ui:message key='agreed.clearing.date.yyyy.mm.dd' />" />
                                                        <div class="invalid-feedback">
                                                            <liferay-ui:message key="date.should.be.valid" />!
                                                        </div>
                                                    </core_rt:when>
                                                    <core_rt:otherwise>
                                                        <sw360:out value="${clearingRequest.agreedClearingDate}" bare="true"/>
                                                    </core_rt:otherwise>
                                                </core_rt:choose>
                                                </td>
                                            </tr>
                                            <core_rt:if test="${clearingRequest.isSetTimestampOfDecision() and clearingRequest.timestampOfDecision > 0}">
                                                <tr>
                                                    <td><label class="form-group"><liferay-ui:message key="request.closed.on" />:</label></td>
                                                    <td>
                                                        <jsp:setProperty name="printDate" property="time" value="${clearingRequest.timestampOfDecision}"/>
                                                        <fmt:formatDate value="${printDate}" pattern="yyyy-MM-dd"/>
                                                    </td>
                                                </tr>
                                            </core_rt:if>
                                            <core_rt:if test="${clearingRequest.isSetModifiedOn() and clearingRequest.modifiedOn > 0}">
                                                <tr>
                                                    <td><label class="form-group"><liferay-ui:message key="last.updated.on" />:</label></td>
                                                    <td class="modifiedOn">
                                                        <jsp:setProperty name="printDate" property="time" value="${clearingRequest.modifiedOn}"/>
                                                        <fmt:formatDate value="${printDate}" pattern="yyyy-MM-dd"/>
                                                    </td>
                                                </tr>
                                            </core_rt:if>
                                            <core_rt:if test="${isCRreOpened}">
                                            <core_rt:set var="listSize" value="${clearingRequest.reOpenOn.size()}"/>
                                                <tr>
                                                    <td><label class="form-group"><liferay-ui:message key="reopened.on" />:</label></td>
                                                    <td>
                                                        <jsp:setProperty name="printDate" property="time" value="${clearingRequest.reOpenOn[listSize - 1]}"/>
                                                        <fmt:formatDate value="${printDate}" pattern="yyyy-MM-dd"/> <span title="re-open count"><i>(<sw360:out value="${listSize}" bare="true"/>)</i></span>
                                                    </td>
                                                </tr>
                                            </core_rt:if>
                                            <core_rt:if test="${isProgressBarVisible}">
                                                <tr>
                                                    <td><label class="form-group"> <liferay-ui:message key="clearing.progress" />:</label></td>
                                                    <td>
                                                        <div class="progress h-100 rounded-0" style="font-size: 100%;">
                                                            <div id="crProgress" class="progress-bar progress-bar-striped" role="progressbar" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100" style="width: 0%">
                                                                <span class="text-dark font-weight-bold"></span>
                                                            </div>
                                                        </div>
                                                    </td>
                                                </tr>
                                            </core_rt:if>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </div>
                    </div>
                </form>
            </div>
            <div class="card">
                <div id="clearing-comments-heading" class="card-header">
                    <h2 class="mb-0">
                        <button class="btn btn-secondary btn-block" type="button" data-toggle="collapse" data-target="#clearing-comments" aria-expanded="false" aria-controls="clearing-comments">
                            <svg class="lexicon-icon"><use href="/o/org.eclipse.sw360.liferay-theme/images/clay/icons.svg#comments"/></svg>
                                <liferay-ui:message key="clearing.request.comments" />: <span id="commentCount" class="badge badge-light text-dark"><sw360:out default="(0)" value="(${not empty clearingRequest.comments ? clearingRequest.comments.size() : '0'})"/></span>
                        </button>
                    </h2>
                </div>
                <div id="clearing-comments" class="collapse" aria-labelledby="clearing-comments-heading" >
                    <div class="card-body">
                        <div class="m-auto">
                            <table class="table label-value-table mt-2" id="clearingCommentsTable">
                                <thead>
                                    <tr><th><liferay-ui:message key="comments" /><input id="commentSearch" type="input" placeholder="<liferay-ui:message key="search" />" class="float-right"></th></tr>
                                <thead>
                                <tbody>
                                        <tr>
                                            <td>
                                                <core_rt:if test="${isProjectPresent and (isClearingTeam or isRequestingUser)}">
	                                                <textarea id="clearingRequestComment" placeholder="<liferay-ui:message key='enter.comment' />..." class="h-25 form-control"></textarea>
	                                                <div class="my-2 btn-group" role="group">
	                                                    <button id="addCommentBtn" type="button" class="btn btn-success"><liferay-ui:message key='add.comment' /></button>
	                                                </div>
	                                                <span id="addCommentStatusMessage" class="py-2 my-2 alert alert-danger" style="display: none;"></span>
                                                </core_rt:if>
                                            </td>
                                        </tr>
                                    <core_rt:forEach items="${clearingRequest.comments}" var="comment" varStatus="loop">
                                        <tr>
                                            <td>
                                                <core_rt:set var="by" value="${comment.commentedBy}" />
                                                <core_rt:set var="iconTextArray" value="${fn:split(by, '_|.')}"/>
                                                <core_rt:set var="iconText" value="NA"/>
                                                <core_rt:choose>
                                                    <core_rt:when test="${fn:length(iconTextArray) gt 2}">
                                                        <core_rt:set var="iconText" value="${fn:substring(iconTextArray[0], 0, 1)}${fn:substring(iconTextArray[1], 0, 1)}"/>
                                                    </core_rt:when>
                                                    <core_rt:otherwise>
                                                        <core_rt:set var="iconText" value="${fn:substring(iconTextArray[0], 0, 1)}${fn:substring(iconTextArray[0], 1, 2)}"/>
                                                    </core_rt:otherwise>
                                                </core_rt:choose>
                                                <jsp:setProperty name="printDate" property="time" value="${comment.commentedOn}"/>
                                                <div class="m-auto row">
                                                    <div class="col-0 user-icon user-icon-info text-uppercase"><span>${iconText}</span></div>
                                                    <div class="col-11">
                                                        <div class="comment-text"><core_rt:if test="${comment.autoGenerated}"> <liferay-ui:message key="this.is.auto.generated.comment" /></core_rt:if><sw360:out value="${comment.text}" stripNewlines="false"/></div>
                                                            <footer class="blockquote-footer"><liferay-ui:message key="by" /> <cite><b><sw360:DisplayUserEmail email="${by}"/></b></cite> <liferay-ui:message key="on" /> <cite>
                                                                <b><fmt:formatDate value="${printDate}" pattern="yyyy-MM-dd HH:mm"/></b></cite>
                                                            </footer>
                                                    </div>
                                                </div>
                                            </td>
                                        </tr>
                                        </core_rt:forEach>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
<div class="dialogs auto-dialogs">
<core_rt:if test="${isReOpenButtonVisible}">
    <div id="reOpenClearingRequestDialog" class="modal fade" tabindex="-1" role="dialog">
        <div class="modal-dialog modal-lg modal-dialog-centered modal-info mw-100 w-75" role="document">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title">
                        <clay:icon symbol="question-circle" />
                        <liferay-ui:message key="reopen.clearing.request" />?
                    </h5>
                    <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                        <span aria-hidden="true">&times;</span>
                    </button>
                </div>
                <div class="modal-body">
                    <p><liferay-ui:message key="please.enter.preferred.clearing.date.to.reopen.clearing.request" /></p>
                    <hr/>
                    <form id="reOpenClearingRequestForm" name="reOpenClearingRequestForm" action="<%=updateClearingRequestURL%>" method="post">
                        <div class="form-group">
                            <label for="requestedClearingDate" class="mandatory"><liferay-ui:message key="preferred.clearing.date" />:</label>
                            <input class="datepicker form-control" id="requestedClearingDate" name="<portlet:namespace/><%=ClearingRequest._Fields.REQUESTED_CLEARING_DATE%>" type="text" pattern="\d{4}-\d{2}-\d{2}" placeholder="<liferay-ui:message key='preferred.clearing.date.yyyy.mm.dd' />" required/>
                            <div class="invalid-feedback">
                                <liferay-ui:message key="date.should.be.valid" />!
                            </div>
                        </div>
                        <core_rt:if test="${criticalCount lt 2}">
                        <div class="form-group">
                            <div class="form-check">
                                <input id="priority" type="checkbox" class="form-check-input" name="<portlet:namespace/><%=ClearingRequest._Fields.PRIORITY%>" >
                                <label class="form-check-label" for="priority"><liferay-ui:message key="critical" /></label>
                                <div class="alert alert-info"><liferay-ui:message key="in.case.you.need.the.clearing.at.an.earlier.date.then.the.preferred.date.that.is.available.please.check.the.critical.checkbox.and.reselect.the.preferred.clearing.date"/>.</div>
                            </div>
                        </div>
                       </core_rt:if>
                        <div class="form-group">
                            <label for="clearingRequestComment"><liferay-ui:message key="please.comment.your.request" />:</label>
                            <textarea id="clearingRequestComment" class="form-control" name="<portlet:namespace/><%=ClearingRequest._Fields.REQUESTING_USER_COMMENT%>" rows="4" placeholder="<liferay-ui:message key='comment.your.request' />..."></textarea>
                        </div>
                    </form>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-light" data-dismiss="modal"><liferay-ui:message key="close" /></button>
                    <button type="button" id="createClearingrequestButton" class="btn btn-primary"><liferay-ui:message key="reopen.clearing.request" /></button>
                </div>
            </div>
        </div>
    </div>
</core_rt:if>
</div>
<script>
require(['jquery', 'modules/dialog', 'modules/validation', 'modules/button', 'bridges/jquery-ui' ], function($, dialog, validation, button) {
    validation.enableForm('#updateCRForm');
    let pcdLimit = ${PreferredClearingDateLimit};
    var clearingTeamEmailEditable = $("#CLEARING_TEAM");

    $('#formSubmit').click(
        function() {
            let $form = $("#updateCRForm"),
                $acDate = $("#agreedClearingDate");
            $form.addClass('was-validated');

            if (clearingTeamEmailEditable){
                let emailId = $("#CLEARING_TEAM").val();
                if (validation.isValidEmail(emailId)) {
                    $("#clearingTeamEmailErrorMsg").hide();
                } else {
                    $("#clearingTeamEmailErrorMsg").show();
                    return;
                }
            }
            $emailId.removeClass("is-invalid");
            if ($acDate.val() && !validation.isValidDate($acDate.val())) {
                $acDate.addClass("is-invalid");
                return;                
            }
            $acDate.removeClass("is-invalid");
            $('#updateCRForm').submit();
        }
    );

    $("#commentSearch").on("keyup", function(event) {
        let value = this.value.toLowerCase().trim();
        if (!value) {
            $("#clearingCommentsTable tbody").find('tr').each(function(index) {
                $(this).show();
            });
            return;
        }
        $("#clearingCommentsTable tbody tr").first().hide();
        $("#clearingCommentsTable tbody tr").not(":first").each(function(index) {
            let id = $(this).find("td").first().text().trim().toLowerCase();
            $(this).toggle(id.indexOf(value) !== -1);
        });
    });

    function unEscapeHtmlTextFormatting(input) {
        return input.replace(/&lt;(b|i|u|s|ul|li)\s*&gt;/gi, "<$1>").replace(/&lt;\/(b|i|u|s|ul|li)\s*&gt;/gi, "</$1>")
    }

    $(document).ready(function() {
        if ("${isProgressBarVisible}" === "true") {
        let totalRelease = "${project.releaseIdToUsageSize}",
            approvedReleaseCount = "${approvedReleaseCount}",
            $progressBar = $("#crProgress"),
            $td = $("#crProgress").closest("tr").find("td:eq(1)");

            if(approvedReleaseCount === "0") {
                let progressText = "(0/"+totalRelease+") "+"<liferay-ui:message key="none.of.the.directly.linked.releases.are.cleared" />";
                $progressBar.find('span').text("0%").removeClass('text-dark').addClass('text-danger');
                $progressBar.attr("aria-valuenow", "0").css({"width": "0%", "overflow": "visible"}).removeClass('text-dark').addClass('text-danger');
                $td.attr("title", progressText);
            } else if (approvedReleaseCount === totalRelease) {
                let progressText = "("+totalRelease+"/"+totalRelease+") "+"<liferay-ui:message key="all.of.the.directly.linked.releases.are.cleared" />";
                $progressBar.find('span').text("100%");
                $progressBar.attr("aria-valuenow", "100").css("width", "100%").addClass("closed");
                $td.attr("title", progressText);
            } else {
                let progressPercentage = ((approvedReleaseCount / totalRelease) * 100).toFixed(0),
                    progressText = "("+ approvedReleaseCount +"/"+totalRelease+") "+"<liferay-ui:message key="directly.linked.releases.are.cleared" />";
                $progressBar.find("span").text(progressPercentage + "%");
                $progressBar.attr("aria-valuenow", progressPercentage).css("width", progressPercentage + "%").addClass("progress-bar-animated inProgress");
                $td.attr("title", progressText);
            }
        }

        $('.datepicker').datepicker({
            <core_rt:choose>
                <core_rt:when test="${isReOpenButtonVisible}">
                    minDate: pcdLimit,
                </core_rt:when>
                <core_rt:otherwise>
                    minDate: new Date(),
                </core_rt:otherwise>
            </core_rt:choose>
            changeMonth: true,
            changeYear: true,
            dateFormat: "yy-mm-dd"
        });

        $("#clearingCommentsTable tbody tr").not(":first").each(function(index) {
            let innerHtml = unEscapeHtmlTextFormatting($(this).find('td .comment-text').html());
            $(this).find('.comment-text').html(innerHtml);
        });

        let requestorComment = unEscapeHtmlTextFormatting($("#clearingRequestData tbody tr td .comment-text").html());
        $("#clearingRequestData tbody tr td .comment-text").html(requestorComment);

        let anchor = $("a.breadcrumb-link"),
            href = $(anchor).attr("href"),
            state = "${clearingRequest.clearingState}";
        if (state === "CLOSED" || state === "REJECTED") {
            $(anchor).attr("href", href+"?selectedTab=tab-ClosedCR");
        } else {
            $(anchor).attr("href", href+"?selectedTab=tab-OpenCR");
        }
    });

    /* Add event listener for saving the comment */
    $("#addCommentBtn").on("click", function (event) {

        if (!$.trim($("#clearingRequestComment").val())) {
            displayErrorMessage('Invalid comment!');
            return;
        }

        let comment = $("#clearingRequestComment").val(),
            email = '${user.emailAddress}',
            by = '<sw360:DisplayUserEmail email="${user.emailAddress}"/>',
            on = new Date().toISOString().slice(0,16).replace('T', ' '),
            count = Number($('#commentCount').text().replace(/[()]/g, '')) + 1,
            iconTextArray = email.split(/[_.]/),
            iconText = 'NA',
            $button = $("#addCommentBtn");

            if (iconTextArray.length > 2) {
                iconText = iconTextArray[0].substring(0,1) + iconTextArray[1].substring(0,1);
            } else {
                iconText = iconTextArray[0].substring(0,1) + iconTextArray[0].substring(1,2);
            }
            button.wait($button);
        jQuery.ajax({
            type: 'POST',
            url: '<%=addCommentUrl%>',
            cache: false,
            data: {
                "<portlet:namespace/><%=PortalConstants.CLEARING_REQUEST_COMMENT%>": $("#clearingRequestComment").val()
            },
            success: function (data) {
                if (data.result == 'SUCCESS') {
                    $('#clearingRequestComment').val('');
                    $('#commentCount').text('('+count+')');
                    $('#clearingCommentsTable tbody tr:first')
                        .after('<tr><td>'
                                    +'<div class="m-auto row">'
                                    +'<div class="col-0 user-icon user-icon-info text-uppercase"><span>'+iconText+'</span></div>'
                                    +'<div class="col-11">'
                                    +'<div class="comment-text"></div><footer class="blockquote-footer">'
                                    +' <liferay-ui:message key="by" /> <cite><b>' + by + '</b></cite>'
                                    +' <liferay-ui:message key="on" /> <cite><b>' + on + '</b></cite></footer>'
                                    +'</div></div>'
                                +'</td></tr>');
                    $('#clearingCommentsTable tbody tr:eq(1) .comment-text').prepend(document.createTextNode(comment));
                    $("#clearingDecisionData tbody tr td.modifiedOn").html(on);
                }
                else {
                    displayErrorMessage('<liferay-ui:message key="failed.to.add.comment" />');
                }
                button.finish($button);
            },
            error: function () {
                displayErrorMessage('<liferay-ui:message key="error.adding.comment.to.clearing.request" />!');
                button.finish($button);
            }
        });
    });

    $("#priority").change(function() {
        if (this.checked) {
        	validation.changeDisabledDate(0);
        } else {
        	validation.changeDisabledDate(pcdLimit);
        }
    });

    /* Add event listener for re opening the CR */
    $("#reOpenRequest").on("click", function (event) {
    $dialog = dialog.open('#reOpenClearingRequestDialog', {
    }, function(submit, callback) {
        let selectedDate = $("#requestedClearingDate").val(),
            isCritical = $("#priority:checked").val(),
            $form = $('#reOpenClearingRequestForm');
        $form.removeClass('was-validated');
        if ((isCritical && !validation.isValidDate(selectedDate, 0)) || (!isCritical && !validation.isValidDate(selectedDate, pcdLimit))) {
            $("#requestedClearingDate").addClass("is-invalid");
            callback();
        } else {
            $("#requestedClearingDate").removeClass("is-invalid");
            $form.addClass('was-validated');
            $form.append('<input type="hidden" value="true" name="<portlet:namespace/><%=PortalConstants.RE_OPEN_REQUEST%>"/>');
            $form.submit();
        }
    }, function() {
        $('#reOpenClearingRequestDialog #reOpenClearingRequestForm').removeClass('was-validated');
    });
    });

    function displayErrorMessage(message) {
        $("#addCommentStatusMessage").html(message).show().delay(5000).fadeOut();
    }

    if (clearingTeamEmailEditable) {
        clearingTeamEmailEditable.parents("div:eq(0)").find("label:eq(0)").remove();
        $("#CLEARING_TEAMDisplay").click(function(){
            $("#clearingTeamEmailErrorMsg").hide();
        });
    }
});
</script>
</core_rt:if>

<jsp:include page="/html/utils/includes/searchAndSelectUsers.jsp" />
<jsp:include page="/html/utils/includes/searchUsers.jsp" />
