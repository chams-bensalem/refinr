// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

/** Lit un document, le fait analyser et cree le backlog correspondant dans le gestionnaire de tickets. */
public interface AnalyzeDocument {

    /**
     * Document lu dans la source active par son identifiant.
     *
     * @param project projet cible ; null ou vide : projet par defaut (permet a un outil appelant de remplir
     *                un projet qu'il vient de creer)
     * @return le resultat de la creation (elements crees, avec la cle de leur ticket, et echecs)
     */
    BacklogCreationResult analyzeAndCreate(String documentId, String project);

    /**
     * Document fourni en texte (Markdown ou texte brut), sans appel a la source active ; meme suite ensuite.
     *
     * @param title   titre optionnel (null ou vide : aucun), donne au modele en tete du texte
     * @param project projet cible ; null ou vide : projet par defaut
     */
    BacklogCreationResult analyzeTextAndCreate(String title, String content, String project);
}
