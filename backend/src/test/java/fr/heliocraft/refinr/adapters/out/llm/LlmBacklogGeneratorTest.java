package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Story;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.StringNode;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LlmBacklogGeneratorTest {

    private MockWebServer server;
    private LlmBacklogGenerator generator;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        generator = new LlmBacklogGenerator(
                LlmClients.create(LlmTestSettings.of(LlmProvider.OPENAI_COMPATIBLE, server.url("/v1").toString())),
                new StructuredOutput(2));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    static MockResponse answer(String text) {
        return new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":" + StringNode.valueOf(text) + "}}]}");
    }

    static final String BACKLOG = """
            {"epics":[{"name":"Epic: Prix","description":"Filtre","priority":"High","labels":["frontend"],
              "stories":[{"asA":"Client","iWant":"filtrer par prix","soThat":"trouver vite","title":"Filtre par prix",
                "description":"Curseur min/max","acceptanceCriteria":["Given une liste, When je filtre, Then seuls les prix compris restent"],
                "estimatedStoryPoints":3,"priority":"High","labels":["frontend"],"dependencies":[],
                "aiReady":true,"aiReadyReason":"Perimetre clair",
                "tasks":[{"title":"Composant curseur","description":"Slider","estimatedHours":4,"labels":["frontend"]}]}]}]}
            """;

    @Test
    @DisplayName("Parses the backlog (story verdict included) and asks for a proportional breakdown with the backlog schema")
    void parsesBacklogAndSendsPromptAndSchema() throws Exception {
        server.enqueue(answer(BACKLOG));

        List<Epic> epics = generator.generate("Ajouter un filtre par prix");

        Story story = epics.get(0).getStories().get(0);
        assertEquals("Epic: Prix", epics.get(0).getName());
        assertEquals("Filtre par prix", story.getTitle());
        assertEquals(Boolean.TRUE, story.getAiReady());
        assertEquals("Perimetre clair", story.getAiReadyReason());
        assertEquals("Composant curseur", story.getTasks().get(0).getTitle());

        JsonNode body = JsonMapper.builder().build().readTree(server.takeRequest().getBody().readUtf8());
        String prompt = body.path("messages").path(1).path("content").asString();
        assertTrue(prompt.contains("Ajouter un filtre par prix"));
        assertTrue(prompt.contains("ÉCHELLE (PROPORTIONNELLE AU DOCUMENT)"));
        assertTrue(prompt.contains("PRÊTE POUR UN AGENT DE CODE"));
        assertEquals("backlog", body.path("response_format").path("json_schema").path("name").asString());
        assertTrue(body.path("response_format").path("json_schema").path("schema").path("properties").has("epics"));
    }

    @Test
    @DisplayName("Markdown fences and a <think> block around the JSON are ignored")
    void toleratesFencesAndThinking() {
        server.enqueue(answer("<think>je reflechis {pas du json}</think>\n```json\n" + BACKLOG + "\n```"));

        assertEquals(1, generator.generate("Besoin").size());
    }

    @Test
    @DisplayName("An empty backlog is a valid answer")
    void emptyBacklog() {
        server.enqueue(answer("{\"epics\":[]}"));

        assertEquals(List.of(), generator.generate("Besoin"));
    }
}
