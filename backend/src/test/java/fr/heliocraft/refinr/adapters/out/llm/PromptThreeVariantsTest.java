// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Outil de mesure R3 : la copie figee de verdict-v3.3 est bien l'ancien texte, distinct de la production (v3.4). */
class PromptThreeVariantsTest {

    @Test
    @DisplayName("Frozen verdict-v3.3 copy: old single rule, no steps; question line kept only with a question; fr and en")
    void frozenCopyIsTheOldText() {
        String fr = PromptThreeVariants.frozen(Language.FR, "T", "D", "P", "Q ?");
        assertTrue(fr.contains("Si une autre question reste ouverte, la story reste non prête"), fr);
        assertFalse(fr.contains("ÉTAPE"));
        assertTrue(fr.contains("Question bloquante précédente : Q ?"));
        assertFalse(PromptThreeVariants.frozen(Language.FR, "T", "D", "P", null).contains("Question bloquante"));
        String en = PromptThreeVariants.frozen(Language.EN, "T", "D", "P", null);
        assertTrue(en.contains("If another question remains open, the story stays not ready"), en);
        assertFalse(en.contains("STEP"));
        assertNotEquals(fr, LlmPrompts.reassessment("T", "D", "P", "Q ?", Language.FR), "production is v3.4");
    }
}
