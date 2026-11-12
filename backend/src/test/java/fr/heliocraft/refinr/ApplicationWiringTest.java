package fr.heliocraft.refinr;

import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.in.CreateEditedBacklog;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.in.ReassessStory;
import fr.heliocraft.refinr.application.port.in.SearchDocuments;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.LlmPort;
import fr.heliocraft.refinr.domain.model.Epic;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contexte Spring complet : cablage des cas d'usage sur les adaptateurs, et comportement de retry (proxies Spring
 * actifs) tel que decrit dans le README : lectures et analyse IA retentees, creation Jira jamais.
 */
@SpringBootTest
class ApplicationWiringTest {

    private static final MockWebServer server = new MockWebServer();

    @DynamicPropertySource
    static void remoteServices(DynamicPropertyRegistry registry) throws IOException {
        server.start();
        registry.add("jira.base-url", () -> server.url("/").toString());
        registry.add("confluence.base-url", () -> server.url("/").toString());
        registry.add("ai.provider", () -> "gemini");
        registry.add("gemini.base-url", () -> server.url("/").toString());
    }

    @AfterAll
    static void stopServer() throws IOException {
        server.shutdown();
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DocumentSourcePort documentSource;

    @Autowired
    private IssueTrackerPort issueTracker;

    @Autowired
    private LlmPort llm;

    @Test
    @DisplayName("Every use case is wired")
    void wiresEveryUseCase() {
        for (Class<?> useCase : List.of(AnalyzeDocument.class, PreviewBacklog.class, CreateBacklog.class, CreateEditedBacklog.class,
                ReassessStory.class, SearchDocuments.class)) {
            assertNotNull(context.getBean(useCase), useCase.getSimpleName());
        }
    }

    @Test
    @DisplayName("Reading a document is retried on HTTP 429")
    void documentReadIsRetried() {
        int before = server.getRequestCount();
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json")
            .setBody("{\"body\":{\"storage\":{\"value\":\"<p>cdc</p>\"}}}"));

        assertEquals("<p>cdc</p>", documentSource.getDocumentContent("42"));
        assertEquals(2, server.getRequestCount() - before);
    }

    @Test
    @DisplayName("Creating an issue is never retried (non-idempotent POST)")
    void issueCreationIsNotRetried() {
        int before = server.getRequestCount();
        server.enqueue(new MockResponse().setResponseCode(500));
        Epic epic = new Epic();
        epic.setName("E");

        assertThrows(RuntimeException.class, () -> issueTracker.createEpic(epic, null));
        assertEquals(1, server.getRequestCount() - before);
    }

    // Comportement documente dans le README (section « Nouveaux essais ») : chaque cas ci-dessous y figure.

    @Test
    @DisplayName("Reading a document is retried on HTTP 500, but not on 503")
    void documentReadRetriesOnlyOn429And500() {
        int before = server.getRequestCount();
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json")
            .setBody("{\"body\":{\"storage\":{\"value\":\"ok\"}}}"));
        assertEquals("ok", documentSource.getDocumentContent("42"));
        assertEquals(2, server.getRequestCount() - before);

        before = server.getRequestCount();
        server.enqueue(new MockResponse().setResponseCode(503));
        assertThrows(RuntimeException.class, () -> documentSource.getDocumentContent("42"));
        assertEquals(1, server.getRequestCount() - before);
    }

    @Test
    @DisplayName("Searching documents is not retried")
    void documentSearchIsNotRetried() {
        int before = server.getRequestCount();
        server.enqueue(new MockResponse().setResponseCode(429));
        assertThrows(RuntimeException.class, () -> documentSource.searchDocuments("SPACE", "t"));
        assertEquals(1, server.getRequestCount() - before);
    }

    @Test
    @DisplayName("The LLM analysis is retried on HTTP 429")
    void llmAnalysisIsRetried() {
        int before = server.getRequestCount();
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json")
            .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"[]\"}]}}]}"));

        assertEquals(List.of(), llm.analyzeDocument("cdc"));
        assertEquals(2, server.getRequestCount() - before);
    }

    @Test
    @DisplayName("A single-story verdict (create-jira, reassess-story) is not retried by Spring")
    void storyVerdictIsNotRetried() {
        int before = server.getRequestCount();
        server.enqueue(new MockResponse().setResponseCode(429));
        assertThrows(RuntimeException.class, () -> llm.reassessStory("S", "D", "P"));
        assertEquals(1, server.getRequestCount() - before);
    }
}
