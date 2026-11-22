// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import java.time.Duration;
import java.util.Optional;

/**
 * Le fournisseur du modele de langage est indisponible : injoignable, cle refusee, delai depasse... (apres les
 * nouveaux essais prevus). Repondu 502 au client, avec un message generique ; les sous-classes
 * {@link ModelRateLimitedException} et {@link ModelOverloadedException} sont repondues 503.
 */
public class ModelUnavailableException extends RuntimeException {

    private final Duration retryAfter;

    public ModelUnavailableException(Throwable cause) {
        this("The LLM provider is unavailable", cause, null);
    }

    protected ModelUnavailableException(String message, Throwable cause, Duration retryAfter) {
        super(message, cause);
        this.retryAfter = retryAfter;
    }

    /** Delai annonce par le fournisseur avant un nouvel essai, seulement s'il en a donne un. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
