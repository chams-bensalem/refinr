// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.DescribeTracker;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

public class DescribeTrackerService implements DescribeTracker {

    private final IssueTrackerPort issueTracker;
    private final FeatureLevels.Policy featurePolicy;

    public DescribeTrackerService(IssueTrackerPort issueTracker) {
        this(issueTracker, FeatureLevels.Policy.LABEL);
    }

    public DescribeTrackerService(IssueTrackerPort issueTracker, FeatureLevels.Policy featurePolicy) {
        this.issueTracker = issueTracker;
        this.featurePolicy = featurePolicy;
    }

    @Override
    public String displayName() {
        return issueTracker.displayName();
    }

    @Override
    public boolean acceptsProjectId(String projectId) {
        return projectId != null && !projectId.isBlank() && issueTracker.acceptsProjectId(projectId);
    }

    @Override
    public List<String> featureWarnings(List<Epic> epics, String projectId) {
        if (!FeatureLevels.hasExplicitFeatures(epics)) {
            return List.of();
        }
        return FeatureLevels.warnings(epics, FeatureLevels.levelOf(issueTracker, projectId), featurePolicy);
    }
}
