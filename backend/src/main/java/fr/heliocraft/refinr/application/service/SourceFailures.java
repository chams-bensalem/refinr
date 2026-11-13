// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.DocumentNotFoundException;
import fr.heliocraft.refinr.application.port.in.DocumentSourceUnavailableException;
import fr.heliocraft.refinr.application.port.out.DocumentSourceException;

import java.util.function.Supplier;

/** Traduit un echec de la source documentaire (document introuvable, source indisponible) en erreur du cas d'usage. */
final class SourceFailures {

    private SourceFailures() {}

    static <T> T translate(Supplier<T> call) {
        try {
            return call.get();
        } catch (DocumentSourceException e) {
            if (e.reason() == DocumentSourceException.Reason.NOT_FOUND) {
                throw new DocumentNotFoundException(e.getMessage(), e);
            }
            throw new DocumentSourceUnavailableException(e.getMessage(), e);
        }
    }
}
