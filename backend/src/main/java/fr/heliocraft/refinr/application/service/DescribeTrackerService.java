// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.DescribeTracker;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;

public class DescribeTrackerService implements DescribeTracker {

    private final IssueTrackerPort issueTracker;

    public DescribeTrackerService(IssueTrackerPort issueTracker) {
        this.issueTracker = issueTracker;
    }

    @Override
    public String displayName() {
        return issueTracker.displayName();
    }

    @Override
    public boolean acceptsProjectId(String projectId) {
        return projectId != null && !projectId.isBlank() && issueTracker.acceptsProjectId(projectId);
    }
}
