// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.openproject;

import fr.heliocraft.refinr.application.port.out.IssueTrackerException;
import fr.heliocraft.refinr.domain.model.ComponentHealth;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adaptateur OpenProject contre un serveur HTTP simule. Les reponses reprennent la forme relevee contre
 * OpenProject 17.9.7 en v1.1 phase 0 (cf. DECISIONS.md).
 */
class OpenProjectIssueTrackerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String API_KEY = "test-api-key";

    static final String TYPES = """
            {"_embedded":{"elements":[
              {"id":1,"name":"Task","_links":{"self":{"href":"/api/v3/types/1"}}},
              {"id":5,"name":"Epic","_links":{"self":{"href":"/api/v3/types/5"}}},
              {"id":6,"name":"User story","_links":{"self":{"href":"/api/v3/types/6"}}}]}}""";

    static final String FORM = """
            {"_embedded":{"schema":{
              "subject":{"name":"Subject"},
              "customField1":{"name":"Agent readiness","type":"CustomOption","_embedded":{"allowedValues":[
                {"value":"ai-ready","_links":{"self":{"href":"/api/v3/custom_options/1"}}},
                {"value":"validation-necessaire","_links":{"self":{"href":"/api/v3/custom_options/2"}}}]}},
              "priority":{"_embedded":{"allowedValues":[
                {"name":"Low","_links":{"self":{"href":"/api/v3/priorities/7"}}},
                {"name":"Normal","_links":{"self":{"href":"/api/v3/priorities/8"}}},
                {"name":"High","_links":{"self":{"href":"/api/v3/priorities/9"}}},
                {"name":"Immediate","_links":{"self":{"href":"/api/v3/priorities/10"}}}]}}}}}""";

    private MockWebServer server;
    private OpenProjectIssueTracker tracker;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        tracker = tracker(true, true, Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private OpenProjectIssueTracker tracker(boolean storyPoints, boolean priority, Duration timeout) {
        return new OpenProjectIssueTracker(server.url("/").toString(), API_KEY, "refinr-spike", "Epic", "User story",
                "Task", "Agent readiness", storyPoints, priority, timeout);
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).addHeader("Content-Type", "application/hal+json")
                .setBody(body);
    }

    private static MockResponse created(long id) {
        return json(201, "{\"_type\":\"WorkPackage\",\"id\":" + id + ",\"lockVersion\":0}");
    }

    private static MockResponse error(int status, String code, String attribute, String message) {
        String details = attribute == null ? "" : ",\"_embedded\":{\"details\":{\"attribute\":\"" + attribute + "\"}}";
        return json(status, "{\"_type\":\"Error\",\"errorIdentifier\":\"urn:openproject-org:api:v3:errors:" + code
                + "\",\"message\":\"" + message + "\"" + details + "}");
    }

    static String workPackage(long id, int lockVersion, String verdict) {
        String verdictLink = verdict == null ? "{\"href\":null}"
                : "{\"href\":\"/api/v3/custom_options/" + ("ai-ready".equals(verdict) ? 1 : 2) + "\",\"title\":\""
                + verdict + "\"}";
        return "{\"_type\":\"WorkPackage\",\"id\":" + id + ",\"subject\":\"Low-stock alerts\",\"lockVersion\":"
                + lockVersion + ",\"description\":{\"format\":\"markdown\",\"raw\":\"Alert when stock is low\\n\"},"
                + "\"_links\":{\"self\":{\"href\":\"/api/v3/work_packages/" + id + "\"},"
                + "\"project\":{\"href\":\"/api/v3/projects/3\",\"title\":\"Refinr spike\"},"
                + "\"customField1\":" + verdictLink + "}}";
    }

    private static JsonNode body(RecordedRequest request) {
        return JSON.readTree(request.getBody().readUtf8());
    }

    private RecordedRequest next() throws InterruptedException {
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertTrue(request != null, "expected one more request");
        return request;
    }

    private static Epic epic() {
        Epic epic = new Epic();
        epic.setName("Stock management");
        epic.setDescription("Track stock levels per warehouse.");
        epic.setPriority("High");
        epic.setLabels(List.of("Back End", "ai-ready"));
        return epic;
    }

    private static Story story(Boolean aiReady) {
        Story story = new Story();
        story.setTitle("Low-stock alerts");
        story.setAsA("warehouse manager");
        story.setIWant("an alert when stock is low");
        story.setSoThat("I can reorder in time");
        story.setAcceptanceCriteria(List.of("Given a threshold, When stock goes below, Then an alert is sent"));
        story.setPriority("Medium");
        story.setEstimatedStoryPoints(5);
        story.setLabels(List.of("alerts"));
        story.setAiReady(aiReady);
        story.setAiReadyReason("The alert channel is not specified.");
        return story;
    }

    private static Task task() {
        Task task = new Task();
        task.setTitle("Compute thresholds");
        task.setDescription("Per product.");
        return task;
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("Epic -> story -> task: one POST each, parents linked, names resolved once per project")
        void createsTheHierarchy() throws Exception {
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            server.enqueue(created(10));
            server.enqueue(created(11));
            server.enqueue(created(12));

            assertEquals("10", tracker.createEpic(epic(), null));
            assertEquals("11", tracker.createStory(story(null), "10", null));
            assertEquals("12", tracker.createSubtask(task(), "11", null));
            assertEquals(5, server.getRequestCount(), "types and form read once, then three POSTs");

            RecordedRequest types = next();
            assertEquals("GET", types.getMethod());
            assertEquals("/api/v3/projects/refinr-spike/types", types.getPath());
            String expectedAuth = "Basic " + Base64.getEncoder()
                    .encodeToString(("apikey:" + API_KEY).getBytes(StandardCharsets.UTF_8));
            assertEquals(expectedAuth, types.getHeader("Authorization"));

            RecordedRequest form = next();
            assertEquals("POST", form.getMethod());
            assertEquals("/api/v3/projects/refinr-spike/work_packages/form", form.getPath());
            assertEquals("/api/v3/types/6", body(form).at("/_links/type/href").asString());

            RecordedRequest epicRequest = next();
            assertEquals("/api/v3/work_packages?notify=false", epicRequest.getPath());
            JsonNode epic = body(epicRequest);
            assertEquals("Stock management", epic.at("/subject").asString());
            assertEquals("markdown", epic.at("/description/format").asString());
            assertEquals("Track stock levels per warehouse.\n\n**Labels:** back-end",
                    epic.at("/description/raw").asString(), "verdict label never copied to the labels line");
            assertEquals("/api/v3/projects/refinr-spike", epic.at("/_links/project/href").asString());
            assertEquals("/api/v3/types/5", epic.at("/_links/type/href").asString());
            assertEquals("/api/v3/priorities/9", epic.at("/_links/priority/href").asString());
            assertTrue(epic.at("/_links/parent").isMissingNode());
            assertTrue(epic.at("/_links/customField1").isMissingNode());
            assertTrue(epic.at("/storyPoints").isMissingNode(), "no story points on an epic");

            JsonNode story = body(next());
            assertEquals("/api/v3/types/6", story.at("/_links/type/href").asString());
            assertEquals("/api/v3/work_packages/10", story.at("/_links/parent/href").asString());
            assertEquals("/api/v3/custom_options/2", story.at("/_links/customField1/href").asString(),
                    "no explicit true from the analysis: validation-necessaire");
            assertEquals("/api/v3/priorities/8", story.at("/_links/priority/href").asString(), "Medium -> Normal");
            assertEquals(5, story.at("/storyPoints").asInt());
            String raw = story.at("/description/raw").asString();
            assertTrue(raw.startsWith("**As a** warehouse manager\n"), raw);
            assertTrue(raw.contains("- **AC1:** Given a threshold"), raw);
            assertTrue(raw.contains("AI readiness (validation-necessaire): The alert channel is not specified."), raw);
            assertTrue(raw.endsWith("\n\n**Labels:** alerts"), raw);

            JsonNode task = body(next());
            assertEquals("/api/v3/types/1", task.at("/_links/type/href").asString());
            assertEquals("/api/v3/work_packages/11", task.at("/_links/parent/href").asString());
            assertEquals("Per product.", task.at("/description/raw").asString());
            assertTrue(task.at("/storyPoints").isMissingNode(), "no story points on a task");
            assertTrue(task.at("/_links/customField1").isMissingNode());
        }

        @Test
        @DisplayName("An explicit true gives ai-ready; story points and priority are not sent when disabled")
        void aiReadyWithoutPointsNorPriority() throws Exception {
            OpenProjectIssueTracker plain = tracker(false, false, Duration.ofSeconds(5));
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            server.enqueue(created(11));

            plain.createStory(story(true), "10", null);

            next();
            next();
            JsonNode story = body(next());
            assertEquals("/api/v3/custom_options/1", story.at("/_links/customField1/href").asString());
            assertTrue(story.at("/storyPoints").isMissingNode());
            assertTrue(story.at("/_links/priority").isMissingNode());
        }

        @Test
        @DisplayName("An unknown priority is left to the OpenProject default")
        void unknownPriorityIsNotSent() throws Exception {
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            server.enqueue(created(10));
            Epic epic = epic();
            epic.setPriority("Blocker");

            tracker.createEpic(epic, null);

            next();
            next();
            assertTrue(body(next()).at("/_links/priority").isMissingNode());
        }

        @Test
        @DisplayName("The project of the request replaces the default project")
        void requestedProject() throws Exception {
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            server.enqueue(created(10));

            tracker.createEpic(epic(), "other-project");

            assertEquals("/api/v3/projects/other-project/types", next().getPath());
            next();
            assertEquals("/api/v3/projects/other-project", body(next()).at("/_links/project/href").asString());
        }
    }

    @Nested
    class Resolution {

        @Test
        @DisplayName("A type not enabled in the project: explicit error listing the 3 admin settings, nothing cached")
        void missingType() {
            server.enqueue(json(200, "{\"_embedded\":{\"elements\":[{\"name\":\"Task\",\"_links\":{\"self\":"
                    + "{\"href\":\"/api/v3/types/1\"}}}]}}"));

            IssueTrackerException error = assertThrows(IssueTrackerException.class,
                    () -> tracker.createEpic(epic(), null));

            assertTrue(error.getMessage().contains("type 'Epic' is not enabled in project refinr-spike"),
                    error.getMessage());
            assertSetupHint(error.getMessage());
            assertEquals(1, server.getRequestCount());

            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            server.enqueue(created(10));
            assertEquals("10", tracker.createEpic(epic(), null), "resolved again once the admin fixed it");
        }

        @Test
        @DisplayName("Readiness field absent from the story form: explicit error with the 3 admin settings")
        void missingField() {
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, "{\"_embedded\":{\"schema\":{\"subject\":{\"name\":\"Subject\"}}}}"));

            IssueTrackerException error = assertThrows(IssueTrackerException.class,
                    () -> tracker.createEpic(epic(), null));

            assertTrue(error.getMessage().contains("field 'Agent readiness' not found for type 'User story'"),
                    error.getMessage());
            assertSetupHint(error.getMessage());
        }

        @Test
        @DisplayName("Readiness option missing: explicit error naming the option")
        void missingOption() {
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM.replace("validation-necessaire", "to-check")));

            IssueTrackerException error = assertThrows(IssueTrackerException.class,
                    () -> tracker.createEpic(epic(), null));

            assertTrue(error.getMessage().contains("has no option 'validation-necessaire'"), error.getMessage());
            assertSetupHint(error.getMessage());
        }

        private void assertSetupHint(String message) {
            assertTrue(message.contains("(1) a list custom field 'Agent readiness' exists with the options ai-ready"
                    + " and validation-necessaire"), message);
            assertTrue(message.contains("(2) it is enabled in project refinr-spike"), message);
            assertTrue(message.contains("(3) it is added to the form of the types Epic, User story and Task"), message);
        }
    }

    @Nested
    class Errors {

        @Test
        @DisplayName("401: Unauthenticated, without the key in the message")
        void unauthenticated() {
            server.enqueue(error(401, "Unauthenticated", null, "Vous devez être authentifié."));

            IssueTrackerException error = assertThrows(IssueTrackerException.class,
                    () -> tracker.createEpic(epic(), null));

            assertTrue(error.getMessage().contains("HTTP 401 Unauthenticated"), error.getMessage());
            assertFalse(error.getMessage().contains(API_KEY));
        }

        @Test
        @DisplayName("403 on creation: MissingPermission, no retry")
        void missingPermission() {
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            server.enqueue(error(403, "MissingPermission", null, "Vous n'êtes pas autorisé."));

            IssueTrackerException error = assertThrows(IssueTrackerException.class,
                    () -> tracker.createEpic(epic(), null));

            assertTrue(error.getMessage().startsWith("OpenProject rejected the epic (HTTP 403 MissingPermission"),
                    error.getMessage());
            assertEquals(3, server.getRequestCount());
        }

        @Test
        @DisplayName("422 on customFieldN: setup hint, project cache invalidated, POST not retried")
        void constraintOnCustomFieldInvalidatesTheCache() throws Exception {
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            server.enqueue(error(422, "PropertyConstraintViolation", "customField1",
                    "Agent readiness ne correspond à aucune des valeurs autorisées."));

            IssueTrackerException error = assertThrows(IssueTrackerException.class,
                    () -> tracker.createStory(story(null), "10", null));

            assertTrue(error.getMessage().contains("HTTP 422 PropertyConstraintViolation on customField1"),
                    error.getMessage());
            assertTrue(error.getMessage().contains("(3) it is added to the form of the types"), error.getMessage());
            assertEquals(3, server.getRequestCount(), "the POST is not retried");

            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            server.enqueue(created(10));
            tracker.createEpic(epic(), null);
            for (int i = 0; i < 3; i++) {
                next();
            }
            assertEquals("/api/v3/projects/refinr-spike/types", next().getPath(), "the next request resolves again");
        }

        @Test
        @DisplayName("422 on type also invalidates the cache")
        void constraintOnTypeInvalidatesTheCache() {
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            server.enqueue(error(422, "PropertyConstraintViolation", "type", "Type invalide."));
            assertThrows(IssueTrackerException.class, () -> tracker.createEpic(epic(), null));

            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            server.enqueue(created(10));
            tracker.createEpic(epic(), null);
            assertEquals(6, server.getRequestCount());
        }

        @Test
        @DisplayName("422 PropertyIsReadOnly (e.g. parent without manage_subtasks): reported, cache kept")
        void readOnlyProperty() {
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            server.enqueue(error(422, "PropertyIsReadOnly", "parent", "Parent est en lecture seule."));

            IssueTrackerException error = assertThrows(IssueTrackerException.class,
                    () -> tracker.createSubtask(task(), "11", null));
            assertTrue(error.getMessage().contains("HTTP 422 PropertyIsReadOnly on parent"), error.getMessage());
            assertFalse(error.getMessage().contains("Check the OpenProject setup"), error.getMessage());

            server.enqueue(created(12));
            assertEquals("12", tracker.createSubtask(task(), "11", null));
            assertEquals(4, server.getRequestCount(), "setup still cached");
        }

        @Test
        @DisplayName("422 MultipleErrors: every sub-error code and attribute is reported")
        void multipleErrors() {
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            server.enqueue(json(422, """
                    {"_type":"Error","errorIdentifier":"urn:openproject-org:api:v3:errors:MultipleErrors",
                     "message":"Plusieurs contraintes de champ n'ont pas été respectées.",
                     "_embedded":{"errors":[
                       {"errorIdentifier":"urn:openproject-org:api:v3:errors:PropertyConstraintViolation",
                        "message":"Parent n'existe pas.","_embedded":{"details":{"attribute":"parent"}}},
                       {"errorIdentifier":"urn:openproject-org:api:v3:errors:PropertyConstraintViolation",
                        "message":"Sujet ne peut pas être vide.","_embedded":{"details":{"attribute":"subject"}}}]}}"""));

            IssueTrackerException error = assertThrows(IssueTrackerException.class,
                    () -> tracker.createSubtask(task(), "999", null));

            assertTrue(error.clientMessage().contains("HTTP 422 MultipleErrors, PropertyConstraintViolation, "
                    + "PropertyConstraintViolation on parent, subject"), error.clientMessage());
            assertTrue(error.clientMessage().contains("Parent n'existe pas."), error.clientMessage());
            // Logs et traces : statut, codes et attributs, sans les messages d'OpenProject (#33)
            assertEquals("OpenProject rejected the subtask (HTTP 422 MultipleErrors, PropertyConstraintViolation, "
                    + "PropertyConstraintViolation on parent, subject)", error.getMessage());
            assertNull(error.getCause());
        }

        @Test
        @DisplayName("No answer within the delay: explicit timeout (the issue may have been created)")
        void timeout() {
            OpenProjectIssueTracker slow = tracker(true, true, Duration.ofMillis(300));
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            server.enqueue(created(10).setHeadersDelay(2, TimeUnit.SECONDS));

            IssueTrackerException error = assertThrows(IssueTrackerException.class,
                    () -> slow.createEpic(epic(), null));

            assertTrue(error.getMessage().startsWith("OpenProject did not answer within"), error.getMessage());
            assertTrue(error.getMessage().contains("(it may have been created)"), error.getMessage());
        }

        @Test
        @DisplayName("A non numeric work package id is refused without any call")
        void nonNumericId() {
            assertThrows(IssueTrackerException.class, () -> tracker.findStory("PROJ-1"));
            assertThrows(IssueTrackerException.class, () -> tracker.findStory("../projects"));
            assertEquals(0, server.getRequestCount());
        }
    }

    @Nested
    class Reassessment {

        @Test
        @DisplayName("findStory: subject, raw description and the verdict option as label")
        void findStory() throws Exception {
            server.enqueue(json(200, workPackage(39, 0, "validation-necessaire")));
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));

            TrackedStory story = tracker.findStory("39");

            assertEquals("39", story.key());
            assertEquals("Low-stock alerts", story.summary());
            assertEquals("Alert when stock is low", story.description());
            assertEquals(List.of("validation-necessaire"), story.labels());
            assertNull(story.originalContent());
            assertEquals("/api/v3/work_packages/39", next().getPath());
            assertEquals("/api/v3/projects/3/types", next().getPath(), "setup of the project of the work package");
        }

        @Test
        @DisplayName("findStory without verdict: no label (the story is not awaiting validation)")
        void findStoryWithoutVerdict() {
            server.enqueue(json(200, workPackage(39, 0, null)));
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));

            assertEquals(List.of(), tracker.findStory("39").labels());
        }

        private TrackedStory found() throws InterruptedException {
            server.enqueue(json(200, workPackage(39, 0, "validation-necessaire")));
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            TrackedStory story = tracker.findStory("39");
            for (int i = 0; i < 3; i++) {
                next();
            }
            return story;
        }

        @Test
        @DisplayName("Comment, read again (fresh lockVersion), then PATCH of the verdict")
        void commentThenReadThenPatch() throws Exception {
            TrackedStory story = found();
            server.enqueue(json(201, "{\"_type\":\"Activity::Comment\",\"id\":64}"));
            server.enqueue(json(200, workPackage(39, 1, "validation-necessaire")));
            server.enqueue(json(200, workPackage(39, 2, "ai-ready")));

            tracker.applyReassessment(story, "Alerts go to Slack #stock", new Readiness(true, "Channel now known."));

            RecordedRequest comment = next();
            assertEquals("POST", comment.getMethod());
            assertEquals("/api/v3/work_packages/39/activities?notify=false", comment.getPath());
            assertEquals("Precision de l'utilisateur (reevaluation): Alerts go to Slack #stock\n\n"
                    + "AI readiness (ai-ready): Channel now known.", body(comment).at("/comment/raw").asString());

            RecordedRequest read = next();
            assertEquals("GET", read.getMethod());
            assertEquals("/api/v3/work_packages/39", read.getPath());

            RecordedRequest patch = next();
            assertEquals("PATCH", patch.getMethod());
            assertEquals("/api/v3/work_packages/39?notify=false", patch.getPath());
            JsonNode body = body(patch);
            assertEquals(1, body.at("/lockVersion").asInt(), "lockVersion read after the comment");
            assertEquals("/api/v3/custom_options/1", body.at("/_links/customField1/href").asString());
        }

        @Test
        @DisplayName("409 on PATCH: read again and retry once")
        void conflictRetriedOnce() throws Exception {
            TrackedStory story = found();
            server.enqueue(json(201, "{}"));
            server.enqueue(json(200, workPackage(39, 1, "validation-necessaire")));
            server.enqueue(error(409, "UpdateConflict", null, "Conflit."));
            server.enqueue(json(200, workPackage(39, 2, "validation-necessaire")));
            server.enqueue(json(200, workPackage(39, 3, "validation-necessaire")));

            tracker.applyReassessment(story, "precision", new Readiness(false, "Still unclear."));

            next();
            next();
            assertEquals(1, body(next()).at("/lockVersion").asInt());
            assertEquals("GET", next().getMethod());
            JsonNode retry = body(next());
            assertEquals(2, retry.at("/lockVersion").asInt());
            assertEquals("/api/v3/custom_options/2", retry.at("/_links/customField1/href").asString());
        }

        @Test
        @DisplayName("409 twice: failure after a single retry")
        void conflictTwice() throws Exception {
            TrackedStory story = found();
            server.enqueue(json(201, "{}"));
            server.enqueue(json(200, workPackage(39, 1, "validation-necessaire")));
            server.enqueue(error(409, "UpdateConflict", null, "Conflit."));
            server.enqueue(json(200, workPackage(39, 2, "validation-necessaire")));
            server.enqueue(error(409, "UpdateConflict", null, "Conflit."));

            IssueTrackerException error = assertThrows(IssueTrackerException.class,
                    () -> tracker.applyReassessment(story, "precision", new Readiness(true, "ok")));

            assertTrue(error.getMessage().contains("conflict persisted after one retry"), error.getMessage());
            assertEquals(3 + 5, server.getRequestCount());
        }

        @Test
        @DisplayName("422 on the verdict PATCH (field recreated by the admin): setup hint, cache invalidated, no retry")
        void constraintOnPatchInvalidatesTheCache() throws Exception {
            TrackedStory story = found();
            server.enqueue(json(201, "{}"));
            server.enqueue(json(200, workPackage(39, 1, "validation-necessaire")));
            server.enqueue(error(422, "PropertyConstraintViolation", "customField1", "Valeur non autorisée."));

            IssueTrackerException error = assertThrows(IssueTrackerException.class,
                    () -> tracker.applyReassessment(story, "precision", new Readiness(true, "ok")));
            assertTrue(error.getMessage().contains("HTTP 422 PropertyConstraintViolation on customField1"),
                    error.getMessage());
            assertTrue(error.getMessage().contains("(2) it is enabled in project 3"), error.getMessage());
            assertEquals(3 + 3, server.getRequestCount(), "the PATCH is not retried");

            server.enqueue(json(200, workPackage(39, 2, "validation-necessaire")));
            server.enqueue(json(200, TYPES));
            server.enqueue(json(200, FORM));
            tracker.findStory("39");
            for (int i = 0; i < 4; i++) {
                next();
            }
            assertEquals("/api/v3/projects/3/types", next().getPath(), "the next read resolves the project again");
        }

        @Test
        @DisplayName("404 on the comment: suggests the missing add_work_package_comments permission, no PATCH")
        void commentNotFoundSuggestsPermission() throws Exception {
            TrackedStory story = found();
            server.enqueue(error(404, "NotFound", null, "Introuvable."));

            IssueTrackerException error = assertThrows(IssueTrackerException.class,
                    () -> tracker.applyReassessment(story, "precision", new Readiness(true, "ok")));

            assertTrue(error.getMessage().contains("add_work_package_comments"), error.getMessage());
            assertEquals(4, server.getRequestCount());
        }
    }

    @Nested
    class Health {

        @Test
        @DisplayName("Empty default project: DOWN without any call")
        void emptyProject() {
            OpenProjectIssueTracker noProject = new OpenProjectIssueTracker(server.url("/").toString(), API_KEY, "",
                    "Epic", "User story", "Task", "Agent readiness", true, true, Duration.ofSeconds(5));

            ComponentHealth health = noProject.health().get(0);

            assertFalse(health.up());
            assertEquals("openproject.project not set", health.details().get("error"));
            assertEquals(0, server.getRequestCount());
        }

        @Test
        @DisplayName("UP when the default project is readable")
        void up() throws Exception {
            server.enqueue(json(200, "{\"id\":3,\"identifier\":\"refinr-spike\"}"));

            List<ComponentHealth> health = tracker.health();

            assertEquals(1, health.size());
            assertEquals("openproject", health.get(0).name());
            assertTrue(health.get(0).up());
            assertEquals("/api/v3/projects/refinr-spike", next().getPath());
        }

        @Test
        @DisplayName("DOWN with a short cause, never the key")
        void down() {
            server.enqueue(error(401, "Unauthenticated", null, "Non authentifié."));

            ComponentHealth health = tracker.health().get(0);

            assertFalse(health.up());
            assertEquals("HTTP 401", health.details().get("error"));
        }
    }

    @Test
    @DisplayName("Labels line: normalized labels without the verdict, alone when there is no description")
    void labelsLine() {
        assertEquals("Text\n\n**Labels:** back-end, api",
                OpenProjectIssueTracker.withLabels("Text", List.of("Back End", "API", "validation-necessaire")));
        assertEquals("**Labels:** api", OpenProjectIssueTracker.withLabels(null, List.of("api")));
        assertEquals("Text", OpenProjectIssueTracker.withLabels("Text", List.of("ai-ready")));
        assertEquals("", OpenProjectIssueTracker.withLabels(null, null));
    }

    @Test
    @DisplayName("Identity of the tracker and OpenProject project format")
    void describesOpenProjectProjects() {
        assertEquals("OpenProject", tracker.displayName());
        assertTrue(tracker.acceptsProjectId("refinr-spike"));
        assertTrue(tracker.acceptsProjectId("3"));
        assertFalse(tracker.acceptsProjectId("PROJ"));
    }

    @Test
    @DisplayName("Web address of a work package: {instance}/work_packages/{id}, only for numeric ids")
    void issueUrl() {
        OpenProjectIssueTracker withSlash = new OpenProjectIssueTracker("https://op.example.com/", API_KEY, "p", "Epic",
                "User story", "Task", "Agent readiness", true, true, Duration.ofSeconds(5));
        assertEquals("https://op.example.com/work_packages/39", withSlash.issueUrl("39"));
        assertNull(withSlash.issueUrl("PROJ-1"));
        assertNull(withSlash.issueUrl(null));
    }

    @Test
    @DisplayName("openproject.web-url, when set, replaces base-url in the links only (API calls keep base-url)")
    void webUrlForLinksOnly() throws Exception {
        OpenProjectIssueTracker internal = new OpenProjectIssueTracker(server.url("/").toString(), API_KEY,
                "refinr-spike", "Epic", "User story", "Task", "Agent readiness", true, true, Duration.ofSeconds(5),
                "http://localhost:8090/");
        assertEquals("http://localhost:8090/work_packages/39", internal.issueUrl("39"));

        server.enqueue(json(200, "{\"id\":3}"));
        assertTrue(internal.health().get(0).up());
        assertEquals("/api/v3/projects/refinr-spike", next().getPath(), "calls still go to base-url");

        OpenProjectIssueTracker blank = new OpenProjectIssueTracker("https://op.example.com", API_KEY, "p", "Epic",
                "User story", "Task", "Agent readiness", true, true, Duration.ofSeconds(5), " ");
        assertEquals("https://op.example.com/work_packages/39", blank.issueUrl("39"), "empty web-url: base-url");
    }
}
