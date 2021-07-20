/*
 * Copyright Siemens AG, 2018.
 * Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.rest.resourceserver.core.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.eclipse.sw360.datahandler.thrift.projects.ProjectProjectRelationship;
import org.eclipse.sw360.rest.resourceserver.project.ProjectController;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;

@Component
public class JsonProjectRelationSerializer extends JsonSerializer<Map<String, ProjectProjectRelationship>> {

    @Override
    public void serialize(Map<String, ProjectProjectRelationship> projectRelationMap, JsonGenerator gen, SerializerProvider provider)
            throws IOException {

        List<Map<String, String>> linkedProjects = new ArrayList<>();
        for (Map.Entry<String, ProjectProjectRelationship> projectRelation : projectRelationMap.entrySet()) {
            String projectLink = linkTo(ProjectController.class).slash("api" +
                    ProjectController.PROJECTS_URL + "/" + projectRelation.getKey()).withSelfRel().getHref();

            Map<String, String> linkedProject = new HashMap<>();
            linkedProject.put("relation", projectRelation.getValue().getProjectRelationship().name());
            linkedProject.put("enableSvm", String.valueOf(projectRelation.getValue().isEnableSvm()));
            linkedProject.put("project", projectLink);
            linkedProjects.add(linkedProject);

        }
        gen.writeObject(linkedProjects);
    }
}
