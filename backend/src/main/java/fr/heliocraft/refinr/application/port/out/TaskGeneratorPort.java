// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;

import java.util.List;

/** Modele de langage qui produit les sous-taches techniques de stories pretes (chaine C1, prompt 4). */
public interface TaskGeneratorPort {

    /**
     * Sous-taches de chaque story, dans l'ordre de {@code stories} (meme taille). Un element {@code null} signifie
     * qu'aucune reponse fiable n'a pu etre obtenue pour cette story.
     *
     * @param precisions precision du PO pour chaque story (null s'il n'y en a pas), meme ordre et meme taille
     * @param language   langue de sortie, deja resolue (requete, propriete, detection)
     * @throws LlmException fournisseur indisponible, ou aucune reponse exploitable
     */
    List<List<Task>> generateTasks(List<Story> stories, List<String> precisions, Language language);
}
