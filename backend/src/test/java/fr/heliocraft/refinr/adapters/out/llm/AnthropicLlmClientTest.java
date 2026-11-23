// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.StringNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicLlmClientTest extends AbstractLlmClientTest {

    @Override
    LlmProvider provider() {
        return LlmProvider.ANTHROPIC;
    }

    @Override
    MockResponse success(String text) {
        return jsonResponse("{\"type\":\"message\",\"stop_reason\":\"end_turn\",\"content\":["
                + "{\"type\":\"thinking\",\"thinking\":\"\"},{\"type\":\"text\",\"text\":" + StringNode.valueOf(text) + "}]}");
    }

    @Override
    MockResponse truncated(String partialText) {
        return jsonResponse("{\"type\":\"message\",\"stop_reason\":\"max_tokens\",\"content\":["
                + "{\"type\":\"text\",\"text\":" + StringNode.valueOf(partialText) + "}]}");
    }

    @Override
    String expectedHealthPath() {
        return "/v1/models/claude-opus-5-5";
    }

    @Override
    MockResponse healthUp() {
        return jsonResponse("{\"id\":\"claude-opus-5-5\",\"type\":\"model\"}");
    }

    @Override
    String expectedPath() {
        return "/v1/messages";
    }

    @Override
    void assertRequestFormat(RecordedRequest request, JsonNode body) {
        assertEquals("fake-api-key", request.getHeader("x-api-key"));
        assertEquals("2023-06-01", request.getHeader("anthropic-version"));
        assertEquals("claude-opus-5-5", body.path("model").asString());
        assertEquals(1000, body.path("max_tokens").asInt());
        assertEquals("system prompt", body.path("system").asString());
        assertEquals("user", body.path("messages").path(0).path("role").asString());
        assertEquals("user prompt", body.path("messages").path(0).path("content").asString());
        assertEquals("json_schema", body.path("output_config").path("format").path("type").asString());
        assertTrue(body.path("output_config").path("format").path("schema").path("properties").has("aiReady"));
        assertFalse(body.has("stream"), "no streaming");
    }

    @Override
    String temperaturePath() {
        return "temperature";
    }

    /** Objet JSON (thinking) et chemin fusionne a cote du format gere par Refinr (output_config.effort). */
    @Override
    Map<String, Object> extraBodyExample() {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("output_config.effort", "low");
        example.put("thinking", Map.of("type", "adaptive"));
        return example;
    }

    @Override
    List<String> reservedKeys() {
        return List.of("model", "max_tokens", "system", "messages", "output_config", "output_config.format",
                "output_config.format.schema", "temperature", "stream");
    }

    @Test
    @DisplayName("The schema sent to Anthropic has no unsupported constraint (minLength, minimum...), local schema unchanged")
    void unsupportedConstraintsAreStripped() throws Exception {
        for (String name : new String[] {LlmSchemas.BACKLOG, LlmSchemas.READINESS_BATCH}) {
            server.enqueue(success("{}"));
            JsonNode local = LlmSchemas.forProvider(LlmSchemas.load(name));
            client().complete(LlmRequest.of("s", "u", name, local));

            String sent = json.readTree(server.takeRequest().getBody().readUtf8())
                    .path("output_config").path("format").path("schema").toString();
            for (String keyword : new String[] {"\"minLength\"", "\"minimum\"", "\"maxLength\"", "\"maximum\""}) {
                assertFalse(sent.contains(keyword), name + " sent with " + keyword + ": " + sent);
            }
            assertTrue(sent.contains("\"additionalProperties\":false"), sent);
            assertTrue(sent.contains("\"required\""), sent);
            assertTrue(local.toString().contains("\"minLength\""), "the local schema keeps its constraints");
        }
    }

    @Test
    @DisplayName("A field named like a keyword (e.g. 'pattern') is kept: only keywords are stripped")
    void propertyNamesAreKept() {
        JsonNode schema = json.readTree("{\"type\":\"object\",\"properties\":{\"pattern\":{\"type\":\"string\",\"minLength\":1}}}");

        JsonNode stripped = AnthropicLlmClient.supportedSchema(schema);

        assertTrue(stripped.path("properties").has("pattern"));
        assertFalse(stripped.path("properties").path("pattern").has("minLength"));
    }

    /** Objets du schema dont une propriete n'est pas dans required (chemin JSON de chacun). */
    private static void optionalProperties(JsonNode node, String path, java.util.List<String> found) {
        if (node.isObject()) {
            JsonNode properties = node.get("properties");
            if (properties != null && properties.isObject()) {
                java.util.Set<String> required = new java.util.HashSet<>();
                node.path("required").forEach(name -> required.add(name.asString()));
                for (String name : properties.propertyNames()) {
                    if (!required.contains(name)) {
                        found.add(path + "/" + name);
                    }
                    optionalProperties(properties.get(name), path + "/" + name, found);
                }
            }
            for (String key : node.propertyNames()) {
                if (!"properties".equals(key)) {
                    optionalProperties(node.get(key), path + "/" + key, found);
                }
            }
        } else if (node.isArray()) {
            node.forEach(item -> optionalProperties(item, path + "[]", found));
        }
    }

    @Test
    @DisplayName("Backlog schema sent to Anthropic: no optional property at any level, and every field still accepts an empty value")
    void backlogSchemaHasNoOptionalProperty() {
        JsonNode local = LlmSchemas.load(LlmSchemas.BACKLOG);
        JsonNode sent = AnthropicLlmClient.supportedSchema(LlmSchemas.forProvider(local));

        java.util.List<String> optional = new java.util.ArrayList<>();
        optionalProperties(sent, "", optional);
        assertTrue(optional.isEmpty(), "optional properties sent to Anthropic: " + optional);
        java.util.List<String> localOptional = new java.util.ArrayList<>();
        optionalProperties(local, "", localOptional);
        assertFalse(localOptional.isEmpty(), "the local schema keeps its optional properties");

        // Tout vide sauf les priorites (enumeration) et le verdict (booleen) : le modele n'est jamais force a inventer
        String task = "{\"title\":\"\",\"description\":\"\",\"estimatedHours\":0,\"labels\":[]}";
        String story = "{\"asA\":\"\",\"iWant\":\"\",\"soThat\":\"\",\"title\":\"\",\"description\":\"\","
                + "\"acceptanceCriteria\":[],\"estimatedStoryPoints\":0,\"priority\":\"Medium\",\"labels\":[],"
                + "\"dependencies\":[],\"aiReady\":false,\"aiReadyReason\":\"\",\"tasks\":[" + task + "]}";
        String feature = "{\"name\":\"\",\"description\":\"\",\"priority\":\"Medium\",\"labels\":[],\"stories\":["
                + story + "]}";
        String backlog = "{\"epics\":[{\"name\":\"\",\"description\":\"\",\"priority\":\"Medium\",\"labels\":[],"
                + "\"features\":[" + feature + "]}]}";
        StructuredOutput validator = new StructuredOutput(0);
        assertTrue(validator.validate(sent, json.readTree(backlog)).isEmpty(),
                () -> validator.validate(sent, json.readTree(backlog)).toString());
        String emptyLists = "{\"epics\":[{\"name\":\"\",\"description\":\"\",\"priority\":\"Low\",\"labels\":[],"
                + "\"features\":[]}]}";
        assertTrue(validator.validate(sent, json.readTree(emptyLists)).isEmpty(), "empty lists accepted");
    }

    @Test
    @DisplayName("HTTP 529 (overloaded) is retried")
    void overloadedIsRetried() {
        server.enqueue(new MockResponse().setResponseCode(529));
        server.enqueue(success("{}"));

        assertEquals("{}", client().complete(request()));
        assertEquals(2, server.getRequestCount());
    }
}
