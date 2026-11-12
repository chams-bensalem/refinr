package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.ModelOutputException;
import fr.heliocraft.refinr.application.port.out.LlmException;

import java.util.function.Supplier;

/** Traduit une sortie de modele inexploitable (invalide ou tronquee) en erreur du cas d'usage. */
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
            throw e;
        }
    }
}
