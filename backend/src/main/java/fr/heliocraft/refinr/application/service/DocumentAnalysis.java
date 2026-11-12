package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.domain.service.DocumentText;

/** Garde commune aux cas d'usage qui analysent un document. */
final class DocumentAnalysis {

    static final String EMPTY_DOCUMENT = "The document is empty or contains no text to analyze";

    private DocumentAnalysis() {}

    /** Refuse (400) un document sans texte utile, avant tout appel au modele. */
    static void requireUsefulText(String content) {
        if (!DocumentText.hasUsefulText(content)) {
            throw new InvalidRequestException(EMPTY_DOCUMENT);
        }
    }
}
