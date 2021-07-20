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
public class SW360VendorAdvisory {
    private String id;
    private String vendor;
    private String name;
    private String url;

    public String getId() {
        return id;
    }

    public SW360VendorAdvisory setId(String id) {
        this.id = id;
        return this;
    }

    public String getVendor() {
        return vendor;
    }

    public SW360VendorAdvisory setVendor(String vendor) {
        this.vendor = vendor;
        return this;
    }

    public String getName() {
        return name;
    }

    public SW360VendorAdvisory setName(String name) {
        this.name = name;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public SW360VendorAdvisory setUrl(String url) {
        this.url = url;
        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, vendor, name, url);
    }

    @Override
    public boolean equals(Object obj) {
        SW360VendorAdvisory sw360VendorAdvisory = null;
        if (this == obj)
            return true;
        if ((obj instanceof SW360VendorAdvisory) || super.equals(obj)) {
            sw360VendorAdvisory = (SW360VendorAdvisory) obj;
        } else {
            return false;
        }

        return Objects.equals(vendor, sw360VendorAdvisory.getVendor())
                && Objects.equals(name, sw360VendorAdvisory.getName())
                && Objects.equals(id, sw360VendorAdvisory.getId())
                && Objects.equals(url, sw360VendorAdvisory.getUrl());
    }
}
