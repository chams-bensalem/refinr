package fr.heliocraft.refinr.analyzer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.heliocraft.refinr.model.Epic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class DocumentAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(DocumentAnalyzer.class);

    private final WebClient ollamaClient;
    private final ObjectMapper objectMapper;
    private final String ollamaModel;

    public DocumentAnalyzer(
            @Value("${ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${ollama.model:llama3.1:8b}") String ollamaModel) {
        
        this.objectMapper = new ObjectMapper();
        this.ollamaModel = ollamaModel;
        
        log.info("🤖 Initializing Ollama analyzer with model: {}", ollamaModel);
        
        this.ollamaClient = WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        
        log.info("✅ Using Ollama (local, FREE)");
    }

    public List<Epic> analyzeDocument(String documentContent) {
        log.info("Analyzing document to generate Scrum structure...");

        String prompt = buildAnalysisPrompt(documentContent);
        
        try {
            String aiResponse = callOllamaAPI(prompt);
            List<Epic> epics = parseAIResponse(aiResponse);
            
            log.info("Analysis complete. Generated {} epics", epics.size());
            return epics;
        } catch (Exception e) {
            log.error("Error analyzing document", e);
            throw new RuntimeException("Failed to analyze document: " + e.getMessage(), e);
        }
    }

    private String buildAnalysisPrompt(String documentContent) {
        return String.format("""
            Tu es un expert Scrum Master et Product Owner. Ton rôle est d'analyser un cahier des charges technique
            et de le transformer en une structure Scrum complète avec Epics, User Stories et Tasks.
            
            RÈGLES IMPORTANTES:
            1. Créer des Epics de haut niveau pour les grandes fonctionnalités
            2. Chaque Epic contient plusieurs User Stories au format: "As a <role>, I want <goal>, So that <benefit>"
            3. Chaque Story a des Acceptance Criteria clairs et testables
            4. Story Points en Fibonacci (1, 2, 3, 5, 8, 13, 21)
            5. Priorités: High, Medium, Low
            6. Labels techniques: backend, frontend, devops, database, api, etc.
            7. Détecter les dépendances entre stories
            8. Créer des sous-tâches techniques pour chaque story
            
            DOCUMENT À ANALYSER:
            %s
            
            RÉPONDS UNIQUEMENT avec un JSON valide au format suivant (pas de markdown, pas de backticks):
            [
              {
                "name": "Epic 1: Module X",
                "description": "Description de l'epic",
                "priority": "High",
                "labels": ["backend", "api"],
                "stories": [
                  {
                    "asA": "Developer",
                    "iWant": "Connect GitLab account",
                    "soThat": "The system can index my code",
                    "title": "GitLab Connection",
                    "description": "Implement GitLab OAuth connection",
                    "acceptanceCriteria": [
                      "User can enter GitLab token",
                      "Connection is validated",
                      "User sees their projects"
                    ],
                    "storyPoints": 5,
                    "priority": "High",
                    "labels": ["backend", "api", "security"],
                    "dependencies": [],
                    "tasks": [
                      {
                        "title": "Create GitLabConnector service",
                        "description": "Implement Spring service with OAuth logic",
                        "estimatedHours": 4,
                        "labels": ["backend"]
                      }
                    ]
                  }
                ]
              }
            ]
            """, documentContent);
    }

    private String callOllamaAPI(String prompt) {
        log.info("🦙 Calling Ollama API...");
        
        Map<String, Object> requestBody = Map.of(
                "model", ollamaModel,
                "prompt", prompt,
                "stream", false,
                "options", Map.of(
                        "temperature", 0.7,
                        "num_predict", 4096
                )
        );

        try {
            Map<String, Object> response = ollamaClient.post()
                    .uri("/api/generate")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String text = (String) response.get("response");
            log.debug("Ollama response received ({} chars)", text != null ? text.length() : 0);
            return text;
        } catch (Exception e) {
            log.error("Failed to call Ollama API - Make sure Ollama is running (ollama serve)", e);
            throw new RuntimeException("Ollama API call failed: " + e.getMessage(), e);
        }
    }

    private List<Epic> parseAIResponse(String jsonResponse) {
        try {
            String cleaned = jsonResponse.trim();
            
            // Enlever les backticks markdown si présents
            if (cleaned.contains("```json")) {
                int start = cleaned.indexOf("```json") + 7;
                int end = cleaned.lastIndexOf("```");
                if (start > 0 && end > start) {
                    cleaned = cleaned.substring(start, end).trim();
                }
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("```json\\n?", "")
                        .replaceFirst("```$", "")
                        .trim();
            }
            
            // Trouver le JSON array
            int startIdx = cleaned.indexOf('[');
            int endIdx = cleaned.lastIndexOf(']');
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                cleaned = cleaned.substring(startIdx, endIdx + 1);
            }

            // Nettoyer les dependencies vides (objet -> tableau)
            cleaned = cleaned.replaceAll("\"dependencies\"\\s*:\\s*\\{\\s*\\}", "\"dependencies\": []");

            List<Epic> epics = objectMapper.readValue(cleaned, new TypeReference<List<Epic>>() {});
            
            log.info("Successfully parsed {} epics from AI response", epics.size());
            return epics;
        } catch (Exception e) {
            log.error("Error parsing AI response", e);
            log.error("Raw response was: {}", jsonResponse);
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage(), e);
        }
    }
}
