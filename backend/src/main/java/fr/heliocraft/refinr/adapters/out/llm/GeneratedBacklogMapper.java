// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.ReadinessSource;
import fr.heliocraft.refinr.domain.service.ReadinessPolicy;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Feature;
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
        if (dto.getFeatures() != null && !dto.getFeatures().isEmpty()) {
            epic.setFeatures(map(dto.getFeatures(), GeneratedBacklogMapper::toDomain));
        } else {
            // Epic sans features (ancien modele, modele peu discipline) : une Feature implicite plutot qu'un refus
            epic.setStories(map(dto.getStories(), GeneratedBacklogMapper::toDomain));
        }
        return epic;
    }

    static Feature toDomain(GeneratedFeature dto) {
        if (dto == null) {
            return null;
        }
        Feature feature = new Feature();
        feature.setName(dto.getName());
        feature.setDescription(dto.getDescription());
        feature.setPriority(dto.getPriority());
        feature.setLabels(dto.getLabels());
        feature.setStories(map(dto.getStories(), GeneratedBacklogMapper::toDomain));
        return feature;
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
        story.setEstimatedStoryPoints(dto.getEstimatedStoryPoints());
        story.setPriority(dto.getPriority());
        story.setLabels(dto.getLabels());
        story.setTasks(map(dto.getTasks(), GeneratedBacklogMapper::toDomain));
        story.setDependencies(dto.getDependencies());
        if (dto.getAiReady() == null) {
            // Modele qui ne respecte pas le schema (aiReady absent) : verdict de repli, jamais ai-ready
            Readiness fallback = ReadinessPolicy.unavailable("no verdict");
            story.setAiReady(fallback.aiReady());
            story.setAiReadyReason(fallback.aiReadyReason());
            story.setAiReadySource(fallback.source());
        } else {
            // Verdict de la generation (sans fournisseur de verdict distinct) : il vient du modele
            story.setAiReady(dto.getAiReady());
            story.setAiReadyReason(dto.getAiReadyReason());
            story.setAiReadySource(ReadinessSource.MODEL);
        }
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
        return task;
    }

    // Collectors.toList() (ArrayList modifiable, comme la liste produite par Jackson) ; null conserve
    private static <S, T> List<T> map(List<S> items, Function<S, T> mapper) {
        return items == null ? null : items.stream().map(mapper).collect(Collectors.toList());
    }
}
