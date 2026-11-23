// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Story;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompts envoyes aux modeles (le format de sortie est en plus impose par le schema JSON), charges de deux ressources
 * seulement : {@code llm/prompts/prompts.fr.md} pour le francais, {@code llm/prompts/prompts.en.md} pour l'anglais et
 * toute autre langue. Chaque fichier contient huit blocs de code, dans l'ordre : message systeme, bloc READINESS,
 * generation, verdict par lots, reevaluation, generation sans sous-taches (chaine C1), sous-taches des stories pretes,
 * cas de test des stories pretes (C2). Les marqueurs {@code {{…}}} du texte sont remplaces par les valeurs ;
 * {@code {{LANGUAGE}}} recoit le nom de la langue de sortie, jamais une valeur recue d'un utilisateur.
 */
final class LlmPrompts {

    static final String FR_RESOURCE = "llm/prompts/prompts.fr.md";
    static final String EN_RESOURCE = "llm/prompts/prompts.en.md";

    /** Textes d'une langue, marqueurs non remplaces. */
    record Texts(String system, String readiness, String analysis, String verdicts, String reassessment,
                 String stories, String tasks, String testCases) {}

    private static final Pattern BLOCK = Pattern.compile("```\\n(.*?)\\n```", Pattern.DOTALL);
    private static final Pattern MARKER = Pattern.compile("\\{\\{([A-Z_]+)\\}\\}");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    static final Texts FR = load(FR_RESOURCE);
    static final Texts EN = load(EN_RESOURCE);

    /** Version des prompts de verdict : fait partie de la cle du cache (un prompt modifie invalide le cache). */
    static final String VERDICT_PROMPT_VERSION = "verdict-v3.2";
    /** Version du prompt 3 (reevaluation) : derniere question ouverte en champ separe, lignes de verdict retirees (B3). */
    static final String REASSESSMENT_PROMPT_VERSION = "verdict-v3.4";
    /** Marqueur de ligne du prompt 3 : gardee seulement s'il y a une question ouverte precedente. */
    static final String IF_QUESTION = "{{IF_QUESTION}}";

    /** Version du prompt de generation (prompt 1), relevee dans les mesures ; le verdict garde sa propre version. */
    static final String GENERATION_PROMPT_VERSION = "generation-v2";
    /** Version du prompt de generation de la chaine C1 (sans sous-taches). */
    static final String STORIES_PROMPT_VERSION = "generation-v3";
    /** Version du prompt des sous-taches des stories pretes (prompt 4). */
    static final String TASKS_PROMPT_VERSION = "tasks-v1";
    /** Version du prompt des cas de test des stories pretes (prompt 5). */
    static final String TEST_CASES_PROMPT_VERSION = "test-cases-v1";

    private LlmPrompts() {}

    /** Francais : prompt francais ; toute autre langue : prompt anglais, avec le nom anglais de la langue. */
    static Texts texts(Language language) {
        return language.isFrench() ? FR : EN;
    }

    /** Nom injecte dans {@code {{LANGUAGE}}} : « français » pour le prompt francais, sinon le nom anglais. */
    static String languageName(Language language) {
        return language.displayName(language.isFrench() ? Locale.FRENCH : Locale.ENGLISH);
    }

    static String system(Language language) {
        return texts(language).system();
    }

    static String readiness(Language language) {
        return texts(language).readiness();
    }

    /** Prompt 1 : generation du backlog. */
    static String analysis(String documentContent, Language language) {
        Texts t = texts(language);
        return fill(t.analysis(), Map.of("READINESS", t.readiness(), "DOCUMENT", documentContent,
                "LANGUAGE", languageName(language)));
    }

    /**
     * Prompt 2 : verdict de plusieurs stories en un appel ; chaque story est identifiee par {@code ids.get(i)}. Les
     * stories sont encodees en JSON : leur contenu ne peut pas imiter l'en-tete ou l'identifiant d'une autre story du
     * lot. Cles francaises dans le prompt francais, anglaises dans le prompt anglais.
     */
    static String verdicts(List<Story> stories, List<String> ids, Language language) {
        boolean french = language.isFrench();
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < stories.size(); i++) {
            Story story = stories.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ids.get(i));
            item.put(french ? "titre" : "title", story.getTitle() != null ? story.getTitle() : "");
            item.put("description", story.getDescription() != null ? story.getDescription() : "");
            item.put(french ? "criteresAcceptation" : "acceptanceCriteria",
                    story.getAcceptanceCriteria() != null ? story.getAcceptanceCriteria() : List.of());
            items.add(item);
        }
        Texts t = texts(language);
        return fill(t.verdicts(), Map.of("READINESS", t.readiness(),
                "STORIES_JSON", JSON.writerWithDefaultPrettyPrinter().writeValueAsString(items),
                "LANGUAGE", languageName(language)));
    }

    /** Prompt 1 de la chaine C1 : generation sans sous-taches. */
    static String stories(String documentContent, Language language) {
        Texts t = texts(language);
        return fill(t.stories(), Map.of("READINESS", t.readiness(), "DOCUMENT", documentContent,
                "LANGUAGE", languageName(language)));
    }

    /**
     * Prompt 4 : sous-taches de plusieurs stories pretes en un appel, chacune identifiee par {@code ids.get(i)}, avec
     * la precision du PO quand il y en a une ({@code precisions.get(i)}, null sinon). Stories encodees en JSON, comme
     * pour le verdict ; cles francaises dans le prompt francais, anglaises dans le prompt anglais.
     */
    static String tasks(List<Story> stories, List<String> precisions, List<String> ids, Language language) {
        boolean french = language.isFrench();
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < stories.size(); i++) {
            Story story = stories.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ids.get(i));
            item.put(french ? "titre" : "title", story.getTitle() != null ? story.getTitle() : "");
            item.put("description", story.getDescription() != null ? story.getDescription() : "");
            item.put(french ? "criteresAcceptation" : "acceptanceCriteria",
                    story.getAcceptanceCriteria() != null ? story.getAcceptanceCriteria() : List.of());
            String precision = precisions.get(i);
            if (precision != null && !precision.isBlank()) {
                item.put(french ? "precisionDuPO" : "poClarification", precision);
            }
            items.add(item);
        }
        Texts t = texts(language);
        return fill(t.tasks(), Map.of("STORIES_JSON", JSON.writerWithDefaultPrettyPrinter().writeValueAsString(items),
                "LANGUAGE", languageName(language)));
    }

    /**
     * Prompt 5 : cas de test de plusieurs stories pretes en un appel. Criteres numerotes ({@code AC1: …}) pour que
     * chaque cas designe le sien ; stories encodees en JSON comme pour le verdict.
     */
    static String testCases(List<Story> stories, List<String> ids, Language language) {
        return testCases(stories, java.util.Collections.nCopies(stories.size(), null), ids, language);
    }

    /** Marqueur de ligne du prompt 5 : ligne gardee seulement si une story porte une precision du PO. */
    static final String IF_PRECISION = "{{IF_PRECISION}}";

    /**
     * Prompt 5 avec la precision du PO de chaque story (null sinon), donnee comme une donnee de la story. Sans aucune
     * precision, la ligne {@link #IF_PRECISION} est retiree : texte identique a celui de la chaine mesuree.
     */
    static String testCases(List<Story> stories, List<String> precisions, List<String> ids, Language language) {
        boolean french = language.isFrench();
        boolean anyPrecision = precisions.stream().anyMatch(p -> p != null && !p.isBlank());
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < stories.size(); i++) {
            Story story = stories.get(i);
            List<String> criteria = new ArrayList<>();
            List<String> source = story.getAcceptanceCriteria() != null ? story.getAcceptanceCriteria() : List.of();
            for (int c = 0; c < source.size(); c++) {
                criteria.add("AC" + (c + 1) + ": " + source.get(c));
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ids.get(i));
            item.put(french ? "titre" : "title", story.getTitle() != null ? story.getTitle() : "");
            item.put("description", story.getDescription() != null ? story.getDescription() : "");
            item.put(french ? "criteresAcceptation" : "acceptanceCriteria", criteria);
            String precision = precisions.get(i);
            if (precision != null && !precision.isBlank()) {
                item.put(french ? "precisionDuPO" : "poClarification", precision);
            }
            items.add(item);
        }
        Texts t = texts(language);
        String template = anyPrecision ? t.testCases().replace(IF_PRECISION, "")
                : t.testCases().replaceAll("(?m)^" + java.util.regex.Pattern.quote(IF_PRECISION) + ".*\\R", "");
        return fill(template, Map.of("STORIES_JSON", JSON.writerWithDefaultPrettyPrinter().writeValueAsString(items),
                "LANGUAGE", languageName(language)));
    }

    /** Prompt 3 : reevaluation d'une story jugee non prete, avec la precision de l'utilisateur. */
    static String reassessment(String summary, String description, String precision, Language language) {
        return reassessment(summary, description, precision, null, language);
    }

    /**
     * Prompt 3 avec la derniere question ouverte ({@code previousQuestion}, une donnee) ; null ou vide : la ligne est
     * retiree et le texte est celui de verdict-v3.2.
     */
    static String reassessment(String summary, String description, String precision, String previousQuestion,
                               Language language) {
        Texts t = texts(language);
        boolean question = previousQuestion != null && !previousQuestion.isBlank();
        String template = question ? t.reassessment().replace(IF_QUESTION, "")
                : t.reassessment().replaceAll("(?m)^" + java.util.regex.Pattern.quote(IF_QUESTION) + ".*\\R", "");
        Map<String, String> values = new java.util.HashMap<>(Map.of("READINESS", t.readiness(), "TITLE", String.valueOf(summary),
                "DESCRIPTION", String.valueOf(description), "PRECISION", String.valueOf(precision),
                "LANGUAGE", languageName(language)));
        if (question) {
            values.put("PREVIOUS_QUESTION", previousQuestion.strip());
        }
        return fill(template, values);
    }

    /**
     * Remplace les marqueurs du gabarit seulement (une valeur inseree n'est jamais relue : un marqueur dans un document
     * reste du texte). Un marqueur sans valeur est une erreur de programmation.
     */
    static String fill(String template, Map<String, String> values) {
        Matcher m = MARKER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            if (value == null) {
                throw new IllegalStateException("no value for prompt marker " + m.group(1));
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Huit blocs de code, dans l'ordre ; tout autre contenu du fichier est ignore. */
    static Texts parse(String markdown, String name) {
        List<String> blocks = new ArrayList<>();
        Matcher m = BLOCK.matcher(markdown);
        while (m.find()) {
            blocks.add(m.group(1));
        }
        if (blocks.size() != 8) {
            throw new IllegalStateException(name + ": expected 8 prompt blocks, found " + blocks.size());
        }
        return new Texts(blocks.get(0), blocks.get(1), blocks.get(2), blocks.get(3), blocks.get(4), blocks.get(5),
                blocks.get(6), blocks.get(7));
    }

    private static Texts load(String resource) {
        try (InputStream in = LlmPrompts.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("prompt resource not found: " + resource);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), resource);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read prompt resource " + resource, e);
        }
    }
}
