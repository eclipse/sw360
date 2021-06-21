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

public enum SW360VerificationState {
    NOT_CHECKED(0),
    CHECKED(1),
    INCORRECT(2);

    private final int value;

    private SW360VerificationState(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
}
