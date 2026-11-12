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

    /** Chemin de la lecture de health check et reponse UP du fournisseur. */
    abstract String expectedHealthPath();

    abstract MockResponse healthUp();

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
    @DisplayName("Invalid JSON, then a repair request carrying the faulty answer and the errors, then success")
    void invalidJsonIsRepaired() throws Exception {
        server.enqueue(success("{\"aiReady\": \"oui\""));
        server.enqueue(success("{\"aiReady\":true,\"aiReadyReason\":\"corrige\"}"));

        String reason = structured().call(client(), request(), LlmSchemas.load(LlmSchemas.READINESS),
                json -> json.path("aiReadyReason").asString());

        assertEquals("corrige", reason);
        assertEquals(2, server.getRequestCount());
        server.takeRequest();
        String repair = server.takeRequest().getBody().readUtf8();
        assertTrue(repair.contains("n'est pas valide"), repair);
        assertTrue(repair.contains("JSON invalide"), repair);
        assertTrue(repair.contains("oui"), "the faulty answer is part of the conversation: " + repair);
    }

    @Test
    @DisplayName("Output not matching the schema on every attempt: 1 call + 2 repairs, then INVALID_OUTPUT")
    void schemaViolationsExhaustRepairs() throws Exception {
        for (int i = 0; i < 3; i++) {
            server.enqueue(success("{\"aiReady\":\"yes\"}"));
        }

        LlmException error = assertThrows(LlmException.class, () -> structured().call(client(), request(),
                LlmSchemas.load(LlmSchemas.READINESS), json -> json));

        assertEquals(LlmException.Reason.INVALID_OUTPUT, error.reason());
        assertEquals(3, server.getRequestCount());
        server.takeRequest();
        String repair = server.takeRequest().getBody().readUtf8();
        assertTrue(repair.contains("aiReadyReason"), "the missing property is reported: " + repair);
        assertTrue(repair.contains("aiReady"), repair);
    }

    @Test
    @DisplayName("A truncated output is never repaired")
    void truncatedOutputIsNotRepaired() {
        server.enqueue(truncated("{\"aiReady\":tr"));

        LlmException error = assertThrows(LlmException.class, () -> structured().call(client(), request(),
                LlmSchemas.load(LlmSchemas.READINESS), json -> json));

        assertEquals(LlmException.Reason.TRUNCATED, error.reason());
        assertEquals(1, server.getRequestCount());
    }

    protected static StructuredOutput structured() {
        return new StructuredOutput(2);
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
    @DisplayName("Only a short provider error type is kept from an error body, never its message")
    void errorTypeOnly() {
        LlmClient client = client();
        assertEquals("authentication_error",
                client.errorType("{\"error\":{\"type\":\"authentication_error\",\"message\":\"key sk-abc\"}}"));
        assertEquals("UNAUTHENTICATED", client.errorType("{\"error\":{\"code\":401,\"status\":\"UNAUTHENTICATED\"}}"));
        assertEquals("unknown", client.errorType("invalid key sk-abc"));
        assertEquals("unknown", client.errorType("{\"error\":{\"type\":\"Incorrect API key provided: sk-abc\"}}"));
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
    @DisplayName("Health UP: authenticated read of the model, provider and model reported, no key nor URL")
    void healthIsUp() throws Exception {
        server.enqueue(healthUp());

        var health = client().health("ai");

        assertTrue(health.up(), health.toString());
        assertEquals("ai", health.name());
        assertEquals(provider().id(), health.details().get("provider"));
        RecordedRequest recorded = server.takeRequest();
        assertEquals("GET", recorded.getMethod());
        assertEquals(expectedHealthPath(), recorded.getPath());
        assertFalse(health.toString().contains("fake-api-key"));
        assertFalse(health.toString().contains(String.valueOf(server.getPort())));
    }

    @Test
    @DisplayName("Health DOWN on a rejected key: 'HTTP 401', checked once (no retry)")
    void healthDownOnRejectedKey() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"bad key fake-api-key\"}"));

        var health = client().health("aiVerdict");

        assertFalse(health.up());
        assertEquals("HTTP 401", health.details().get("error"));
        assertFalse(health.toString().contains("fake-api-key"));
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
