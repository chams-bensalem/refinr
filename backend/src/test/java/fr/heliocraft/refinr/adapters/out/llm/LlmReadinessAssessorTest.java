package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static fr.heliocraft.refinr.adapters.out.llm.LlmBacklogGeneratorTest.answer;
import static org.junit.jupiter.api.Assertions.*;

class LlmReadinessAssessorTest {

    private MockWebServer server;
    private LlmReadinessAssessor assessor;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        assessor = new LlmReadinessAssessor(
                LlmClients.create(LlmTestSettings.of(LlmProvider.OPENAI_COMPATIBLE, server.url("/v1").toString())),
                new StructuredOutput(2));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private static Story story(String title) {
        Story story = new Story();
        story.setTitle(title);
        story.setDescription("Exporter la liste en A4");
        story.setAcceptanceCriteria(List.of("Given une liste, When j'exporte, Then un PDF A4"));
        return story;
    }

    @Test
    @DisplayName("assess judges each story on its content (title, description, criteria)")
    void assessJudgesEachStory() throws Exception {
        server.enqueue(answer("{\"aiReady\": true, \"aiReadyReason\": \"Complet\"}"));

        List<Readiness> verdicts = assessor.assess(List.of(story("Export PDF")));

        assertEquals(List.of(new Readiness(true, "Complet")), verdicts);
        String body = server.takeRequest().getBody().readUtf8();
        assertTrue(body.contains("Export PDF"), body);
        assertTrue(body.contains("Exporter la liste en A4"), body);
        assertTrue(body.contains("un PDF A4"), body);
    }

    @Test
    @DisplayName("A story whose verdict call fails gets no verdict (null), the others are still judged")
    void failedVerdictIsNull() {
        server.enqueue(new MockResponse().setResponseCode(401));
        server.enqueue(answer("{\"aiReady\": false, \"aiReadyReason\": \"flou\"}"));

        List<Readiness> verdicts = assessor.assess(List.of(story("A"), story("B")));

        assertEquals(Arrays.asList(null, new Readiness(false, "flou")), verdicts);
    }

    @Test
    @DisplayName("reassess sends the user's precision and parses the verdict")
    void reassessWithPrecision() throws Exception {
        server.enqueue(answer("{\"aiReady\": true, \"aiReadyReason\": \"format tranche\"}"));

        Readiness verdict = assessor.reassess("Export PDF", "Exporter en PDF", "format A4 uniquement");

        assertEquals(new Readiness(true, "format tranche"), verdict);
        assertTrue(server.takeRequest().getBody().readUtf8().contains("format A4 uniquement"));
    }

    @Test
    @DisplayName("A non-boolean aiReady is never treated as ready")
    void ambiguousVerdictIsNotReady() {
        assertFalse(LlmReadinessAssessor.readiness(
                tools.jackson.databind.json.JsonMapper.builder().build().readTree("{\"aiReady\":\"yes\",\"aiReadyReason\":\"x\"}")).aiReady());
    }
}
