// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.ModelUnavailableException;
import fr.heliocraft.refinr.application.port.in.StoryNotReadyException;
import fr.heliocraft.refinr.application.port.in.StoryTasksInProgressException;
import fr.heliocraft.refinr.application.port.in.StoryTasksResult;
import fr.heliocraft.refinr.application.port.out.IssueTrackerException;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.application.port.out.TaskGeneratorPort;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import fr.heliocraft.refinr.domain.service.OutputLanguagePolicy;
import fr.heliocraft.refinr.domain.service.ReadinessPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Sous-taches d'une story devenue prete : jamais deux fois, dans le projet de la story, langue comme partout. */
class StoryTasksServiceTest {

    private final IssueTrackerPort tracker = mock(IssueTrackerPort.class);
    private final TaskGeneratorPort generator = mock(TaskGeneratorPort.class);
    private final StoryTasksService service = new StoryTasksService(tracker, generator, OutputLanguagePolicy.DETECT);

    private static TrackedStory story(String label, List<String> subtasks) {
        return new TrackedStory("PROJ-7", "Export the orders", "As a customer I want to export my orders", List.of(label),
                null, "PROJ", subtasks);
    }

    private static Task task(String title) {
        Task task = new Task();
        task.setTitle(title);
        return task;
    }

    @Test
    @DisplayName("Ready story without sub-tasks: tasks generated (with the PO clarification) and created in the story's project")
    void createsTheTasks() {
        when(tracker.findStory("PROJ-7")).thenReturn(story(ReadinessPolicy.LABEL_AI_READY, List.of()));
        when(generator.generateTasks(anyList(), anyList(), any())).thenReturn(List.of(List.of(task("API"), task("Tests"))));
        when(tracker.createSubtask(any(), eq("PROJ-7"), eq("PROJ"))).thenReturn("PROJ-8", "PROJ-9");

        StoryTasksResult result = service.generate("PROJ-7", "CSV with ; separator", null);

        assertEquals(List.of("PROJ-8", "PROJ-9"), result.created().stream().map(c -> c.key()).toList());
        assertNull(result.existingSubtasks());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Story>> stories = ArgumentCaptor.forClass(List.class);
        // Sans parametre ni propriete : detection sur le titre et la description de la story
        Language detected = OutputLanguagePolicy.DETECT.resolve(null,
                "Export the orders\nAs a customer I want to export my orders");
        verify(generator).generateTasks(stories.capture(), eq(Arrays.asList("CSV with ; separator")), eq(detected));
        assertEquals("Export the orders", stories.getValue().getFirst().getTitle());
    }

    @Test
    @DisplayName("Story that already has sub-tasks: nothing generated nor created, their number is given")
    void neverTwice() {
        when(tracker.findStory("PROJ-7")).thenReturn(story(ReadinessPolicy.LABEL_AI_READY, List.of("PROJ-8", "PROJ-9")));

        StoryTasksResult result = service.generate("PROJ-7", null, null);

        assertEquals(2, result.existingSubtasks());
        assertEquals("the story already has 2 sub-tasks", result.skippedMessage());
        assertEquals("the story already has 1 sub-task", StoryTasksResult.skipped(1).skippedMessage());
        verifyNoInteractions(generator);
        verify(tracker, never()).createSubtask(any(), any(), any());
    }

    @Test
    @DisplayName("Story not ready: refused (409), no model call")
    void notReadyIsRefused() {
        when(tracker.findStory("PROJ-7")).thenReturn(story(ReadinessPolicy.LABEL_VALIDATION, List.of()));
        assertThrows(StoryNotReadyException.class, () -> service.generate("PROJ-7", null, null));
        verifyNoInteractions(generator);
    }

    @Test
    @DisplayName("Lock on the tracker's key: another spelling of a story being processed is refused too (409)")
    void lockOnTheCanonicalKey() {
        StoryLocks locks = new StoryLocks();
        StoryTasksService locked = new StoryTasksService(tracker, generator, OutputLanguagePolicy.DETECT, locks);
        when(tracker.findStory("proj-7")).thenReturn(story(ReadinessPolicy.LABEL_AI_READY, List.of()));
        assertTrue(locks.tryLock("PROJ-7"), "another request holds PROJ-7");

        assertThrows(StoryTasksInProgressException.class, () -> locked.generate("proj-7", null, null));
        verifyNoInteractions(generator);
        locks.unlock("PROJ-7");
        when(tracker.findStory("PROJ-7")).thenReturn(story(ReadinessPolicy.LABEL_AI_READY, List.of("PROJ-8")));
        assertEquals(1, locked.generate("proj-7", null, null).existingSubtasks(), "lock released, story re-read under it");
    }

    @Test
    @DisplayName("The backlog creation holds the story's lock until its sub-tasks are created: story-tasks gets 409 meanwhile")
    void creationHoldsTheLock() {
        StoryLocks locks = new StoryLocks();
        StoryTasksService storyTasks = new StoryTasksService(tracker, generator, OutputLanguagePolicy.DETECT, locks);
        CreateBacklogService creation = new CreateBacklogService(tracker, FeatureLevels.Policy.LABEL, locks);
        when(tracker.createEpic(any(), any())).thenReturn("PROJ-1");
        when(tracker.createStory(any(), any(), any())).thenReturn("PROJ-7");
        when(tracker.findStory("PROJ-7")).thenReturn(story(ReadinessPolicy.LABEL_AI_READY, List.of()));
        java.util.List<Class<?>> seen = new java.util.ArrayList<>();
        when(tracker.createSubtask(any(), eq("PROJ-7"), any())).thenAnswer(call -> {
            try {
                storyTasks.generate("PROJ-7", null, null);
            } catch (StoryTasksInProgressException e) {
                seen.add(e.getClass());
            }
            return "PROJ-8";
        });
        fr.heliocraft.refinr.domain.model.Epic epic = new fr.heliocraft.refinr.domain.model.Epic();
        epic.setName("E");
        Story ready = new Story();
        ready.setTitle("S");
        ready.setTasks(new java.util.ArrayList<>(List.of(task("API"))));
        epic.setStories(new java.util.ArrayList<>(List.of(ready)));

        creation.create(List.of(epic), null);

        assertEquals(List.of(StoryTasksInProgressException.class), seen);
        verifyNoInteractions(generator);
        assertTrue(locks.tryLock("PROJ-7"), "released after the creation");
    }

    @Test
    @DisplayName("Partial failure: created and failed listed; a new request then finds the sub-tasks and creates nothing")
    void partialFailure() {
        // Deux lectures par demande (avant et sous le verrou) : la seconde demande voit la sous-tache creee
        when(tracker.findStory("PROJ-7")).thenReturn(story(ReadinessPolicy.LABEL_AI_READY, List.of()),
                story(ReadinessPolicy.LABEL_AI_READY, List.of()), story(ReadinessPolicy.LABEL_AI_READY, List.of("PROJ-8")));
        when(generator.generateTasks(anyList(), anyList(), any())).thenReturn(List.of(List.of(task("API"), task("Tests"))));
        when(tracker.createSubtask(any(), eq("PROJ-7"), eq("PROJ"))).thenReturn("PROJ-8")
                .thenThrow(new IssueTrackerException("rejected"));

        StoryTasksResult result = service.generate("PROJ-7", null, null);
        assertEquals(1, result.created().size());
        assertEquals(1, result.failed().size());
        assertEquals(1, service.generate("PROJ-7", null, null).existingSubtasks());
    }

    @Test
    @DisplayName("Language: the request parameter first, then detection on the story")
    void languagePriority() {
        when(tracker.findStory("PROJ-7")).thenReturn(story(ReadinessPolicy.LABEL_AI_READY, List.of()));
        when(generator.generateTasks(anyList(), anyList(), any())).thenReturn(List.of(List.of()));
        service.generate("PROJ-7", null, Language.FR);
        verify(generator).generateTasks(anyList(), anyList(), eq(Language.FR));
        StoryTasksService fixed = new StoryTasksService(tracker, generator, OutputLanguagePolicy.fromProperty("de"));
        fixed.generate("PROJ-7", null, null);
        verify(generator).generateTasks(anyList(), anyList(), eq(Language.of("de")));
    }

    @Test
    @DisplayName("After a verdict: nothing when not ready; a model error is returned in the result, never thrown")
    void afterVerdict() {
        assertTrue(service.afterVerdict("PROJ-7", null, new Readiness(false, "Quel format ?"), null).notReady());
        verifyNoInteractions(tracker);

        when(tracker.findStory("PROJ-7")).thenReturn(story(ReadinessPolicy.LABEL_AI_READY, List.of()));
        when(generator.generateTasks(anyList(), anyList(), any()))
                .thenThrow(new LlmException(LlmException.Reason.UNAVAILABLE, "down"));
        StoryTasksResult result = service.afterVerdict("PROJ-7", "p", new Readiness(true, "ok"), null);
        assertEquals(StoryTasksService.MODEL_FAILED, result.error());
        assertThrows(ModelUnavailableException.class, () -> service.generate("PROJ-7", null, null), "direct call: thrown");
    }
}
