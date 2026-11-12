package fr.heliocraft.refinr.adapters.out.jira;

import fr.heliocraft.refinr.domain.model.ComponentHealth;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class JiraHealthTest {

    private MockWebServer server;
    private JiraIssueTracker tracker;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        tracker = new JiraIssueTracker(server.url("/").toString(), "u", "t", "PROJ");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("UP when the default project can be read")
    void up() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        assertTrue(tracker.health().get(0).up());
        assertEquals("/rest/api/3/project/PROJ", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("DOWN with HTTP 404 when the project does not exist, without the response body")
    void projectMissing() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"errorMessages\":[\"no project\"]}"));

        ComponentHealth health = tracker.health().get(0);

        assertEquals("jira", health.name());
        assertEquals("HTTP 404", health.details().get("error"));
        assertFalse(health.toString().contains("no project"));
    }

    @Test
    @DisplayName("DOWN 'unreachable' when Jira cannot be reached")
    void unreachable() throws IOException {
        server.shutdown();

        assertEquals("unreachable", tracker.health().get(0).details().get("error"));
    }
}
