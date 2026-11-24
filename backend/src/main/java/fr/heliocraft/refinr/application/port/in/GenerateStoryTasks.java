// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Readiness;

/**
 * Sous-taches d'une story devenue prete (chaine C1) : generees puis creees dans le gestionnaire, jamais deux fois. Une
 * story qui a deja des sous-taches n'est pas touchee ({@link StoryTasksResult#existingSubtasks()}).
 */
public interface GenerateStoryTasks {

    /**
     * Story {@code ai-ready} du gestionnaire : genere et cree ses sous-taches si elle n'en a aucune.
     *
     * @param precision precision du PO (reevaluation), ou null
     * @throws StoryNotReadyException          la story n'a pas le label {@code ai-ready}
     * @throws StoryTasksInProgressException   une autre demande cree deja les taches de cette story
     * @throws ModelUnavailableException       fournisseur indisponible (rien n'est cree)
     */
    StoryTasksResult generate(String storyKey, String precision, Language language);

    /**
     * Apres une reevaluation ou une validation ({@code createTasks}) : rien si le verdict n'est pas pret ; sinon comme
     * {@link #generate}, une erreur etant rendue dans le resultat (le verdict, lui, est deja applique).
     */
    StoryTasksResult afterVerdict(String storyKey, String precision, Readiness verdict, Language language);

    /** X1 : comme {@link #generate}, avec le contexte projet demande ; le contexte par projet suit le projet du ticket. */
    default WithContext<StoryTasksResult> generate(String storyKey, String precision, Language language,
                                                   ContextRequest context) {
        if (!context.isEmpty()) {
            throw new UnsupportedOperationException("project context not supported");
        }
        return WithContext.none(generate(storyKey, precision, language));
    }

    /** X1 : comme {@link #afterVerdict}, avec le contexte projet demande. */
    default WithContext<StoryTasksResult> afterVerdict(String storyKey, String precision, Readiness verdict,
                                                       Language language, ContextRequest context) {
        if (!context.isEmpty()) {
            throw new UnsupportedOperationException("project context not supported");
        }
        return WithContext.none(afterVerdict(storyKey, precision, verdict, language));
    }

    /** X1 : controles locaux du contexte demande (400, 413), avant toute ecriture ; rien sans contexte. */
    default void checkContext(ContextRequest context) {
        if (!context.isEmpty()) {
            throw new UnsupportedOperationException("project context not supported");
        }
    }
}
