// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

/**
 * La source documentaire n'a pas pu repondre : injoignable, en erreur, identifiants ou droits du compte refuses, delai
 * depasse (apres les nouveaux essais prevus). Repondu 502 au client, avec le message sur ecrit par l'adaptateur.
 */
public class DocumentSourceUnavailableException extends RuntimeException {

    public DocumentSourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
