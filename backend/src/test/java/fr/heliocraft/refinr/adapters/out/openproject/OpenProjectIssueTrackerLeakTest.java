// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.openproject;

import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult.FailedIssue;
import fr.heliocraft.refinr.application.port.out.IssueTrackerException;
import fr.heliocraft.refinr.application.service.CreateBacklogService;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
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
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #33 : ni le contenu des work packages (titre, description, labels, priorite : sortie du modele), ni le corps
 * des reponses d'OpenProject, ni la cle d'API ne doivent apparaitre dans les logs ou dans la trace complete d'une
 * exception. Le marqueur est unique a chaque test.
 */
@ExtendWith(OutputCaptureExtension.class)
class OpenProjectIssueTrackerLeakTest {

    private static final String VIOLATION = "urn:openproject-org:api:v3:errors:PropertyConstraintViolation";

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

    private OpenProjectIssueTracker tracker() {
        return new OpenProjectIssueTracker(server.url("/").toString(), "fake-key-" + marker, "refinr-spike", "Epic",
                "User story", "Task", "Agent readiness", true, true, Duration.ofSeconds(2));
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
        return new MockResponse().setResponseCode(status).addHeader("Content-Type", "application/hal+json")
                .setBody(body);
    }

    private void setupResponses() {
        server.enqueue(json(200, OpenProjectIssueTrackerTest.TYPES));
        server.enqueue(json(200, OpenProjectIssueTrackerTest.FORM));
    }

    /** Creation via CreateBacklogService (qui logge les echecs) : l'element en echec et sa raison. */
    private FailedIssue failedCreation(CapturedOutput output) {
        BacklogCreationResult result = new CreateBacklogService(tracker()).create(List.of(epic()), null);
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
    @DisplayName("Created work package: neither the title nor the unknown priority is logged")
    void created(CapturedOutput output) throws Exception {
        setupResponses();
        server.enqueue(json(201, "{\"_type\":\"WorkPackage\",\"id\":10,\"lockVersion\":0}"));

        BacklogCreationResult result = new CreateBacklogService(tracker()).create(List.of(epic()), null);

        assertTrue(result.failed().isEmpty());
        assertTrue(output.getAll().contains("Unknown OpenProject priority"), output.getAll());
        server.takeRequest();
        server.takeRequest();
        assertTrue(server.takeRequest().getBody().readUtf8().contains(marker), "the content is sent to OpenProject");
        assertNoLeak(output, null);
    }

    @Test
    @DisplayName("HTTP 422 quoting the content: safe log (status, code, attribute), details for the client only")
    void validationErrorQuotingTheContent(CapturedOutput output) {
        setupResponses();
        server.enqueue(json(422, "{\"_type\":\"Error\",\"errorIdentifier\":\"" + VIOLATION + "\",\"message\":"
                + "\"Subject 'Confidential epic " + marker + "' is invalid\","
                + "\"_embedded\":{\"details\":{\"attribute\":\"subject\"}}}"));

        FailedIssue failed = failedCreation(output);

        assertNoLeak(output, null);
        assertTrue(output.getAll().contains(
                "OpenProject rejected the epic: HTTP 422 PropertyConstraintViolation on subject"), output.getAll());
        // Raison renvoyee au client inchangee : il a envoye ce contenu et en a besoin pour corriger
        assertEquals("OpenProject rejected the epic (HTTP 422 PropertyConstraintViolation on subject: Subject "
                + "'Confidential epic " + marker + "' is invalid)", failed.reason());
    }

    @Test
    @DisplayName("Marker outside the error message, HTTP 500 HTML: neither logged nor returned")
    void responseBodies(CapturedOutput output) {
        setupResponses();
        server.enqueue(json(422, "{\"_type\":\"Error\",\"errorIdentifier\":\"" + VIOLATION + "\",\"message\":"
                + "\"invalid\",\"debug\":\"" + marker + "\"}"));
        server.enqueue(new MockResponse().setResponseCode(500).addHeader("Content-Type", "text/html")
                .setBody("<html>" + marker + "</html>"));

        BacklogCreationResult result = new CreateBacklogService(tracker()).create(List.of(epic(), epic()), null);

        assertEquals(List.of("OpenProject rejected the epic (HTTP 422 PropertyConstraintViolation: invalid)",
                        "OpenProject rejected the epic (HTTP 500)"),
                result.failed().stream().map(FailedIssue::reason).toList());
        assertNoLeak(output, null);
    }

    @Test
    @DisplayName("Unreadable 2xx response: dedicated reason (it may have been created), body not logged")
    void unreadableResponse(CapturedOutput output) {
        setupResponses();
        server.enqueue(json(201, marker + " is not JSON"));

        FailedIssue failed = failedCreation(output);

        assertEquals("OpenProject returned an unreadable response for the epic (it may have been created)",
                failed.reason());
        assertFalse(output.getAll().contains("Unexpected error"), "no longer an unexpected error with a stack trace");
        assertNoLeak(output, null);
    }

    @Test
    @DisplayName("Timeout and unreachable OpenProject: no cause, no URL")
    void timeoutAndUnreachable(CapturedOutput output) throws IOException {
        setupResponses();
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        assertEquals("OpenProject did not answer within 2s while creating the epic (it may have been created)",
                failedCreation(output).reason());

        server.shutdown();
        assertEquals("OpenProject unreachable", failedCreation(output).reason());
        assertNoLeak(output, null);
    }

    @Test
    @DisplayName("Reassessment: reading or commenting the work package fails without body nor cause")
    void reassessment(CapturedOutput output) {
        server.enqueue(json(404, "{\"_type\":\"Error\",\"message\":\"No work package " + marker + "\"}"));
        IssueTrackerException read = assertThrows(IssueTrackerException.class, () -> tracker().findStory("39"));
        assertEquals("OpenProject could not read work package 39 (HTTP 404)", read.getMessage());
        assertNoLeak(output, read);

        server.enqueue(json(500, "{\"_type\":\"Error\",\"message\":\"Boom " + marker + "\"}"));
        TrackedStory story = new TrackedStory("39", "Story " + marker, "Description " + marker, List.of(), null);
        IssueTrackerException comment = assertThrows(IssueTrackerException.class,
                () -> tracker().applyReassessment(story, "Precision " + marker, new Readiness(true, "Reason " + marker)));
        assertEquals("OpenProject could not comment work package 39 (HTTP 500)", comment.getMessage());
        assertNoLeak(output, comment);

        server.enqueue(new MockResponse().setResponseCode(502).addHeader("Content-Type", "text/html")
                .setBody("<html>" + marker + "</html>"));
        IssueTrackerException gateway = assertThrows(IssueTrackerException.class,
                () -> tracker().applyReassessment(story, "Precision " + marker, new Readiness(true, "Reason " + marker)));
        assertEquals("OpenProject could not comment work package 39 (HTTP 502)", gateway.getMessage());
        assertNoLeak(output, gateway);
    }
}
