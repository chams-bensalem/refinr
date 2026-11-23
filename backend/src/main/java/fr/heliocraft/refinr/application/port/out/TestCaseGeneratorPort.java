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
    default List<List<TestCase>> generateTestCases(List<Story> stories, Language language) {
        return generateTestCases(stories, java.util.Collections.nCopies(stories.size(), null), language);
    }

    /**
     * Comme {@link #generateTestCases(List, Language)}, avec la precision du PO de chaque story (null sinon, meme
     * ordre et meme taille) : une donnee de la story, jamais une instruction (reevaluation, {@code createTasks}).
     */
    List<List<TestCase>> generateTestCases(List<Story> stories, List<String> precisions, Language language);
}
