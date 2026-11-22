// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

/** Gestionnaire de tickets actif (un seul par instance, choisi par configuration) : nom et format des projets. */
public interface DescribeTracker {

    /** Nom affichable du gestionnaire de tickets actif. */
    String displayName();

    /** Vrai si {@code projectId} a le format d'un projet du gestionnaire actif (null ou vide : faux). */
    boolean acceptsProjectId(String projectId);

    /**
     * Avertissements de l'apercu sur le niveau Feature : des Features explicites alors que le gestionnaire n'a pas de
     * niveau Feature dans {@code projectId} (null ou vide : projet par defaut) ; vide sinon, ou si le niveau est inconnu.
     */
    default List<String> featureWarnings(List<Epic> epics, String projectId) {
        return List.of();
    }
}
