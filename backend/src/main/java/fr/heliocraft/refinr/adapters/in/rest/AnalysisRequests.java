// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.domain.model.Language;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Lecture du corps des requetes d'analyse ({@code analyze-preview}, {@code analyze-and-create}), commune aux endpoints
 * synchrones et aux jobs : memes champs, memes refus (400), memes messages.
 */
final class AnalysisRequests {

    // Identifiant du document a analyser : champ neutre, et ancien nom garde comme alias (clients existants)
    static final String DOCUMENT_ID = "documentId";
    static final String LEGACY_DOCUMENT_ID = "confluencePageId";
    // Document fourni en texte (Markdown ou texte brut), exclusif de l'identifiant, et son titre optionnel
    static final String CONTENT = "content";
    static final String TITLE = "title";
    static final String PROJECT_KEY = "projectKey";
    /** Langue de sortie optionnelle : code ISO 639-1 (casse ignoree) ; jamais recopiee dans un prompt ni une reponse. */
    static final String LANGUAGE = "language";
    static final String INVALID_LANGUAGE = LANGUAGE + " " + Language.INVALID;
    // Valeur recue jamais recopiee (meme regle que language) : elle peut contenir n'importe quoi
    static final String INVALID_PROJECT_KEY = "Invalid projectKey: expected a project key or identifier of the active tracker";
    /** Chaine C1 (analyze-preview, analyze-and-create, synchrones et jobs). */
    static final String PIPELINE = "pipeline";
    /** reassess-story, validate-story : sous-taches creees si la story devient prete. */
    static final String CREATE_TASKS = "createTasks";

    /** 400 qui nomme l'option booleenne, sans recopier la valeur recue. */
    static String invalidBoolean(String name) {
        return name + " must be true or false";
    }

    /**
     * Option booleenne d'un corps JSON lu en {@code Map<String, String>} : absente ou vide -> false ; {@code true} /
     * {@code false} (booleen JSON ou texte, casse ignoree) ; IllegalArgumentException sinon.
     */
    static boolean flag(Map<String, String> request, String name) {
        String raw = request.get(name);
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return switch (raw.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(invalidBoolean(name));
        };
    }

    private AnalysisRequests() {}

    /**
     * Identifiant du document : {@code documentId}, ou son alias {@code confluencePageId}. Valeurs vides ignorees ;
     * deux valeurs differentes -> 400. Absent : renvoye tel quel, le cas d'usage repond 400 « documentId is required ».
     */
    static String documentId(Map<String, String> request) {
        String id = request.get(DOCUMENT_ID);
        String legacy = request.get(LEGACY_DOCUMENT_ID);
        boolean hasId = id != null && !id.isBlank();
        boolean hasLegacy = legacy != null && !legacy.isBlank();
        if (hasId && hasLegacy && !id.equals(legacy)) {
            throw new InvalidRequestException(DOCUMENT_ID + " and " + LEGACY_DOCUMENT_ID + " differ");
        }
        return hasId ? id : hasLegacy ? legacy : id;
    }

    /**
     * Vrai si le document est fourni en texte : {@code content} present (meme vide : refuse ensuite comme document
     * vide). Combine a un identifiant non vide -> 400 ; {@code title} sans {@code content} -> 400.
     */
    static boolean isTextInput(Map<String, String> request) {
        if (request.get(CONTENT) == null) {
            if (isPresent(request.get(TITLE))) {
                throw new InvalidRequestException(TITLE + " is only accepted with " + CONTENT);
            }
            return false;
        }
        if (isPresent(request.get(DOCUMENT_ID)) || isPresent(request.get(LEGACY_DOCUMENT_ID))) {
            throw new InvalidRequestException(CONTENT + " cannot be combined with " + DOCUMENT_ID + " or " + LEGACY_DOCUMENT_ID);
        }
        return true;
    }

    static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    /** Journal du mode texte : taille seulement, jamais le contenu ni le titre. */
    static void logTextInput(Logger log, String action, Map<String, String> request) {
        String content = request.get(CONTENT);
        log.info("{} request for provided text: {} characters, title: {}", action,
                content.codePointCount(0, content.length()), isPresent(request.get(TITLE)) ? "yes" : "no");
    }

    /** Langue demandee, ou null si absente ; IllegalArgumentException si ce n'est pas un code ISO 639-1 connu. */
    static Language language(String raw) {
        return raw == null ? null : Language.of(raw);
    }
}
