// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Language;

import java.util.Optional;

/**
 * Analyses executees en arriere-plan (jobs) : soumission, consultation et annulation. Une soumission controle la
 * requete comme l'endpoint synchrone ({@link InvalidRequestException}, {@link ContentTooLargeException}) avant de
 * creer le job ; la lecture du document et les appels a l'IA et au gestionnaire de tickets ont lieu dans le job.
 */
public interface AnalysisJobs {

    /** Resultat d'une annulation. */
    enum CancelResult {
        /** Job en attente (il ne s'executera pas) ou termine : efface. */
        REMOVED,
        /** Job en cours : rien n'est fait. */
        RUNNING,
        /** Job inconnu ou expire. */
        NOT_FOUND
    }

    /** Soumission refusee : file d'attente pleine, ou arret du serveur en cours. */
    final class RejectedException extends RuntimeException {
        public RejectedException(String message) {
            super(message);
        }
    }

    /** Apercu d'un document de la source active. */
    JobView submitPreview(String documentId, Language language);

    /** Apercu d'un document fourni en texte. */
    JobView submitPreviewText(String title, String content, Language language);

    /** Analyse et creation d'un document de la source active ; {@code project} null ou vide : projet par defaut. */
    JobView submitAnalyzeAndCreate(String documentId, String project, Language language);

    /** Analyse et creation d'un document fourni en texte. */
    JobView submitAnalyzeTextAndCreate(String title, String content, String project, Language language);

    /** Etat du job, vide s'il est inconnu ou expire. */
    Optional<JobView> find(String id);

    CancelResult cancel(String id);
}
