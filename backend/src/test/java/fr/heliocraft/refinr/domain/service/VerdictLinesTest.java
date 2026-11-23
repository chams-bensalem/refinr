// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.TrackerFixtures;
import fr.heliocraft.refinr.domain.model.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** B3 : lignes de verdict remplacees (barrees, mention datee selon la langue) et texte envoye au modele. */
class VerdictLinesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 24);
    private static final String STORY = "As a user\n\n### Acceptance Criteria\n\n- **AC1:** Given a\n\n"
            + "AI readiness (validation-necessaire): Which channel?\n\n**Labels:** backend\n\nNote du PO : semaine.";

    @Test
    @DisplayName("Replacement note and precision prefix follow the language (fr, en)")
    void languages() {
        assertEquals(" (remplacée le 2026-11-24)", StoryMarkdown.replacedSuffix(Language.FR, DAY));
        assertEquals(" (replaced on 2026-11-24)", StoryMarkdown.replacedSuffix(Language.EN, DAY));
        assertEquals(" (replaced on 2026-11-24)", StoryMarkdown.replacedSuffix(Language.of("de"), DAY), "other languages: English");
        assertEquals("Précision de l'utilisateur (réévaluation) : e-mail", StoryMarkdown.precisionLine("e-mail", Language.FR));
        assertEquals("User clarification (reassessment): e-mail", StoryMarkdown.precisionLine("e-mail", Language.EN));
    }

    @Test
    @DisplayName("Verdict lines recognized: current, struck, with a bullet, entities or leading spaces; other lines are not")
    void recognition() {
        assertTrue(StoryMarkdown.isVerdictLine("AI readiness (ai-ready): ok"));
        assertTrue(StoryMarkdown.isVerdictLine("  ~~AI readiness (validation-necessaire): Which?~~ (remplacée le 2026-11-24)"));
        assertTrue(StoryMarkdown.isVerdictLine("*   AI readiness (validation-necessaire): l&#x27;écran ?"));
        assertTrue(StoryMarkdown.isReplacedVerdictLine("~~AI readiness (ai-ready): ok~~ (replaced on 2026-11-24)"));
        assertFalse(StoryMarkdown.isReplacedVerdictLine("AI readiness (ai-ready): ok"));
        assertFalse(StoryMarkdown.isVerdictLine("The AI readiness (x) is mentioned inline"));
    }

    @Test
    @DisplayName("One reassessment: old line struck with the dated note, precision and new line right after, PO note intact")
    void oneReplacement() {
        String block = StoryMarkdown.precisionLine("E-mail only", Language.EN) + "\n\nAI readiness (ai-ready): Clear now.";
        String updated = StoryMarkdown.replaceVerdict(STORY, StoryMarkdown.replacedSuffix(Language.EN, DAY), block);

        assertTrue(updated.contains("~~AI readiness (validation-necessaire): Which channel?~~ (replaced on 2026-11-24)\n\n"
                + "User clarification (reassessment): E-mail only\n\nAI readiness (ai-ready): Clear now.\n\n**Labels:** backend"), updated);
        assertTrue(updated.endsWith("Note du PO : semaine."), "PO note intact");
        assertTrue(updated.startsWith("As a user\n\n### Acceptance Criteria\n\n- **AC1:** Given a\n\n"), "start intact");
    }

    @Test
    @DisplayName("Two reassessments: the first struck line stays as is, the second is struck, the new line comes after both")
    void twoReplacements() {
        String suffix = StoryMarkdown.replacedSuffix(Language.FR, DAY);
        String once = StoryMarkdown.replaceVerdict(STORY, suffix, "AI readiness (validation-necessaire): Which format?");
        String twice = StoryMarkdown.replaceVerdict(once, suffix, "AI readiness (ai-ready): ok");

        assertEquals(1, twice.split("~~AI readiness \\(validation-necessaire\\): Which channel\\?~~", -1).length - 1, "struck once");
        assertTrue(twice.contains("~~AI readiness (validation-necessaire): Which format?~~ (remplacée le 2026-11-24)"), twice);
        int first = twice.indexOf("Which channel?"), second = twice.indexOf("Which format?"), current = twice.indexOf("AI readiness (ai-ready): ok");
        assertTrue(first < second && second < current, twice);
        assertEquals(1, twice.lines().filter(l -> StoryMarkdown.isVerdictLine(l) && !StoryMarkdown.isReplacedVerdictLine(l)).count(),
                "a single current verdict line");
    }

    @Test
    @DisplayName("Description rewritten by the OpenProject editor (real fixture): verdict line struck in place, PO note last")
    void editorRewritten() {
        String updated = StoryMarkdown.replaceVerdict(TrackerFixtures.OPENPROJECT_EDITED,
                StoryMarkdown.replacedSuffix(Language.FR, DAY), "AI readiness (ai-ready): Hebdomadaire.");
        assertTrue(updated.contains("~~AI readiness (validation-necessaire): Quels indicateurs précis"), updated);
        assertTrue(updated.contains("~~ (remplacée le 2026-11-24)\n\nAI readiness (ai-ready): Hebdomadaire.\n\n**Labels:**"), updated);
        assertTrue(updated.endsWith("Note du PO : affichage par semaine en priorité."));
    }

    @Test
    @DisplayName("Text sent to the model: verdict lines (current and struck) removed, previous precisions and PO note kept")
    void forModel() {
        String history = StoryMarkdown.replaceVerdict(STORY, StoryMarkdown.replacedSuffix(Language.FR, DAY),
                StoryMarkdown.precisionLine("Slack", Language.FR) + "\n\nAI readiness (validation-necessaire): Which room?");
        String sent = StoryMarkdown.forModel(history);

        assertFalse(sent.contains("AI readiness"), sent);
        assertTrue(sent.contains("Précision de l'utilisateur (réévaluation) : Slack"), "previous precision kept");
        assertTrue(sent.contains("Note du PO : semaine.") && sent.contains("- **AC1:** Given a"));
        assertFalse(sent.contains("\n\n\n"), "no hole left");
        assertFalse(StoryMarkdown.forModel(TrackerFixtures.OPENPROJECT_EDITED).contains("&#x27;"), "entities decoded");
    }

    @Test
    @DisplayName("Last open question: reason of the last verdict line if validation-necessaire; absent if it is ai-ready")
    void lastOpenQuestion() {
        assertEquals("Which channel?", StoryMarkdown.lastOpenQuestion(STORY));
        String suffix = StoryMarkdown.replacedSuffix(Language.FR, DAY);
        String stillBlocked = StoryMarkdown.replaceVerdict(STORY, suffix, "AI readiness (validation-necessaire): Which room?");
        assertEquals("Which room?", StoryMarkdown.lastOpenQuestion(stillBlocked), "the most recent one");
        assertNull(StoryMarkdown.lastOpenQuestion(StoryMarkdown.replaceVerdict(stillBlocked, suffix, "AI readiness (ai-ready): ok")));
        assertNull(StoryMarkdown.lastOpenQuestion("no verdict line"));
        assertEquals("Quels indicateurs précis le tableau de bord d'occupation doit-il afficher et sur quelle période ?",
                StoryMarkdown.lastOpenQuestion(TrackerFixtures.OPENPROJECT_EDITED), "editor text, entities decoded");
    }
}
