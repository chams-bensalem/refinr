// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Nouveaux essais d'un appel au modele (429, 5xx, fournisseur injoignable ; jamais apres un delai depasse).
 * Delai de l'essai n : {@code baseDelay * multiplier^(n-1)}, a +/- {@code jitter} pres (ex. 2 s puis 8 s, +/- 20 %),
 * plafonne a {@code maxDelay}. Un en-tete {@code Retry-After} du fournisseur est prioritaire (plafonne, sans jitter).
 *
 * @param maxRetries nouveaux essais apres le premier appel (0 = aucun)
 * @param multiplier facteur entre deux delais successifs (>= 1)
 * @param jitter     part aleatoire du delai, entre 0 (aucune) et 1 : le delai est tire dans [d*(1-jitter), d*(1+jitter)]
 */
public record RetryPolicy(int maxRetries, Duration baseDelay, double multiplier, double jitter, Duration maxDelay) {

    public RetryPolicy {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("max-retries must be >= 0");
        }
        if (baseDelay == null || baseDelay.isNegative() || maxDelay == null || maxDelay.isNegative()) {
            throw new IllegalArgumentException("retry-base-delay and retry-max-delay must be >= 0");
        }
        if (!(multiplier >= 1)) {
            throw new IllegalArgumentException("retry-multiplier must be >= 1");
        }
        if (!(jitter >= 0 && jitter <= 1)) {
            throw new IllegalArgumentException("retry-jitter must be between 0 and 1");
        }
    }

    /** Aucun nouvel essai (tests). */
    public static RetryPolicy none() {
        return new RetryPolicy(0, Duration.ZERO, 1, 0, Duration.ZERO);
    }

    /**
     * Delai avant le prochain essai.
     *
     * @param attempt    numero de l'essai qui vient d'echouer (1 = premier appel)
     * @param retryAfter en-tete Retry-After (secondes ou date HTTP), ou null
     * @param random     tirage uniforme dans [0, 1) pour le jitter
     * @param now        instant courant, pour un Retry-After en date HTTP
     */
    public Duration delay(int attempt, String retryAfter, double random, Instant now) {
        Duration requested = parseRetryAfter(retryAfter, now);
        if (requested != null) {
            return requested.compareTo(maxDelay) > 0 ? maxDelay : requested;
        }
        double exponential = baseDelay.toMillis() * Math.pow(multiplier, Math.max(0, attempt - 1));
        double jittered = exponential * (1 - jitter + 2 * jitter * random);
        if (jittered >= maxDelay.toMillis()) {
            return maxDelay;
        }
        return Duration.ofMillis(Math.max(0, Math.round(jittered)));
    }

    /** Retry-After en secondes ({@code 120}) ou en date HTTP ({@code Wed, 21 Oct 2026 07:28:00 GMT}) ; null si absent ou illisible. */
    static Duration parseRetryAfter(String retryAfter, Instant now) {
        if (retryAfter == null || retryAfter.isBlank()) {
            return null;
        }
        String value = retryAfter.trim();
        try {
            long seconds = Long.parseLong(value);
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException notSeconds) {
            try {
                Duration untilDate = Duration.between(now,
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return untilDate.isNegative() ? Duration.ZERO : untilDate;
            } catch (DateTimeParseException unreadable) {
                return null;
            }
        }
    }
}
