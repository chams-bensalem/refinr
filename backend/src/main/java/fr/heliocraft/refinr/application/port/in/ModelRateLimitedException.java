// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import java.time.Duration;

/** Quota ou limite de debit du fournisseur atteint (HTTP 429), apres les nouveaux essais prevus. Repondu 503. */
public class ModelRateLimitedException extends ModelUnavailableException {

    /** @param retryAfter delai annonce par le fournisseur, ou null */
    public ModelRateLimitedException(Throwable cause, Duration retryAfter) {
        super("The LLM provider is rate limiting the requests", cause, retryAfter);
    }
}
