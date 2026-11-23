// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.StoryNotAwaitingValidationException;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReassessStoryServiceTest {

    private final IssueTrackerPort tracker = mock(IssueTrackerPort.class);
    private final ReadinessAssessorPort llm = mock(ReadinessAssessorPort.class);
    private final ReassessStoryService service = new ReassessStoryService(tracker, llm);

    @Test
    @DisplayName("A story awaiting validation is reassessed and the verdict written back")
    void reassessesStoryAwaitingValidation() {
        TrackedStory story = new TrackedStory("PROJ-7", "Export PDF", "Exporter", List.of("validation-necessaire"));
        Readiness verdict = new Readiness(true, "Perimetre clair");
        when(tracker.findStory("PROJ-7")).thenReturn(story);
        when(llm.reassess(eq("Export PDF"), eq("Exporter"), eq("format A4"), any(), any())).thenReturn(verdict);

        assertEquals(verdict, service.reassess("PROJ-7", "format A4"));

        verify(tracker).applyReassessment(eq(story), eq("format A4"), eq(verdict), any());
    }

    @Test
    @DisplayName("A story that is not awaiting validation is refused before any model call")
    void refusesStoryNotAwaitingValidation() {
        when(tracker.findStory("PROJ-7")).thenReturn(
            new TrackedStory("PROJ-7", "Export PDF", "Exporter", List.of("ai-ready")));

        StoryNotAwaitingValidationException error = assertThrows(StoryNotAwaitingValidationException.class,
            () -> service.reassess("PROJ-7", "format A4"));

        assertEquals("Issue PROJ-7 is not awaiting validation (label validation-necessaire absent)", error.getMessage());
        verifyNoInteractions(llm);
        verify(tracker, never()).applyReassessment(any(), any(), any(), any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("B3: the model gets the description without verdict lines and the last open question apart; tracker gets the language")
    void modelInputWithoutVerdictLines() {
        String description = "Exporter\n\n~~AI readiness (validation-necessaire): Quel format ?~~ (remplacée le 2026-11-23)\n\n"
                + "Précision de l'utilisateur (réévaluation) : CSV\n\nAI readiness (validation-necessaire): Quel séparateur ?";
        TrackedStory story = new TrackedStory("PROJ-1", "Export l&#x27;état", description, List.of("validation-necessaire"));
        when(tracker.findStory("PROJ-1")).thenReturn(story);
        Readiness verdict = new Readiness(true, "ok");
        when(llm.reassess(any(), any(), any(), any(), any())).thenReturn(verdict);

        new ReassessStoryService(tracker, llm).reassess("PROJ-1", "Point-virgule", fr.heliocraft.refinr.domain.model.Language.FR);

        verify(llm).reassess(eq("Export l'état"), eq("Exporter\n\nPrécision de l'utilisateur (réévaluation) : CSV"),
                eq("Point-virgule"), eq("Quel séparateur ?"), eq(fr.heliocraft.refinr.domain.model.Language.FR));
        verify(tracker).applyReassessment(eq(story), eq("Point-virgule"), eq(verdict), eq(fr.heliocraft.refinr.domain.model.Language.FR));
    }
}
