// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

import java.time.Instant;
import java.util.Optional;

/**
 * Stockage des jobs d'analyse : etats successifs d'un job, jusqu'a son expiration (duree de vie comptee depuis la fin
 * du job, fixee par l'adaptateur). Ne stocke jamais le contenu d'un document. Generique : l'adaptateur ne connait du
 * job que son identifiant et sa fin.
 */
public interface JobStorePort<J extends JobStorePort.Entry> {

    /** Ce que le stockage connait d'un job. */
    interface Entry {
        String id();

        boolean isFinished();

        /** Fin du job ; null tant qu'il n'est pas termine. */
        Instant finishedAt();
    }

    /**
     * Enregistre l'etat d'un job (nouveau, ou remplacement de l'etat precedent).
     *
     * @return false si c'est un nouveau job et que le stockage est plein sans job termine a evincer
     */
    boolean save(J job);

    /** Etat du job, vide s'il est inconnu ou expire. */
    Optional<J> find(String id);

    void remove(String id);

    /** Retire les jobs termines dont la duree de vie est ecoulee. */
    void evictExpired();
}
