// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import java.time.Duration;

/** Surcharge temporaire du fournisseur (HTTP 503 ou 529), apres les nouveaux essais prevus. Repondu 503. */
public class ModelOverloadedException extends ModelUnavailableException {

    /** @param retryAfter delai annonce par le fournisseur, ou null */
    public ModelOverloadedException(Throwable cause, Duration retryAfter) {
        super("The LLM provider is temporarily overloaded", cause, retryAfter);
    }
}
