package fr.heliocraft.refinr.jira;

import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JiraServiceTest {

    private MockWebServer mockWebServer;
    private JiraService jiraService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();

        // Créer le service avec l'URL mockée
        jiraService = new JiraService(
            baseUrl,
            "test@example.com",
            "fake-token",
            "TEST"
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
            .setBody(body)
            .addHeader("Content-Type", "application/json")
            .setResponseCode(201);
    }

    @Test
    @DisplayName("Should create Epic successfully")
    void shouldCreateEpicSuccessfully() throws InterruptedException {
        // Given
        Epic epic = new Epic();
        epic.setName("Epic Test");
        epic.setDescription("Epic description");
        epic.setPriority("High");
        epic.setLabels(List.of("backend", "api"));

        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-1\",\"id\":\"10001\"}"));

        // When
        String epicKey = jiraService.createEpic(epic);

        // Then
        assertEquals("TEST-1", epicKey);
        assertEquals("TEST-1", epic.getTrackerKey());

        // Verify request
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertTrue(request.getPath().contains("/rest/api/3/issue"));
        assertTrue(request.getBody().readUtf8().contains("Epic Test"));
    }

    @Test
    @DisplayName("Should clean labels by removing spaces")
    void shouldCleanLabelsRemovingSpaces() throws InterruptedException {
        // Given
        Epic epic = new Epic();
        epic.setName("Epic Test");
        epic.setDescription("Test");
        epic.setLabels(List.of("auth service", "API Gateway"));

        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-1\"}"));

        // When
        jiraService.createEpic(epic);

        // Then
        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("auth-service"));
        assertTrue(body.contains("api-gateway"));
        assertFalse(body.contains("auth service"));
    }

    @Test
    @DisplayName("Should throw exception when Epic creation fails")
    void shouldThrowExceptionWhenEpicCreationFails() {
        // Given
        Epic epic = new Epic();
        epic.setName("Epic Test");

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(400)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"errorMessages\":[\"Bad Request\"]}"));

        // When/Then
        assertThrows(RuntimeException.class, () -> jiraService.createEpic(epic));
    }

    @Test
    @DisplayName("Should create Story with parent Epic")
    void shouldCreateStoryWithParentEpic() throws InterruptedException {
        // Given
        String epicKey = "TEST-1";
        Story story = new Story();
        story.setTitle("Story Title");
        story.setAsA("User");
        story.setIWant("Feature");
        story.setSoThat("Value");
        story.setPriority("Medium");
        story.setLabels(List.of("frontend"));

        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-2\"}"));

        // When
        String storyKey = jiraService.createStory(story, epicKey);

        // Then
        assertEquals("TEST-2", storyKey);

        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"parent\""));
        assertTrue(body.contains("TEST-1"));
    }

    @Test
    @DisplayName("Should create Subtask with parent Story")
    void shouldCreateSubtaskWithParentStory() throws InterruptedException {
        // Given
        String storyKey = "TEST-2";
        Task task = new Task();
        task.setTitle("Task name");
        task.setDescription("Task description");

        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-3\"}"));

        // When
        String taskKey = jiraService.createTask(task, storyKey);

        // Then
        assertEquals("TEST-3", taskKey);

        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"Subtask\""));
        assertTrue(body.contains("\"parent\""));
        assertTrue(body.contains("TEST-2"));
    }

    @Test
    @DisplayName("Should send priority by default (company-managed projects)")
    void shouldSendPriorityByDefault() throws InterruptedException {
        Epic epic = new Epic();
        epic.setName("Epic Test");
        epic.setPriority("High");

        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-1\"}"));

        jiraService.createEpic(epic);

        String body = mockWebServer.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"priority\""));
    }

    @Test
    @DisplayName("Should omit priority on Epic and Story when disabled (team-managed projects)")
    void shouldOmitPriorityWhenDisabled() throws InterruptedException {
        JiraService sansPriorite = new JiraService(
            mockWebServer.url("/").toString(), "test@example.com", "fake-token", "TEST", false);

        Epic epic = new Epic();
        epic.setName("Epic Test");
        epic.setPriority("High");
        Story story = new Story();
        story.setTitle("Story Title");
        story.setPriority("Medium");

        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-1\"}"));
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-2\"}"));

        sansPriorite.createEpic(epic);
        sansPriorite.createStory(story, "TEST-1");

        assertFalse(mockWebServer.takeRequest().getBody().readUtf8().contains("\"priority\""));
        assertFalse(mockWebServer.takeRequest().getBody().readUtf8().contains("\"priority\""));
    }

    @Test
    @DisplayName("Should label story ai-ready only when analysis explicitly says so")
    void shouldLabelStoryAiReadyOnlyWhenFlagged() throws InterruptedException {
        Story ready = new Story();
        ready.setTitle("Ready");
        ready.setAiReady(true);
        ready.setAiReadyReason("Perimetre clair");
        ready.setLabels(List.of("backend"));
        Story unclear = new Story();
        unclear.setTitle("Unclear");
        unclear.setAiReady(false);
        Story unknown = new Story();
        unknown.setTitle("Unknown");

        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-2\"}"));
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-3\"}"));
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-4\"}"));

        jiraService.createStory(ready, "TEST-1");
        jiraService.createStory(unclear, "TEST-1");
        jiraService.createStory(unknown, "TEST-1");

        String readyBody = mockWebServer.takeRequest().getBody().readUtf8();
        assertTrue(readyBody.contains("\"ai-ready\""));
        assertTrue(readyBody.contains("\"backend\""));
        assertFalse(readyBody.contains("validation-necessaire"));
        assertTrue(readyBody.contains("Perimetre clair"));

        String unclearBody = mockWebServer.takeRequest().getBody().readUtf8();
        assertTrue(unclearBody.contains("\"validation-necessaire\""));
        assertFalse(unclearBody.contains("\"ai-ready\""));

        String unknownBody = mockWebServer.takeRequest().getBody().readUtf8();
        assertTrue(unknownBody.contains("\"validation-necessaire\""));
        assertFalse(unknownBody.contains("\"ai-ready\""));
    }

    @Test
    @DisplayName("Should not let analysis labels spoof the ai-ready label")
    void shouldIgnoreReadinessLabelsComingFromAnalysis() throws InterruptedException {
        Story story = new Story();
        story.setTitle("Spoof");
        story.setAiReady(false);
        story.setLabels(List.of("ai-ready"));

        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-2\"}"));

        jiraService.createStory(story, "TEST-1");

        String body = mockWebServer.takeRequest().getBody().readUtf8();
        assertFalse(body.contains("\"ai-ready\""));
        assertTrue(body.contains("\"validation-necessaire\""));
    }

    @Test
    @DisplayName("Should never put readiness labels from the analysis on Epics or Subtasks")
    void shouldStripReadinessLabelsFromEpicsAndSubtasks() throws InterruptedException {
        Epic epic = new Epic();
        epic.setName("Epic Test");
        epic.setLabels(List.of("AI Ready", "backend", "Validation Necessaire"));
        Task task = new Task();
        task.setTitle("Task name");
        task.setLabels(List.of("ai-ready", "api"));

        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-1\"}"));
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-3\"}"));

        jiraService.createEpic(epic);
        jiraService.createTask(task, "TEST-2");

        String epicBody = mockWebServer.takeRequest().getBody().readUtf8();
        assertFalse(epicBody.contains("\"ai-ready\""));
        assertFalse(epicBody.contains("\"validation-necessaire\""));
        assertTrue(epicBody.contains("\"backend\""));
        String taskBody = mockWebServer.takeRequest().getBody().readUtf8();
        assertFalse(taskBody.contains("\"ai-ready\""));
        assertTrue(taskBody.contains("\"api\""));
    }

    @Test
    @DisplayName("Should convert description to ADF format")
    void shouldConvertDescriptionToADFFormat() throws InterruptedException {
        // Given
        Epic epic = new Epic();
        epic.setName("Test");
        epic.setDescription("Simple description");

        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-1\"}"));

        // When
        jiraService.createEpic(epic);

        // Then
        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"type\":\"doc\""));
        assertTrue(body.contains("\"type\":\"paragraph\""));
        assertTrue(body.contains("\"type\":\"text\""));
        assertTrue(body.contains("Simple description"));
    }

    @Test
    @DisplayName("Should read a story to reassess: summary, flattened description and labels")
    void shouldReadStoryForReview() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"key\":\"TEST-7\",\"fields\":{\"summary\":\"Export PDF\","
                + "\"labels\":[\"backend\",\"validation-necessaire\"],"
                + "\"description\":{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"paragraph\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"Exporter en PDF\"}]}]}}}"));

        JiraService.StoryForReview story = jiraService.getStoryForReview("TEST-7");

        assertEquals("TEST-7", story.key());
        assertEquals("Export PDF", story.summary());
        assertEquals("Exporter en PDF", story.description());
        assertEquals(List.of("backend", "validation-necessaire"), story.labels());
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertTrue(request.getPath().startsWith("/rest/api/3/issue/TEST-7"));
    }

    @Test
    @DisplayName("Should record the user's precision and swap the readiness label")
    void shouldApplyReassessment() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));
        JiraService.StoryForReview story = new JiraService.StoryForReview(
            "TEST-7", "Export PDF", "Exporter en PDF", List.of("backend", "validation-necessaire"));

        jiraService.applyReassessment(story, "format A4 uniquement", true, "Perimetre clair");

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("PUT", request.getMethod());
        assertTrue(request.getPath().endsWith("/rest/api/3/issue/TEST-7"));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("Exporter en PDF"));
        assertTrue(body.contains("format A4 uniquement"));
        assertTrue(body.contains("AI readiness (ai-ready): Perimetre clair"));
        assertTrue(body.contains("\"ai-ready\""));
        assertTrue(body.contains("\"backend\""));
        assertFalse(body.contains("\"validation-necessaire\""));
    }

    @Test
    @DisplayName("A still-not-ready story keeps the validation label, never ai-ready")
    void shouldKeepValidationLabelWhenStillNotReady() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));
        JiraService.StoryForReview story = new JiraService.StoryForReview(
            "TEST-7", "Export PDF", "Exporter en PDF", List.of("validation-necessaire"));

        jiraService.applyReassessment(story, "format A4", false, "La maquette manque");

        String body = mockWebServer.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"validation-necessaire\""));
        assertFalse(body.contains("\"ai-ready\""));
        assertTrue(body.contains("AI readiness (validation-necessaire): La maquette manque"));
    }

    @Test
    @DisplayName("Reassessment appends to the original rich description instead of flattening it")
    void shouldKeepOriginalRichDescription() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"key\":\"TEST-7\",\"fields\":{\"summary\":\"Export PDF\",\"labels\":[\"validation-necessaire\"],"
                + "\"description\":{\"type\":\"doc\",\"version\":1,\"content\":["
                + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Voir la maquette\","
                + "\"marks\":[{\"type\":\"strong\"}]}]},"
                + "{\"type\":\"mediaSingle\",\"content\":[{\"type\":\"media\","
                + "\"attrs\":{\"id\":\"maquette-123\",\"type\":\"file\",\"collection\":\"jira\"}}]}]}}}"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        JiraService.StoryForReview story = jiraService.getStoryForReview("TEST-7");
        jiraService.applyReassessment(story, "format A4", false, "La maquette manque");

        mockWebServer.takeRequest();
        String body = mockWebServer.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"mediaSingle\""));
        assertTrue(body.contains("maquette-123"));
        assertTrue(body.contains("\"strong\""));
        assertTrue(body.contains("Precision de l'utilisateur (reevaluation): format A4"));
        assertTrue(body.contains("AI readiness (validation-necessaire): La maquette manque"));
    }

    @Test
    @DisplayName("Should create the hierarchy in the requested project, default project otherwise")
    void shouldCreateHierarchyInRequestedProject() throws InterruptedException {
        Task task = new Task();
        task.setTitle("Tache");
        Story story = new Story();
        story.setTitle("Story");
        story.setTasks(List.of(task));
        Epic epic = new Epic();
        epic.setName("Epic");
        epic.setDescription("d");
        epic.setStories(List.of(story));

        mockWebServer.enqueue(jsonResponse("{\"key\":\"DEMO-1\"}"));
        mockWebServer.enqueue(jsonResponse("{\"key\":\"DEMO-2\"}"));
        mockWebServer.enqueue(jsonResponse("{\"key\":\"DEMO-3\"}"));
        jiraService.createFullHierarchy(List.of(epic), "DEMO");
        for (int i = 0; i < 3; i++) {
            assertTrue(mockWebServer.takeRequest().getBody().readUtf8().contains("{\"key\":\"DEMO\"}"));
        }

        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-1\"}"));
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-2\"}"));
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-3\"}"));
        jiraService.createFullHierarchy(List.of(epic), " ");
        for (int i = 0; i < 3; i++) {
            assertTrue(mockWebServer.takeRequest().getBody().readUtf8().contains("{\"key\":\"TEST\"}"));
        }
    }
}
