package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.LlmException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contrat commun aux trois clients, sur un serveur HTTP simule : succes (requete au format du fournisseur, schema
 * envoye), 401 sans nouvel essai, 429 + Retry-After puis succes, 5xx epuise, delai depasse sans nouvel essai,
 * sortie tronquee sans nouvel essai, et aucun secret dans les messages d'erreur.
 */
abstract class AbstractLlmClientTest {

    protected MockWebServer server;
    protected final JsonMapper json = JsonMapper.builder().build();

    abstract LlmProvider provider();

    /** Reponse de succes du fournisseur portant ce texte. */
    abstract MockResponse success(String text);

    /** Reponse du fournisseur coupee par la limite de tokens. */
    abstract MockResponse truncated(String partialText);

    abstract String expectedPath();

    /** Verifie l'authentification et le format de la requete (modele, messages, schema, limite de tokens). */
    abstract void assertRequestFormat(RecordedRequest request, JsonNode body);

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    protected LlmClient client() {
        return LlmClients.create(LlmTestSettings.of(provider(), server.url("/").toString()));
    }

    protected static LlmRequest request() {
        return LlmRequest.of("system prompt", "user prompt", LlmSchemas.READINESS,
                LlmSchemas.forProvider(LlmSchemas.load(LlmSchemas.READINESS)));
    }

    protected static MockResponse jsonResponse(String body) {
        return new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    @DisplayName("Success: the text is returned and the request follows the provider format, with the schema")
    void success() throws Exception {
        server.enqueue(success("{\"aiReady\":true,\"aiReadyReason\":\"ok\"}"));

        String text = client().complete(request());

        assertEquals("{\"aiReady\":true,\"aiReadyReason\":\"ok\"}", text);
        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals(expectedPath(), recorded.getPath());
        String body = recorded.getBody().readUtf8();
        assertFalse(recorded.getPath().contains("fake-api-key"), "the key never goes in the URL");
        assertFalse(body.contains("$schema"), "the provider receives the schema without $schema");
        assertRequestFormat(recorded, json.readTree(body));
    }

    @Test
    @DisplayName("HTTP 401: clean failure, never retried, no key nor body in the message")
    void unauthorizedIsNotRetried() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"invalid key fake-api-key\"}"));

        LlmException error = assertThrows(LlmException.class, () -> client().complete(request()));

        assertEquals(LlmException.Reason.UNAVAILABLE, error.reason());
        assertTrue(error.getMessage().contains("HTTP 401"), error.getMessage());
        assertFalse(error.getMessage().contains("fake-api-key"), error.getMessage());
        assertFalse(error.getMessage().contains(String.valueOf(server.getPort())), error.getMessage());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    @DisplayName("HTTP 429 with Retry-After: retried (delay capped), then success")
    void rateLimitIsRetried() {
        server.enqueue(new MockResponse().setResponseCode(429).addHeader("Retry-After", "30"));
        server.enqueue(success("{\"aiReady\":false,\"aiReadyReason\":\"flou\"}"));
        long start = System.nanoTime();

        String text = client().complete(request());

        assertEquals("{\"aiReady\":false,\"aiReadyReason\":\"flou\"}", text);
        assertEquals(2, server.getRequestCount());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 5, "Retry-After is capped by retry-max-delay");
    }

    @Test
    @DisplayName("HTTP 503 on every attempt: 1 call + 2 new attempts, then clean failure")
    void serverErrorsExhaustRetries() {
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setResponseCode(503));
        }

        LlmException error = assertThrows(LlmException.class, () -> client().complete(request()));

        assertEquals(LlmException.Reason.UNAVAILABLE, error.reason());
        assertTrue(error.getMessage().contains("HTTP 503"), error.getMessage());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    @DisplayName("Timeout: clean failure after the configured delay, never retried")
    void timeoutIsNotRetried() {
        server.enqueue(success("{}").setHeadersDelay(3, TimeUnit.SECONDS));
        LlmClient client = LlmClients.create(LlmTestSettings.of(provider(), server.url("/").toString(),
                Duration.ofMillis(300), 1000));
        long start = System.nanoTime();

        LlmException error = assertThrows(LlmException.class, () -> client.complete(request()));

        assertEquals(LlmException.Reason.UNAVAILABLE, error.reason());
        assertTrue(error.getMessage().contains("did not answer"), error.getMessage());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2500, "the timeout bounds the call");
        assertEquals(1, server.getRequestCount());
    }

    @Test
    @DisplayName("Output truncated by the token limit: explicit TRUNCATED failure, never retried")
    void truncationIsExplicit() {
        server.enqueue(truncated("{\"aiReady\":tr"));

        LlmException error = assertThrows(LlmException.class, () -> client().complete(request()));

        assertEquals(LlmException.Reason.TRUNCATED, error.reason());
        assertTrue(error.getMessage().contains("max-output-tokens"), error.getMessage());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    @DisplayName("Unreachable provider: clean failure without the URL")
    void unreachableProvider() throws IOException {
        String url = server.url("/").toString();
        server.shutdown();
        LlmClient client = LlmClients.create(LlmTestSettings.of(provider(), url));

        LlmException error = assertThrows(LlmException.class, () -> client.complete(request()));

        assertEquals(LlmException.Reason.UNAVAILABLE, error.reason());
        assertFalse(error.getMessage().contains(url), error.getMessage());
    }
}
