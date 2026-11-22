// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.adapters.in.rest.dto.EpicDto;
import fr.heliocraft.refinr.adapters.in.rest.dto.FeatureDto;
import fr.heliocraft.refinr.adapters.in.rest.dto.StoryDto;
import fr.heliocraft.refinr.adapters.in.rest.dto.TaskDto;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Feature;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Correspondance champ a champ entre le contrat JSON REST et le domaine. Les cles du gestionnaire de tickets
 * gardent leurs noms historiques cote JSON (jiraKey, epicKey, parentStoryKey) ; les listes null restent null.
 */
public final class RestBacklogMapper {

    private RestBacklogMapper() {}

    /** Avertissement : forme d'avant (stories) modifiee par le client, Features ignorees. */
    static final String LEGACY_STORIES_WIN = "The stories were edited in the former shape (epics[].stories): their "
            + "features were ignored and the stories kept under their epic";

    public static List<Epic> toDomain(List<EpicDto> epics) {
        return toDomain(epics, new ArrayList<>());
    }

    /**
     * Requete d'un client : {@code features} si present ; {@code stories} seul (client d'avant la 1.5) : une Feature
     * implicite ; les deux : {@code features} s'il contient les memes stories que {@code stories} (apercu renvoye tel
     * quel), sinon {@code stories} l'emporte (le client a modifie l'ancienne forme) et un avertissement est ajoute.
     */
    public static List<Epic> toDomain(List<EpicDto> epics, List<String> warnings) {
        if (epics == null) {
            return null;
        }
        List<Epic> result = new ArrayList<>();
        boolean legacyWon = false;
        for (EpicDto dto : epics) {
            Epic epic = toDomain(dto);
            if (dto != null && hasFeatures(dto)) {
                List<StoryDto> flat = flatten(dto.getFeatures());
                if (dto.getStories() == null || dto.getStories().isEmpty() || sameStories(flat, dto.getStories())) {
                    epic.setFeatures(map(dto.getFeatures(), RestBacklogMapper::toDomain));
                } else {
                    legacyWon = true;
                }
            }
            result.add(epic);
        }
        if (legacyWon) {
            warnings.add(LEGACY_STORIES_WIN);
        }
        return result;
    }

    private static boolean hasFeatures(EpicDto dto) {
        return dto.getFeatures() != null && !dto.getFeatures().isEmpty();
    }

    private static List<StoryDto> flatten(List<FeatureDto> features) {
        List<StoryDto> stories = new ArrayList<>();
        for (FeatureDto feature : features) {
            if (feature != null && feature.getStories() != null) {
                stories.addAll(feature.getStories());
            }
        }
        return stories;
    }

    /** Memes stories, champ a champ (contenu modifiable par le client ; verdict et cles compris). */
    private static boolean sameStories(List<StoryDto> a, List<StoryDto> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!sameStory(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameStory(StoryDto a, StoryDto b) {
        if (a == null || b == null) {
            return a == b;
        }
        return Objects.equals(a.getTitle(), b.getTitle()) && Objects.equals(a.getDescription(), b.getDescription())
                && Objects.equals(a.getAsA(), b.getAsA()) && Objects.equals(a.getIWant(), b.getIWant())
                && Objects.equals(a.getSoThat(), b.getSoThat())
                && Objects.equals(a.getAcceptanceCriteria(), b.getAcceptanceCriteria())
                && Objects.equals(a.getEstimatedStoryPoints(), b.getEstimatedStoryPoints())
                && Objects.equals(a.getPriority(), b.getPriority()) && Objects.equals(a.getLabels(), b.getLabels())
                && Objects.equals(a.getDependencies(), b.getDependencies())
                && Objects.equals(a.getJiraKey(), b.getJiraKey()) && sameTasks(a.getTasks(), b.getTasks());
    }

    private static boolean sameTasks(List<TaskDto> a, List<TaskDto> b) {
        List<TaskDto> x = a == null ? List.of() : a;
        List<TaskDto> y = b == null ? List.of() : b;
        if (x.size() != y.size()) {
            return false;
        }
        for (int i = 0; i < x.size(); i++) {
            TaskDto t = x.get(i), u = y.get(i);
            if (t == null || u == null ? t != u : !(Objects.equals(t.getTitle(), u.getTitle())
                    && Objects.equals(t.getDescription(), u.getDescription())
                    && Objects.equals(t.getEstimatedHours(), u.getEstimatedHours())
                    && Objects.equals(t.getLabels(), u.getLabels()))) {
                return false;
            }
        }
        return true;
    }

    public static List<EpicDto> toDto(List<Epic> epics) {
        return map(epics, RestBacklogMapper::toDto);
    }

    static Epic toDomain(EpicDto dto) {
        if (dto == null) {
            return null;
        }
        Epic epic = new Epic();
        epic.setName(dto.getName());
        epic.setDescription(dto.getDescription());
        epic.setPriority(dto.getPriority());
        epic.setLabels(dto.getLabels());
        epic.setStories(map(dto.getStories(), RestBacklogMapper::toDomain));
        epic.setTrackerKey(dto.getJiraKey());
        epic.setTrackerUrl(dto.getJiraUrl());
        return epic;
    }

    static Feature toDomain(FeatureDto dto) {
        if (dto == null) {
            return null;
        }
        Feature feature = new Feature();
        feature.setName(dto.getName());
        feature.setDescription(dto.getDescription());
        feature.setPriority(dto.getPriority());
        feature.setLabels(dto.getLabels());
        feature.setStories(map(dto.getStories(), RestBacklogMapper::toDomain));
        feature.setTrackerKey(dto.getJiraKey());
        feature.setTrackerUrl(dto.getJiraUrl());
        return feature;
    }

    static Story toDomain(StoryDto dto) {
        if (dto == null) {
            return null;
        }
        Story story = new Story();
        story.setAsA(dto.getAsA());
        story.setIWant(dto.getIWant());
        story.setSoThat(dto.getSoThat());
        story.setTitle(dto.getTitle());
        story.setDescription(dto.getDescription());
        story.setAcceptanceCriteria(dto.getAcceptanceCriteria());
        story.setEstimatedStoryPoints(dto.getEstimatedStoryPoints());
        story.setPriority(dto.getPriority());
        story.setLabels(dto.getLabels());
        story.setTasks(map(dto.getTasks(), RestBacklogMapper::toDomain));
        story.setDependencies(dto.getDependencies());
        // aiReady / aiReadyReason / aiReadySource du client ignores : le serveur recalcule le verdict sur le contenu final (create-jira)
        story.setTrackerKey(dto.getJiraKey());
        story.setTrackerUrl(dto.getJiraUrl());
        story.setEpicTrackerKey(dto.getEpicKey());
        return story;
    }

    static Task toDomain(TaskDto dto) {
        if (dto == null) {
            return null;
        }
        Task task = new Task();
        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        task.setEstimatedHours(dto.getEstimatedHours());
        task.setLabels(dto.getLabels());
        task.setTrackerKey(dto.getJiraKey());
        task.setTrackerUrl(dto.getJiraUrl());
        task.setParentTrackerKey(dto.getParentStoryKey());
        return task;
    }

    static EpicDto toDto(Epic epic) {
        if (epic == null) {
            return null;
        }
        EpicDto dto = new EpicDto();
        dto.setName(epic.getName());
        dto.setDescription(epic.getDescription());
        dto.setPriority(epic.getPriority());
        dto.setLabels(epic.getLabels());
        List<FeatureDto> features = new ArrayList<>();
        for (Feature feature : epic.getFeatures() == null ? List.<Feature>of() : epic.getFeatures()) {
            if (feature != null) {
                FeatureDto featureDto = toDto(feature, epic.isImplicit(feature));
                // Feature implicite sans nom propre (forme d'avant) : le nom de son epic
                if (featureDto.getName() == null || featureDto.getName().isBlank()) {
                    featureDto.setName(epic.getName());
                }
                features.add(featureDto);
            }
        }
        dto.setFeatures(features);
        // Forme d'avant, gardee : toutes les stories de l'epic, a plat
        dto.setStories(map(epic.getStories(), RestBacklogMapper::toDto));
        dto.setJiraKey(epic.getTrackerKey());
        dto.setJiraUrl(epic.getTrackerUrl());
        return dto;
    }

    static FeatureDto toDto(Feature feature, boolean implicit) {
        FeatureDto dto = new FeatureDto();
        dto.setName(feature.getName());
        dto.setDescription(feature.getDescription());
        dto.setPriority(feature.getPriority());
        dto.setLabels(feature.getLabels());
        dto.setImplicit(implicit);
        dto.setStories(map(feature.getStories(), RestBacklogMapper::toDto));
        dto.setJiraKey(feature.getTrackerKey());
        dto.setJiraUrl(feature.getTrackerUrl());
        return dto;
    }

    static StoryDto toDto(Story story) {
        if (story == null) {
            return null;
        }
        StoryDto dto = new StoryDto();
        dto.setAsA(story.getAsA());
        dto.setIWant(story.getIWant());
        dto.setSoThat(story.getSoThat());
        dto.setTitle(story.getTitle());
        dto.setDescription(story.getDescription());
        dto.setAcceptanceCriteria(story.getAcceptanceCriteria());
        dto.setEstimatedStoryPoints(story.getEstimatedStoryPoints());
        dto.setPriority(story.getPriority());
        dto.setLabels(story.getLabels());
        dto.setTasks(map(story.getTasks(), RestBacklogMapper::toDto));
        dto.setDependencies(story.getDependencies());
        dto.setAiReady(story.getAiReady());
        dto.setAiReadyReason(story.getAiReadyReason());
        dto.setAiReadySource(story.getAiReadySource() != null ? story.getAiReadySource().id() : null);
        dto.setJiraKey(story.getTrackerKey());
        dto.setJiraUrl(story.getTrackerUrl());
        dto.setEpicKey(story.getEpicTrackerKey());
        return dto;
    }

    static TaskDto toDto(Task task) {
        if (task == null) {
            return null;
        }
        TaskDto dto = new TaskDto();
        dto.setTitle(task.getTitle());
        dto.setDescription(task.getDescription());
        dto.setEstimatedHours(task.getEstimatedHours());
        dto.setLabels(task.getLabels());
        dto.setJiraKey(task.getTrackerKey());
        dto.setJiraUrl(task.getTrackerUrl());
        dto.setParentStoryKey(task.getParentTrackerKey());
        return dto;
    }

    // Collectors.toList() (ArrayList modifiable, comme la liste produite par Jackson) ; null conserve
    private static <S, T> List<T> map(List<S> items, Function<S, T> mapper) {
        return items == null ? null : items.stream().map(mapper).collect(Collectors.toList());
    }
}
