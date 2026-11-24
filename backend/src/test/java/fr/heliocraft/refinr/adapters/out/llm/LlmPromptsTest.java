// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmPromptsTest {

    private static final Path PROMPTS = Path.of("src/main/resources/llm/prompts");
    private static final Pattern MARKER = Pattern.compile("\\{\\{[A-Z_]+\\}\\}");

    private static Story story() {
        Story s = new Story();
        s.setTitle("Titre A");
        s.setDescription("Description A");
        s.setAcceptanceCriteria(List.of("Given A, When B, Then C"));
        return s;
    }

    private static List<String> prompts(Language language) {
        return List.of(LlmPrompts.analysis("DOC", language), LlmPrompts.verdicts(List.of(story()), List.of("s1"), language),
                LlmPrompts.reassessment("Titre A", "Description A", "Precision A", language),
                LlmPrompts.stories("DOC", language));
    }

    private static String file(String name) throws Exception {
        return Files.readString(PROMPTS.resolve(name), StandardCharsets.UTF_8);
    }

    private static List<String> blocks(String markdown) {
        List<String> blocks = new ArrayList<>();
        Matcher m = Pattern.compile("```\\n(.*?)\\n```", Pattern.DOTALL).matcher(markdown);
        while (m.find()) blocks.add(m.group(1));
        return blocks;
    }

    private static List<String> markers(String block) {
        List<String> found = new ArrayList<>();
        Matcher m = MARKER.matcher(block);
        while (m.find()) found.add(m.group());
        return found;
    }

    private static long rules(String block) {
        // Une ligne conditionnelle (prompt 5 : {{IF_PRECISION}}- …) est une regle
        return block.lines().map(l -> l.replace(LlmPrompts.IF_PRECISION, "")).filter(l -> l.startsWith("- ")).count();
    }

    private static List<String> jsonLines(String block) {
        // JSON : lignes qui commencent par une accolade, pas par un marqueur ({{STORIES_JSON}}, {{IF_PRECISION}}…)
        return block.lines().filter(l -> l.startsWith("{") && !l.startsWith("{{")).toList();
    }

    // ---------- parite des deux fichiers ----------

    @Test
    @DisplayName("Parity: prompts.fr.md and prompts.en.md have the same sections, markers, rules and JSON")
    void frenchAndEnglishFilesAreParallel() throws Exception {
        String fr = file("prompts.fr.md"), en = file("prompts.en.md");
        assertEquals(fr.lines().filter(l -> l.startsWith("## ")).count(), en.lines().filter(l -> l.startsWith("## ")).count(),
                "same number of sections");
        List<String> frBlocks = blocks(fr), enBlocks = blocks(en);
        assertEquals(10, frBlocks.size());
        assertEquals(10, enBlocks.size());
        long frRules = 0, enRules = 0;
        for (int i = 0; i < 10; i++) {
            assertEquals(markers(frBlocks.get(i)), markers(enBlocks.get(i)), "same markers, same order, block " + i);
            assertEquals(rules(frBlocks.get(i)), rules(enBlocks.get(i)), "same number of rules, block " + i);
            assertEquals(jsonLines(frBlocks.get(i)), jsonLines(enBlocks.get(i)), "identical JSON, block " + i);
            frRules += rules(frBlocks.get(i));
            enRules += rules(enBlocks.get(i));
        }
        // 59 regles : 33 (generation-v2 et READINESS : 29 + 4 regles de taille du niveau Feature) + 20 (generation-v3 :
        // le prompt 1 sans ses 4 regles de sous-taches) + 6 (tasks-v1) + 7 (test-cases-v1, dont la regle conditionnelle
        // de la precision du PO)
        assertEquals(66, frRules);
        assertEquals(66, enRules);
    }

    @Test
    @DisplayName("Pipeline prompt 1 (generation-v3): prompt 1 without sub-tasks; prompt 1 (generation-v2) keeps them")
    void pipelineGenerationHasNoTasks() {
        for (Language language : List.of(Language.FR, Language.EN)) {
            String stories = LlmPrompts.stories("SPEC", language);
            String analysis = LlmPrompts.analysis("SPEC", language);
            assertFalse(stories.contains("\"tasks\""), "no tasks in the JSON example");
            assertTrue(analysis.contains("\"tasks\""), "generation-v2 unchanged");
            assertTrue(stories.contains("SPEC") && stories.contains("aiReadyReason"), "READINESS and document kept");
            assertFalse(stories.contains("estimatedHours"));
        }
    }

    @Test
    @DisplayName("Prompt 3 (verdict-v3.3): previous blocking question as its own line when given; without it, verdict-v3.2 text")
    void reassessmentWithPreviousQuestion() {
        for (Language language : List.of(Language.FR, Language.EN)) {
            String with = LlmPrompts.reassessment("T", "D", "P", "Quel canal ?", language);
            String without = LlmPrompts.reassessment("T", "D", "P", null, language);
            String label = language.isFrench() ? "Question bloquante précédente : Quel canal ?" : "Previous blocking question: Quel canal ?";
            assertTrue(with.contains("\n" + label + "\n"), with);
            assertFalse(with.contains(LlmPrompts.IF_QUESTION) || without.contains(LlmPrompts.IF_QUESTION));
            assertFalse(without.contains(language.isFrench() ? "Question bloquante" : "Previous blocking question"));
            assertEquals(without, LlmPrompts.reassessment("T", "D", "P", language), "4-argument form unchanged");
            assertEquals(without, LlmPrompts.reassessment("T", "D", "P", " ", language), "blank question: no line");
        }
        assertEquals("verdict-v3.4", LlmPrompts.REASSESSMENT_PROMPT_VERSION);
        assertEquals("verdict-v3.2", LlmPrompts.VERDICT_PROMPT_VERSION, "prompt 2 (E0, verdict cache) unchanged");
    }

    @Test
    @DisplayName("Prompt 3 (verdict-v3.4, R3): two steps; a new point blocks only if READINESS says so or the clarification introduces it")
    void reassessmentInTwoSteps() {
        String fr = LlmPrompts.reassessment("T", "D", "P", "Q ?", Language.FR);
        assertTrue(fr.contains("ÉTAPE 1. Décide si la précision règle la question bloquante précédente"), fr);
        assertTrue(fr.contains("et un point qui n'est pas nécessaire pour implémenter la story et faire passer ses critères "
                + "d'acceptation (par exemple un point qui relève d'une autre story)."), fr);
        String en = LlmPrompts.reassessment("T", "D", "P", "Q ?", Language.EN);
        assertTrue(en.contains("STEP 1. Decide whether the clarification settles the previous blocking question"), en);
        assertTrue(en.contains("and a point that is not needed to implement the story and pass its acceptance criteria "
                + "(for example a point that belongs to another story)."), en);
        assertTrue(fr.contains("Si la précision règle la question et qu'aucun point de l'étape 2 ne reste ouvert : aiReady = true."));
        assertTrue(en.contains("If the clarification settles the question and no step-2 point remains open: aiReady = true."));
    }

    @Test
    @DisplayName("Prompt 5 with a PO clarification (createTasks): the precision rule is kept, the clarification is story data")
    void testCasesWithClarification() {
        for (Language language : List.of(Language.FR, Language.EN)) {
            String with = LlmPrompts.testCases(List.of(story()), java.util.Arrays.asList("e-mail uniquement"), List.of("s1"), language);
            String without = LlmPrompts.testCases(List.of(story()), List.of("s1"), language);
            String key = language.isFrench() ? "precisionDuPO" : "poClarification";
            assertTrue(with.contains("\"" + key + "\" : \"e-mail uniquement\""), with);
            assertTrue(with.contains("- " + (language.isFrench() ? "La précision du PO" : "The PO's clarification")), with);
            assertFalse(with.contains(LlmPrompts.IF_PRECISION));
            assertFalse(without.contains(key) || without.contains(LlmPrompts.IF_PRECISION), without);
            assertFalse(without.contains(language.isFrench() ? "La précision du PO" : "The PO's clarification"));
        }
    }

    @Test
    @DisplayName("Parity of prompt 5: story JSON differs only by key names; criteria numbered AC1, AC2…")
    void testCasesJsonDiffersOnlyByKeyNames() {
        String fr = storiesJson(LlmPrompts.testCases(List.of(story()), List.of("s1"), Language.FR), "STORIES (JSON) :\n");
        String en = storiesJson(LlmPrompts.testCases(List.of(story()), List.of("s1"), Language.EN), "STORIES (JSON):\n");
        assertEquals(en, fr.replace("\"titre\"", "\"title\"").replace("\"criteresAcceptation\"", "\"acceptanceCriteria\""));
        assertTrue(en.contains("\"AC1: "), en);
    }

    @Test
    @DisplayName("Parity of prompt 4: story JSON differs only by key names; the PO clarification key only when given")
    void tasksJsonDiffersOnlyByKeyNames() {
        String fr = storiesJson(LlmPrompts.tasks(List.of(story()), java.util.Arrays.asList("10 jours"), List.of("s1"),
                Language.FR), "STORIES (JSON) :\n");
        String en = storiesJson(LlmPrompts.tasks(List.of(story()), java.util.Arrays.asList("10 jours"), List.of("s1"),
                Language.EN), "STORIES (JSON):\n");
        assertEquals(en, fr.replace("\"titre\"", "\"title\"").replace("\"criteresAcceptation\"", "\"acceptanceCriteria\"")
                .replace("\"precisionDuPO\"", "\"poClarification\""));
        assertFalse(LlmPrompts.tasks(List.of(story()), java.util.Collections.singletonList(null), List.of("s1"), Language.EN)
                .contains("poClarification"));
    }

    @Test
    @DisplayName("Parity of the batch verdict: the story JSON differs only by its key names (titre / title, criteresAcceptation / acceptanceCriteria)")
    void storyJsonDiffersOnlyByKeyNames() {
        String fr = storiesJson(LlmPrompts.verdicts(List.of(story()), List.of("s1"), Language.FR), "STORIES (JSON) :\n");
        String en = storiesJson(LlmPrompts.verdicts(List.of(story()), List.of("s1"), Language.EN), "STORIES (JSON):\n");
        assertTrue(fr.contains("\"titre\"") && fr.contains("\"criteresAcceptation\""), fr);
        assertTrue(en.contains("\"title\"") && en.contains("\"acceptanceCriteria\""), en);
        assertEquals(en, fr.replace("\"titre\"", "\"title\"").replace("\"criteresAcceptation\"", "\"acceptanceCriteria\""));
    }

    private static String storiesJson(String prompt, String header) {
        int from = prompt.indexOf(header) + header.length();
        return prompt.substring(from, prompt.indexOf("\n\n", from));
    }

    // ---------- concordance avec LlmPrompts, pour les deux langues ----------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"fr", "en"})
    @DisplayName("Prompt text matches its resource verbatim, markers replaced")
    void textMatchesItsResource(String code) throws Exception {
        Language language = Language.of(code);
        List<String> b = blocks(file("prompts." + code + ".md"));
        String name = code.equals("fr") ? "français" : "English";
        String readiness = LlmPrompts.readiness(language);
        assertEquals(b.get(0), LlmPrompts.system(language));
        assertEquals(b.get(1), readiness);
        assertEquals(b.get(2).replace("{{READINESS}}", readiness).replace("{{DOCUMENT}}", "DOC").replace("{{LANGUAGE}}", name),
                LlmPrompts.analysis("DOC", language));
        String verdicts = LlmPrompts.verdicts(List.of(story()), List.of("s1"), language);
        String header = code.equals("fr") ? "STORIES (JSON) :\n" : "STORIES (JSON):\n";
        assertEquals(b.get(3).replace("{{READINESS}}", readiness).replace("{{STORIES_JSON}}", storiesJson(verdicts, header))
                .replace("{{LANGUAGE}}", name), verdicts);
        assertEquals(b.get(4).replaceAll("(?m)^" + java.util.regex.Pattern.quote(LlmPrompts.IF_QUESTION) + ".*\\R", "")
                .replace("{{READINESS}}", readiness).replace("{{TITLE}}", "T").replace("{{DESCRIPTION}}", "D")
                .replace("{{PRECISION}}", "P").replace("{{LANGUAGE}}", name), LlmPrompts.reassessment("T", "D", "P", language));
        assertEquals(b.get(5).replace("{{READINESS}}", readiness).replace("{{DOCUMENT}}", "DOC").replace("{{LANGUAGE}}", name),
                LlmPrompts.stories("DOC", language));
        String tasks = LlmPrompts.tasks(List.of(story()), java.util.Collections.singletonList(null), List.of("s1"), language);
        assertEquals(b.get(6).replace("{{STORIES_JSON}}", storiesJson(tasks, header)).replace("{{LANGUAGE}}", name), tasks);
        String testCases = LlmPrompts.testCases(List.of(story()), List.of("s1"), language);
        // Sans precision (la chaine) : la ligne conditionnelle est retiree, texte mesure en C2
        String withoutPrecision = b.get(7).replaceAll("(?m)^" + java.util.regex.Pattern.quote(LlmPrompts.IF_PRECISION) + ".*\\R", "");
        assertEquals(withoutPrecision.replace("{{STORIES_JSON}}", storiesJson(testCases, header)).replace("{{LANGUAGE}}", name),
                testCases);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"fr", "en"})
    @DisplayName("Generation (v2 and pipeline v3), batch verdict and reassessment share the same READINESS block, once each; "
            + "prompt 4 (sub-tasks of ready stories) has none; every marker is replaced")
    void promptsShareTheReadinessBlock(String code) {
        Language language = Language.of(code);
        String readiness = LlmPrompts.readiness(language);
        for (String prompt : prompts(language)) {
            assertTrue(prompt.contains(readiness), prompt);
            assertEquals(prompt.indexOf(readiness), prompt.lastIndexOf(readiness), "block present once");
            assertFalse(prompt.contains("{{"), prompt);
        }
        // Prompt 4 ne juge pas : il ecrit les sous-taches de stories deja pretes (pas de verdict a produire)
        String tasks = LlmPrompts.tasks(List.of(story()), java.util.Collections.singletonList(null), List.of("s1"), language);
        assertFalse(tasks.contains(readiness), "no READINESS block in prompt 4");
        assertFalse(tasks.contains("{{"), tasks);
        // Prompt 5 non plus : il ecrit les cas de test de stories deja pretes
        String testCases = LlmPrompts.testCases(List.of(story()), List.of("s1"), language);
        assertFalse(testCases.contains(readiness), "no READINESS block in prompt 5");
        assertFalse(testCases.contains("{{"), testCases);
    }

    // ---------- langue de sortie ----------

    @Test
    @DisplayName("French: French prompt, « français »; English: English prompt, English")
    void frenchAndEnglishUseTheirNativePrompt() {
        assertTrue(LlmPrompts.analysis("DOC", Language.FR)
                .contains("- Rédige tout le contenu en français : titres, descriptions, critères d'acceptation, sous-tâches et raisons."));
        assertTrue(LlmPrompts.reassessment("T", "D", "P", Language.FR).contains("Rédige aiReadyReason en français."));
        assertTrue(LlmPrompts.analysis("DOC", Language.EN)
                .contains("- Write all content in English: titles, descriptions, acceptance criteria, sub-tasks and reasons."));
        assertTrue(LlmPrompts.verdicts(List.of(story()), List.of("s1"), Language.EN).contains("Write aiReadyReason in English."));
        assertEquals(LlmPrompts.system(Language.FR), LlmPrompts.FR.system());
        assertEquals(LlmPrompts.system(Language.EN), LlmPrompts.EN.system());
    }

    @Test
    @DisplayName("Any other language: English prompt with the English name of the language, never the received code")
    void otherLanguageUsesTheEnglishPromptWithItsName() {
        Language german = Language.of("DE");
        String analysis = LlmPrompts.analysis("DOC", german);
        assertTrue(analysis.startsWith("MISSION: turn the specification"), "English prompt");
        assertTrue(analysis.contains("- Write all content in German: titles, descriptions, acceptance criteria, sub-tasks and reasons."));
        assertFalse(analysis.contains("in de:") || analysis.contains("in DE"), "the code is never copied into the prompt");
        assertTrue(LlmPrompts.reassessment("T", "D", "P", Language.of("es")).contains("Write aiReadyReason in Spanish."));
        assertEquals(LlmPrompts.system(Language.EN), LlmPrompts.system(german));
    }

    @Test
    @DisplayName("A marker inside the document stays text; a missing marker value is refused")
    void markersAreReplacedInTheTemplateOnly() {
        String prompt = LlmPrompts.analysis("Spec {{READINESS}} {{LANGUAGE}}", Language.FR);
        assertTrue(prompt.contains("Spec {{READINESS}} {{LANGUAGE}}"), "document content is never re-read");
        assertThrows(IllegalStateException.class, () -> LlmPrompts.fill("{{X}}", java.util.Map.of()));
        assertThrows(IllegalStateException.class, () -> LlmPrompts.parse("```\nseul\n```", "test.md"));
    }

    @Test
    @DisplayName("Verdict prompt version is verdict-v3.2")
    void version() {
        assertEquals("verdict-v3.2", LlmPrompts.VERDICT_PROMPT_VERSION);
        String reassessment = LlmPrompts.reassessment("Titre A", "Description A", "Precision A", Language.FR);
        assertTrue(reassessment.contains("Titre : Titre A\nDescription : Description A\n\nPRÉCISION DE L'UTILISATEUR :\nPrecision A\n"));
    }
}
