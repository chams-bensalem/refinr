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
    // Lecture du niveau Feature pour l'apercu : 5 s au plus, cache par projet
    private final FeatureLevelLookup featureLevels;

    public DescribeTrackerService(IssueTrackerPort issueTracker) {
        this(issueTracker, FeatureLevels.Policy.LABEL);
    }

    public DescribeTrackerService(IssueTrackerPort issueTracker, FeatureLevels.Policy featurePolicy) {
        this(issueTracker, featurePolicy, new FeatureLevelLookup(issueTracker));
    }

    DescribeTrackerService(IssueTrackerPort issueTracker, FeatureLevels.Policy featurePolicy,
                           FeatureLevelLookup featureLevels) {
        this.issueTracker = issueTracker;
        this.featurePolicy = featurePolicy;
        this.featureLevels = featureLevels;
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
        IssueTrackerPort.FeatureLevel level = featureLevels.level(projectId);
        // Echec, niveau inconnu ou delai depasse : l'apercu part tout de suite, la creation reverifiera
        if (level == IssueTrackerPort.FeatureLevel.UNKNOWN) {
            return List.of(FeatureLevels.UNCHECKED_WARNING);
        }
        return FeatureLevels.warnings(epics, level, featurePolicy);
    }
}
