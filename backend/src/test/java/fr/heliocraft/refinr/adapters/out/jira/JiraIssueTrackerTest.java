// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.jira;

import fr.heliocraft.refinr.application.service.CreateBacklogService;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.TrackedStory;
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

class JiraIssueTrackerTest {

    private MockWebServer mockWebServer;
    private JiraIssueTracker jiraService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();

        // Créer le service avec l'URL mockée
        jiraService = new JiraIssueTracker(
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
        // Le port renvoie la cle sans modifier le modele : c'est le service de creation du backlog qui l'affecte
        assertNull(epic.getTrackerKey());

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
        String taskKey = jiraService.createSubtask(task, storyKey);

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
        JiraIssueTracker sansPriorite = new JiraIssueTracker(
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
        jiraService.createSubtask(task, "TEST-2");

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

        TrackedStory story = jiraService.findStory("TEST-7");

        assertEquals("TEST-7", story.key());
        assertEquals("Export PDF", story.summary());
        assertEquals("Exporter en PDF", story.description());
        assertEquals(List.of("backend", "validation-necessaire"), story.labels());
        assertNull(story.project());
        assertEquals(List.of(), story.subtaskKeys());
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertTrue(request.getPath().startsWith("/rest/api/3/issue/TEST-7"));
        assertTrue(request.getPath().contains("project,subtasks"), request.getPath());
    }

    @Test
    @DisplayName("Should read the project and the sub-tasks of a story (pipeline: tasks created in its project, never twice)")
    void shouldReadProjectAndSubtasks() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"key\":\"OTHER-7\",\"fields\":{\"summary\":\"Export PDF\",\"labels\":[\"ai-ready\"],"
                + "\"project\":{\"key\":\"OTHER\",\"name\":\"Other\"},"
                + "\"subtasks\":[{\"key\":\"OTHER-8\",\"fields\":{}},{\"key\":\"OTHER-9\"}]}}"));

        TrackedStory story = jiraService.findStory("other-7");

        assertEquals("OTHER-7", story.key(), "canonical key returned by Jira (one lock per story)");
        assertEquals("OTHER", story.project());
        assertEquals(List.of("OTHER-8", "OTHER-9"), story.subtaskKeys());
    }

    /** Description ADF comme a la creation : criteres, puis ligne de verdict. */
    private static final String STORY_ADF = "{\"type\":\"doc\",\"version\":1,\"content\":["
            + "{\"type\":\"heading\",\"attrs\":{\"level\":3},\"content\":[{\"type\":\"text\",\"text\":\"Acceptance Criteria\"}]},"
            + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"AC1: Given a, When b, Then c\"}]},"
            + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"AI readiness (ai-ready): clear\"}]}]}";

    @Test
    @DisplayName("C2: test cases inserted before the verdict line, description read again just before the PUT, labels untouched")
    void insertsTestCasesBeforeTheVerdictLine() throws Exception {
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-7\",\"fields\":{\"description\":" + STORY_ADF + "}}"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        assertTrue(jiraService.insertTestCases("TEST-7", "### Test cases\n\n- **TC1 (AC1, nominal):** x — Given a, When b, Then c"));

        RecordedRequest read = mockWebServer.takeRequest();
        assertEquals("GET", read.getMethod());
        assertTrue(read.getPath().contains("fields=description"), read.getPath());
        RecordedRequest put = mockWebServer.takeRequest();
        assertEquals("PUT", put.getMethod());
        String body = put.getBody().readUtf8();
        assertFalse(body.contains("\"labels\""), "only the description is sent: " + body);
        int criteria = body.indexOf("AC1: Given a"), section = body.indexOf("Test cases"), verdict = body.indexOf("AI readiness");
        assertTrue(criteria < section && section < verdict, body);
        assertTrue(body.contains("TC1 (AC1, nominal):"), body);
    }

    /** Description modifiee dans l'interface Jira : criteres en bulletList, « AC1: » en gras, note du PO apres les labels. */
    private static final String JIRA_EDITED_ADF = "{\"type\":\"doc\",\"version\":1,\"content\":["
            + "{\"type\":\"heading\",\"attrs\":{\"level\":3},\"content\":[{\"type\":\"text\",\"text\":\"Acceptance Criteria\"}]},"
            + "{\"type\":\"bulletList\",\"content\":["
            + "{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":["
            + "{\"type\":\"text\",\"text\":\"AC1:\",\"marks\":[{\"type\":\"strong\"}]},"
            + "{\"type\":\"text\",\"text\":\" Given un gestionnaire connecté, When il ouvre le tableau de bord, Then l'occupation est affichée. \"}]}]},"
            + "{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\",\"content\":["
            + "{\"type\":\"text\",\"text\":\"AC2:\",\"marks\":[{\"type\":\"strong\"}]},"
            + "{\"type\":\"text\",\"text\":\" Given une salle libre, When il consulte, Then elle est inoccupée.\"}]}]}]},"
            + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"AI readiness (validation-necessaire): Quels indicateurs ?\"}]},"
            + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Labels:\",\"marks\":[{\"type\":\"strong\"}]},"
            + "{\"type\":\"text\",\"text\":\" frontend\"}]},"
            + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Note du PO : affichage par semaine en priorité.\"}]}]}";

    @Test
    @DisplayName("C2 real trial (Jira): description edited in the Jira UI -> criteria found, section before the verdict, PO note last")
    void jiraEditedDescription() throws Exception {
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-7\",\"fields\":{\"summary\":\"S\",\"labels\":[\"ai-ready\"],"
                + "\"description\":" + JIRA_EDITED_ADF + "}}"));
        TrackedStory story = jiraService.findStory("TEST-7");
        assertEquals(List.of("Given un gestionnaire connecté, When il ouvre le tableau de bord, Then l'occupation est affichée.",
                "Given une salle libre, When il consulte, Then elle est inoccupée."),
                fr.heliocraft.refinr.domain.service.StoryMarkdown.criteria(story.description()));
        mockWebServer.takeRequest();

        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-7\",\"fields\":{\"description\":" + JIRA_EDITED_ADF + "}}"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));
        assertTrue(jiraService.insertTestCases("TEST-7", "### Test cases\n\n- **TC1 (AC1, nominal):** x"));
        mockWebServer.takeRequest();
        String body = mockWebServer.takeRequest().getBody().readUtf8();
        int list = body.indexOf("bulletList"), section = body.indexOf("Test cases"), verdict = body.indexOf("AI readiness");
        int note = body.indexOf("Note du PO");
        assertTrue(list < section && section < verdict && verdict < note, body);
    }

    @Test
    @DisplayName("C2 review: Jira unreachable while writing the test cases -> IssueTrackerException, never the raw error")
    void unreachableJiraIsATrackerError() throws Exception {
        mockWebServer.shutdown();
        fr.heliocraft.refinr.application.port.out.IssueTrackerException error = assertThrows(
                fr.heliocraft.refinr.application.port.out.IssueTrackerException.class,
                () -> jiraService.insertTestCases("TEST-7", "### Test cases\n\n- TC1"));
        assertEquals("Failed to read issue TEST-7: Jira unreachable or unreadable answer", error.getMessage());
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @Test
    @DisplayName("C2: a description that already has the section is left untouched (no PUT); too long: nothing written")
    void testCasesNeverTwiceNorTruncated() {
        String withSection = STORY_ADF.replace("{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"AI readiness",
                "{\"type\":\"heading\",\"attrs\":{\"level\":3},\"content\":[{\"type\":\"text\",\"text\":\"Test cases\"}]},"
                + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"AI readiness");
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-7\",\"fields\":{\"description\":" + withSection + "}}"));
        assertFalse(jiraService.insertTestCases("TEST-7", "### Test cases\n\n- TC1"));
        assertEquals(1, mockWebServer.getRequestCount(), "no PUT");

        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-7\",\"fields\":{\"description\":" + STORY_ADF + "}}"));
        fr.heliocraft.refinr.application.port.out.IssueTrackerException tooLong = assertThrows(
                fr.heliocraft.refinr.application.port.out.IssueTrackerException.class,
                () -> jiraService.insertTestCases("TEST-7", "### Test cases\n\n" + "x".repeat(32_767)));
        assertEquals(fr.heliocraft.refinr.application.port.out.IssueTrackerPort.TEST_CASES_TOO_LONG, tooLong.getMessage());
        assertEquals(2, mockWebServer.getRequestCount(), "still no PUT");
    }

    @Test
    @DisplayName("C2: reassessment of a story whose description has the section: section kept in place, new verdict line at the end")
    void reassessmentKeepsTheSection() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> adf = new tools.jackson.databind.json.JsonMapper().readValue(STORY_ADF.replace(
                "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"AI readiness",
                "{\"type\":\"heading\",\"attrs\":{\"level\":3},\"content\":[{\"type\":\"text\",\"text\":\"Test cases\"}]},"
                + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"TC1 (AC1, nominal): x\"}]},"
                + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"AI readiness"), java.util.Map.class);
        TrackedStory story = new TrackedStory("TEST-7", "Export", "text", List.of("validation-necessaire"), new JiraAdfContent(adf));

        jiraService.applyReassessment(story, "format A4", new Readiness(true, "Now clear"));

        String body = mockWebServer.takeRequest().getBody().readUtf8();
        int section = body.indexOf("Test cases"), testCase = body.indexOf("TC1 (AC1, nominal)");
        int original = body.indexOf("AI readiness (ai-ready): clear"), reassessed = body.indexOf("AI readiness (ai-ready): Now clear");
        assertTrue(section >= 0 && section < testCase && testCase < original && original < reassessed, body);
    }

    private static final String B3_ADF = "{\"type\":\"doc\",\"version\":1,\"content\":["
            + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"AC1: Given a\"}]},"
            + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"AI readiness (validation-necessaire): Which channel?\"}]},"
            + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Note du PO : semaine.\"}]}]}";

    @SuppressWarnings("unchecked")
    private static TrackedStory b3Story(String adf) throws Exception {
        java.util.Map<String, Object> document = new tools.jackson.databind.json.JsonMapper().readValue(adf, java.util.Map.class);
        return new TrackedStory("TEST-7", "S", "text", List.of("validation-necessaire"), new JiraAdfContent(document));
    }

    @Test
    @DisplayName("B3 (Jira): old verdict line struck (strike mark) with the dated note, precision and new line right after, PO note last")
    void b3StrikesTheOldVerdictLine() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        jiraService.applyReassessment(b3Story(B3_ADF), "Email only", new Readiness(true, "Clear now"),
                fr.heliocraft.refinr.domain.model.Language.EN);

        tools.jackson.databind.JsonNode content = new tools.jackson.databind.json.JsonMapper()
                .readTree(mockWebServer.takeRequest().getBody().readUtf8()).at("/fields/description/content");
        assertEquals(5, content.size(), content.toString());
        assertEquals("strike", content.at("/1/content/0/marks/0/type").asString(), "old line struck");
        assertTrue(content.at("/1/content/1/text").asString().matches(" \\(replaced on \\d{4}-\\d{2}-\\d{2}\\)"),
                content.at("/1/content/1").toString());
        assertTrue(content.at("/1/content/1/marks").isMissingNode(), "the note itself is not struck");
        assertEquals("User clarification (reassessment): Email only", content.at("/2/content/0/text").asString());
        assertEquals("AI readiness (ai-ready): Clear now", content.at("/3/content/0/text").asString());
        assertEquals("Note du PO : semaine.", content.at("/4/content/0/text").asString(), "PO note last");
    }

    @Test
    @DisplayName("B3 (Jira): a second reassessment strikes only the current line; French note in French")
    void b3SecondReassessment() throws Exception {
        String once = B3_ADF.replace("{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"AI readiness (validation-necessaire): Which channel?\"}]},",
                "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"AI readiness (validation-necessaire): Which channel?\","
                + "\"marks\":[{\"type\":\"strike\"}]},{\"type\":\"text\",\"text\":\" (remplacée le 2026-11-23)\"}]},"
                + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"AI readiness (validation-necessaire): Which room?\"}]},");
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        jiraService.applyReassessment(b3Story(once), "Salle A", new Readiness(true, "ok"), fr.heliocraft.refinr.domain.model.Language.FR);

        tools.jackson.databind.JsonNode content = new tools.jackson.databind.json.JsonMapper()
                .readTree(mockWebServer.takeRequest().getBody().readUtf8()).at("/fields/description/content");
        assertEquals(1, content.at("/1/content/0/marks").size(), "already struck: unchanged");
        assertEquals(" (remplacée le 2026-11-23)", content.at("/1/content/1/text").asString());
        assertEquals("strike", content.at("/2/content/0/marks/0/type").asString());
        assertTrue(content.at("/2/content/1/text").asString().startsWith(" (remplacée le "));
        assertEquals("Précision de l'utilisateur (réévaluation) : Salle A", content.at("/3/content/0/text").asString());
        assertEquals("AI readiness (ai-ready): ok", content.at("/4/content/0/text").asString());
        assertEquals("Note du PO : semaine.", content.at("/5/content/0/text").asString());
    }

    @Test
    @DisplayName("Should record the user's precision and swap the readiness label")
    void shouldApplyReassessment() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));
        TrackedStory story = new TrackedStory(
            "TEST-7", "Export PDF", "Exporter en PDF", List.of("backend", "validation-necessaire"));

        jiraService.applyReassessment(story, "format A4 uniquement", new Readiness(true, "Perimetre clair"));

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
    @DisplayName("Human validation: verdict line without any precision line, label swapped to ai-ready")
    void shouldApplyHumanValidation() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));
        TrackedStory story = new TrackedStory(
            "TEST-7", "Export PDF", "Exporter en PDF", List.of("backend", "validation-necessaire"));

        jiraService.applyHumanValidation(story, fr.heliocraft.refinr.domain.service.ReadinessPolicy.humanValidation());

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("PUT", request.getMethod());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("Exporter en PDF"));
        assertTrue(body.contains("AI readiness (ai-ready): validée par un humain, sans appel au modèle"));
        assertFalse(body.contains("Precision de l'utilisateur"));
        assertTrue(body.contains("\"ai-ready\""));
        assertFalse(body.contains("\"validation-necessaire\""));
    }

    @Test
    @DisplayName("A still-not-ready story keeps the validation label, never ai-ready")
    void shouldKeepValidationLabelWhenStillNotReady() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));
        TrackedStory story = new TrackedStory(
            "TEST-7", "Export PDF", "Exporter en PDF", List.of("validation-necessaire"));

        jiraService.applyReassessment(story, "format A4", new Readiness(false, "La maquette manque"));

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

        TrackedStory story = jiraService.findStory("TEST-7");
        jiraService.applyReassessment(story, "format A4", new Readiness(false, "La maquette manque"));

        mockWebServer.takeRequest();
        String body = mockWebServer.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"mediaSingle\""));
        assertTrue(body.contains("maquette-123"));
        assertTrue(body.contains("\"strong\""));
        assertTrue(body.contains("Précision de l'utilisateur (réévaluation) : format A4"));
        assertTrue(body.contains("AI readiness (validation-necessaire): La maquette manque"));
    }

    @Test
    @DisplayName("Reassessment refuses tracker content it did not read itself, without calling Jira")
    void shouldRejectForeignTrackerContent() {
        TrackedStory story = new TrackedStory("TEST-7", "Export PDF", "Exporter en PDF",
            List.of("validation-necessaire"), new fr.heliocraft.refinr.domain.model.TrackerContent() {});

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> jiraService.applyReassessment(story, "format A4", new Readiness(false, "La maquette manque")));

        assertTrue(error.getMessage().startsWith("Unsupported tracker content for Jira"));
        assertEquals(0, mockWebServer.getRequestCount());
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
        new CreateBacklogService(jiraService).create(List.of(epic), "DEMO");
        for (int i = 0; i < 3; i++) {
            assertTrue(mockWebServer.takeRequest().getBody().readUtf8().contains("{\"key\":\"DEMO\"}"));
        }

        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-1\"}"));
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-2\"}"));
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-3\"}"));
        new CreateBacklogService(jiraService).create(List.of(epic), " ");
        for (int i = 0; i < 3; i++) {
            assertTrue(mockWebServer.takeRequest().getBody().readUtf8().contains("{\"key\":\"TEST\"}"));
        }
    }

    @Test
    @DisplayName("Story description is sent as structured ADF: headings, bold, acceptance criteria list")
    void storyDescriptionIsStructuredAdf() throws InterruptedException {
        Story story = new Story();
        story.setTitle("Export");
        story.setAsA("user");
        story.setIWant("an export");
        story.setSoThat("I can share");
        story.setDescription("Export **PDF** via `pdf-lib`");
        story.setAcceptanceCriteria(List.of("Given a list, When I export, Then I get a PDF"));
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-2\"}"));

        jiraService.createStory(story, "TEST-1");

        String body = mockWebServer.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("\"heading\""), body);
        assertTrue(body.contains("\"bulletList\""), body);
        assertTrue(body.contains("{\"type\":\"strong\"}"), body);
        assertTrue(body.contains("{\"type\":\"code\"}"), body);
        // Plus de balisage brut (wiki Jira ou Markdown) dans le texte
        assertFalse(body.contains("h3."), body);
        assertFalse(body.contains("*As a*"), body);
        assertFalse(body.contains("**"), body);
    }

    private Story storyWithPoints(Integer points) {
        Story story = new Story();
        story.setTitle("Story");
        story.setEstimatedStoryPoints(points);
        return story;
    }

    @Test
    @DisplayName("Story points are sent in the configured jira.story-points-field")
    void storyPointsSentInConfiguredField() throws InterruptedException {
        JiraIssueTracker withPoints = new JiraIssueTracker(
            mockWebServer.url("/").toString(), "test@example.com", "fake-token", "TEST", true, "customfield_10016");
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-2\"}"));

        withPoints.createStory(storyWithPoints(5), "TEST-1");

        assertTrue(mockWebServer.takeRequest().getBody().readUtf8().contains("\"customfield_10016\":5"));
    }

    @Test
    @DisplayName("Story points are not sent when jira.story-points-field is empty, or when the story has none")
    void storyPointsDisabledWhenFieldEmpty() throws InterruptedException {
        JiraIssueTracker disabled = new JiraIssueTracker(
            mockWebServer.url("/").toString(), "test@example.com", "fake-token", "TEST", true, " ");
        JiraIssueTracker enabled = new JiraIssueTracker(
            mockWebServer.url("/").toString(), "test@example.com", "fake-token", "TEST", true, "customfield_10016");
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-2\"}"));
        mockWebServer.enqueue(jsonResponse("{\"key\":\"TEST-3\"}"));

        disabled.createStory(storyWithPoints(5), "TEST-1");
        enabled.createStory(storyWithPoints(null), "TEST-1");

        assertFalse(mockWebServer.takeRequest().getBody().readUtf8().contains("customfield_"));
        assertFalse(mockWebServer.takeRequest().getBody().readUtf8().contains("customfield_"));
    }

    @Test
    @DisplayName("A Jira rejection becomes an IssueTrackerException carrying the HTTP status and Jira's error messages")
    void jiraRejectionIsReadable() {
        Epic epic = new Epic();
        epic.setName("E");
        mockWebServer.enqueue(new MockResponse().setResponseCode(400).addHeader("Content-Type", "application/json")
            .setBody("{\"errorMessages\":[\"Bad project\"],\"errors\":{\"priority\":\"Field 'priority' cannot be set.\"}}"));

        var error = assertThrows(fr.heliocraft.refinr.application.port.out.IssueTrackerException.class,
            () -> jiraService.createEpic(epic));

        // Client : statut et erreurs de Jira ; logs et traces : statut et forme des erreurs seulement (#33)
        assertEquals("Jira rejected the epic (HTTP 400): Bad project; priority: Field 'priority' cannot be set.",
            error.clientMessage());
        assertEquals("Jira rejected the epic (HTTP 400) [fields: priority; 1 error message]", error.getMessage());
        assertNull(error.getCause());
    }

    @Test
    @DisplayName("An unreachable Jira gives a short IssueTrackerException without the URL")
    void unreachableJira() throws IOException {
        JiraIssueTracker unreachable = new JiraIssueTracker("http://127.0.0.1:1", "u", "t", "TEST");
        Epic epic = new Epic();
        epic.setName("E");

        var error = assertThrows(fr.heliocraft.refinr.application.port.out.IssueTrackerException.class,
            () -> unreachable.createEpic(epic));

        assertEquals("Jira unreachable", error.getMessage());
    }

    @Test
    @DisplayName("Jira is the display name and project keys follow JiraProjectKeys")
    void describesJiraProjects() {
        assertEquals("Jira", jiraService.displayName());
        assertTrue(jiraService.acceptsProjectId("PROJ"));
        assertFalse(jiraService.acceptsProjectId("mon-projet"));
        assertFalse(jiraService.acceptsProjectId(null));
    }

    @Test
    @DisplayName("Web address of a Jira issue: {site}/browse/{KEY}, without double slash")
    void issueUrl() {
        JiraIssueTracker withSlash = new JiraIssueTracker("https://your-site.atlassian.net/", "u", "t", "PROJ");
        assertEquals("https://your-site.atlassian.net/browse/PROJ-42", withSlash.issueUrl("PROJ-42"));
        assertNull(withSlash.issueUrl(null));
    }
}
