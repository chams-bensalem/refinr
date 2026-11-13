// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult.CreatedIssue;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult.FailedIssue;
import fr.heliocraft.refinr.application.port.out.IssueTrackerException;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreateBacklogServiceTest {

    /** Gestionnaire de tickets en memoire : renvoie des cles sequentielles et journalise les appels. */
    static class RecordingTracker implements IssueTrackerPort {
        final List<String> calls = new ArrayList<>();
        // Titres dont la creation echoue (IssueTrackerException avec ce message, ou erreur inattendue si "boom")
        final java.util.Set<String> failing = new java.util.HashSet<>();
        private int next = 1;

        @Override
        public String displayName() {
            return "Recording";
        }

        @Override
        public boolean acceptsProjectId(String project) {
            return true;
        }

        @Override
        public String createEpic(Epic epic, String project) {
            fail(epic.getName());
            return record("epic " + epic.getName() + " project=" + project);
        }

        @Override
        public String createStory(Story story, String epicKey, String project) {
            fail(story.getTitle());
            return record("story " + story.getTitle() + " parent=" + epicKey + " project=" + project);
        }

        @Override
        public String createSubtask(Task task, String storyKey, String project) {
            fail(task.getTitle());
            return record("subtask " + task.getTitle() + " parent=" + storyKey + " project=" + project);
        }

        @Override
        public TrackedStory findStory(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void applyReassessment(TrackedStory story, String precision, Readiness verdict) {
            throw new UnsupportedOperationException();
        }

        private void fail(String title) {
            if (failing.contains(title)) {
                if (title.startsWith("boom")) {
                    throw new IllegalStateException("raw detail https://internal.example.net");
                }
                throw new IssueTrackerException("Rejected by the tracker (HTTP 400): " + title);
            }
        }

        private String record(String call) {
            calls.add(call);
            return "PROJ-" + next++;
        }
    }

    @Test
    @DisplayName("Creates Epic -> Story -> Subtask in order and assigns each returned key to the model")
    void createsHierarchyAndAssignsKeys() {
        Task task = new Task();
        task.setTitle("T");
        Story story = new Story();
        story.setTitle("S");
        story.setTasks(List.of(task));
        Epic epic = new Epic();
        epic.setName("E");
        epic.setStories(List.of(story));
        RecordingTracker tracker = new RecordingTracker();

        new CreateBacklogService(tracker).create(List.of(epic), "DEMO");

        assertEquals(List.of(
            "epic E project=DEMO",
            "story S parent=PROJ-1 project=DEMO",
            "subtask T parent=PROJ-2 project=DEMO"), tracker.calls);
        assertEquals("PROJ-1", epic.getTrackerKey());
        assertEquals("PROJ-2", story.getTrackerKey());
        assertEquals("PROJ-1", story.getEpicTrackerKey());
        assertEquals("PROJ-3", task.getTrackerKey());
        assertEquals("PROJ-2", task.getParentTrackerKey());
    }

    @Test
    @DisplayName("Passes a null project through: the tracker adapter picks its default project")
    void passesNullProjectThrough() {
        Epic epic = new Epic();
        epic.setName("E");
        RecordingTracker tracker = new RecordingTracker();

        new CreateBacklogService(tracker).create(List.of(epic), null);

        assertEquals(List.of("epic E project=null"), tracker.calls);
    }

    private static Story story(String title, String... tasks) {
        Story story = new Story();
        story.setTitle(title);
        story.setTasks(java.util.Arrays.stream(tasks).map(t -> {
            Task task = new Task();
            task.setTitle(t);
            return task;
        }).toList());
        return story;
    }

    private static Epic epic(String name, Story... stories) {
        Epic epic = new Epic();
        epic.setName(name);
        epic.setStories(List.of(stories));
        return epic;
    }

    @Test
    @DisplayName("A failure does not stop the creation: the result lists what was created and what failed")
    void failureGivesPartialResult() {
        RecordingTracker tracker = new RecordingTracker();
        tracker.failing.add("S2");
        tracker.failing.add("E2");
        Epic e1 = epic("E1", story("S1", "T1"), story("S2", "T2", "T3"));
        Epic e2 = epic("E2", story("S3", "T4"));
        Epic e3 = epic("E3", story("S4"));

        BacklogCreationResult result = new CreateBacklogService(tracker).create(List.of(e1, e2, e3), null);

        assertEquals(List.of(
            new CreatedIssue("epic", "E1", "PROJ-1"),
            new CreatedIssue("story", "S1", "PROJ-2"),
            new CreatedIssue("subtask", "T1", "PROJ-3"),
            new CreatedIssue("epic", "E3", "PROJ-4"),
            new CreatedIssue("story", "S4", "PROJ-5")), result.created());
        assertEquals(List.of(
            new FailedIssue("story", "S2", "Rejected by the tracker (HTTP 400): S2"),
            new FailedIssue("subtask", "T2", "Not created: parent story S2 failed"),
            new FailedIssue("subtask", "T3", "Not created: parent story S2 failed"),
            new FailedIssue("epic", "E2", "Rejected by the tracker (HTTP 400): E2"),
            new FailedIssue("story", "S3", "Not created: parent epic E2 failed"),
            new FailedIssue("subtask", "T4", "Not created: parent epic E2 failed")), result.failed());
        assertFalse(result.isComplete());
        assertSame(e1, result.epics().get(0));
        assertNull(e2.getTrackerKey());
    }

    @Test
    @DisplayName("An unexpected error is reported with a generic reason, never its raw message")
    void unexpectedErrorHasGenericReason() {
        RecordingTracker tracker = new RecordingTracker();
        tracker.failing.add("boom");

        BacklogCreationResult result = new CreateBacklogService(tracker).create(List.of(epic("boom")), null);

        assertEquals(List.of(new FailedIssue("epic", "boom", "Unexpected error")), result.failed());
        assertTrue(result.created().isEmpty());
    }

    @Test
    @DisplayName("Without failure the result is complete")
    void completeResult() {
        BacklogCreationResult result = new CreateBacklogService(new RecordingTracker())
            .create(List.of(epic("E", story("S"))), null);

        assertTrue(result.isComplete());
        assertEquals(2, result.created().size());
    }
}
