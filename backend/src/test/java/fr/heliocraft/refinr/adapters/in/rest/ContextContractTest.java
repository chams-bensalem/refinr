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
 * X1 dans le contrat REST : {@code context} / {@code contextDocumentId} (corps) sur analyze-preview, analyze-and-create
 * (synchrones et jobs), reassess-story, validate-story ({@code createTasks}) et story-tasks ; {@code contextDocumentId}
 * (parametre) sur create-jira. Refus locaux avant tout appel ; jamais le contexte dans les logs. Vrais cas d'usage.
 */
@WebMvcTest({BacklogController.class, JobController.class})
@Import({UseCaseConfig.class, JobConfiguration.class})
@TestPropertySource(properties = {"jira.base-url=http://localhost:8080", "jira.project-key=TEST"})
@ExtendWith(OutputCaptureExtension.class)
class ContextContractTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CTX = "Glossaire-marqueur-x1 : une commande payee est capturee.";
    private static final String PAGE = "Page-de-contexte-marqueur-x1";

    @Autowired MockMvc mockMvc;
    @MockitoBean DocumentSourcePort source;
    @MockitoBean BacklogGeneratorPort generator;
    @MockitoBean ReadinessAssessorPort assessor;
    @MockitoBean TaskGeneratorPort taskGenerator;
    @MockitoBean fr.heliocraft.refinr.application.port.out.TestCaseGeneratorPort testCaseGenerator;
    @MockitoBean IssueTrackerPort tracker;

    private static List<Epic> backlog() {
        Story story = new Story();
        story.setTitle("Export");
        story.setDescription("d");
        story.setAcceptanceCriteria(new ArrayList<>(List.of("Given a, When b, Then c")));
        Epic epic = new Epic();
        epic.setName("Orders");
        epic.setStories(new ArrayList<>(List.of(story)));
        return new ArrayList<>(List.of(epic));
    }

    @BeforeEach
    void stubs() {
        when(source.displayName()).thenReturn("Confluence");
        when(source.acceptsDocumentId(any())).thenAnswer(call -> ConfluenceIds.isPageId(call.getArgument(0)));
        when(source.getDocumentContent("123456")).thenReturn(PAGE);
        when(tracker.displayName()).thenReturn("Jira");
        when(tracker.acceptsProjectId(any())).thenAnswer(call -> JiraProjectKeys.isValid(call.getArgument(0)));
        when(tracker.featureLevel(any())).thenReturn(FeatureLevel.NONE);
        when(tracker.previewFeatureLevel(any())).thenReturn(FeatureLevel.NONE);
        when(tracker.createEpic(any(), any())).thenReturn("TEST-1");
        when(tracker.createStory(any(), anyString(), any())).thenReturn("TEST-2");
        when(tracker.createSubtask(any(), anyString(), any())).thenReturn("TEST-5");
        // Ports lies au contexte : le meme mock, pour verifier le texte transmis
        when(generator.withContext(anyString())).thenReturn(generator);
        when(assessor.withContext(anyString())).thenReturn(assessor);
        when(taskGenerator.withContext(anyString())).thenReturn(taskGenerator);
        when(testCaseGenerator.withContext(anyString())).thenReturn(testCaseGenerator);
        when(generator.generate(any(), any())).thenAnswer(call -> backlog());
        when(generator.generateStories(any(), any())).thenAnswer(call -> backlog());
        when(assessor.assess(anyList(), anyList())).thenReturn(List.of(new Readiness(true, "clear")));
        when(taskGenerator.generateTasks(anyList(), anyList(), any())).thenReturn(List.of(List.of(new Task())));
        when(testCaseGenerator.generateTestCases(anyList(), anyList(), any())).thenReturn(Arrays.asList((List<fr.heliocraft.refinr.domain.model.TestCase>) null));
    }

    private MockHttpServletResponse send(String path, String body) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    private static String body(Object... pairs) throws Exception {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return JSON.writeValueAsString(map);
    }

    private static String error(MockHttpServletResponse response) throws Exception {
        return JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8)).get("error").asString();
    }

    private static JsonNode read(MockHttpServletResponse response) throws Exception {
        return JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    /** Champ context : source et taille, documentId seulement pour une page, jamais le texte dans le corps. */
    private static void assertContext(MockHttpServletResponse response, String source, int characters, String documentId,
                                      String secret) throws Exception {
        JsonNode context = read(response).get("context");
        assertNotNull(context, response.getContentAsString());
        assertEquals(source, context.get("source").asString());
        assertEquals(characters, context.get("characters").asInt());
        if (documentId == null) {
            assertFalse(context.has("documentId"), context.toString());
        } else {
            assertEquals(documentId, context.get("documentId").asString());
        }
        assertEquals(documentId == null ? 2 : 3, context.size(), "only source, characters and documentId");
        assertFalse(response.getContentAsString(StandardCharsets.UTF_8).contains(secret), "never the context text");
    }

    @Test
    @DisplayName("Response field context on every endpoint: request text (no documentId), request page (documentId), absent without context")
    void contextFieldInResponses() throws Exception {
        int ctx = CTX.length();
        int page = PAGE.length();
        assertContext(send("/api/v1/analyze-preview", body("content", "Spec", "context", CTX)), "request", ctx, null, "Glossaire-marqueur-x1");
        assertContext(send("/api/v1/analyze-and-create", body("content", "Spec", "contextDocumentId", "123456")), "request", page, "123456", PAGE);
        assertContext(send("/api/v1/analyze-preview", body("content", "Spec", "pipeline", true, "contextDocumentId", "123456")),
                "request", page, "123456", PAGE);
        // Sans contexte : champ absent, reponse inchangee
        assertFalse(read(send("/api/v1/analyze-preview", body("content", "Spec"))).has("context"));
        assertFalse(read(send("/api/v1/analyze-and-create", body("content", "Spec"))).has("context"));

        String epics = "[{\"name\":\"E\",\"stories\":[{\"title\":\"S\",\"description\":\"d\"}]}]";
        assertContext(mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/create-jira?contextDocumentId=123456")
                .contentType(MediaType.APPLICATION_JSON).content(epics)).andReturn().getResponse(), "request", page, "123456", PAGE);
        assertFalse(read(mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/create-jira")
                .contentType(MediaType.APPLICATION_JSON).content(epics)).andReturn().getResponse()).has("context"));

        when(tracker.findStory("TEST-7")).thenReturn(new TrackedStory("TEST-7", "S", "d", List.of("validation-necessaire")));
        when(assessor.reassess(any(), any(), any(), any(), any())).thenReturn(new Readiness(false, "Which one?"));
        assertContext(send("/api/v1/reassess-story", body("issueKey", "TEST-7", "precision", "p", "context", CTX)), "request", ctx,
                null, "Glossaire-marqueur-x1");
        assertFalse(read(send("/api/v1/reassess-story", body("issueKey", "TEST-7", "precision", "p"))).has("context"));

        when(tracker.findStory("TEST-8")).thenReturn(new TrackedStory("TEST-8", "S", "d", List.of("ai-ready"), null, "TEST", List.of()));
        assertContext(send("/api/v1/story-tasks", body("issueKey", "TEST-8", "contextDocumentId", "123456")), "request", page, "123456", PAGE);
        assertFalse(read(send("/api/v1/story-tasks", body("issueKey", "TEST-8"))).has("context"));

        // validate-story avec createTasks : la story validee passe par story-tasks, qui rend le contexte
        when(tracker.findStory("TEST-9")).thenReturn(new TrackedStory("TEST-9", "S", "d", List.of("validation-necessaire")),
                new TrackedStory("TEST-9", "S", "d", List.of("ai-ready"), null, "TEST", List.of()));
        assertContext(send("/api/v1/validate-story", body("issueKey", "TEST-9", "createTasks", true, "context", CTX)), "request",
                ctx, null, "Glossaire-marqueur-x1");
    }

    @Test
    @DisplayName("Jobs: the replayed result carries the same context field as the synchronous response")
    void contextFieldInJobResult() throws Exception {
        MockHttpServletResponse submitted = send("/api/v1/jobs/analyze-preview", body("content", "Spec", "contextDocumentId", "123456"));
        assertEquals(202, submitted.getStatus());
        String id = read(submitted).get("jobId").asString();
        MockHttpServletResponse result;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            Thread.sleep(10);
            result = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/jobs/" + id + "/result")).andReturn().getResponse();
        } while (result.getStatus() == 409 && System.nanoTime() < deadline);
        assertEquals(200, result.getStatus(), result.getContentAsString());
        assertContext(result, "request", PAGE.length(), "123456", PAGE);
        // Etat du job : aucun texte de contexte
        assertFalse(mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/jobs/" + id)).andReturn().getResponse()
                .getContentAsString().contains(PAGE));
    }

    @Test
    @DisplayName("analyze-preview: context text bound to the generation; without context, no port is bound (unchanged path)")
    void previewWithAndWithoutContext(CapturedOutput output) throws Exception {
        assertEquals(200, send("/api/v1/analyze-preview", body("content", "Spec", "context", CTX)).getStatus());
        verify(generator).withContext(CTX);
        clearInvocations(generator);
        assertEquals(200, send("/api/v1/analyze-preview", body("content", "Spec")).getStatus());
        verify(generator, never()).withContext(anyString());
        assertFalse(output.getAll().contains("Glossaire-marqueur-x1"), "the context never reaches the logs");
        assertTrue(output.getAll().contains("Project context: source request"));
    }

    @Test
    @DisplayName("Pipeline with contextDocumentId: the page is read and prompts 1, 2, 4 and 5 all get it (analyze-and-create)")
    void pipelineWithContextPage(CapturedOutput output) throws Exception {
        MockHttpServletResponse response = send("/api/v1/analyze-and-create",
                body("content", "Spec", "pipeline", true, "contextDocumentId", "123456"));
        assertEquals(200, response.getStatus(), response.getContentAsString());
        verify(generator).withContext(PAGE);
        verify(assessor).withContext(PAGE);
        verify(taskGenerator).withContext(PAGE);
        verify(testCaseGenerator).withContext(PAGE);
        assertFalse(output.getAll().contains(PAGE));
    }

    @Test
    @DisplayName("Local refusals before any call: both fields -> 400, invalid page id -> 400, over 20,000 characters -> 413 (sync and jobs)")
    void localRefusals() throws Exception {
        for (String path : List.of("/api/v1/analyze-preview", "/api/v1/analyze-and-create", "/api/v1/jobs/analyze-preview",
                "/api/v1/jobs/analyze-and-create")) {
            MockHttpServletResponse both = send(path, body("content", "Spec", "context", "c", "contextDocumentId", "123456"));
            assertEquals(400, both.getStatus(), path);
            assertEquals("context and contextDocumentId cannot be combined", error(both));
            assertEquals("Invalid contextDocumentId", error(send(path, body("content", "Spec", "contextDocumentId", "abc"))));
            MockHttpServletResponse ok = send(path, body("content", "Spec", "context", "x".repeat(20_000)));
            assertTrue(ok.getStatus() == 200 || ok.getStatus() == 202, path + " " + ok.getStatus());
            MockHttpServletResponse large = send(path, body("content", "Spec", "context", "x".repeat(20_001)));
            assertEquals(413, large.getStatus(), path);
            assertEquals("The project context exceeds the maximum size of 20000 characters", error(large));
        }
        verify(source, never()).getDocumentContent("abc");
    }

    @Test
    @DisplayName("reassess-story and story-tasks bind the context; validate-story accepts it only with createTasks (400 otherwise)")
    void storyEndpoints() throws Exception {
        when(tracker.findStory("TEST-7")).thenReturn(new TrackedStory("TEST-7", "S", "d", List.of("validation-necessaire")));
        when(assessor.reassess(any(), any(), any(), any(), any())).thenReturn(new Readiness(false, "Which one?"));
        assertEquals(200, send("/api/v1/reassess-story", body("issueKey", "TEST-7", "precision", "p", "context", CTX)).getStatus());
        verify(assessor).withContext(CTX);
        assertEquals("context and contextDocumentId are only accepted with createTasks",
                error(send("/api/v1/validate-story", body("issueKey", "TEST-7", "context", CTX))));
        verify(tracker, never()).applyHumanValidation(any(), any(), any());
        assertEquals(413, send("/api/v1/validate-story", body("issueKey", "TEST-7", "createTasks", true,
                "context", "x".repeat(20_001))).getStatus());
        verify(tracker, never()).applyHumanValidation(any(), any(), any());

        when(tracker.findStory("TEST-8")).thenReturn(new TrackedStory("TEST-8", "S", "d", List.of("ai-ready"), null,
                "TEST", List.of()));
        assertEquals(200, send("/api/v1/story-tasks", body("issueKey", "TEST-8", "contextDocumentId", "123456")).getStatus());
        verify(taskGenerator).withContext(PAGE);
    }

    @Test
    @DisplayName("create-jira?contextDocumentId: the recomputed verdicts get the page; unknown page id format -> 400")
    void createJira() throws Exception {
        String epics = "[{\"name\":\"E\",\"stories\":[{\"title\":\"S\",\"description\":\"d\"}]}]";
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/create-jira?contextDocumentId=123456")
                .contentType(MediaType.APPLICATION_JSON).content(epics)).andReturn().getResponse().getStatus());
        verify(assessor).withContext(PAGE);
        assertEquals(400, mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/create-jira?contextDocumentId=abc")
                .contentType(MediaType.APPLICATION_JSON).content(epics)).andReturn().getResponse().getStatus());
    }
}
