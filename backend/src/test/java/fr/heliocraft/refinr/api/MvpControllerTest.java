package fr.heliocraft.refinr.api;

import fr.heliocraft.refinr.analyzer.GeminiDocumentAnalyzer;
import fr.heliocraft.refinr.confluence.ConfluenceService;
import fr.heliocraft.refinr.jira.JiraService;
import fr.heliocraft.refinr.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MvpController.class)
@TestPropertySource(properties = {
    "jira.base-url=http://localhost:8080",
    "jira.username=test@example.com",
    "jira.token=test-token",
    "jira.project-key=TEST",
    "confluence.base-url=http://localhost:8080",
    "confluence.username=test@example.com",
    "confluence.token=test-token",
    "gemini.api-key=test-api-key"
})
class MvpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConfluenceService confluenceService;

    @MockBean
    private GeminiDocumentAnalyzer geminiAnalyzer;

    @MockBean
    private JiraService jiraService;

    private Epic testEpic;
    private Story testStory;
    private Task testTask;
    private List<Epic> testEpics;

    @BeforeEach
    void setUp() {
        // Setup test data
        testTask = new Task();
        testTask.setTitle("Test Task");
        testTask.setDescription("Task description");

        testStory = new Story();
        testStory.setTitle("Test Story");
        testStory.setAsA("Developer");
        testStory.setIWant("feature");
        testStory.setSoThat("value");
        testStory.setAcceptanceCriteria(List.of("AC1", "AC2"));
        testStory.setPriority("High");
        testStory.setLabels(List.of("backend"));
        testStory.setTasks(List.of(testTask));

        testEpic = new Epic();
        testEpic.setName("Epic: Inventaire");
        testEpic.setDescription("Inventory management module");
        testEpic.setPriority("High");
        testEpic.setLabels(List.of("backend", "api"));
        testEpic.setStories(List.of(testStory));

        testEpics = List.of(testEpic);
    }

    @Test
    @DisplayName("Should analyze and create Jira issues successfully")
    void shouldAnalyzeAndCreateJiraIssuesSuccessfully() throws Exception {
        // Given
        String pageId = "123456";
        String cdcContent = "# Refinr CDC\nModule Inventaire";

        when(confluenceService.getPageContent(pageId)).thenReturn(cdcContent);
        when(geminiAnalyzer.analyzeDocument(cdcContent)).thenReturn(testEpics);
        when(jiraService.createEpic(any(Epic.class))).thenReturn("PROJ-1");
        when(jiraService.createStory(any(Story.class), anyString())).thenReturn("PROJ-2");
        when(jiraService.createTask(any(Task.class), anyString())).thenReturn("PROJ-3");

        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"" + pageId + "\"}"))
                .andExpect(status().isOk());

        // Verify interactions
        verify(confluenceService).getPageContent(pageId);
        verify(geminiAnalyzer).analyzeDocument(cdcContent);
        verify(jiraService).createFullHierarchy(anyList(), isNull());
    }

    @Test
    @DisplayName("Should create the Jira issues in the requested project")
    void shouldCreateInRequestedProject() throws Exception {
        when(confluenceService.getPageContent("42")).thenReturn("cdc");
        when(geminiAnalyzer.analyzeDocument("cdc")).thenReturn(testEpics);

        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"42\",\"projectKey\":\"DEMO\"}"))
                .andExpect(status().isOk());

        verify(jiraService).createFullHierarchy(anyList(), eq("DEMO"));
    }

    @Test
    @DisplayName("Should reject an invalid projectKey before reading Confluence")
    void shouldRejectInvalidProjectKey() throws Exception {
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"42\",\"projectKey\":\"demo; drop\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(confluenceService, jiraService);
    }

    @Test
    @DisplayName("Should handle missing confluencePageId")
    void shouldHandleMissingConfluencePageId() throws Exception {
        // Given - when confluencePageId is null, the service should fail
        when(confluenceService.getPageContent(null))
            .thenThrow(new RuntimeException("Page ID is required"));

        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("Should handle Confluence service error")
    void shouldHandleConfluenceServiceError() throws Exception {
        // Given
        String pageId = "123456";
        when(confluenceService.getPageContent(pageId))
            .thenThrow(new RuntimeException("Confluence API error"));

        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"" + pageId + "\"}"))
                .andExpect(status().is5xxServerError());

        verify(confluenceService).getPageContent(pageId);
        verifyNoInteractions(geminiAnalyzer);
        verifyNoInteractions(jiraService);
    }

    @Test
    @DisplayName("Should handle Gemini analyzer error")
    void shouldHandleGeminiAnalyzerError() throws Exception {
        // Given
        String pageId = "123456";
        String cdcContent = "Test content";

        when(confluenceService.getPageContent(pageId)).thenReturn(cdcContent);
        when(geminiAnalyzer.analyzeDocument(cdcContent))
            .thenThrow(new RuntimeException("Gemini API error"));

        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"" + pageId + "\"}"))
                .andExpect(status().is5xxServerError());

        verify(confluenceService).getPageContent(pageId);
        verify(geminiAnalyzer).analyzeDocument(cdcContent);
        verifyNoInteractions(jiraService);
    }

    @Test
    @DisplayName("Should handle Jira service error")
    void shouldHandleJiraServiceError() throws Exception {
        // Given
        String pageId = "123456";
        String cdcContent = "Test content";

        when(confluenceService.getPageContent(pageId)).thenReturn(cdcContent);
        when(geminiAnalyzer.analyzeDocument(cdcContent)).thenReturn(testEpics);
        doThrow(new RuntimeException("Jira API error"))
            .when(jiraService).createFullHierarchy(anyList(), any());

        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"" + pageId + "\"}"))
                .andExpect(status().is5xxServerError());

        verify(confluenceService).getPageContent(pageId);
        verify(geminiAnalyzer).analyzeDocument(cdcContent);
        verify(jiraService).createFullHierarchy(anyList(), isNull());
    }

    @Test
    @DisplayName("Should handle empty analysis result")
    void shouldHandleEmptyAnalysisResult() throws Exception {
        // Given
        String pageId = "123456";
        String cdcContent = "Empty content";
        List<Epic> emptyEpics = List.of();

        when(confluenceService.getPageContent(pageId)).thenReturn(cdcContent);
        when(geminiAnalyzer.analyzeDocument(cdcContent)).thenReturn(emptyEpics);

        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"" + pageId + "\"}"))
                .andExpect(status().isOk());

        verify(confluenceService).getPageContent(pageId);
        verify(geminiAnalyzer).analyzeDocument(cdcContent);
    }

    @Test
    @DisplayName("Should handle invalid JSON request")
    void shouldHandleInvalidJSONRequest() throws Exception {
        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("invalid json"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Should return health status")
    void shouldReturnHealthStatus() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/mvp/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Should reassess a story awaiting validation with the user's precision")
    void shouldReassessStoryAwaitingValidation() throws Exception {
        JiraService.StoryForReview story = new JiraService.StoryForReview(
            "TEST-7", "Export PDF", "Exporter", List.of("backend", "validation-necessaire"));
        when(jiraService.getStoryForReview("TEST-7")).thenReturn(story);
        when(geminiAnalyzer.reassessStory("Export PDF", "Exporter", "format A4"))
            .thenReturn(new GeminiDocumentAnalyzer.Readiness(true, "Perimetre clair"));

        mockMvc.perform(post("/api/v1/mvp/reassess-story")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"issueKey\":\"TEST-7\",\"precision\":\"format A4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.issueKey").value("TEST-7"))
                .andExpect(jsonPath("$.aiReady").value(true))
                .andExpect(jsonPath("$.aiReadyReason").value("Perimetre clair"));

        verify(jiraService).applyReassessment(story, "format A4", true, "Perimetre clair");
    }

    @Test
    @DisplayName("Should refuse to reassess a story that is not awaiting validation")
    void shouldRefuseReassessOutsideValidation() throws Exception {
        when(jiraService.getStoryForReview("TEST-7")).thenReturn(
            new JiraService.StoryForReview("TEST-7", "Export PDF", "Exporter", List.of("ai-ready")));

        mockMvc.perform(post("/api/v1/mvp/reassess-story")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"issueKey\":\"TEST-7\",\"precision\":\"format A4\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(geminiAnalyzer);
        verify(jiraService, never()).applyReassessment(any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("Should require an issue key and a precision")
    void shouldRequireIssueKeyAndPrecision() throws Exception {
        mockMvc.perform(post("/api/v1/mvp/reassess-story")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"issueKey\":\"TEST-7\",\"precision\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(jiraService);
        verifyNoInteractions(geminiAnalyzer);
    }
}
