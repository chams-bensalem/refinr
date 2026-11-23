// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.TestCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Regles des cas de test (C2) et leur place dans la description. */
class TestCasePolicyTest {

    private static TestCase tc(String criterion, String kind) {
        return new TestCase("Book a room", criterion, kind, "a free room", "the employee books it", "the booking is saved");
    }

    private static Story story(TestCase... testCases) {
        Story story = new Story();
        story.setTitle("Book");
        story.setAcceptanceCriteria(List.of("Given …, When …, Then …", "Given …, When …, Then …"));
        story.setTestCases(new ArrayList<>(List.of(testCases)));
        story.setAiReady(true);
        story.setAiReadyReason("clear");
        return story;
    }

    @Test
    @DisplayName("Client test cases: criterion must exist, kind valid, steps filled, lengths bounded; message never quotes content")
    void violations() {
        assertNull(TestCasePolicy.violation(story(tc("AC1", "nominal"), tc("AC2", "edge")), "1.1"));
        assertEquals("testCases[1] of story 1.1: criterion must name an existing acceptance criterion (AC1 to AC2)",
                TestCasePolicy.violation(story(tc("AC1", "nominal"), tc("AC3", "nominal")), "1.1"));
        assertTrue(TestCasePolicy.violation(story(tc("ac1", "nominal")), "1.1").contains("criterion"));
        assertTrue(TestCasePolicy.violation(story(tc("AC1", "smoke")), "1.1").contains("kind must be nominal, error or edge"));
        assertTrue(TestCasePolicy.violation(story(new TestCase("t", "AC1", "error", " ", "w", "t")), "1.1")
                .contains("title, given, when and then are required"));
        String secret = "Secret-marker-c2 " + "x".repeat(TestCasePolicy.MAX_STEP);
        String tooLong = TestCasePolicy.violation(story(new TestCase("t", "AC1", "error", secret, "w", "t")), "2.3");
        assertTrue(tooLong.startsWith("testCases[0] of story 2.3: given, when and then are limited to"), tooLong);
        assertFalse(tooLong.contains("Secret-marker-c2"), "content never quoted");
        assertTrue(TestCasePolicy.violation(story(new TestCase("t".repeat(201), "AC1", "edge", "g", "w", "t")), "1.1")
                .contains("title is longer than 200"));
    }

    @Test
    @DisplayName("Model output: invalid test cases are dropped (never corrected), valid ones trimmed")
    void validKeepsOnlyValid() {
        List<TestCase> kept = TestCasePolicy.valid(List.of(tc(" AC1 ", "nominal"), tc("AC9", "nominal"), tc("AC2", "bad")), 2);
        assertEquals(List.of(tc("AC1", "nominal")), kept);
        assertTrue(TestCasePolicy.valid(null, 2).isEmpty());
    }

    @Test
    @DisplayName("Creation: ### Test cases after the acceptance criteria, before the verdict line; one line per case")
    void sectionBeforeTheVerdictLine() {
        String description = StoryMarkdown.description(story(tc("AC1", "nominal"), tc("AC2", "error")));
        int criteria = description.indexOf("### Acceptance Criteria");
        int section = description.indexOf("### " + StoryMarkdown.TEST_CASES_TITLE);
        int verdict = description.indexOf(StoryMarkdown.VERDICT_LINE_PREFIX);
        assertTrue(criteria >= 0 && criteria < section && section < verdict, description);
        assertTrue(description.contains("- **TC1 (AC1, nominal):** Book a room — Given a free room, When the employee books it, "
                + "Then the booking is saved"), description);
        assertTrue(description.contains("- **TC2 (AC2, error):**"));
        assertFalse(StoryMarkdown.description(story()).contains(StoryMarkdown.TEST_CASES_TITLE), "no section without cases");
    }

    @Test
    @DisplayName("Later insertion: same place as at creation (before the verdict line if any, otherwise at the end)")
    void insertionSamePlace() {
        String section = StoryMarkdown.testCasesSection(List.of(tc("AC1", "nominal")));
        Story story = story();
        String created = StoryMarkdown.description(story);
        String inserted = StoryMarkdown.insertTestCases(created, section);
        assertEquals(StoryMarkdown.description(story(tc("AC1", "nominal"))).strip(), inserted.strip(),
                "inserted later = written at creation");
        assertEquals("Intro\n\n" + section, StoryMarkdown.insertTestCases("Intro\n", section), "no verdict line: at the end");
        assertEquals("text AI readiness (x) inline\n\n" + section,
                StoryMarkdown.insertTestCases("text AI readiness (x) inline", section), "verdict prefix only at a line start");
        String reassessed = created + "\n\nAI readiness (ai-ready): later line";
        String twice = StoryMarkdown.insertTestCases(reassessed, section);
        assertTrue(twice.indexOf(section) < twice.indexOf("AI readiness (validation-necessaire)") + 1_000_000);
        assertTrue(twice.indexOf(section) < twice.indexOf(StoryMarkdown.VERDICT_LINE_PREFIX), "before the first verdict line");
    }

    @Test
    @DisplayName("Idempotence: the section is found by its title (constant list, ready for a translated title in L2)")
    void detection() {
        assertTrue(StoryMarkdown.hasTestCases("x\n### Test cases\n- TC1"));
        assertTrue(StoryMarkdown.hasTestCases("x\nTest cases\nTC1"), "Jira plain text of the heading");
        assertFalse(StoryMarkdown.hasTestCases("We need test cases soon"));
        assertFalse(StoryMarkdown.hasTestCases(null));
        assertEquals(List.of(StoryMarkdown.TEST_CASES_TITLE), StoryMarkdown.TEST_CASES_TITLES);
    }


    @Test
    @DisplayName("Description rewritten by the OpenProject editor: '*   **AC1:**', indented blank lines, entities, trailing spaces")
    void editorRewrittenDescription() {
        assertEquals(List.of(
                "Given un gestionnaire connecté, When il ouvre le tableau de bord, Then l'occupation des salles est affichée.",
                "Given une salle sans réservation sur la période affichée, When le gestionnaire consulte le tableau de bord, "
                        + "Then cette salle apparaît comme inoccupée."), StoryMarkdown.criteria(fr.heliocraft.refinr.TrackerFixtures.OPENPROJECT_EDITED));
        assertFalse(StoryMarkdown.criteria(fr.heliocraft.refinr.TrackerFixtures.OPENPROJECT_EDITED).toString().contains("&#x27;"), "entities decoded");

        String section = StoryMarkdown.testCasesSection(List.of(tc("AC1", "nominal"), tc("AC2", "edge")));
        String inserted = StoryMarkdown.insertTestCases(fr.heliocraft.refinr.TrackerFixtures.OPENPROJECT_EDITED, section);
        int verdict = fr.heliocraft.refinr.TrackerFixtures.OPENPROJECT_EDITED.indexOf("AI readiness (");
        String tail = fr.heliocraft.refinr.TrackerFixtures.OPENPROJECT_EDITED.substring(verdict);
        assertTrue(inserted.endsWith(tail), "verdict line, labels and the PO note intact");
        assertTrue(inserted.indexOf(section) < inserted.indexOf("AI readiness ("), "section before the verdict line");
        assertTrue(inserted.contains("Note du PO : affichage par semaine en priorité."));
        assertTrue(inserted.startsWith(fr.heliocraft.refinr.TrackerFixtures.OPENPROJECT_EDITED.substring(0, fr.heliocraft.refinr.TrackerFixtures.OPENPROJECT_EDITED.indexOf("\n\nAI readiness")).stripTrailing()),
                "text before the verdict line kept (only trailing blanks trimmed)");
    }

    @Test
    @DisplayName("Security review: the criteria pattern stays linear on a long line of spaces (no catastrophic backtracking)")
    @org.junit.jupiter.api.Timeout(2)
    void criteriaPatternIsLinear() {
        String hostile = "AC1: x" + " ".repeat(30_000) + "y" + " \t".repeat(10_000);
        assertEquals(1, StoryMarkdown.criteria(hostile).size());
        assertTrue(StoryMarkdown.criteria(" ".repeat(32_000)).isEmpty());
    }

    @Test
    @DisplayName("HTML entities: named and numeric decoded, unknown ones left as they are")
    void entities() {
        assertEquals("l'occupation & « d\"un » <b> é", StoryMarkdown.decodeEntities("l&#x27;occupation &amp; « d&quot;un » &lt;b&gt; &#233;"));
        assertEquals("&unknown; stays", StoryMarkdown.decodeEntities("&unknown; stays"));
        assertNull(StoryMarkdown.decodeEntities(null));
    }

    @Test
    @DisplayName("Criteria read back from a description written by Refinr (Markdown and Jira plain text)")
    void criteriaReadBack() {
        Story story = story();
        assertEquals(story.getAcceptanceCriteria(), StoryMarkdown.criteria(StoryMarkdown.description(story)));
        assertEquals(List.of("first", "second"), StoryMarkdown.criteria("Description\nAC1: first\nAC2: second\nAC4: gap"));
        assertTrue(StoryMarkdown.criteria("no criteria here").isEmpty());
    }
}
