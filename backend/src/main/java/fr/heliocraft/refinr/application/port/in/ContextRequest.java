// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

/**
 * Contexte projet demande par l'appelant (X1) : un texte ({@code context}) ou une page de la source documentaire
 * ({@code contextDocumentId}), exclusifs ; aucun des deux : la configuration s'applique (propriete du projet, puis
 * contexte par defaut). Valeurs vides traitees comme absentes.
 */
public record ContextRequest(String text, String documentId) {

    public static final ContextRequest NONE = new ContextRequest(null, null);

    public ContextRequest {
        text = text == null || text.isBlank() ? null : text;
        documentId = documentId == null || documentId.isBlank() ? null : documentId.strip();
    }

    /** Vrai si la requete ne demande aucun contexte (la configuration s'applique alors). */
    public boolean isEmpty() {
        return text == null && documentId == null;
    }
}
