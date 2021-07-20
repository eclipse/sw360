/*
 * Copyright Siemens AG, 2021. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.clients.rest.resource.vulnerabilities;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SW360CVEReference {
    private String id;
    private String year;
    private String number;

    public String getId() {
        return id;
    }

    public SW360CVEReference setId(String id) {
        this.id = id;
        return this;
    }

    public String getYear() {
        return year;
    }

    public SW360CVEReference setYear(String year) {
        this.year = year;
        return this;
    }

    public String getNumber() {
        return number;
    }

    public SW360CVEReference setNumber(String number) {
        this.number = number;
        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, year, number);
    }

    @Override
    public boolean equals(Object obj) {
        SW360CVEReference sw360CVEReference = null;
        if (this == obj)
            return true;
        if ((obj instanceof SW360CVEReference) || super.equals(obj)) {
            sw360CVEReference = (SW360CVEReference) obj;
        } else {
            return false;
        }

        return Objects.equals(year, sw360CVEReference.getYear()) 
                && Objects.equals(id, sw360CVEReference.getId())
                && Objects.equals(number, sw360CVEReference.getNumber());
    }
}
