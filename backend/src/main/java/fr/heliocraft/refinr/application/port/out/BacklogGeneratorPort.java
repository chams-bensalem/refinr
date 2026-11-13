// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

/** Modele de langage qui structure un document en Epics, Stories et sous-taches. */
public interface BacklogGeneratorPort {

    /**
     * Epics, stories et sous-taches du document. Chaque story porte le verdict de preparation produit par la meme
     * analyse ; il est remplace par celui de {@link ReadinessAssessorPort} quand un fournisseur de verdict distinct
     * est configure.
     *
     * @throws LlmException fournisseur indisponible, sortie invalide ou tronquee
     */
    List<Epic> generate(String documentContent);
}
