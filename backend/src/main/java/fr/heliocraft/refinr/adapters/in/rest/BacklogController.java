// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.application.port.in.WithContext;
import fr.heliocraft.refinr.application.port.in.ContextRequest;
import java.util.LinkedHashMap;
import fr.heliocraft.refinr.application.port.in.StoryTasksInProgressException;
import fr.heliocraft.refinr.application.port.in.StoryNotReadyException;
import fr.heliocraft.refinr.application.port.in.StoryTasksResult;
import fr.heliocraft.refinr.application.port.in.GenerateStoryTasks;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.AnalysisProgress;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.adapters.in.rest.dto.EpicDto;
import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.ContentTooLargeException;
import fr.heliocraft.refinr.application.port.in.CreateEditedBacklog;
import fr.heliocraft.refinr.application.port.in.DescribeSource;
import fr.heliocraft.refinr.application.port.in.DocumentNotFoundException;
import fr.heliocraft.refinr.application.port.in.DocumentSourceUnavailableException;
import fr.heliocraft.refinr.application.port.in.FeatureLevelMissingException;
import fr.heliocraft.refinr.application.port.in.DescribeTracker;
import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.in.ModelOutputException;
import fr.heliocraft.refinr.application.port.in.ModelOverloadedException;
import fr.heliocraft.refinr.application.port.in.ModelRateLimitedException;
import fr.heliocraft.refinr.application.port.in.ModelUnavailableException;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.in.ReassessStory;
import fr.heliocraft.refinr.application.port.in.ValidateStory;
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

    static final String LANGUAGE = AnalysisRequests.LANGUAGE;
    static final String INVALID_LANGUAGE = AnalysisRequests.INVALID_LANGUAGE;

    // Alias deprecie de GET /documents/search (RFC 9745 : date de depreciation, RFC 8288 : remplacant)
    static final String LEGACY_SEARCH_DEPRECATION = "@1794528000";
    static final String LEGACY_SEARCH_SUCCESSOR = "</api/v1/documents/search>; rel=\"successor-version\"";

    private final AnalyzeDocument analyzeDocument;
    private final PreviewBacklog previewBacklog;
    private final CreateEditedBacklog createEditedBacklog;
    private final ReassessStory reassessStory;
    private final ValidateStory validateStory;
    private final SearchDocuments searchDocuments;
    private final DescribeTracker describeTracker;
    private final DescribeSource describeSource;
    // Chaine C1 : sous-taches d'une story devenue prete
    private final GenerateStoryTasks storyTasks;

    public BacklogController(
            AnalyzeDocument analyzeDocument,
            PreviewBacklog previewBacklog,
            CreateEditedBacklog createEditedBacklog,
            ReassessStory reassessStory,
            ValidateStory validateStory,
            SearchDocuments searchDocuments,
            DescribeTracker describeTracker,
            DescribeSource describeSource,
            GenerateStoryTasks storyTasks) {
        this.storyTasks = storyTasks;
        this.analyzeDocument = analyzeDocument;
        this.previewBacklog = previewBacklog;
        this.createEditedBacklog = createEditedBacklog;
        this.reassessStory = reassessStory;
        this.validateStory = validateStory;
        this.searchDocuments = searchDocuments;
        this.describeTracker = describeTracker;
        this.describeSource = describeSource;
    }

    @PostMapping("/analyze-and-create")
    public ResponseEntity<Map<String, Object>> analyzeAndCreate(@RequestBody Map<String, String> request) {
        // Projet cible optionnel (sinon le projet par defaut du gestionnaire de tickets actif) : cle Jira ou identifiant
        // OpenProject selon tracker.provider ; permet a un outil appelant de remplir un projet qu'il vient de creer.
        String projectKey = request.get(AnalysisRequests.PROJECT_KEY);
        if (!isValidProjectKey(projectKey)) {
            return invalidProjectKey(projectKey);
        }
        Language language;
        try {
            language = language(request.get(LANGUAGE));
        } catch (IllegalArgumentException e) {
            return invalidLanguage();
        }

        boolean pipeline;
        try {
            pipeline = AnalysisRequests.flag(request, AnalysisRequests.PIPELINE);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponses.error(e.getMessage()));
        }

        try {
            if (AnalysisRequests.isTextInput(request)) {
                // Pas de documentUrl : le texte n'a pas d'adresse dans la source active
                AnalysisRequests.logTextInput(log, "Analyze", request);
                String title = request.get(AnalysisRequests.TITLE);
                String content = request.get(AnalysisRequests.CONTENT);
                WithContext<BacklogCreationResult> result = analyzeDocument.analyzeTextAndCreate(title, content,
                        projectKey, language, pipeline, AnalysisRequests.context(request), AnalysisProgress.NONE);
                return BacklogResponses.withContext(creationResponse(result.value(), pipeline), result.context());
            }
            String documentId = AnalysisRequests.documentId(request);
            log.info("Received request to analyze {} document: {}", describeSource.displayName(), documentId);
            WithContext<BacklogCreationResult> result = analyzeDocument.analyzeAndCreate(documentId, projectKey, language,
                    pipeline, AnalysisRequests.context(request), AnalysisProgress.NONE);
            return BacklogResponses.withContext(BacklogResponses.withDocumentUrl(creationResponse(result.value(), pipeline),
                    describeSource.documentUrl(documentId)), result.context());

        } catch (InvalidRequestException e) {
            return invalidRequest(e);
        } catch (ContentTooLargeException e) {
            return contentTooLarge(e);
        } catch (DocumentNotFoundException e) {
            return documentNotFound(e);
        } catch (DocumentSourceUnavailableException e) {
            return sourceUnavailable(e);
        } catch (ModelOutputException e) {
            return modelOutputFailure(e);
        } catch (ModelUnavailableException e) {
            return modelUnavailable(e);
        } catch (FeatureLevelMissingException e) {
            return BacklogResponses.featureLevelMissing(e.getMessage());
        } catch (Exception e) {
            log.error("Error processing request", e);
            return BacklogResponses.internalError(BacklogResponses.CREATION_FAILED);
        }
    }

    private ResponseEntity<Map<String, Object>> previewResponse(List<Epic> epics, String projectKey, boolean pipeline) {
        ResponseEntity<Map<String, Object>> response =
                BacklogResponses.preview(epics, describeTracker.featureWarnings(epics, projectKey));
        return pipeline ? BacklogResponses.withPipeline(response, epics) : response;
    }

    private static ResponseEntity<Map<String, Object>> creationResponse(BacklogCreationResult result, boolean pipeline) {
        ResponseEntity<Map<String, Object>> response = BacklogResponses.creation(result);
        return pipeline ? BacklogResponses.withPipeline(response, result.epics()) : response;
    }

    /** 502 : le modele n'a pas produit de resultat exploitable (sortie invalide apres relances, ou tronquee). */
    private static ResponseEntity<Map<String, Object>> modelOutputFailure(ModelOutputException e) {
        log.warn("Unusable model output: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        return BacklogResponses.modelOutputFailure(e.isTruncated());
    }

    static final String RATE_LIMITED = BacklogResponses.RATE_LIMITED;
    static final String CONTEXT_NEEDS_CREATE_TASKS = "context and contextDocumentId are only accepted with createTasks";
    static final String OVERLOADED = BacklogResponses.OVERLOADED;

    /**
     * Fournisseur du modele indisponible apres les nouveaux essais : 503 pour un quota ou une limite de debit (429) et
     * pour une surcharge temporaire (503, 529), avec l'en-tete {@code Retry-After} et le champ
     * {@code retryAfterSeconds} seulement quand le fournisseur a annonce un delai ; 502 pour tout autre echec
     * (injoignable, cle refusee, delai depasse...).
     */
    static ResponseEntity<Map<String, Object>> modelUnavailable(ModelUnavailableException e) {
        log.warn("LLM provider unavailable: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        boolean rateLimited = e instanceof ModelRateLimitedException;
        if (!rateLimited && !(e instanceof ModelOverloadedException)) {
            return BacklogResponses.modelUnavailable();
        }
        return BacklogResponses.modelBusy(rateLimited, e.retryAfter());
    }

    /** 404 : document absent ou non visible pour le compte ; message sur ecrit par l'adaptateur (ni URL ni corps brut). */
    private static ResponseEntity<Map<String, Object>> documentNotFound(DocumentNotFoundException e) {
        log.warn("Document not found: {}", e.getMessage());
        return BacklogResponses.documentNotFound(e.getMessage());
    }

    /** 502 : la source documentaire n'a pas pu repondre (identifiants, droits, indisponibilite, delai), message sur. */
    private static ResponseEntity<Map<String, Object>> sourceUnavailable(DocumentSourceUnavailableException e) {
        log.warn("Document source unavailable: {}", e.getMessage());
        return BacklogResponses.sourceUnavailable(e.getMessage());
    }

    /** 400 : entree refusee par le cas d'usage ; son message, ecrit par l'application, est renvoye tel quel. */
    private static ResponseEntity<Map<String, Object>> invalidRequest(InvalidRequestException e) {
        return BacklogResponses.invalidRequest(e.getMessage());
    }

    /** 413 : texte fourni au-dela de refinr.document.max-content-chars ; le message ne cite que la limite. */
    private static ResponseEntity<Map<String, Object>> contentTooLarge(ContentTooLargeException e) {
        log.warn("Provided text rejected: {}", e.getMessage());
        return BacklogResponses.contentTooLarge(e.getMessage());
    }

    /** Absent ou vide : projet par defaut ; sinon au format du gestionnaire de tickets actif (ex. PROJ pour Jira). */
    private boolean isValidProjectKey(String projectKey) {
        return projectKey == null || projectKey.isBlank() || describeTracker.acceptsProjectId(projectKey);
    }

    private static ResponseEntity<Map<String, Object>> invalidProjectKey(String projectKey) {
        return ResponseEntity.badRequest().body(ErrorResponses.error(AnalysisRequests.INVALID_PROJECT_KEY));
    }

    @PostMapping("/analyze-preview")
    public ResponseEntity<Map<String, Object>> analyzePreview(@RequestBody Map<String, String> request) {
        // Projet cible optionnel : l'apercu ne cree rien, il lit seulement le niveau Feature de ce projet (avertissements)
        String projectKey = request.get(AnalysisRequests.PROJECT_KEY);
        if (!isValidProjectKey(projectKey)) {
            return invalidProjectKey(projectKey);
        }
        Language language;
        try {
            language = language(request.get(LANGUAGE));
        } catch (IllegalArgumentException e) {
            return invalidLanguage();
        }
        boolean pipeline;
        try {
            pipeline = AnalysisRequests.flag(request, AnalysisRequests.PIPELINE);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponses.error(e.getMessage()));
        }
        try {
            if (AnalysisRequests.isTextInput(request)) {
                // Pas de documentUrl : le texte n'a pas d'adresse dans la source active
                AnalysisRequests.logTextInput(log, "Preview", request);
                String title = request.get(AnalysisRequests.TITLE);
                String content = request.get(AnalysisRequests.CONTENT);
                WithContext<List<Epic>> epics = previewBacklog.previewText(title, content, language, pipeline,
                        AnalysisRequests.context(request), projectKey, AnalysisProgress.NONE);
                return BacklogResponses.withContext(previewResponse(epics.value(), projectKey, pipeline), epics.context());
            }
            String documentId = AnalysisRequests.documentId(request);
            log.info("Preview request for {} document: {}", describeSource.displayName(), documentId);
            WithContext<List<Epic>> epics = previewBacklog.preview(documentId, language, pipeline,
                    AnalysisRequests.context(request), projectKey, AnalysisProgress.NONE);
            return BacklogResponses.withContext(BacklogResponses.withDocumentUrl(previewResponse(epics.value(), projectKey,
                    pipeline), describeSource.documentUrl(documentId)), epics.context());

        } catch (InvalidRequestException e) {
            return invalidRequest(e);
        } catch (ContentTooLargeException e) {
            return contentTooLarge(e);
        } catch (DocumentNotFoundException e) {
            return documentNotFound(e);
        } catch (DocumentSourceUnavailableException e) {
            return sourceUnavailable(e);
        } catch (ModelOutputException e) {
            return modelOutputFailure(e);
        } catch (ModelUnavailableException e) {
            return modelUnavailable(e);
        } catch (Exception e) {
            log.error("Error generating preview", e);
            return BacklogResponses.internalError(BacklogResponses.PREVIEW_FAILED);
        }
    }

    @PostMapping("/create-jira")
    public ResponseEntity<Map<String, Object>> createJira(
            @RequestBody List<EpicDto> request,
            // Projet cible optionnel (sinon le projet par defaut du gestionnaire actif), meme validation que analyze-and-create
            @RequestParam(required = false) String projectKey,
            // Langue des verdicts optionnelle (ex. celle de l'apercu) ; sinon la propriete, puis la detection par story
            @RequestParam(required = false) String language,
            // Contexte projet (X1) : page de la source documentaire (le corps est la liste des epics)
            @RequestParam(required = false) String contextDocumentId) {
        if (!isValidProjectKey(projectKey)) {
            return invalidProjectKey(projectKey);
        }
        Language verdictLanguage;
        try {
            verdictLanguage = language(language);
        } catch (IllegalArgumentException e) {
            return invalidLanguage();
        }
        log.info("Received request to create {} epics in {}", request.size(), describeTracker.displayName());

        try {
            List<String> shapeWarnings = new java.util.ArrayList<>();
            List<Epic> epics = RestBacklogMapper.toDomain(request, shapeWarnings);
            // Verdict recalcule sur le contenu final : aiReady / aiReadyReason envoyes par le client sont ignores
            WithContext<BacklogCreationResult> result = createEditedBacklog.create(epics, projectKey, verdictLanguage,
                    new ContextRequest(null, contextDocumentId));
            return BacklogResponses.withContext(BacklogResponses.creation(result.value().withWarnings(shapeWarnings)),
                    result.context());

        } catch (InvalidRequestException e) {
            // Cas de test du client invalides : message ecrit par l'application, sans le contenu
            return invalidRequest(e);
        } catch (ContentTooLargeException e) {
            return contentTooLarge(e);
        } catch (DocumentNotFoundException e) {
            return documentNotFound(e);
        } catch (DocumentSourceUnavailableException e) {
            return sourceUnavailable(e);
        } catch (FeatureLevelMissingException e) {
            return BacklogResponses.featureLevelMissing(e.getMessage());
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
        Language language;
        try {
            language = language(request.get(LANGUAGE));
        } catch (IllegalArgumentException e) {
            return invalidLanguage();
        }

        boolean createTasks;
        try {
            createTasks = AnalysisRequests.flag(request, AnalysisRequests.CREATE_TASKS);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponses.error(e.getMessage()));
        }

        log.info("Reassessment request for {}", issueKey);
        try {
            ContextRequest context = AnalysisRequests.context(request);
            WithContext<Readiness> reassessed = reassessStory.reassess(issueKey, precision, language, context);
            Readiness verdict = reassessed.value();
            Map<String, Object> body = new LinkedHashMap<>(Map.of(
                    "success", true,
                    "issueKey", issueKey,
                    "aiReady", verdict.aiReady(),
                    "aiReadyReason", verdict.aiReadyReason() != null ? verdict.aiReadyReason() : "",
                    "aiReadySource", verdict.source().id()
            ));
            if (createTasks) {
                putTasks(body, storyTasks.afterVerdict(issueKey, precision, verdict, language, context).value());
            }
            BacklogResponses.putContext(body, reassessed.context());
            return ResponseEntity.ok(body);
        } catch (StoryNotAwaitingValidationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponses.error(e.getMessage()));
        } catch (InvalidRequestException e) {
            return invalidRequest(e);
        } catch (ContentTooLargeException e) {
            return contentTooLarge(e);
        } catch (DocumentNotFoundException e) {
            return documentNotFound(e);
        } catch (DocumentSourceUnavailableException e) {
            return sourceUnavailable(e);
        } catch (ModelOutputException e) {
            return modelOutputFailure(e);
        } catch (ModelUnavailableException e) {
            return modelUnavailable(e);
        } catch (Exception e) {
            log.error("Error reassessing story {}", issueKey, e);
            return ResponseEntity.internalServerError().body(ErrorResponses.error("Failed to reassess the story"));
        }
    }

    /**
     * Validation humaine directe d'une story en attente de validation : marquee {@code ai-ready} sans appel au modele
     * ({@code aiReadySource: "human"}), ligne de verdict ajoutee au ticket. 409 si la story n'attend pas de validation.
     */
    @PostMapping("/validate-story")
    public ResponseEntity<Map<String, Object>> validateStory(@RequestBody Map<String, String> request) {
        String issueKey = request.get("issueKey");
        if (issueKey == null || issueKey.isBlank()) {
            return ResponseEntity.badRequest().body(ErrorResponses.error("issueKey is required"));
        }
        boolean createTasks;
        Language language;
        try {
            createTasks = AnalysisRequests.flag(request, AnalysisRequests.CREATE_TASKS);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponses.error(e.getMessage()));
        }
        try {
            // Langue de la raison ecrite (et des sous-taches avec createTasks) : requete, puis propriete, puis detection
            language = language(request.get(LANGUAGE));
        } catch (IllegalArgumentException e) {
            return invalidLanguage();
        }
        ContextRequest context = AnalysisRequests.context(request);
        if (!context.isEmpty() && !createTasks) {
            // Sans createTasks, aucun appel au modele : un contexte n'aurait aucun effet
            return ResponseEntity.badRequest().body(ErrorResponses.error(CONTEXT_NEEDS_CREATE_TASKS));
        }
        log.info("Human validation request for {}", issueKey);
        try {
            if (createTasks) {
                // Controles locaux du contexte (400, 413) avant d'ecrire la validation dans le ticket
                storyTasks.checkContext(context);
            }
            Readiness verdict = validateStory.validate(issueKey, language);
            Map<String, Object> body = new LinkedHashMap<>(Map.of(
                    "success", true,
                    "issueKey", issueKey,
                    "aiReady", verdict.aiReady(),
                    "aiReadyReason", verdict.aiReadyReason(),
                    "aiReadySource", verdict.source().id()
            ));
            if (createTasks) {
                WithContext<StoryTasksResult> tasks = storyTasks.afterVerdict(issueKey, null, verdict, language, context);
                putTasks(body, tasks.value());
                BacklogResponses.putContext(body, tasks.context());
            }
            return ResponseEntity.ok(body);
        } catch (StoryNotAwaitingValidationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponses.error(e.getMessage()));
        } catch (InvalidRequestException e) {
            return invalidRequest(e);
        } catch (ContentTooLargeException e) {
            return contentTooLarge(e);
        } catch (Exception e) {
            log.error("Error validating story {}", issueKey, e);
            return ResponseEntity.internalServerError().body(ErrorResponses.error("Failed to validate the story"));
        }
    }

    /**
     * Chaine C1 : sous-taches d'une story {@code ai-ready} qui n'en a aucune, generees puis creees dans son projet.
     * Story qui a deja des sous-taches : rien n'est fait, {@code tasksSkipped} en donne le nombre (une creation
     * precedente a pu etre incomplete). 409 si la story n'est pas prete ou si une autre demande cree deja ses taches.
     */
    @PostMapping("/story-tasks")
    public ResponseEntity<Map<String, Object>> storyTasks(@RequestBody Map<String, String> request) {
        String issueKey = request.get("issueKey");
        if (issueKey == null || issueKey.isBlank()) {
            return ResponseEntity.badRequest().body(ErrorResponses.error("issueKey is required"));
        }
        Language language;
        try {
            language = language(request.get(LANGUAGE));
        } catch (IllegalArgumentException e) {
            return invalidLanguage();
        }
        log.info("Sub-task request for {}", issueKey);
        try {
            WithContext<StoryTasksResult> generated = storyTasks.generate(issueKey, null, language,
                    AnalysisRequests.context(request));
            StoryTasksResult result = generated.value();
            Map<String, Object> body = new LinkedHashMap<>();
            HttpStatus status = HttpStatus.OK;
            if (result.existingSubtasks() != null) {
                body.put("success", true);
                // Sous-taches deja la : seuls les cas de test ont pu etre ecrits (reprise apres un testCasesError)
                body.put("message", result.testCases() != null
                        ? "Sub-tasks already present; " + result.testCases() + " test cases written" : "Nothing created");
            } else if (result.failed().isEmpty()) {
                body.put("success", true);
                body.put("message", "Successfully created " + result.created().size() + " sub-tasks");
            } else if (!result.created().isEmpty()) {
                status = HttpStatus.MULTI_STATUS;
                body.put("success", false);
                body.put("message", "Partially created: " + result.created().size() + " sub-tasks created, "
                        + result.failed().size() + " not created");
            } else {
                status = HttpStatus.BAD_GATEWAY;
                body.putAll(ErrorResponses.error("No sub-task could be created"));
            }
            body.put("issueKey", issueKey);
            putTasks(body, result);
            BacklogResponses.putContext(body, generated.context());
            return ResponseEntity.status(status).body(body);
        } catch (StoryNotReadyException | StoryTasksInProgressException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponses.error(e.getMessage()));
        } catch (InvalidRequestException e) {
            return invalidRequest(e);
        } catch (ContentTooLargeException e) {
            return contentTooLarge(e);
        } catch (DocumentNotFoundException e) {
            return documentNotFound(e);
        } catch (DocumentSourceUnavailableException e) {
            return sourceUnavailable(e);
        } catch (ModelOutputException e) {
            return modelOutputFailure(e);
        } catch (ModelUnavailableException e) {
            return modelUnavailable(e);
        } catch (Exception e) {
            log.error("Error creating the sub-tasks of {}", issueKey, e);
            return ResponseEntity.internalServerError().body(ErrorResponses.error("Failed to create the sub-tasks"));
        }
    }

    /** Champs des sous-taches : {@code tasks} (created / failed), {@code tasksSkipped} ou {@code tasksError}. */
    private static void putTasks(Map<String, Object> body, StoryTasksResult result) {
        if (result.notReady()) {
            return;
        }
        if (result.error() != null) {
            body.put("tasksError", result.error());
        } else if (result.existingSubtasks() != null) {
            body.put("tasksSkipped", result.skippedMessage());
        } else {
            body.put("tasks", Map.of("created", result.created(), "failed", result.failed()));
        }
        // Chaine C2 : cas de test ecrits dans la description, deja presents, ou raison de leur absence
        if (result.testCases() != null) {
            body.put("testCases", Map.of("written", result.testCases()));
        } else if (result.testCasesSkipped()) {
            body.put("testCasesSkipped", "the description already has a test cases section");
        } else if (result.testCasesError() != null) {
            body.put("testCasesError", result.testCasesError());
        }
    }

    /** Langue demandee, ou null si absente ; IllegalArgumentException si ce n'est pas un code ISO 639-1 connu. */
    static Language language(String raw) {
        return AnalysisRequests.language(raw);
    }

    /** 400 qui nomme le parametre, sans recopier la valeur recue. */
    private static ResponseEntity<Map<String, Object>> invalidLanguage() {
        return ResponseEntity.badRequest().body(ErrorResponses.error(INVALID_LANGUAGE));
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
        } catch (DocumentSourceUnavailableException e) {
            return sourceUnavailable(e);
        } catch (DocumentNotFoundException e) {
            // Une recherche ne vise pas un document : un « introuvable » de la source y est une indisponibilite (502)
            log.warn("Document source search failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponses.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error searching {} documents", describeSource.displayName(), e);
            return ResponseEntity.internalServerError().body(ErrorResponses.error("Failed to search the documents"));
        }
    }
}
