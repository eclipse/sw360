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
package org.eclipse.sw360.clients.adapter;

/**
 * <p>
 * The central interface for obtaining client objects to interact with an
 * SW360 server.
 * </p>
 * <p>
 * This interface provides access to a number of adapter objects that can
 * deal with specific SW360 endpoints. Via these adapter objects information
 * can be queried from and updated in SW360.
 * </p>
 * <p>
 * Adapters are available for both synchronous and asynchronous interactions.
 * Thus a client can choose the programming model that fits best to its use
 * cases.
 * </p>
 */
public interface SW360Connection {
    /**
     * Returns an adapter object for the synchronous interaction with the
     * <em>components</em> endpoint of SW360. Using this adapter, information
     * about all known software components can be queried.
     *
     * @return the synchronous adapter for components
     */
    SW360ComponentClientAdapter getComponentAdapter();

    /**
     * Returns an adapter object for the asynchronous interaction with the
     * <em>components</em> endpoint of SW360. Using this adapter, information
     * about all known software components can be queried.
     *
     * @return the asynchronous adapter for components
     */
    SW360ComponentClientAdapterAsync getComponentAdapterAsync();

    /**
     * Returns an adapter object for the synchronous interaction with the
     * <em>releases</em> endpoint of SW360. Each software component can have
     * multiple releases. Using this adapter, the releases for the known
     * components can be queried and managed.
     *
     * @return the synchronous adapter for releases
     */
    SW360ReleaseClientAdapter getReleaseAdapter();

    /**
     * Returns an adapter object for the asynchronous interaction with the
     * <em>releases</em> endpoint of SW360. Each software component can have
     * multiple releases. Using this adapter, the releases for the known
     * components can be queried and managed.
     *
     * @return the asynchronous adapter for releases
     */
    SW360ReleaseClientAdapterAsync getReleaseAdapterAsync();

    /**
     * Returns an adapter object for the synchronous interaction with the
     * <em>licenses</em> endpoint of SW360. This adapter allows querying the
     * licenses known to SW360.
     *
     * @return the synchronous adapter for licenses
     */
    SW360LicenseClientAdapter getLicenseAdapter();

    /**
     * Returns an adapter object for the asynchronous interaction with the
     * <em>licenses</em> endpoint of SW360. This adapter allows querying the
     * licenses known to SW360.
     *
     * @return the asynchronous adapter for licenses
     */
    SW360LicenseClientAdapterAsync getLicenseAdapterAsync();

    /**
     * Returns an adapter object for the synchronous interaction with the
     * <em>projects</em> endpoint of SW360. With this adapter projects can be
     * created and assigned to the software components they are using.
     *
     * @return the synchronous adapter for projects
     */
    SW360ProjectClientAdapter getProjectAdapter();

    /**
     * Returns an adapter object for the asynchronous interaction with the
     * <em>projects</em> endpoint of SW360. With this adapter projects can be
     * created and assigned to the software components they are using.
     *
     * @return the asynchronous adapter for projects
     */
    SW360ProjectClientAdapterAsync getProjectAdapterAsync();

    /**
     * Returns an adapter object for the asynchronous interaction with the
     * <em>vulnerabilities</em> endpoint of SW360. With this adapter vulnerabilities 
     * and releaseVulnerabilityRelation can be created, updated, deleted.
     *
     * @return the asynchronous adapter for vulnerabilities
     */
    SW360VulnerabilityClientAdapterAsync getVulnerabilityAdapterAsync();
}
