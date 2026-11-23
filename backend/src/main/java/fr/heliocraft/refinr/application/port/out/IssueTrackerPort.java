// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Feature;
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

    /** Adresse web du ticket {@code key} pour un navigateur (ex. lien de l'interface), ou null si inconnue. */
    String issueUrl(String key);

    /** Niveau Feature du gestionnaire dans ce projet, entre l'epic et la story. */
    enum FeatureLevel {
        /** Pas de niveau Feature : les stories vont sous l'epic (les Features deviennent des labels). */
        NONE,
        /** Niveau Feature natif ; une story peut aussi aller directement sous l'epic (Feature implicite sans ticket). */
        OPTIONAL,
        /** Niveau Feature natif et obligatoire : le parent d'une story est toujours une Feature. */
        REQUIRED,
        /** Niveau inconnu (gestionnaire injoignable ou mal configure) : traite comme {@link #NONE}, sans avertissement. */
        UNKNOWN;

        public boolean isNative() {
            return this == OPTIONAL || this == REQUIRED;
        }
    }

    /** Niveau Feature dans {@code project} (null ou vide : projet par defaut) ; aucun par defaut. Lu a la creation. */
    default FeatureLevel featureLevel(String project) {
        return FeatureLevel.NONE;
    }

    /**
     * Niveau Feature lu pendant l'apercu (avertissements seulement) : par defaut {@link #featureLevel} ; un adaptateur
     * peut le lire avec le seul droit de lecture. Peut lever {@link TrackerPermissionException} quand le compte n'a
     * pas le droit necessaire, pour que l'avertissement le dise.
     */
    default FeatureLevel previewFeatureLevel(String project) {
        return featureLevel(project);
    }

    String createEpic(Epic epic, String project);

    /** Cree une Feature sous l'epic ; appele seulement quand {@link #featureLevel} est natif. */
    default String createFeature(Feature feature, String epicKey, String project) {
        throw new IssueTrackerException(displayName() + " has no Feature level");
    }

    /** @param parentKey cle de la Feature, ou de l'epic quand la story n'a pas de Feature dans le gestionnaire */
    String createStory(Story story, String parentKey, String project);

    String createSubtask(Task task, String storyKey, String project);

    /** Relit une story existante pour la reevaluer. */
    TrackedStory findStory(String key);

    /** Ajoute la precision et le nouveau verdict a la story, et remplace son label de verdict. */
    void applyReassessment(TrackedStory story, String precision, Readiness verdict);

    /**
     * Validation humaine directe : ajoute la ligne du verdict (sans precision) et remplace le label de verdict par
     * {@code ai-ready}, comme une reevaluation.
     */
    void applyHumanValidation(TrackedStory story, Readiness verdict);
}
