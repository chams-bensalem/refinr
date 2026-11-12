package fr.heliocraft.refinr.confluence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class ConfluenceService {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceService.class);

    private final WebClient webClient;

    @Autowired
    public ConfluenceService(
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
    ConfluenceService(WebClient webClient, String username, String token) {
        this.webClient = webClient;
    }

    @Retryable(
        retryFor = {WebClientResponseException.TooManyRequests.class,
                    WebClientResponseException.InternalServerError.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public String getPageContent(String pageId) {
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

    public Map<String, Object> searchPages(String spaceKey, String title) {
        log.info("Searching pages in space {} with title: {}", spaceKey, title);

        try {
            String cql = String.format("space=%s AND title~\"%s\"", spaceKey, title);
            
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/wiki/rest/api/content/search")
                            .queryParam("cql", cql)
                            .queryParam("expand", "body.storage")
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            log.info("Search completed");
            return response;
        } catch (Exception e) {
            log.error("Error searching Confluence pages", e);
            throw new RuntimeException("Failed to search Confluence pages: " + e.getMessage(), e);
        }
    }
}
