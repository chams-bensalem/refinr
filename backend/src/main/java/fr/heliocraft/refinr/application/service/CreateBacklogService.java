// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult.CreatedIssue;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult.FailedIssue;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.in.FeatureLevelMissingException;
import fr.heliocraft.refinr.application.port.out.IssueTrackerException;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort.FeatureLevel;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Feature;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Cree la hierarchie Epic -> Feature -> Story -> Subtask, dans l'ordre, et affecte a chaque element la cle de son
 * ticket. Selon le gestionnaire ({@link IssueTrackerPort#featureLevel}) :
 * <ul>
 *   <li>niveau Feature natif : un ticket par Feature explicite ; la Feature implicite (seule de son epic) n'a pas de
 *   ticket, sauf si le gestionnaire l'exige ({@code REQUIRED} : ticket au nom de l'epic) ;</li>
 *   <li>pas de niveau Feature : stories sous l'epic, label {@code feature-<nom>} pour une Feature explicite et un
 *   avertissement, ou refus avant tout appel au gestionnaire ({@link FeatureLevels.Policy#REFUSE}).</li>
 * </ul>
 * Un echec n'arrete pas la creation : l'element est signale en echec, ses enfants ne sont pas crees (signales
 * aussi), et les elements suivants sont crees normalement.
 */
public class CreateBacklogService implements CreateBacklog {

    private static final Logger log = LoggerFactory.getLogger(CreateBacklogService.class);

    static final String EPIC = "epic";
    static final String FEATURE = "feature";
    static final String STORY = "story";
    static final String SUBTASK = "subtask";

    private final IssueTrackerPort issueTracker;
    private final FeatureLevels.Policy policy;
    // Partages avec story-tasks : une story creee ici garde son verrou jusqu'a la fin de ses sous-taches
    private final StoryLocks locks;

    public CreateBacklogService(IssueTrackerPort issueTracker) {
        this(issueTracker, FeatureLevels.Policy.LABEL);
    }

    public CreateBacklogService(IssueTrackerPort issueTracker, FeatureLevels.Policy policy) {
        this(issueTracker, policy, new StoryLocks());
    }

    public CreateBacklogService(IssueTrackerPort issueTracker, FeatureLevels.Policy policy, StoryLocks locks) {
        this.issueTracker = issueTracker;
        this.policy = policy;
        this.locks = locks;
    }

    @Override
    public BacklogCreationResult create(List<Epic> epics, String project) {
        log.info("Creating full hierarchy: {} epics in project {}", epics.size(),
                project == null || project.isBlank() ? "(default)" : project);
        FeatureLevel level = FeatureLevels.levelOf(issueTracker, project);
        // Refus seulement quand l'absence de niveau Feature est connue ; un niveau inconnu (gestionnaire injoignable
        // ou mal configure) ne donne jamais un 422 trompeur : la creation de l'epic echoue alors avec la vraie cause
        if (level == FeatureLevel.NONE && policy == FeatureLevels.Policy.REFUSE && FeatureLevels.hasExplicitFeatures(epics)) {
            log.warn("Creation refused: the tracker has no Feature level and refinr.features.without-level=refuse");
            throw new FeatureLevelMissingException();
        }
        // Niveau inconnu : relu une fois apres la premiere creation reussie (le projet est alors resolu), sinon absent
        boolean recheck = level == FeatureLevel.UNKNOWN;
        FeatureLevel effective = level == FeatureLevel.UNKNOWN ? FeatureLevel.NONE : level;
        List<CreatedIssue> created = new ArrayList<>();
        List<FailedIssue> failed = new ArrayList<>();

        int epicIndex = 0;
        for (Epic epic : epics) {
            String epicPosition = String.valueOf(++epicIndex);
            String epicKey = attempt(EPIC, epicPosition, epic.getName(), () -> issueTracker.createEpic(epic, project),
                    created, failed);
            if (epicKey == null) {
                skipFeatures(epic, effective, "Not created: parent epic " + epic.getName() + " failed", failed);
                continue;
            }
            epic.setTrackerKey(epicKey);
            if (recheck) {
                recheck = false;
                FeatureLevel known = FeatureLevels.levelOf(issueTracker, project);
                if (known.isNative()) {
                    effective = known;
                }
            }
            FeatureLevel epicLevel = effective;

            int featureIndex = 0;
            int storyIndex = 0;
            for (Feature feature : nonNull(epic.getFeatures())) {
                if (feature == null) {
                    continue;
                }
                String featurePosition = epicPosition + "." + ++featureIndex;
                boolean implicit = epic.isImplicit(feature);
                String parentKey = epicKey;
                String featureLabel = null;
                if (hasTicket(epicLevel, implicit)) {
                    String featureKey = attempt(FEATURE, featurePosition, featureName(epic, feature),
                            () -> issueTracker.createFeature(named(epic, feature), epicKey, project), created, failed);
                    if (featureKey == null) {
                        skipStories(feature.getStories(), "Not created: parent feature " + featureName(epic, feature)
                                + " failed", failed);
                        storyIndex += nonNull(feature.getStories()).size();
                        continue;
                    }
                    feature.setTrackerKey(featureKey);
                    feature.setTrackerUrl(issueTracker.issueUrl(featureKey));
                    parentKey = featureKey;
                } else if (!epicLevel.isNative() && !implicit) {
                    featureLabel = FeatureLevels.label(feature);
                }

                for (Story story : nonNull(feature.getStories())) {
                    String storyPosition = epicPosition + "." + ++storyIndex;
                    if (story == null) {
                        continue;
                    }
                    if (featureLabel != null) {
                        addLabel(story, featureLabel);
                    }
                    String storyParent = parentKey;
                    String storyKey = attempt(STORY, storyPosition, story.getTitle(),
                            () -> issueTracker.createStory(story, storyParent, project), created, failed);
                    if (storyKey == null) {
                        skipTasks(story.getTasks(), "Not created: parent story " + story.getTitle() + " failed", failed);
                        continue;
                    }
                    story.setTrackerKey(storyKey);
                    story.setEpicTrackerKey(epicKey);

                    // La story existe deja (label ai-ready compris) : story-tasks attend la fin de ses sous-taches
                    boolean locked = locks.tryLock(storyKey);
                    try {
                        int taskIndex = 0;
                        for (Task task : nonNull(story.getTasks())) {
                            String taskPosition = storyPosition + "." + ++taskIndex;
                            String taskKey = attempt(SUBTASK, taskPosition, task.getTitle(),
                                    () -> issueTracker.createSubtask(task, storyKey, project), created, failed);
                            if (taskKey != null) {
                                task.setTrackerKey(taskKey);
                                task.setParentTrackerKey(storyKey);
                            }
                        }
                    } finally {
                        if (locked) {
                            locks.unlock(storyKey);
                        }
                    }
                }
            }
        }

        if (failed.isEmpty()) {
            log.info("Full hierarchy created successfully ({} issues)", created.size());
        } else {
            log.warn("Hierarchy partially created: {} created, {} not created", created.size(), failed.size());
        }
        // Avertissement de ce qui a ete fait (labels), jamais celui du refus
        List<String> warnings = FeatureLevels.warnings(epics, effective, FeatureLevels.Policy.LABEL);
        return new BacklogCreationResult(epics, created, failed, warnings);
    }

    /** Ticket de Feature : niveau natif et Feature explicite, ou Feature implicite quand le niveau est obligatoire. */
    private static boolean hasTicket(FeatureLevel level, boolean implicit) {
        return level == FeatureLevel.REQUIRED || (level == FeatureLevel.OPTIONAL && !implicit);
    }

    /** Nom de la Feature, ou celui de l'epic pour une Feature sans nom (Feature implicite de l'ancienne forme). */
    private static String featureName(Epic epic, Feature feature) {
        return feature.getName() != null && !feature.getName().isBlank() ? feature.getName() : epic.getName();
    }

    /** La Feature envoyee au gestionnaire porte toujours un nom (celui de l'epic a defaut). */
    private static Feature named(Epic epic, Feature feature) {
        if (feature.getName() == null || feature.getName().isBlank()) {
            feature.setName(epic.getName());
        }
        return feature;
    }

    private static void addLabel(Story story, String label) {
        List<String> labels = new ArrayList<>(nonNull(story.getLabels()));
        if (!labels.contains(label)) {
            labels.add(label);
        }
        story.setLabels(labels);
    }

    /**
     * Cle du ticket cree, ou null si la creation a echoue (echec consigne). Les logs citent la position dans le
     * backlog ({@code 2.1.3}), jamais le titre (sortie du modele ou texte de l'utilisateur).
     */
    private String attempt(String type, String position, String title, Supplier<String> creation,
                           List<CreatedIssue> created, List<FailedIssue> failed) {
        try {
            String key = creation.get();
            created.add(new CreatedIssue(type, title, key, issueTracker.issueUrl(key)));
            return key;
        } catch (IssueTrackerException e) {
            // Message sur dans les logs ; le client recoit en plus les erreurs de validation du gestionnaire
            log.error("Failed to create {} {}: {}", type, position, e.getMessage(), e);
            failed.add(new FailedIssue(type, title, e.clientMessage()));
        } catch (RuntimeException e) {
            // Message brut jamais renvoye au client : il reste dans les logs
            log.error("Unexpected error creating {} {}", type, position, e);
            failed.add(new FailedIssue(type, title, "Unexpected error"));
        }
        return null;
    }

    /** Epic en echec : ses Features (si elles auraient eu un ticket), stories et sous-taches sont signalees. */
    private static void skipFeatures(Epic epic, FeatureLevel level, String reason, List<FailedIssue> failed) {
        for (Feature feature : nonNull(epic.getFeatures())) {
            if (feature == null) {
                continue;
            }
            if (hasTicket(level, epic.isImplicit(feature))) {
                failed.add(new FailedIssue(FEATURE, featureName(epic, feature), reason));
            }
            skipStories(feature.getStories(), reason, failed);
        }
    }

    private static void skipStories(List<Story> stories, String reason, List<FailedIssue> failed) {
        for (Story story : nonNull(stories)) {
            if (story == null) {
                continue;
            }
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
