// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.service.StoryMarkdown;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mesure R3 (hors tests automatiques) : le prompt 3 en trois versions sur le meme code, pour les comparer sur le meme
 * jeu. {@code v3.2} : texte fige de verdict-v3.3 sans ligne de question, description complete (lignes de verdict
 * comprises) ; {@code v3.3} : texte fige, lignes de verdict retirees, derniere question ouverte ; {@code v3.4} : prompt
 * de production. N'expose que le verdict, jamais la reponse brute.
 */
public final class PromptThreeVariants {

    public static final List<String> VERSIONS = List.of("v3.2", "v3.3", "v3.4");
    private static final Pattern BLOCK = Pattern.compile("```\\n(.*?)\\n```", Pattern.DOTALL);

    private PromptThreeVariants() {}

    /** Reevaluation de la story ({@code fullDescription} : telle qu'ecrite dans le ticket, ligne de verdict comprise). */
    public static Readiness reassess(String version, LlmClient client, StructuredOutput output, String title,
                                     String fullDescription, String precision, Language language) {
        String stripped = StoryMarkdown.forModel(fullDescription);
        String question = StoryMarkdown.lastOpenQuestion(fullDescription);
        String prompt = switch (version) {
            case "v3.2" -> frozen(language, title, fullDescription, precision, null);
            case "v3.3" -> frozen(language, title, stripped, precision, question);
            case "v3.4" -> LlmPrompts.reassessment(title, stripped, precision, question, language);
            default -> throw new IllegalArgumentException("--versions accepts " + VERSIONS);
        };
        JsonNode schema = LlmSchemas.load(LlmSchemas.READINESS);
        LlmRequest request = LlmRequest.of(LlmPrompts.system(language), prompt, LlmSchemas.READINESS,
                LlmSchemas.forProvider(schema));
        return output.call(client, request, schema, LlmReadinessAssessor::readiness);
    }

    /** Texte fige de verdict-v3.3 (ressource de test), rempli comme le fait {@link LlmPrompts#reassessment}. */
    static String frozen(Language language, String title, String description, String precision, String question) {
        String template = template(language.isFrench() ? "fr" : "en");
        boolean withQuestion = question != null && !question.isBlank();
        template = withQuestion ? template.replace(LlmPrompts.IF_QUESTION, "")
                : template.replaceAll("(?m)^" + Pattern.quote(LlmPrompts.IF_QUESTION) + ".*\\R", "");
        Map<String, String> values = new HashMap<>(Map.of("READINESS", LlmPrompts.readiness(language), "TITLE", title,
                "DESCRIPTION", description, "PRECISION", precision, "LANGUAGE", LlmPrompts.languageName(language)));
        if (withQuestion) {
            values.put("PREVIOUS_QUESTION", question.strip());
        }
        return LlmPrompts.fill(template, values);
    }

    private static String template(String lang) {
        String resource = "eval/prompt3-verdict-v3.3." + lang + ".md";
        try (InputStream in = PromptThreeVariants.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing " + resource);
            }
            Matcher m = BLOCK.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            if (!m.find()) {
                throw new IllegalStateException("no prompt block in " + resource);
            }
            return m.group(1);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
