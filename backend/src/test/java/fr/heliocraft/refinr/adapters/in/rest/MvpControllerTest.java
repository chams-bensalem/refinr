package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.LlmPort;
import fr.heliocraft.refinr.config.UseCaseConfig;
import fr.heliocraft.refinr.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Controleur + vrais cas d'usage (UseCaseConfig) ; seuls les ports sortants sont simules
@WebMvcTest(MvpController.class)
@Import(UseCaseConfig.class)
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

    @MockitoBean
    private DocumentSourcePort confluenceService;

    @MockitoBean
    private LlmPort geminiAnalyzer;

    @MockitoBean
    private IssueTrackerPort jiraService;

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

        when(confluenceService.getDocumentContent(pageId)).thenReturn(cdcContent);
        when(geminiAnalyzer.analyzeDocument(cdcContent)).thenReturn(testEpics);
        when(jiraService.createEpic(any(Epic.class), any())).thenReturn("PROJ-1");
        when(jiraService.createStory(any(Story.class), anyString(), any())).thenReturn("PROJ-2");
        when(jiraService.createSubtask(any(Task.class), anyString(), any())).thenReturn("PROJ-3");

        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"" + pageId + "\"}"))
                .andExpect(status().isOk());

        // Verify interactions
        verify(confluenceService).getDocumentContent(pageId);
        verify(geminiAnalyzer).analyzeDocument(cdcContent);
        verify(jiraService).createEpic(any(Epic.class), isNull());
        verify(jiraService).createStory(any(Story.class), eq("PROJ-1"), isNull());
        verify(jiraService).createSubtask(any(Task.class), eq("PROJ-2"), isNull());
    }

    @Test
    @DisplayName("Should create the Jira issues in the requested project")
    void shouldCreateInRequestedProject() throws Exception {
        when(confluenceService.getDocumentContent("42")).thenReturn("cdc");
        when(geminiAnalyzer.analyzeDocument("cdc")).thenReturn(testEpics);
        when(jiraService.createEpic(any(Epic.class), any())).thenReturn("DEMO-1");
        when(jiraService.createStory(any(Story.class), anyString(), any())).thenReturn("DEMO-2");

        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"42\",\"projectKey\":\"DEMO\"}"))
                .andExpect(status().isOk());

        verify(jiraService).createEpic(any(Epic.class), eq("DEMO"));
        verify(jiraService).createStory(any(Story.class), eq("DEMO-1"), eq("DEMO"));
        verify(jiraService).createSubtask(any(Task.class), eq("DEMO-2"), eq("DEMO"));
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
        when(confluenceService.getDocumentContent(null))
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
        when(confluenceService.getDocumentContent(pageId))
            .thenThrow(new RuntimeException("Confluence API error"));

        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"" + pageId + "\"}"))
                .andExpect(status().is5xxServerError());

        verify(confluenceService).getDocumentContent(pageId);
        verifyNoInteractions(geminiAnalyzer);
        verifyNoInteractions(jiraService);
    }

    @Test
    @DisplayName("Should handle Gemini analyzer error")
    void shouldHandleGeminiAnalyzerError() throws Exception {
        // Given
        String pageId = "123456";
        String cdcContent = "Test content";

        when(confluenceService.getDocumentContent(pageId)).thenReturn(cdcContent);
        when(geminiAnalyzer.analyzeDocument(cdcContent))
            .thenThrow(new RuntimeException("Gemini API error"));

        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"" + pageId + "\"}"))
                .andExpect(status().is5xxServerError());

        verify(confluenceService).getDocumentContent(pageId);
        verify(geminiAnalyzer).analyzeDocument(cdcContent);
        verifyNoInteractions(jiraService);
    }

    @Test
    @DisplayName("Should handle Jira service error")
    void shouldHandleJiraServiceError() throws Exception {
        // Given
        String pageId = "123456";
        String cdcContent = "Test content";

        when(confluenceService.getDocumentContent(pageId)).thenReturn(cdcContent);
        when(geminiAnalyzer.analyzeDocument(cdcContent)).thenReturn(testEpics);
        when(jiraService.createEpic(any(Epic.class), any()))
            .thenThrow(new RuntimeException("Jira API error"));

        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"" + pageId + "\"}"))
                .andExpect(status().is5xxServerError());

        verify(confluenceService).getDocumentContent(pageId);
        verify(geminiAnalyzer).analyzeDocument(cdcContent);
        verify(jiraService).createEpic(any(Epic.class), isNull());
        // L'Epic a echoue : rien n'est cree en dessous
        verify(jiraService, never()).createStory(any(), any(), any());
        verify(jiraService, never()).createSubtask(any(), any(), any());
    }

    @Test
    @DisplayName("Should handle empty analysis result")
    void shouldHandleEmptyAnalysisResult() throws Exception {
        // Given
        String pageId = "123456";
        String cdcContent = "Empty content";
        List<Epic> emptyEpics = List.of();

        when(confluenceService.getDocumentContent(pageId)).thenReturn(cdcContent);
        when(geminiAnalyzer.analyzeDocument(cdcContent)).thenReturn(emptyEpics);

        // When/Then
        mockMvc.perform(post("/api/v1/mvp/analyze-and-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"" + pageId + "\"}"))
                .andExpect(status().isOk());

        verify(confluenceService).getDocumentContent(pageId);
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
    @DisplayName("Confluence search returns typed document summaries, not the raw Confluence payload")
    void searchReturnsDocumentSummaries() throws Exception {
        when(confluenceService.searchDocuments("SPACE", "cahier")).thenReturn(List.of(
            new DocumentSummary("111", "Cahier des charges", "https://your-site.atlassian.net/wiki/spaces/SPACE/pages/111")));

        mockMvc.perform(get("/api/v1/mvp/confluence/search").param("spaceKey", "SPACE").param("title", "cahier"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].id").value("111"))
                .andExpect(jsonPath("$.results[0].title").value("Cahier des charges"))
                .andExpect(jsonPath("$.results[0].url").value("https://your-site.atlassian.net/wiki/spaces/SPACE/pages/111"))
                .andExpect(jsonPath("$.results[0].body").doesNotExist());
    }

    @Test
    @DisplayName("Confluence search answers 400 on invalid input")
    void searchRejectsInvalidInput() throws Exception {
        when(confluenceService.searchDocuments(any(), any()))
            .thenThrow(new IllegalArgumentException("Invalid spaceKey"));

        mockMvc.perform(get("/api/v1/mvp/confluence/search").param("spaceKey", "SPACE OR x").param("title", "t"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Invalid spaceKey"));
    }

    @Test
    @DisplayName("Errors never return the raw exception message to the client")
    void errorsDoNotLeakExceptionMessages() throws Exception {
        String secret = "401 from https://internal.example.net/rest?token=abc";
        when(confluenceService.getDocumentContent(any())).thenThrow(new RuntimeException(secret));
        when(confluenceService.searchDocuments(any(), any())).thenThrow(new RuntimeException(secret));
        when(jiraService.findStory(any())).thenThrow(new RuntimeException(secret));

        for (var request : List.of(
                post("/api/v1/mvp/analyze-and-create").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"confluencePageId\":\"1\"}"),
                post("/api/v1/mvp/analyze-preview").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"confluencePageId\":\"1\"}"),
                post("/api/v1/mvp/reassess-story").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"issueKey\":\"TEST-1\",\"precision\":\"p\"}"),
                get("/api/v1/mvp/confluence/search").param("spaceKey", "SPACE").param("title", "t"))) {
            String body = mockMvc.perform(request)
                    .andExpect(status().is5xxServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("internal.example.net"), body);
            assertFalse(body.contains("token=abc"), body);
        }
    }

    private static final String CREATE_BODY = "[{\"name\":\"E\",\"stories\":[{\"title\":\"S\",\"tasks\":[{\"title\":\"T\"}]}]}]";

    @Test
    @DisplayName("create-jira creates the issues in the optional projectKey")
    void createJiraInRequestedProject() throws Exception {
        when(jiraService.createEpic(any(Epic.class), any())).thenReturn("DEMO-1");
        when(jiraService.createStory(any(Story.class), anyString(), any())).thenReturn("DEMO-2");
        when(jiraService.createSubtask(any(Task.class), anyString(), any())).thenReturn("DEMO-3");

        mockMvc.perform(post("/api/v1/mvp/create-jira").param("projectKey", "DEMO")
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isOk());

        verify(jiraService).createEpic(any(Epic.class), eq("DEMO"));
        verify(jiraService).createStory(any(Story.class), eq("DEMO-1"), eq("DEMO"));
        verify(jiraService).createSubtask(any(Task.class), eq("DEMO-2"), eq("DEMO"));
    }

    @Test
    @DisplayName("create-jira without projectKey uses the default project")
    void createJiraInDefaultProject() throws Exception {
        when(jiraService.createEpic(any(Epic.class), any())).thenReturn("TEST-1");
        when(jiraService.createStory(any(Story.class), anyString(), any())).thenReturn("TEST-2");

        mockMvc.perform(post("/api/v1/mvp/create-jira")
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isOk());

        verify(jiraService).createEpic(any(Epic.class), isNull());
    }

    @Test
    @DisplayName("create-jira rejects an invalid projectKey with 400, like analyze-and-create")
    void createJiraRejectsInvalidProjectKey() throws Exception {
        mockMvc.perform(post("/api/v1/mvp/create-jira").param("projectKey", "demo; drop")
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Invalid projectKey: demo; drop"));

        verifyNoInteractions(jiraService);
    }

    @Test
    @DisplayName("A failure during creation gives a 207 partial response listing created and failed issues")
    void partialCreationResponse() throws Exception {
        when(jiraService.createEpic(any(Epic.class), any())).thenReturn("TEST-1");
        when(jiraService.createStory(any(Story.class), anyString(), any()))
            .thenThrow(new fr.heliocraft.refinr.application.port.out.IssueTrackerException(
                "Rejected by Jira (HTTP 400): priority: Field 'priority' cannot be set"));

        mockMvc.perform(post("/api/v1/mvp/create-jira")
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.created.length()").value(1))
                .andExpect(jsonPath("$.created[0].type").value("epic"))
                .andExpect(jsonPath("$.created[0].title").value("E"))
                .andExpect(jsonPath("$.created[0].key").value("TEST-1"))
                .andExpect(jsonPath("$.failed.length()").value(2))
                .andExpect(jsonPath("$.failed[0].type").value("story"))
                .andExpect(jsonPath("$.failed[0].reason").value("Rejected by Jira (HTTP 400): priority: Field 'priority' cannot be set"))
                .andExpect(jsonPath("$.failed[1].type").value("subtask"))
                .andExpect(jsonPath("$.epics[0].jiraKey").value("TEST-1"));
    }

    @Test
    @DisplayName("When nothing could be created the response is 502 with the failures")
    void nothingCreatedResponse() throws Exception {
        when(jiraService.createEpic(any(Epic.class), any()))
            .thenThrow(new fr.heliocraft.refinr.application.port.out.IssueTrackerException("Jira unreachable"));

        mockMvc.perform(post("/api/v1/mvp/create-jira")
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.created.length()").value(0))
                .andExpect(jsonPath("$.failed.length()").value(3))
                .andExpect(jsonPath("$.failed[0].reason").value("Jira unreachable"));
    }

    @Test
    @DisplayName("A complete creation answers 200 with the created issues and no failure")
    void completeCreationResponse() throws Exception {
        when(jiraService.createEpic(any(Epic.class), any())).thenReturn("TEST-1");
        when(jiraService.createStory(any(Story.class), anyString(), any())).thenReturn("TEST-2");
        when(jiraService.createSubtask(any(Task.class), anyString(), any())).thenReturn("TEST-3");

        when(confluenceService.getDocumentContent("1")).thenReturn("cdc");
        when(geminiAnalyzer.analyzeDocument("cdc")).thenReturn(testEpics);

        mockMvc.perform(post("/api/v1/mvp/analyze-and-create").contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.created.length()").value(3))
                .andExpect(jsonPath("$.failed.length()").value(0))
                .andExpect(jsonPath("$.epics[0].stories[0].tasks[0].jiraKey").value("TEST-3"));
    }

    @Test
    @DisplayName("create-jira recomputes the verdict on the final story content and ignores aiReady sent by the client")
    void createJiraRecomputesVerdict() throws Exception {
        when(geminiAnalyzer.assessStory(any(Story.class))).thenReturn(new Readiness(false, "Critere flou"));
        when(jiraService.createEpic(any(Epic.class), any())).thenReturn("TEST-1");
        when(jiraService.createStory(any(Story.class), anyString(), any())).thenReturn("TEST-2");

        mockMvc.perform(post("/api/v1/mvp/create-jira").contentType(MediaType.APPLICATION_JSON)
                .content("[{\"name\":\"E\",\"stories\":[{\"title\":\"S\",\"description\":\"modifiee\","
                    + "\"aiReady\":true,\"aiReadyReason\":\"verdict de l'apercu\"}]}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epics[0].stories[0].aiReady").value(false))
                .andExpect(jsonPath("$.epics[0].stories[0].aiReadyReason").value("Critere flou"));

        ArgumentCaptor<Story> sent = ArgumentCaptor.forClass(Story.class);
        verify(geminiAnalyzer).assessStory(argThat(story -> "modifiee".equals(story.getDescription())));
        verify(jiraService).createStory(sent.capture(), eq("TEST-1"), isNull());
        assertEquals(Boolean.FALSE, sent.getValue().getAiReady());
        assertEquals("Critere flou", sent.getValue().getAiReadyReason());
    }

    @Test
    @DisplayName("The debug endpoint test-issue-types no longer exists")
    void debugIssueTypesEndpointIsGone() throws Exception {
        mockMvc.perform(post("/api/v1/mvp/test-issue-types"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(jiraService);
    }

    @Test
    @DisplayName("Should reassess a story awaiting validation with the user's precision")
    void shouldReassessStoryAwaitingValidation() throws Exception {
        TrackedStory story = new TrackedStory(
            "TEST-7", "Export PDF", "Exporter", List.of("backend", "validation-necessaire"));
        when(jiraService.findStory("TEST-7")).thenReturn(story);
        when(geminiAnalyzer.reassessStory("Export PDF", "Exporter", "format A4"))
            .thenReturn(new Readiness(true, "Perimetre clair"));

        mockMvc.perform(post("/api/v1/mvp/reassess-story")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"issueKey\":\"TEST-7\",\"precision\":\"format A4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.issueKey").value("TEST-7"))
                .andExpect(jsonPath("$.aiReady").value(true))
                .andExpect(jsonPath("$.aiReadyReason").value("Perimetre clair"));

        verify(jiraService).applyReassessment(story, "format A4", new Readiness(true, "Perimetre clair"));
    }

    @Test
    @DisplayName("Should refuse to reassess a story that is not awaiting validation")
    void shouldRefuseReassessOutsideValidation() throws Exception {
        when(jiraService.findStory("TEST-7")).thenReturn(
            new TrackedStory("TEST-7", "Export PDF", "Exporter", List.of("ai-ready")));

        mockMvc.perform(post("/api/v1/mvp/reassess-story")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"issueKey\":\"TEST-7\",\"precision\":\"format A4\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(geminiAnalyzer);
        verify(jiraService, never()).applyReassessment(any(), any(), any());
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
