// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.ReadinessSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Regles du verdict de preparation : labels reserves (destines a alimenter un agent de code via
 * {@code labels = "ai-ready"}) et verdict par defaut (dans le doute, validation humaine).
 */
public final class ReadinessPolicy {

    public static final String LABEL_AI_READY = "ai-ready";
    public static final String LABEL_VALIDATION = "validation-necessaire";

    private ReadinessPolicy() {}

    /** Seul un true explicite rend la story codable par l'agent : absent, null ou autre valeur -> validation humaine. */
    public static boolean isAiReady(Object verdict) {
        return Boolean.TRUE.equals(verdict);
    }

    /** Label de verdict correspondant (ai-ready ou validation-necessaire). */
    public static String verdictLabel(Object verdict) {
        return isAiReady(verdict) ? LABEL_AI_READY : LABEL_VALIDATION;
    }

    /** Labels reserves au verdict : jamais repris tels quels de l'analyse ni d'un ticket existant. */
    public static boolean isReservedLabel(String label) {
        return LABEL_AI_READY.equals(label) || LABEL_VALIDATION.equals(label);
    }

    /** Une story n'est reevaluee que si elle est en attente de validation humaine. */
    public static boolean isAwaitingValidation(Collection<String> labels) {
        return labels.contains(LABEL_VALIDATION);
    }

    /** Remplace le label de verdict, en gardant les autres labels dans leur ordre. */
    public static List<String> replaceVerdictLabel(Collection<String> labels, boolean aiReady) {
        List<String> result = new ArrayList<>(labels.stream().filter(label -> !isReservedLabel(label)).toList());
        result.add(verdictLabel(aiReady));
        return result;
    }

    /** Verdict lu dans la reponse d'un modele : seul un true explicite compte, raison vide si absente ou non textuelle. */
    public static Readiness fromModelAnswer(Object aiReady, Object reason) {
        return new Readiness(isAiReady(aiReady), reason instanceof String text ? text : "");
    }

    /** Verdict par defaut quand aucun verdict fiable n'a pu etre obtenu : jamais ai-ready. */
    public static Readiness unavailable(String cause) {
        // Texte fige jusqu'en v2.0 : le plugin 0.1.0 le reconnait par une regex exacte
        return new Readiness(false, "verdict indisponible (" + cause + ") : validation humaine necessaire",
                ReadinessSource.FALLBACK);
    }

    /** Raison du verdict d'une validation humaine directe (ecrite dans le ticket), en francais. */
    public static final String HUMAN_VALIDATION_REASON = "validée par un humain, sans appel au modèle";
    /** La meme raison en anglais (toute autre langue que le francais, comme les prompts). */
    public static final String HUMAN_VALIDATION_REASON_EN = "validated by a human, without a model call";

    /** Verdict d'une validation humaine directe : ai-ready, sans appel au modele ; raison en francais. */
    public static Readiness humanValidation() {
        return humanValidation(Language.FR);
    }

    /** Verdict d'une validation humaine directe, raison dans {@code language} (francais, sinon anglais). */
    public static Readiness humanValidation(Language language) {
        boolean french = language == null || language.isFrench();
        return new Readiness(true, french ? HUMAN_VALIDATION_REASON : HUMAN_VALIDATION_REASON_EN, ReadinessSource.HUMAN);
    }
}
