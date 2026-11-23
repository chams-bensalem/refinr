// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.TestCase;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static fr.heliocraft.refinr.adapters.out.llm.LlmBacklogGeneratorTest.answer;
import static org.junit.jupiter.api.Assertions.*;

/** Prompt 5 (chaine C2) : cas de test des stories pretes, par lots, appaires par identifiant. */
class LlmTestCaseGeneratorTest {

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

    private LlmTestCaseGenerator generator(int batchSize) {
        return new LlmTestCaseGenerator(
                LlmClients.create(LlmTestSettings.of(LlmProvider.OPENAI_COMPATIBLE, server.url("/v1").toString())),
                new StructuredOutput(2), batchSize);
    }

    private static Story story(String title) {
        Story story = new Story();
        story.setTitle(title);
        story.setDescription("Description de " + title);
        story.setAcceptanceCriteria(List.of("Given " + title + ", When it runs, Then ok"));
        return story;
    }

    private static String entry(String id, String title) {
        return "{\"id\":\"" + id + "\",\"testCases\":[{\"title\":\"" + title + "\",\"criterion\":\"AC1\","
                + "\"kind\":\"nominal\",\"given\":\"g\",\"when\":\"w\",\"then\":\"t\"}]}";
    }

    @Test
    @DisplayName("Stories by batches, criteria numbered AC1… in the prompt, test cases matched by id, test cases schema sent")
    void byBatches() throws Exception {
        server.enqueue(answer("{\"stories\":[" + entry("s2", "B1") + "," + entry("s1", "A1") + "]}"));
        server.enqueue(answer("{\"stories\":[" + entry("s1", "C1") + "]}"));

        List<List<TestCase>> result = generator(2).generateTestCases(List.of(story("A"), story("B"), story("C")), Language.EN);

        assertEquals(new TestCase("A1", "AC1", "nominal", "g", "w", "t"), result.get(0).getFirst());
        assertEquals("B1", result.get(1).getFirst().title());
        assertEquals("C1", result.get(2).getFirst().title());
        String first = server.takeRequest().getBody().readUtf8();
        assertTrue(first.contains("story-test-cases"), "the test cases schema is sent: " + first);
        assertTrue(first.contains("AC1: Given A, When it runs, Then ok"), "criteria numbered: " + first);
        assertFalse(first.contains("Description de C"));
    }

    private static Story twoCriteria(String title) {
        Story story = story(title);
        story.setAcceptanceCriteria(List.of("Given a, Then b", "Given c, Then d"));
        return story;
    }

    @Test
    @DisplayName("Coverage: a story with an uncovered criterion gets ONE repair, for itself only, naming the criterion")
    void oneRepairForTheStoryOnly() throws Exception {
        LlmTestCaseGenerator generator = generator(10);
        server.enqueue(answer("{\"stories\":[" + entry("s1", "A1") + "," + entry("s2", "B1") + "]}"));
        server.enqueue(answer("{\"stories\":[{\"id\":\"s1\",\"testCases\":["
                + "{\"title\":\"B1\",\"criterion\":\"AC1\",\"kind\":\"nominal\",\"given\":\"g\",\"when\":\"w\",\"then\":\"t\"},"
                + "{\"title\":\"B2\",\"criterion\":\"AC2\",\"kind\":\"edge\",\"given\":\"g\",\"when\":\"w\",\"then\":\"t\"}]}]}"));

        List<List<TestCase>> result = generator.generateTestCases(List.of(story("A"), twoCriteria("B")), Language.EN);

        assertEquals(List.of("A1"), result.get(0).stream().map(TestCase::title).toList(), "A was compliant: kept");
        assertEquals(List.of("B1", "B2"), result.get(1).stream().map(TestCase::title).toList(), "B repaired");
        assertEquals(1, generator.repairsTriggered());
        assertEquals(2, server.getRequestCount());
        server.takeRequest();
        String repair = server.takeRequest().getBody().readUtf8();
        assertTrue(repair.contains("AC2 has no test case"), repair);
        assertTrue(repair.contains("Description de B") && !repair.contains("Description de A"), "only story B is sent again");
    }

    @Test
    @DisplayName("Coverage: repair still not compliant (or failing): the original answer is returned, no second repair")
    void failedRepairReturnsTheOriginal() {
        LlmTestCaseGenerator generator = generator(10);
        server.enqueue(answer("{\"stories\":[" + entry("s1", "B1") + "]}"));
        server.enqueue(answer("{\"stories\":[" + entry("s1", "B1 again") + "]}"));

        List<List<TestCase>> result = generator.generateTestCases(List.of(twoCriteria("B")), Language.EN);

        assertEquals(List.of("B1"), result.getFirst().stream().map(TestCase::title).toList());
        assertEquals(2, server.getRequestCount(), "one repair only");
        assertEquals(1, generator.repairsTriggered());
    }

    @Test
    @DisplayName("Invalid kind or too many test cases: schema violation, repaired like any other output")
    void invalidOutputIsRepaired() {
        server.enqueue(answer("{\"stories\":[{\"id\":\"s1\",\"testCases\":[{\"title\":\"x\",\"criterion\":\"AC1\","
                + "\"kind\":\"smoke\",\"given\":\"g\",\"when\":\"w\",\"then\":\"t\"}]}]}"));
        server.enqueue(answer("{\"stories\":[" + entry("s1", "A1") + "]}"));

        List<List<TestCase>> result = generator(10).generateTestCases(List.of(story("A")), Language.FR);

        assertEquals("A1", result.getFirst().getFirst().title());
        assertEquals(2, server.getRequestCount(), "one repair");
    }
}
