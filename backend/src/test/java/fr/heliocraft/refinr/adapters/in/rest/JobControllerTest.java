// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.adapters.out.confluence.ConfluenceIds;
import fr.heliocraft.refinr.adapters.out.jira.JiraProjectKeys;
import fr.heliocraft.refinr.application.port.in.DescribeTracker;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.DocumentSourceException;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.config.JobConfiguration;
import fr.heliocraft.refinr.config.UseCaseConfig;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Jobs d'analyse, avec les vrais cas d'usage et le vrai service de jobs (seuls les ports sortants sont simules) : le
 * resultat d'un job est compare a la reponse de l'endpoint synchrone, pour les memes donnees.
 */
@WebMvcTest({BacklogController.class, JobController.class})
@Import({UseCaseConfig.class, JobConfiguration.class})
@TestPropertySource(properties = {
    "jira.base-url=http://localhost:8080",
    "jira.username=test@example.com",
    "jira.token=test-token",
    "jira.project-key=TEST",
    "confluence.base-url=http://localhost:8080",
    "confluence.username=test@example.com",
    "confluence.token=test-token",
    "refinr.document.max-content-chars=300",
    "refinr.jobs.max-concurrent=1",
    "refinr.jobs.max-queued=1",
    "refinr.jobs.max-duration=2s"
})
@ExtendWith(OutputCaptureExtension.class)
class JobControllerTest {

    private static final String PAGE_ID = "123456";
    private static final String SPEC = "Le magasinier saisit chaque entree de stock avec la reference et la quantite.";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentSourcePort source;

    @MockitoBean
    private BacklogGeneratorPort generator;
    @MockitoBean fr.heliocraft.refinr.application.port.out.TaskGeneratorPort taskGenerator;

    @MockitoBean
    private ReadinessAssessorPort assessor;

    @MockitoBean
    private IssueTrackerPort tracker;

    @MockitoBean
    private DescribeTracker describeTracker;

    @BeforeEach
    void setUp() {
        when(describeTracker.acceptsProjectId(any())).thenAnswer(call -> JiraProjectKeys.isValid(call.getArgument(0)));
        when(describeTracker.displayName()).thenReturn("Jira");
        when(source.displayName()).thenReturn("Confluence");
        when(source.acceptsDocumentId(any())).thenAnswer(call -> ConfluenceIds.isPageId(call.getArgument(0)));
        when(source.getDocumentContent(PAGE_ID)).thenReturn(SPEC);
        when(source.documentUrl(PAGE_ID)).thenReturn("https://wiki.example.com/pages/" + PAGE_ID);
        when(generator.generate(any(), any())).thenAnswer(call -> epics());
        when(tracker.createEpic(any(Epic.class), any())).thenReturn("TEST-1");
        when(tracker.createStory(any(Story.class), anyString(), any())).thenReturn("TEST-2");
        when(tracker.createSubtask(any(Task.class), anyString(), any())).thenReturn("TEST-3");
    }

    private static List<Epic> epics() {
        Task task = new Task();
        task.setTitle("Saisir l'entree");
        Story story = new Story();
        story.setTitle("Saisie d'une entree de stock");
        story.setAcceptanceCriteria(List.of("La quantite est enregistree"));
        story.setAiReady(true);
        story.setAiReadyReason("clear");
        story.setTasks(new ArrayList<>(List.of(task)));
        Epic epic = new Epic();
        epic.setName("Stocks");
        epic.setStories(new ArrayList<>(List.of(story)));
        return new ArrayList<>(List.of(epic));
    }

    private static String json(String... keyValues) {
        ObjectNode node = JSON.createObjectNode();
        for (int i = 0; i < keyValues.length; i += 2) {
            node.put(keyValues[i], keyValues[i + 1]);
        }
        return node.toString();
    }

    private MockHttpServletResponse postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    /** Soumet le job (202), attend sa fin et renvoie l'etat final. */
    private JsonNode runJob(String endpoint, String body) throws Exception {
        MockHttpServletResponse submitted = postJson("/api/v1/jobs/" + endpoint, body);
        assertEquals(202, submitted.getStatus(), submitted.getContentAsString());
        String id = JSON.readTree(submitted.getContentAsString()).get("jobId").asString();
        return awaitFinished(id);
    }

    private JsonNode awaitFinished(String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            JsonNode job = JSON.readTree(mockMvc.perform(get("/api/v1/jobs/" + id)).andReturn().getResponse()
                    .getContentAsString());
            String jobStatus = job.get("status").asString();
            if (jobStatus.equals("succeeded") || jobStatus.equals("failed")) {
                return job;
            }
            assertTrue(System.nanoTime() < deadline, "job not finished within 10 s");
            Thread.sleep(10);
        }
    }

    private static JsonNode withoutCorrelationId(String body) {
        JsonNode node = JSON.readTree(body);
        if (node instanceof ObjectNode object) {
            object.remove("correlationId");
        }
        return node;
    }

    /**
     * Meme requete en synchrone et en job : meme statut, meme Retry-After, meme corps (hors correlationId) ; etat du job
     * {@code succeeded} seulement si la reponse rejouee est 2xx, sinon {@code failed}.
     */
    private JsonNode assertSameAsSynchronous(String endpoint, String body) throws Exception {
        MockHttpServletResponse sync = postJson("/api/v1/" + endpoint, body);
        JsonNode job = runJob(endpoint, body);
        MockHttpServletResponse result = mockMvc.perform(get("/api/v1/jobs/" + job.get("jobId").asString() + "/result"))
                .andReturn().getResponse();

        assertEquals(sync.getStatus(), result.getStatus(), result.getContentAsString());
        assertEquals(sync.getHeader("Retry-After"), result.getHeader("Retry-After"));
        assertEquals(withoutCorrelationId(sync.getContentAsString()), withoutCorrelationId(result.getContentAsString()));
        assertEquals(sync.getStatus(), job.get("outcome").get("httpStatus").asInt());
        assertEquals(sync.getStatus() < 300 ? "succeeded" : "failed", job.get("status").asString(),
                "status for an HTTP " + sync.getStatus() + " result");
        if (sync.getHeader("Retry-After") != null) {
            assertEquals(Long.parseLong(sync.getHeader("Retry-After")), job.get("outcome").get("retryAfterSeconds").asLong());
        } else {
            assertFalse(job.get("outcome").has("retryAfterSeconds"));
        }
        return job;
    }

    @Test
    @DisplayName("Submission: 202, Location of the job, queued status and the status and result addresses")
    void submissionIsAccepted() throws Exception {
        MockHttpServletResponse response = postJson("/api/v1/jobs/analyze-preview", json("content", SPEC));

        assertEquals(202, response.getStatus());
        JsonNode body = JSON.readTree(response.getContentAsString());
        String id = body.get("jobId").asString();
        assertTrue(JobPaths.isJobId(id), id);
        assertEquals("/api/v1/jobs/" + id, response.getHeader("Location"));
        assertEquals("analyze-preview", body.get("type").asString());
        assertEquals("queued", body.get("status").asString());
        assertEquals("/api/v1/jobs/" + id, body.get("statusUrl").asString());
        assertEquals("/api/v1/jobs/" + id + "/result", body.get("resultUrl").asString());
        awaitFinished(id);
    }

    @Test
    @DisplayName("Result identical to the synchronous response: preview of a document (with documentUrl) and of a text")
    void previewResultIsTheSynchronousResponse() throws Exception {
        assertSameAsSynchronous("analyze-preview", json("documentId", PAGE_ID));
        assertSameAsSynchronous("analyze-preview", json("content", SPEC, "title", "Stocks", "language", "fr"));
    }

    @Test
    @DisplayName("Creation: complete (200) and partial (207) give succeeded, nothing created (502) gives failed; result identical")
    void creationResultIsTheSynchronousResponse() throws Exception {
        JsonNode complete = assertSameAsSynchronous("analyze-and-create", json("documentId", PAGE_ID, "projectKey", "PROJ"));
        assertEquals(200, complete.get("outcome").get("httpStatus").asInt());
        assertEquals("succeeded", complete.get("status").asString());

        doThrow(new fr.heliocraft.refinr.application.port.out.IssueTrackerException("Rejected (HTTP 400)")).when(tracker).createSubtask(any(Task.class), anyString(), any());
        JsonNode partial = assertSameAsSynchronous("analyze-and-create", json("content", SPEC));
        assertEquals(207, partial.get("outcome").get("httpStatus").asInt());
        assertEquals("succeeded", partial.get("status").asString());

        doThrow(new fr.heliocraft.refinr.application.port.out.IssueTrackerException("Rejected (HTTP 400)")).when(tracker).createEpic(any(Epic.class), any());
        JsonNode nothing = assertSameAsSynchronous("analyze-and-create", json("content", SPEC));
        assertEquals(502, nothing.get("outcome").get("httpStatus").asInt());
        assertEquals("failed", nothing.get("status").asString());
        mockMvc.perform(get("/api/v1/jobs/" + nothing.get("jobId").asString() + "/result"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("No issue could be created"))
                .andExpect(jsonPath("$.failed[0].reason").value("Rejected (HTTP 400)"));
    }

    @Test
    @DisplayName("Result identical to the synchronous response: 503 quota with Retry-After, 503 overload without delay")
    void providerBusyIs503() throws Exception {
        doThrow(new LlmException(LlmException.Reason.RATE_LIMITED, "quota", Duration.ofSeconds(30))).when(generator).generate(any(), any());
        assertSameAsSynchronous("analyze-preview", json("content", SPEC));
        assertSameAsSynchronous("analyze-and-create", json("documentId", PAGE_ID));

        doThrow(new LlmException(LlmException.Reason.OVERLOADED, "busy")).when(generator).generate(any(), any());
        assertSameAsSynchronous("analyze-preview", json("content", SPEC));
    }

    @Test
    @DisplayName("Result identical to the synchronous response: 502 provider unavailable and truncated output")
    void providerFailureIs502() throws Exception {
        doThrow(new LlmException(LlmException.Reason.UNAVAILABLE, "down")).when(generator).generate(any(), any());
        assertSameAsSynchronous("analyze-preview", json("content", SPEC));
        doThrow(new LlmException(LlmException.Reason.TRUNCATED, "cut")).when(generator).generate(any(), any());
        assertSameAsSynchronous("analyze-and-create", json("content", SPEC));
    }

    @Test
    @DisplayName("Result identical to the synchronous response: document not found (404), source down (502), unexpected (500)")
    void otherFailures() throws Exception {
        doThrow(new DocumentSourceException(DocumentSourceException.Reason.NOT_FOUND, "missing", null)).when(source).getDocumentContent(PAGE_ID);
        assertSameAsSynchronous("analyze-preview", json("documentId", PAGE_ID));
        doThrow(new DocumentSourceException(DocumentSourceException.Reason.UNAVAILABLE, "down", null)).when(source).getDocumentContent(PAGE_ID);
        assertSameAsSynchronous("analyze-and-create", json("documentId", PAGE_ID));
        doReturn("   ").when(source).getDocumentContent(PAGE_ID);
        assertSameAsSynchronous("analyze-preview", json("documentId", PAGE_ID));
        doThrow(new IllegalStateException("raw detail")).when(generator).generate(any(), any());
        assertSameAsSynchronous("analyze-preview", json("content", SPEC));
        assertSameAsSynchronous("analyze-and-create", json("content", SPEC));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"content\":\"x\",\"language\":\"xx\"}",
        "{\"documentId\":\"" + PAGE_ID + "\",\"content\":\"x\"}",
        "{\"title\":\"T\"}",
        "{}",
        "{\"documentId\":\"not a page\"}",
        "{\"content\":\"   \"}",
        "{\"documentId\":\"123\",\"confluencePageId\":\"456\"}"
    })
    @DisplayName("Submission refusals are the synchronous ones (same status and message), and no job is created")
    void refusalsAtSubmission(String body) throws Exception {
        for (String endpoint : List.of("analyze-preview", "analyze-and-create")) {
            MockHttpServletResponse sync = postJson("/api/v1/" + endpoint, body);
            MockHttpServletResponse job = postJson("/api/v1/jobs/" + endpoint, body);

            assertEquals(400, job.getStatus(), job.getContentAsString());
            assertEquals(sync.getStatus(), job.getStatus());
            assertEquals(withoutCorrelationId(sync.getContentAsString()), withoutCorrelationId(job.getContentAsString()));
            assertNull(job.getHeader("Location"));
        }
        verifyNoInteractions(generator);
    }

    @Test
    @DisplayName("Submission refusals: text over the size limit (413) and invalid projectKey (400), like the synchronous endpoints")
    void sizeAndProjectRefusals() throws Exception {
        String tooLong = json("content", "x".repeat(301));
        assertEquals(413, postJson("/api/v1/jobs/analyze-preview", tooLong).getStatus());
        assertEquals(withoutCorrelationId(postJson("/api/v1/analyze-preview", tooLong).getContentAsString()),
                withoutCorrelationId(postJson("/api/v1/jobs/analyze-preview", tooLong).getContentAsString()));
        String badProject = json("content", SPEC, "projectKey", "not a key");
        assertEquals(withoutCorrelationId(postJson("/api/v1/analyze-and-create", badProject).getContentAsString()),
                withoutCorrelationId(postJson("/api/v1/jobs/analyze-and-create", badProject).getContentAsString()));
        verifyNoInteractions(generator);
    }

    @Test
    @DisplayName("While running: status with step and start date, result 409, cancel 409; then succeeded with its outcome")
    void runningJob() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(generator.generate(any(), any())).thenAnswer(call -> {
            release.await(5, TimeUnit.SECONDS);
            return epics();
        });
        String id = JSON.readTree(postJson("/api/v1/jobs/analyze-preview", json("documentId", PAGE_ID))
                .getContentAsString()).get("jobId").asString();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        JsonNode job;
        do {
            Thread.sleep(10);
            job = JSON.readTree(mockMvc.perform(get("/api/v1/jobs/" + id)).andReturn().getResponse().getContentAsString());
        } while (!job.path("step").asString("").equals("generating") && System.nanoTime() < deadline);

        assertEquals("running", job.get("status").asString());
        assertTrue(job.has("startedAt"));
        assertFalse(job.has("finishedAt"));
        assertFalse(job.has("outcome"));
        mockMvc.perform(get("/api/v1/jobs/" + id + "/result"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(JobController.NOT_FINISHED));
        mockMvc.perform(delete("/api/v1/jobs/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(JobController.RUNNING));

        release.countDown();
        JsonNode done = awaitFinished(id);
        assertEquals("succeeded", done.get("status").asString());
        assertEquals(200, done.get("outcome").get("httpStatus").asInt());
        assertTrue(done.has("finishedAt"));
        assertEquals("analyze-preview", done.get("type").asString());
    }

    @Test
    @DisplayName("max-concurrent and max-queued: a third job is refused with 503 and no Retry-After")
    void queueFullIs503() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(generator.generate(any(), any())).thenAnswer(call -> {
            release.await(5, TimeUnit.SECONDS);
            return epics();
        });
        String first = JSON.readTree(postJson("/api/v1/jobs/analyze-preview", json("content", SPEC))
                .getContentAsString()).get("jobId").asString();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!JSON.readTree(mockMvc.perform(get("/api/v1/jobs/" + first)).andReturn().getResponse()
                .getContentAsString()).get("status").asString().equals("running")) {
            assertTrue(System.nanoTime() < deadline);
            Thread.sleep(10);
        }
        String second = JSON.readTree(postJson("/api/v1/jobs/analyze-preview", json("content", SPEC))
                .getContentAsString()).get("jobId").asString();

        mockMvc.perform(post("/api/v1/jobs/analyze-preview").contentType(MediaType.APPLICATION_JSON).content(json("content", SPEC)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Too many analysis jobs waiting, try again later"))
                .andExpect(header().doesNotExist("Retry-After"));

        release.countDown();
        awaitFinished(first);
        awaitFinished(second);
    }

    @Test
    @DisplayName("max-duration exceeded before the creation: failed, result 504, no issue created")
    void timeoutIs504() throws Exception {
        when(generator.generate(any(), any())).thenAnswer(call -> {
            new CountDownLatch(1).await();
            return epics();
        });

        JsonNode job = runJob("analyze-and-create", json("content", SPEC));

        assertEquals("failed", job.get("status").asString());
        assertEquals(504, job.get("outcome").get("httpStatus").asInt());
        mockMvc.perform(get("/api/v1/jobs/" + job.get("jobId").asString() + "/result"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value(JobController.TIMEOUT));
        verify(tracker, never()).createEpic(any(), any());
    }

    @Test
    @DisplayName("Unknown, expired or malformed ids: 404 without echoing the id; a finished job is deleted (204), then 404")
    void unknownAndDeletedJobs() throws Exception {
        for (String path : List.of("/api/v1/jobs/1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d", "/api/v1/jobs/not-a-job",
                "/api/v1/jobs/1A2B3C4D-5E6F-4A7B-8C9D-0E1F2A3B4C5D/result")) {
            String body = mockMvc.perform(get(path))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value(JobController.UNKNOWN_JOB))
                    .andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("not-a-job"));
        }
        mockMvc.perform(delete("/api/v1/jobs/not-a-job")).andExpect(status().isNotFound());

        String id = runJob("analyze-preview", json("content", SPEC)).get("jobId").asString();
        mockMvc.perform(delete("/api/v1/jobs/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/jobs/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/jobs/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Logs of a job: neither the document nor the result nor the full job id")
    void logsStaySafe(CapturedOutput output) throws Exception {
        when(generator.generate(any(), any())).thenAnswer(call -> {
            List<Epic> epics = epics();
            epics.getFirst().setName("Result-marker-5d2e");
            return epics;
        });

        JsonNode job = runJob("analyze-preview", json("content", SPEC + " Content-marker-91ab", "title", "Title-marker-0c7f"));

        String logs = output.getAll();
        String id = job.get("jobId").asString();
        assertTrue(logs.contains(id.substring(0, 8)), logs);
        assertFalse(logs.contains(id), logs);
        assertFalse(logs.contains("Content-marker-91ab"), logs);
        assertFalse(logs.contains("Title-marker-0c7f"), logs);
        assertFalse(logs.contains("Result-marker-5d2e"), logs);
    }
}
