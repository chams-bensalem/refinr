// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.TestCase;

import java.util.List;

/** Modele de langage qui produit les cas de test de stories pretes (chaine C2, prompt 5). */
public interface TestCaseGeneratorPort {

    /**
     * Cas de test de chaque story, dans l'ordre de {@code stories} (meme taille) ; {@code null} : pas de reponse fiable
     * pour cette story.
     *
     * @throws LlmException fournisseur indisponible, ou aucune reponse exploitable
     */
    List<List<TestCase>> generateTestCases(List<Story> stories, Language language);
}
