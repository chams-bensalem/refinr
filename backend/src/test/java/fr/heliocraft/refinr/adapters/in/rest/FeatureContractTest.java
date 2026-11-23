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
import fr.heliocraft.refinr.config.JobConfiguration;
import fr.heliocraft.refinr.config.UseCaseConfig;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Feature;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Niveau Feature dans le contrat REST : nouvelle forme (features) et forme d'avant (stories a plat), avertissements,
 * refus 422. Vrais cas d'usage ; seuls les ports sortants sont simules.
 */
class FeatureContractTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SPEC = "Le gestionnaire reserve une salle et administre la liste des salles.";

    @WebMvcTest({BacklogController.class, JobController.class})
    @Import({UseCaseConfig.class, JobConfiguration.class})
    abstract static class WebLayer {
        @Autowired MockMvc mockMvc;
        @MockitoBean DocumentSourcePort source;
        @MockitoBean BacklogGeneratorPort generator;
        @MockitoBean ReadinessAssessorPort assessor;
        @MockitoBean IssueTrackerPort tracker;

        @BeforeEach
        void stubs() {
            when(source.displayName()).thenReturn("Confluence");
            when(source.acceptsDocumentId(any())).thenAnswer(call -> ConfluenceIds.isPageId(call.getArgument(0)));
            when(tracker.displayName()).thenReturn("Jira");
            when(tracker.acceptsProjectId(any())).thenAnswer(call -> JiraProjectKeys.isValid(call.getArgument(0)));
            when(tracker.featureLevel(any())).thenReturn(FeatureLevel.NONE);
            when(tracker.createEpic(any(), any())).thenReturn("TEST-1");
            when(tracker.createFeature(any(), anyString(), any())).thenReturn("TEST-2");
            when(tracker.createStory(any(), anyString(), any())).thenReturn("TEST-3");
            when(assessor.assess(anyList(), anyList())).thenAnswer(call -> ((List<?>) call.getArgument(0)).stream()
                    .map(story -> new Readiness(true, "clear")).toList());
            when(generator.generate(any(), any())).thenAnswer(call -> List.of(twoFeatures()));
        }

        MockHttpServletResponse postJson(String path, String body) throws Exception {
            return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn()
                    .getResponse();
        }
    }

    static Story story(String title) {
        Story story = new Story();
        story.setTitle(title);
        story.setAiReady(true);
        story.setAiReadyReason("clear");
        return story;
    }

    static Epic twoFeatures() {
        Feature booking = new Feature();
        booking.setName("Booking");
        booking.setStories(new ArrayList<>(List.of(story("Book a room"))));
        Feature admin = new Feature();
        admin.setName("Administration");
        admin.setStories(new ArrayList<>(List.of(story("Add a room"))));
        Epic epic = new Epic();
        epic.setName("Rooms");
        epic.setFeatures(new ArrayList<>(List.of(booking, admin)));
        return epic;
    }

    private static String json(String key, String value) {
        return JSON.createObjectNode().put(key, value).toString();
    }

    @Nested
    @TestPropertySource(properties = {"jira.base-url=http://localhost:8080", "jira.project-key=TEST"})
    class LabelPolicy extends WebLayer {

        @Test
        @DisplayName("Preview: epics[].features with their stories, epics[].stories still flat, warning when the tracker has no feature level")
        void previewHasFeaturesAndLegacyStories() throws Exception {
            JsonNode body = JSON.readTree(postJson("/api/v1/analyze-preview", json("content", SPEC)).getContentAsString());

            JsonNode epic = body.at("/epics/0");
            assertEquals(2, epic.get("features").size());
            assertEquals("Booking", epic.at("/features/0/name").asString());
            assertFalse(epic.at("/features/0/implicit").asBoolean());
            assertEquals("Book a room", epic.at("/features/0/stories/0/title").asString());
            assertEquals(List.of("Book a room", "Add a room"),
                    List.of(epic.at("/stories/0/title").asString(), epic.at("/stories/1/title").asString()));
            assertEquals(1, body.get("warnings").size());
            assertTrue(body.at("/warnings/0").asString().contains("features are kept as labels"));
        }

        @Test
        // Contexte neuf : le niveau Feature lu par un autre test est en cache (1 h) dans le bean DescribeTracker
        @org.springframework.test.annotation.DirtiesContext(
                methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
        @DisplayName("Preview with a native feature level, or a single (implicit) feature: no warnings field at all")
        void noWarningsField() throws Exception {
            when(tracker.featureLevel(any())).thenReturn(FeatureLevel.OPTIONAL);
            assertFalse(JSON.readTree(postJson("/api/v1/analyze-preview", json("content", SPEC)).getContentAsString())
                    .has("warnings"));

            when(tracker.featureLevel(any())).thenReturn(FeatureLevel.NONE);
            Epic single = new Epic();
            single.setName("Export");
            single.setStories(List.of(story("Export the orders")));
            when(generator.generate(any(), any())).thenAnswer(call -> List.of(single));
            JsonNode body = JSON.readTree(postJson("/api/v1/analyze-preview", json("content", SPEC)).getContentAsString());
            assertFalse(body.has("warnings"));
            assertTrue(body.at("/epics/0/features/0/implicit").asBoolean());
            assertEquals("Export", body.at("/epics/0/features/0/name").asString(), "implicit feature named after its epic");
        }

        @Test
        @DisplayName("create-jira, former shape only (stories): one implicit feature, stories under the epic, no warning")
        void legacyShapeOnly() throws Exception {
            MockHttpServletResponse response = postJson("/api/v1/create-jira",
                    "[{\"name\":\"E\",\"stories\":[{\"title\":\"S\"}]}]");

            assertEquals(200, response.getStatus(), response.getContentAsString());
            verify(tracker).createStory(any(), eq("TEST-1"), any());
            verify(tracker, never()).createFeature(any(), anyString(), any());
            assertFalse(JSON.readTree(response.getContentAsString()).has("warnings"));
        }

        @Test
        @DisplayName("create-jira, new shape only (features): features used (native level: one issue per feature)")
        void newShapeOnly() throws Exception {
            when(tracker.featureLevel(any())).thenReturn(FeatureLevel.OPTIONAL);

            MockHttpServletResponse response = postJson("/api/v1/create-jira", "[{\"name\":\"E\",\"features\":["
                    + "{\"name\":\"A\",\"stories\":[{\"title\":\"S1\"}]},{\"name\":\"B\",\"stories\":[{\"title\":\"S2\"}]}]}]");

            assertEquals(200, response.getStatus(), response.getContentAsString());
            ArgumentCaptor<Feature> features = ArgumentCaptor.forClass(Feature.class);
            verify(tracker, times(2)).createFeature(features.capture(), eq("TEST-1"), any());
            assertEquals(List.of("A", "B"), features.getAllValues().stream().map(Feature::getName).toList());
            verify(tracker, times(2)).createStory(any(), eq("TEST-2"), any());
            JsonNode body = JSON.readTree(response.getContentAsString());
            assertEquals("TEST-2", body.at("/epics/0/features/0/jiraKey").asString());
            assertEquals(2, body.at("/epics/0/stories").size(), "flat stories kept in the result");
        }

        @Test
        @DisplayName("create-jira, both shapes identical (preview sent back as is): features used, no shape warning")
        void bothShapesIdentical() throws Exception {
            String preview = postJson("/api/v1/analyze-preview", json("content", SPEC)).getContentAsString();
            String epics = JSON.readTree(preview).get("epics").toString();
            when(tracker.featureLevel(any())).thenReturn(FeatureLevel.OPTIONAL);

            MockHttpServletResponse response = postJson("/api/v1/create-jira", epics);

            assertEquals(200, response.getStatus(), response.getContentAsString());
            verify(tracker, times(2)).createFeature(any(), eq("TEST-1"), any());
            assertFalse(JSON.readTree(response.getContentAsString()).has("warnings"));
        }

        @Test
        @DisplayName("create-jira, both shapes different (stories edited in the former shape): stories win, with a warning")
        void editedLegacyStoriesWin() throws Exception {
            String preview = postJson("/api/v1/analyze-preview", json("content", SPEC)).getContentAsString();
            JsonNode epics = JSON.readTree(preview).get("epics");
            ((tools.jackson.databind.node.ObjectNode) epics.at("/0/stories/0")).put("title", "Book a meeting room");
            when(tracker.featureLevel(any())).thenReturn(FeatureLevel.OPTIONAL);

            MockHttpServletResponse response = postJson("/api/v1/create-jira", epics.toString());

            assertEquals(200, response.getStatus(), response.getContentAsString());
            verify(tracker, never()).createFeature(any(), anyString(), any());
            ArgumentCaptor<Story> stories = ArgumentCaptor.forClass(Story.class);
            verify(tracker, times(2)).createStory(stories.capture(), eq("TEST-1"), any());
            assertEquals("Book a meeting room", stories.getAllValues().getFirst().getTitle());
            JsonNode body = JSON.readTree(response.getContentAsString());
            assertEquals(RestBacklogMapper.LEGACY_STORIES_WIN, body.at("/warnings/0").asString());
        }
    }

    @Nested
    @TestPropertySource(properties = {"jira.base-url=http://localhost:8080", "jira.project-key=TEST",
            "refinr.features.without-level=refuse"})
    class RefusePolicy extends WebLayer {

        @Test
        @DisplayName("Policy refuse: create-jira and analyze-and-create answer 422 before any tracker call; the job result is the same")
        void refusalIs422() throws Exception {
            String explicit = "[{\"name\":\"E\",\"features\":[{\"name\":\"A\",\"stories\":[{\"title\":\"S1\"}]},"
                    + "{\"name\":\"B\",\"stories\":[{\"title\":\"S2\"}]}]}]";
            MockHttpServletResponse created = postJson("/api/v1/create-jira", explicit);
            assertEquals(422, created.getStatus(), created.getContentAsString());
            assertEquals(fr.heliocraft.refinr.application.port.in.FeatureLevelMissingException.MESSAGE,
                    JSON.readTree(created.getContentAsString()).get("error").asString());

            MockHttpServletResponse sync = postJson("/api/v1/analyze-and-create", json("content", SPEC));
            assertEquals(422, sync.getStatus());
            verify(tracker, never()).createEpic(any(), any());

            String id = JSON.readTree(postJson("/api/v1/jobs/analyze-and-create", json("content", SPEC))
                    .getContentAsString()).get("jobId").asString();
            JsonNode job;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            do {
                Thread.sleep(10);
                job = JSON.readTree(mockMvc.perform(get("/api/v1/jobs/" + id)).andReturn().getResponse()
                        .getContentAsString());
            } while (!job.get("status").asString().equals("failed") && System.nanoTime() < deadline);
            assertEquals(422, job.at("/outcome/httpStatus").asInt());
            MockHttpServletResponse result = mockMvc.perform(get("/api/v1/jobs/" + id + "/result")).andReturn().getResponse();
            assertEquals(422, result.getStatus());
            assertEquals(JSON.readTree(sync.getContentAsString()).get("error"),
                    JSON.readTree(result.getContentAsString()).get("error"));
        }

        @Test
        @DisplayName("Policy refuse: the preview is never refused, it warns that the creation will be")
        void previewWarnsOfTheRefusal() throws Exception {
            JsonNode body = JSON.readTree(postJson("/api/v1/analyze-preview", json("content", SPEC)).getContentAsString());
            assertTrue(body.at("/warnings/0").asString().contains("will be refused"), body.toString());
        }
    }

    /**
     * Projet demande ({@code projectKey}) sans niveau Feature, alors que le projet par defaut en a un : l'apercu, le job
     * d'apercu et la creation lisent le niveau du projet demande (essai reel du 2026-11-23, refinr-spike-ro).
     */
    @Nested
    @TestPropertySource(properties = {"jira.base-url=http://localhost:8080", "jira.project-key=TEST",
            "refinr.features.without-level=label"})
    class RequestedProject extends WebLayer {

        private static final String NO_FEATURE = "NOFEAT";
        private static final String LABELS = "features are kept as labels";

        @BeforeEach
        void levels() {
            // Projet par defaut (null) : niveau natif ; projet demande : aucun niveau Feature
            when(tracker.featureLevel(any())).thenAnswer(call ->
                    NO_FEATURE.equals(call.getArgument(0)) ? FeatureLevel.NONE : FeatureLevel.OPTIONAL);
        }

        private String request(String projectKey) {
            tools.jackson.databind.node.ObjectNode body = JSON.createObjectNode().put("content", SPEC);
            if (projectKey != null) {
                body.put("projectKey", projectKey);
            }
            return body.toString();
        }

        private JsonNode finishedJob(String id) throws Exception {
            JsonNode job;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            do {
                Thread.sleep(10);
                job = JSON.readTree(mockMvc.perform(get("/api/v1/jobs/" + id)).andReturn().getResponse()
                        .getContentAsString());
            } while (!job.get("status").asString().equals("succeeded") && System.nanoTime() < deadline);
            assertEquals("succeeded", job.get("status").asString(), job.toString());
            return JSON.readTree(mockMvc.perform(get("/api/v1/jobs/" + id + "/result")).andReturn().getResponse()
                    .getContentAsString());
        }

        @Test
        @DisplayName("Preview with projectKey of a project without feature level: labels warning (the default project has one)")
        void previewReadsTheRequestedProject() throws Exception {
            JsonNode requested = JSON.readTree(postJson("/api/v1/analyze-preview", request(NO_FEATURE))
                    .getContentAsString());
            assertEquals(1, requested.path("warnings").size(), requested.toString());
            assertTrue(requested.at("/warnings/0").asString().contains(LABELS), requested.toString());
            verify(tracker).featureLevel(NO_FEATURE);

            JsonNode byDefault = JSON.readTree(postJson("/api/v1/analyze-preview", request(null)).getContentAsString());
            assertFalse(byDefault.has("warnings"), "default project has a native feature level: " + byDefault);
        }

        @Test
        @DisplayName("Preview with an invalid projectKey: 400, as analyze-and-create")
        void previewRejectsAnInvalidProjectKey() throws Exception {
            MockHttpServletResponse response = postJson("/api/v1/analyze-preview", request("not a key"));
            assertEquals(400, response.getStatus());
            assertEquals("Invalid projectKey: not a key",
                    JSON.readTree(response.getContentAsString()).get("error").asString());
            verify(generator, never()).generate(any(), any());
        }

        @Test
        @DisplayName("Preview job with projectKey of a project without feature level: labels warning in the job result")
        void previewJobReadsTheRequestedProject() throws Exception {
            String requested = JSON.readTree(postJson("/api/v1/jobs/analyze-preview", request(NO_FEATURE))
                    .getContentAsString()).get("jobId").asString();
            JsonNode result = finishedJob(requested);
            assertTrue(result.at("/warnings/0").asString().contains(LABELS), result.toString());

            String byDefault = JSON.readTree(postJson("/api/v1/jobs/analyze-preview", request(null))
                    .getContentAsString()).get("jobId").asString();
            assertFalse(finishedJob(byDefault).has("warnings"));

            MockHttpServletResponse invalid = postJson("/api/v1/jobs/analyze-preview", request("not a key"));
            assertEquals(400, invalid.getStatus());
        }

        @Test
        @DisplayName("create-jira to a project without feature level: no feature issue, feature-… labels, warning; default project: native")
        void creationFollowsTheRequestedProject() throws Exception {
            String explicit = JSON.readTree(postJson("/api/v1/analyze-preview", request(null))
                    .getContentAsString()).get("epics").toString();

            MockHttpServletResponse labelled = mockMvc.perform(post("/api/v1/create-jira").param("projectKey", NO_FEATURE)
                    .contentType(MediaType.APPLICATION_JSON).content(explicit)).andReturn().getResponse();

            assertEquals(200, labelled.getStatus(), labelled.getContentAsString());
            verify(tracker, never()).createFeature(any(), anyString(), any());
            ArgumentCaptor<Story> stories = ArgumentCaptor.forClass(Story.class);
            verify(tracker, times(2)).createStory(stories.capture(), eq("TEST-1"), eq(NO_FEATURE));
            assertTrue(stories.getAllValues().get(0).getLabels().contains("feature-booking"),
                    stories.getAllValues().get(0).getLabels().toString());
            assertTrue(stories.getAllValues().get(1).getLabels().contains("feature-administration"));
            JsonNode body = JSON.readTree(labelled.getContentAsString());
            assertTrue(body.at("/warnings/0").asString().contains(LABELS), body.toString());

            MockHttpServletResponse nativeLevel = postJson("/api/v1/create-jira", explicit);
            assertEquals(200, nativeLevel.getStatus());
            verify(tracker, times(2)).createFeature(any(), eq("TEST-1"), isNull());
            assertFalse(JSON.readTree(nativeLevel.getContentAsString()).has("warnings"));
        }

        @Test
        @DisplayName("analyze-and-create to a project without feature level: feature-… labels and warning")
        void analyzeAndCreateFollowsTheRequestedProject() throws Exception {
            MockHttpServletResponse response = postJson("/api/v1/analyze-and-create", request(NO_FEATURE));

            assertEquals(200, response.getStatus(), response.getContentAsString());
            verify(tracker, never()).createFeature(any(), anyString(), any());
            ArgumentCaptor<Story> stories = ArgumentCaptor.forClass(Story.class);
            verify(tracker, times(2)).createStory(stories.capture(), anyString(), eq(NO_FEATURE));
            assertTrue(stories.getAllValues().get(0).getLabels().contains("feature-booking"));
            assertTrue(JSON.readTree(response.getContentAsString()).at("/warnings/0").asString().contains(LABELS));
        }
    }
}
