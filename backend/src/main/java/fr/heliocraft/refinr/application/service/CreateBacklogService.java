package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult.CreatedIssue;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult.FailedIssue;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.out.IssueTrackerException;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Cree la hierarchie Epic -> Story -> Subtask, dans l'ordre, et affecte a chaque element la cle de son ticket.
 * Un echec n'arrete pas la creation : l'element est signale en echec, ses enfants ne sont pas crees (signales
 * aussi), et les elements suivants sont crees normalement.
 */
public class CreateBacklogService implements CreateBacklog {

    private static final Logger log = LoggerFactory.getLogger(CreateBacklogService.class);

    static final String EPIC = "epic";
    static final String STORY = "story";
    static final String SUBTASK = "subtask";

    private final IssueTrackerPort issueTracker;

    public CreateBacklogService(IssueTrackerPort issueTracker) {
        this.issueTracker = issueTracker;
    }

    @Override
    public BacklogCreationResult create(List<Epic> epics, String project) {
        log.info("Creating full hierarchy: {} epics in project {}", epics.size(),
                project == null || project.isBlank() ? "(default)" : project);
        List<CreatedIssue> created = new ArrayList<>();
        List<FailedIssue> failed = new ArrayList<>();

        for (Epic epic : epics) {
            String epicKey = attempt(EPIC, epic.getName(), () -> issueTracker.createEpic(epic, project), created, failed);
            if (epicKey == null) {
                skipStories(epic.getStories(), "Not created: parent epic " + epic.getName() + " failed", failed);
                continue;
            }
            epic.setTrackerKey(epicKey);

            for (Story story : nonNull(epic.getStories())) {
                String storyKey = attempt(STORY, story.getTitle(),
                        () -> issueTracker.createStory(story, epicKey, project), created, failed);
                if (storyKey == null) {
                    skipTasks(story.getTasks(), "Not created: parent story " + story.getTitle() + " failed", failed);
                    continue;
                }
                story.setTrackerKey(storyKey);
                story.setEpicTrackerKey(epicKey);

                for (Task task : nonNull(story.getTasks())) {
                    String taskKey = attempt(SUBTASK, task.getTitle(),
                            () -> issueTracker.createSubtask(task, storyKey, project), created, failed);
                    if (taskKey != null) {
                        task.setTrackerKey(taskKey);
                        task.setParentTrackerKey(storyKey);
                    }
                }
            }
        }

        if (failed.isEmpty()) {
            log.info("Full hierarchy created successfully ({} issues)", created.size());
        } else {
            log.warn("Hierarchy partially created: {} created, {} not created", created.size(), failed.size());
        }
        return new BacklogCreationResult(epics, created, failed);
    }

    /** Cle du ticket cree, ou null si la creation a echoue (echec consigne). */
    private static String attempt(String type, String title, Supplier<String> creation,
                                  List<CreatedIssue> created, List<FailedIssue> failed) {
        try {
            String key = creation.get();
            created.add(new CreatedIssue(type, title, key));
            return key;
        } catch (IssueTrackerException e) {
            log.error("Failed to create {} '{}': {}", type, title, e.getMessage(), e);
            failed.add(new FailedIssue(type, title, e.getMessage()));
        } catch (RuntimeException e) {
            // Message brut jamais renvoye au client : il reste dans les logs
            log.error("Unexpected error creating {} '{}'", type, title, e);
            failed.add(new FailedIssue(type, title, "Unexpected error"));
        }
        return null;
    }

    private static void skipStories(List<Story> stories, String reason, List<FailedIssue> failed) {
        for (Story story : nonNull(stories)) {
            failed.add(new FailedIssue(STORY, story.getTitle(), reason));
            skipTasks(story.getTasks(), reason, failed);
        }
    }

    private static void skipTasks(List<Task> tasks, String reason, List<FailedIssue> failed) {
        for (Task task : nonNull(tasks)) {
            failed.add(new FailedIssue(SUBTASK, task.getTitle(), reason));
        }
    }

    private static <T> List<T> nonNull(List<T> items) {
        return items != null ? items : List.of();
    }
}
