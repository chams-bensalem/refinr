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
    private final fr.heliocraft.refinr.domain.service.OutputLanguagePolicy languagePolicy;

    public ValidateStoryService(IssueTrackerPort issueTracker) {
        this(issueTracker, fr.heliocraft.refinr.domain.service.OutputLanguagePolicy.DETECT);
    }

    public ValidateStoryService(IssueTrackerPort issueTracker,
                                fr.heliocraft.refinr.domain.service.OutputLanguagePolicy languagePolicy) {
        this.issueTracker = issueTracker;
        this.languagePolicy = languagePolicy;
    }

    @Override
    public Readiness validate(String storyKey, fr.heliocraft.refinr.domain.model.Language requested) {
        TrackedStory story = issueTracker.findStory(storyKey);
        if (!ReadinessPolicy.isAwaitingValidation(story.labels())) {
            throw new StoryNotAwaitingValidationException(storyKey);
        }
        // Raison ecrite dans le ticket : langue demandee, puis propriete, puis detection sur la story
        Readiness verdict = ReadinessPolicy.humanValidation(
                languagePolicy.resolve(requested, story.summary() + "\n" + story.description()));
        issueTracker.applyHumanValidation(story, verdict);
        return verdict;
    }
}
