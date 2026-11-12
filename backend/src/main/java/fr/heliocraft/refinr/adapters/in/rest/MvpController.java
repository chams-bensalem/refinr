package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.adapters.in.rest.dto.EpicDto;
import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.CreateEditedBacklog;
import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.in.ReassessStory;
import fr.heliocraft.refinr.application.port.in.SearchDocuments;
import fr.heliocraft.refinr.application.port.in.StoryNotAwaitingValidationException;
import fr.heliocraft.refinr.domain.model.DocumentSummary;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mvp")
@CrossOrigin(origins = "*")
public class MvpController {

    private static final Logger log = LoggerFactory.getLogger(MvpController.class);

    private static final java.util.regex.Pattern CLE_PROJET = java.util.regex.Pattern.compile("^[A-Z][A-Z0-9]{1,9}$");

    private final AnalyzeDocument analyzeDocument;
    private final PreviewBacklog previewBacklog;
    private final CreateEditedBacklog createEditedBacklog;
    private final ReassessStory reassessStory;
    private final SearchDocuments searchDocuments;

    public MvpController(
            AnalyzeDocument analyzeDocument,
            PreviewBacklog previewBacklog,
            CreateEditedBacklog createEditedBacklog,
            ReassessStory reassessStory,
            SearchDocuments searchDocuments) {
        this.analyzeDocument = analyzeDocument;
        this.previewBacklog = previewBacklog;
        this.createEditedBacklog = createEditedBacklog;
        this.reassessStory = reassessStory;
        this.searchDocuments = searchDocuments;
    }

    @PostMapping("/analyze-and-create")
    public ResponseEntity<Map<String, Object>> analyzeAndCreate(@RequestBody Map<String, String> request) {
        String confluencePageId = request.get("confluencePageId");
        // Projet Jira cible optionnel (sinon jira.project-key) : permet a un outil appelant de remplir un projet qu'il vient de creer.
        String projectKey = request.get("projectKey");
        if (!isValidProjectKey(projectKey)) {
            return invalidProjectKey(projectKey);
        }
        
        log.info("Received request to analyze Confluence page: {}", confluencePageId);

        try {
            return creationResponse(analyzeDocument.analyzeAndCreate(confluencePageId, projectKey));

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

    /** Absent ou vide : projet par defaut ; sinon une cle de projet valide (ex. PROJ, DEMO2). */
    private static boolean isValidProjectKey(String projectKey) {
        return projectKey == null || projectKey.isBlank() || CLE_PROJET.matcher(projectKey).matches();
    }

    private static ResponseEntity<Map<String, Object>> invalidProjectKey(String projectKey) {
        return ResponseEntity.badRequest().body(ErrorResponses.error("Invalid projectKey: " + projectKey));
    }

    @PostMapping("/analyze-preview")
    public ResponseEntity<Map<String, Object>> analyzePreview(@RequestBody Map<String, String> request) {
        String confluencePageId = request.get("confluencePageId");
        
        log.info("Preview request for Confluence page: {}", confluencePageId);

        try {
            List<Epic> epics = previewBacklog.preview(confluencePageId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Preview generated successfully",
                    "epics", RestBacklogMapper.toDto(epics)
            ));
            
        } catch (Exception e) {
            log.error("Error generating preview", e);
            return ResponseEntity.internalServerError().body(ErrorResponses.error("Failed to generate the preview"));
        }
    }

    @PostMapping("/create-jira")
    public ResponseEntity<Map<String, Object>> createJira(
            @RequestBody List<EpicDto> request,
            // Projet cible optionnel (sinon jira.project-key), meme validation que analyze-and-create
            @RequestParam(required = false) String projectKey) {
        if (!isValidProjectKey(projectKey)) {
            return invalidProjectKey(projectKey);
        }
        log.info("Received request to create {} epics in Jira", request.size());

        try {
            List<Epic> epics = RestBacklogMapper.toDomain(request);
            // Verdict recalcule sur le contenu final : aiReady / aiReadyReason envoyes par le client sont ignores
            return creationResponse(createEditedBacklog.create(epics, projectKey));

        } catch (Exception e) {
            log.error("Error creating Jira tickets", e);
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
        } catch (Exception e) {
            log.error("Error reassessing story {}", issueKey, e);
            return ResponseEntity.internalServerError().body(ErrorResponses.error("Failed to reassess the story"));
        }
    }

    @GetMapping("/confluence/search")
    public ResponseEntity<Map<String, Object>> searchConfluencePages(
            @RequestParam String spaceKey,
            @RequestParam String title) {
        
        log.info("Searching Confluence pages: space={}, title={}", spaceKey, title);

        try {
            List<DocumentSummary> results = searchDocuments.search(spaceKey, title);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "results", results
            ));

        } catch (InvalidRequestException e) {
            // Message ecrit par le cas d'usage (sans donnee technique) : renvoye tel quel
            return ResponseEntity.badRequest().body(ErrorResponses.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error searching Confluence", e);
            return ResponseEntity.internalServerError().body(ErrorResponses.error("Failed to search the documents"));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "MVP Doc-to-Jira is running"
        ));
    }
}
