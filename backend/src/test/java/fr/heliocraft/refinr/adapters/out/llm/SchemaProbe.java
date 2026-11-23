// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sonde du schema de generation chez Anthropic, pour le programme de mesure ({@code VerdictEval probe-schema}) : un
 * appel avec le corps de production ({@link AnthropicLlmClient#body}, donc le schema transforme par
 * {@link AnthropicLlmClient#supportedSchema}), un prompt d'une phrase et une sortie limitee a {@value #MAX_TOKENS}
 * tokens. N'expose que le statut HTTP, {@code error.message} d'une reponse d'erreur et la duree : ni la cle, ni la
 * reponse du modele. Hors des tests automatiques.
 */
public final class SchemaProbe {

    static final int MAX_TOKENS = 256;
    static final String PROMPT = "Ecris un backlog minimal pour : exporter les commandes du jour en CSV.";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Resultat de la sonde ; {@code response} (200 seulement) sert a relever l'usage facture, jamais affiche. */
    public record Result(int status, String errorMessage, long millis, JsonNode response) {}

    private SchemaProbe() {}

    /** Requete envoyee : celle d'une generation, avec le schema du backlog. */
    public static LlmRequest request() {
        return request(LlmSchemas.BACKLOG);
    }

    /** Schemas sondables : backlog (generation-v2), backlog-stories et story-tasks (chaine C1). */
    public static final java.util.List<String> SCHEMAS = java.util.List.of(LlmSchemas.BACKLOG, LlmSchemas.BACKLOG_STORIES,
            LlmSchemas.STORY_TASKS, LlmSchemas.STORY_TEST_CASES);

    /** Requete avec le schema {@code name} (un des {@link #SCHEMAS}). */
    public static LlmRequest request(String name) {
        if (!SCHEMAS.contains(name)) {
            throw new IllegalArgumentException("--schema must be one of " + SCHEMAS);
        }
        String prompt = name.equals(LlmSchemas.STORY_TASKS)
                ? "Ecris les sous-taches de la story s1 : exporter les commandes du jour en CSV."
                : name.equals(LlmSchemas.STORY_TEST_CASES)
                ? "Ecris les cas de test de la story s1 (AC1 : un fichier CSV est telecharge)." : PROMPT;
        return LlmRequest.of("Reponds uniquement par le JSON demande.", prompt, name,
                LlmSchemas.forProvider(LlmSchemas.load(name)));
    }

    public static Result probe(LlmSettings settings, LlmRequest request, Duration timeout) {
        if (settings.provider() != LlmProvider.ANTHROPIC) {
            throw new IllegalArgumentException("probe-schema targets Anthropic only");
        }
        AnthropicLlmClient client = new AnthropicLlmClient(settings);
        Map<String, Object> body = new LinkedHashMap<>(client.body(request));
        body.put("max_tokens", MAX_TOKENS);
        WebClient web = WebClient.builder().baseUrl(settings.baseUrl()).build();
        long start = System.nanoTime();
        Result result = web.post().uri(client.generatePath())
                .headers(headers -> client.headers().forEach(headers::set))
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("").map(text -> {
                    long millis = (System.nanoTime() - start) / 1_000_000;
                    int status = response.statusCode().value();
                    JsonNode json = parse(text);
                    if (status == 200) {
                        return new Result(status, null, millis, json);
                    }
                    String message = json != null && json.at("/error/message").isString()
                            ? json.at("/error/message").asString() : "(no error.message)";
                    return new Result(status, message, millis, null);
                }))
                .block(timeout);
        return result;
    }

    private static JsonNode parse(String text) {
        try {
            return text.isBlank() ? null : JSON.readTree(text);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
