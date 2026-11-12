package fr.heliocraft.refinr.adapters.out.llm;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.StringNode;

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
        assertFalse(body.has("temperature"), "no temperature for Claude models");
        assertFalse(body.has("stream"), "no streaming");
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
