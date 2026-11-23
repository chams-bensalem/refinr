// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

/** Modele de langage qui structure un document en Epics, Stories et sous-taches. */
public interface BacklogGeneratorPort {

    /**
     * Epics, stories et sous-taches du document. Chaque story porte le verdict de preparation produit par la meme
     * analyse ; il est remplace par celui de {@link ReadinessAssessorPort} quand un fournisseur de verdict distinct
     * est configure.
     *
     * @param language langue de sortie, deja resolue (requete, propriete, detection)
     * @throws LlmException fournisseur indisponible, sortie invalide ou tronquee
     */
    List<Epic> generate(String documentContent, Language language);

    /**
     * Chaine C1 : epics, features, stories et criteres, sans sous-taches (prompt {@code generation-v3}) ; les
     * sous-taches des stories pretes viennent ensuite de {@link TaskGeneratorPort}. Par defaut : {@link #generate}
     * dont les sous-taches sont retirees.
     */
    default List<Epic> generateStories(String documentContent, Language language) {
        List<Epic> epics = generate(documentContent, language);
        epics.forEach(epic -> epic.getStories().forEach(story -> story.setTasks(new java.util.ArrayList<>())));
        return epics;
    }
}
