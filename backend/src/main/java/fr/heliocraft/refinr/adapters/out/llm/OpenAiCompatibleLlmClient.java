// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API OpenAI Chat Completions et compatibles (OpenAI, DeepSeek, Mistral, Groq, OpenRouter, Ollama {@code /v1},
 * LiteLLM, vLLM). Mode JSON natif selon {@link JsonMode} ; {@code finish_reason: length} = troncature.
 * La {@code base-url} inclut la version de l'API (ex. {@code https://api.openai.com/v1}, {@code http://localhost:11434/v1}).
 */
public class OpenAiCompatibleLlmClient extends LlmClient {

    public OpenAiCompatibleLlmClient(LlmSettings settings) {
        super(settings);
    }

    @Override
    protected String generatePath() {
        return "/chat/completions";
    }

    @Override
    protected String healthPath() {
        return "/models";
    }

    /** La liste des modeles doit contenir le modele configure (ex. modele Ollama pas encore telecharge). */
    @Override
    protected String modelProblem(JsonNode response) {
        for (JsonNode model : response.path("data")) {
            if (settings.model().equals(model.path("id").asString(""))) {
                return null;
            }
        }
        return "model " + settings.model() + " not found";
    }

    @Override
    protected Map<String, String> headers() {
        // Cle facultative (Ollama local, vLLM sans authentification)
        return settings.apiKey().isBlank() ? Map.of() : Map.of("Authorization", "Bearer " + settings.apiKey());
    }

    @Override
    protected Map<String, Object> body(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.model());
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.system()));
        for (LlmRequest.Turn turn : request.messages()) {
            messages.add(Map.of("role", turn.role() == LlmRequest.Role.USER ? "user" : "assistant", "content", turn.text()));
        }
        body.put("messages", messages);
        if (settings.temperature() != null) {
            body.put("temperature", settings.temperature());
        }
        // max-tokens-param : max_tokens (defaut) | max_completion_tokens (modeles OpenAI a raisonnement) | none ;
        // max-output-tokens 0 : limite non envoyee non plus
        String limitField = settings.maxTokensParam().field();
        if (limitField != null && settings.maxOutputTokens() > 0) {
            body.put(limitField, settings.maxOutputTokens());
        }
        switch (settings.jsonMode()) {
            case JSON_SCHEMA -> {
                if (request.schema() != null) {
                    body.put("response_format", Map.of("type", "json_schema", "json_schema",
                            Map.of("name", request.schemaName(), "schema", request.schema(), "strict", false)));
                } else {
                    body.put("response_format", Map.of("type", "json_object"));
                }
            }
            case JSON_OBJECT -> body.put("response_format", Map.of("type", "json_object"));
            case NONE -> { }
        }
        body.putAll(settings.extraBody());
        return body;
    }

    @Override
    protected String text(JsonNode response) {
        JsonNode choice = response.path("choices").path(0);
        if (choice.isMissingNode()) {
            throw emptyOutput("no choice");
        }
        if ("length".equals(choice.path("finish_reason").asString(""))) {
            throw truncated();
        }
        String content = choice.path("message").path("content").asString("");
        if (content.isBlank()) {
            throw emptyOutput("empty content, finish_reason=" + choice.path("finish_reason").asString(""));
        }
        return content;
    }
}
