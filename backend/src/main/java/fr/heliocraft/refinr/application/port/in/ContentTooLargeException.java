// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

/**
 * Texte fourni plus long que la taille maximale configuree (413). Son message, ecrit par l'application, ne cite que
 * la limite (jamais le contenu) : il est renvoye tel quel au client.
 */
public class ContentTooLargeException extends RuntimeException {

    public ContentTooLargeException(String message) {
        super(message);
    }
}
