// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.ModelOutputException;
import fr.heliocraft.refinr.application.port.in.ModelOverloadedException;
import fr.heliocraft.refinr.application.port.in.ModelRateLimitedException;
import fr.heliocraft.refinr.application.port.in.ModelUnavailableException;
import fr.heliocraft.refinr.application.port.out.LlmException;

import java.util.function.Supplier;

/**
 * Traduit un echec du modele en erreur du cas d'usage : sortie invalide ou tronquee, quota ou limite de debit (429),
 * surcharge (503, 529), autre indisponibilite. Le delai annonce par le fournisseur est conserve.
 */
final class LlmFailures {

    private LlmFailures() {}

    static <T> T translate(Supplier<T> call) {
        try {
            return call.get();
        } catch (LlmException e) {
            if (e.reason() == LlmException.Reason.INVALID_OUTPUT) {
                throw new ModelOutputException(false, e);
            }
            if (e.reason() == LlmException.Reason.TRUNCATED) {
                throw new ModelOutputException(true, e);
            }
            throw switch (e.reason()) {
                case RATE_LIMITED -> new ModelRateLimitedException(e, e.retryAfter().orElse(null));
                case OVERLOADED -> new ModelOverloadedException(e, e.retryAfter().orElse(null));
                default -> new ModelUnavailableException(e);
            };
        }
    }
}
