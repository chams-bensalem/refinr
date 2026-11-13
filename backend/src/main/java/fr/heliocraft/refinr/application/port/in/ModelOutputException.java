// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

/**
 * Le modele de langage n'a pas produit de resultat exploitable : sortie non conforme au schema meme apres relances,
 * ou sortie tronquee par la limite de tokens. Repondu 502 au client, avec un message generique.
 */
public class ModelOutputException extends RuntimeException {

    private final boolean truncated;

    public ModelOutputException(boolean truncated, Throwable cause) {
        super(truncated ? "The model output was truncated by the token limit" : "The model did not produce a valid result",
                cause);
        this.truncated = truncated;
    }

    /** Vrai si la sortie a ete coupee par la limite de tokens (max-output-tokens a augmenter). */
    public boolean isTruncated() {
        return truncated;
    }
}
