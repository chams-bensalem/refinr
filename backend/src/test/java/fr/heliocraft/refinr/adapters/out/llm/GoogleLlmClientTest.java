// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.StringNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GoogleLlmClientTest extends AbstractStructuredErrorLlmClientTest {

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
    }

    @Override
    String temperaturePath() {
        return "generationConfig.temperature";
    }

    @Override
    Map<String, Object> extraBodyExample() {
        return Map.of("generationConfig.thinkingConfig.thinkingLevel", "low");
    }

    @Override
    List<String> reservedKeys() {
        return List.of("systemInstruction", "contents", "generationConfig", "generationConfig.maxOutputTokens",
                "generationConfig.responseMimeType", "generationConfig.responseJsonSchema", "generationConfig.temperature",
                "generation_config.temperature");
    }

    /** google.rpc.BadRequest : Google designe le champ en snake_case (generation_config.thinking_config...). */
    @Override
    MockResponse badRequestNaming(String field, String message) {
        String snake = field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
        return new MockResponse().setResponseCode(400).addHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"code\":400,\"status\":\"INVALID_ARGUMENT\",\"message\":" + StringNode.valueOf(message)
                        + ",\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.BadRequest\",\"fieldViolations\":[{\"field\":"
                        + StringNode.valueOf(snake) + ",\"description\":" + StringNode.valueOf(message) + "}]}]}}");
    }

    @Test
    @DisplayName("A blocked prompt (no candidate) is a clean failure")
    void blockedPrompt() {
        server.enqueue(jsonResponse("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));

        assertThrows(fr.heliocraft.refinr.application.port.out.LlmException.class, () -> client().complete(request()));
    }

    // ---------- delai annonce par google.rpc.RetryInfo (B1) ----------

    private static MockResponse quotaWithRetryInfo(String retryDelay) {
        return new MockResponse().setResponseCode(429).addHeader("Content-Type", "application/json").setBody(
                "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"quota for fake-api-key\","
                        + "\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.QuotaFailure\"},"
                        + "{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"" + retryDelay + "\"}]}}");
    }

    @Test
    @DisplayName("HTTP 429 with google.rpc.RetryInfo beyond retry-max-delay: RATE_LIMITED with that delay, 1 request")
    void retryInfoIsRead() {
        server.enqueue(quotaWithRetryInfo("45s"));

        fr.heliocraft.refinr.application.port.out.LlmException error = assertThrows(
                fr.heliocraft.refinr.application.port.out.LlmException.class, () -> client().complete(request()));

        assertEquals(fr.heliocraft.refinr.application.port.out.LlmException.Reason.RATE_LIMITED, error.reason());
        assertEquals(java.util.Optional.of(java.time.Duration.ofSeconds(45)), error.retryAfter());
        assertEquals(1, server.getRequestCount());
        assertFalse(error.getMessage().contains("fake-api-key"), error.getMessage());
    }

    @Test
    @DisplayName("RetryInfo: fractional delay rounded up, unreadable delay ignored, Retry-After header preferred")
    void retryInfoFormats() {
        LlmClient client = client();
        String body = "{\"error\":{\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"%s\"}]}}";
        assertEquals(java.time.Duration.ofSeconds(2), client.requestedDelay(null, body.formatted("1.5s")));
        assertEquals(java.time.Duration.ofSeconds(1), client.requestedDelay(null, body.formatted("0.2s")));
        assertNull(client.requestedDelay(null, body.formatted("soon")));
        assertNull(client.requestedDelay(null, "{\"error\":{\"details\":[]}}"));
        assertEquals(java.time.Duration.ofSeconds(9), client.requestedDelay("9", body.formatted("45s")));
    }
}
