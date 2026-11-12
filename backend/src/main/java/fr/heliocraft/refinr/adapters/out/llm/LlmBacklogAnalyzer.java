package fr.heliocraft.refinr.adapters.out.llm;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.service.ReadinessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Analyse par LLM : structure Epic/Story/Subtask d'un document et verdict de preparation des stories
 * (Gemini, Ollama ; verdict eventuellement confie a un autre fournisseur, ex. proxy LiteLLM).
 */
@Service
public class LlmBacklogAnalyzer implements BacklogGeneratorPort, ReadinessAssessorPort {

    private static final Logger log = LoggerFactory.getLogger(LlmBacklogAnalyzer.class);

    private final WebClient geminiClient;
    private final WebClient ollamaClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    // gemini (API Google) ou ollama (local, gratuit) : prompt et parsing sont communs
    private final String provider;
    // Modele Gemini de l'analyse (gemini.model) ; le verdict confie a Gemini utilise ai.verdict.model
    private final String geminiModel;
    private final String ollamaModel;
    // Verdict ai-ready / validation-necessaire (a l'analyse et a la reevaluation) : peut etre confie a un autre
    // fournisseur que la structure Epic/Story/Task, ex. structure par Ollama local et verdict par le pool Gemini
    // d'un proxy LiteLLM. Vide = le verdict reste dans l'appel d'analyse, par le meme fournisseur.
    private final WebClient verdictClient;
    private final String verdictProvider;
    private final String verdictModel;
    private final String verdictApiKey;
    private final long verdictRetryDelayMs;
    // Delai borne : un proxy bloque ne doit jamais suspendre la requete HTTP (pas de nouvel essai apres un delai depasse)
    private final Duration verdictTimeout;

    // Gemini renvoie des 503 (modele surcharge) et des 429 : chaque nouvel essai repasse par le proxy, qui tire
    // une autre cle du pool.
    private static final int VERDICT_ATTEMPTS = 3;

    // Defaut de gemini.model et ai.verdict.model (meme valeur dans application-example.properties et le README)
    static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";

    private static final String READINESS_RULES = """
            - aiReady = true UNIQUEMENT si un agent de code peut implémenter la story sans aucune décision humaine:
              périmètre clair, critères d'acceptation testables, pas d'ambiguïté fonctionnelle, pas de dépendance
              externe non précisée (accès, clé d'API, maquette, choix produit à trancher)
            - Sinon aiReady = false
            - aiReadyReason: une phrase qui justifie le verdict (ce qui manque encore si false)
            """;

    @Autowired
    public LlmBacklogAnalyzer(
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${gemini.model:gemini-2.5-flash}") String geminiModel,
            @Value("${ai.provider:ollama}") String provider,
            @Value("${ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${ollama.model:qwen3:8b}") String ollamaModel,
            @Value("${ai.verdict.provider:}") String verdictProvider,
            @Value("${ai.verdict.base-url:http://localhost:4000}") String verdictBaseUrl,
            @Value("${ai.verdict.model:gemini-2.5-flash}") String verdictModel,
            @Value("${ai.verdict.api-key:}") String verdictApiKey,
            @Value("${ai.verdict.retry-delay-ms:2000}") long verdictRetryDelayMs,
            @Value("${ai.verdict.timeout-seconds:90}") long verdictTimeoutSeconds) {
        this.verdictTimeout = Duration.ofSeconds(verdictTimeoutSeconds);
        this.objectMapper = JsonMapper.builderWithJackson2Defaults().build();
        this.apiKey = apiKey;
        this.provider = provider;
        this.geminiModel = geminiModel;
        this.ollamaModel = ollamaModel;
        this.verdictProvider = verdictProvider == null ? "" : verdictProvider.trim();
        this.verdictModel = verdictModel;
        this.verdictApiKey = verdictApiKey;
        this.verdictRetryDelayMs = verdictRetryDelayMs;
        this.verdictClient = WebClient.builder()
                .baseUrl(verdictBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        this.geminiClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.ollamaClient = WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        log.info("Document analyzer: {}", isOllama() ? "Ollama (" + ollamaModel + ")" : "Gemini (" + geminiModel + ")");
        log.info("Readiness verdict: {}", hasSeparateVerdict()
                ? this.verdictProvider + " (" + verdictModel + ")" : "same provider as the analysis");
    }

    // Constructor for testing with custom WebClient
    LlmBacklogAnalyzer(WebClient webClient, String apiKey) {
        this(webClient, apiKey, "gemini", "qwen3:8b");
    }

    // Constructor for testing with a chosen provider (the WebClient serves both providers)
    LlmBacklogAnalyzer(WebClient webClient, String apiKey, String provider, String ollamaModel) {
        this(webClient, apiKey, provider, ollamaModel, "", DEFAULT_GEMINI_MODEL, "");
    }

    // Constructor for testing a separate verdict provider (the WebClient serves every provider)
    LlmBacklogAnalyzer(WebClient webClient, String apiKey, String provider, String ollamaModel,
                           String verdictProvider, String verdictModel, String verdictApiKey) {
        this(webClient, apiKey, provider, DEFAULT_GEMINI_MODEL, ollamaModel, verdictProvider, verdictModel, verdictApiKey);
    }

    // Constructor for testing with chosen models (the WebClient serves every provider)
    LlmBacklogAnalyzer(WebClient webClient, String apiKey, String provider, String geminiModel, String ollamaModel,
                           String verdictProvider, String verdictModel, String verdictApiKey) {
        this.objectMapper = JsonMapper.builderWithJackson2Defaults().build();
        this.apiKey = apiKey;
        this.provider = provider;
        this.geminiModel = geminiModel;
        this.ollamaModel = ollamaModel;
        this.geminiClient = webClient;
        this.ollamaClient = webClient;
        this.verdictClient = webClient;
        this.verdictProvider = verdictProvider == null ? "" : verdictProvider.trim();
        this.verdictModel = verdictModel;
        this.verdictApiKey = verdictApiKey;
        this.verdictRetryDelayMs = 0;
        this.verdictTimeout = Duration.ofMillis(500);
    }

    private boolean isOllama() {
        return "ollama".equalsIgnoreCase(provider);
    }

    private boolean hasSeparateVerdict() {
        return !verdictProvider.isEmpty();
    }



    @Retryable(
        includes = {WebClientResponseException.TooManyRequests.class,
                    WebClientResponseException.InternalServerError.class},
        maxRetries = 2,
        delay = 1000,
        multiplier = 2
    )
    @Override
    public List<Epic> generate(String documentContent) {
        log.info("Analyzing document with {}...", isOllama() ? "Ollama" : "Gemini");

        String prompt = buildAnalysisPrompt(documentContent);

        try {
            String aiResponse = isOllama() ? callOllamaAPI(prompt) : callGeminiAPI(prompt, geminiModel);
            List<Epic> epics = parseAIResponse(aiResponse);

            log.info("Analysis complete. Generated {} epics", epics.size());
            return epics;
        } catch (Exception e) {
            log.error("Error analyzing document", e);
            throw new RuntimeException("Failed to analyze document: " + e.getMessage(), e);
        }
    }

    /** Reevalue une seule story (deja creee dans Jira) avec la precision apportee par l'utilisateur. */
    @Override
    public Readiness reassess(String summary, String description, String precision) {
        log.info("Reassessing story '{}' with the user's precision ({})", summary, isOllama() ? "Ollama" : "Gemini");
        String prompt = buildReassessmentPrompt(summary, description, precision);
        try {
            return parseReadiness(callVerdictAPI(prompt));
        } catch (Exception e) {
            log.error("Error reassessing story", e);
            throw new RuntimeException("Failed to reassess story: " + e.getMessage(), e);
        }
    }

    private String buildReassessmentPrompt(String summary, String description, String precision) {
        return """
            Tu es un expert Scrum Master et Product Owner.

            MISSION: Une User Story Jira a été jugée NON prête pour un agent de code. L'utilisateur vient
            d'apporter une précision. Réévalue la story en tenant compte de cette précision.

            PRÊTE POUR UN AGENT DE CODE:
            - aiReady = true UNIQUEMENT si un agent de code peut implémenter la story sans aucune décision humaine:
              périmètre clair, critères d'acceptation testables, pas d'ambiguïté fonctionnelle, pas de dépendance
              externe non précisée (accès, clé d'API, maquette, choix produit à trancher)
            - Sinon aiReady = false
            - aiReadyReason: une phrase qui justifie le verdict (ce qui manque encore si false)
            - La précision de l'utilisateur fait foi sur ce qu'elle tranche ; ne suppose rien d'autre

            STORY:
            Titre: """ + summary + """

            Description:
            """ + description + """

            PRÉCISION DE L'UTILISATEUR:
            """ + precision + """

            Réponds UNIQUEMENT en JSON valide (pas de markdown, pas de texte avant/après):
            {"aiReady": false, "aiReadyReason": "..."}
            """;
    }

    /** Un verdict par story (un appel chacune) ; null pour une story dont le verdict n'a pas pu etre obtenu. */
    @Override
    public List<Readiness> assess(List<Story> stories) {
        List<Readiness> verdicts = new ArrayList<>();
        for (Story story : stories) {
            log.info("Assessing readiness of story '{}'", story.getTitle());
            try {
                verdicts.add(parseReadiness(callVerdictAPI(buildVerdictPrompt(story))));
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.warn("Readiness verdict unavailable for '{}': {}", story.getTitle(), cause.getMessage());
                verdicts.add(null);
            }
        }
        return verdicts;
    }

    private String buildVerdictPrompt(Story story) {
        List<String> criteria = story.getAcceptanceCriteria() != null ? story.getAcceptanceCriteria() : List.of();
        return """
            Tu es un expert Scrum Master et Product Owner.

            MISSION: Dire si la User Story Jira ci-dessous est PRÊTE POUR UN AGENT DE CODE.
            """ + READINESS_RULES + """

            STORY:
            Titre: """ + story.getTitle() + """

            Description:
            """ + (story.getDescription() != null ? story.getDescription() : "") + """

            Critères d'acceptation:
            """ + String.join("\n", criteria) + """

            Réponds UNIQUEMENT en JSON valide (pas de markdown, pas de texte avant/après):
            {"aiReady": false, "aiReadyReason": "..."}
            """;
    }

    private String callVerdictAPI(String prompt) {
        if (!hasSeparateVerdict()) {
            return isOllama() ? callOllamaAPI(prompt) : callGeminiAPI(prompt, geminiModel);
        }
        return switch (verdictProvider.toLowerCase()) {
            case "litellm" -> callLitellmAPI(prompt);
            case "ollama" -> callOllamaAPI(prompt);
            case "gemini" -> callGeminiAPI(prompt, verdictModel);
            default -> throw new IllegalStateException("Unknown ai.verdict.provider: " + verdictProvider);
        };
    }

    /** Proxy LiteLLM local (API compatible OpenAI) : ex. pool de cles Gemini derriere un proxy unique. */
    private String callLitellmAPI(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", verdictModel,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.1
        );
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= VERDICT_ATTEMPTS; attempt++) {
            try {
                log.info("Calling LiteLLM verdict model ({}), attempt {}/{}", verdictModel, attempt, VERDICT_ATTEMPTS);
                Map<String, Object> response = verdictClient.post()
                        .uri("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + verdictApiKey)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(verdictTimeout);
                Object choices = response != null ? response.get("choices") : null;
                if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> choice
                        && choice.get("message") instanceof Map<?, ?> message
                        && message.get("content") instanceof String content && !content.isBlank()) {
                    return content;
                }
                throw new IllegalStateException("Reponse LiteLLM sans contenu");
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status != 429 && status < 500) {
                    throw e;  // requete refusee (cle maitre, modele inconnu...) : un nouvel essai n'y changerait rien
                }
                log.warn("Verdict pool busy (HTTP {}), attempt {}/{}", status, attempt, VERDICT_ATTEMPTS);
                lastFailure = e;
            } catch (WebClientRequestException e) {
                log.warn("Verdict proxy unreachable, attempt {}/{}: {}", attempt, VERDICT_ATTEMPTS, e.getMessage());
                lastFailure = e;
            }
            if (attempt < VERDICT_ATTEMPTS) {
                pause(verdictRetryDelayMs * attempt);
            }
        }
        throw lastFailure;
    }

    private static void pause(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verdict retry interrupted", e);
        }
    }

    private Readiness parseReadiness(String response) throws Exception {
        String cleaned = response.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start == -1 || end <= start) {
            throw new IllegalStateException("No JSON object in AI response");
        }
        Map<String, Object> verdict = objectMapper.readValue(
                cleaned.substring(start, end + 1), new TypeReference<Map<String, Object>>() {});
        // Seul un true explicite rend la story codable : "yes", absent ou null -> validation humaine
        return ReadinessPolicy.fromModelAnswer(verdict.get("aiReady"), verdict.get("aiReadyReason"));
    }

    private String buildAnalysisPrompt(String documentContent) {
        return """
            Tu es un expert Scrum Master et Product Owner.

            MISSION: Analyser le cahier des charges complet ci-dessous et créer une structure Scrum DÉTAILLÉE.

            ÉCHELLE (PROPORTIONNELLE AU DOCUMENT):
            - Besoin court (quelques phrases, une seule fonctionnalité): 1 seul Epic, 1 à 5 User Stories, 1 à 4 tasks par story
            - Cahier des charges complet multi-modules: un Epic par module réellement décrit, 3 à 8 User Stories par Epic, 2 à 5 tasks par story
            - Ne JAMAIS inventer de fonctionnalités, modules ou exigences absents du document: la taille de la sortie suit la taille du besoin

            RÈGLES STRICTES:
            1. Créer un Epic pour chaque module/fonctionnalité réellement mentionné dans le document (voir ÉCHELLE)
            2. Format Story: "As a <role>, I want <goal>, So that <benefit>"
            3. Priorités réalistes: High (critique), Medium (important), Low (nice-to-have)
            4. Labels techniques précis: backend, frontend, devops, database, api, security, kafka, etc.
            5. Acceptance Criteria clairs et testables (2-5 par story)

            PRÊTE POUR UN AGENT DE CODE (champs 'aiReady' et 'aiReadyReason' sur chaque Story):
            - aiReady = true UNIQUEMENT si un agent de code peut implémenter la story sans aucune décision humaine:
              périmètre clair, critères d'acceptation testables, pas d'ambiguïté fonctionnelle, pas de dépendance
              externe non précisée (accès, clé d'API, maquette, choix produit à trancher)
            - Sinon aiReady = false
            - aiReadyReason: une phrase qui justifie le verdict (ce qui manque si false)

            ESTIMATION DES STORY POINTS (Suite de Fibonacci):
            Pour chaque Story, estime la complexité et assigne des story points:
            - 1: Très simple (< 2h)
            - 2: Simple (2-4h)
            - 3: Moyen (1 jour)
            - 5: Complexe (2-3 jours)
            - 8: Très complexe (1 semaine)
            - 13: Epic-level (> 1 semaine)

            FORMAT DES ACCEPTANCE CRITERIA (Given/When/Then):
            Pour chaque Acceptance Criteria, utilise le format Given/When/Then:
            - Given: Contexte initial
            - When: Action de l'utilisateur
            - Then: Résultat attendu

            Exemple: "Given l'utilisateur est connecté, When il clique sur 'Analyser', Then le système affiche les résultats en moins de 2 secondes"

            IMPORTANT:
            - LIS TOUT LE DOCUMENT ATTENTIVEMENT
            - Crée des Epics pour TOUS les modules mentionnés
            - Utilise les informations RÉELLES du document (noms de services, technologies, architecture)
            - NE PAS utiliser d'exemples génériques
            - Sois précis et technique
            - Ajoute le champ 'estimatedStoryPoints' dans chaque Story

            DOCUMENT À ANALYSER:
            """ + documentContent + """

            Réponds UNIQUEMENT en JSON valide (pas de markdown, pas de texte avant/après):
            [
              {
                "name": "Epic: [Nom exact du module du document]",
                "description": "[Description détaillée basée sur le document]",
                "priority": "High",
                "labels": ["backend", "api", "kafka"],
                "stories": [
                  {
                    "asA": "Developer",
                    "iWant": "Set up the GitLab connector",
                    "soThat": "The system can index repositories",
                    "title": "GitLab Integration Setup",
                    "description": "Implement OAuth2 connection to GitLab",
                    "acceptanceCriteria": [
                      "Given l'utilisateur a un token GitLab valide, When il soumet le token, Then le système authentifie et affiche 'Connexion réussie'",
                      "Given un token invalide est soumis, When le système valide les permissions, Then un message d'erreur explicite est affiché",
                      "Given l'utilisateur est authentifié, When il accède à la page des repos, Then la liste des repositories accessibles s'affiche en moins de 3 secondes"
                    ],
                    "estimatedStoryPoints": 5,
                    "priority": "High",
                    "labels": ["backend", "api", "security"],
                    "dependencies": [],
                    "tasks": [
                      {
                        "title": "Create GitLabConnector service",
                        "description": "Implement Spring service with OAuth2 logic",
                        "estimatedHours": 4,
                        "labels": ["backend", "spring"]
                      },
                      {
                        "title": "Add token validation",
                        "description": "Verify token scopes and permissions",
                        "estimatedHours": 2,
                        "labels": ["backend", "security"]
                      }
                    ]
                  }
                ]
              }
            ]
            """;
    }

    private String callGeminiAPI(String prompt, String model) {
        log.info("Calling Gemini API ({})...", model);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", prompt)
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 65536,
                        "topP", 0.95
                )
        );

        try {
            Map<String, Object> response = geminiClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    // Cle en en-tete, jamais dans l'URL (qui finit dans les logs et les messages d'erreur)
                    .header("x-goog-api-key", apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String text = (String) parts.get(0).get("text");

            log.debug("Gemini response received ({} chars)", text.length());
            return text;
        } catch (Exception e) {
            log.error("Failed to call Gemini API", e);
            throw new RuntimeException("Gemini API call failed: " + e.getMessage(), e);
        }
    }

    private String callOllamaAPI(String prompt) {
        log.info("Calling Ollama API ({})...", ollamaModel);

        Map<String, Object> requestBody = Map.of(
                "model", ollamaModel,
                "prompt", prompt,
                "stream", false,
                // Pas de phase de reflexion : seule la reponse JSON est attendue
                "think", false,
                "options", Map.of("temperature", 0.2)
        );

        try {
            Map<String, Object> response = ollamaClient.post()
                    .uri("/api/generate")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            Object text = response != null ? response.get("response") : null;
            if (!(text instanceof String answer) || answer.isBlank()) {
                throw new IllegalStateException("Reponse Ollama vide");
            }
            log.debug("Ollama response received ({} chars)", answer.length());
            return answer;
        } catch (Exception e) {
            log.error("Failed to call Ollama API (is Ollama running?)", e);
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

            List<Epic> epics = GeneratedBacklogMapper.toDomain(
                    objectMapper.readValue(cleaned, new TypeReference<List<GeneratedEpic>>() {}));

            log.info("Successfully parsed {} epics from Gemini response", epics.size());
            return epics;
        } catch (Exception e) {
            log.error("Error parsing Gemini response", e);
            log.error("Raw response was: {}", jsonResponse);
            throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage(), e);
        }
    }
}
