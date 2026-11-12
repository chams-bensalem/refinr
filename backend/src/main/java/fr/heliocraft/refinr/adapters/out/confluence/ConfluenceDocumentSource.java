package fr.heliocraft.refinr.adapters.out.confluence;

import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.domain.model.DocumentSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class ConfluenceDocumentSource implements DocumentSourcePort {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceDocumentSource.class);

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
        log.info("Searching pages in space {} with title: {}", spaceKey, title);

        try {
            String cql = String.format("space=%s AND title~\"%s\"", spaceKey, title);

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
}
