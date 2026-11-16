// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmPromptsTest {

    private static Story story() {
        Story s = new Story();
        s.setTitle("Titre A");
        s.setDescription("Description A");
        s.setAcceptanceCriteria(List.of("Given A, When B, Then C"));
        return s;
    }

    private static List<String> prompts() {
        return List.of(LlmPrompts.analysis("DOC"), LlmPrompts.verdicts(List.of(story()), List.of("s1")),
                LlmPrompts.reassessment("Titre A", "Description A", "Precision A"));
    }

    @Test
    @DisplayName("Generation, batch verdict and reassessment share the same READINESS block, once each")
    void threePromptsShareTheReadinessBlock() {
        assertTrue(LlmPrompts.READINESS.startsWith("CONTRÔLE « PRÊTE POUR UN AGENT DE CODE »"));
        for (String prompt : prompts()) {
            assertTrue(prompt.contains(LlmPrompts.READINESS), prompt);
            assertEquals(prompt.indexOf(LlmPrompts.READINESS), prompt.lastIndexOf(LlmPrompts.READINESS), "block present once");
            assertEquals(1, prompt.split("CONTRÔLE « PRÊTE POUR UN AGENT DE CODE »", -1).length - 1, "no second copy of the rules");
        }
    }

    @Test
    @DisplayName("Every marker is replaced and the verdict prompt version is verdict-v3.1")
    void markersReplaced() {
        for (String prompt : prompts()) assertFalse(prompt.contains("{{"), prompt);
        assertEquals("verdict-v3.1", LlmPrompts.VERDICT_PROMPT_VERSION);
        String reassessment = LlmPrompts.reassessment("Titre A", "Description A", "Precision A");
        assertTrue(reassessment.contains("Titre : Titre A\nDescription : Description A\n\nPRÉCISION DE L'UTILISATEUR :\nPrecision A\n"));
    }

    /** Le texte des prompts est celui de eval/generation/prompts-v3.1.md, marqueurs remplaces. */
    @Test
    @DisplayName("Prompt text matches eval/generation/prompts-v3.1.md verbatim")
    void textMatchesTheV3Document() throws Exception {
        String md = Files.readString(Path.of("..", "eval", "generation", "prompts-v3.1.md"), StandardCharsets.UTF_8);
        List<String> blocks = new ArrayList<>();
        Matcher m = Pattern.compile("```\\n(.*?)\\n```", Pattern.DOTALL).matcher(md);
        while (m.find()) blocks.add(m.group(1));
        assertEquals(5, blocks.size());
        assertEquals(blocks.get(0), LlmPrompts.SYSTEM);
        assertEquals(blocks.get(1), LlmPrompts.READINESS);
        assertEquals(blocks.get(2).replace("{{READINESS}}", LlmPrompts.READINESS).replace("{{DOCUMENT}}", "DOC"),
                LlmPrompts.analysis("DOC"));
        String verdicts = LlmPrompts.verdicts(List.of(story()), List.of("s1"));
        String json = verdicts.substring(verdicts.indexOf("STORIES (JSON) :\n") + "STORIES (JSON) :\n".length(),
                verdicts.indexOf("\n\nRédige aiReadyReason"));
        assertEquals(blocks.get(3).replace("{{READINESS}}", LlmPrompts.READINESS).replace("{{STORIES_JSON}}", json), verdicts);
        assertTrue(json.contains("\"criteresAcceptation\""), "story JSON encoding unchanged");
        assertEquals(blocks.get(4).replace("{{READINESS}}", LlmPrompts.READINESS).replace("{{TITLE}}", "T")
                .replace("{{DESCRIPTION}}", "D").replace("{{PRECISION}}", "P"), LlmPrompts.reassessment("T", "D", "P"));
    }
}
