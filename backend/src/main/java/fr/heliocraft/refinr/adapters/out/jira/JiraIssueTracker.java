package fr.heliocraft.refinr.adapters.out.jira;

import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import fr.heliocraft.refinr.domain.model.TrackerContent;
import fr.heliocraft.refinr.domain.service.LabelNormalizer;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JiraIssueTracker implements IssueTrackerPort {

    private static final Logger log = LoggerFactory.getLogger(JiraIssueTracker.class);

    private final WebClient webClient;
    private final String projectKey;
    // Les projets "team-managed" (next-gen) n'ont pas de champ priority : Jira refuse l'issue s'il est envoye
    private final boolean priorityEnabled;

    public JiraIssueTracker(String baseUrl, String username, String token, String projectKey) {
        this(baseUrl, username, token, projectKey, true);
    }

    @Autowired
    public JiraIssueTracker(
            @Value("${jira.base-url}") String baseUrl,
            @Value("${jira.username}") String username,
            @Value("${jira.token}") String token,
            @Value("${jira.project-key}") String projectKey,
            @Value("${jira.priority-enabled:true}") boolean priorityEnabled) {

        this.projectKey = projectKey;
        this.priorityEnabled = priorityEnabled;

        String auth = username + ":" + token;
        String encodedAuth = Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String createEpic(Epic epic) {
        return createEpic(epic, null);
    }

    @Retryable(
        includes = {WebClientResponseException.TooManyRequests.class,
                    WebClientResponseException.InternalServerError.class},
        maxRetries = 2,
        delay = 1000,
        multiplier = 2
    )
    @Override
    public String createEpic(Epic epic, String project) {
        log.info("Creating Jira Epic: {}", epic.getName());

        Map<String, Object> fields = new HashMap<>();
        fields.put("project", Map.of("key", projet(project)));
        fields.put("summary", epic.getName());
        fields.put("description", convertToADF(epic.getDescription()));
        fields.put("issuetype", Map.of("name", "Epic"));
        putPriority(fields, epic.getPriority());
        fields.put("labels", LabelNormalizer.normalize(epic.getLabels()));

        Map<String, Object> requestBody = Map.of("fields", fields);

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/rest/api/3/issue")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String epicKey = (String) response.get("key");

            log.info("Epic created successfully: {}", epicKey);
            return epicKey;
        } catch (WebClientResponseException e) {
            log.error("Jira API Error: Status={}, Response={}", e.getStatusCode(), e.getResponseBodyAsString());
            log.error("Error creating Epic", e);
            throw new RuntimeException("Failed to create Epic: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error creating Epic", e);
            throw new RuntimeException("Failed to create Epic: " + e.getMessage(), e);
        }
    }

    public String createStory(Story story, String epicKey) {
        return createStory(story, epicKey, null);
    }

    @Retryable(
        includes = {WebClientResponseException.TooManyRequests.class,
                    WebClientResponseException.InternalServerError.class},
        maxRetries = 2,
        delay = 1000,
        multiplier = 2
    )
    @Override
    public String createStory(Story story, String epicKey, String project) {
        log.info("Creating Jira Story: {} under Epic: {}", story.getTitle(), epicKey);

        String description = formatStoryDescription(story);
        String fullDescription = description + "\n\n" + formatAcceptanceCriteria(story.getAcceptanceCriteria())
                + formatAiReadiness(story);
        String summary = story.getTitle();
        List<String> labels = story.getLabels();

        Map<String, Object> fields = new HashMap<>();
        fields.put("project", Map.of("key", projet(project)));
        fields.put("summary", summary);
        fields.put("description", convertToADF(fullDescription));
        fields.put("issuetype", Map.of("name", "Story"));
        putPriority(fields, story.getPriority());

        // Utiliser le nouveau champ "parent"
        if (epicKey != null && !epicKey.isEmpty()) {
            fields.put("parent", Map.of("key", epicKey));
        }

//        // Story Points
//        if (story.getEstimatedStoryPoints() != null) {
//            fields.put("customfield_10016", story.getEstimatedStoryPoints());
//        }

        List<String> cleanedLabels = new ArrayList<>(LabelNormalizer.normalize(labels));
        // Seul un "true" explicite de l'analyse rend la story codable par l'agent : dans le doute, validation humaine
        cleanedLabels.add(ReadinessPolicy.verdictLabel(story.getAiReady()));
        fields.put("labels", cleanedLabels);

        Map<String, Object> requestBody = Map.of("fields", fields);

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/rest/api/3/issue")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String storyKey = (String) response.get("key");

            log.info("Story created successfully: {}", storyKey);
            return storyKey;
        } catch (WebClientResponseException e) {
            log.error("Jira API Error: Status={}, Response={}", e.getStatusCode(), e.getResponseBodyAsString());
            log.error("Error creating Story", e);
            throw new RuntimeException("Failed to create Story: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error creating Story", e);
            throw new RuntimeException("Failed to create Story: " + e.getMessage(), e);
        }
    }

    public String createSubtask(Task task, String parentStoryKey) {
        return createSubtask(task, parentStoryKey, null);
    }

    @Retryable(
        includes = {WebClientResponseException.TooManyRequests.class,
                    WebClientResponseException.InternalServerError.class},
        maxRetries = 2,
        delay = 1000,
        multiplier = 2
    )
    @Override
    public String createSubtask(Task task, String parentStoryKey, String project) {
        log.info("Creating Jira Task: {} under Story: {}", task.getTitle(), parentStoryKey);

        Map<String, Object> fields = Map.of(
                "project", Map.of("key", projet(project)),
                "summary", task.getTitle(),
                "description", convertToADF(task.getDescription() != null ? task.getDescription() : ""),
                "issuetype", Map.of("name", "Subtask"),
                "parent", Map.of("key", parentStoryKey),
                "labels", LabelNormalizer.normalize(task.getLabels())  // ✅ BON
        );

        Map<String, Object> requestBody = Map.of("fields", fields);

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/rest/api/3/issue")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String taskKey = (String) response.get("key");

            log.info("Task created successfully: {}", taskKey);
            return taskKey;
        } catch (WebClientResponseException e) {
            log.error("Jira API Error: Status={}, Response={}", e.getStatusCode(), e.getResponseBodyAsString());
            log.error("Error creating Task", e);
            throw new RuntimeException("Failed to create Task: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error creating Task", e);
            throw new RuntimeException("Failed to create Task: " + e.getMessage(), e);
        }
    }

    public void createFullHierarchy(List<Epic> epics) {
        createFullHierarchy(epics, null);
    }

    /**
     * Cree epics, stories et sous-taches dans le projet donne (null ou vide : projet par defaut,
     * jira.project-key). Permet a un outil appelant de remplir un projet qu'il vient de creer.
     */
    public void createFullHierarchy(List<Epic> epics, String project) {
        log.info("Creating full Jira hierarchy: {} epics in project {}", epics.size(), projet(project));

        for (Epic epic : epics) {
            String epicKey = createEpic(epic, project);
            epic.setTrackerKey(epicKey);

            for (Story story : epic.getStories()) {
                String storyKey = createStory(story, epicKey, project);
                story.setTrackerKey(storyKey);
                story.setEpicTrackerKey(epicKey);

                for (Task task : story.getTasks()) {
                    String taskKey = createSubtask(task, storyKey, project);
                    task.setTrackerKey(taskKey);
                    task.setParentTrackerKey(storyKey);
                }
            }
        }

        log.info("Full hierarchy created successfully");
    }

    private String projet(String project) {
        return project == null || project.isBlank() ? projectKey : project;
    }

    private void putPriority(Map<String, Object> fields, String priority) {
        if (priorityEnabled) {
            fields.put("priority", Map.of("name", priority != null ? priority : "Medium"));
        }
    }

    private void updateStoryPoints(String issueKey, Integer storyPoints) {
        log.info("Updating Story Points for {}: {}", issueKey, storyPoints);

        Map<String, Object> fields = Map.of(
                "customfield_10016", storyPoints
        );

        Map<String, Object> requestBody = Map.of("fields", fields);

        try {
            webClient.put()
                    .uri("/rest/api/3/issue/{issueKey}", issueKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("Story Points updated successfully");
        } catch (Exception e) {
            log.warn("Failed to update Story Points (field may not exist): {}", e.getMessage());
        }
    }

    private String formatStoryDescription(Story story) {
        StringBuilder sb = new StringBuilder();

        sb.append("*As a* ").append(story.getAsA()).append("\n");
        sb.append("*I want* ").append(story.getIWant()).append("\n");
        sb.append("*So that* ").append(story.getSoThat()).append("\n\n");

        if (story.getDescription() != null && !story.getDescription().isEmpty()) {
            sb.append("h3. Description\n");
            sb.append(story.getDescription()).append("\n");
        }

        return sb.toString();
    }

    private String formatAcceptanceCriteria(List<String> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return "No acceptance criteria defined";
        }

        StringBuilder formatted = new StringBuilder();
        formatted.append("h3. Acceptance Criteria\n\n");

        for (int i = 0; i < criteria.size(); i++) {
            formatted.append("*AC").append(i + 1).append(":* ");
            formatted.append(criteria.get(i));
            formatted.append("\n\n");
        }

        return formatted.toString();
    }

    /**
     * Story relue pour une reevaluation : description en texte brut (pour l'analyse), description ADF d'origine
     * (conservee telle quelle a la reecriture : images, tableaux, liens, via {@link JiraAdfContent}) et labels actuels.
     */
    @Override
    public TrackedStory findStory(String issueKey) {
        log.info("Reading Jira issue {} for reassessment", issueKey);
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/rest/api/3/issue/{issueKey}?fields=summary,description,labels", issueKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            Object rawFields = response != null ? response.get("fields") : null;
            if (!(rawFields instanceof Map<?, ?> fields)) {
                throw new IllegalStateException("Issue " + issueKey + " has no readable fields");
            }
            List<String> labels = fields.get("labels") instanceof List<?> raw
                    ? raw.stream().map(String::valueOf).toList()
                    : List.of();
            Object summary = fields.get("summary");
            @SuppressWarnings("unchecked")
            Map<String, Object> descriptionAdf = fields.get("description") instanceof Map<?, ?> adf
                    ? (Map<String, Object>) adf
                    : null;
            return new TrackedStory(issueKey, summary != null ? summary.toString() : "",
                    adfToText(fields.get("description")).strip(), labels,
                    descriptionAdf != null ? new JiraAdfContent(descriptionAdf) : null);
        } catch (WebClientResponseException e) {
            throw new RuntimeException("Failed to read issue " + issueKey + ": HTTP " + e.getStatusCode().value(), e);
        }
    }

    /**
     * Ajoute la precision de l'utilisateur et le nouveau verdict a la fin de la description, et remplace le label
     * de verdict (ai-ready ou validation-necessaire). La description d'origine n'est jamais reconstruite depuis le
     * texte brut (images, tableaux, liens et mise en forme gardes) ; les autres labels sont conserves.
     */
    @Override
    public void applyReassessment(TrackedStory story, String precision, Readiness readiness) {
        boolean aiReady = readiness.aiReady();
        String reason = readiness.aiReadyReason();
        String verdict = ReadinessPolicy.verdictLabel(aiReady);
        String justification = reason != null && !reason.isBlank() ? reason : "non precise par l'analyse";
        List<Object> content = new ArrayList<>();
        Map<String, Object> originalAdf = adfOf(story.originalContent());
        if (originalAdf != null && originalAdf.get("content") instanceof List<?> original) {
            content.addAll(original);
        } else if (story.description() != null && !story.description().isBlank()) {
            content.add(adfParagraph(story.description()));
        }
        content.add(adfParagraph("Precision de l'utilisateur (reevaluation): " + precision));
        content.add(adfParagraph("AI readiness (" + verdict + "): " + justification));
        Map<String, Object> description = new HashMap<>();
        description.put("type", "doc");
        description.put("version", 1);
        description.put("content", content);
        List<String> labels = ReadinessPolicy.replaceVerdictLabel(story.labels(), aiReady);

        log.info("Applying reassessment to {}: {}", story.key(), verdict);
        try {
            webClient.put()
                    .uri("/rest/api/3/issue/{issueKey}", story.key())
                    .bodyValue(Map.of("fields", Map.of("description", description, "labels", labels)))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new RuntimeException("Failed to update issue " + story.key() + ": " + e.getResponseBodyAsString(), e);
        }
    }

    /** Contenu d'origine lu par cet adaptateur ; tout autre type est refuse explicitement (jamais ignore). */
    private static Map<String, Object> adfOf(TrackerContent content) {
        if (content == null) {
            return null;
        }
        if (content instanceof JiraAdfContent adf) {
            return adf.document();
        }
        throw new IllegalArgumentException("Unsupported tracker content for Jira: " + content.getClass().getName());
    }

    private static Map<String, Object> adfParagraph(String text) {
        return Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", text)));
    }

    private static String adfToText(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        if ("text".equals(map.get("type")) && map.get("text") instanceof String value) {
            text.append(value);
        }
        if (map.get("content") instanceof List<?> children) {
            for (Object child : children) {
                text.append(adfToText(child));
            }
        }
        if ("paragraph".equals(map.get("type")) || "heading".equals(map.get("type"))) {
            text.append("\n");
        }
        return text.toString();
    }

    private String formatAiReadiness(Story story) {
        String verdict = ReadinessPolicy.verdictLabel(story.getAiReady());
        String reason = story.getAiReadyReason() != null ? story.getAiReadyReason() : "non evalue par l'analyse";
        return "\n\nAI readiness (" + verdict + "): " + reason;
    }

    private Map<String, Object> convertToADF(String text) {
        if (text == null || text.isEmpty()) {
            text = "No description provided";
        }

        return Map.of(
                "type", "doc",
                "version", 1,
                "content", List.of(
                        Map.of(
                                "type", "paragraph",
                                "content", List.of(
                                        Map.of(
                                                "type", "text",
                                                "text", text
                                        )
                                )
                        )
                )
        );
    }

}
