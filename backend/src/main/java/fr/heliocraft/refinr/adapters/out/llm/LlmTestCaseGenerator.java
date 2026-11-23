// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.TestCaseGeneratorPort;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cas de test des stories pretes (chaine C2, prompt 5) par le fournisseur d'analyse, par lots de stories
 * ({@link LlmStoryBatches}). Les cas sont rendus tels que le modele les donne ; le controle des criteres designes est
 * fait par l'application ({@code TestCasePolicy}).
 */
public class LlmTestCaseGenerator extends LlmStoryBatches<List<TestCase>> implements TestCaseGeneratorPort {

    private static final Logger log = LoggerFactory.getLogger(LlmTestCaseGenerator.class);

    private List<Story> stories;

    public LlmTestCaseGenerator(LlmClient client, StructuredOutput output, int batchSize) {
        super(client, output, batchSize, LlmSchemas.STORY_TEST_CASES);
    }

    @Override
    public synchronized List<List<TestCase>> generateTestCases(List<Story> stories, Language language) {
        this.stories = stories;
        try {
            return run(stories, language);
        } finally {
            this.stories = null;
        }
    }

    @Override
    String prompt(List<Integer> indexes, List<String> ids, Language language) {
        return LlmPrompts.testCases(indexes.stream().map(stories::get).toList(), ids, language);
    }

    @Override
    Map<String, List<TestCase>> parse(JsonNode json) {
        Map<String, List<TestCase>> byId = new HashMap<>();
        for (JsonNode story : json.path("stories")) {
            String id = story.path("id").asString("");
            List<TestCase> testCases = new ArrayList<>();
            for (JsonNode t : story.path("testCases")) {
                testCases.add(new TestCase(t.path("title").asString(""), t.path("criterion").asString(""),
                        t.path("kind").asString(""), t.path("given").asString(""), t.path("when").asString(""),
                        t.path("then").asString("")));
            }
            if (byId.put(id, testCases) != null) {
                throw new IllegalArgumentException("duplicate id in stories: " + id + " (one entry per story)");
            }
        }
        return byId;
    }

    @Override
    String what() {
        return "test cases";
    }

    @Override
    Logger log() {
        return log;
    }
}
