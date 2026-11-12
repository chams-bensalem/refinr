package fr.heliocraft.refinr;

import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.in.ReassessStory;
import fr.heliocraft.refinr.application.port.in.SearchDocuments;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
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
 * Contexte Spring complet : cablage des cas d'usage sur les adaptateurs, et comportement de retry tel qu'il etait
 * avant le passage en hexagonal (caracterisation ; sa correction est prevue en phase 4).
 */
@SpringBootTest
class ApplicationWiringTest {

    private static final MockWebServer server = new MockWebServer();

    @DynamicPropertySource
    static void remoteServices(DynamicPropertyRegistry registry) throws IOException {
        server.start();
        registry.add("jira.base-url", () -> server.url("/").toString());
        registry.add("confluence.base-url", () -> server.url("/").toString());
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

    @Test
    @DisplayName("Every use case is wired")
    void wiresEveryUseCase() {
        for (Class<?> useCase : List.of(AnalyzeDocument.class, PreviewBacklog.class, CreateBacklog.class,
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
}
