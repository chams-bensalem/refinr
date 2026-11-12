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
        when(llm.reassess("Export PDF", "Exporter", "format A4")).thenReturn(verdict);

        assertEquals(verdict, service.reassess("PROJ-7", "format A4"));

        verify(tracker).applyReassessment(story, "format A4", verdict);
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
        verify(tracker, never()).applyReassessment(any(), any(), any());
    }
}
