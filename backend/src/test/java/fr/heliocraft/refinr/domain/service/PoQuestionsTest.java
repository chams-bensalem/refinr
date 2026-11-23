// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.PoQuestion;
import fr.heliocraft.refinr.domain.model.ReadinessSource;
import fr.heliocraft.refinr.domain.model.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PoQuestionsTest {

    private static Story story(String title, Boolean ready, String reason, ReadinessSource source) {
        Story story = new Story();
        story.setTitle(title);
        story.setAiReady(ready);
        story.setAiReadyReason(reason);
        story.setAiReadySource(source);
        return story;
    }

    private static List<Epic> backlog(Story... stories) {
        Epic epic = new Epic();
        epic.setName("E");
        epic.setStories(new ArrayList<>(List.of(stories)));
        return List.of(epic);
    }

    @Test
    @DisplayName("One question per distinct aiReadyReason of the not-ready stories, exact duplicates grouped, order kept")
    void groupsExactDuplicates() {
        Story a = story("A", false, "Quel est le format du fichier ?", ReadinessSource.MODEL);
        Story b = story("B", true, "Clair.", ReadinessSource.MODEL);
        Story c = story("C", false, "  quel est le FORMAT du   fichier?  ", ReadinessSource.MODEL);
        Story d = story("D", false, "Qui valide la demande ?", ReadinessSource.MODEL);
        c.setTrackerKey("PROJ-3");

        List<PoQuestion> questions = PoQuestions.from(backlog(a, b, c, d));

        assertEquals(2, questions.size());
        assertEquals("Quel est le format du fichier ?", questions.get(0).question(), "first wording kept");
        assertEquals(List.of(a, c), questions.get(0).stories());
        assertEquals(List.of(d), questions.get(1).stories());
    }

    @Test
    @DisplayName("Never merged: two different questions stay two entries")
    void differentQuestionsStaySeparate() {
        List<PoQuestion> questions = PoQuestions.from(backlog(
                story("A", false, "Quel format ?", ReadinessSource.MODEL),
                story("B", false, "Quel format de date ?", ReadinessSource.MODEL)));
        assertEquals(2, questions.size());
    }

    @Test
    @DisplayName("Ready stories, fallback verdicts (no model verdict) and empty reasons give no question")
    void ignoredStories() {
        List<PoQuestion> questions = PoQuestions.from(backlog(
                story("A", true, "Clair.", ReadinessSource.MODEL),
                story("B", false, "verdict indisponible (no verdict) : validation humaine necessaire", ReadinessSource.FALLBACK),
                story("C", false, " ", ReadinessSource.MODEL),
                story("D", null, null, null)));
        assertTrue(questions.isEmpty(), questions.toString());
        assertTrue(PoQuestions.from(null).isEmpty());
    }

    @Test
    @DisplayName("isQuestion: the reason ends with '?' (trailing spaces ignored)")
    void isQuestion() {
        assertTrue(PoQuestions.isQuestion("Quel format ?  "));
        assertFalse(PoQuestions.isQuestion("Le format manque."));
        assertFalse(PoQuestions.isQuestion(null));
    }
}
