/*
 * Copyright (c) Bosch Software Innovations GmbH 2017-2018.
 * Copyright (c) Bosch.IO GmbH 2020.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.clients.rest.resource;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LinkObjects {
    private Self self;

    public Self getSelf() {
        return this.self;
    }

    public LinkObjects setSelf(Self self) {
        this.self = self;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkObjects that = (LinkObjects) o;
        return that.canEqual(this) && Objects.equals(self, that.self);
    }

    @Override
    public int hashCode() {
        return Objects.hash(self);
    }

    /**
     * Checks whether an equals comparison to the given object is possible.
     * This is needed to support sub classes with additional state while
     * keeping the contract of equals(). Refer to
     * <a href="https://www.artima.com/lejava/articles/equality.html">this
     * article</a> for further details.
     *
     * @param o the object to compare to
     * @return a flag whether this object can be equal to this
     */
    public boolean canEqual(Object o) {
        return o instanceof LinkObjects;
    }
}
