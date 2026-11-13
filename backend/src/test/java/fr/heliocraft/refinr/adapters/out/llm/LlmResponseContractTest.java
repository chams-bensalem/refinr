package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import fr.heliocraft.refinr.application.port.out.LlmException;
import tools.jackson.databind.node.StringNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contrat de parsing de la reponse LLM (caracterisation, phase 3) : fige les champs lus dans le JSON
 * produit par le modele et la tolerance actuelle aux proprietes inconnues.
 */
class LlmResponseContractTest {

    private MockWebServer mockWebServer;
    private LlmBacklogGenerator analyzer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        analyzer = new LlmBacklogGenerator(LlmClients.create(LlmTestSettings.of(LlmProvider.GOOGLE,
            mockWebServer.url("/").toString())), new StructuredOutput(2));
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Every Epic/Story/Task field produced by the model is read")
    void readsEveryField() throws IOException {
        mockWebServer.enqueue(geminiText(resource("contract/llm-backlog-response.json")));

        List<Epic> epics = analyzer.generate("cdc");

        assertEquals(1, epics.size());
        Epic epic = epics.get(0);
        assertEquals("Epic: Inventaire", epic.getName());
        assertEquals("Gestion des stocks", epic.getDescription());
        assertEquals("High", epic.getPriority());
        assertEquals(List.of("backend", "api"), epic.getLabels());
        assertNull(epic.getTrackerKey(), "the model never provides a tracker key");

        Story story = epic.getStories().get(0);
        assertEquals("Developer", story.getAsA());
        assertEquals("track stock levels", story.getIWant());
        assertEquals("avoid stock-outs", story.getSoThat());
        assertEquals("Track Stock", story.getTitle());
        assertEquals("Story description", story.getDescription());
        assertEquals(List.of("AC1", "AC2"), story.getAcceptanceCriteria());
        assertEquals(5, story.getEstimatedStoryPoints());
        assertEquals("Medium", story.getPriority());
        assertEquals(List.of("api"), story.getLabels());
        assertEquals(List.of("PROJ-0"), story.getDependencies());
        assertEquals(Boolean.TRUE, story.getAiReady());
        assertEquals("Perimetre clair", story.getAiReadyReason());
        assertNull(story.getTrackerKey());

        Task task = story.getTasks().get(0);
        assertEquals("Create service", task.getTitle());
        assertEquals("Task description", task.getDescription());
        assertEquals(4, task.getEstimatedHours());
        assertEquals(List.of("backend"), task.getLabels());
        assertNull(task.getTrackerKey());
    }

    @Test
    @DisplayName("An unknown property (here a tracker key) is rejected by the schema: 1 call + 2 repairs, then INVALID_OUTPUT")
    void rejectsUnknownProperty() {
        for (int i = 0; i < 3; i++) {
            mockWebServer.enqueue(geminiText("{\"epics\":[{\"name\":\"E\",\"description\":\"d\",\"jiraKey\":\"PROJ-1\",\"stories\":[]}]}"));
        }

        LlmException error = assertThrows(LlmException.class, () -> analyzer.generate("cdc"));

        assertEquals(LlmException.Reason.INVALID_OUTPUT, error.reason());
        assertEquals(3, mockWebServer.getRequestCount());
    }

    private static MockResponse geminiText(String text) {
        return new MockResponse()
            .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + StringNode.valueOf(text) + "}]}}]}")
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200);
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = LlmResponseContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing test resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
