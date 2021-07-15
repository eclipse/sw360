#!/usr/bin/env bash
# Copyright (c) Bosch Software Innovations GmbH 2019.
# Part of the SW360 Portal Project.
#
# SPDX-License-Identifier: EPL-2.0
#
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License v2.0
# which is available at http://www.eclipse.org/legal/epl-2.0/

set -e

NAME=couchdb-for-sw360-testing

if [[ ! "$(docker ps -q -f name=$NAME)" ]]; then
    if netstat -tln | grep -q -e ':5984.*LISTEN' 2>/dev/null; then
        echo "port is already bocked, probably by another sw360 deployment"
        exit 0
    fi

    echo "Test container is not running, starting it ..."
    docker run \
	   -e COUCHDB_USER=admin \
	   -e COUCHDB_PASSWORD=password \
           --rm \
           -p 5984:5984 \
           -d \
           --name "$NAME" \
           couchdb:3.1
    echo "Test container is started and listening on 5984."
else
    echo "Test container is running, shutting it down ..."
    docker stop "$NAME"
    echo "Test container is stopped."
fi

