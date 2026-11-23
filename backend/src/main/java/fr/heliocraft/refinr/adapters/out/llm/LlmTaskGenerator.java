// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.TaskGeneratorPort;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sous-taches des stories pretes (chaine C1, prompt 4) par le fournisseur d'analyse, par lots de stories
 * ({@link LlmStoryBatches} : decoupage d'un lot tronque, stories absentes redemandees, null sinon).
 */
public class LlmTaskGenerator extends LlmStoryBatches<List<Task>> implements TaskGeneratorPort {

    private static final Logger log = LoggerFactory.getLogger(LlmTaskGenerator.class);

    public LlmTaskGenerator(LlmClient client, StructuredOutput output, int batchSize) {
        super(client, output, batchSize, LlmSchemas.STORY_TASKS);
    }

    private LlmTaskGenerator(LlmClient client, StructuredOutput output, int batchSize, String context) {
        super(client, output, batchSize, LlmSchemas.STORY_TASKS, context);
    }

    @Override
    public LlmTaskGenerator withContext(String context) {
        return new LlmTaskGenerator(client(), output(), batchSize(), context);
    }

    @Override
    public List<List<Task>> generateTasks(List<Story> stories, List<String> precisions, Language language) {
        if (precisions.size() != stories.size()) {
            throw new IllegalArgumentException("one precision (or null) per story is required");
        }
        return run(stories, language, (indexes, ids, lang) -> LlmPrompts.tasks(indexes.stream().map(stories::get).toList(),
                indexes.stream().map(precisions::get).toList(), ids, lang, context()));
    }

    @Override
    Map<String, List<Task>> parse(JsonNode json) {
        return tasksById(json);
    }

    @Override
    String what() {
        return "sub-tasks";
    }

    @Override
    Logger log() {
        return log;
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
