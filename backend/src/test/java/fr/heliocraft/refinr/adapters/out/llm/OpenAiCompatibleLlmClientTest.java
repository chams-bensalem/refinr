package fr.heliocraft.refinr.adapters.out.llm;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.StringNode;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiCompatibleLlmClientTest extends AbstractLlmClientTest {

    @Override
    LlmProvider provider() {
        return LlmProvider.OPENAI_COMPATIBLE;
    }

    @Override
    MockResponse success(String text) {
        return jsonResponse("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":"
                + StringNode.valueOf(text) + "}}]}");
    }

    @Override
    MockResponse truncated(String partialText) {
        return jsonResponse("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":"
                + StringNode.valueOf(partialText) + "}}]}");
    }

    @Override
    String expectedHealthPath() {
        return "/v1/models";
    }

    @Override
    MockResponse healthUp() {
        return jsonResponse("{\"object\":\"list\",\"data\":[{\"id\":\"llama3\"},{\"id\":\"qwen3:8b\"}]}");
    }

    @Override
    String expectedPath() {
        return "/v1/chat/completions";
    }

    @Override
    protected LlmClient client() {
        // base-url avec la version de l'API, comme http://localhost:11434/v1
        return LlmClients.create(LlmTestSettings.of(provider(), server.url("/v1").toString()));
    }

    @Override
    void assertRequestFormat(RecordedRequest request, JsonNode body) {
        assertEquals("Bearer fake-api-key", request.getHeader("Authorization"));
        assertEquals("qwen3:8b", body.path("model").asString());
        assertEquals("system", body.path("messages").path(0).path("role").asString());
        assertEquals("system prompt", body.path("messages").path(0).path("content").asString());
        assertEquals("user", body.path("messages").path(1).path("role").asString());
        assertEquals(1000, body.path("max_tokens").asInt());
        JsonNode format = body.path("response_format");
        assertEquals("json_schema", format.path("type").asString());
        assertEquals("readiness", format.path("json_schema").path("name").asString());
        assertTrue(format.path("json_schema").path("schema").path("properties").has("aiReady"));
    }

    private LlmClient client(JsonMode mode, String apiKey, Map<String, Object> extraBody, int maxOutputTokens) {
        return LlmClients.create(new LlmSettings("verdict", LlmProvider.OPENAI_COMPATIBLE, server.url("/v1").toString(),
                "deepseek-chat", apiKey, Duration.ofSeconds(2), maxOutputTokens, null, mode, extraBody,
                RetryPolicy.none()));
    }

    @Test
    @DisplayName("json_object mode, no key (no Authorization header), extra-body merged, no limit when 0")
    void jsonObjectModeWithoutKey() throws Exception {
        server.enqueue(success("{}"));

        client(JsonMode.JSON_OBJECT, "", Map.of("think", false), 0).complete(request());

        RecordedRequest recorded = server.takeRequest();
        assertNull(recorded.getHeader("Authorization"));
        JsonNode body = json.readTree(recorded.getBody().readUtf8());
        assertEquals("json_object", body.path("response_format").path("type").asString());
        assertFalse(body.path("think").asBoolean(true));
        assertFalse(body.has("max_tokens"));
        assertFalse(body.has("temperature"));
    }

    @Test
    @DisplayName("Health DOWN when the configured model is not in the list (e.g. Ollama model not pulled)")
    void healthDownWhenModelMissing() {
        server.enqueue(jsonResponse("{\"data\":[{\"id\":\"llama3\"}]}"));

        var health = client().health("ai");

        assertFalse(health.up());
        assertEquals("model qwen3:8b not found", health.details().get("error"));
    }

    @Test
    @DisplayName("none mode sends no response_format")
    void noJsonMode() throws Exception {
        server.enqueue(success("{}"));

        client(JsonMode.NONE, "k", Map.of(), 100).complete(request());

        assertFalse(json.readTree(server.takeRequest().getBody().readUtf8()).has("response_format"));
    }
}
