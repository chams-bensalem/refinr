package fr.heliocraft.refinr.integration;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
//TODO The integration test can be re-enabled later after refactoring to use @MockBean for services instead of relying on @DynamicPropertySource to inject mock server URLs
//  before bean creation.
@org.junit.jupiter.api.Disabled("Temporarily disabled - needs refactoring for @DynamicPropertySource timing issues")
class FullFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static MockWebServer mockConfluenceServer;
    private static MockWebServer mockGeminiServer;
    private static MockWebServer mockJiraServer;

    @BeforeAll
    static void setUpServers() throws IOException {
        mockConfluenceServer = new MockWebServer();
        mockConfluenceServer.start();

        mockGeminiServer = new MockWebServer();
        mockGeminiServer.start();

        mockJiraServer = new MockWebServer();
        mockJiraServer.start();
    }

    @AfterAll
    static void tearDownServers() throws IOException {
        mockConfluenceServer.shutdown();
        mockGeminiServer.shutdown();
        mockJiraServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("confluence.base-url", () -> mockConfluenceServer.url("/").toString());
        registry.add("confluence.username", () -> "test@example.com");
        registry.add("confluence.token", () -> "fake-token");
        registry.add("jira.base-url", () -> mockJiraServer.url("/").toString());
        registry.add("jira.username", () -> "test@example.com");
        registry.add("jira.token", () -> "fake-token");
        registry.add("jira.project-key", () -> "TEST");
        registry.add("refinr.llm.analysis.provider", () -> "google");
        registry.add("refinr.llm.analysis.model", () -> "gemini-2.5-flash");
        registry.add("refinr.llm.analysis.api-key", () -> "fake-api-key");
        registry.add("refinr.llm.analysis.base-url", () -> mockGeminiServer.url("/").toString());
        registry.add("refinr.llm.analysis.retry-base-delay", () -> "10ms");
    }

    @Test
    @DisplayName("Full flow: Confluence → Gemini → Jira creation")
    void fullFlowConfluenceToGeminiToJira() throws Exception {
        // Given - Mock Confluence response
        String confluenceResponse = """
            {
              "id": "123456",
              "body": {
                "storage": {
                  "value": "<h1>Refinr</h1><p>Inventory module</p>"
                }
              }
            }
            """;
        mockConfluenceServer.enqueue(new MockResponse()
                .setBody(confluenceResponse)
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        // Given - Mock Gemini response
        String geminiResponse = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "text": "{\\"epics\\":[{\\"name\\":\\"Epic: Inventaire\\",\\"description\\":\\"Inventory management\\",\\"priority\\":\\"High\\",\\"labels\\":[\\"backend\\"],\\"stories\\":[{\\"description\\":\\"d\\",\\"aiReady\\":false,\\"aiReadyReason\\":\\"r\\",\\"asA\\":\\"Developer\\",\\"iWant\\":\\"track stock levels\\",\\"soThat\\":\\"avoid stock-outs\\",\\"title\\":\\"Track Stock Story\\",\\"acceptanceCriteria\\":[\\"AC1\\"],\\"priority\\":\\"High\\",\\"labels\\":[\\"api\\"],\\"tasks\\":[{\\"title\\":\\"Setup GitLab\\",\\"description\\":\\"Configure OAuth\\"}]}]}]}"
                  }]
                }
              }]
            }
            """;
        mockGeminiServer.enqueue(new MockResponse()
                .setBody(geminiResponse)
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        // Given - Mock Jira responses (Epic, Story, Subtask)
        mockJiraServer.enqueue(new MockResponse()
                .setBody("{\"key\":\"TEST-1\",\"id\":\"10001\"}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(201));  // Epic creation

        mockJiraServer.enqueue(new MockResponse()
                .setBody("{\"key\":\"TEST-2\",\"id\":\"10002\"}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(201));  // Story creation

        mockJiraServer.enqueue(new MockResponse()
                .setBody("{\"key\":\"TEST-3\",\"id\":\"10003\"}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(201));  // Subtask creation

        // When - Call the endpoint
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confluencePageId\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("created successfully")));

        // Then - Verify all servers received requests
        Assertions.assertEquals(1, mockConfluenceServer.getRequestCount(), "Should call Confluence once");
        Assertions.assertEquals(1, mockGeminiServer.getRequestCount(), "Should call Gemini once");
        Assertions.assertEquals(3, mockJiraServer.getRequestCount(), "Should call Jira 3 times (Epic, Story, Subtask)");
    }

    @Test
    @DisplayName("Should handle Confluence API failure gracefully")
    void shouldHandleConfluenceAPIFailure() throws Exception {
        // Given - Confluence returns error
        mockConfluenceServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Page not found\"}"));

        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confluencePageId\":\"999999\"}"))
                .andExpect(status().is5xxServerError());

        // Verify no other APIs were called
        Assertions.assertEquals(1, mockConfluenceServer.getRequestCount());
        Assertions.assertEquals(0, mockGeminiServer.getRequestCount());
        Assertions.assertEquals(0, mockJiraServer.getRequestCount());
    }

    @Test
    @DisplayName("Should handle Gemini API failure gracefully")
    void shouldHandleGeminiAPIFailure() throws Exception {
        // Given - Confluence succeeds
        mockConfluenceServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"123\",\"body\":{\"storage\":{\"value\":\"<p>Content</p>\"}}}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        // Given - Gemini fails (HTTP 500 on the first try and on the 2 new attempts)
        for (int i = 0; i < 3; i++) {
            mockGeminiServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .addHeader("Content-Type", "application/json")
                    .setBody("{\"error\":\"Internal error\"}"));
        }

        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confluencePageId\":\"123\"}"))
                .andExpect(status().is5xxServerError());

        // Verify Jira was not called
        Assertions.assertEquals(1, mockConfluenceServer.getRequestCount());
        Assertions.assertEquals(3, mockGeminiServer.getRequestCount(), "1 call + 2 new attempts on HTTP 500");
        Assertions.assertEquals(0, mockJiraServer.getRequestCount());
    }

    @Test
    @DisplayName("Should handle Jira API failure gracefully")
    void shouldHandleJiraAPIFailure() throws Exception {
        // Given - Confluence succeeds
        mockConfluenceServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"123\",\"body\":{\"storage\":{\"value\":\"<p>Content</p>\"}}}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        // Given - Gemini succeeds
        mockGeminiServer.enqueue(new MockResponse()
                .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"epics\\\":[]}\"}]}}]}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        // Given - Jira fails
        mockJiraServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"errorMessages\":[\"Bad request\"]}"));

        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confluencePageId\":\"123\"}"))
                .andExpect(status().isOk());  // Empty result, no error

        Assertions.assertEquals(1, mockConfluenceServer.getRequestCount());
        Assertions.assertEquals(3, mockGeminiServer.getRequestCount(), "1 call + 2 new attempts on HTTP 500");
        Assertions.assertEquals(0, mockJiraServer.getRequestCount());  // No epics to create
    }

    @Test
    @DisplayName("Should create multiple Epics, Stories, and Subtasks")
    void shouldCreateMultipleEpicsStoriesAndSubtasks() throws Exception {
        // Given - Confluence
        mockConfluenceServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"123\",\"body\":{\"storage\":{\"value\":\"<p>Large project</p>\"}}}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        // Given - Gemini with 2 Epics, each with 2 Stories, each with 2 Tasks
        String geminiResponse = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "text": "{\\"epics\\":[{\\"name\\":\\"Epic 1\\",\\"description\\":\\"D1\\",\\"priority\\":\\"High\\",\\"labels\\":[],\\"stories\\":[{\\"description\\":\\"d\\",\\"aiReady\\":false,\\"aiReadyReason\\":\\"r\\",\\"asA\\":\\"U\\",\\"iWant\\":\\"F\\",\\"soThat\\":\\"V\\",\\"title\\":\\"Story 1\\",\\"acceptanceCriteria\\":[],\\"priority\\":\\"High\\",\\"labels\\":[],\\"tasks\\":[{\\"title\\":\\"T1\\",\\"description\\":\\"D1\\"},{\\"title\\":\\"T2\\",\\"description\\":\\"D2\\"}]},{\\"description\\":\\"d\\",\\"aiReady\\":false,\\"aiReadyReason\\":\\"r\\",\\"asA\\":\\"U\\",\\"iWant\\":\\"F\\",\\"soThat\\":\\"V\\",\\"title\\":\\"Story 2\\",\\"acceptanceCriteria\\":[],\\"priority\\":\\"High\\",\\"labels\\":[],\\"tasks\\":[{\\"title\\":\\"T3\\",\\"description\\":\\"D3\\"}]}]},{\\"name\\":\\"Epic 2\\",\\"description\\":\\"D2\\",\\"priority\\":\\"Medium\\",\\"labels\\":[],\\"stories\\":[{\\"description\\":\\"d\\",\\"aiReady\\":false,\\"aiReadyReason\\":\\"r\\",\\"asA\\":\\"U\\",\\"iWant\\":\\"F\\",\\"soThat\\":\\"V\\",\\"title\\":\\"Story 3\\",\\"acceptanceCriteria\\":[],\\"priority\\":\\"Medium\\",\\"labels\\":[],\\"tasks\\":[{\\"title\\":\\"T4\\",\\"description\\":\\"D4\\"}]}]}]}"
                  }]
                }
              }]
            }
            """;
        mockGeminiServer.enqueue(new MockResponse()
                .setBody(geminiResponse)
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        // Given - Jira responses (2 Epics + 3 Stories + 4 Subtasks = 9 total)
        for (int i = 1; i <= 9; i++) {
            mockJiraServer.enqueue(new MockResponse()
                    .setBody("{\"key\":\"TEST-" + i + "\"}")
                    .addHeader("Content-Type", "application/json")
                    .setResponseCode(201));
        }

        // When
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confluencePageId\":\"123\"}"))
                .andExpect(status().isOk());

        // Then
        Assertions.assertEquals(9, mockJiraServer.getRequestCount(),
                "Should create 2 Epics + 3 Stories + 4 Subtasks");
    }
}
