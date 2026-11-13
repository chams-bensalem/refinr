// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import fr.heliocraft.refinr.domain.model.TrackedStory;

/**
 * Gestionnaire de tickets, agnostique de l'outil (Jira aujourd'hui, OpenProject en v1.1) ; un seul adaptateur actif
 * par instance ({@code tracker.provider}). Les methodes de creation
 * renvoient la cle du ticket cree sans modifier le modele : l'appelant l'affecte.
 * {@code project} null ou vide : projet par defaut de l'adaptateur.
 */
public interface IssueTrackerPort {

    /** Nom affichable de l'outil (ex. dans l'interface). */
    String displayName();

    /** Vrai si {@code project} a le format d'un identifiant de projet de l'outil (cle, identifiant ou id). */
    boolean acceptsProjectId(String project);

    String createEpic(Epic epic, String project);

    String createStory(Story story, String epicKey, String project);

    String createSubtask(Task task, String storyKey, String project);

    /** Relit une story existante pour la reevaluer. */
    TrackedStory findStory(String key);

    /** Ajoute la precision et le nouveau verdict a la story, et remplace son label de verdict. */
    void applyReassessment(TrackedStory story, String precision, Readiness verdict);
}
