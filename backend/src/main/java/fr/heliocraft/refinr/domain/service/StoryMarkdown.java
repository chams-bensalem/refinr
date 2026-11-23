// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.TestCase;

import java.util.List;

/**
 * Texte Markdown commun a tous les gestionnaires de tickets : description d'une story (As a / I want / So that,
 * description, criteres d'acceptation, verdict) et lignes ajoutees lors d'une reevaluation. Chaque adaptateur le
 * convertit ou l'envoie tel quel selon son format.
 */
public final class StoryMarkdown {

    private StoryMarkdown() {
    }

    /** Description complete d'une story, verdict de preparation compris. */
    public static String description(Story story) {
        return userStory(story) + "\n\n" + acceptanceCriteria(story.getAcceptanceCriteria())
                + testCasesBlock(story.getTestCases()) + readiness(story);
    }

    /** Limite de la description d'un ticket (Jira : 32 767 caracteres) ; au-dela, la section n'est pas ecrite. */
    public static final int MAX_DESCRIPTION = 32_767;

    /**
     * Titre de la section des cas de test : une seule constante. {@link #TEST_CASES_TITLES} liste tous les titres
     * reconnus (L2 y ajoutera un titre traduit) : une section deja presente n'est jamais ajoutee une seconde fois.
     */
    public static final String TEST_CASES_TITLE = "Test cases";
    public static final List<String> TEST_CASES_TITLES = List.of(TEST_CASES_TITLE);
    /** Debut de la ligne de verdict (creation et reevaluation) : la section se place juste avant. */
    public static final String VERDICT_LINE_PREFIX = "AI readiness (";

    /** Section {@code ### Test cases} (titre et lignes), sans saut de ligne autour ; chaine vide sans cas. */
    public static String testCasesSection(List<TestCase> testCases) {
        if (testCases == null || testCases.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("### ").append(TEST_CASES_TITLE).append("\n\n");
        for (int i = 0; i < testCases.size(); i++) {
            TestCase t = testCases.get(i);
            sb.append("- **TC").append(i + 1).append(" (").append(t.criterion()).append(", ").append(t.kind())
                    .append("):** ").append(t.title()).append(" — Given ").append(t.given()).append(", When ")
                    .append(t.when()).append(", Then ").append(t.then()).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private static String testCasesBlock(List<TestCase> testCases) {
        String section = testCasesSection(testCases);
        return section.isEmpty() ? "" : "\n" + section;
    }

    /** Vrai si le texte contient deja une section de cas de test (titre seul sur sa ligne, avec ou sans #). */
    public static boolean hasTestCases(String text) {
        if (text == null) {
            return false;
        }
        for (String line : text.split("\n", -1)) {
            String title = line.strip().replaceFirst("^#+\\s*", "");
            if (TEST_CASES_TITLES.contains(title)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Texte Markdown avec la section inseree au meme endroit qu'a la creation : avant la premiere ligne de verdict si
     * elle existe, sinon a la fin.
     */
    public static String insertTestCases(String markdown, String section) {
        String text = markdown != null ? markdown.stripTrailing() : "";
        int at = verdictLineStart(text);
        if (at < 0) {
            return text.isEmpty() ? section : text + "\n\n" + section;
        }
        String before = text.substring(0, at).stripTrailing();
        return (before.isEmpty() ? "" : before + "\n\n") + section + "\n\n" + text.substring(at);
    }

    /** Index du debut de la premiere ligne de verdict, ou -1. */
    static int verdictLineStart(String text) {
        int from = 0;
        while (from <= text.length()) {
            int at = text.indexOf(VERDICT_LINE_PREFIX, from);
            if (at < 0) {
                return -1;
            }
            if (at == 0 || text.charAt(at - 1) == '\n') {
                return at;
            }
            from = at + 1;
        }
        return -1;
    }

    /** Criteres d'une description ecrite par Refinr ({@code AC1:} …), dans l'ordre ; liste vide sinon. */
    public static List<String> criteria(String description) {
        List<String> criteria = new java.util.ArrayList<>();
        if (description == null) {
            return criteria;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*(?:-\\s*)?(?:\\*\\*)?AC(\\d+):(?:\\*\\*)?\\s*(.+)$")
                .matcher(description);
        while (m.find()) {
            if (Integer.parseInt(m.group(1)) == criteria.size() + 1) {
                criteria.add(m.group(2).strip());
            }
        }
        return criteria;
    }

    /** Ligne de la precision fournie par l'utilisateur lors d'une reevaluation. */
    public static String precisionLine(String precision) {
        return "Precision de l'utilisateur (reevaluation): " + precision;
    }

    /** Ligne du nouveau verdict apres une reevaluation (justification par defaut si absente). */
    public static String reassessedReadinessLine(Readiness readiness) {
        String reason = readiness.aiReadyReason();
        String justification = reason != null && !reason.isBlank() ? reason : "non precise par l'analyse";
        return "AI readiness (" + ReadinessPolicy.verdictLabel(readiness.aiReady()) + "): " + justification;
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(value).append("\n");
        }
    }

    private static String userStory(Story story) {
        StringBuilder sb = new StringBuilder();

        // Ligne absente ou vide (le modele n'a pas de valeur) : pas ecrite
        appendLine(sb, "**As a** ", story.getAsA());
        appendLine(sb, "**I want** ", story.getIWant());
        appendLine(sb, "**So that** ", story.getSoThat());
        sb.append("\n");

        if (story.getDescription() != null && !story.getDescription().isEmpty()) {
            sb.append("### Description\n\n");
            sb.append(story.getDescription()).append("\n");
        }

        return sb.toString();
    }

    private static String acceptanceCriteria(List<String> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return "No acceptance criteria defined";
        }

        StringBuilder formatted = new StringBuilder();
        formatted.append("### Acceptance Criteria\n\n");

        for (int i = 0; i < criteria.size(); i++) {
            formatted.append("- **AC").append(i + 1).append(":** ");
            formatted.append(criteria.get(i)).append("\n");
        }

        return formatted.toString();
    }

    private static String readiness(Story story) {
        String verdict = ReadinessPolicy.verdictLabel(story.getAiReady());
        String reason = story.getAiReadyReason() != null ? story.getAiReadyReason() : "non evalue par l'analyse";
        return "\n\nAI readiness (" + verdict + "): " + reason;
    }
}
