package fr.heliocraft.refinr.analyzer;

import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Conversion du backlog genere par le LLM vers le domaine (champ a champ, listes null conservees). */
final class GeneratedBacklogMapper {

    private GeneratedBacklogMapper() {}

    static List<Epic> toDomain(List<GeneratedEpic> epics) {
        return map(epics, GeneratedBacklogMapper::toDomain);
    }

    static Epic toDomain(GeneratedEpic dto) {
        if (dto == null) {
            return null;
        }
        Epic epic = new Epic();
        epic.setName(dto.getName());
        epic.setDescription(dto.getDescription());
        epic.setPriority(dto.getPriority());
        epic.setLabels(dto.getLabels());
        epic.setStories(map(dto.getStories(), GeneratedBacklogMapper::toDomain));
        epic.setTrackerKey(dto.getJiraKey());
        epic.setTrackerUrl(dto.getJiraUrl());
        return epic;
    }

    static Story toDomain(GeneratedStory dto) {
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
        story.setStoryPoints(dto.getStoryPoints());
        story.setEstimatedStoryPoints(dto.getEstimatedStoryPoints());
        story.setPriority(dto.getPriority());
        story.setLabels(dto.getLabels());
        story.setTasks(map(dto.getTasks(), GeneratedBacklogMapper::toDomain));
        story.setDependencies(dto.getDependencies());
        story.setAiReady(dto.getAiReady());
        story.setAiReadyReason(dto.getAiReadyReason());
        story.setTrackerKey(dto.getJiraKey());
        story.setTrackerUrl(dto.getJiraUrl());
        story.setEpicTrackerKey(dto.getEpicKey());
        return story;
    }

    static Task toDomain(GeneratedTask dto) {
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

    // Collectors.toList() (ArrayList modifiable, comme la liste produite par Jackson) ; null conserve
    private static <S, T> List<T> map(List<S> items, Function<S, T> mapper) {
        return items == null ? null : items.stream().map(mapper).collect(Collectors.toList());
    }
}
