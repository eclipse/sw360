<%--
  ~ Copyright Siemens AG, 2013-2017, 2019. Part of the SW360 Portal Project.
  ~
  ~ This program and the accompanying materials are made
  ~ available under the terms of the Eclipse Public License 2.0
  ~ which is available at https://www.eclipse.org/legal/epl-2.0/
  ~
  ~ SPDX-License-Identifier: EPL-2.0
  --%>

<%@ page import="com.liferay.portal.kernel.portlet.PortletURLFactoryUtil" %>
<%@include file="/html/init.jsp" %>

<portlet:defineObjects/>
<liferay-theme:defineObjects/>

<%@ page import="org.eclipse.sw360.datahandler.thrift.projects.ProjectLink" %>
<%@ page import="org.eclipse.sw360.datahandler.thrift.projects.Project" %>
<%@ page import="org.eclipse.sw360.datahandler.thrift.projects.ProjectRelationship" %>
<jsp:useBean id="projectList" type="java.util.List<org.eclipse.sw360.datahandler.thrift.projects.ProjectLink>"  scope="request"/>

<core_rt:forEach items="${projectList}" var="projectLink" varStatus="loop">
    <core_rt:set var="uuid" value="${projectLink.id}"/>
    <tr id="projectLinkRow${uuid}" >
        <td>
            <div class="form-group">
                <input type="hidden" value="${projectLink.id}" name="<portlet:namespace/><%=Project._Fields.LINKED_PROJECTS%><%=ProjectLink._Fields.ID%>">
                <input type="text" placeholder="<liferay-ui:message key="enter.project" />" class="form-control" data-content="projectName"
                    value="<sw360:out value="${projectLink.name}"/>" readonly onclick="window.location='<sw360:DisplayProjectLink projectId="${projectLink.id}" bare="true"/>'"/>
            </div>
        </td>
        <td>
            <div class="form-group">
                <input type="hidden" value="${projectLink.version}" name="<portlet:namespace/><%=ProjectLink._Fields.VERSION%>">
                <input type="text" placeholder="<liferay-ui:message key="no.project.version" />"  class="form-control"
                    value="<sw360:out value="${projectLink.version}"/>" readonly/>
            </div>
        </td>
        <td>
            <div class="form-group">
                <select name="<portlet:namespace/><%=Project._Fields.LINKED_PROJECTS%><%=ProjectLink._Fields.RELATION%>" class="form-control">
                    <sw360:DisplayEnumOptions type="<%=ProjectRelationship.class%>" selected="${projectLink.relation}"/>
                </select>
            </div>
        </td>
        <td>
            <div class="form-group mt-2 pt-1 mx-5">
                <input type="checkbox" name="<portlet:namespace/><%=Project._Fields.LINKED_PROJECTS%><%=ProjectLink._Fields.ENABLE_SVM%>${projectLink.id}" <core_rt:if test="${projectLink.enableSvm}"> checked="checked" </core_rt:if> class="form-check-input" />
            </div>
        </td>
        <td class="content-middle">
            <svg class="action lexicon-icon" data-row-id="projectLinkRow${uuid}">
                <title><liferay-ui:message key="delete" /></title>
                <use href="/o/org.eclipse.sw360.liferay-theme/images/clay/icons.svg#trash"/>
            </svg>
        </td>
    </tr>
</core_rt:forEach>
