// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

/**
 * Echec d'une operation du gestionnaire de tickets. Le message est sur et utile au client (statut HTTP, erreurs de
 * validation renvoyees par l'outil) : il ne contient ni URL, ni identifiant, ni secret.
 */
public class IssueTrackerException extends RuntimeException {

    public IssueTrackerException(String message) {
        super(message);
    }

    public IssueTrackerException(String message, Throwable cause) {
        super(message, cause);
    }
}
