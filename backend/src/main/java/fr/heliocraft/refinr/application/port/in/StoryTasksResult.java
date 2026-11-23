// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.application.port.in.BacklogCreationResult.CreatedIssue;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult.FailedIssue;

import java.util.List;

/**
 * Resultat de la creation des sous-taches d'une story.
 *
 * @param existingSubtasks nombre de sous-taches deja presentes (rien n'a ete genere ni cree), sinon null
 * @param notReady         verdict non pret apres une reevaluation : rien n'a ete fait
 * @param error            message d'une erreur du modele ou du gestionnaire apres le verdict ({@code afterVerdict}),
 *                         sans contenu ; sinon null
 * @param testCases        cas de test ecrits dans la description (C2), ou null s'ils ne l'ont pas ete
 * @param testCasesSkipped la description avait deja une section de cas de test : rien n'a ete ecrit
 * @param testCasesError   raison pour laquelle les cas de test n'ont pas ete ecrits (sans contenu), sinon null
 */
public record StoryTasksResult(List<CreatedIssue> created, List<FailedIssue> failed, Integer existingSubtasks,
                               boolean notReady, String error, Integer testCases, boolean testCasesSkipped,
                               String testCasesError) {

    public StoryTasksResult {
        created = created != null ? List.copyOf(created) : List.of();
        failed = failed != null ? List.copyOf(failed) : List.of();
    }

    public StoryTasksResult(List<CreatedIssue> created, List<FailedIssue> failed, Integer existingSubtasks,
                            boolean notReady, String error) {
        this(created, failed, existingSubtasks, notReady, error, null, false, null);
    }

    /** Meme resultat de sous-taches, avec celui des cas de test. */
    public StoryTasksResult withTestCases(Integer written, boolean skipped, String testCaseError) {
        return new StoryTasksResult(created, failed, existingSubtasks, notReady, error, written, skipped, testCaseError);
    }

    public static StoryTasksResult skipped(int existingSubtasks) {
        return new StoryTasksResult(List.of(), List.of(), existingSubtasks, false, null);
    }

    public static StoryTasksResult notReadyYet() {
        return new StoryTasksResult(List.of(), List.of(), null, true, null);
    }

    public static StoryTasksResult failure(String error) {
        return new StoryTasksResult(List.of(), List.of(), null, false, error);
    }

    /** Message de {@code tasksSkipped} : un utilisateur sait qu'une creation precedente a pu etre incomplete. */
    public String skippedMessage() {
        return existingSubtasks == null ? null
                : "the story already has " + existingSubtasks + " sub-task" + (existingSubtasks == 1 ? "" : "s");
    }
}
