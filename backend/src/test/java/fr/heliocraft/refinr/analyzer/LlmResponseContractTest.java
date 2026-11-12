package fr.heliocraft.refinr.analyzer;

import fr.heliocraft.refinr.model.Epic;
import fr.heliocraft.refinr.model.Story;
import fr.heliocraft.refinr.model.Task;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
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
    private GeminiDocumentAnalyzer analyzer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        analyzer = new GeminiDocumentAnalyzer(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(), "fake-api-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Every Epic/Story/Task field produced by the model is read")
    void readsEveryField() throws IOException {
        mockWebServer.enqueue(geminiText(resource("contract/create-jira-request.json")));

        List<Epic> epics = analyzer.analyzeDocument("cdc");

        assertEquals(1, epics.size());
        Epic epic = epics.get(0);
        assertEquals("Epic: Inventaire", epic.getName());
        assertEquals("Gestion des stocks", epic.getDescription());
        assertEquals("High", epic.getPriority());
        assertEquals(List.of("backend", "api"), epic.getLabels());
        assertEquals("PROJ-1", epic.getJiraKey());
        assertEquals("https://your-site.atlassian.net/browse/PROJ-1", epic.getJiraUrl());

        Story story = epic.getStories().get(0);
        assertEquals("Developer", story.getAsA());
        assertEquals("track stock levels", story.getIWant());
        assertEquals("avoid stock-outs", story.getSoThat());
        assertEquals("Track Stock", story.getTitle());
        assertEquals("Story description", story.getDescription());
        assertEquals(List.of("AC1", "AC2"), story.getAcceptanceCriteria());
        assertEquals(3, story.getStoryPoints());
        assertEquals(5, story.getEstimatedStoryPoints());
        assertEquals("Medium", story.getPriority());
        assertEquals(List.of("api"), story.getLabels());
        assertEquals(List.of("PROJ-0"), story.getDependencies());
        assertEquals(Boolean.TRUE, story.getAiReady());
        assertEquals("Perimetre clair", story.getAiReadyReason());
        assertEquals("PROJ-2", story.getJiraKey());
        assertEquals("https://your-site.atlassian.net/browse/PROJ-2", story.getJiraUrl());
        assertEquals("PROJ-1", story.getEpicKey());

        Task task = story.getTasks().get(0);
        assertEquals("Create service", task.getTitle());
        assertEquals("Task description", task.getDescription());
        assertEquals(4, task.getEstimatedHours());
        assertEquals(List.of("backend"), task.getLabels());
        assertEquals("PROJ-3", task.getJiraKey());
        assertEquals("https://your-site.atlassian.net/browse/PROJ-3", task.getJiraUrl());
        assertEquals("PROJ-2", task.getParentStoryKey());
    }

    @Test
    @DisplayName("An unknown property in the model output is rejected (Jackson 2 defaults)")
    void rejectsUnknownProperty() {
        mockWebServer.enqueue(geminiText("[{\"name\":\"E\",\"color\":\"red\",\"stories\":[]}]"));

        RuntimeException error = assertThrows(RuntimeException.class, () -> analyzer.analyzeDocument("cdc"));
        assertTrue(error.getMessage().contains("Unrecognized property"), error.getMessage());
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
