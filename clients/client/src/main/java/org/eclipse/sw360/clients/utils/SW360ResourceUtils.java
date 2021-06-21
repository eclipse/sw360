/*
 * Copyright (c) Bosch.IO GmbH 2020.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.clients.utils;

import org.eclipse.sw360.clients.rest.resource.Embedded;
import org.eclipse.sw360.clients.rest.resource.SW360HalResource;
import org.eclipse.sw360.clients.rest.resource.components.SW360ComponentList;
import org.eclipse.sw360.clients.rest.resource.components.SW360ComponentListEmbedded;
import org.eclipse.sw360.clients.rest.resource.components.SW360SparseComponent;
import org.eclipse.sw360.clients.rest.resource.licenses.SW360LicenseList;
import org.eclipse.sw360.clients.rest.resource.licenses.SW360LicenseListEmbedded;
import org.eclipse.sw360.clients.rest.resource.licenses.SW360SparseLicense;
import org.eclipse.sw360.clients.rest.resource.projects.SW360Project;
import org.eclipse.sw360.clients.rest.resource.projects.SW360ProjectList;
import org.eclipse.sw360.clients.rest.resource.projects.SW360ProjectListEmbedded;
import org.eclipse.sw360.clients.rest.resource.releases.SW360ReleaseList;
import org.eclipse.sw360.clients.rest.resource.releases.SW360ReleaseListEmbedded;
import org.eclipse.sw360.clients.rest.resource.releases.SW360SparseRelease;
import org.eclipse.sw360.clients.rest.resource.vulnerabilities.SW360Vulnerability;
import org.eclipse.sw360.clients.rest.resource.vulnerabilities.SW360VulnerabilityList;
import org.eclipse.sw360.clients.rest.resource.vulnerabilities.SW360VulnerabilityListEmbedded;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * <p>
 * A class providing functionality to extract data of specific entities from
 * resource objects that have been retrieved from the server.
 * </p>
 * <p>
 * The SW360 resource model uses special list classes to deal with embedded
 * elements in resources in a type-safe way. With the functions offered by this
 * class, these classes can be converted to plain Java lists.
 * </p>
 */
public class SW360ResourceUtils {
    /**
     * Private constructor to prevent instantiation.
     */
    private SW360ResourceUtils() {
    }

    /**
     * An extractor function to obtain embedded license data from a server
     * response.
     *
     * @param response the de-serialized server response
     * @return a list with the licenses contained in the response
     */
    public static List<SW360SparseLicense> getSw360SparseLicenses(SW360LicenseList response) {
        return extractEmbeddedList(response, SW360ResourceUtils::extractSparseLicenseList);
    }

    /**
     * An extractor function to obtain embedded project data from a server
     * response.
     *
     * @param response the de-serialized server response
     * @return a list with the projects contained in the response
     */
    public static List<SW360Project> getSw360Projects(SW360ProjectList response) {
        return extractEmbeddedList(response, SW360ProjectListEmbedded::getProjects);
    }

    /**
     * An extractor function to obtain embedded vulnerability data from a server
     * response.
     *
     * @param response the de-serialized server response
     * @return a list with the vulnerability contained in the response
     */
    public static List<SW360Vulnerability> getSw360Vulnerabilities(SW360VulnerabilityList response) {
        return extractEmbeddedList(response, SW360VulnerabilityListEmbedded::getVulnerabilities);
    }

    /**
     * An extractor function to obtain embedded release data from a server
     * response.
     *
     * @param response the de-serialized server response
     * @return a list with the sparse releases contained in the response
     */
    public static List<SW360SparseRelease> getSw360SparseReleases(SW360ReleaseList response) {
        return extractEmbeddedList(response, SW360ReleaseListEmbedded::getReleases);
    }

    /**
     * An extractor function to obtain embedded component data from a server
     * response.
     *
     * @param response the de-serialized server response
     * @return a list with the sparse components contained in the response
     */
    public static List<SW360SparseComponent> getSw360SparseComponents(SW360ComponentList response) {
        return extractEmbeddedList(response, SW360ComponentListEmbedded::getComponents);
    }

    /**
     * Helper function to extract a list of sparse licenses from an
     * {@code SW360LicenseListEmbedded} object. The model object only provides
     * a set of licenses; therefore, the data has to be copied into a list.
     *
     * @param embeddedLicenses the {@code SW360LicenseListEmbedded}
     * @return a list with the licenses contained
     */
    private static List<SW360SparseLicense> extractSparseLicenseList(SW360LicenseListEmbedded embeddedLicenses) {
        return Optional.ofNullable(embeddedLicenses.getLicenses())
                .map(ArrayList::new)
                .orElse(null);
    }

    /**
     * Obtains the embedded data from the given resource and transforms it to
     * a list of objects of a specific result type. Many requests can yield
     * results containing embedded elements of a specific type. As embedded
     * elements may not be present in a response received from the server, the
     * access to them is not null-safe. This function implements a generic
     * means to obtain a list of embedded elements from a query result handling
     * potential null references. For this purpose, an extractor function has
     * to be provided that fetches the elements of interest from the model
     * class. If data is not present (i.e. a null reference is encountered), an
     * empty list is returned as fallback.
     *
     * @param resource  the resource obtained from the result of a request
     * @param extractor the function to extract the result elements
     * @param <E>       the type of the embedded data
     * @param <T>       the concrete resource class
     * @param <U>       the type of the result elements
     * @return a list with the result elements
     */
    private static <E extends Embedded, T extends SW360HalResource<?, E>, U>
    List<U> extractEmbeddedList(T resource, Function<E, List<U>> extractor) {
        return Optional.ofNullable(resource.getEmbedded())
                .flatMap(embedded -> Optional.ofNullable(extractor.apply(embedded))
                        .map(ArrayList::new))
                .orElseGet(ArrayList::new);
    }
}
