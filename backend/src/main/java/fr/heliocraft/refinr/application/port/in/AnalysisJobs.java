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

    /** Apercu d'un document de la source active, avertissements du projet par defaut. */
    default JobView submitPreview(String documentId, Language language) {
        return submitPreview(documentId, null, language);
    }

    /**
     * Apercu d'un document de la source active ; {@code project} (null ou vide : projet par defaut) : projet dont le
     * niveau Feature donne les avertissements de l'apercu.
     */
    default JobView submitPreview(String documentId, String project, Language language) {
        return submitPreview(documentId, project, language, false);
    }

    /** {@code pipeline} : chaine C1 ({@code "pipeline": true}) ; false : comportement par defaut. */
    JobView submitPreview(String documentId, String project, Language language, boolean pipeline);

    /** Apercu d'un document fourni en texte, avertissements du projet par defaut. */
    default JobView submitPreviewText(String title, String content, Language language) {
        return submitPreviewText(title, content, null, language);
    }

    /** Apercu d'un document fourni en texte ; {@code project} : comme {@link #submitPreview(String, String, Language)}. */
    default JobView submitPreviewText(String title, String content, String project, Language language) {
        return submitPreviewText(title, content, project, language, false);
    }

    /** {@code pipeline} : chaine C1 ({@code "pipeline": true}) ; false : comportement par defaut. */
    JobView submitPreviewText(String title, String content, String project, Language language, boolean pipeline);

    /** Analyse et creation d'un document de la source active ; {@code project} null ou vide : projet par defaut. */
    default JobView submitAnalyzeAndCreate(String documentId, String project, Language language) {
        return submitAnalyzeAndCreate(documentId, project, language, false);
    }

    /** {@code pipeline} : chaine C1 ({@code "pipeline": true}) ; false : comportement par defaut. */
    JobView submitAnalyzeAndCreate(String documentId, String project, Language language, boolean pipeline);

    /** Analyse et creation d'un document fourni en texte. */
    default JobView submitAnalyzeTextAndCreate(String title, String content, String project, Language language) {
        return submitAnalyzeTextAndCreate(title, content, project, language, false);
    }

    /** {@code pipeline} : chaine C1 ({@code "pipeline": true}) ; false : comportement par defaut. */
    JobView submitAnalyzeTextAndCreate(String title, String content, String project, Language language,
                                       boolean pipeline);

    /**
     * X1 : soumissions avec le contexte projet demande ; controles locaux du contexte (400, 413) a la soumission, page de
     * contexte lue pendant le job. Sans contexte demande : comme les soumissions sans contexte.
     */
    default JobView submitPreview(String documentId, String project, Language language, boolean pipeline,
                                  ContextRequest context) {
        if (!context.isEmpty()) {
            throw new UnsupportedOperationException("project context not supported");
        }
        return submitPreview(documentId, project, language, pipeline);
    }

    default JobView submitPreviewText(String title, String content, String project, Language language, boolean pipeline,
                                      ContextRequest context) {
        if (!context.isEmpty()) {
            throw new UnsupportedOperationException("project context not supported");
        }
        return submitPreviewText(title, content, project, language, pipeline);
    }

    default JobView submitAnalyzeAndCreate(String documentId, String project, Language language, boolean pipeline,
                                           ContextRequest context) {
        if (!context.isEmpty()) {
            throw new UnsupportedOperationException("project context not supported");
        }
        return submitAnalyzeAndCreate(documentId, project, language, pipeline);
    }

    default JobView submitAnalyzeTextAndCreate(String title, String content, String project, Language language,
                                               boolean pipeline, ContextRequest context) {
        if (!context.isEmpty()) {
            throw new UnsupportedOperationException("project context not supported");
        }
        return submitAnalyzeTextAndCreate(title, content, project, language, pipeline);
    }

    /** Etat du job, vide s'il est inconnu ou expire. */
    Optional<JobView> find(String id);

    CancelResult cancel(String id);
}
