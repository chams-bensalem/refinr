// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.domain.service.TestCasePolicy;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.domain.model.Epic;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reponses des endpoints d'analyse ({@code analyze-preview}, {@code analyze-and-create}), partagees par les endpoints
 * synchrones et par le resultat des jobs ({@code GET /api/v1/jobs/{id}/result}) : un meme resultat ou un meme echec
 * donne le meme statut, les memes en-tetes et le meme corps. Aucun log ici : chaque appelant journalise a sa facon.
 */
final class BacklogResponses {

    static final String RATE_LIMITED = "The AI provider quota or rate limit was reached";
    static final String OVERLOADED = "The AI provider is temporarily overloaded";
    static final String UNAVAILABLE = "The AI provider is unavailable";
    static final String OUTPUT_TRUNCATED = "The AI model output was truncated: increase max-output-tokens";
    static final String OUTPUT_INVALID = "The AI model did not produce a valid result, even after retries";
    static final String PREVIEW_FAILED = "Failed to generate the preview";
    static final String CREATION_FAILED = "Failed to analyze the document and create the issues";

    private BacklogResponses() {}

    /** 200 : apercu genere ; {@code warnings} seulement s'il y en a (ex. gestionnaire sans niveau Feature). */
    static ResponseEntity<Map<String, Object>> preview(List<Epic> epics, List<String> warnings) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "Preview generated successfully");
        body.put("epics", RestBacklogMapper.toDto(epics));
        putWarnings(body, warnings);
        return ResponseEntity.ok(body);
    }

    /**
     * Chaine C1 : ajoute {@code questions} (toujours present, liste vide comprise) et l'avertissement des stories pretes
     * restees sans sous-taches. Reponse inchangee pour un statut d'erreur sans backlog.
     */
    static ResponseEntity<Map<String, Object>> withPipeline(ResponseEntity<Map<String, Object>> response,
                                                            List<Epic> epics) {
        if (response.getBody() == null || epics == null) {
            return response;
        }
        Map<String, Object> body = new LinkedHashMap<>(response.getBody());
        List<Map<String, Object>> questions = new java.util.ArrayList<>();
        for (fr.heliocraft.refinr.domain.model.PoQuestion question : fr.heliocraft.refinr.domain.service.PoQuestions.from(epics)) {
            List<Map<String, Object>> stories = new java.util.ArrayList<>();
            for (fr.heliocraft.refinr.domain.model.Story story : question.stories()) {
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("title", story.getTitle());
                ref.put("key", story.getTrackerKey());
                stories.add(ref);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question", question.question());
            item.put("stories", stories);
            questions.add(item);
        }
        body.put("questions", questions);
        long readyWithoutTasks = epics.stream().filter(java.util.Objects::nonNull).flatMap(e -> e.getStories().stream())
                .filter(s -> s != null && Boolean.TRUE.equals(s.getAiReady())
                        && (s.getTasks() == null || s.getTasks().isEmpty()))
                .count();
        List<String> added = new java.util.ArrayList<>();
        if (readyWithoutTasks > 0) {
            added.add(readyWithoutTasksWarning(readyWithoutTasks));
        }
        // Chaine C2 : story dont les cas de test n'ont pas ete ecrits (couverture), nommee avec ses criteres ; prompt 5
        // indisponible pour tout le lot : un seul avertissement global
        long unavailable = epics.stream().filter(java.util.Objects::nonNull).flatMap(e -> e.getStories().stream())
                .filter(s -> s != null && TestCasePolicy.MODEL_UNAVAILABLE.equals(s.getTestCasesError())).count();
        if (unavailable > 0) {
            added.add(testCasesUnavailableWarning(unavailable));
        }
        epics.stream().filter(java.util.Objects::nonNull).flatMap(e -> e.getStories().stream())
                .filter(s -> s != null && s.getTestCasesError() != null
                        && !TestCasePolicy.MODEL_UNAVAILABLE.equals(s.getTestCasesError()))
                .forEach(s -> added.add("story \"" + s.getTitle() + "\": " + s.getTestCasesError()));
        if (!added.isEmpty()) {
            List<String> warnings = new java.util.ArrayList<>();
            if (body.get("warnings") instanceof List<?> existing) {
                existing.forEach(w -> warnings.add(String.valueOf(w)));
            }
            warnings.addAll(added);
            body.put("warnings", warnings);
        }
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(body);
    }

    static String testCasesUnavailableWarning(long count) {
        return "test cases could not be generated for " + count + " ready stor" + (count == 1 ? "y" : "ies")
                + " (model unavailable): create them with POST /api/v1/story-tasks once the story exists";
    }

    static String readyWithoutTasksWarning(long count) {
        return count + " ready stor" + (count == 1 ? "y has" : "ies have")
                + " no sub-tasks (the model gave none): create them with POST /api/v1/story-tasks once the story exists";
    }

    /** Ajout retrocompatible (1.5) : present seulement quand la liste n'est pas vide. */
    private static void putWarnings(Map<String, Object> body, List<String> warnings) {
        if (warnings != null && !warnings.isEmpty()) {
            body.put("warnings", List.copyOf(warnings));
        }
    }

    /** 422 : des Features explicites, pas de niveau Feature dans le gestionnaire, et la politique est le refus. */
    static ResponseEntity<Map<String, Object>> featureLevelMissing(String message) {
        return ResponseEntity.status(HttpStatus.valueOf(422)).body(ErrorResponses.error(message));
    }

    /**
     * Reponse de creation : 200 si tout est cree, 207 si la creation est partielle, 502 si rien n'a pu etre cree.
     * Toujours les listes created / failed et les epics (avec la cle des tickets crees).
     */
    static ResponseEntity<Map<String, Object>> creation(BacklogCreationResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        HttpStatus status;
        if (result.isComplete()) {
            status = HttpStatus.OK;
            body.put("success", true);
            body.put("message", "Successfully created " + result.created().size() + " issues");
        } else if (!result.isNothingCreated()) {
            status = HttpStatus.MULTI_STATUS;
            body.put("success", false);
            body.put("message", "Partially created: " + result.created().size() + " issues created, "
                    + result.failed().size() + " not created");
        } else {
            status = HttpStatus.BAD_GATEWAY;
            body.putAll(ErrorResponses.error("No issue could be created"));
        }
        body.put("created", result.created());
        body.put("failed", result.failed());
        body.put("epics", RestBacklogMapper.toDto(result.epics()));
        putWarnings(body, result.warnings());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Ajoute {@code documentUrl} (lien vers le document dans la source active) quand il est connu : ajout
     * retrocompatible, absent si la source ne sait pas construire le lien.
     */
    static ResponseEntity<Map<String, Object>> withDocumentUrl(ResponseEntity<Map<String, Object>> response, String url) {
        if (url == null || response.getBody() == null) {
            return response;
        }
        Map<String, Object> body = new LinkedHashMap<>(response.getBody());
        body.put("documentUrl", url);
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(body);
    }

    /** 400 : entree refusee par le cas d'usage ; son message, ecrit par l'application, est renvoye tel quel. */
    static ResponseEntity<Map<String, Object>> invalidRequest(String message) {
        return ResponseEntity.badRequest().body(ErrorResponses.error(message));
    }

    /** 413 : texte fourni au-dela de refinr.document.max-content-chars ; le message ne cite que la limite. */
    static ResponseEntity<Map<String, Object>> contentTooLarge(String message) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(ErrorResponses.error(message));
    }

    /** 404 : document absent ou non visible pour le compte ; message sur ecrit par l'adaptateur (ni URL ni corps brut). */
    static ResponseEntity<Map<String, Object>> documentNotFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponses.error(message));
    }

    /** 502 : la source documentaire n'a pas pu repondre (identifiants, droits, indisponibilite, delai), message sur. */
    static ResponseEntity<Map<String, Object>> sourceUnavailable(String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponses.error(message));
    }

    /** 502 : le modele n'a pas produit de resultat exploitable (sortie invalide apres relances, ou tronquee). */
    static ResponseEntity<Map<String, Object>> modelOutputFailure(boolean truncated) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponses.error(truncated ? OUTPUT_TRUNCATED : OUTPUT_INVALID));
    }

    /** 502 : fournisseur du modele injoignable, cle refusee, delai depasse... */
    static ResponseEntity<Map<String, Object>> modelUnavailable() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponses.error(UNAVAILABLE));
    }

    /**
     * 503 pour un quota ou une limite de debit (429 du fournisseur) ou une surcharge temporaire (503, 529), avec
     * l'en-tete {@code Retry-After} et le champ {@code retryAfterSeconds} seulement quand un delai a ete annonce.
     */
    static ResponseEntity<Map<String, Object>> modelBusy(boolean rateLimited, Optional<Duration> retryAfter) {
        Map<String, Object> body = ErrorResponses.error(rateLimited ? RATE_LIMITED : OVERLOADED);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE);
        retryAfter.ifPresent(delay -> {
            body.put("retryAfterSeconds", delay.toSeconds());
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(delay.toSeconds()));
        });
        return response.body(body);
    }

    /** 500 : erreur inattendue, message generique propre a l'endpoint. */
    static ResponseEntity<Map<String, Object>> internalError(String message) {
        return ResponseEntity.internalServerError().body(ErrorResponses.error(message));
    }
}
