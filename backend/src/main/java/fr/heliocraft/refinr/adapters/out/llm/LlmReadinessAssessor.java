// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.service.ReadinessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verdict de preparation par le fournisseur de verdict (ou d'analyse s'il n'y en a pas de distinct), par lots de
 * {@code batchSize} stories : un appel par lot ({@code batchSize = 1} : un appel par story). Les stories sans
 * verdict dans la reponse sont redemandees une fois, seules ; sinon pas de verdict (null) pour elles.
 */
public class LlmReadinessAssessor implements ReadinessAssessorPort {

    private static final Logger log = LoggerFactory.getLogger(LlmReadinessAssessor.class);

    private final LlmClient client;
    private final StructuredOutput output;
    private final int batchSize;
    private final JsonNode schema = LlmSchemas.load(LlmSchemas.READINESS);
    private final JsonNode batchSchema = LlmSchemas.load(LlmSchemas.READINESS_BATCH);

    public LlmReadinessAssessor(LlmClient client, StructuredOutput output, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("verdict batch-size must be >= 1");
        }
        this.client = client;
        this.output = output;
        this.batchSize = batchSize;
    }

    /** Fournisseur, modele et version du prompt : ce qui, avec le contenu de la story, determine un verdict. */
    public String fingerprint() {
        LlmSettings settings = client.settings();
        return settings.provider().id() + "|" + settings.baseUrl() + "|" + settings.model() + "|" + LlmPrompts.VERDICT_PROMPT_VERSION;
    }

    @Override
    public List<Readiness> assess(List<Story> stories) {
        Readiness[] verdicts = new Readiness[stories.size()];
        for (int from = 0; from < stories.size(); from += batchSize) {
            List<Integer> batch = new ArrayList<>();
            for (int i = from; i < Math.min(from + batchSize, stories.size()); i++) {
                batch.add(i);
            }
            List<Integer> missing = assessBatch(stories, batch, verdicts);
            if (!missing.isEmpty()) {
                log.warn("{} verdict(s) missing from the batch answer, asking again for them only", missing.size());
                missing = assessBatch(stories, missing, verdicts);
            }
            missing.forEach(i -> log.warn("Readiness verdict unavailable for '{}'", stories.get(i).getTitle()));
        }
        return Arrays.asList(verdicts);
    }

    /** Juge les stories d'indices {@code indexes} en un appel ; renvoie les indices restes sans verdict. */
    private List<Integer> assessBatch(List<Story> stories, List<Integer> indexes, Readiness[] verdicts) {
        List<Story> batch = indexes.stream().map(stories::get).toList();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            ids.add("s" + (i + 1));
        }
        log.info("Assessing readiness of {} story(ies) with {}", batch.size(), client.settings().describe());
        Map<String, Readiness> byId;
        try {
            LlmRequest request = LlmRequest.of(LlmPrompts.SYSTEM, LlmPrompts.verdicts(batch, ids),
                    LlmSchemas.READINESS_BATCH, LlmSchemas.forProvider(batchSchema));
            byId = output.call(client, request, batchSchema, LlmReadinessAssessor::verdictsById);
        } catch (LlmException e) {
            log.warn("Readiness verdicts unavailable for {} story(ies): {}", batch.size(), e.getMessage());
            return List.of();
        }
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < indexes.size(); i++) {
            Readiness verdict = byId.get(ids.get(i));
            if (verdict != null) {
                verdicts[indexes.get(i)] = verdict;
            } else {
                missing.add(indexes.get(i));
            }
        }
        return missing;
    }

    @Override
    public Readiness reassess(String summary, String description, String precision) {
        log.info("Reassessing story '{}' with the user's precision ({})", summary, client.settings().describe());
        LlmRequest request = LlmRequest.of(LlmPrompts.SYSTEM, LlmPrompts.reassessment(summary, description, precision),
                LlmSchemas.READINESS, LlmSchemas.forProvider(schema));
        return output.call(client, request, schema, LlmReadinessAssessor::readiness);
    }

    /** Verdicts par identifiant ; un identifiant en double est une sortie invalide (relance avec l'erreur). */
    static Map<String, Readiness> verdictsById(JsonNode json) {
        Map<String, Readiness> byId = new HashMap<>();
        for (JsonNode verdict : json.path("verdicts")) {
            String id = verdict.path("id").asString("");
            if (byId.put(id, readiness(verdict)) != null) {
                throw new IllegalArgumentException("identifiant en double dans verdicts : " + id
                        + " (un seul verdict par story)");
            }
        }
        return byId;
    }

    /** Seul un true explicite rend la story codable : "yes", absent ou null -> validation humaine. */
    static Readiness readiness(JsonNode json) {
        if (!json.isObject()) {
            throw new IllegalArgumentException("verdict is not a JSON object");
        }
        JsonNode ready = json.get("aiReady");
        JsonNode reason = json.get("aiReadyReason");
        Object answer = ready == null || ready.isNull() ? null : ready.isBoolean() ? ready.booleanValue() : ready.asString();
        return ReadinessPolicy.fromModelAnswer(answer, reason == null || reason.isNull() ? null : reason.asString());
    }
}
