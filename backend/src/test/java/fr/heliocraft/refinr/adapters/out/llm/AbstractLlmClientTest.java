// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contrat commun aux trois clients, sur un serveur HTTP simule : succes (requete au format du fournisseur, schema
 * envoye), 401 sans nouvel essai, 429 + Retry-After puis succes, 5xx epuise, delai depasse sans nouvel essai,
 * sortie tronquee sans nouvel essai, et aucun secret dans les messages d'erreur.
 * <p>
 * Non-fuite (issue #29) : un marqueur unique est place dans le prompt, la sortie du modele, le corps des reponses
 * d'erreur et une cle factice ; sur chaque chemin d'echec, il n'apparait ni dans les logs captures, ni dans la trace
 * complete de l'exception, et une {@link LlmException} n'a jamais de cause.
 */
@ExtendWith(OutputCaptureExtension.class)
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
    // --- Non-fuite du contenu (issue #29) ---

    private static String marker() {
        return "LEAK-MARKER-" + UUID.randomUUID();
    }

    /** Cle factice evidente portant le marqueur. */
    private static String fakeKey(String marker) {
        return "fake-test-key-" + marker;
    }

    private LlmClient leakClient(String marker) {
        return leakClient(marker, server.url("/").toString(), Duration.ofSeconds(2));
    }

    private LlmClient leakClient(String marker, String baseUrl, Duration timeout) {
        return LlmClients.create(LlmTestSettings.of(provider(), baseUrl, timeout, 1000, fakeKey(marker)));
    }

    /** Analyse d'un document portant le marqueur : l'echec attendu, toujours une LlmException. */
    private LlmException failGenerate(LlmClient client, String marker) {
        return assertThrows(LlmException.class, () -> new LlmBacklogGenerator(client, structured())
                .generate("# Spec confidentielle " + marker + "\nGestion des stocks " + marker));
    }

    private static void assertNoLeak(String marker, CapturedOutput output, Throwable error) {
        assertFalse(output.getAll().contains(marker), "marker in the logs:\n" + output.getAll());
        if (error != null) {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            assertFalse(trace.toString().contains(marker), "marker in the stack trace:\n" + trace);
            assertNull(error.getCause(), "an LlmException never carries a cause: " + trace);
        }
    }

    @Test
    @DisplayName("No leak: invalid JSON from the model on every attempt (prompt and output never logged)")
    void noLeakOnInvalidJson(CapturedOutput output) {
        String marker = marker();
        for (int i = 0; i < 3; i++) {
            server.enqueue(success(marker + " pas du JSON {\"epics\": " + marker + "}"));
        }

        LlmException error = failGenerate(leakClient(marker), marker);

        assertEquals(LlmException.Reason.INVALID_OUTPUT, error.reason());
        assertTrue(output.getAll().contains("problem(s) [json-syntax]"), output.getAll());
        assertNoLeak(marker, output, error);
    }

    @Test
    @DisplayName("No leak: schema violated after every repair (field names and values from the model never logged)")
    void noLeakOnSchemaViolation(CapturedOutput output) throws Exception {
        String marker = marker();
        for (int i = 0; i < 3; i++) {
            server.enqueue(success("{\"epics\":[{\"" + marker + "\":\"" + marker + "\",\"name\":1}],\""
                    + marker + "x\":\"" + marker + "\"}"));
        }

        LlmException error = failGenerate(leakClient(marker), marker);

        assertEquals(LlmException.Reason.INVALID_OUTPUT, error.reason());
        assertTrue(output.getAll().contains("[schema: "), output.getAll());
        assertTrue(output.getAll().contains("additionalProperties="), output.getAll());
        assertNoLeak(marker, output, error);
        // Le detail reste renvoye au modele, seul destinataire de la reparation
        server.takeRequest();
        assertTrue(server.takeRequest().getBody().readUtf8().contains(marker));
    }

    @Test
    @DisplayName("No leak: a mapping error quoting the model output (duplicate verdict id) is never logged")
    void noLeakOnMappingError(CapturedOutput output) {
        String marker = marker();
        String duplicated = "{\"verdicts\":[{\"id\":\"" + marker + "\",\"aiReady\":true,\"aiReadyReason\":\"ok\"},"
                + "{\"id\":\"" + marker + "\",\"aiReady\":true,\"aiReadyReason\":\"ok\"}]}";
        for (int i = 0; i < 6; i++) {
            server.enqueue(success(duplicated));
        }
        Story story = new Story();
        story.setTitle("Story " + marker);
        story.setDescription("Description " + marker);

        List<Readiness> verdicts = new LlmReadinessAssessor(leakClient(marker), structured(), 5).assess(List.of(story));

        assertNull(verdicts.get(0), "no verdict after the failed repairs");
        assertTrue(output.getAll().contains("[mapping: IllegalArgumentException]"), output.getAll());
        assertNoLeak(marker, output, null);
    }

    @Test
    @DisplayName("No leak: stories without a verdict are logged by position, never by title")
    void noLeakOnMissingVerdict(CapturedOutput output) {
        String marker = marker();
        server.enqueue(success("{\"verdicts\":[]}"));
        server.enqueue(success("{\"verdicts\":[]}"));
        Story story = new Story();
        story.setTitle("Story " + marker);

        List<Readiness> verdicts = new LlmReadinessAssessor(leakClient(marker), structured(), 5).assess(List.of(story));

        assertNull(verdicts.get(0));
        assertTrue(output.getAll().contains("Readiness verdict unavailable for story 1/1"), output.getAll());
        assertNoLeak(marker, output, null);
    }

    @Test
    @DisplayName("No leak: reassessment failing (story summary, description and user precision never logged)")
    void noLeakOnReassessment(CapturedOutput output) {
        String marker = marker();
        for (int i = 0; i < 3; i++) {
            server.enqueue(success("{\"aiReady\":\"" + marker + "\"}"));
        }

        LlmException error = assertThrows(LlmException.class, () -> new LlmReadinessAssessor(leakClient(marker),
                structured(), 5).reassess("Export " + marker, "Description " + marker, "Precision " + marker));

        assertEquals(LlmException.Reason.INVALID_OUTPUT, error.reason());
        assertNoLeak(marker, output, error);
    }

    @Test
    @DisplayName("No leak: output truncated by the token limit")
    void noLeakOnTruncation(CapturedOutput output) {
        String marker = marker();
        server.enqueue(truncated("{\"epics\":[{\"name\":\"" + marker));

        LlmException error = failGenerate(leakClient(marker), marker);

        assertEquals(LlmException.Reason.TRUNCATED, error.reason());
        assertNoLeak(marker, output, error);
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {400, 401, 403, 404, 413, 429, 500, 503})
    @DisplayName("No leak: HTTP error whose body echoes the prompt and the key")
    void noLeakOnHttpError(int status, CapturedOutput output) {
        String marker = marker();
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setResponseCode(status).addHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"prompt " + marker
                            + " rejected for key " + fakeKey(marker) + "\"}}"));
        }

        LlmException error = failGenerate(leakClient(marker), marker);

        assertEquals(LlmException.Reason.UNAVAILABLE, error.reason());
        assertTrue(error.getMessage().contains("HTTP " + status), error.getMessage());
        assertTrue(output.getAll().contains("HTTP " + status), output.getAll());
        assertNoLeak(marker, output, error);
    }

    @Test
    @DisplayName("No leak: HTTP 200 whose body is not JSON (proxy page quoting the request)")
    void noLeakOnUnreadableResponse(CapturedOutput output) {
        String marker = marker();
        server.enqueue(new MockResponse().setResponseCode(200).setBody(marker + " <html>proxy " + marker + "</html>"));

        LlmException error = failGenerate(leakClient(marker), marker);

        assertEquals(LlmException.Reason.UNAVAILABLE, error.reason());
        assertTrue(output.getAll().contains("invalid-json"), output.getAll());
        assertNoLeak(marker, output, error);
    }

    @Test
    @DisplayName("No leak: timeout")
    void noLeakOnTimeout(CapturedOutput output) {
        String marker = marker();
        server.enqueue(success(marker).setHeadersDelay(3, TimeUnit.SECONDS));

        LlmException error = failGenerate(leakClient(marker, server.url("/").toString(), Duration.ofMillis(300)), marker);

        assertEquals(LlmException.Reason.UNAVAILABLE, error.reason());
        assertNoLeak(marker, output, error);
    }

    @Test
    @DisplayName("No leak: unreachable provider whose base URL carries credentials (user info, ?key=)")
    void noLeakOnUnreachableProvider(CapturedOutput output) throws IOException {
        String marker = marker();
        int port = server.getPort();
        server.shutdown();
        String baseUrl = "http://refinr:" + fakeKey(marker) + "@localhost:" + port + "/v1?key=" + fakeKey(marker);

        LlmException error = failGenerate(leakClient(marker, baseUrl, Duration.ofSeconds(2)), marker);

        assertEquals(LlmException.Reason.UNAVAILABLE, error.reason());
        assertTrue(output.getAll().contains("connection-refused"), output.getAll());
        assertNoLeak(marker, output, error);
    }

    @Test
    @DisplayName("No leak: HTTP error with credentials in the base URL (user info, ?key=)")
    void noLeakOnHttpErrorWithCredentialsInUrl(CapturedOutput output) {
        String marker = marker();
        server.enqueue(new MockResponse().setResponseCode(401).setBody("unauthorized"));
        String baseUrl = "http://refinr:" + fakeKey(marker) + "@localhost:" + server.getPort() + "/?key=" + fakeKey(marker);

        LlmException error = failGenerate(leakClient(marker, baseUrl, Duration.ofSeconds(2)), marker);

        assertEquals(LlmException.Reason.UNAVAILABLE, error.reason());
        assertNoLeak(marker, output, error);
    }
}
