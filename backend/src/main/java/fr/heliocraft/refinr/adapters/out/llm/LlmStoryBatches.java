// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Story;
import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Appels par lots de stories pretes (prompts 4 et 5) : un appel par lot de {@code batchSize} stories, reponse appariee
 * par identifiant. Un lot coupe par la limite de tokens de sortie est coupe en deux jusqu'a la story seule ; les
 * stories absentes de la reponse sont redemandees une fois, seules ; sinon {@code null} pour elles. Si aucun lot
 * n'aboutit, la derniere erreur du fournisseur est levee.
 *
 * @param <T> resultat par story (sous-taches, cas de test)
 */
abstract class LlmStoryBatches<T> {

    static final int MAX_SPLIT_DEPTH = LlmReadinessAssessor.MAX_SPLIT_DEPTH;

    private final LlmClient client;
    private final StructuredOutput output;
    private final int batchSize;
    private final JsonNode schema;
    private final String schemaName;

    LlmStoryBatches(LlmClient client, StructuredOutput output, int batchSize, String schemaName) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batch size must be >= 1");
        }
        this.client = client;
        this.output = output;
        this.batchSize = batchSize;
        this.schemaName = schemaName;
        this.schema = LlmSchemas.load(schemaName);
    }

    /** Texte du prompt d'un lot (stories, leur position d'origine et leurs identifiants). */
    abstract String prompt(List<Integer> indexes, List<String> ids, Language language);

    /** Resultat par identifiant ; une exception = sortie invalide (relance avec l'erreur). */
    abstract Map<String, T> parse(JsonNode json);

    /** Nom de ce qui est produit, pour les logs (jamais le contenu). */
    abstract String what();

    abstract Logger log();

    LlmClient client() {
        return client;
    }

    JsonNode schema() {
        return schema;
    }

    String schemaName() {
        return schemaName;
    }

    private final class Run {
        final Object[] results;
        LlmException lastError;
        int answered;

        Run(int size) {
            results = new Object[size];
        }
    }

    @SuppressWarnings("unchecked")
    final List<T> run(List<Story> stories, Language language) {
        Run run = new Run(stories.size());
        for (int from = 0; from < stories.size(); from += batchSize) {
            List<Integer> batch = new ArrayList<>();
            for (int i = from; i < Math.min(from + batchSize, stories.size()); i++) {
                batch.add(i);
            }
            List<Integer> missing = batch(batch, language, 0, run);
            if (!missing.isEmpty()) {
                log().warn("{} story(ies) missing from the {} answer, asking again for them only", missing.size(), what());
                missing = batch(missing, language, 0, run);
            }
            // Position seulement : le titre vient du modele ou de l'utilisateur
            missing.forEach(i -> log().warn("{} unavailable for ready story {}/{}", what(), i + 1, stories.size()));
        }
        if (run.answered == 0 && run.lastError != null) {
            throw run.lastError;
        }
        return (List<T>) Arrays.asList(run.results);
    }

    private List<Integer> batch(List<Integer> indexes, Language language, int depth, Run run) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < indexes.size(); i++) {
            ids.add("s" + (i + 1));
        }
        log().info("Generating {} of {} ready story(ies) with {}, output language {}", what(), indexes.size(),
                client.settings().describe(), language.code());
        Map<String, T> byId;
        try {
            LlmRequest request = LlmRequest.of(LlmPrompts.system(language), prompt(indexes, ids, language), schemaName,
                    LlmSchemas.forProvider(schema));
            byId = output.call(client, request, schema, this::parse);
        } catch (LlmException e) {
            if (e.reason() == LlmException.Reason.TRUNCATED && indexes.size() > 1 && depth < MAX_SPLIT_DEPTH) {
                int half = (indexes.size() + 1) / 2;
                log().warn("Batch of {} story(ies) truncated by the token limit ({}), splitting into {} + {}",
                        indexes.size(), what(), half, indexes.size() - half);
                List<Integer> missing = new ArrayList<>(batch(indexes.subList(0, half), language, depth + 1, run));
                missing.addAll(batch(indexes.subList(half, indexes.size()), language, depth + 1, run));
                return missing;
            }
            log().warn("{} unavailable for {} story(ies): {}", what(), indexes.size(), e.getMessage());
            run.lastError = e;
            return List.of();
        }
        run.answered++;
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < indexes.size(); i++) {
            T found = byId.get(ids.get(i));
            if (found != null) {
                run.results[indexes.get(i)] = found;
            } else {
                missing.add(indexes.get(i));
            }
        }
        return missing;
    }
}
