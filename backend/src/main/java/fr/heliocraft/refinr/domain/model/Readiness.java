// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

/**
 * Verdict de preparation d'une story pour un agent de code, avec son origine ({@link ReadinessSource}).
 * Le constructeur a deux arguments donne un verdict du modele.
 */
public record Readiness(boolean aiReady, String aiReadyReason, ReadinessSource source) {

    public Readiness {
        if (source == null) {
            throw new IllegalArgumentException("readiness source is required");
        }
    }

    /** Verdict du modele. */
    public Readiness(boolean aiReady, String aiReadyReason) {
        this(aiReady, aiReadyReason, ReadinessSource.MODEL);
    }
}
