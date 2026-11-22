// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.jira;

import fr.heliocraft.refinr.application.port.out.IssueTrackerException;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort.FeatureLevel;
import fr.heliocraft.refinr.application.service.CreateBacklogService;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Feature;
import fr.heliocraft.refinr.domain.model.Story;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Niveau Feature de Jira : deux niveaux (Free, Standard) ou trois niveaux configures (Premium). */
class JiraHierarchyTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Premium : Epic natif renomme Feature (niveau 1), nouveau type Epic au-dessus (niveau 2). */
    private static final String PREMIUM_PROJECT = """
            {"key":"TEST","issueTypes":[
              {"name":"Epic","hierarchyLevel":2},{"name":"Feature","hierarchyLevel":1},
              {"name":"Story","hierarchyLevel":0},{"name":"Subtask","hierarchyLevel":-1,"subtask":true}]}""";

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private JiraIssueTracker tracker(JiraIssueTracker.Hierarchy hierarchy, JiraIssueTracker.IssueTypes types) {
        return new JiraIssueTracker(server.url("/").toString(), "test@example.com", "fake-token", "TEST", true, "",
                hierarchy, types);
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).addHeader("Content-Type", "application/json").setBody(body);
    }

    private static MockResponse created(String key) {
        return json(201, "{\"key\":\"" + key + "\"}");
    }

    private RecordedRequest next() throws InterruptedException {
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request, "expected one more request");
        return request;
    }

    private static JsonNode fields(RecordedRequest request) {
        return JSON.readTree(request.getBody().readUtf8()).get("fields");
    }

    private static Story story(String title) {
        Story story = new Story();
        story.setTitle(title);
        story.setAiReady(true);
        return story;
    }

    private static Feature feature(String name, Story... stories) {
        Feature feature = new Feature();
        feature.setName(name);
        feature.setStories(new ArrayList<>(List.of(stories)));
        return feature;
    }

    private static Epic epic(Feature... features) {
        Epic epic = new Epic();
        epic.setName("Room booking");
        epic.setFeatures(new ArrayList<>(List.of(features)));
        return epic;
    }

    @Test
    @DisplayName("two-levels (default): no feature level; stories under the epic with a feature-<name> label")
    void twoLevelsKeepsFeaturesAsLabels() throws Exception {
        JiraIssueTracker jira = tracker(JiraIssueTracker.Hierarchy.TWO_LEVELS, JiraIssueTracker.IssueTypes.DEFAULT);
        server.enqueue(created("TEST-1"));
        server.enqueue(created("TEST-2"));
        server.enqueue(created("TEST-3"));

        var result = new CreateBacklogService(jira).create(List.of(
                epic(feature("Booking", story("Book a room")), feature("Administration", story("Add a room")))), null);

        assertEquals(FeatureLevel.NONE, jira.featureLevel(null));
        assertEquals(1, result.warnings().size());
        JsonNode epicFields = fields(next());
        assertEquals("Epic", epicFields.at("/issuetype/name").asString());
        JsonNode storyFields = fields(next());
        assertEquals("Story", storyFields.at("/issuetype/name").asString());
        assertEquals("TEST-1", storyFields.at("/parent/key").asString());
        assertTrue(storyFields.get("labels").toString().contains("\"feature-booking\""), storyFields.toString());
        assertTrue(fields(next()).get("labels").toString().contains("\"feature-administration\""));
        assertEquals(3, server.getRequestCount(), "no feature issue");
    }

    @Test
    @DisplayName("Issue type names are configurable (jira.types.*), defaults unchanged")
    void typeNamesAreConfigurable() throws Exception {
        JiraIssueTracker jira = tracker(JiraIssueTracker.Hierarchy.TWO_LEVELS,
                new JiraIssueTracker.IssueTypes("Initiative", null, "User Story", "Sub-task"));
        server.enqueue(created("TEST-1"));
        server.enqueue(created("TEST-2"));
        server.enqueue(created("TEST-3"));
        Epic epic = new Epic();
        epic.setName("E");
        Story story = story("S");
        story.setTasks(List.of(new fr.heliocraft.refinr.domain.model.Task()));
        story.getTasks().getFirst().setTitle("T");
        epic.setStories(List.of(story));

        new CreateBacklogService(jira).create(List.of(epic), null);

        assertEquals("Initiative", fields(next()).at("/issuetype/name").asString());
        assertEquals("User Story", fields(next()).at("/issuetype/name").asString());
        assertEquals("Sub-task", fields(next()).at("/issuetype/name").asString());
    }

    @Test
    @DisplayName("three-levels: project checked once, epic -> feature (parent epic) -> story (parent feature)")
    void threeLevelsCreatesFeatures() throws Exception {
        JiraIssueTracker jira = tracker(JiraIssueTracker.Hierarchy.THREE_LEVELS, JiraIssueTracker.IssueTypes.DEFAULT);
        server.enqueue(json(200, PREMIUM_PROJECT));
        server.enqueue(created("TEST-1"));
        server.enqueue(created("TEST-2"));
        server.enqueue(created("TEST-3"));
        server.enqueue(created("TEST-4"));
        server.enqueue(created("TEST-5"));

        var result = new CreateBacklogService(jira).create(List.of(
                epic(feature("Booking", story("Book a room")), feature("Administration", story("Add a room")))), null);

        assertEquals(FeatureLevel.REQUIRED, jira.featureLevel(null));
        assertTrue(result.warnings().isEmpty());
        assertTrue(result.failed().isEmpty(), result.failed().toString());
        assertEquals("/rest/api/3/project/TEST", next().getPath());
        assertEquals("Epic", fields(next()).at("/issuetype/name").asString());
        JsonNode featureFields = fields(next());
        assertEquals("Feature", featureFields.at("/issuetype/name").asString());
        assertEquals("Booking", featureFields.at("/summary").asString());
        assertEquals("TEST-1", featureFields.at("/parent/key").asString());
        JsonNode storyFields = fields(next());
        assertEquals("TEST-2", storyFields.at("/parent/key").asString());
        assertFalse(storyFields.get("labels").toString().contains("feature-"), "no label with a real feature level");
        assertEquals(List.of("epic", "feature", "story", "feature", "story"),
                result.created().stream().map(issue -> issue.type()).toList());
    }

    @Test
    @DisplayName("three-levels: the implicit feature (only one of its epic) gets an issue named after the epic")
    void threeLevelsCreatesTheImplicitFeature() throws Exception {
        JiraIssueTracker jira = tracker(JiraIssueTracker.Hierarchy.THREE_LEVELS, JiraIssueTracker.IssueTypes.DEFAULT);
        server.enqueue(json(200, PREMIUM_PROJECT));
        server.enqueue(created("TEST-1"));
        server.enqueue(created("TEST-2"));
        server.enqueue(created("TEST-3"));
        Epic epic = new Epic();
        epic.setName("Order export");
        epic.setStories(List.of(story("Export the orders")));

        new CreateBacklogService(jira).create(List.of(epic), null);

        next();
        next();
        JsonNode featureFields = fields(next());
        assertEquals("Feature", featureFields.at("/issuetype/name").asString());
        assertEquals("Order export", featureFields.at("/summary").asString());
        assertEquals("TEST-2", fields(next()).at("/parent/key").asString());
    }

    @Test
    @DisplayName("three-levels misconfigured (missing type, wrong level): epic refused with the properties to fix, nothing created")
    void threeLevelsValidation() throws Exception {
        JiraIssueTracker jira = tracker(JiraIssueTracker.Hierarchy.THREE_LEVELS, JiraIssueTracker.IssueTypes.DEFAULT);
        server.enqueue(json(200, """
                {"key":"TEST","issueTypes":[{"name":"Epic","hierarchyLevel":1},{"name":"Story","hierarchyLevel":0},
                  {"name":"Subtask","hierarchyLevel":-1}]}"""));

        IssueTrackerException missing = assertThrows(IssueTrackerException.class,
                () -> jira.createEpic(epic(), null));
        assertTrue(missing.getMessage().contains("jira.types.feature=Feature is not an issue type of project TEST"),
                missing.getMessage());

        server.enqueue(json(200, """
                {"key":"TEST","issueTypes":[{"name":"Epic","hierarchyLevel":1},{"name":"Feature","hierarchyLevel":1},
                  {"name":"Story","hierarchyLevel":0},{"name":"Subtask","hierarchyLevel":-1}]}"""));
        IssueTrackerException levels = assertThrows(IssueTrackerException.class, () -> jira.createEpic(epic(), null));
        assertTrue(levels.getMessage().contains("jira.types.epic=Epic must be one level above Feature"), levels.getMessage());
        assertEquals(2, server.getRequestCount(), "only the project reads, no issue posted");
    }

    @Test
    @DisplayName("An unknown jira.hierarchy is refused at startup")
    void unknownHierarchy() {
        assertThrows(IllegalStateException.class, () -> JiraIssueTracker.Hierarchy.of("four-levels"));
        assertEquals(JiraIssueTracker.Hierarchy.TWO_LEVELS, JiraIssueTracker.Hierarchy.of(""));
        assertEquals(JiraIssueTracker.Hierarchy.THREE_LEVELS, JiraIssueTracker.Hierarchy.of("Three-Levels"));
    }
}
