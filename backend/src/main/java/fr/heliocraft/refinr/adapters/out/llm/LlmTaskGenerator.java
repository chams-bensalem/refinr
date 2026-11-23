// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.application.port.out.TaskGeneratorPort;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sous-taches des stories pretes (chaine C1, prompt 4) par le fournisseur d'analyse, par lots de {@code batchSize}
 * stories : un appel par lot. Comme le verdict par lots : un lot coupe par la limite de tokens de sortie est coupe en
 * deux jusqu'a la story seule ; les stories absentes de la reponse sont redemandees une fois, seules ; sinon pas de
 * sous-taches (null) pour elles. Si aucun lot n'aboutit, la derniere erreur du fournisseur est levee.
 */
public class LlmTaskGenerator implements TaskGeneratorPort {

    private static final Logger log = LoggerFactory.getLogger(LlmTaskGenerator.class);

    /** Garde-fou du decoupage d'un lot tronque, comme pour le verdict. */
    static final int MAX_SPLIT_DEPTH = LlmReadinessAssessor.MAX_SPLIT_DEPTH;

    private final LlmClient client;
    private final StructuredOutput output;
    private final int batchSize;
    private final JsonNode schema = LlmSchemas.load(LlmSchemas.STORY_TASKS);

    public LlmTaskGenerator(LlmClient client, StructuredOutput output, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("task batch size must be >= 1");
        }
        this.client = client;
        this.output = output;
        this.batchSize = batchSize;
    }

    /** Etat d'un appel : sous-taches par position, derniere erreur, nombre de lots qui ont abouti. */
    private static final class Run {
        final List<Task>[] tasks;
        LlmException lastError;
        int answered;

        @SuppressWarnings("unchecked")
        Run(int size) {
            tasks = new List[size];
        }
    }

    @Override
    public List<List<Task>> generateTasks(List<Story> stories, List<String> precisions, Language language) {
        if (precisions.size() != stories.size()) {
            throw new IllegalArgumentException("one precision (or null) per story is required");
        }
        Run run = new Run(stories.size());
        for (int from = 0; from < stories.size(); from += batchSize) {
            List<Integer> batch = new ArrayList<>();
            for (int i = from; i < Math.min(from + batchSize, stories.size()); i++) {
                batch.add(i);
            }
            List<Integer> missing = generateBatch(stories, precisions, batch, language, 0, run);
            if (!missing.isEmpty()) {
                log.warn("{} story(ies) missing from the sub-task answer, asking again for them only", missing.size());
                missing = generateBatch(stories, precisions, missing, language, 0, run);
            }
            // Position seulement : le titre vient du modele ou de l'utilisateur
            missing.forEach(i -> log.warn("Sub-tasks unavailable for ready story {}/{}", i + 1, stories.size()));
        }
        if (run.answered == 0 && run.lastError != null) {
            throw run.lastError;
        }
        return Arrays.asList(run.tasks);
    }

    private List<Integer> generateBatch(List<Story> stories, List<String> precisions, List<Integer> indexes,
                                        Language language, int depth, Run run) {
        List<Story> batch = indexes.stream().map(stories::get).toList();
        List<String> batchPrecisions = indexes.stream().map(precisions::get).toList();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            ids.add("s" + (i + 1));
        }
        log.info("Generating sub-tasks of {} ready story(ies) with {}, output language {}", batch.size(),
                client.settings().describe(), language.code());
        Map<String, List<Task>> byId;
        try {
            LlmRequest request = LlmRequest.of(LlmPrompts.system(language),
                    LlmPrompts.tasks(batch, batchPrecisions, ids, language), LlmSchemas.STORY_TASKS,
                    LlmSchemas.forProvider(schema));
            byId = output.call(client, request, schema, LlmTaskGenerator::tasksById);
        } catch (LlmException e) {
            if (e.reason() == LlmException.Reason.TRUNCATED && indexes.size() > 1 && depth < MAX_SPLIT_DEPTH) {
                int half = (indexes.size() + 1) / 2;
                log.warn("Sub-task batch of {} story(ies) truncated by the token limit, splitting into {} + {}",
                        indexes.size(), half, indexes.size() - half);
                List<Integer> missing = new ArrayList<>(generateBatch(stories, precisions, indexes.subList(0, half),
                        language, depth + 1, run));
                missing.addAll(generateBatch(stories, precisions, indexes.subList(half, indexes.size()), language,
                        depth + 1, run));
                return missing;
            }
            log.warn("Sub-tasks unavailable for {} story(ies): {}", batch.size(), e.getMessage());
            run.lastError = e;
            return List.of();
        }
        run.answered++;
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < indexes.size(); i++) {
            List<Task> found = byId.get(ids.get(i));
            if (found != null) {
                run.tasks[indexes.get(i)] = found;
            } else {
                missing.add(indexes.get(i));
            }
        }
        return missing;
    }

    /** Sous-taches par identifiant ; un identifiant en double est une sortie invalide (relance avec l'erreur). */
    static Map<String, List<Task>> tasksById(JsonNode json) {
        Map<String, List<Task>> byId = new HashMap<>();
        for (JsonNode story : json.path("stories")) {
            String id = story.path("id").asString("");
            List<Task> tasks = new ArrayList<>();
            for (JsonNode task : story.path("tasks")) {
                GeneratedTask dto = new GeneratedTask();
                dto.setTitle(task.path("title").asString(""));
                dto.setDescription(task.path("description").isString() ? task.path("description").asString() : null);
                dto.setEstimatedHours(task.path("estimatedHours").isNumber() ? task.path("estimatedHours").asInt() : null);
                List<String> labels = new ArrayList<>();
                task.path("labels").forEach(label -> labels.add(label.asString("")));
                dto.setLabels(labels);
                tasks.add(GeneratedBacklogMapper.toDomain(dto));
            }
            if (byId.put(id, tasks) != null) {
                throw new IllegalArgumentException("duplicate id in stories: " + id + " (one entry per story)");
            }
        }
        return byId;
    }
}
