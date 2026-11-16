// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.domain.service.OutputLanguagePolicy;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.application.port.in.ReassessStory;
import fr.heliocraft.refinr.application.port.in.StoryNotAwaitingValidationException;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import fr.heliocraft.refinr.domain.service.ReadinessPolicy;

public class ReassessStoryService implements ReassessStory {

    private final IssueTrackerPort issueTracker;
    private final ReadinessAssessorPort assessor;
    private final OutputLanguagePolicy languagePolicy;

    public ReassessStoryService(IssueTrackerPort issueTracker, ReadinessAssessorPort assessor) {
        this(issueTracker, assessor, OutputLanguagePolicy.DETECT);
    }

    public ReassessStoryService(IssueTrackerPort issueTracker, ReadinessAssessorPort assessor,
                                OutputLanguagePolicy languagePolicy) {
        this.languagePolicy = languagePolicy;
        this.issueTracker = issueTracker;
        this.assessor = assessor;
    }

    @Override
    public Readiness reassess(String storyKey, String precision, Language requested) {
        TrackedStory story = issueTracker.findStory(storyKey);
        // Seules les stories en attente de validation sont reevaluees : une story ai-ready ne regresse pas ici
        if (!ReadinessPolicy.isAwaitingValidation(story.labels())) {
            throw new StoryNotAwaitingValidationException(storyKey);
        }
        Language language = languagePolicy.resolve(requested, story.summary() + "\n" + story.description());
        Readiness verdict = LlmFailures.translate(() -> assessor.reassess(story.summary(), story.description(), precision, language));
        issueTracker.applyReassessment(story, precision, verdict);
        return verdict;
    }
}
