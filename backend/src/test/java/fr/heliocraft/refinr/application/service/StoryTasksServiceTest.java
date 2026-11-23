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

    private final fr.heliocraft.refinr.application.port.out.TestCaseGeneratorPort testCaseGenerator =
            mock(fr.heliocraft.refinr.application.port.out.TestCaseGeneratorPort.class);
    private final StoryTasksService withTestCases = new StoryTasksService(tracker, generator, OutputLanguagePolicy.DETECT,
            new StoryLocks(), testCaseGenerator);

    private static TrackedStory written(List<String> subtasks, String extra) {
        return new TrackedStory("PROJ-7", "Export the orders", "As a customer I want to export my orders\n"
                + "### Acceptance Criteria\n- **AC1:** Given paid orders, When I export, Then a CSV is downloaded\n" + extra
                + "AI readiness (ai-ready): clear", List.of(ReadinessPolicy.LABEL_AI_READY), null, "PROJ", subtasks);
    }

    private static fr.heliocraft.refinr.domain.model.TestCase tc(String criterion) {
        return new fr.heliocraft.refinr.domain.model.TestCase("CSV", criterion, "nominal", "paid orders", "I export", "a CSV");
    }

    @Test
    @DisplayName("C2: after its sub-tasks, the story's test cases are written into its description (criteria read back from it)")
    void testCasesWritten() {
        when(tracker.findStory("PROJ-7")).thenReturn(written(List.of("PROJ-8"), ""));
        when(testCaseGenerator.generateTestCases(anyList(), anyList(), any())).thenReturn(List.of(List.of(tc("AC1"))));
        when(tracker.insertTestCases(eq("PROJ-7"), any())).thenReturn(true);

        StoryTasksResult result = withTestCases.generate("PROJ-7", null, Language.EN);

        assertEquals(1, result.existingSubtasks(), "sub-tasks already there: skipped (recovery path)");
        assertEquals(1, result.testCases(), "only the test cases are generated");
        verifyNoInteractions(generator);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Story>> input = ArgumentCaptor.forClass(List.class);
        verify(testCaseGenerator).generateTestCases(input.capture(), anyList(), eq(Language.EN));
        assertEquals(List.of("Given paid orders, When I export, Then a CSV is downloaded"),
                input.getValue().getFirst().getAcceptanceCriteria());
        ArgumentCaptor<String> section = ArgumentCaptor.forClass(String.class);
        verify(tracker).insertTestCases(eq("PROJ-7"), section.capture());
        assertTrue(section.getValue().startsWith("### Test cases\n\n- **TC1 (AC1, nominal):** CSV"), section.getValue());
    }

    @Test
    @DisplayName("C2 review: unexpected error while writing the test cases (Jira unreachable) -> sub-tasks kept in the result")
    void writeFailureKeepsTheSubtasks() {
        when(tracker.findStory("PROJ-7")).thenReturn(written(List.of(), ""));
        when(generator.generateTasks(anyList(), anyList(), any())).thenReturn(List.of(List.of(task("API"))));
        when(tracker.createSubtask(any(), eq("PROJ-7"), any())).thenReturn("PROJ-8");
        when(testCaseGenerator.generateTestCases(anyList(), anyList(), any())).thenReturn(List.of(List.of(tc("AC1"))));
        when(tracker.insertTestCases(eq("PROJ-7"), any())).thenThrow(new IllegalStateException("connection reset"));

        StoryTasksResult result = withTestCases.generate("PROJ-7", null, null);

        assertEquals(List.of("PROJ-8"), result.created().stream().map(c -> c.key()).toList());
        assertEquals(StoryTasksService.TEST_CASES_WRITE_FAILED, result.testCasesError());
    }

    @Test
    @DisplayName("C2 real trial: editor-rewritten description -> decoded criteria and text sent to prompts 4 and 5, never &#x27;")
    void editorRewrittenDescriptionIsDecoded() {
        when(tracker.findStory("352")).thenReturn(new TrackedStory("352", "Consulter l&#x27;occupation",
                fr.heliocraft.refinr.TrackerFixtures.OPENPROJECT_EDITED, List.of(ReadinessPolicy.LABEL_AI_READY),
                null, "3", List.of()));
        when(generator.generateTasks(anyList(), anyList(), any())).thenReturn(List.of(List.of(task("API"))));
        when(tracker.createSubtask(any(), eq("352"), any())).thenReturn("353");
        when(testCaseGenerator.generateTestCases(anyList(), anyList(), any())).thenReturn(List.of(List.of(tc("AC1"), tc("AC2"))));
        when(tracker.insertTestCases(eq("352"), any())).thenReturn(true);

        StoryTasksResult result = withTestCases.generate("352", "Affichage par semaine", Language.FR);

        assertEquals(2, result.testCases());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Story>> toPrompt5 = ArgumentCaptor.forClass(List.class);
        verify(testCaseGenerator).generateTestCases(toPrompt5.capture(), eq(Arrays.asList("Affichage par semaine")), eq(Language.FR));
        Story sent = toPrompt5.getValue().getFirst();
        assertEquals("Consulter l'occupation", sent.getTitle());
        assertTrue(sent.getAcceptanceCriteria().getFirst().contains("l'occupation des salles"));
        assertFalse(sent.getDescription().contains("&#x27;") || sent.getAcceptanceCriteria().toString().contains("&#x27;"));
        assertFalse(sent.getDescription().contains("AI readiness"), "B3: no verdict line sent to prompt 5");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Story>> toPrompt4 = ArgumentCaptor.forClass(List.class);
        verify(generator).generateTasks(toPrompt4.capture(), anyList(), any());
        assertFalse(toPrompt4.getValue().getFirst().getDescription().contains("&#x27;"));
        assertFalse(toPrompt4.getValue().getFirst().getDescription().contains("AI readiness"), "B3: no verdict line sent to prompt 4");
    }

    @Test
    @DisplayName("M1/R3: prompt 5 receives the PO note on both trackers (OpenProject: after the labels line; Jira: end of the text)")
    void poNoteReachesPrompt5OnBothTrackers() {
        String openProject = "As a user\n\n### Acceptance Criteria\n\n- **AC1:** Given a, Then b\n\n"
                + "AI readiness (ai-ready): ok\n\n**Labels:** backend\n\nNote du PO : affichage par semaine.";
        String jira = "As a user\nAcceptance Criteria\nAC1: Given a, Then b\nAI readiness (ai-ready): ok\n"
                + "Labels: backend\nNote du PO : affichage par semaine.";
        for (String description : List.of(openProject, jira)) {
            reset(testCaseGenerator, tracker);
            when(tracker.findStory("PROJ-7")).thenReturn(new TrackedStory("PROJ-7", "S", description,
                    List.of(ReadinessPolicy.LABEL_AI_READY), null, "PROJ", List.of("PROJ-8")));
            when(testCaseGenerator.generateTestCases(anyList(), anyList(), any())).thenReturn(List.of(List.of(tc("AC1"))));
            when(tracker.insertTestCases(eq("PROJ-7"), any())).thenReturn(true);

            withTestCases.generate("PROJ-7", null, Language.FR);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Story>> input = ArgumentCaptor.forClass(List.class);
            verify(testCaseGenerator).generateTestCases(input.capture(), anyList(), any());
            assertTrue(input.getValue().getFirst().getDescription().contains("Note du PO : affichage par semaine."),
                    input.getValue().getFirst().getDescription());
        }
    }

    @Test
    @DisplayName("C2 real trial: on the createTasks path, prompt 5 receives the same PO clarification as prompt 4")
    void clarificationReachesPrompt5() {
        when(tracker.findStory("PROJ-7")).thenReturn(written(List.of(), ""));
        when(generator.generateTasks(anyList(), anyList(), any())).thenReturn(List.of(List.of(task("API"))));
        when(tracker.createSubtask(any(), eq("PROJ-7"), any())).thenReturn("PROJ-8");
        when(testCaseGenerator.generateTestCases(anyList(), anyList(), any())).thenReturn(List.of(List.of(tc("AC1"))));
        when(tracker.insertTestCases(eq("PROJ-7"), any())).thenReturn(true);

        withTestCases.afterVerdict("PROJ-7", "e-mail uniquement", new Readiness(true, "ok"), null);

        verify(generator).generateTasks(anyList(), eq(Arrays.asList("e-mail uniquement")), any());
        verify(testCaseGenerator).generateTestCases(anyList(), eq(Arrays.asList("e-mail uniquement")), any());
    }

    @Test
    @DisplayName("C2: coverage not compliant after the repair -> testCasesError naming the criteria, nothing written")
    void nonCompliantCoverage() {
        when(tracker.findStory("PROJ-7")).thenReturn(written(List.of("PROJ-8"), ""));
        when(testCaseGenerator.generateTestCases(anyList(), anyList(), any())).thenReturn(List.of(List.of(tc("AC1"), tc("AC5"))));

        StoryTasksResult result = withTestCases.generate("PROJ-7", null, null);

        assertEquals("test cases not written: test case 2: criterion must name an existing acceptance criterion (AC1 to AC1)",
                result.testCasesError());
        verify(tracker, never()).insertTestCases(any(), any());
    }

    @Test
    @DisplayName("C2: section already there -> testCasesSkipped, no model call; no criteria -> testCasesError")
    void testCasesSkippedOrImpossible() {
        when(tracker.findStory("PROJ-7")).thenReturn(written(List.of("PROJ-8"), "### Test cases\n- TC1\n"));
        assertTrue(withTestCases.generate("PROJ-7", null, null).testCasesSkipped());
        verifyNoInteractions(testCaseGenerator);

        when(tracker.findStory("PROJ-7")).thenReturn(story(ReadinessPolicy.LABEL_AI_READY, List.of("PROJ-8")));
        assertEquals(StoryTasksService.NO_CRITERIA, withTestCases.generate("PROJ-7", null, null).testCasesError());
    }

    @Test
    @DisplayName("C2: model failure or a description that would be too long -> testCasesError; the sub-tasks stay created")
    void testCasesErrors() {
        when(tracker.findStory("PROJ-7")).thenReturn(written(List.of(), ""));
        when(generator.generateTasks(anyList(), anyList(), any())).thenReturn(List.of(List.of(task("API"))));
        when(tracker.createSubtask(any(), eq("PROJ-7"), any())).thenReturn("PROJ-8");
        when(testCaseGenerator.generateTestCases(anyList(), anyList(), any()))
                .thenThrow(new LlmException(LlmException.Reason.UNAVAILABLE, "down"));

        StoryTasksResult failed = withTestCases.generate("PROJ-7", null, null);
        assertEquals(1, failed.created().size());
        assertEquals(StoryTasksService.TEST_CASES_MODEL_FAILED, failed.testCasesError());

        reset(testCaseGenerator);
        when(tracker.findStory("PROJ-7")).thenReturn(written(List.of("PROJ-8"), ""));
        when(testCaseGenerator.generateTestCases(anyList(), anyList(), any())).thenReturn(List.of(List.of(tc("AC1"))));
        when(tracker.insertTestCases(eq("PROJ-7"), any())).thenThrow(new IssueTrackerException(IssueTrackerPort.TEST_CASES_TOO_LONG));
        assertEquals(IssueTrackerPort.TEST_CASES_TOO_LONG, withTestCases.generate("PROJ-7", null, null).testCasesError());
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
