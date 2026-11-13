// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.service.ReadinessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Applique aux stories d'un backlog le verdict du fournisseur de verdict ; sans verdict fiable, jamais ai-ready. */
final class StoryVerdicts {

    private static final Logger log = LoggerFactory.getLogger(StoryVerdicts.class);

    private StoryVerdicts() {}

    static void apply(List<Epic> epics, ReadinessAssessorPort assessor) {
        List<Story> stories = new ArrayList<>();
        for (Epic epic : epics) {
            if (epic.getStories() != null) {
                epic.getStories().stream().filter(story -> story != null).forEach(stories::add);
            }
        }
        if (stories.isEmpty()) {
            return;
        }
        List<Readiness> verdicts = null;
        String failure = "no verdict";
        try {
            verdicts = assessor.assess(stories);
        } catch (RuntimeException e) {
            failure = e.getClass().getSimpleName();
            log.warn("Readiness verdicts unavailable for {} stories: {}", stories.size(), failure);
        }
        for (int i = 0; i < stories.size(); i++) {
            Story story = stories.get(i);
            Readiness verdict = verdicts != null && i < verdicts.size() ? verdicts.get(i) : null;
            if (verdict == null) {
                log.warn("Readiness verdict unavailable for story {}/{}", i + 1, stories.size());
                verdict = ReadinessPolicy.unavailable(failure);
            }
            story.setAiReady(verdict.aiReady());
            story.setAiReadyReason(verdict.aiReadyReason());
        }
    }
}
