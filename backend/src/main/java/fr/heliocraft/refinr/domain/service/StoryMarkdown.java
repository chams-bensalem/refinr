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

    /**
     * Index du debut de la premiere ligne de verdict (espaces en tete toleres : texte reecrit par un editeur), ou -1.
     */
    static int verdictLineStart(String text) {
        int lineStart = 0;
        while (lineStart <= text.length()) {
            int end = text.indexOf('\n', lineStart);
            String line = text.substring(lineStart, end < 0 ? text.length() : end);
            // Ligne courante ou remplacee (barree) : la section se place avant tout l'historique du verdict
            if (isVerdictLine(line)) {
                return lineStart;
            }
            if (end < 0) {
                return -1;
            }
            lineStart = end + 1;
        }
        return -1;
    }

    private static final java.util.regex.Pattern ENTITY =
            java.util.regex.Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|amp|lt|gt|quot|apos|nbsp);");

    /**
     * Entites HTML d'une description relue dans le gestionnaire (l'editeur OpenProject ecrit {@code &#x27;},
     * {@code &amp;}…), decodees avant l'envoi au modele ; toute autre entite reste telle quelle.
     */
    public static String decodeEntities(String text) {
        if (text == null || text.indexOf('&') < 0) {
            return text;
        }
        java.util.regex.Matcher m = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String value = switch (name) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                case "nbsp" -> "\u00a0";
                default -> {
                    int code = name.charAt(1) == 'x' || name.charAt(1) == 'X'
                            ? Integer.parseInt(name.substring(2), 16) : Integer.parseInt(name.substring(1));
                    yield Character.isValidCodePoint(code) ? new String(Character.toChars(code)) : m.group();
                }
            };
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    // Lineaire (pas de quantificateur paresseux suivi d'espaces) : la capture est rognee ensuite
    private static final java.util.regex.Pattern CRITERION_LINE =
            java.util.regex.Pattern.compile("(?m)^[ \\t]*+(?:[-*+][ \\t]++)?(?:\\*\\*)?AC(\\d{1,3}):(?:\\*\\*)?[ \\t]*+(.+)$");

    /**
     * Criteres d'une description ecrite par Refinr ({@code AC1:} …), dans l'ordre ; liste vide sinon. Tolere le texte
     * reecrit par un editeur : puce {@code -}, {@code *} ou {@code +} suivie d'espaces, gras, espaces en fin de ligne,
     * lignes indentees vides ; entites HTML decodees.
     */
    public static List<String> criteria(String description) {
        List<String> criteria = new java.util.ArrayList<>();
        if (description == null) {
            return criteria;
        }
        java.util.regex.Matcher m = CRITERION_LINE.matcher(description);
        while (m.find()) {
            if (Integer.parseInt(m.group(1)) == criteria.size() + 1) {
                criteria.add(decodeEntities(m.group(2)).strip());
            }
        }
        return criteria;
    }

    /** Ligne de la precision fournie par l'utilisateur lors d'une reevaluation. */
    public static String precisionLine(String precision) {
        return precisionLine(precision, fr.heliocraft.refinr.domain.model.Language.FR);
    }

    /** Ligne de la precision, prefixe dans la langue de la reevaluation (francais, sinon anglais). */
    public static String precisionLine(String precision, fr.heliocraft.refinr.domain.model.Language language) {
        return (french(language) ? "Précision de l'utilisateur (réévaluation) : " : "User clarification (reassessment): ")
                + precision;
    }

    /** Mention ajoutee a une ligne de verdict remplacee : « (remplacée le AAAA-MM-JJ) » ou « (replaced on …) ». */
    public static String replacedSuffix(fr.heliocraft.refinr.domain.model.Language language, java.time.LocalDate date) {
        return french(language) ? " (remplacée le " + date + ")" : " (replaced on " + date + ")";
    }

    private static boolean french(fr.heliocraft.refinr.domain.model.Language language) {
        return language == null || language.isFrench();
    }

    private static final java.util.regex.Pattern LEADING_BULLET = java.util.regex.Pattern.compile("^[-*+][ \\t]++");
    private static final java.util.regex.Pattern VERDICT =
            java.util.regex.Pattern.compile("^AI readiness \\(([^)]{0,40})\\):[ \\t]*+(.*)$");

    /** Texte d'une ligne sans puce, gras ni barre : ce qui suit reste a comparer au prefixe du verdict. */
    private static String bare(String line) {
        String t = decodeEntities(line).strip();
        t = LEADING_BULLET.matcher(t).replaceFirst("");
        return t.startsWith("~~") ? t.substring(2) : t;
    }

    /**
     * Vrai si la ligne est une ligne de verdict, courante ou remplacee (barree) ; tolere le texte reecrit par un
     * editeur (puce, entites, espaces en tete).
     */
    public static boolean isVerdictLine(String line) {
        return line != null && bare(line).startsWith(VERDICT_LINE_PREFIX);
    }

    /** Vrai si la ligne est une ligne de verdict deja remplacee (barree). */
    public static boolean isReplacedVerdictLine(String line) {
        if (!isVerdictLine(line)) {
            return false;
        }
        String t = LEADING_BULLET.matcher(decodeEntities(line).strip()).replaceFirst("");
        return t.startsWith("~~");
    }

    /**
     * Texte envoye au modele (prompts 3, 4, 5) : entites decodees, lignes de verdict (courantes et remplacees)
     * retirees ; les precisions precedentes restent.
     */
    public static String forModel(String description) {
        if (description == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String line : decodeEntities(description).split("\n", -1)) {
            if (!isVerdictLine(line)) {
                out.append(line).append('\n');
            }
        }
        return out.toString().replaceAll("\n{3,}", "\n\n").strip();
    }

    /**
     * Derniere question ouverte : la raison de la derniere ligne de verdict si elle est {@code validation-necessaire} ;
     * null si la derniere est {@code ai-ready} ou s'il n'y en a pas.
     */
    public static String lastOpenQuestion(String description) {
        if (description == null) {
            return null;
        }
        String verdict = null;
        String reason = null;
        for (String line : description.split("\n", -1)) {
            if (!isVerdictLine(line)) {
                continue;
            }
            java.util.regex.Matcher m = VERDICT.matcher(bare(line));
            if (m.matches()) {
                verdict = m.group(1).strip();
                String text = m.group(2);
                int struck = text.indexOf("~~");
                reason = (struck >= 0 ? text.substring(0, struck) : text).strip();
            }
        }
        return ReadinessPolicy.LABEL_VALIDATION.equals(verdict) && reason != null && !reason.isEmpty() ? reason : null;
    }

    /**
     * Markdown (OpenProject) : chaque ligne de verdict courante est barree avec la mention de remplacement, puis
     * {@code block} (precision eventuelle et nouvelle ligne) est insere juste apres la derniere ligne de verdict
     * (a la fin s'il n'y en a pas). Le reste du texte (note du PO comprise) est intact.
     */
    public static String replaceVerdict(String markdown, String suffix, String block) {
        String text = markdown != null ? markdown.stripTrailing() : "";
        String[] lines = text.isEmpty() ? new String[0] : text.split("\n", -1);
        int last = -1;
        for (int i = 0; i < lines.length; i++) {
            if (isVerdictLine(lines[i])) {
                last = i;
                if (!isReplacedVerdictLine(lines[i])) {
                    java.util.regex.Matcher bullet = LEADING_BULLET.matcher(lines[i].stripLeading());
                    String indent = lines[i].substring(0, lines[i].length() - lines[i].stripLeading().length());
                    String prefix = bullet.find() ? bullet.group() : "";
                    String body = lines[i].stripLeading().substring(prefix.length()).stripTrailing();
                    lines[i] = indent + prefix + "~~" + body + "~~" + suffix;
                }
            }
        }
        if (last < 0) {
            return text.isEmpty() ? block : text + "\n\n" + block;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            out.append(lines[i]);
            if (i == last) {
                out.append("\n\n").append(block);
            }
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
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
