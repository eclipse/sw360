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
public class SW360VerificationStateInfo {
    private String checkedOn;
    private String checkedBy;
    private String comment;
    private SW360VerificationState verificationState;

    public String getCheckedOn() {
        return checkedOn;
    }

    public SW360VerificationStateInfo setCheckedOn(String checkedOn) {
        this.checkedOn = checkedOn;
        return this;
    }

    public String getCheckedBy() {
        return checkedBy;
    }

    public SW360VerificationStateInfo setCheckedBy(String checkedBy) {
        this.checkedBy = checkedBy;
        return this;
    }

    public String getComment() {
        return comment;
    }

    public SW360VerificationStateInfo setComment(String comment) {
        this.comment = comment;
        return this;
    }

    public SW360VerificationState getVerificationState() {
        return verificationState;
    }

    public SW360VerificationStateInfo setVerificationState(SW360VerificationState verificationState) {
        this.verificationState = verificationState;
        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), checkedOn, checkedBy, comment, verificationState);
    }

    @Override
    public boolean equals(Object obj) {
        SW360VerificationStateInfo sw360VerificationStateInfo = null;

        if (this == obj)
            return true;
        if ((obj instanceof SW360VerificationStateInfo) || super.equals(obj)) {
            sw360VerificationStateInfo = (SW360VerificationStateInfo) obj;
        } else {
            return false;
        }

        return Objects.equals(checkedOn, sw360VerificationStateInfo.getCheckedOn())
                && Objects.equals(checkedBy, sw360VerificationStateInfo.getCheckedBy())
                && Objects.equals(comment, sw360VerificationStateInfo.getComment())
                && Objects.equals(verificationState, sw360VerificationStateInfo.getVerificationState());
    }
}
