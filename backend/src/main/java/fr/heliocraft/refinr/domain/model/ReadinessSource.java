// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

import java.util.Locale;

/**
 * Origine d'un verdict : le modele ({@code model}), le repli faute de verdict fiable ({@code fallback}, jamais
 * ai-ready) ou une validation humaine directe, sans appel au modele ({@code human}). Valeur exposee par l'API REST
 * ({@code aiReadySource}) : {@link #id()}.
 */
public enum ReadinessSource {
    MODEL, FALLBACK, HUMAN;

    /** Identifiant REST, en minuscules : model, fallback, human. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
