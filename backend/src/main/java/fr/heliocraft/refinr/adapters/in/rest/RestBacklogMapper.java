// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.adapters.in.rest.dto.EpicDto;
import fr.heliocraft.refinr.adapters.in.rest.dto.StoryDto;
import fr.heliocraft.refinr.adapters.in.rest.dto.TaskDto;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Correspondance champ a champ entre le contrat JSON REST et le domaine. Les cles du gestionnaire de tickets
 * gardent leurs noms historiques cote JSON (jiraKey, epicKey, parentStoryKey) ; les listes null restent null.
 */
public final class RestBacklogMapper {

    private RestBacklogMapper() {}

    public static List<Epic> toDomain(List<EpicDto> epics) {
        return map(epics, RestBacklogMapper::toDomain);
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
        // aiReady / aiReadyReason du client ignores : le serveur recalcule le verdict sur le contenu final (create-jira)
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
        dto.setStories(map(epic.getStories(), RestBacklogMapper::toDto));
        dto.setJiraKey(epic.getTrackerKey());
        dto.setJiraUrl(epic.getTrackerUrl());
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
