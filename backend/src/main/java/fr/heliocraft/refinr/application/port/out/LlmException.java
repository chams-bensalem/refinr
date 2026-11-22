// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

import java.time.Duration;
import java.util.Optional;

/**
 * Echec d'un appel au modele de langage. Le message est sur pour le client comme pour les logs : jamais d'URL, de
 * cle, de corps de reponse, de texte du prompt ni de sortie du modele (seulement fournisseur, statut HTTP, categorie
 * et tailles). Les adaptateurs ne lui donnent pas de cause, pour qu'une trace complete reste sure elle aussi.
 */
public class LlmException extends RuntimeException {

    /** Nature de l'echec. */
    public enum Reason {
        /** Fournisseur injoignable, cle refusee, delai depasse, erreur 5xx autre que 503... */
        UNAVAILABLE,
        /** HTTP 429 : quota ou limite de debit du fournisseur (apres les nouveaux essais prevus). */
        RATE_LIMITED,
        /** HTTP 503 ou 529 : surcharge temporaire du fournisseur (apres les nouveaux essais prevus). */
        OVERLOADED,
        /** Le modele n'a pas produit de sortie conforme au schema attendu, meme apres relances. */
        INVALID_OUTPUT,
        /** La sortie du modele a ete coupee par la limite de tokens de sortie. */
        TRUNCATED
    }

    private final Reason reason;
    private final Duration retryAfter;

    public LlmException(Reason reason, String message) {
        this(reason, message, (Duration) null);
    }

    /** @param retryAfter delai annonce par le fournisseur avant un nouvel essai (deja borne), ou null s'il n'en donne pas */
    public LlmException(Reason reason, String message, Duration retryAfter) {
        super(message);
        this.reason = reason;
        this.retryAfter = retryAfter;
    }

    public LlmException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.retryAfter = null;
    }

    public Reason reason() {
        return reason;
    }

    /** Delai annonce par le fournisseur (Retry-After, RetryInfo), seulement s'il en a donne un. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
