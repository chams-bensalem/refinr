// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

import java.util.List;

/**
 * Story relue dans le gestionnaire de tickets pour une reevaluation : description en texte brut (pour l'analyse),
 * labels actuels et contenu natif d'origine (conserve tel quel a la reecriture ; null si absent).
 */
public record TrackedStory(String key, String summary, String description, List<String> labels,
                           TrackerContent originalContent) {

    public TrackedStory(String key, String summary, String description, List<String> labels) {
        this(key, summary, description, labels, null);
    }
}
