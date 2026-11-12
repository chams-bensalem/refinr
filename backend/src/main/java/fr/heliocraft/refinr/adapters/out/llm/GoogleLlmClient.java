package fr.heliocraft.refinr.adapters.out.llm;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API Gemini (Google AI Studio) : sortie JSON native ({@code responseMimeType} + {@code responseJsonSchema}),
 * cle dans l'en-tete {@code x-goog-api-key} (jamais dans l'URL) ; {@code finishReason: MAX_TOKENS} = troncature.
 */
public class GoogleLlmClient extends LlmClient {

    public GoogleLlmClient(LlmSettings settings) {
        super(settings);
    }

    @Override
    protected String generatePath() {
        return "/v1beta/models/" + settings.model() + ":generateContent";
    }

    @Override
    protected String healthPath() {
        return "/v1beta/models/" + settings.model();
    }

    @Override
    protected Map<String, String> headers() {
        return Map.of("x-goog-api-key", settings.apiKey());
    }

    @Override
    protected Map<String, Object> body(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", request.system()))));
        List<Map<String, Object>> contents = new ArrayList<>();
        for (LlmRequest.Turn turn : request.messages()) {
            contents.add(Map.of(
                    "role", turn.role() == LlmRequest.Role.USER ? "user" : "model",
                    "parts", List.of(Map.of("text", turn.text()))));
        }
        body.put("contents", contents);
        Map<String, Object> generation = new LinkedHashMap<>();
        if (settings.temperature() != null) {
            generation.put("temperature", settings.temperature());
        }
        generation.put("maxOutputTokens", settings.maxOutputTokens());
        generation.put("responseMimeType", "application/json");
        if (request.schema() != null) {
            generation.put("responseJsonSchema", request.schema());
        }
        body.put("generationConfig", generation);
        return body;
    }

    @Override
    protected String text(JsonNode response) {
        JsonNode candidate = response.path("candidates").path(0);
        if (candidate.isMissingNode()) {
            throw emptyOutput("no candidate, blockReason=" + response.path("promptFeedback").path("blockReason").asString(""));
        }
        String finishReason = candidate.path("finishReason").asString("");
        if ("MAX_TOKENS".equals(finishReason)) {
            throw truncated();
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            // Les parties de reflexion (thought) ne font pas partie de la reponse
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asString(""));
            }
        }
        if (text.isEmpty()) {
            throw emptyOutput("empty candidate, finishReason=" + finishReason);
        }
        return text.toString();
    }
}
