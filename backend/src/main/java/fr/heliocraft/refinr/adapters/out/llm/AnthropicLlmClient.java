package fr.heliocraft.refinr.adapters.out.llm;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API Messages d'Anthropic, sans streaming : sortie structuree par {@code output_config.format} (json_schema).
 * Aucune temperature envoyee (refusee par les modeles Claude recents) ; {@code stop_reason: max_tokens} = troncature.
 */
public class AnthropicLlmClient extends LlmClient {

    static final String API_VERSION = "2023-06-01";

    public AnthropicLlmClient(LlmSettings settings) {
        super(settings);
    }

    @Override
    protected String generatePath() {
        return "/v1/messages";
    }

    @Override
    protected String healthPath() {
        return "/v1/models/" + settings.model();
    }

    @Override
    protected Map<String, String> headers() {
        return Map.of("x-api-key", settings.apiKey(), "anthropic-version", API_VERSION);
    }

    @Override
    protected Map<String, Object> body(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.model());
        body.put("max_tokens", settings.maxOutputTokens());
        body.put("system", request.system());
        List<Map<String, Object>> messages = new ArrayList<>();
        for (LlmRequest.Turn turn : request.messages()) {
            messages.add(Map.of("role", turn.role() == LlmRequest.Role.USER ? "user" : "assistant", "content", turn.text()));
        }
        body.put("messages", messages);
        if (request.schema() != null) {
            body.put("output_config", Map.of("format", Map.of("type", "json_schema", "schema", request.schema())));
        }
        return body;
    }

    @Override
    protected String text(JsonNode response) {
        String stopReason = response.path("stop_reason").asString("");
        if ("max_tokens".equals(stopReason)) {
            throw truncated();
        }
        if ("refusal".equals(stopReason)) {
            throw emptyOutput("refusal");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asString(""))) {
                text.append(block.path("text").asString(""));
            }
        }
        if (text.isEmpty()) {
            throw emptyOutput("no text block, stop_reason=" + stopReason);
        }
        return text.toString();
    }
}
