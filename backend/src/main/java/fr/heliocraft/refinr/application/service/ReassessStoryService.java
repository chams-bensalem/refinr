package fr.heliocraft.refinr.application.service;

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

    public ReassessStoryService(IssueTrackerPort issueTracker, ReadinessAssessorPort assessor) {
        this.issueTracker = issueTracker;
        this.assessor = assessor;
    }

    @Override
    public Readiness reassess(String storyKey, String precision) {
        TrackedStory story = issueTracker.findStory(storyKey);
        // Seules les stories en attente de validation sont reevaluees : une story ai-ready ne regresse pas ici
        if (!ReadinessPolicy.isAwaitingValidation(story.labels())) {
            throw new StoryNotAwaitingValidationException(storyKey);
        }
        Readiness verdict = LlmFailures.translate(() -> assessor.reassess(story.summary(), story.description(), precision));
        issueTracker.applyReassessment(story, precision, verdict);
        return verdict;
    }
}
