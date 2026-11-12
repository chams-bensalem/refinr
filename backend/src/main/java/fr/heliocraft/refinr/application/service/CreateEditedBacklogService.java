package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.in.CreateEditedBacklog;
import fr.heliocraft.refinr.application.port.out.LlmPort;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.service.ReadinessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CreateEditedBacklogService implements CreateEditedBacklog {

    private static final Logger log = LoggerFactory.getLogger(CreateEditedBacklogService.class);

    private final LlmPort llm;
    private final CreateBacklog createBacklog;

    public CreateEditedBacklogService(LlmPort llm, CreateBacklog createBacklog) {
        this.llm = llm;
        this.createBacklog = createBacklog;
    }

    @Override
    public BacklogCreationResult create(List<Epic> epics, String project) {
        for (Epic epic : epics) {
            if (epic.getStories() == null) {
                continue;
            }
            for (Story story : epic.getStories()) {
                Readiness verdict = assess(story);
                story.setAiReady(verdict.aiReady());
                story.setAiReadyReason(verdict.aiReadyReason());
            }
        }
        return createBacklog.create(epics, project);
    }

    /** Verdict sur le contenu final ; sans verdict fiable, jamais ai-ready (validation humaine). */
    private Readiness assess(Story story) {
        try {
            Readiness verdict = llm.assessStory(story);
            return verdict != null ? verdict : ReadinessPolicy.unavailable("no verdict");
        } catch (RuntimeException e) {
            log.warn("Readiness verdict unavailable for '{}': {}", story.getTitle(), e.getClass().getSimpleName());
            return ReadinessPolicy.unavailable(e.getClass().getSimpleName());
        }
    }
}
