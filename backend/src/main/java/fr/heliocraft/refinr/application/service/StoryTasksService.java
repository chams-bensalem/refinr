// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.BacklogCreationResult.CreatedIssue;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult.FailedIssue;
import fr.heliocraft.refinr.application.port.in.GenerateStoryTasks;
import fr.heliocraft.refinr.application.port.in.ModelOutputException;
import fr.heliocraft.refinr.application.port.in.ModelUnavailableException;
import fr.heliocraft.refinr.application.port.in.StoryNotReadyException;
import fr.heliocraft.refinr.application.port.in.StoryTasksInProgressException;
import fr.heliocraft.refinr.application.port.in.StoryTasksResult;
import fr.heliocraft.refinr.application.port.out.IssueTrackerException;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.TaskGeneratorPort;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import fr.heliocraft.refinr.domain.service.OutputLanguagePolicy;
import fr.heliocraft.refinr.domain.service.ReadinessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sous-taches d'une story devenue prete (chaine C1). Idempotence : le gestionnaire fait foi (une story qui a deja des
 * sous-taches n'est jamais touchee, leur nombre est rendu) et un verrou par story, dans ce processus, empeche deux
 * demandes simultanees de creer les memes sous-taches (une seule instance de Refinr, comme les jobs). Les sous-taches
 * sont creees dans le projet de la story. Les logs citent la cle et des nombres, jamais le contenu.
 */
public class StoryTasksService implements GenerateStoryTasks {

    private static final Logger log = LoggerFactory.getLogger(StoryTasksService.class);

    static final String MODEL_FAILED = "The model could not generate the sub-tasks: try again with POST /api/v1/story-tasks";
    static final String CREATION_FAILED = "The sub-tasks could not be created: try again with POST /api/v1/story-tasks";

    private final IssueTrackerPort issueTracker;
    private final TaskGeneratorPort taskGenerator;
    private final OutputLanguagePolicy languagePolicy;
    private final StoryLocks locks;

    public StoryTasksService(IssueTrackerPort issueTracker, TaskGeneratorPort taskGenerator,
                             OutputLanguagePolicy languagePolicy) {
        this(issueTracker, taskGenerator, languagePolicy, new StoryLocks());
    }

    public StoryTasksService(IssueTrackerPort issueTracker, TaskGeneratorPort taskGenerator,
                             OutputLanguagePolicy languagePolicy, StoryLocks locks) {
        this.issueTracker = issueTracker;
        this.taskGenerator = taskGenerator;
        this.languagePolicy = languagePolicy;
        this.locks = locks;
    }

    @Override
    public StoryTasksResult generate(String storyKey, String precision, Language requested) {
        // Verrou sur la cle que rend le gestionnaire (PROJ-7 et proj-7, 039 et 39 : une seule story), puis relecture
        // sous le verrou : les sous-taches creees entre-temps par un autre chemin sont vues
        String key = issueTracker.findStory(storyKey).key();
        if (!locks.tryLock(key)) {
            throw new StoryTasksInProgressException(key);
        }
        try {
            TrackedStory story = issueTracker.findStory(key);
            storyKey = key;
            if (!story.labels().contains(ReadinessPolicy.LABEL_AI_READY)) {
                throw new StoryNotReadyException(storyKey);
            }
            if (!story.subtaskKeys().isEmpty()) {
                log.info("Story {} already has {} sub-task(s): nothing generated", storyKey, story.subtaskKeys().size());
                return StoryTasksResult.skipped(story.subtaskKeys().size());
            }
            // Meme priorite que partout : parametre language, puis propriete, puis detection sur la story
            Language language = languagePolicy.resolve(requested, story.summary() + "\n" + story.description());
            Story input = new Story();
            input.setTitle(story.summary());
            input.setDescription(story.description());
            List<List<Task>> answer = LlmFailures.translate(() -> taskGenerator.generateTasks(List.of(input),
                    Collections.singletonList(precision), language));
            List<Task> tasks = answer.isEmpty() ? null : answer.getFirst();
            if (tasks == null) {
                throw new ModelOutputException(false, null);
            }
            List<CreatedIssue> created = new ArrayList<>();
            List<FailedIssue> failed = new ArrayList<>();
            for (int i = 0; i < tasks.size(); i++) {
                Task task = tasks.get(i);
                try {
                    String subtaskKey = issueTracker.createSubtask(task, storyKey, story.project());
                    created.add(new CreatedIssue("subtask", task.getTitle(), subtaskKey, issueTracker.issueUrl(subtaskKey)));
                } catch (IssueTrackerException e) {
                    log.error("Failed to create sub-task {}/{} of {}: {}", i + 1, tasks.size(), storyKey, e.getMessage());
                    failed.add(new FailedIssue("subtask", task.getTitle(), e.clientMessage()));
                } catch (RuntimeException e) {
                    log.error("Unexpected error creating sub-task {}/{} of {}", i + 1, tasks.size(), storyKey, e);
                    failed.add(new FailedIssue("subtask", task.getTitle(), "Unexpected error"));
                }
            }
            log.info("Story {}: {} sub-task(s) created, {} failed", storyKey, created.size(), failed.size());
            return new StoryTasksResult(created, failed, null, false, null);
        } finally {
            locks.unlock(key);
        }
    }

    @Override
    public StoryTasksResult afterVerdict(String storyKey, String precision, Readiness verdict, Language language) {
        if (verdict == null || !verdict.aiReady()) {
            return StoryTasksResult.notReadyYet();
        }
        try {
            return generate(storyKey, precision, language);
        } catch (StoryTasksInProgressException | StoryNotReadyException e) {
            return StoryTasksResult.failure(e.getMessage());
        } catch (ModelOutputException | ModelUnavailableException e) {
            log.warn("Sub-tasks of {} not generated after the verdict: {}", storyKey, e.getClass().getSimpleName());
            return StoryTasksResult.failure(MODEL_FAILED);
        } catch (RuntimeException e) {
            log.error("Sub-tasks of {} not created after the verdict", storyKey, e);
            return StoryTasksResult.failure(CREATION_FAILED);
        }
    }
}
