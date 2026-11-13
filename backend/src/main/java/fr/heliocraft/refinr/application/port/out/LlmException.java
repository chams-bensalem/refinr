// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

/**
 * Echec d'un appel au modele de langage. Le message est sur pour le client : jamais d'URL, de cle ni de corps de
 * reponse (le detail est dans les logs de l'adaptateur).
 */
public class LlmException extends RuntimeException {

    /** Nature de l'echec. */
    public enum Reason {
        /** Fournisseur injoignable, cle refusee, quota epuise, delai depasse... */
        UNAVAILABLE,
        /** Le modele n'a pas produit de sortie conforme au schema attendu, meme apres relances. */
        INVALID_OUTPUT,
        /** La sortie du modele a ete coupee par la limite de tokens de sortie. */
        TRUNCATED
    }

    private final Reason reason;

    public LlmException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public LlmException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
