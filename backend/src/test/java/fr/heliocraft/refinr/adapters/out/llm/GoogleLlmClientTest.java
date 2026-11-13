// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.StringNode;

import static org.junit.jupiter.api.Assertions.*;

class GoogleLlmClientTest extends AbstractLlmClientTest {

    @Override
    LlmProvider provider() {
        return LlmProvider.GOOGLE;
    }

    @Override
    MockResponse success(String text) {
        return jsonResponse("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":["
                + "{\"text\":\"reflexion\",\"thought\":true},{\"text\":" + StringNode.valueOf(text) + "}]}}]}");
    }

    @Override
    MockResponse truncated(String partialText) {
        return jsonResponse("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":["
                + "{\"text\":" + StringNode.valueOf(partialText) + "}]}}]}");
    }

    @Override
    String expectedHealthPath() {
        return "/v1beta/models/gemini-3.6-flash";
    }

    @Override
    MockResponse healthUp() {
        return jsonResponse("{\"name\":\"models/gemini-3.6-flash\"}");
    }

    @Override
    String expectedPath() {
        return "/v1beta/models/gemini-3.6-flash:generateContent";
    }

    @Override
    void assertRequestFormat(RecordedRequest request, JsonNode body) {
        assertEquals("fake-api-key", request.getHeader("x-goog-api-key"));
        assertEquals("system prompt", body.path("systemInstruction").path("parts").path(0).path("text").asString());
        assertEquals("user", body.path("contents").path(0).path("role").asString());
        assertEquals("user prompt", body.path("contents").path(0).path("parts").path(0).path("text").asString());
        JsonNode generation = body.path("generationConfig");
        assertEquals("application/json", generation.path("responseMimeType").asString());
        assertTrue(generation.path("responseJsonSchema").path("properties").has("aiReady"));
        assertEquals(1000, generation.path("maxOutputTokens").asInt());
        assertEquals(0.2, generation.path("temperature").asDouble());
    }

    @Test
    @DisplayName("A blocked prompt (no candidate) is a clean failure")
    void blockedPrompt() {
        server.enqueue(jsonResponse("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));

        assertThrows(fr.heliocraft.refinr.application.port.out.LlmException.class, () -> client().complete(request()));
    }
}
