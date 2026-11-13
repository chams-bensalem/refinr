// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.ContentTooLargeException;
import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.domain.service.DocumentText;

import java.util.regex.Pattern;

/** Garde commune aux cas d'usage qui analysent un document. */
final class DocumentAnalysis {

    static final String EMPTY_DOCUMENT = "The document is empty or contains no text to analyze";
    static final String MISSING_ID = "documentId is required";
    static final String INVALID_ID = "Invalid documentId";

    // Titre ramene a une seule ligne : un retour a la ligne ne doit pas sortir du titre Markdown
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

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

    /** Limite de taille du texte fourni : strictement positive (sinon erreur de configuration). */
    static int requirePositiveLimit(int maxContentChars) {
        if (maxContentChars <= 0) {
            throw new IllegalArgumentException("The maximum content size must be positive: " + maxContentChars);
        }
        return maxContentChars;
    }

    /**
     * Texte fourni tel qu'envoye au modele : {@code # titre} sur une ligne en tete si un titre est donne, puis le
     * contenu inchange. Refuse (413) au-dela de {@code maxContentChars} caracteres Unicode, titre compris, avant tout
     * appel au modele, puis (400) un contenu vide ou sans texte utile : le titre seul ne suffit pas.
     */
    static String providedText(String title, String content, int maxContentChars) {
        String body = content != null ? content : "";
        String heading = title == null ? "" : WHITESPACE_RUN.matcher(title).replaceAll(" ").strip();
        String text = heading.isEmpty() ? body : "# " + heading + "\n\n" + body;
        if (text.codePointCount(0, text.length()) > maxContentChars) {
            throw new ContentTooLargeException("The document exceeds the maximum size of " + maxContentChars + " characters");
        }
        requireUsefulText(body);
        return text;
    }
}
