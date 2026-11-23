// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

/**
 * Le compte de Refinr n'a pas le droit necessaire dans le projet (ex. creer des work packages). Le message est une
 * phrase destinee a l'utilisateur, montree telle quelle comme avertissement de l'apercu : jamais de corps de reponse,
 * d'URL ni de secret, seulement l'identifiant du projet (deja valide).
 */
public class TrackerPermissionException extends IssueTrackerException {

    public TrackerPermissionException(String message) {
        super(message);
    }
}
