package fr.heliocraft.refinr.api;

import fr.heliocraft.refinr.adapters.in.rest.RestBacklogMapper;
import fr.heliocraft.refinr.adapters.in.rest.dto.EpicDto;
import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.in.ReassessStory;
import fr.heliocraft.refinr.application.port.in.SearchDocuments;
import fr.heliocraft.refinr.application.port.in.StoryNotAwaitingValidationException;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mvp")
@CrossOrigin(origins = "*")
public class MvpController {

    private static final Logger log = LoggerFactory.getLogger(MvpController.class);

    @Value("${jira.base-url}")
    private String jiraBaseUrl;

    @Value("${jira.username}")
    private String jiraEmail;

    @Value("${jira.token}")
    private String jiraApiToken;

    private static final java.util.regex.Pattern CLE_PROJET = java.util.regex.Pattern.compile("^[A-Z][A-Z0-9]{1,9}$");

    private final AnalyzeDocument analyzeDocument;
    private final PreviewBacklog previewBacklog;
    private final CreateBacklog createBacklog;
    private final ReassessStory reassessStory;
    private final SearchDocuments searchDocuments;

    public MvpController(
            AnalyzeDocument analyzeDocument,
            PreviewBacklog previewBacklog,
            CreateBacklog createBacklog,
            ReassessStory reassessStory,
            SearchDocuments searchDocuments) {
        this.analyzeDocument = analyzeDocument;
        this.previewBacklog = previewBacklog;
        this.createBacklog = createBacklog;
        this.reassessStory = reassessStory;
        this.searchDocuments = searchDocuments;
    }

    @PostMapping("/analyze-and-create")
    public ResponseEntity<Map<String, Object>> analyzeAndCreate(@RequestBody Map<String, String> request) {
        String confluencePageId = request.get("confluencePageId");
        // Projet Jira cible optionnel (sinon jira.project-key) : permet a un outil appelant de remplir un projet qu'il vient de creer.
        String projectKey = request.get("projectKey");
        if (projectKey != null && !projectKey.isBlank() && !CLE_PROJET.matcher(projectKey).matches()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Invalid projectKey: " + projectKey));
        }
        
        log.info("Received request to analyze Confluence page: {}", confluencePageId);

        try {
            List<Epic> epics = analyzeDocument.analyzeAndCreate(confluencePageId, projectKey);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Successfully created " + epics.size() + " epics with stories and tasks",
                    "epics", RestBacklogMapper.toDto(epics)
            ));
            
        } catch (Exception e) {
            log.error("Error processing request", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
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
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/create-jira")
    public ResponseEntity<Map<String, Object>> createJira(@RequestBody List<EpicDto> request) {
        log.info("Received request to create {} epics in Jira", request.size());

        try {
            List<Epic> epics = RestBacklogMapper.toDomain(request);
            createBacklog.create(epics, null);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Successfully created " + epics.size() + " epics in Jira"
            ));
            
        } catch (Exception e) {
            log.error("Error creating Jira tickets", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
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
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error reassessing story {}", issueKey, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", String.valueOf(e.getMessage())
            ));
        }
    }

    @GetMapping("/confluence/search")
    public ResponseEntity<Map<String, Object>> searchConfluencePages(
            @RequestParam String spaceKey,
            @RequestParam String title) {
        
        log.info("Searching Confluence pages: space={}, title={}", spaceKey, title);

        try {
            Map<String, Object> results = searchDocuments.search(spaceKey, title);
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            log.error("Error searching Confluence", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "MVP Doc-to-Jira is running"
        ));
    }

    @PostMapping("/test-issue-types")
    public ResponseEntity<String> testIssueTypes() {
        try {
            log.info("Testing Jira issue types");

            // Appel API Jira pour lister les types disponibles
            Map<String, Object> response = WebClient.builder()
                .baseUrl(jiraBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString(
                        (jiraEmail + ":" + jiraApiToken).getBytes()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .get()
                .uri("/rest/api/3/issue/createmeta?projectKeys=PROJ&expand=projects.issuetypes")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            return ResponseEntity.ok(response.toString());

        } catch (Exception e) {
            log.error("Error testing issue types", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}
