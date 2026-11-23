// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.PoQuestion;
import fr.heliocraft.refinr.domain.model.ReadinessSource;
import fr.heliocraft.refinr.domain.model.Story;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Questions au PO de la chaine (C1) : une par question distincte, tiree de {@code aiReadyReason} des stories non
 * pretes, sans appel au modele. Dedoublonnage exact seulement (espaces, casse et ponctuation finale ignores) : deux
 * questions differentes ne sont jamais fusionnees. Stories sans verdict du modele (repli) ou sans raison : ignorees,
 * leur raison n'est pas une question au PO.
 */
public final class PoQuestions {

    private PoQuestions() {}

    public static List<PoQuestion> from(List<Epic> epics) {
        Map<String, String> questions = new LinkedHashMap<>();
        Map<String, List<Story>> stories = new LinkedHashMap<>();
        if (epics == null) {
            return List.of();
        }
        for (Epic epic : epics) {
            if (epic == null) {
                continue;
            }
            for (Story story : epic.getStories()) {
                if (story == null || Boolean.TRUE.equals(story.getAiReady())
                        || story.getAiReadySource() == ReadinessSource.FALLBACK) {
                    continue;
                }
                String reason = story.getAiReadyReason();
                if (reason == null || reason.isBlank()) {
                    continue;
                }
                String key = normalized(reason);
                questions.putIfAbsent(key, reason.strip());
                stories.computeIfAbsent(key, k -> new ArrayList<>()).add(story);
            }
        }
        List<PoQuestion> result = new ArrayList<>();
        questions.forEach((key, question) -> result.add(new PoQuestion(question, stories.get(key))));
        return result;
    }

    /** Vrai si la raison est formulee comme une question (se termine par « ? », espaces finaux ignores). */
    public static boolean isQuestion(String reason) {
        return reason != null && reason.strip().endsWith("?");
    }

    static String normalized(String reason) {
        String collapsed = reason.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return collapsed.replaceAll("[\\s?!.…]+$", "");
    }
}
