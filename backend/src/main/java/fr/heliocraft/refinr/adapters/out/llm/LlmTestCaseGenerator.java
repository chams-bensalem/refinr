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
    // Reparations de couverture declenchees depuis la creation (mesure C2) ; jamais de contenu
    private final java.util.concurrent.atomic.AtomicInteger repairs = new java.util.concurrent.atomic.AtomicInteger();
    // Une seule tentative pour la reparation de couverture (deja une seconde chance)
    private final StructuredOutput once = new StructuredOutput(0);

    public LlmTestCaseGenerator(LlmClient client, StructuredOutput output, int batchSize) {
        super(client, output, batchSize, LlmSchemas.STORY_TEST_CASES);
    }

    /** Nombre de reparations de couverture declenchees (une par story non conforme). */
    public int repairsTriggered() {
        return repairs.get();
    }

    /**
     * Cas de test par lots ; puis, pour chaque story dont la couverture n'est pas conforme (critere sans cas, plus de 3
     * cas, critere inexistant), <b>une seule</b> reparation, pour cette story seulement : sa reponse et la liste des
     * problemes (par exemple « AC2 has no test case ») sont renvoyees au modele. Reparation sans succes : la reponse
     * d'origine est rendue, l'application refuse alors d'ecrire une section partielle.
     */
    @Override
    public synchronized List<List<TestCase>> generateTestCases(List<Story> stories, Language language) {
        this.stories = stories;
        try {
            List<List<TestCase>> results = new java.util.ArrayList<>(run(stories, language));
            for (int i = 0; i < stories.size(); i++) {
                List<TestCase> found = results.get(i);
                int criteria = stories.get(i).getAcceptanceCriteria() != null ? stories.get(i).getAcceptanceCriteria().size() : 0;
                if (found == null) {
                    continue;
                }
                List<String> problems = fr.heliocraft.refinr.domain.service.TestCasePolicy.coverageProblems(found, criteria);
                if (!problems.isEmpty()) {
                    results.set(i, repair(stories.get(i), found, problems, language, i + 1, stories.size()));
                }
            }
            return results;
        } finally {
            this.stories = null;
        }
    }

    private List<TestCase> repair(Story story, List<TestCase> previous, List<String> problems, Language language, int position,
                                  int size) {
        repairs.incrementAndGet();
        // Position et nombre de problemes seulement : jamais le titre ni les cas
        log.warn("Test cases of ready story {}/{}: {} coverage problem(s), one repair for this story only", position, size,
                problems.size());
        LlmRequest request = LlmRequest.of(LlmPrompts.system(language),
                LlmPrompts.testCases(List.of(story), List.of("s1"), language), schemaName(), LlmSchemas.forProvider(schema()))
                .followUp(answer(previous), StructuredOutput.repairMessage(problems));
        try {
            int criteria = story.getAcceptanceCriteria() != null ? story.getAcceptanceCriteria().size() : 0;
            List<TestCase> repaired = once.call(client(), request, schema(), this::parse).get("s1");
            if (repaired != null && fr.heliocraft.refinr.domain.service.TestCasePolicy.coverageProblems(repaired, criteria).isEmpty()) {
                return repaired;
            }
            log.warn("Test cases of ready story {}/{} still not compliant after the repair", position, size);
        } catch (fr.heliocraft.refinr.application.port.out.LlmException e) {
            log.warn("Repair of the test cases of ready story {}/{} failed: {}", position, size, e.getMessage());
        }
        return previous;
    }

    /** Reponse precedente du modele pour cette story, reconstruite au format du schema. */
    private static String answer(List<TestCase> testCases) {
        List<java.util.Map<String, Object>> cases = new java.util.ArrayList<>();
        for (TestCase t : testCases) {
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("title", t.title());
            item.put("criterion", t.criterion());
            item.put("kind", t.kind());
            item.put("given", t.given());
            item.put("when", t.when());
            item.put("then", t.then());
            cases.add(item);
        }
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .writeValueAsString(java.util.Map.of("stories", List.of(java.util.Map.of("id", "s1", "testCases", cases))));
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
