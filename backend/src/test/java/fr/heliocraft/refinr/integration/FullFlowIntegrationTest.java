// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.integration;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Parcours complet HTTP → Confluence → IA (Gemini) → Jira, contexte Spring reel et trois serveurs HTTP simules :
 * aucun acces reseau ni identifiant reel. Les serveurs sont partages par la classe (leur URL est figee au demarrage du
 * contexte) : la file de reponses et les compteurs de requetes sont remis a zero avant chaque test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FullFlowIntegrationTest {

    private static final MockWebServer confluence = new MockWebServer();
    private static final MockWebServer gemini = new MockWebServer();
    private static final MockWebServer jira = new MockWebServer();

    private static int confluenceBaseline;
    private static int geminiBaseline;
    private static int jiraBaseline;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void startServers() throws IOException {
        confluence.start();
        gemini.start();
        jira.start();
    }

    @AfterAll
    static void stopServers() throws IOException {
        confluence.shutdown();
        gemini.shutdown();
        jira.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("confluence.base-url", () -> confluence.url("/").toString());
        registry.add("confluence.username", () -> "test@example.com");
        registry.add("confluence.token", () -> "fake-token");
        registry.add("jira.base-url", () -> jira.url("/").toString());
        registry.add("jira.username", () -> "test@example.com");
        registry.add("jira.token", () -> "fake-token");
        registry.add("jira.project-key", () -> "TEST");
        registry.add("refinr.llm.analysis.provider", () -> "google");
        registry.add("refinr.llm.analysis.model", () -> "gemini-2.5-flash");
        registry.add("refinr.llm.analysis.api-key", () -> "fake-api-key");
        registry.add("refinr.llm.analysis.base-url", () -> gemini.url("/").toString());
        registry.add("refinr.llm.analysis.retry-base-delay", () -> "10ms");
    }

    /** Oublie les reponses non consommees par le test precedent et repart de zero pour les compteurs. */
    @BeforeEach
    void resetServers() {
        for (MockWebServer server : new MockWebServer[] {confluence, gemini, jira}) {
            server.setDispatcher(new QueueDispatcher());
        }
        confluenceBaseline = confluence.getRequestCount();
        geminiBaseline = gemini.getRequestCount();
        jiraBaseline = jira.getRequestCount();
    }

    private static int confluenceCalls() {
        return confluence.getRequestCount() - confluenceBaseline;
    }

    private static int geminiCalls() {
        return gemini.getRequestCount() - geminiBaseline;
    }

    private static int jiraCalls() {
        return jira.getRequestCount() - jiraBaseline;
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).addHeader("Content-Type", "application/json").setBody(body);
    }

    private static void confluencePage(String html) {
        confluence.enqueue(json(200, "{\"id\":\"123456\",\"body\":{\"storage\":{\"value\":\"" + html + "\"}}}"));
    }

    /** Reponse Gemini dont le texte est le backlog JSON donne (echappe pour tenir dans une chaine JSON). */
    private static void geminiBacklog(String backlogJson) {
        String text = backlogJson.replace("\\", "\\\\").replace("\"", "\\\"");
        gemini.enqueue(json(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + text + "\"}]},"
                + "\"finishReason\":\"STOP\"}]}"));
    }

    private static String story(String title, String... tasks) {
        StringBuilder taskJson = new StringBuilder();
        for (String task : tasks) {
            taskJson.append(taskJson.isEmpty() ? "" : ",").append("{\"title\":\"").append(task).append("\",\"description\":\"d\"}");
        }
        return "{\"title\":\"" + title + "\",\"description\":\"d\",\"asA\":\"User\",\"iWant\":\"a feature\",\"soThat\":\"a value\","
                + "\"acceptanceCriteria\":[\"Given a, When b, Then c\"],\"priority\":\"High\",\"labels\":[\"api\"],"
                + "\"aiReady\":false,\"aiReadyReason\":\"r\",\"tasks\":[" + taskJson + "]}";
    }

    private static String epic(String name, String... stories) {
        return "{\"name\":\"" + name + "\",\"description\":\"d\",\"priority\":\"High\",\"labels\":[\"backend\"],"
                + "\"stories\":[" + String.join(",", stories) + "]}";
    }

    private static void jiraCreates(int issues) {
        for (int i = 1; i <= issues; i++) {
            jira.enqueue(json(201, "{\"key\":\"TEST-" + i + "\",\"id\":\"1000" + i + "\"}"));
        }
    }

    @Test
    @DisplayName("Full flow: Confluence → Gemini → Jira creation (Epic, Story, Subtask)")
    void fullFlowConfluenceToGeminiToJira() throws Exception {
        confluencePage("<h1>Refinr</h1><p>Inventory module</p>");
        geminiBacklog("{\"epics\":[" + epic("Epic: Inventory", story("Track stock levels", "Create the stock table")) + "]}");
        jiraCreates(3);

        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confluencePageId\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Successfully created 3 issues"))
                .andExpect(jsonPath("$.created", hasSize(3)))
                .andExpect(jsonPath("$.epics[0].jiraKey").value("TEST-1"));

        Assertions.assertEquals(1, confluenceCalls(), "Should call Confluence once");
        Assertions.assertEquals(1, geminiCalls(), "Should call Gemini once");
        Assertions.assertEquals(3, jiraCalls(), "Should call Jira 3 times (Epic, Story, Subtask)");

        // The Gemini key travels in a header, never in the URL
        RecordedRequest geminiRequest = gemini.takeRequest(1, TimeUnit.SECONDS);
        Assertions.assertNotNull(geminiRequest);
        Assertions.assertEquals("fake-api-key", geminiRequest.getHeader("x-goog-api-key"));
        Assertions.assertFalse(geminiRequest.getPath().contains("fake-api-key"), geminiRequest.getPath());
    }

    @Test
    @DisplayName("Confluence failure: generic error, neither the AI nor Jira is called")
    void shouldHandleConfluenceAPIFailure() throws Exception {
        confluence.enqueue(json(404, "{\"message\":\"Page not found\"}"));

        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confluencePageId\":\"999999\"}"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.correlationId").exists());

        Assertions.assertEquals(1, confluenceCalls(), "404 is not retried");
        Assertions.assertEquals(0, geminiCalls());
        Assertions.assertEquals(0, jiraCalls());
    }

    @Test
    @DisplayName("AI failure: 502 after the 2 retries, Jira is not called")
    void shouldHandleGeminiAPIFailure() throws Exception {
        confluencePage("<p>Content</p>");
        for (int i = 0; i < 3; i++) {
            gemini.enqueue(json(500, "{\"error\":\"Internal error\"}"));
        }

        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confluencePageId\":\"123\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));

        Assertions.assertEquals(1, confluenceCalls());
        Assertions.assertEquals(3, geminiCalls(), "1 call + 2 retries on HTTP 500");
        Assertions.assertEquals(0, jiraCalls());
    }

    @Test
    @DisplayName("Jira failure: the rejected Epic is not retried, its children are reported as not created (502)")
    void shouldHandleJiraAPIFailure() throws Exception {
        confluencePage("<p>Content</p>");
        geminiBacklog("{\"epics\":[" + epic("Epic 1", story("Story 1", "Task 1")) + "]}");
        jira.enqueue(json(400, "{\"errorMessages\":[\"Bad request\"]}"));

        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confluencePageId\":\"123\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.created", hasSize(0)))
                .andExpect(jsonPath("$.failed", hasSize(3)))
                .andExpect(jsonPath("$.failed[0].type").value("epic"));

        Assertions.assertEquals(1, confluenceCalls());
        Assertions.assertEquals(1, geminiCalls());
        Assertions.assertEquals(1, jiraCalls(), "Issue creation is never retried (non-idempotent POST)");
    }

    @Test
    @DisplayName("Should create multiple Epics, Stories, and Subtasks")
    void shouldCreateMultipleEpicsStoriesAndSubtasks() throws Exception {
        confluencePage("<p>Large project</p>");
        geminiBacklog("{\"epics\":["
                + epic("Epic 1", story("Story 1", "T1", "T2"), story("Story 2", "T3")) + ","
                + epic("Epic 2", story("Story 3", "T4")) + "]}");
        jiraCreates(9); // 2 Epics + 3 Stories + 4 Subtasks

        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confluencePageId\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", hasSize(9)));

        Assertions.assertEquals(9, jiraCalls(), "Should create 2 Epics + 3 Stories + 4 Subtasks");
    }
}
