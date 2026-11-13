// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

/** Lit un document, le fait analyser et cree le backlog correspondant dans le gestionnaire de tickets. */
public interface AnalyzeDocument {

    /**
     * @param project projet cible ; null ou vide : projet par defaut (permet a un outil appelant de remplir
     *                un projet qu'il vient de creer)
     * @return le resultat de la creation (elements crees, avec la cle de leur ticket, et echecs)
     */
    BacklogCreationResult analyzeAndCreate(String documentId, String project);
}
