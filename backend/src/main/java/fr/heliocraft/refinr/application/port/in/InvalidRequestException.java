// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

/**
 * Entree refusee par un cas d'usage. Son message est ecrit par l'application, sans donnee technique : il est renvoye
 * tel quel au client (400). Toute autre exception, y compris une IllegalArgumentException d'une librairie, reste une
 * erreur interne au message generique.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
