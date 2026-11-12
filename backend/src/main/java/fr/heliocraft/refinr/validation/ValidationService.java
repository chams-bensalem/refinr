package fr.heliocraft.refinr.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private final WebClient confluenceClient;
    private final WebClient jiraClient;

    public ValidationService(WebClient confluenceClient, WebClient jiraClient) {
        this.confluenceClient = confluenceClient;
        this.jiraClient = jiraClient;
    }

    public void validateConfluencePageExists(String pageId) {
        log.info("Validating Confluence page exists: {}", pageId);
        try {
            confluenceClient.get()
                    .uri("/rest/api/content/" + pageId)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Confluence page {} exists", pageId);
        } catch (WebClientResponseException.NotFound e) {
            throw new IllegalArgumentException("Confluence page " + pageId + " not found");
        } catch (Exception e) {
            throw new RuntimeException("Failed to validate Confluence page: " + e.getMessage(), e);
        }
    }

    public void validateJiraProjectExists(String projectKey) {
        log.info("Validating Jira project exists: {}", projectKey);
        try {
            jiraClient.get()
                    .uri("/rest/api/3/project/" + projectKey)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Jira project {} exists", projectKey);
        } catch (WebClientResponseException.NotFound e) {
            throw new IllegalArgumentException("Jira project " + projectKey + " not found");
        } catch (Exception e) {
            throw new RuntimeException("Failed to validate Jira project: " + e.getMessage(), e);
        }
    }
}
