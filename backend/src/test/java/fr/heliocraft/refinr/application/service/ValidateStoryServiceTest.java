// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.StoryNotAwaitingValidationException;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.ReadinessSource;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ValidateStoryServiceTest {

    private final IssueTrackerPort tracker = mock(IssueTrackerPort.class);
    private final ValidateStoryService service = new ValidateStoryService(tracker);

    @Test
    @DisplayName("A story awaiting validation is marked ai-ready by a human, source human, verdict written back")
    void validatesStoryAwaitingValidation() {
        TrackedStory story = new TrackedStory("PROJ-7", "Export PDF", "Exporter", List.of("validation-necessaire"));
        when(tracker.findStory("PROJ-7")).thenReturn(story);

        Readiness verdict = service.validate("PROJ-7");

        assertTrue(verdict.aiReady());
        assertEquals(ReadinessSource.HUMAN, verdict.source());
        verify(tracker).applyHumanValidation(eq(story), eq(verdict), any());
        verify(tracker, never()).applyReassessment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("A story that is not awaiting validation is refused, nothing written")
    void refusesStoryNotAwaitingValidation() {
        when(tracker.findStory("PROJ-7")).thenReturn(
                new TrackedStory("PROJ-7", "Export PDF", "Exporter", List.of("ai-ready")));

        assertThrows(StoryNotAwaitingValidationException.class, () -> service.validate("PROJ-7"));

        verify(tracker, never()).applyHumanValidation(any(), any(), any());
    }
}
