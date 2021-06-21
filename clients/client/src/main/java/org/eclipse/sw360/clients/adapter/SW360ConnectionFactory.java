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

import org.eclipse.sw360.clients.auth.AccessTokenProvider;
import org.eclipse.sw360.clients.auth.SW360AuthenticationClient;
import org.eclipse.sw360.clients.config.SW360ClientConfig;
import org.eclipse.sw360.clients.rest.SW360ComponentClient;
import org.eclipse.sw360.clients.rest.SW360LicenseClient;
import org.eclipse.sw360.clients.rest.SW360ProjectClient;
import org.eclipse.sw360.clients.rest.SW360ReleaseClient;
import org.eclipse.sw360.clients.rest.SW360VulnerabilityClient;

/**
 * <p>
 * A factory class for creating a new {@link SW360Connection}.
 * </p>
 * <p>
 * This class is the central entry point into the SW360 client library. It can
 * create an {@link SW360Connection} object from an {@link SW360ClientConfig}
 * instance. From this connection instance the adapter objects can be obtained
 * that allow the actual interaction with an SW360 server.
 * </p>
 */
public class SW360ConnectionFactory {
    /**
     * Creates a new {@code SW360Connection} instance based on the passed in
     * configuration object. The configuration defines the SW360 server to be
     * accessed, together with some central helper objects that are used for
     * these interactions.
     *
     * @param config the configuration of this SW360 client
     * @return a new {@code SW360Connection} object
     */
    public SW360Connection newConnection(SW360ClientConfig config) {
        SW360AuthenticationClient authClient = new SW360AuthenticationClient(config);
        AccessTokenProvider tokenProvider = new AccessTokenProvider(authClient);

        SW360ComponentClient componentClient = new SW360ComponentClient(config, tokenProvider);
        SW360ComponentClientAdapterAsync componentAdapterAsync =
                new SW360ComponentClientAdapterAsyncImpl(componentClient);
        SW360ComponentClientAdapter componentAdapterSync =
                SyncClientAdapterHandler.newHandler(SW360ComponentClientAdapter.class,
                        SW360ComponentClientAdapterAsync.class, componentAdapterAsync);

        SW360ReleaseClient releaseClient = new SW360ReleaseClient(config, tokenProvider);
        SW360ReleaseClientAdapterAsyncImpl releaseAdapterAsync =
                new SW360ReleaseClientAdapterAsyncImpl(releaseClient, componentAdapterAsync);
        SW360ReleaseClientAdapter releaseAdapterSync =
                SyncClientAdapterHandler.newHandler(SW360ReleaseClientAdapter.class,
                        SW360ReleaseClientAdapterAsync.class, releaseAdapterAsync);

        SW360LicenseClient licenseClient = new SW360LicenseClient(config, tokenProvider);
        SW360LicenseClientAdapterAsync licenseAdapterAsync = new SW360LicenseClientAdapterAsyncImpl(licenseClient);
        SW360LicenseClientAdapter licenseAdapterSync =
                SyncClientAdapterHandler.newHandler(SW360LicenseClientAdapter.class,
                        SW360LicenseClientAdapterAsync.class, licenseAdapterAsync);

        SW360ProjectClient projectClient = new SW360ProjectClient(config, tokenProvider);
        SW360ProjectClientAdapterAsync projectAdapterAsync = new SW360ProjectClientAdapterAsyncImpl(projectClient);
        SW360ProjectClientAdapter projectAdapterSync =
                SyncClientAdapterHandler.newHandler(SW360ProjectClientAdapter.class,
                        SW360ProjectClientAdapterAsync.class, projectAdapterAsync);

        SW360VulnerabilityClient vulnerabilityClient = new SW360VulnerabilityClient(config, tokenProvider);
        SW360VulnerabilityClientAdapterAsync vulnerabilityAdapterAsync = new SW360VulnerabilityClientAdapterAsyncImpl(vulnerabilityClient);
        SW360VulnerabilityClientAdapter vulnerabilityAdapterSync =
                SyncClientAdapterHandler.newHandler(SW360VulnerabilityClientAdapter.class,
                        SW360VulnerabilityClientAdapterAsync.class, vulnerabilityAdapterAsync);

        return new SW360Connection() {
            @Override
            public SW360ComponentClientAdapter getComponentAdapter() {
                return componentAdapterSync;
            }

            @Override
            public SW360ComponentClientAdapterAsync getComponentAdapterAsync() {
                return componentAdapterAsync;
            }

            @Override
            public SW360ReleaseClientAdapter getReleaseAdapter() {
                return releaseAdapterSync;
            }

            @Override
            public SW360ReleaseClientAdapterAsync getReleaseAdapterAsync() {
                return releaseAdapterAsync;
            }

            @Override
            public SW360LicenseClientAdapter getLicenseAdapter() {
                return licenseAdapterSync;
            }

            @Override
            public SW360LicenseClientAdapterAsync getLicenseAdapterAsync() {
                return licenseAdapterAsync;
            }

            @Override
            public SW360ProjectClientAdapter getProjectAdapter() {
                return projectAdapterSync;
            }

            @Override
            public SW360ProjectClientAdapterAsync getProjectAdapterAsync() {
                return projectAdapterAsync;
            }

            @Override
            public SW360VulnerabilityClientAdapterAsync getVulnerabilityAdapterAsync() {
                return vulnerabilityAdapterAsync;
            }
        };
    }
}
