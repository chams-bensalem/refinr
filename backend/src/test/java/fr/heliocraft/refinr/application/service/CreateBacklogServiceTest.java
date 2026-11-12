package fr.heliocraft.refinr.application.service;

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
        private int next = 1;

        @Override
        public String createEpic(Epic epic, String project) {
            return record("epic " + epic.getName() + " project=" + project);
        }

        @Override
        public String createStory(Story story, String epicKey, String project) {
            return record("story " + story.getTitle() + " parent=" + epicKey + " project=" + project);
        }

        @Override
        public String createSubtask(Task task, String storyKey, String project) {
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
}
