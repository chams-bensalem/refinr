package fr.heliocraft.refinr.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthCheckController {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckController.class);

    private final WebClient confluenceClient;
    private final WebClient jiraClient;
    private final WebClient geminiClient;

    @Value("${jira.project-key}")
    private String jiraProjectKey;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.provider:gemini}")
    private String aiProvider;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:qwen3:8b}")
    private String ollamaModel;

    public HealthCheckController(WebClient confluenceClient, WebClient jiraClient, WebClient geminiClient) {
        this.confluenceClient = confluenceClient;
        this.jiraClient = jiraClient;
        this.geminiClient = geminiClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("services", Map.of(
                "confluence", checkConfluence(),
                "jira", checkJira(),
                "ai", checkAi()
        ));

        boolean allUp = ((Map<String, Map<String, String>>) health.get("services")).values().stream()
                .allMatch(m -> "UP".equals(m.get("status")));

        health.put("status", allUp ? "UP" : "DEGRADED");

        return ResponseEntity.ok(health);
    }

    private Map<String, String> checkConfluence() {
        try {
            // Confluence Cloud expose son API REST sous /wiki (comme ConfluenceService)
            confluenceClient.get()
                    .uri("/wiki/rest/api/content?limit=1")
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return Map.of("status", "UP");
        } catch (Exception e) {
            log.error("Confluence health check failed", e);
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }

    private Map<String, String> checkJira() {
        try {
            jiraClient.get()
                    .uri("/rest/api/3/project/" + jiraProjectKey)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return Map.of("status", "UP");
        } catch (Exception e) {
            log.error("Jira health check failed", e);
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }

    private Map<String, String> checkAi() {
        return "ollama".equalsIgnoreCase(aiProvider) ? checkOllama() : checkGemini();
    }

    private Map<String, String> checkOllama() {
        try {
            Map<String, Object> tags = WebClient.create(ollamaBaseUrl).get()
                    .uri("/api/tags")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            List<Map<String, Object>> models = tags != null ? (List<Map<String, Object>>) tags.get("models") : List.of();
            boolean present = models != null && models.stream().anyMatch(m -> ollamaModel.equals(m.get("name")));
            return present
                    ? Map.of("status", "UP", "provider", "ollama", "model", ollamaModel)
                    : Map.of("status", "DOWN", "provider", "ollama", "error", "modele " + ollamaModel + " absent (ollama pull " + ollamaModel + ")");
        } catch (Exception e) {
            log.error("Ollama health check failed", e);
            return Map.of("status", "DOWN", "provider", "ollama", "error", String.valueOf(e.getMessage()));
        }
    }

    private Map<String, String> checkGemini() {
        try {
            // Sans cle, Gemini repond 403 meme si la cle configuree est valide
            geminiClient.get()
                    .uri("/v1beta/models/gemini-2.5-flash")
                    .header("x-goog-api-key", geminiApiKey)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            return Map.of("status", "UP", "provider", "gemini");
        } catch (Exception e) {
            log.error("Gemini health check failed", e);
            return Map.of("status", "DOWN", "provider", "gemini", "error", String.valueOf(e.getMessage()));
        }
    }
}
