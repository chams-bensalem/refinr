// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;

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
        return userStory(story) + "\n\n" + acceptanceCriteria(story.getAcceptanceCriteria()) + readiness(story);
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

    private static String userStory(Story story) {
        StringBuilder sb = new StringBuilder();

        sb.append("**As a** ").append(story.getAsA()).append("\n");
        sb.append("**I want** ").append(story.getIWant()).append("\n");
        sb.append("**So that** ").append(story.getSoThat()).append("\n\n");

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
