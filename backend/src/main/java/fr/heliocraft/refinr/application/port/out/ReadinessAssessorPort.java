// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;

import java.util.List;

/** Modele de langage qui juge si des stories sont pretes pour un agent de code. */
public interface ReadinessAssessorPort {

    /**
     * Un verdict par story, dans l'ordre de {@code stories} (meme taille). Un element {@code null} signifie qu'aucun
     * verdict fiable n'a pu etre obtenu pour cette story : l'appelant applique alors la politique par defaut.
     *
     * @throws LlmException si aucun verdict n'a pu etre obtenu
     */
    List<Readiness> assess(List<Story> stories);

    /**
     * Reevalue une story deja creee avec la precision apportee par l'utilisateur.
     *
     * @throws LlmException fournisseur indisponible, sortie invalide ou tronquee
     */
    Readiness reassess(String summary, String description, String precision);
}
