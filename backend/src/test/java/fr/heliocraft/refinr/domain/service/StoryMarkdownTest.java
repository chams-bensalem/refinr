// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Texte exact produit avant l'extraction (formateurs prives de l'adaptateur Jira) : rien ne doit changer. */
class StoryMarkdownTest {

    private static Story story() {
        Story story = new Story();
        story.setAsA("manager");
        story.setIWant("alerts");
        story.setSoThat("I reorder in time");
        story.setDescription("Low stock alerts.");
        story.setAcceptanceCriteria(List.of("Given a, When b, Then c", "Given d, When e, Then f"));
        story.setAiReady(true);
        story.setAiReadyReason("Clear scope.");
        return story;
    }

    @Test
    @DisplayName("Full story description: user story, description, acceptance criteria and verdict")
    void fullDescription() {
        assertEquals("""
                **As a** manager
                **I want** alerts
                **So that** I reorder in time

                ### Description

                Low stock alerts.


                ### Acceptance Criteria

                - **AC1:** Given a, When b, Then c
                - **AC2:** Given d, When e, Then f


                AI readiness (ai-ready): Clear scope.""", StoryMarkdown.description(story()));
    }

    @Test
    @DisplayName("Without description, criteria nor verdict: defaults of the analysis")
    void defaults() {
        Story story = story();
        story.setDescription(null);
        story.setAcceptanceCriteria(List.of());
        story.setAiReady(null);
        story.setAiReadyReason(null);

        assertEquals("""
                **As a** manager
                **I want** alerts
                **So that** I reorder in time



                No acceptance criteria defined

                AI readiness (validation-necessaire): non evalue par l'analyse""", StoryMarkdown.description(story));
    }

    @Test
    @DisplayName("Reassessment lines: precision, then the new verdict with a default justification")
    void reassessmentLines() {
        assertEquals("Precision de l'utilisateur (reevaluation): Slack #stock", StoryMarkdown.precisionLine("Slack #stock"));
        assertEquals("AI readiness (ai-ready): Channel known.",
                StoryMarkdown.reassessedReadinessLine(new Readiness(true, "Channel known.")));
        assertEquals("AI readiness (validation-necessaire): non precise par l'analyse",
                StoryMarkdown.reassessedReadinessLine(new Readiness(false, " ")));
    }
}
