package fr.heliocraft.refinr.adapters.out.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            body.put("output_config", Map.of("format", Map.of("type", "json_schema", "schema", supportedSchema(request.schema()))));
        }
        return body;
    }

    // Contraintes refusees (HTTP 400) par les sorties structurees d'Anthropic ; elles restent verifiees localement
    private static final Set<String> UNSUPPORTED = Set.of("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
            "multipleOf", "minLength", "maxLength", "minItems", "maxItems", "uniqueItems", "pattern");

    /** Copie du schema sans les contraintes non prises en charge par Anthropic (validation locale inchangee). */
    static JsonNode supportedSchema(JsonNode schema) {
        JsonNode copy = schema.deepCopy();
        strip(copy);
        return copy;
    }

    private static void strip(JsonNode node) {
        if (node instanceof ObjectNode object) {
            // "properties" contient des noms de champs, pas des mots-cles : seules leurs valeurs sont nettoyees
            boolean keywords = true;
            strip(object, keywords);
        } else if (node.isArray()) {
            node.forEach(AnthropicLlmClient::strip);
        }
    }

    private static void strip(ObjectNode object, boolean keywords) {
        if (keywords) {
            UNSUPPORTED.forEach(object::remove);
        }
        for (Map.Entry<String, JsonNode> field : object.properties()) {
            JsonNode value = field.getValue();
            if ("properties".equals(field.getKey()) && value instanceof ObjectNode properties) {
                for (Map.Entry<String, JsonNode> property : properties.properties()) {
                    strip(property.getValue());
                }
            } else {
                strip(value);
            }
        }
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
