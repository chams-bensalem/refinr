// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

/**
 * Le fournisseur du modele de langage est indisponible : injoignable, cle refusee, quota epuise, delai depasse...
 * (apres les nouveaux essais prevus). Repondu 502 au client, avec un message generique.
 */
public class ModelUnavailableException extends RuntimeException {

    public ModelUnavailableException(Throwable cause) {
        super("The LLM provider is unavailable", cause);
    }
}
