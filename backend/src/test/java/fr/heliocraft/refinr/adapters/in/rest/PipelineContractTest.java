// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.adapters.out.confluence.ConfluenceIds;
import fr.heliocraft.refinr.adapters.out.jira.JiraProjectKeys;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort.FeatureLevel;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.application.port.out.TaskGeneratorPort;
import fr.heliocraft.refinr.config.JobConfiguration;
import fr.heliocraft.refinr.config.UseCaseConfig;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Chaine C1 dans le contrat REST : {@code "pipeline": true} (synchrone et jobs), {@code questions}, sous-taches des
 * seules stories pretes, {@code story-tasks} et {@code createTasks}. Sans l'option : reponses inchangees. Vrais cas
 * d'usage ; seuls les ports sortants sont simules.
 */
@WebMvcTest({BacklogController.class, JobController.class})
@Import({UseCaseConfig.class, JobConfiguration.class})
@TestPropertySource(properties = {"jira.base-url=http://localhost:8080", "jira.project-key=TEST"})
@ExtendWith(OutputCaptureExtension.class)
class PipelineContractTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SPEC = "Customers export their paid orders as CSV.";
    private static final String QUESTION = "Which CSV separator is expected?";

    @Autowired MockMvc mockMvc;
    @MockitoBean DocumentSourcePort source;
    @MockitoBean BacklogGeneratorPort generator;
    @MockitoBean ReadinessAssessorPort assessor;
    @MockitoBean TaskGeneratorPort taskGenerator;
    @MockitoBean fr.heliocraft.refinr.application.port.out.TestCaseGeneratorPort testCaseGenerator;
    @MockitoBean IssueTrackerPort tracker;

    private static Story story(String title) {
        Story story = new Story();
        story.setTitle(title);
        story.setDescription("Secret-marker-c1 " + title);
        story.setAiReady(true);
        story.setAiReadyReason("generation verdict");
        return story;
    }

    private static Task task(String title) {
        Task task = new Task();
        task.setTitle(title);
        return task;
    }

    /** Trois stories : la premiere prete, les deux autres bloquees par la meme question. */
    private static List<Epic> backlog() {
        Epic epic = new Epic();
        epic.setName("Orders");
        epic.setStories(new ArrayList<>(List.of(story("Export"), story("Separator"), story("Encoding"))));
        return new ArrayList<>(List.of(epic));
    }

    @BeforeEach
    void stubs() {
        when(source.displayName()).thenReturn("Confluence");
        when(source.acceptsDocumentId(any())).thenAnswer(call -> ConfluenceIds.isPageId(call.getArgument(0)));
        when(tracker.displayName()).thenReturn("Jira");
        when(tracker.acceptsProjectId(any())).thenAnswer(call -> JiraProjectKeys.isValid(call.getArgument(0)));
        when(tracker.featureLevel(any())).thenReturn(FeatureLevel.NONE);
        when(tracker.previewFeatureLevel(any())).thenReturn(FeatureLevel.NONE);
        when(tracker.createEpic(any(), any())).thenReturn("TEST-1");
        when(tracker.createStory(any(), anyString(), any())).thenReturn("TEST-2", "TEST-3", "TEST-4");
        when(tracker.createSubtask(any(), anyString(), any())).thenReturn("TEST-5", "TEST-6");
        when(generator.generate(any(), any())).thenAnswer(call -> backlog());
        when(generator.generateStories(any(), any())).thenAnswer(call -> backlog());
        when(assessor.assess(anyList(), anyList())).thenReturn(List.of(new Readiness(true, "clear"),
                new Readiness(false, QUESTION), new Readiness(false, QUESTION.toLowerCase())));
        when(taskGenerator.generateTasks(anyList(), anyList(), any())).thenReturn(List.of(List.of(task("API"), task("Tests"))));
    }

    private MockHttpServletResponse send(String path, String body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    private static JsonNode read(MockHttpServletResponse response) throws Exception {
        return JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode finished(String id) throws Exception {
        JsonNode job;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            Thread.sleep(10);
            job = read(mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/jobs/" + id)).andReturn().getResponse());
        } while (!job.get("status").asString().equals("succeeded") && System.nanoTime() < deadline);
        assertEquals("succeeded", job.get("status").asString(), job.toString());
        return job;
    }

    private JsonNode result(String id) throws Exception {
        return read(mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/jobs/" + id + "/result")).andReturn().getResponse());
    }

    @Test
    @DisplayName("Without pipeline: unchanged (generation-v2, no questions field, no task call)")
    void withoutPipelineUnchanged() throws Exception {
        JsonNode body = read(send("/api/v1/analyze-preview", "{\"content\":\"" + SPEC + "\"}"));
        assertFalse(body.has("questions"), body.toString());
        verify(generator).generate(any(), any());
        verify(generator, never()).generateStories(any(), any());
        verifyNoInteractions(taskGenerator, assessor);

        JsonNode explicitFalse = read(send("/api/v1/analyze-preview", "{\"content\":\"" + SPEC + "\",\"pipeline\":false}"));
        assertFalse(explicitFalse.has("questions"));
    }

    @Test
    @DisplayName("Preview with pipeline: prompt 2 verdict, tasks on the ready story only, one grouped question")
    void previewWithPipeline() throws Exception {
        JsonNode body = read(send("/api/v1/analyze-preview", "{\"content\":\"" + SPEC + "\",\"pipeline\":true}"));

        JsonNode stories = body.at("/epics/0/stories");
        assertEquals(2, stories.get(0).get("tasks").size(), body.toString());
        assertEquals(0, stories.get(1).get("tasks").size());
        assertEquals(0, stories.get(2).get("tasks").size());
        assertEquals(QUESTION, stories.get(1).get("aiReadyReason").asString(), "prompt 2 verdict");
        assertEquals(1, body.get("questions").size(), "exact duplicates grouped");
        assertEquals(QUESTION, body.at("/questions/0/question").asString());
        assertEquals(List.of("Separator", "Encoding"), List.of(body.at("/questions/0/stories/0/title").asString(),
                body.at("/questions/0/stories/1/title").asString()));
        assertTrue(body.at("/questions/0/stories/0/key").isNull(), "no key before creation");
        assertFalse(body.has("warnings"));
        verify(generator, never()).generate(any(), any());
    }

    @Test
    @DisplayName("Pipeline as text \"true\" accepted; any other value: 400 naming the option, value never echoed")
    void pipelineValues() throws Exception {
        assertTrue(read(send("/api/v1/analyze-preview", "{\"content\":\"" + SPEC + "\",\"pipeline\":\"true\"}")).has("questions"));
        for (String path : List.of("/api/v1/analyze-preview", "/api/v1/analyze-and-create", "/api/v1/jobs/analyze-preview",
                "/api/v1/jobs/analyze-and-create")) {
            MockHttpServletResponse response = send(path, "{\"content\":\"" + SPEC + "\",\"pipeline\":\"zz-injected\"}");
            assertEquals(400, response.getStatus(), path);
            assertEquals("pipeline must be true or false", read(response).get("error").asString());
        }
    }

    @Test
    @DisplayName("Ready story whose tasks the model did not give: warning pointing to story-tasks")
    void readyWithoutTasksWarning() throws Exception {
        when(taskGenerator.generateTasks(anyList(), anyList(), any())).thenReturn(Arrays.asList((List<Task>) null));
        JsonNode body = read(send("/api/v1/analyze-preview", "{\"content\":\"" + SPEC + "\",\"pipeline\":true}"));
        assertEquals(BacklogResponses.readyWithoutTasksWarning(1), body.at("/warnings/0").asString(), body.toString());
    }

    @Test
    @DisplayName("analyze-and-create with pipeline: sub-tasks created for the ready story only; questions carry the keys")
    void analyzeAndCreateWithPipeline() throws Exception {
        MockHttpServletResponse response = send("/api/v1/analyze-and-create", "{\"content\":\"" + SPEC + "\",\"pipeline\":true}");

        assertEquals(200, response.getStatus(), response.getContentAsString());
        verify(tracker, times(2)).createSubtask(any(), eq("TEST-2"), any());
        verify(tracker, never()).createSubtask(any(), eq("TEST-3"), any());
        JsonNode body = read(response);
        assertEquals(List.of("TEST-3", "TEST-4"), List.of(body.at("/questions/0/stories/0/key").asString(),
                body.at("/questions/0/stories/1/key").asString()));
    }

    @Test
    @DisplayName("Jobs with pipeline: the result replays the questions; step generating-tasks reached; without it: none")
    void jobsWithPipeline() throws Exception {
        String id = read(send("/api/v1/jobs/analyze-preview", "{\"content\":\"" + SPEC + "\",\"pipeline\":true}"))
                .get("jobId").asString();
        assertEquals("generating-test-cases", finished(id).get("step").asString(), "last step of the pipeline (C2)");
        assertEquals(QUESTION, result(id).at("/questions/0/question").asString());

        String plain = read(send("/api/v1/jobs/analyze-preview", "{\"content\":\"" + SPEC + "\"}")).get("jobId").asString();
        finished(plain);
        assertFalse(result(plain).has("questions"), "without pipeline: no questions");
    }

    @Test
    @DisplayName("story-tasks: created in the story's project; already has sub-tasks: tasksSkipped with their number")
    void storyTasks(CapturedOutput output) throws Exception {
        when(tracker.findStory("OTHER-7")).thenReturn(new TrackedStory("OTHER-7", "Export", "Secret-marker-c1 export",
                List.of("ai-ready"), null, "OTHER", List.of()));
        MockHttpServletResponse created = send("/api/v1/story-tasks", "{\"issueKey\":\"OTHER-7\"}");
        assertEquals(200, created.getStatus(), created.getContentAsString());
        assertEquals(2, read(created).at("/tasks/created").size());
        verify(tracker, times(2)).createSubtask(any(), eq("OTHER-7"), eq("OTHER"));

        when(tracker.findStory("OTHER-7")).thenReturn(new TrackedStory("OTHER-7", "Export", "d", List.of("ai-ready"),
                null, "OTHER", List.of("OTHER-8", "OTHER-9")));
        JsonNode skipped = read(send("/api/v1/story-tasks", "{\"issueKey\":\"OTHER-7\"}"));
        assertEquals("the story already has 2 sub-tasks", skipped.get("tasksSkipped").asString());
        verify(taskGenerator, times(1)).generateTasks(anyList(), anyList(), any());
        assertFalse(output.getAll().contains("Secret-marker-c1"), () -> "no story content in the logs: " + output.getAll().lines()
                .filter(l -> l.contains("Secret-marker-c1")).map(l -> l.substring(0, Math.min(400, l.length()))).toList());
    }

    private static final String WITH_TEST_CASE = "[{\"name\":\"E\",\"stories\":[{\"title\":\"S\",\"acceptanceCriteria\":[\"Given a\"],"
            + "\"testCases\":[{\"title\":\"T\",\"criterion\":\"%s\",\"kind\":\"%s\",\"given\":\"g\",\"when\":\"w\",\"then\":\"t\"}]}]}]";

    @Test
    @DisplayName("C2 create-jira: test cases edited by the client are validated (400, no model or tracker call); valid ones are written")
    void createJiraValidatesTestCases() throws Exception {
        MockHttpServletResponse unknown = send("/api/v1/create-jira", WITH_TEST_CASE.formatted("AC2", "nominal"));
        assertEquals(400, unknown.getStatus());
        assertEquals("testCases[0] of story 1.1: criterion must name an existing acceptance criterion (AC1 to AC1)",
                read(unknown).get("error").asString());
        MockHttpServletResponse kind = send("/api/v1/create-jira", WITH_TEST_CASE.formatted("AC1", "zz-injected"));
        assertEquals(400, kind.getStatus());
        assertFalse(kind.getContentAsString().contains("zz-injected"), "value never echoed");
        verifyNoInteractions(assessor);
        verify(tracker, never()).createEpic(any(), any());

        when(assessor.assess(anyList(), anyList())).thenReturn(List.of(new Readiness(true, "clear")));
        org.mockito.ArgumentCaptor<Story> created = org.mockito.ArgumentCaptor.forClass(Story.class);
        assertEquals(200, send("/api/v1/create-jira", WITH_TEST_CASE.formatted("AC1", "edge")).getStatus());
        verify(tracker).createStory(created.capture(), anyString(), any());
        assertEquals("edge", created.getValue().getTestCases().getFirst().kind());
    }

    @Test
    @DisplayName("C2 pipeline: test cases on the ready story only, in the preview")
    void pipelineTestCases() throws Exception {
        when(testCaseGenerator.generateTestCases(anyList(), any())).thenAnswer(call -> List.of(List.of(
                new fr.heliocraft.refinr.domain.model.TestCase("T1", "AC1", "nominal", "g", "w", "t"))));
        Epic epic = new Epic();
        epic.setName("Orders");
        Story ready = story("Export");
        ready.setAcceptanceCriteria(new ArrayList<>(List.of("Given paid orders")));
        epic.setStories(new ArrayList<>(List.of(ready, story("Separator"), story("Encoding"))));
        when(generator.generateStories(any(), any())).thenReturn(new ArrayList<>(List.of(epic)));

        JsonNode body = read(send("/api/v1/analyze-preview", "{\"content\":\"" + SPEC + "\",\"pipeline\":true}"));

        assertEquals("T1", body.at("/epics/0/stories/0/testCases/0/title").asString(), body.toString());
        assertEquals(0, body.at("/epics/0/stories/1/testCases").size());
    }

    @Test
    @DisplayName("C2 story-tasks: written test cases reported; section already there: testCasesSkipped")
    void storyTasksTestCases() throws Exception {
        String description = "### Acceptance Criteria\n- **AC1:** Given a\nAI readiness (ai-ready): ok";
        when(tracker.findStory("TEST-7")).thenReturn(new TrackedStory("TEST-7", "S", description, List.of("ai-ready"),
                null, null, List.of("TEST-8")));
        when(testCaseGenerator.generateTestCases(anyList(), any())).thenReturn(List.of(List.of(
                new fr.heliocraft.refinr.domain.model.TestCase("T1", "AC1", "nominal", "g", "w", "t"))));
        when(tracker.insertTestCases(eq("TEST-7"), any())).thenReturn(true, false);

        JsonNode written = read(send("/api/v1/story-tasks", "{\"issueKey\":\"TEST-7\"}"));
        assertEquals(1, written.at("/testCases/written").asInt(), written.toString());
        JsonNode skipped = read(send("/api/v1/story-tasks", "{\"issueKey\":\"TEST-7\"}"));
        assertEquals("the description already has a test cases section", skipped.get("testCasesSkipped").asString());
    }

    @Test
    @DisplayName("C2 recovery: story-tasks on a story with sub-tasks but no section -> tasksSkipped AND testCases, no sub-task call")
    void storyTasksRecovery() throws Exception {
        String description = "### Acceptance Criteria\n- **AC1:** Given a\nAI readiness (ai-ready): ok";
        when(tracker.findStory("TEST-9")).thenReturn(new TrackedStory("TEST-9", "S", description, List.of("ai-ready"),
                null, null, List.of("TEST-10", "TEST-11")));
        when(testCaseGenerator.generateTestCases(anyList(), any())).thenReturn(List.of(List.of(
                new fr.heliocraft.refinr.domain.model.TestCase("T1", "AC1", "nominal", "g", "w", "t"))));
        when(tracker.insertTestCases(eq("TEST-9"), any())).thenReturn(true);

        JsonNode body = read(send("/api/v1/story-tasks", "{\"issueKey\":\"TEST-9\"}"));

        assertEquals("the story already has 2 sub-tasks", body.get("tasksSkipped").asString(), body.toString());
        assertEquals(1, body.at("/testCases/written").asInt());
        assertEquals("Sub-tasks already present; 1 test cases written", body.get("message").asString());
        verifyNoInteractions(taskGenerator);
    }

    @Test
    @DisplayName("C2 preview: coverage not compliant -> no testCases, testCasesError on the story, warning naming story and criteria")
    void previewCoverageError() throws Exception {
        when(testCaseGenerator.generateTestCases(anyList(), any())).thenAnswer(call -> List.of(List.of(
                new fr.heliocraft.refinr.domain.model.TestCase("T1", "AC1", "nominal", "g", "w", "t"))));
        Epic epic = new Epic();
        epic.setName("Orders");
        Story ready = story("Export");
        ready.setAcceptanceCriteria(new ArrayList<>(List.of("Given paid orders", "Given no order")));
        epic.setStories(new ArrayList<>(List.of(ready, story("Separator"), story("Encoding"))));
        when(generator.generateStories(any(), any())).thenReturn(new ArrayList<>(List.of(epic)));

        JsonNode body = read(send("/api/v1/analyze-preview", "{\"content\":\"" + SPEC + "\",\"pipeline\":true}"));

        assertEquals(0, body.at("/epics/0/stories/0/testCases").size(), body.toString());
        assertEquals("test cases not written: AC2 has no test case", body.at("/epics/0/stories/0/testCasesError").asString());
        assertEquals("story \"Export\": test cases not written: AC2 has no test case", body.at("/warnings/0").asString());
        assertTrue(body.at("/epics/0/stories/1/testCasesError").isMissingNode(), "absent when there is no error");
    }

    @Test
    @DisplayName("story-tasks: 409 when the story is not ready, 400 without issueKey")
    void storyTasksRefusals() throws Exception {
        when(tracker.findStory("TEST-7")).thenReturn(new TrackedStory("TEST-7", "S", "d", List.of("validation-necessaire")));
        assertEquals(409, send("/api/v1/story-tasks", "{\"issueKey\":\"TEST-7\"}").getStatus());
        assertEquals(400, send("/api/v1/story-tasks", "{}").getStatus());
        verifyNoInteractions(taskGenerator);
    }

    @Test
    @DisplayName("reassess-story with createTasks: ready after the clarification -> sub-tasks created; without it: unchanged")
    void reassessWithCreateTasks() throws Exception {
        when(tracker.findStory("TEST-7")).thenReturn(
                new TrackedStory("TEST-7", "S", "d", List.of("validation-necessaire")),
                new TrackedStory("TEST-7", "S", "d", List.of("ai-ready"), null, null, List.of()));
        when(assessor.reassess(any(), any(), any(), any())).thenReturn(new Readiness(true, "clear now"));

        JsonNode body = read(send("/api/v1/reassess-story",
                "{\"issueKey\":\"TEST-7\",\"precision\":\"Separator ;\",\"createTasks\":true}"));

        assertTrue(body.get("aiReady").asBoolean());
        assertEquals(2, body.at("/tasks/created").size(), body.toString());
        verify(taskGenerator).generateTasks(anyList(), eq(List.of("Separator ;")), any());

        when(tracker.findStory("TEST-8")).thenReturn(new TrackedStory("TEST-8", "S", "d", List.of("validation-necessaire")));
        JsonNode plain = read(send("/api/v1/reassess-story", "{\"issueKey\":\"TEST-8\",\"precision\":\"p\"}"));
        assertFalse(plain.has("tasks") || plain.has("tasksSkipped") || plain.has("tasksError"), plain.toString());
    }

    @Test
    @DisplayName("validate-story with createTasks: sub-tasks created after the human validation")
    void validateWithCreateTasks() throws Exception {
        when(tracker.findStory("TEST-7")).thenReturn(
                new TrackedStory("TEST-7", "S", "d", List.of("validation-necessaire")),
                new TrackedStory("TEST-7", "S", "d", List.of("ai-ready"), null, null, List.of()));

        JsonNode body = read(send("/api/v1/validate-story", "{\"issueKey\":\"TEST-7\",\"createTasks\":true}"));

        assertEquals("human", body.get("aiReadySource").asString());
        assertEquals(2, body.at("/tasks/created").size(), body.toString());
    }
}
