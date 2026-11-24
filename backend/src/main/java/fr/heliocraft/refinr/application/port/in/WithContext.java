// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.ProjectContext;

/**
 * Resultat d'un cas d'usage et contexte projet avec lequel il a ete produit (X1) : la reponse REST en donne la source et
 * la taille, jamais le texte.
 */
public record WithContext<T>(T value, ProjectContext context) {

    public WithContext {
        context = context == null ? ProjectContext.NONE : context;
    }

    /** Resultat produit sans contexte projet. */
    public static <T> WithContext<T> none(T value) {
        return new WithContext<>(value, ProjectContext.NONE);
    }
}
