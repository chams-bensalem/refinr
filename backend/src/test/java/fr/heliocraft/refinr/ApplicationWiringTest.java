// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr;

import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.CheckHealth;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.in.CreateEditedBacklog;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.in.ReassessStory;
import fr.heliocraft.refinr.application.port.in.SearchDocuments;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
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
        registry.add("refinr.llm.analysis.provider", () -> "google");
        registry.add("refinr.llm.analysis.model", () -> "gemini-2.5-flash");
        registry.add("refinr.llm.analysis.api-key", () -> "test-api-key");
        registry.add("refinr.llm.analysis.base-url", () -> server.url("/").toString());
        registry.add("refinr.llm.analysis.retry-base-delay", () -> "10ms");
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
    private BacklogGeneratorPort generator;

    @Autowired
    private ReadinessAssessorPort assessor;

    @Test
    @DisplayName("Every use case is wired")
    void wiresEveryUseCase() {
        for (Class<?> useCase : List.of(AnalyzeDocument.class, PreviewBacklog.class, CreateBacklog.class, CreateEditedBacklog.class,
                ReassessStory.class, SearchDocuments.class, CheckHealth.class)) {
            assertNotNull(context.getBean(useCase), useCase.getSimpleName());
        }
    }

    @Test
    @DisplayName("The health check covers confluence, jira and the analysis provider, in that order")
    void healthCheckComponents() {
        var names = context.getBean(CheckHealth.class).check().components().stream()
                .map(fr.heliocraft.refinr.domain.model.ComponentHealth::name).toList();
        assertEquals(List.of("confluence", "jira", "ai"), names);
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
            .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"epics\\\":[]}\"}]}}]}"));

        assertEquals(List.of(), generator.generate("cdc"));
        assertEquals(2, server.getRequestCount() - before);
    }

    @Test
    @DisplayName("A verdict call (create-jira, reassess-story) is retried on HTTP 429 like the analysis")
    void storyVerdictIsRetried() {
        int before = server.getRequestCount();
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json")
            .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + "\"{\\\"aiReady\\\":true,\\\"aiReadyReason\\\":\\\"ok\\\"}\"}]}}]}"));

        assertEquals(true, assessor.reassess("S", "D", "P").aiReady());
        assertEquals(2, server.getRequestCount() - before);
    }

    @Test
    @DisplayName("A rejected key (HTTP 401) is never retried")
    void rejectedKeyIsNotRetried() {
        int before = server.getRequestCount();
        server.enqueue(new MockResponse().setResponseCode(401));
        assertThrows(RuntimeException.class, () -> assessor.reassess("S", "D", "P"));
        assertEquals(1, server.getRequestCount() - before);
    }
}
