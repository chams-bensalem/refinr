// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

import java.util.List;

/**
 * Story relue dans le gestionnaire de tickets (reevaluation, validation, taches) : description en texte brut (pour
 * l'analyse), labels actuels, contenu natif d'origine (conserve tel quel a la reecriture ; null si absent), projet du
 * ticket (null s'il n'est pas connu : projet par defaut) et cles de ses sous-taches existantes.
 */
public record TrackedStory(String key, String summary, String description, List<String> labels,
                           TrackerContent originalContent, String project, List<String> subtaskKeys) {

    public TrackedStory {
        labels = labels != null ? List.copyOf(labels) : List.of();
        subtaskKeys = subtaskKeys != null ? List.copyOf(subtaskKeys) : List.of();
    }

    public TrackedStory(String key, String summary, String description, List<String> labels,
                        TrackerContent originalContent) {
        this(key, summary, description, labels, originalContent, null, List.of());
    }

    public TrackedStory(String key, String summary, String description, List<String> labels) {
        this(key, summary, description, labels, null);
    }
}
