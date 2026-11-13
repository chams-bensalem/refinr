// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

/**
 * Le document demande est absent de la source, ou non visible pour le compte de Refinr. Repondu 404 au client, avec le
 * message sur ecrit par l'adaptateur (outil et identifiant, ni URL ni corps brut).
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
