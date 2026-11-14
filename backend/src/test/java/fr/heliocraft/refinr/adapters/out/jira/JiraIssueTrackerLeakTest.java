// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.jira;

import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult.FailedIssue;
import fr.heliocraft.refinr.application.service.CreateBacklogService;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #33 : ni le contenu des tickets (titre, description, labels, priorite : sortie du modele), ni le corps des
 * reponses de Jira, ni le jeton ne doivent apparaitre dans les logs ou dans la trace complete d'une exception. Le
 * marqueur est unique a chaque test.
 */
@ExtendWith(OutputCaptureExtension.class)
class JiraIssueTrackerLeakTest {

    private MockWebServer server;
    private String marker;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        marker = "LEAK-MARKER-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private JiraIssueTracker jira() {
        return new JiraIssueTracker(server.url("/").toString(), "leak@example.com", "fake-token-" + marker, "TEST");
    }

    private Epic epic() {
        Epic epic = new Epic();
        epic.setName("Confidential epic " + marker);
        epic.setDescription("Specification " + marker);
        epic.setPriority("Priority" + marker);
        epic.setLabels(List.of("label " + marker));
        return epic;
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).addHeader("Content-Type", "application/json").setBody(body);
    }

    /** Creation via CreateBacklogService (qui logge les echecs) : l'element en echec et sa raison. */
    private FailedIssue failedCreation(CapturedOutput output) {
        BacklogCreationResult result = new CreateBacklogService(jira()).create(List.of(epic()), null);
        assertEquals(1, result.failed().size(), output.getAll());
        return result.failed().get(0);
    }

    private void assertNoLeak(CapturedOutput output, Throwable error) {
        assertFalse(output.getAll().contains(marker), "marker in the logs:\n" + output.getAll());
        if (error != null) {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            assertFalse(trace.toString().contains(marker), "marker in the stack trace:\n" + trace);
            assertNull(error.getCause(), "no cause (WebClient and Jackson exceptions hold URLs and bodies): " + trace);
        }
    }

    @Test
    @DisplayName("Created issue: the title is not logged")
    void created(CapturedOutput output) throws Exception {
        server.enqueue(json(201, "{\"key\":\"TEST-1\"}"));

        BacklogCreationResult result = new CreateBacklogService(jira()).create(List.of(epic()), null);

        assertTrue(result.failed().isEmpty());
        assertTrue(server.takeRequest().getBody().readUtf8().contains(marker), "the content is sent to Jira");
        assertNoLeak(output, null);
    }

    @Test
    @DisplayName("HTTP 400 quoting the content: safe log (fields, count), details for the client only")
    void validationErrorsQuotingTheContent(CapturedOutput output) {
        server.enqueue(json(400, "{\"errorMessages\":[\"Bad value " + marker + "\"],"
                + "\"errors\":{\"labels\":\"The label 'label " + marker + "' contains spaces\"}}"));

        FailedIssue failed = failedCreation(output);

        assertNoLeak(output, null);
        assertTrue(output.getAll().contains("Jira rejected the epic (HTTP 400) [fields: labels; 1 error message]"),
                output.getAll());
        assertTrue(output.getAll().contains("Failed to create epic 1"), "logged by position");
        // Raison renvoyee au client inchangee : il a envoye ce contenu et en a besoin pour corriger
        assertEquals("Jira rejected the epic (HTTP 400): Bad value " + marker + "; labels: The label 'label " + marker
                + "' contains spaces", failed.reason());
    }

    @Test
    @DisplayName("HTTP 400 with the marker outside errorMessages/errors, HTTP 500 HTML: neither logged nor returned")
    void responseBodies(CapturedOutput output) {
        server.enqueue(json(400, "{\"errorMessages\":[],\"errors\":{},\"debug\":\"" + marker + "\"}"));
        server.enqueue(new MockResponse().setResponseCode(500).addHeader("Content-Type", "text/html")
                .setBody("<html>" + marker + "</html>"));

        BacklogCreationResult result = new CreateBacklogService(jira()).create(List.of(epic(), epic()), null);

        assertEquals(List.of("Jira rejected the epic (HTTP 400)", "Jira rejected the epic (HTTP 500)"),
                result.failed().stream().map(FailedIssue::reason).toList());
        assertNoLeak(output, null);
    }

    @Test
    @DisplayName("Unreadable 2xx response: dedicated reason (it may have been created), body not logged")
    void unreadableResponse(CapturedOutput output) {
        server.enqueue(json(201, marker + " is not JSON"));

        FailedIssue failed = failedCreation(output);

        assertEquals("Jira returned an unreadable response for the epic (it may have been created)", failed.reason());
        assertFalse(output.getAll().contains("Unexpected error"), "no longer an unexpected error with a stack trace");
        assertNoLeak(output, null);
    }

    @Test
    @DisplayName("Jira unreachable: no stack trace with the URL")
    void unreachable(CapturedOutput output) throws IOException {
        server.shutdown();

        FailedIssue failed = failedCreation(output);

        assertEquals("Jira unreachable", failed.reason());
        assertNoLeak(output, null);
    }

    @Test
    @DisplayName("Reassessment: reading or updating the issue fails without body nor cause")
    void reassessment(CapturedOutput output) {
        server.enqueue(json(404, "{\"errorMessages\":[\"No issue " + marker + "\"]}"));
        RuntimeException read = assertThrows(RuntimeException.class, () -> jira().findStory("TEST-9"));
        assertEquals("Failed to read issue TEST-9: HTTP 404", read.getMessage());
        assertNoLeak(output, read);

        server.enqueue(json(200, marker + " is not JSON"));
        RuntimeException unreadable = assertThrows(RuntimeException.class, () -> jira().findStory("TEST-9"));
        assertEquals("Failed to read issue TEST-9: unreadable Jira response", unreadable.getMessage());
        assertNoLeak(output, unreadable);

        server.enqueue(json(400, "{\"errorMessages\":[],\"errors\":{\"description\":\"Too long: " + marker + "\"}}"));
        TrackedStory story = new TrackedStory("TEST-9", "Story " + marker, "Description " + marker, List.of(), null);
        RuntimeException update = assertThrows(RuntimeException.class,
                () -> jira().applyReassessment(story, "Precision " + marker, new Readiness(true, "Reason " + marker)));
        assertEquals("Failed to update issue TEST-9: HTTP 400 [fields: description]", update.getMessage());
        assertNoLeak(output, update);
    }
}
