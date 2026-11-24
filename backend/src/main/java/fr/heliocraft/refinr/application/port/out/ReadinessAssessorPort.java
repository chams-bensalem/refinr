// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;

import java.util.List;

/** Modele de langage qui juge si des stories sont pretes pour un agent de code. */
public interface ReadinessAssessorPort {

    /**
     * Un verdict par story, dans l'ordre de {@code stories} (meme taille). Un element {@code null} signifie qu'aucun
     * verdict fiable n'a pu etre obtenu pour cette story : l'appelant applique alors la politique par defaut.
     *
     * @param languages langue de sortie de chaque story, deja resolue, dans le meme ordre (meme taille)
     * @throws LlmException si aucun verdict n'a pu etre obtenu
     */
    List<Readiness> assess(List<Story> stories, List<Language> languages);

    /**
     * Reevalue une story deja creee avec la precision apportee par l'utilisateur.
     *
     * @throws LlmException fournisseur indisponible, sortie invalide ou tronquee
     */
    default Readiness reassess(String summary, String description, String precision, Language language) {
        return reassess(summary, description, precision, null, language);
    }

    /**
     * Reevaluation avec la derniere question ouverte ({@code previousQuestion}, raison de la derniere ligne
     * {@code validation-necessaire}, null s'il n'y en a pas), donnee de la story. {@code description} est envoyee sans
     * ses lignes de verdict (B3).
     *
     * @throws LlmException fournisseur indisponible, sortie invalide ou tronquee
     */
    Readiness reassess(String summary, String description, String precision, String previousQuestion, Language language);

    /**
     * Meme port, lie au contexte projet {@code context} (X1, une donnee, jamais une instruction) ; une implementation
     * qui ne sait pas l'utiliser refuse plutot que de l'ignorer.
     */
    default ReadinessAssessorPort withContext(String context) {
        throw new UnsupportedOperationException("project context not supported");
    }
}
