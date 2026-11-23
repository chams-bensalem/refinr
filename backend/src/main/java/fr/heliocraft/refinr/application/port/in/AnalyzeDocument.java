// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Language;

/** Lit un document, le fait analyser et cree le backlog correspondant dans le gestionnaire de tickets. */
public interface AnalyzeDocument {

    /**
     * Document lu dans la source active par son identifiant.
     *
     * @param project projet cible ; null ou vide : projet par defaut (permet a un outil appelant de remplir
     *                un projet qu'il vient de creer)
     * @return le resultat de la creation (elements crees, avec la cle de leur ticket, et echecs)
     */
    default BacklogCreationResult analyzeAndCreate(String documentId, String project) {
        return analyzeAndCreate(documentId, project, null);
    }

    /** @param language langue de sortie demandee, ou null : propriete, puis detection */
    BacklogCreationResult analyzeAndCreate(String documentId, String project, Language language);

    /**
     * Document fourni en texte (Markdown ou texte brut), sans appel a la source active ; meme suite ensuite.
     *
     * @param title   titre optionnel (null ou vide : aucun), donne au modele en tete du texte
     * @param project projet cible ; null ou vide : projet par defaut
     */
    default BacklogCreationResult analyzeTextAndCreate(String title, String content, String project) {
        return analyzeTextAndCreate(title, content, project, null);
    }

    /** @param language langue de sortie demandee, ou null : propriete, puis detection */
    BacklogCreationResult analyzeTextAndCreate(String title, String content, String project, Language language);

    /** Meme analyse, avec le suivi des etapes (jobs) ; sans suivi par defaut. */
    default BacklogCreationResult analyzeAndCreate(String documentId, String project, Language language,
                                                   AnalysisProgress progress) {
        return analyzeAndCreate(documentId, project, language);
    }

    /** Meme analyse d'un texte fourni, avec le suivi des etapes (jobs) ; sans suivi par defaut. */
    default BacklogCreationResult analyzeTextAndCreate(String title, String content, String project, Language language,
                                                       AnalysisProgress progress) {
        return analyzeTextAndCreate(title, content, project, language);
    }

    /**
     * {@code pipeline} : chaine C1 (generation sans sous-taches, verdict, sous-taches des stories pretes, creation) ;
     * false : comportement par defaut, inchange.
     */
    default BacklogCreationResult analyzeAndCreate(String documentId, String project, Language language, boolean pipeline,
                                                   AnalysisProgress progress) {
        if (pipeline) {
            throw new UnsupportedOperationException("pipeline not supported");
        }
        return analyzeAndCreate(documentId, project, language, progress);
    }

    /** Comme {@link #analyzeAndCreate(String, String, Language, boolean, AnalysisProgress)}, pour un texte fourni. */
    default BacklogCreationResult analyzeTextAndCreate(String title, String content, String project, Language language,
                                                       boolean pipeline, AnalysisProgress progress) {
        if (pipeline) {
            throw new UnsupportedOperationException("pipeline not supported");
        }
        return analyzeTextAndCreate(title, content, project, language, progress);
    }

    /**
     * X1 : meme analyse avec le contexte projet demande ({@code context}) ; le contexte par projet suit {@code project}.
     * Sans contexte demande ni configure : exactement l'analyse sans contexte.
     */
    default BacklogCreationResult analyzeAndCreate(String documentId, String project, Language language, boolean pipeline,
                                                   ContextRequest context, AnalysisProgress progress) {
        if (!context.isEmpty()) {
            throw new UnsupportedOperationException("project context not supported");
        }
        return analyzeAndCreate(documentId, project, language, pipeline, progress);
    }

    /** Comme {@link #analyzeAndCreate(String, String, Language, boolean, ContextRequest, AnalysisProgress)}, pour un texte. */
    default BacklogCreationResult analyzeTextAndCreate(String title, String content, String project, Language language,
                                                       boolean pipeline, ContextRequest context, AnalysisProgress progress) {
        if (!context.isEmpty()) {
            throw new UnsupportedOperationException("project context not supported");
        }
        return analyzeTextAndCreate(title, content, project, language, pipeline, progress);
    }
}
