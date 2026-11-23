// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import java.util.LinkedHashMap;
import fr.heliocraft.refinr.domain.model.Language;
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
 * {@code batchSize} stories : un appel par lot ({@code batchSize = 1} : un appel par story). Un lot dont la reponse est
 * coupee par la limite de tokens de sortie est coupe en deux (premiere moitie arrondie au superieur) et chaque moitie
 * est redemandee, jusqu'a la story seule ; une story seule encore coupee n'a pas de verdict. Les stories sans
 * verdict dans la reponse sont redemandees une fois, seules ; sinon pas de verdict (null) pour elles. Les lots sont
 * homogenes par langue de sortie : stories regroupees par langue (ordre de premiere apparition), puis decoupees en
 * lots de {@code batchSize} dans chaque langue ; les verdicts reviennent a leur position d'origine.
 */
public class LlmReadinessAssessor implements ReadinessAssessorPort {

    private static final Logger log = LoggerFactory.getLogger(LlmReadinessAssessor.class);

    private final LlmClient client;
    private final StructuredOutput output;
    private final int batchSize;
    private final JsonNode schema = LlmSchemas.load(LlmSchemas.READINESS);
    private final JsonNode batchSchema = LlmSchemas.load(LlmSchemas.READINESS_BATCH);

    /** Garde-fou : profondeur maximale du decoupage d'un lot tronque (un lot de 256 stories atteint la story seule). */
    static final int MAX_SPLIT_DEPTH = 8;

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
    public List<Readiness> assess(List<Story> stories, List<Language> languages) {
        if (languages.size() != stories.size()) {
            throw new IllegalArgumentException("one output language per story is required");
        }
        Readiness[] verdicts = new Readiness[stories.size()];
        Map<Language, List<Integer>> byLanguage = new LinkedHashMap<>();
        for (int i = 0; i < stories.size(); i++) {
            byLanguage.computeIfAbsent(languages.get(i), l -> new ArrayList<>()).add(i);
        }
        for (Map.Entry<Language, List<Integer>> group : byLanguage.entrySet()) {
            assessGroup(stories, group.getValue(), group.getKey(), verdicts);
        }
        return Arrays.asList(verdicts);
    }

    /** Stories d'une meme langue, par lots de {@code batchSize}. */
    private void assessGroup(List<Story> stories, List<Integer> indexes, Language language, Readiness[] verdicts) {
        for (int from = 0; from < indexes.size(); from += batchSize) {
            List<Integer> batch = indexes.subList(from, Math.min(from + batchSize, indexes.size()));
            List<Integer> missing = assessBatch(stories, batch, verdicts, language);
            if (!missing.isEmpty()) {
                log.warn("{} verdict(s) missing from the batch answer, asking again for them only", missing.size());
                missing = assessBatch(stories, missing, verdicts, language);
            }
            // Position seulement : le titre vient du modele ou de l'utilisateur
            missing.forEach(i -> log.warn("Readiness verdict unavailable for story {}/{}", i + 1, stories.size()));
        }
    }

    /** Juge les stories d'indices {@code indexes} en un appel ; renvoie les indices restes sans verdict. */
    private List<Integer> assessBatch(List<Story> stories, List<Integer> indexes, Readiness[] verdicts, Language language) {
        return assessBatch(stories, indexes, verdicts, language, 0);
    }

    private List<Integer> assessBatch(List<Story> stories, List<Integer> indexes, Readiness[] verdicts, Language language,
                                      int depth) {
        List<Story> batch = indexes.stream().map(stories::get).toList();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            ids.add("s" + (i + 1));
        }
        log.info("Assessing readiness of {} story(ies) with {}, output language {}", batch.size(),
                client.settings().describe(), language.code());
        Map<String, Readiness> byId;
        try {
            LlmRequest request = LlmRequest.of(LlmPrompts.system(language), LlmPrompts.verdicts(batch, ids, language),
                    LlmSchemas.READINESS_BATCH, LlmSchemas.forProvider(batchSchema));
            byId = output.call(client, request, batchSchema, LlmReadinessAssessor::verdictsById);
        } catch (LlmException e) {
            if (e.reason() == LlmException.Reason.TRUNCATED && indexes.size() > 1 && depth < MAX_SPLIT_DEPTH) {
                // Taille et cause seulement : jamais le contenu des stories ni de la reponse
                int half = (indexes.size() + 1) / 2;
                log.warn("Verdict batch of {} story(ies) truncated by the token limit, splitting into {} + {}",
                        indexes.size(), half, indexes.size() - half);
                List<Integer> missing = new ArrayList<>(assessBatch(stories, indexes.subList(0, half), verdicts, language, depth + 1));
                missing.addAll(assessBatch(stories, indexes.subList(half, indexes.size()), verdicts, language, depth + 1));
                return missing;
            }
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
    public Readiness reassess(String summary, String description, String precision, String previousQuestion,
                              Language language) {
        log.info("Reassessing a story with the user's precision ({}, output language {})", client.settings().describe(),
                language.code());
        LlmRequest request = LlmRequest.of(LlmPrompts.system(language),
                LlmPrompts.reassessment(summary, description, precision, previousQuestion, language),
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
