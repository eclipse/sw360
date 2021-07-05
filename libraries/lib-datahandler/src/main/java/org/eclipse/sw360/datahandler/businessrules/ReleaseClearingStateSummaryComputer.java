/*
 * Copyright Siemens AG, 2013-2015, 2019. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.datahandler.businessrules;

import org.eclipse.sw360.datahandler.thrift.components.ClearingState;
import org.eclipse.sw360.datahandler.thrift.components.ComponentService;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.components.ReleaseClearingStateSummary;
import org.eclipse.sw360.datahandler.thrift.fossology.FossologyService;

import java.util.List;

/**
 * In earlier days, this computer was important and did a lot of computation.
 * Nowadays, the
 * {@link ComponentService.Iface#updateRelease(Release, org.eclipse.sw360.datahandler.thrift.users.User)}
 * and the
 * {@link FossologyService.Iface#process(String, org.eclipse.sw360.datahandler.thrift.users.User)}
 * methods take care of keeping the {@link Release#clearingState} up to date so
 * that this computer really only needs to aggregate the state of all releases.
 */
public class ReleaseClearingStateSummaryComputer {

    public static ReleaseClearingStateSummary computeReleaseClearingStateSummary(List<Release> releases, String clearingTeam) {
        ReleaseClearingStateSummary summary = new ReleaseClearingStateSummary(0, 0, 0, 0, 0);

        if (releases == null) {
            return summary;
        }

        for (Release release : releases) {
            if (release == null) {
                continue;
            }
            if (release.getClearingState() == null) {
                summary.newRelease++;
            } else {
                switch (release.getClearingState()) {
                case NEW_CLEARING:
                    summary.newRelease++;
                    break;
                case SENT_TO_CLEARING_TOOL:
                    summary.sentToClearingTool++;
                    break;
                case UNDER_CLEARING:
                    summary.underClearing++;
                    break;
                case REPORT_AVAILABLE:
                    summary.reportAvailable++;
                    break;
                case APPROVED:
                    summary.approved++;
                    break;
                default:
                    summary.newRelease++;
                    break;
                }
            }

        }
        return summary;
    }
}
