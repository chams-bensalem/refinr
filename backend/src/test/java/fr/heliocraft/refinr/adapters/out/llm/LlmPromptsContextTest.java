// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** X1 : bloc de contexte et regle READINESS seulement avec un contexte ; sans contexte, prompts identiques. */
class LlmPromptsContextTest {

    private static final String CTX = "Glossaire : client fidele = trois commandes. {{DOCUMENT}}";

    private static Story story() {
        Story s = new Story();
        s.setTitle("T");
        s.setDescription("D");
        s.setAcceptanceCriteria(List.of("Given a, When b, Then c"));
        return s;
    }

    @Test
    @DisplayName("Without a context (null or blank), prompts 1 to 5 are identical byte for byte to the prompts without the parameter")
    void noContextIsByteIdentical() {
        for (Language l : List.of(Language.FR, Language.EN)) {
            for (String none : Arrays.asList(null, "  ")) {
                assertEquals(LlmPrompts.analysis("DOC", l), LlmPrompts.analysis("DOC", l, none));
                assertEquals(LlmPrompts.stories("DOC", l), LlmPrompts.stories("DOC", l, none));
                assertEquals(LlmPrompts.verdicts(List.of(story()), List.of("s1"), l),
                        LlmPrompts.verdicts(List.of(story()), List.of("s1"), l, none));
                assertEquals(LlmPrompts.reassessment("T", "D", "P", "Q ?", l),
                        LlmPrompts.reassessment("T", "D", "P", "Q ?", l, none));
                assertEquals(LlmPrompts.tasks(List.of(story()), Arrays.asList((String) null), List.of("s1"), l),
                        LlmPrompts.tasks(List.of(story()), Arrays.asList((String) null), List.of("s1"), l, none));
                assertEquals(LlmPrompts.testCases(List.of(story()), Arrays.asList((String) null), List.of("s1"), l),
                        LlmPrompts.testCases(List.of(story()), Arrays.asList((String) null), List.of("s1"), l, none));
            }
            assertFalse(LlmPrompts.analysis("DOC", l).contains("CONTEXT"));
        }
    }

    @Test
    @DisplayName("With a context: the context block heads prompts 1 to 5; the READINESS rule only in prompts 1, 2, 3 and chain 1")
    void contextBlockAndRule() {
        String frRule = "CONTEXTE PROJET : une règle ou une définition donnée par le contexte projet compte comme donnée par la spécification.";
        String enRule = "PROJECT CONTEXT: a rule or a definition given by the project context counts as given by the specification.";
        for (Language l : List.of(Language.FR, Language.EN)) {
            String rule = l.isFrench() ? frRule : enRule;
            String head = l.isFrench() ? "CONTEXTE PROJET (fourni par l'équipe" : "PROJECT CONTEXT (provided by the team";
            List<String> withRule = List.of(LlmPrompts.analysis("DOC", l, CTX), LlmPrompts.stories("DOC", l, CTX),
                    LlmPrompts.verdicts(List.of(story()), List.of("s1"), l, CTX),
                    LlmPrompts.reassessment("T", "D", "P", null, l, CTX));
            List<String> withoutRule = List.of(
                    LlmPrompts.tasks(List.of(story()), Arrays.asList((String) null), List.of("s1"), l, CTX),
                    LlmPrompts.testCases(List.of(story()), Arrays.asList((String) null), List.of("s1"), l, CTX));
            for (String p : withRule) {
                assertTrue(p.startsWith(head), p);
                assertTrue(p.contains(CTX.strip()), p);
                assertTrue(p.contains(rule), p);
            }
            for (String p : withoutRule) {
                assertTrue(p.startsWith(head), p);
                assertFalse(p.contains(rule), p);
            }
            // Le prompt sans contexte suit le bloc, inchange (le contexte n'est jamais relu : son marqueur reste du texte)
            String plain = LlmPrompts.tasks(List.of(story()), Arrays.asList((String) null), List.of("s1"), l);
            assertTrue(withoutRule.getFirst().endsWith(plain));
            assertTrue(withRule.getFirst().contains("{{DOCUMENT}}"));
        }
    }
}
