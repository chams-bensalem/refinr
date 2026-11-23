// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verrous par story, partages par la creation du backlog et {@code story-tasks} : les sous-taches d'une story ne sont
 * creees que par un seul chemin a la fois. Cle : celle que rend le gestionnaire (forme canonique). Dans ce processus
 * seulement (une seule instance de Refinr, comme les jobs).
 */
public final class StoryLocks {

    private final Set<String> held = ConcurrentHashMap.newKeySet();

    /** Vrai si le verrou est pris ; faux s'il est deja tenu. */
    boolean tryLock(String storyKey) {
        return held.add(storyKey);
    }

    void unlock(String storyKey) {
        held.remove(storyKey);
    }
}
