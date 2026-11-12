package fr.heliocraft.refinr.adapters.out.confluence;

import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.HealthCheckable;
import fr.heliocraft.refinr.domain.model.ComponentHealth;
import fr.heliocraft.refinr.domain.model.DocumentSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Order(1)
public class ConfluenceDocumentSource implements DocumentSourcePort, HealthCheckable {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceDocumentSource.class);

    // Cle d'espace Confluence : alphanumerique, ou espace personnel "~" + identifiant de compte (ex. ~557058:uuid)
    private static final Pattern SPACE_KEY = Pattern.compile("^~?[A-Za-z0-9][A-Za-z0-9:_-]{0,254}$");

    private final WebClient webClient;

    @Autowired
    public ConfluenceDocumentSource(
            @Value("${confluence.base-url}") String baseUrl,
            @Value("${confluence.username}") String username,
            @Value("${confluence.token}") String token) {
        
        String auth = username + ":" + token;
        String encodedAuth = Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // Constructor for testing with custom WebClient
    ConfluenceDocumentSource(WebClient webClient, String username, String token) {
        this.webClient = webClient;
    }

    @Retryable(
        includes = {WebClientResponseException.TooManyRequests.class,
                    WebClientResponseException.InternalServerError.class},
        maxRetries = 2,
        delay = 1000,
        multiplier = 2
    )
    @Override
    public String getDocumentContent(String pageId) {
        log.info("Reading Confluence page: {}", pageId);

        try {
            Map<String, Object> response = webClient.get()
                    .uri("/wiki/rest/api/content/{pageId}?expand=body.storage", pageId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            Map<String, Object> body = (Map<String, Object>) response.get("body");
            Map<String, Object> storage = (Map<String, Object>) body.get("storage");
            String content = (String) storage.get("value");

            log.info("Page content retrieved successfully");
            return content;
        } catch (Exception e) {
            log.error("Error reading Confluence page", e);
            throw new RuntimeException("Failed to read Confluence page: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DocumentSummary> searchDocuments(String spaceKey, String title) {
        // Garde defensive (le cas d'usage valide deja l'entree) : jamais de CQL construit sur une entree invalide
        String cql = searchCql(spaceKey, title);
        log.info("Searching pages in space {} with title: {}", spaceKey, title);

        try {

            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/wiki/rest/api/content/search")
                            .queryParam("cql", cql)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            List<DocumentSummary> results = toSummaries(response);
            log.info("Search completed: {} result(s)", results.size());
            return results;
        } catch (Exception e) {
            log.error("Error searching Confluence pages", e);
            throw new RuntimeException("Failed to search Confluence pages: " + e.getMessage(), e);
        }
    }

    /**
     * Requete CQL de recherche : cle d'espace verifiee (liste blanche) puis citee, titre echappe dans sa chaine CQL
     * ({@code \\} et {@code "}) pour qu'aucune entree ne puisse sortir de la chaine et ajouter des clauses.
     */
    static String searchCql(String spaceKey, String title) {
        if (spaceKey == null || !SPACE_KEY.matcher(spaceKey).matches()) {
            throw new IllegalArgumentException("Invalid spaceKey");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        return "space=\"" + spaceKey + "\" AND title~\"" + escapeCqlString(title.strip()) + "\"";
    }

    private static String escapeCqlString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Resultats Confluence -> resumes ; lien absolu = _links.base de la reponse + _links.webui du resultat. */
    private static List<DocumentSummary> toSummaries(Map<?, ?> response) {
        if (response == null || !(response.get("results") instanceof List<?> results)) {
            return List.of();
        }
        String base = response.get("_links") instanceof Map<?, ?> links && links.get("base") instanceof String b ? b : "";
        List<DocumentSummary> summaries = new ArrayList<>();
        for (Object item : results) {
            if (item instanceof Map<?, ?> result) {
                String webui = result.get("_links") instanceof Map<?, ?> links
                        && links.get("webui") instanceof String w ? w : null;
                summaries.add(new DocumentSummary(
                        String.valueOf(result.get("id")),
                        result.get("title") instanceof String t ? t : "",
                        webui != null ? base + webui : null));
            }
        }
        return summaries;
    }

    /** Lecture minimale de l'API REST Confluence (sous /wiki), avec un delai court. */
    @Override
    public List<ComponentHealth> health() {
        try {
            webClient.get()
                    .uri("/wiki/rest/api/content?limit=1")
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .block();
            return List.of(ComponentHealth.up("confluence", Map.of()));
        } catch (RuntimeException e) {
            log.warn("Confluence health check failed: {}", healthCause(e));
            return List.of(ComponentHealth.down("confluence", healthCause(e), Map.of()));
        }
    }

    /** Cause courte et sure d'un echec : statut HTTP, delai depasse ou service injoignable (jamais d'URL ni de corps). */
    private static String healthCause(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof WebClientResponseException response) {
                return "HTTP " + response.getStatusCode().value();
            }
            if (t instanceof java.util.concurrent.TimeoutException) {
                return "timeout";
            }
            if (t instanceof WebClientRequestException) {
                return "unreachable";
            }
        }
        return error.getClass().getSimpleName();
    }
}
