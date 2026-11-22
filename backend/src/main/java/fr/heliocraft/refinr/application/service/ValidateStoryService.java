// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.StoryNotAwaitingValidationException;
import fr.heliocraft.refinr.application.port.in.ValidateStory;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import fr.heliocraft.refinr.domain.service.ReadinessPolicy;

/**
 * Validation humaine directe : memes gardes que la reevaluation (seule une story au label {@code validation-necessaire}
 * est acceptee), ligne de verdict ajoutee et label remplace par {@code ai-ready} ; aucun appel au modele.
 */
public class ValidateStoryService implements ValidateStory {

    private final IssueTrackerPort issueTracker;

    public ValidateStoryService(IssueTrackerPort issueTracker) {
        this.issueTracker = issueTracker;
    }

    @Override
    public Readiness validate(String storyKey) {
        TrackedStory story = issueTracker.findStory(storyKey);
        if (!ReadinessPolicy.isAwaitingValidation(story.labels())) {
            throw new StoryNotAwaitingValidationException(storyKey);
        }
        Readiness verdict = ReadinessPolicy.humanValidation();
        issueTracker.applyHumanValidation(story, verdict);
        return verdict;
    }
}
