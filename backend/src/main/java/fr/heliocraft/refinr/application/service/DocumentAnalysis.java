// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.domain.service.DocumentText;

/** Garde commune aux cas d'usage qui analysent un document. */
final class DocumentAnalysis {

    static final String EMPTY_DOCUMENT = "The document is empty or contains no text to analyze";
    static final String MISSING_ID = "documentId is required";
    static final String INVALID_ID = "Invalid documentId";

    private DocumentAnalysis() {}

    /** Refuse (400) un identifiant absent ou au mauvais format pour la source active, avant tout appel a la source. */
    static void requireValidId(DocumentSourcePort documentSource, String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new InvalidRequestException(MISSING_ID);
        }
        if (!documentSource.acceptsDocumentId(documentId)) {
            throw new InvalidRequestException(INVALID_ID);
        }
    }

    /** Refuse (400) un document sans texte utile, avant tout appel au modele. */
    static void requireUsefulText(String content) {
        if (!DocumentText.hasUsefulText(content)) {
            throw new InvalidRequestException(EMPTY_DOCUMENT);
        }
    }
}
