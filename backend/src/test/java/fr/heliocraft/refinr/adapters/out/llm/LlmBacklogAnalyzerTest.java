package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LlmBacklogAnalyzerTest {

    private MockWebServer mockWebServer;
    private LlmBacklogAnalyzer analyzer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();

        analyzer = new LlmBacklogAnalyzer(
            WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build(),
            "fake-api-key"
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Should analyze document and return valid result")
    void shouldAnalyzeDocumentAndReturnValidResult() throws InterruptedException {
        // Given
        String cdcContent = "# Projet Refinr\n\nModule 1: Inventaire\nModule 2: Meeting Assistant";

        String mockGeminiResponse = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "text": "[{\\"name\\":\\"Epic: Inventaire\\",\\"description\\":\\"Gestion des stocks\\",\\"priority\\":\\"High\\",\\"labels\\":[\\"backend\\"],\\"stories\\":[{\\"asA\\":\\"Developer\\",\\"iWant\\":\\"track stock levels\\",\\"soThat\\":\\"avoid stock-outs\\",\\"title\\":\\"Track Stock\\",\\"acceptanceCriteria\\":[\\"AC1\\"],\\"priority\\":\\"High\\",\\"labels\\":[\\"api\\"],\\"tasks\\":[{\\"title\\":\\"Task 1\\",\\"description\\":\\"Do something\\"}]}]}]"
                  }]
                }
              }]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(mockGeminiResponse)
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));

        // When
        List<Epic> epics = analyzer.generate(cdcContent);

        // Then
        assertNotNull(epics);
        assertEquals(1, epics.size());

        Epic epic = epics.get(0);
        assertEquals("Epic: Inventaire", epic.getName());
        assertEquals("Gestion des stocks", epic.getDescription());
        assertEquals("High", epic.getPriority());
        assertEquals(1, epic.getStories().size());

        // Verify request
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("POST", request.getMethod());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("maxOutputTokens"));
        assertTrue(body.contains("65536"));
    }

    @Test
    @DisplayName("Should handle Gemini API error gracefully")
    void shouldHandleGeminiAPIErrorGracefully() {
        // Given
        String cdcContent = "Some content";

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"error\":\"Internal Server Error\"}"));

        // When/Then
        assertThrows(RuntimeException.class, () -> analyzer.generate(cdcContent));
    }

    @Test
    @DisplayName("Should send correct prompt structure to Gemini")
    void shouldSendCorrectPromptStructureToGemini() throws InterruptedException {
        // Given
        String cdcContent = "Test CDC content";

        String mockResponse = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "text": "[]"
                  }]
                }
              }]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));

        // When
        analyzer.generate(cdcContent);

        // Then
        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();

        assertTrue(body.contains("\"contents\""));
        assertTrue(body.contains("\"parts\""));
        assertTrue(body.contains("\"text\""));
        assertTrue(body.contains("\"generationConfig\""));
        assertTrue(body.contains("\"temperature\""));
        assertTrue(body.contains("\"maxOutputTokens\""));
        assertTrue(body.contains("Test CDC content"));
    }

    @Test
    @DisplayName("Should parse complex nested JSON from Gemini")
    void shouldParseComplexNestedJSONFromGemini() throws InterruptedException {
        // Given
        String cdcContent = "Complex project";

        String mockGeminiResponse = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "text": "[{\\"name\\":\\"Epic 1\\",\\"description\\":\\"Desc\\",\\"priority\\":\\"High\\",\\"labels\\":[\\"backend\\",\\"api\\"],\\"stories\\":[{\\"asA\\":\\"User\\",\\"iWant\\":\\"feature\\",\\"soThat\\":\\"value\\",\\"title\\":\\"Story 1\\",\\"acceptanceCriteria\\":[\\"AC1\\",\\"AC2\\"],\\"priority\\":\\"Medium\\",\\"labels\\":[\\"frontend\\"],\\"tasks\\":[{\\"title\\":\\"Task 1\\",\\"description\\":\\"Description 1\\"},{\\"title\\":\\"Task 2\\",\\"description\\":\\"Description 2\\"}]},{\\"asA\\":\\"Admin\\",\\"iWant\\":\\"admin feature\\",\\"soThat\\":\\"control\\",\\"title\\":\\"Story 2\\",\\"acceptanceCriteria\\":[\\"AC3\\"],\\"priority\\":\\"Low\\",\\"labels\\":[],\\"tasks\\":[]}]}]"
                  }]
                }
              }]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(mockGeminiResponse)
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));

        // When
        List<Epic> epics = analyzer.generate(cdcContent);

        // Then
        assertNotNull(epics);
        assertEquals(1, epics.size());

        Epic epic = epics.get(0);
        assertEquals(2, epic.getStories().size());
        assertEquals(2, epic.getStories().get(0).getTasks().size());
        assertEquals(0, epic.getStories().get(1).getTasks().size());
    }

    @Test
    @DisplayName("Should handle empty response from Gemini")
    void shouldHandleEmptyResponseFromGemini() throws InterruptedException {
        // Given
        String cdcContent = "Empty content";

        String mockGeminiResponse = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "text": "[]"
                  }]
                }
              }]
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(mockGeminiResponse)
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));

        // When
        List<Epic> epics = analyzer.generate(cdcContent);

        // Then
        assertNotNull(epics);
        assertEquals(0, epics.size());
    }

    @Test
    @DisplayName("Should ask Gemini for a proportional breakdown and an ai-ready verdict")
    void shouldAskForProportionalBreakdownAndAiReadyVerdict() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"[]\"}]}}]}")
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));

        analyzer.generate("Ajouter un filtre par prix");

        String body = mockWebServer.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("1 seul Epic"), "Le prompt doit limiter un besoin court a un Epic");
        assertFalse(body.contains("5-10 User Stories"), "L'ancien minimum fixe de stories ne doit plus etre impose");
        assertTrue(body.contains("aiReady"));
        assertTrue(body.contains("aiReadyReason"));
    }

    @Test
    @DisplayName("Should parse ai-ready verdict from Gemini stories")
    void shouldParseAiReadyVerdict() {
        String mockGeminiResponse = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "text": "[{\\"name\\":\\"Epic: Filtre\\",\\"description\\":\\"Desc\\",\\"stories\\":[{\\"title\\":\\"Filtre prix\\",\\"aiReady\\":true,\\"aiReadyReason\\":\\"Perimetre clair\\",\\"tasks\\":[]},{\\"title\\":\\"Design\\",\\"aiReady\\":false,\\"aiReadyReason\\":\\"Maquette manquante\\",\\"tasks\\":[]}]}]"
                  }]
                }
              }]
            }
            """;
        mockWebServer.enqueue(new MockResponse()
            .setBody(mockGeminiResponse)
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));

        List<Epic> epics = analyzer.generate("Filtre par prix");

        assertEquals(Boolean.TRUE, epics.get(0).getStories().get(0).getAiReady());
        assertEquals("Perimetre clair", epics.get(0).getStories().get(0).getAiReadyReason());
        assertEquals(Boolean.FALSE, epics.get(0).getStories().get(1).getAiReady());
    }

    @Test
    @DisplayName("Should analyze with local Ollama when provider is ollama")
    void shouldAnalyzeWithOllamaWhenConfigured() throws InterruptedException {
        LlmBacklogAnalyzer ollama = new LlmBacklogAnalyzer(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(), "unused", "ollama", "qwen3:8b");
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"model\":\"qwen3:8b\",\"done\":true,\"response\":\"[{\\\"name\\\":\\\"Epic: Filtre\\\",\\\"stories\\\":[{\\\"title\\\":\\\"Filtre prix\\\",\\\"aiReady\\\":true,\\\"tasks\\\":[]}]}]\"}")
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));

        List<Epic> epics = ollama.generate("Ajouter un filtre par prix");

        assertEquals(1, epics.size());
        assertEquals(Boolean.TRUE, epics.get(0).getStories().get(0).getAiReady());
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/api/generate", request.getPath());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"model\":\"qwen3:8b\""));
        assertTrue(body.contains("\"think\":false"));
        assertTrue(body.contains("1 seul Epic"), "Le prompt proportionne doit etre le meme qu'avec Gemini");
    }

    @Test
    @DisplayName("Should fail clearly when Ollama returns an empty response")
    void shouldFailWhenOllamaReturnsEmptyResponse() {
        LlmBacklogAnalyzer ollama = new LlmBacklogAnalyzer(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(), "unused", "ollama", "qwen3:8b");
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"model\":\"qwen3:8b\",\"done\":true,\"response\":\"\"}")
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));

        assertThrows(RuntimeException.class, () -> ollama.generate("Un besoin"));
    }

    @Test
    @DisplayName("Should use correct Gemini model endpoint")
    void shouldUseCorrectGeminiModelEndpoint() throws InterruptedException {
        // Given
        String cdcContent = "Test";

        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"[]\"}]}}]}")
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));

        // When
        analyzer.generate(cdcContent);

        // Then
        RecordedRequest request = mockWebServer.takeRequest();
        String path = request.getPath();

        assertTrue(path.contains("gemini-2.5-flash"),
            "Should use gemini-2.5-flash model, but got: " + path);
        assertTrue(path.contains("generateContent"),
            "Should call generateContent endpoint");
    }

    @Test
    @DisplayName("Sends the Gemini API key in the x-goog-api-key header, never in the URL")
    void sendsGeminiKeyInHeaderNotUrl() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"[]\"}]}}]}")
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));

        analyzer.generate("Test");

        RecordedRequest request = mockWebServer.takeRequest();
        assertFalse(request.getPath().contains("fake-api-key"), request.getPath());
        assertFalse(request.getPath().contains("key="), request.getPath());
        assertEquals("fake-api-key", request.getHeader("x-goog-api-key"));
    }

    @Test
    @DisplayName("A Gemini error never exposes the API key in the exception message")
    void geminiErrorDoesNotLeakKey() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"bad\"}")
            .addHeader("Content-Type", "application/json"));

        RuntimeException error = assertThrows(RuntimeException.class, () -> analyzer.generate("Test"));
        for (Throwable t = error; t != null; t = t.getCause()) {
            assertFalse(String.valueOf(t.getMessage()).contains("fake-api-key"), t.getMessage());
        }
    }

    @Test
    @DisplayName("Should call the configured Gemini model (gemini.model), and ai.verdict.model for a Gemini verdict")
    void shouldUseConfiguredGeminiModels() throws InterruptedException {
        LlmBacklogAnalyzer configured = new LlmBacklogAnalyzer(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(),
            "fake-api-key", "gemini", "gemini-analysis-model", "qwen3:8b", "gemini", "gemini-verdict-model", "");
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"aiReady\\\": false}\"}]}}]}")
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));

        configured.reassess("Titre", "Description", "Precision");

        String path = mockWebServer.takeRequest().getPath();
        assertTrue(path.startsWith("/v1beta/models/gemini-verdict-model:generateContent"), path);

        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"[]\"}]}}]}")
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));

        configured.generate("Un besoin");

        path = mockWebServer.takeRequest().getPath();
        assertTrue(path.startsWith("/v1beta/models/gemini-analysis-model:generateContent"), path);
    }

    @Test
    @DisplayName("assess judges each story on its content (title, description, criteria)")
    void assessStoryJudgesOneStory() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + "\"{\\\"aiReady\\\": true, \\\"aiReadyReason\\\": \\\"Complet\\\"}\"}]}}]}")
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));
        Story story = new Story();
        story.setTitle("Export PDF");
        story.setDescription("Exporter la liste en A4");
        story.setAcceptanceCriteria(List.of("Given une liste, When j'exporte, Then un PDF A4"));

        Readiness verdict = analyzer.assess(List.of(story)).get(0);

        assertEquals(new Readiness(true, "Complet"), verdict);
        String body = mockWebServer.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("Export PDF"), body);
        assertTrue(body.contains("Exporter la liste en A4"), body);
        assertTrue(body.contains("un PDF A4"), body);
    }

    @Test
    @DisplayName("Should reassess a single story with the user's precision")
    void shouldReassessStoryWithPrecision() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + "\"{\\\"aiReady\\\": true, \\\"aiReadyReason\\\": \\\"Le format est maintenant precise\\\"}\"}]}}]}")
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));

        Readiness verdict =
            analyzer.reassess("Export PDF", "Exporter en PDF", "format A4 uniquement");

        assertTrue(verdict.aiReady());
        assertEquals("Le format est maintenant precise", verdict.aiReadyReason());
        String body = mockWebServer.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("format A4 uniquement"));
        assertTrue(body.contains("Export PDF"));
    }

    @Test
    @DisplayName("A missing or non-boolean aiReady is never treated as ready")
    void shouldNotTreatAmbiguousVerdictAsReady() {
        mockWebServer.enqueue(new MockResponse()
            .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + "\"{\\\"aiReady\\\": \\\"yes\\\", \\\"aiReadyReason\\\": \\\"incertain\\\"}\"}]}}]}")
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200));

        Readiness verdict = analyzer.reassess("Export PDF", "Exporter", "precision");

        assertFalse(verdict.aiReady());
        assertEquals("incertain", verdict.aiReadyReason());
    }

    private LlmBacklogAnalyzer analyzerWithLitellmVerdict() {
        return new LlmBacklogAnalyzer(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(),
            "fake-api-key", "ollama", "qwen3:8b", "litellm", "gemini-2.5-flash", "litellm-master-key");
    }

    private MockResponse ollamaStructure(String storyTitle, boolean aiReady) {
        String epics = "[{\"name\":\"Epic: Util\",\"description\":\"d\",\"priority\":\"High\",\"labels\":[\"backend\"],"
            + "\"stories\":[{\"title\":\"" + storyTitle + "\",\"description\":\"Formater une taille\","
            + "\"acceptanceCriteria\":[\"Given 1500, Then '1.5 Ko'\"],\"aiReady\":" + aiReady
            + ",\"aiReadyReason\":\"avis du modele local\",\"tasks\":[]}]}]";
        return new MockResponse()
            .setBody("{\"response\":" + tools.jackson.databind.node.StringNode.valueOf(epics) + "}")
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200);
    }

    private MockResponse litellmVerdict(boolean aiReady, String reason) {
        String content = "{\"aiReady\": " + aiReady + ", \"aiReadyReason\": \"" + reason + "\"}";
        return new MockResponse()
            .setBody("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                + tools.jackson.databind.node.StringNode.valueOf(content) + "}}]}")
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200);
    }

    @Test
    @DisplayName("Ollama builds the structure, the ai-ready verdict comes from the LiteLLM verdict provider")
    void shouldRouteVerdictToLitellmWhileOllamaBuildsStructure() throws InterruptedException {
        LlmBacklogAnalyzer split = analyzerWithLitellmVerdict();
        mockWebServer.enqueue(ollamaStructure("Formater une taille", true));
        mockWebServer.enqueue(litellmVerdict(false, "le systeme d'unites n'est pas precise"));

        List<Epic> epics = split.generate("Besoin : formater une taille de fichier");
        var story = epics.get(0).getStories().get(0);
        Readiness verdict = split.assess(List.of(story)).get(0);

        assertFalse(verdict.aiReady());
        assertEquals("le systeme d'unites n'est pas precise", verdict.aiReadyReason());
        RecordedRequest structure = mockWebServer.takeRequest();
        assertEquals("/api/generate", structure.getPath());
        RecordedRequest verdictCall = mockWebServer.takeRequest();
        assertEquals("/v1/chat/completions", verdictCall.getPath());
        assertEquals("Bearer litellm-master-key", verdictCall.getHeader("Authorization"));
        String body = verdictCall.getBody().readUtf8();
        assertTrue(body.contains("\"model\":\"gemini-2.5-flash\""));
        assertTrue(body.contains("Formater une taille"));
    }

    @Test
    @DisplayName("preciser_story reassessment goes to the verdict provider, never to the structure model")
    void shouldReassessThroughVerdictProvider() throws InterruptedException {
        LlmBacklogAnalyzer split = analyzerWithLitellmVerdict();
        mockWebServer.enqueue(litellmVerdict(true, "unites, arrondi et exemples precises"));

        Readiness verdict = split.reassess("Formater une taille", "desc", "1500 -> '1.5 Ko'");

        assertTrue(verdict.aiReady());
        assertEquals("unites, arrondi et exemples precises", verdict.aiReadyReason());
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/v1/chat/completions", request.getPath());
        assertTrue(request.getBody().readUtf8().contains("1500 -> '1.5 Ko'"));
    }

    @Test
    @DisplayName("A failed verdict call leaves the story awaiting human validation, the analysis still succeeds")
    void shouldFallBackToValidationWhenVerdictCallFails() {
        LlmBacklogAnalyzer split = analyzerWithLitellmVerdict();
        mockWebServer.enqueue(ollamaStructure("Formater une taille", true));
        for (int i = 0; i < 3; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(503).setBody("{\"error\":\"overloaded\"}"));
        }

        List<Epic> epics = split.generate("Besoin : formater une taille de fichier");
        List<Readiness> verdicts = split.assess(epics.get(0).getStories());

        assertEquals(4, mockWebServer.getRequestCount());  // structure + 3 tentatives de verdict, jamais plus
        assertEquals(java.util.Collections.singletonList(null), verdicts);  // pas de verdict : l'application applique la politique par defaut
    }

    @Test
    @DisplayName("A transient 503 from the verdict pool is retried: a fresh key is drawn and the verdict succeeds")
    void shouldRetryTransientVerdictFailure() {
        LlmBacklogAnalyzer split = analyzerWithLitellmVerdict();
        mockWebServer.enqueue(new MockResponse().setResponseCode(503).setBody("{\"error\":\"overloaded\"}"));
        mockWebServer.enqueue(litellmVerdict(true, "unites et arrondi precises"));

        Readiness verdict = split.reassess("Formater une taille", "desc", "1500 -> '1.5 Ko'");

        assertTrue(verdict.aiReady());
        assertEquals(2, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("A verdict proxy that never answers times out once and leaves the story awaiting validation")
    void shouldTimeOutWhenVerdictProxyHangs() {
        LlmBacklogAnalyzer split = analyzerWithLitellmVerdict();
        mockWebServer.enqueue(ollamaStructure("Formater une taille", true));
        mockWebServer.enqueue(new MockResponse()
            .setBodyDelay(5, TimeUnit.SECONDS)
            .setBody("{}")
            .addHeader("Content-Type", "application/json"));
        long start = System.nanoTime();

        List<Epic> epics = split.generate("Besoin : formater une taille de fichier");
        List<Readiness> verdicts = split.assess(epics.get(0).getStories());

        assertEquals(java.util.Collections.singletonList(null), verdicts);
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 4, "a hanging proxy must not block the analysis");
        assertEquals(2, mockWebServer.getRequestCount());  // pas de nouvel essai apres un delai depasse
    }
}
