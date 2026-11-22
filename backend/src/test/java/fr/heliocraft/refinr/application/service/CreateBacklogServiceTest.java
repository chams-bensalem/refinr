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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class CreateBacklogServiceTest {

    /** Gestionnaire de tickets en memoire : renvoie des cles sequentielles et journalise les appels. */
    static class RecordingTracker implements IssueTrackerPort {
        final List<String> calls = new ArrayList<>();
        // Titres dont la creation echoue (IssueTrackerException avec ce message, ou erreur inattendue si "boom")
        final java.util.Set<String> failing = new java.util.HashSet<>();
        IssueTrackerPort.FeatureLevel level = IssueTrackerPort.FeatureLevel.NONE;
        // Labels recus par chaque story creee
        final java.util.Map<String, List<String>> storyLabels = new java.util.LinkedHashMap<>();
        private int next = 1;

        @Override
        public FeatureLevel featureLevel(String project) {
            if (level == null) {
                throw new IllegalStateException("tracker down");
            }
            return level;
        }

        @Override
        public String createFeature(fr.heliocraft.refinr.domain.model.Feature feature, String epicKey, String project) {
            fail(feature.getName());
            return record("feature " + feature.getName() + " parent=" + epicKey + " project=" + project);
        }

        @Override
        public String displayName() {
            return "Recording";
        }

        @Override
        public boolean acceptsProjectId(String project) {
            return true;
        }

        @Override
        public String issueUrl(String key) {
            return null;
        }

        @Override
        public String createEpic(Epic epic, String project) {
            fail(epic.getName());
            return record("epic " + epic.getName() + " project=" + project);
        }

        @Override
        public String createStory(Story story, String epicKey, String project) {
            fail(story.getTitle());
            storyLabels.put(story.getTitle(), story.getLabels());
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

        @Override
        public void applyHumanValidation(TrackedStory story, Readiness verdict) {
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

    @Test
    @DisplayName("Each created issue carries the web address given by the tracker")
    void createdIssuesCarryTheirUrl() {
        RecordingTracker tracker = new RecordingTracker() {
            @Override
            public String issueUrl(String key) {
                return "https://tracker.example.com/issues/" + key;
            }
        };
        Epic epic = new Epic();
        epic.setName("E1");

        BacklogCreationResult result = new CreateBacklogService(tracker).create(List.of(epic), null);

        assertEquals("https://tracker.example.com/issues/" + result.created().get(0).key(), result.created().get(0).url());
    }
    @Test
    @DisplayName("Failures are logged by position in the backlog (epic 2, story 1.2), never by title")
    void failuresAreLoggedByPosition(CapturedOutput output) {
        RecordingTracker tracker = new RecordingTracker();
        tracker.failing.add("boom-Title-marker-4b1d");
        tracker.failing.add("Story-marker-8e2a");

        new CreateBacklogService(tracker).create(List.of(
                epic("E1", story("S1"), story("Story-marker-8e2a", "T1")),
                epic("boom-Title-marker-4b1d", story("S3"))), null);

        assertTrue(output.getAll().contains("Failed to create story 1.2: "), output.getAll());
        assertTrue(output.getAll().contains("Unexpected error creating epic 2"), output.getAll());
        assertFalse(output.getAll().contains("'Story-marker-8e2a'"), output.getAll());
        // Erreur inattendue : ni le titre ni le message brut (le message des erreurs du tracker releve de #33)
        assertFalse(output.getAll().contains("Title-marker-4b1d"), output.getAll());
    }

    // ---------- niveau Feature (F1) ----------

    private static fr.heliocraft.refinr.domain.model.Feature feature(String name, Story... stories) {
        fr.heliocraft.refinr.domain.model.Feature feature = new fr.heliocraft.refinr.domain.model.Feature();
        feature.setName(name);
        feature.setStories(new ArrayList<>(List.of(stories)));
        return feature;
    }

    private static Epic epicWith(String name, fr.heliocraft.refinr.domain.model.Feature... features) {
        Epic epic = new Epic();
        epic.setName(name);
        epic.setFeatures(new ArrayList<>(List.of(features)));
        return epic;
    }

    @Test
    @DisplayName("Native feature level: epic -> feature -> story -> subtask, stories under their feature, no label, no warning")
    void nativeFeatures() {
        RecordingTracker tracker = new RecordingTracker();
        tracker.level = IssueTrackerPort.FeatureLevel.OPTIONAL;

        BacklogCreationResult result = new CreateBacklogService(tracker).create(List.of(
                epicWith("E", feature("Booking", story("S1", "T1")), feature("Admin", story("S2")))), null);

        assertEquals(List.of(
                "epic E project=null",
                "feature Booking parent=PROJ-1 project=null",
                "story S1 parent=PROJ-2 project=null",
                "subtask T1 parent=PROJ-3 project=null",
                "feature Admin parent=PROJ-1 project=null",
                "story S2 parent=PROJ-5 project=null"), tracker.calls);
        assertEquals("PROJ-2", result.epics().getFirst().getFeatures().getFirst().getTrackerKey());
        assertTrue(result.warnings().isEmpty());
        assertFalse(tracker.storyLabels.get("S1").stream().anyMatch(l -> l.startsWith("feature-")));
        assertEquals(List.of("epic", "feature", "story", "subtask", "feature", "story"),
                result.created().stream().map(CreatedIssue::type).toList());
    }

    @Test
    @DisplayName("Optional level: the implicit feature (only one of its epic) gets no issue, stories go under the epic")
    void implicitFeatureHasNoIssue() {
        RecordingTracker tracker = new RecordingTracker();
        tracker.level = IssueTrackerPort.FeatureLevel.OPTIONAL;

        new CreateBacklogService(tracker).create(List.of(epicWith("E", feature("Export", story("S1")))), null);

        assertEquals(List.of("epic E project=null", "story S1 parent=PROJ-1 project=null"), tracker.calls);
    }

    @Test
    @DisplayName("Required level: the implicit feature gets an issue, named after the epic when it has no name")
    void requiredLevelCreatesTheImplicitFeature() {
        RecordingTracker tracker = new RecordingTracker();
        tracker.level = IssueTrackerPort.FeatureLevel.REQUIRED;

        new CreateBacklogService(tracker).create(List.of(epic("Order export", story("S1"))), null);

        assertEquals(List.of("epic Order export project=null", "feature Order export parent=PROJ-1 project=null",
                "story S1 parent=PROJ-2 project=null"), tracker.calls);
    }

    @Test
    @DisplayName("No feature level: stories under the epic, feature-<name> label for explicit features only, one warning")
    void noFeatureLevelGivesLabels() {
        RecordingTracker tracker = new RecordingTracker();

        BacklogCreationResult result = new CreateBacklogService(tracker).create(List.of(
                epicWith("E", feature("Room Booking", story("S1")), feature("Admin", story("S2"))),
                epicWith("F", feature("Single", story("S3")))), null);

        assertEquals(List.of("epic E project=null", "story S1 parent=PROJ-1 project=null",
                "story S2 parent=PROJ-1 project=null", "epic F project=null", "story S3 parent=PROJ-4 project=null"),
                tracker.calls);
        assertTrue(tracker.storyLabels.get("S1").contains("feature-room-booking"), tracker.storyLabels.toString());
        assertTrue(tracker.storyLabels.get("S2").contains("feature-admin"));
        assertFalse(tracker.storyLabels.get("S3").stream().anyMatch(l -> l.startsWith("feature-")), "implicit: no label");
        assertEquals(List.of(FeatureLevels.LABELS_WARNING), result.warnings());
    }

    @Test
    @DisplayName("No feature level and policy refuse: explicit features are refused before any tracker call; implicit ones pass")
    void refusePolicy() {
        RecordingTracker tracker = new RecordingTracker();
        CreateBacklogService service = new CreateBacklogService(tracker, FeatureLevels.Policy.REFUSE);

        assertThrows(fr.heliocraft.refinr.application.port.in.FeatureLevelMissingException.class, () -> service.create(
                List.of(epicWith("E", feature("A", story("S1")), feature("B", story("S2")))), null));
        assertTrue(tracker.calls.isEmpty());

        BacklogCreationResult result = service.create(List.of(epic("E", story("S1"))), null);
        assertTrue(result.isComplete());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    @DisplayName("A failed feature skips its stories and subtasks; the next feature is still created")
    void failedFeatureSkipsItsStories() {
        RecordingTracker tracker = new RecordingTracker();
        tracker.level = IssueTrackerPort.FeatureLevel.OPTIONAL;
        tracker.failing.add("Booking");

        BacklogCreationResult result = new CreateBacklogService(tracker).create(List.of(
                epicWith("E", feature("Booking", story("S1", "T1")), feature("Admin", story("S2")))), null);

        assertEquals(List.of(
                new FailedIssue("feature", "Booking", "Rejected by the tracker (HTTP 400): Booking"),
                new FailedIssue("story", "S1", "Not created: parent feature Booking failed"),
                new FailedIssue("subtask", "T1", "Not created: parent feature Booking failed")), result.failed());
        assertEquals(List.of("epic E project=null", "feature Admin parent=PROJ-1 project=null",
                "story S2 parent=PROJ-2 project=null"), tracker.calls);
    }

    @Test
    @DisplayName("Tracker level unknown at creation (tracker down): treated as no feature level, labels and warning")
    void unknownLevelIsNoLevel() {
        RecordingTracker tracker = new RecordingTracker();
        tracker.level = null;

        BacklogCreationResult result = new CreateBacklogService(tracker).create(List.of(
                epicWith("E", feature("A", story("S1")), feature("B", story("S2")))), null);

        assertTrue(tracker.storyLabels.get("S1").contains("feature-a"));
        assertEquals(List.of(FeatureLevels.LABELS_WARNING), result.warnings());
    }
}
