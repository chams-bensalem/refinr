// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static fr.heliocraft.refinr.adapters.out.llm.LlmBacklogGeneratorTest.answer;
import static org.junit.jupiter.api.Assertions.*;

/** Prompt 4 (chaine C1) : sous-taches des stories pretes, par lots, appariees par identifiant. */
class LlmTaskGeneratorTest {

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

    private LlmTaskGenerator generator(int batchSize) {
        return new LlmTaskGenerator(
                LlmClients.create(LlmTestSettings.of(LlmProvider.OPENAI_COMPATIBLE, server.url("/v1").toString())),
                new StructuredOutput(2), batchSize);
    }

    private static Story story(String title) {
        Story story = new Story();
        story.setTitle(title);
        story.setDescription("Description de " + title);
        story.setAcceptanceCriteria(List.of("Given " + title + ", Then ok"));
        return story;
    }

    private static String entry(String id, String... titles) {
        StringBuilder tasks = new StringBuilder();
        for (String title : titles) {
            if (!tasks.isEmpty()) {
                tasks.append(',');
            }
            tasks.append("{\"title\":\"").append(title).append("\",\"description\":\"d\",\"estimatedHours\":2,"
                    + "\"labels\":[\"test\"]}");
        }
        return "{\"id\":\"" + id + "\",\"tasks\":[" + tasks + "]}";
    }

    private static MockResponse tasks(String... entries) {
        return answer("{\"stories\":[" + String.join(",", entries) + "]}");
    }

    private static List<String> titles(List<Task> tasks) {
        return tasks.stream().map(Task::getTitle).toList();
    }

    @Test
    @DisplayName("Stories by batches, matched by id; the PO's clarification goes with its story only; tasks schema sent")
    void byBatchesWithClarification() throws Exception {
        server.enqueue(tasks(entry("s2", "B1"), entry("s1", "A1", "A2")));
        server.enqueue(tasks(entry("s1", "C1")));

        List<List<Task>> result = generator(2).generateTasks(List.of(story("A"), story("B"), story("C")),
                Arrays.asList(null, "Le seuil est 10 jours", null), Language.FR);

        assertEquals(List.of("A1", "A2"), titles(result.get(0)));
        assertEquals(List.of("B1"), titles(result.get(1)));
        assertEquals(List.of("C1"), titles(result.get(2)));
        assertEquals(List.of("test"), result.get(0).getFirst().getLabels());
        String first = server.takeRequest().getBody().readUtf8();
        assertTrue(first.contains("story-tasks"), "the tasks schema is sent: " + first);
        assertTrue(first.contains("precisionDuPO") && first.contains("Le seuil est 10 jours"), first);
        assertTrue(first.contains("Description de A") && !first.contains("Description de C"), first);
        assertFalse(server.takeRequest().getBody().readUtf8().contains("precisionDuPO"), "no clarification for C");
    }

    @Test
    @DisplayName("A story missing from the answer is asked again alone; still missing: null (no tasks), never invented")
    void missingStoryAskedAgainThenNull() {
        server.enqueue(tasks(entry("s1", "A1")));
        server.enqueue(tasks());

        List<List<Task>> result = generator(10).generateTasks(List.of(story("A"), story("B")),
                Collections.nCopies(2, null), Language.EN);

        assertEquals(List.of("A1"), titles(result.get(0)));
        assertNull(result.get(1));
        assertEquals(2, server.getRequestCount());
    }

    @Test
    @DisplayName("Truncated batch: split in two, each half asked again")
    void truncatedBatchIsSplit() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"stories\\\":[\"}}]}"));
        server.enqueue(tasks(entry("s1", "A1")));
        server.enqueue(tasks(entry("s1", "B1")));

        List<List<Task>> result = generator(10).generateTasks(List.of(story("A"), story("B")),
                Collections.nCopies(2, null), Language.EN);

        assertEquals(List.of("A1"), titles(result.get(0)));
        assertEquals(List.of("B1"), titles(result.get(1)));
        assertEquals(3, server.getRequestCount());
    }

    @Test
    @DisplayName("No batch answered (provider error): the provider error is raised, nothing is invented")
    void providerErrorIsRaised() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"down\"}"));

        assertThrows(LlmException.class, () -> generator(10).generateTasks(List.of(story("A")),
                Collections.singletonList(null), Language.EN));
    }
}
