// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort.FeatureLevel;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Feature;
import fr.heliocraft.refinr.domain.service.LabelNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/**
 * Regles communes du niveau Feature : gestionnaire sans niveau Feature (label ou refus), avertissements de l'apercu et
 * de la creation, label d'une Feature sur ses stories.
 */
public final class FeatureLevels {

    /** Propriete de la politique, et ses valeurs. */
    public static final String PROPERTY = "refinr.features.without-level";

    /** Gestionnaire sans niveau Feature dans le projet ({@code refinr.features.without-level}). */
    public enum Policy {
        /** Les Features explicites deviennent un label {@code feature-<nom>} sur leurs stories (defaut). */
        LABEL,
        /** La creation est refusee (422) avant tout appel au gestionnaire. */
        REFUSE;

        public static Policy of(String value) {
            String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "", "label" -> LABEL;
                case "refuse" -> REFUSE;
                default -> throw new IllegalArgumentException(PROPERTY + " must be label or refuse, got " + value);
            };
        }
    }

    static final String LABELS_WARNING = "The tracker has no Feature level in this project: features are kept as "
            + "labels (feature-…) on their stories";
    static final String REFUSE_WARNING = "The tracker has no Feature level in this project: creating these issues "
            + "will be refused (" + PROPERTY + "=refuse)";
    static final String LABEL_PREFIX = "feature ";

    private static final Logger log = LoggerFactory.getLogger(FeatureLevels.class);

    private FeatureLevels() {}

    /** Vrai si un epic au moins a plusieurs Features (des Features explicites, pas seulement des Features implicites). */
    static boolean hasExplicitFeatures(List<Epic> epics) {
        return epics != null && epics.stream()
                .anyMatch(epic -> epic != null && epic.getFeatures() != null && epic.getFeatures().size() > 1);
    }

    /** Niveau du gestionnaire ; une erreur (gestionnaire injoignable, mal configure) donne {@code UNKNOWN}. */
    static FeatureLevel levelOf(IssueTrackerPort tracker, String project) {
        try {
            FeatureLevel level = tracker.featureLevel(project);
            return level != null ? level : FeatureLevel.UNKNOWN;
        } catch (RuntimeException e) {
            log.warn("Feature level of the tracker unknown: {}", e.getClass().getSimpleName());
            return FeatureLevel.UNKNOWN;
        }
    }

    /** Avertissement de l'apercu ou de la creation : seulement si des Features explicites n'auront pas de niveau. */
    static List<String> warnings(List<Epic> epics, FeatureLevel level, Policy policy) {
        if (level.isNative() || level == FeatureLevel.UNKNOWN || !hasExplicitFeatures(epics)) {
            return List.of();
        }
        return List.of(policy == Policy.REFUSE ? REFUSE_WARNING : LABELS_WARNING);
    }

    /** Label {@code feature-<nom normalise>} d'une Feature explicite, ou null si elle n'a pas de nom utilisable. */
    static String label(Feature feature) {
        if (feature.getName() == null || feature.getName().isBlank()) {
            return null;
        }
        // Accents retires d'abord (« Réservation » -> reservation) : LabelNormalizer supprime les lettres accentuees
        String unaccented = java.text.Normalizer.normalize(feature.getName().strip(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        List<String> normalized = LabelNormalizer.normalize(List.of(LABEL_PREFIX + unaccented));
        return normalized.isEmpty() || normalized.getFirst().equals("feature-") ? null : normalized.getFirst();
    }
}
