// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.adapters.in.rest.dto.EpicDto;
import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.CreateEditedBacklog;
import fr.heliocraft.refinr.application.port.in.DescribeSource;
import fr.heliocraft.refinr.application.port.in.DescribeTracker;
import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.in.ModelOutputException;
import fr.heliocraft.refinr.application.port.in.ModelUnavailableException;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.in.ReassessStory;
import fr.heliocraft.refinr.application.port.in.SearchDocuments;
import fr.heliocraft.refinr.application.port.in.StoryNotAwaitingValidationException;
import fr.heliocraft.refinr.domain.model.DocumentSummary;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoints du backlog : analyse d'un document, apercu, creation dans le gestionnaire de tickets, reevaluation d'une
 * story et recherche de documents. Le health check est servi par {@link HealthCheckController}.
 */
@RestController
@RequestMapping("/api/v1")
public class BacklogController {

    private static final Logger log = LoggerFactory.getLogger(BacklogController.class);

    // Identifiant du document a analyser : champ neutre, et ancien nom garde comme alias (clients existants)
    static final String DOCUMENT_ID = "documentId";
    static final String LEGACY_DOCUMENT_ID = "confluencePageId";

    // Alias deprecie de GET /documents/search (RFC 9745 : date de depreciation, RFC 8288 : remplacant)
    static final String LEGACY_SEARCH_DEPRECATION = "@1794528000";
    static final String LEGACY_SEARCH_SUCCESSOR = "</api/v1/documents/search>; rel=\"successor-version\"";

    private final AnalyzeDocument analyzeDocument;
    private final PreviewBacklog previewBacklog;
    private final CreateEditedBacklog createEditedBacklog;
    private final ReassessStory reassessStory;
    private final SearchDocuments searchDocuments;
    private final DescribeTracker describeTracker;
    private final DescribeSource describeSource;

    public BacklogController(
            AnalyzeDocument analyzeDocument,
            PreviewBacklog previewBacklog,
            CreateEditedBacklog createEditedBacklog,
            ReassessStory reassessStory,
            SearchDocuments searchDocuments,
            DescribeTracker describeTracker,
            DescribeSource describeSource) {
        this.analyzeDocument = analyzeDocument;
        this.previewBacklog = previewBacklog;
        this.createEditedBacklog = createEditedBacklog;
        this.reassessStory = reassessStory;
        this.searchDocuments = searchDocuments;
        this.describeTracker = describeTracker;
        this.describeSource = describeSource;
    }

    /**
     * Identifiant du document : {@code documentId}, ou son alias {@code confluencePageId}. Valeurs vides ignorees ;
     * deux valeurs differentes -> 400. Absent : renvoye tel quel, le cas d'usage repond 400 « documentId is required ».
     */
    private static String documentId(Map<String, String> request) {
        String id = request.get(DOCUMENT_ID);
        String legacy = request.get(LEGACY_DOCUMENT_ID);
        boolean hasId = id != null && !id.isBlank();
        boolean hasLegacy = legacy != null && !legacy.isBlank();
        if (hasId && hasLegacy && !id.equals(legacy)) {
            throw new InvalidRequestException(DOCUMENT_ID + " and " + LEGACY_DOCUMENT_ID + " differ");
        }
        return hasId ? id : hasLegacy ? legacy : id;
    }

    @PostMapping("/analyze-and-create")
    public ResponseEntity<Map<String, Object>> analyzeAndCreate(@RequestBody Map<String, String> request) {
        // Projet cible optionnel (sinon le projet par defaut du gestionnaire de tickets actif) : cle Jira ou identifiant
        // OpenProject selon tracker.provider ; permet a un outil appelant de remplir un projet qu'il vient de creer.
        String projectKey = request.get("projectKey");
        if (!isValidProjectKey(projectKey)) {
            return invalidProjectKey(projectKey);
        }
        
        try {
            String documentId = documentId(request);
            log.info("Received request to analyze {} document: {}", describeSource.displayName(), documentId);
            return creationResponse(analyzeDocument.analyzeAndCreate(documentId, projectKey));

        } catch (InvalidRequestException e) {
            return invalidRequest(e);
        } catch (ModelOutputException e) {
            return modelOutputFailure(e);
        } catch (ModelUnavailableException e) {
            return modelUnavailable(e);
        } catch (Exception e) {
            log.error("Error processing request", e);
            return ResponseEntity.internalServerError().body(ErrorResponses.error("Failed to analyze the document and create the issues"));
        }
    }

    /**
     * Reponse de creation : 200 si tout est cree, 207 si la creation est partielle, 502 si rien n'a pu etre cree.
     * Toujours les listes created / failed et les epics (avec la cle des tickets crees).
     */
    private static ResponseEntity<Map<String, Object>> creationResponse(BacklogCreationResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        HttpStatus status;
        if (result.isComplete()) {
            status = HttpStatus.OK;
            body.put("success", true);
            body.put("message", "Successfully created " + result.created().size() + " issues");
        } else if (!result.created().isEmpty()) {
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
        return ResponseEntity.status(status).body(body);
    }

    /** 502 : le modele n'a pas produit de resultat exploitable (sortie invalide apres relances, ou tronquee). */
    private static ResponseEntity<Map<String, Object>> modelOutputFailure(ModelOutputException e) {
        log.warn("Unusable model output: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponses.error(e.isTruncated()
                ? "The AI model output was truncated: increase max-output-tokens"
                : "The AI model did not produce a valid result, even after retries"));
    }

    /** 502 : le fournisseur du modele est indisponible (injoignable, cle refusee, quota, delai) apres les nouveaux essais. */
    private static ResponseEntity<Map<String, Object>> modelUnavailable(ModelUnavailableException e) {
        log.warn("LLM provider unavailable: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponses.error("The AI provider is unavailable"));
    }

    /** 400 : entree refusee par le cas d'usage ; son message, ecrit par l'application, est renvoye tel quel. */
    private static ResponseEntity<Map<String, Object>> invalidRequest(InvalidRequestException e) {
        return ResponseEntity.badRequest().body(ErrorResponses.error(e.getMessage()));
    }

    /** Absent ou vide : projet par defaut ; sinon au format du gestionnaire de tickets actif (ex. PROJ pour Jira). */
    private boolean isValidProjectKey(String projectKey) {
        return projectKey == null || projectKey.isBlank() || describeTracker.acceptsProjectId(projectKey);
    }

    private static ResponseEntity<Map<String, Object>> invalidProjectKey(String projectKey) {
        return ResponseEntity.badRequest().body(ErrorResponses.error("Invalid projectKey: " + projectKey));
    }

    @PostMapping("/analyze-preview")
    public ResponseEntity<Map<String, Object>> analyzePreview(@RequestBody Map<String, String> request) {
        try {
            String documentId = documentId(request);
            log.info("Preview request for {} document: {}", describeSource.displayName(), documentId);
            List<Epic> epics = previewBacklog.preview(documentId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Preview generated successfully",
                    "epics", RestBacklogMapper.toDto(epics)
            ));
            
        } catch (InvalidRequestException e) {
            return invalidRequest(e);
        } catch (ModelOutputException e) {
            return modelOutputFailure(e);
        } catch (ModelUnavailableException e) {
            return modelUnavailable(e);
        } catch (Exception e) {
            log.error("Error generating preview", e);
            return ResponseEntity.internalServerError().body(ErrorResponses.error("Failed to generate the preview"));
        }
    }

    @PostMapping("/create-jira")
    public ResponseEntity<Map<String, Object>> createJira(
            @RequestBody List<EpicDto> request,
            // Projet cible optionnel (sinon le projet par defaut du gestionnaire actif), meme validation que analyze-and-create
            @RequestParam(required = false) String projectKey) {
        if (!isValidProjectKey(projectKey)) {
            return invalidProjectKey(projectKey);
        }
        log.info("Received request to create {} epics in {}", request.size(), describeTracker.displayName());

        try {
            List<Epic> epics = RestBacklogMapper.toDomain(request);
            // Verdict recalcule sur le contenu final : aiReady / aiReadyReason envoyes par le client sont ignores
            return creationResponse(createEditedBacklog.create(epics, projectKey));

        } catch (Exception e) {
            log.error("Error creating issues in {}", describeTracker.displayName(), e);
            return ResponseEntity.internalServerError().body(ErrorResponses.error("Failed to create the issues"));
        }
    }

    /**
     * Reevalue une story en attente de validation avec une precision de l'utilisateur (fournie par
     * l'appelant) : la story existante est mise a jour, aucun ticket n'est recree.
     */
    @PostMapping("/reassess-story")
    public ResponseEntity<Map<String, Object>> reassessStory(@RequestBody Map<String, String> request) {
        String issueKey = request.get("issueKey");
        String precision = request.get("precision");
        if (issueKey == null || issueKey.isBlank() || precision == null || precision.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "issueKey and precision are required"
            ));
        }

        log.info("Reassessment request for {}", issueKey);
        try {
            Readiness verdict = reassessStory.reassess(issueKey, precision);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "issueKey", issueKey,
                    "aiReady", verdict.aiReady(),
                    "aiReadyReason", verdict.aiReadyReason() != null ? verdict.aiReadyReason() : ""
            ));
        } catch (StoryNotAwaitingValidationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponses.error(e.getMessage()));
        } catch (ModelOutputException e) {
            return modelOutputFailure(e);
        } catch (ModelUnavailableException e) {
            return modelUnavailable(e);
        } catch (Exception e) {
            log.error("Error reassessing story {}", issueKey, e);
            return ResponseEntity.internalServerError().body(ErrorResponses.error("Failed to reassess the story"));
        }
    }

    /** Recherche de documents de la source active par titre, dans un espace optionnel (format de la source). */
    @GetMapping("/documents/search")
    public ResponseEntity<Map<String, Object>> searchDocuments(
            @RequestParam(required = false) String space,
            @RequestParam(required = false) String title) {
        return search(space, title);
    }

    /**
     * Alias deprecie de {@code GET /documents/search}, garde pour les clients existants : {@code spaceKey} reste
     * obligatoire et le meme message d'erreur est renvoye ; en-tetes {@code Deprecation} et {@code Link} en plus.
     */
    @GetMapping("/confluence/search")
    public ResponseEntity<Map<String, Object>> searchConfluencePages(
            @RequestParam String spaceKey,
            @RequestParam String title) {
        ResponseEntity<Map<String, Object>> response = describeSource.acceptsSpace(spaceKey)
                ? search(spaceKey, title)
                : ResponseEntity.badRequest().body(ErrorResponses.error("Invalid spaceKey"));
        return ResponseEntity.status(response.getStatusCode())
                .header("Deprecation", LEGACY_SEARCH_DEPRECATION)
                .header(HttpHeaders.LINK, LEGACY_SEARCH_SUCCESSOR)
                .body(response.getBody());
    }

    private ResponseEntity<Map<String, Object>> search(String space, String title) {
        log.info("Searching {} documents: space={}, title={}", describeSource.displayName(), space, title);
        try {
            List<DocumentSummary> results = searchDocuments.search(space, title);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "results", results
            ));

        } catch (InvalidRequestException e) {
            return invalidRequest(e);
        } catch (Exception e) {
            log.error("Error searching {} documents", describeSource.displayName(), e);
            return ResponseEntity.internalServerError().body(ErrorResponses.error("Failed to search the documents"));
        }
    }
}
