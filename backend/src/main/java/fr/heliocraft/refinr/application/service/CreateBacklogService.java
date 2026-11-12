package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Cree la hierarchie Epic -> Story -> Subtask, dans l'ordre, et affecte a chaque element la cle de son ticket. */
public class CreateBacklogService implements CreateBacklog {

    private static final Logger log = LoggerFactory.getLogger(CreateBacklogService.class);

    private final IssueTrackerPort issueTracker;

    public CreateBacklogService(IssueTrackerPort issueTracker) {
        this.issueTracker = issueTracker;
    }

    @Override
    public void create(List<Epic> epics, String project) {
        log.info("Creating full hierarchy: {} epics in project {}", epics.size(),
                project == null || project.isBlank() ? "(default)" : project);

        for (Epic epic : epics) {
            String epicKey = issueTracker.createEpic(epic, project);
            epic.setTrackerKey(epicKey);

            for (Story story : epic.getStories()) {
                String storyKey = issueTracker.createStory(story, epicKey, project);
                story.setTrackerKey(storyKey);
                story.setEpicTrackerKey(epicKey);

                for (Task task : story.getTasks()) {
                    String taskKey = issueTracker.createSubtask(task, storyKey, project);
                    task.setTrackerKey(taskKey);
                    task.setParentTrackerKey(storyKey);
                }
            }
        }

        log.info("Full hierarchy created successfully");
    }
}
