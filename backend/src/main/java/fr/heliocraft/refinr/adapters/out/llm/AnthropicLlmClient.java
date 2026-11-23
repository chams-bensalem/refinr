// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

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
 * Temperature envoyee seulement si elle est configuree ; {@code stop_reason: max_tokens} = troncature.
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
        if (settings.temperature() != null) {
            body.put("temperature", settings.temperature());
        }
        if (request.schema() != null) {
            body.put("output_config", Map.of("format", Map.of("type", "json_schema", "schema", supportedSchema(request.schema()))));
        }
        return body;
    }

    // output_config.effort reste permis a cote du format gere par Refinr
    private static final Set<String> RESERVED = Set.of("model", "max_tokens", "system", "messages",
            "output_config.format", "temperature", "stream");

    @Override
    protected Set<String> reservedPaths() {
        return RESERVED;
    }

    @Override
    protected String temperaturePath() {
        return "temperature";
    }

    // Contraintes refusees (HTTP 400) par les sorties structurees d'Anthropic ; elles restent verifiees localement
    private static final Set<String> UNSUPPORTED = Set.of("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
            "multipleOf", "minLength", "maxLength", "minItems", "maxItems", "uniqueItems", "pattern");

    /**
     * Copie du schema pour Anthropic (validation locale inchangee) : sans les contraintes non prises en charge, et
     * <b>toutes les proprietes obligatoires</b>. Anthropic compile le schema en grammaire et refuse (400 « Schema is too
     * complex ») un schema trop complexe : chaque propriete facultative double une partie de la grammaire, et le
     * backlog en a une quinzaine sur quatre niveaux (epic, feature, story, sous-tache). Une propriete obligatoire peut
     * rester vide : chaine vide, liste vide ou 0 (les bornes {@code minLength}, {@code minItems} et {@code minimum} ne sont
     * pas envoyees) ; seules les priorites (enumeration High, Medium, Low) n'ont pas de valeur vide, et le prompt les
     * demande pour chaque element.
     */
    static JsonNode supportedSchema(JsonNode schema) {
        JsonNode copy = schema.deepCopy();
        strip(copy);
        requireAll(copy);
        return copy;
    }

    /** Chaque objet a proprietes les declare toutes dans {@code required}, dans leur ordre. */
    private static void requireAll(JsonNode node) {
        if (node instanceof ObjectNode object) {
            if (object.get("properties") instanceof ObjectNode properties) {
                tools.jackson.databind.node.ArrayNode required = object.putArray("required");
                for (Map.Entry<String, JsonNode> property : properties.properties()) {
                    required.add(property.getKey());
                    requireAll(property.getValue());
                }
            }
            for (Map.Entry<String, JsonNode> field : object.properties()) {
                if (!"properties".equals(field.getKey())) {
                    requireAll(field.getValue());
                }
            }
        } else if (node != null && node.isArray()) {
            node.forEach(AnthropicLlmClient::requireAll);
        }
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
