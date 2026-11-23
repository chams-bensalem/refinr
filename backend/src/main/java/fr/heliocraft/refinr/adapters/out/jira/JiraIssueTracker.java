// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.jira;

import fr.heliocraft.refinr.application.port.out.IssueTrackerException;
import fr.heliocraft.refinr.application.port.out.HealthCheckable;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.domain.model.ComponentHealth;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Feature;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import fr.heliocraft.refinr.domain.model.TrackerContent;
import fr.heliocraft.refinr.domain.service.LabelNormalizer;
import fr.heliocraft.refinr.domain.service.ReadinessPolicy;
import fr.heliocraft.refinr.domain.service.StoryMarkdown;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.annotation.Order;
import org.springframework.core.codec.DecodingException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Order(2)
// Seul gestionnaire de tickets instancie quand tracker.provider vaut jira (defaut) ; valeur controlee au demarrage
@ConditionalOnProperty(name = "tracker.provider", havingValue = "jira", matchIfMissing = true)
public class JiraIssueTracker implements IssueTrackerPort, HealthCheckable {

    private static final Logger log = LoggerFactory.getLogger(JiraIssueTracker.class);

    private final WebClient webClient;
    // Adresse du site (sans / final) pour les liens vers les tickets : {site}/browse/{KEY}
    private final String siteUrl;
    private final String projectKey;
    // Les projets "team-managed" (next-gen) n'ont pas de champ priority : Jira refuse l'issue s'il est envoye
    private final boolean priorityEnabled;
    // Champ personnalise des story points (ex. customfield_10016), propre a chaque instance Jira ; vide = non envoye
    private final String storyPointsField;
    // Hierarchie : two-levels (Free, Standard : pas de niveau Feature) ou three-levels (Premium configure)
    private final Hierarchy hierarchy;
    private final String epicType;
    private final String featureType;
    private final String storyType;
    private final String subtaskType;
    // Projets dont la configuration trois niveaux a ete validee (types presents, niveaux coherents)
    private final java.util.Set<String> validatedProjects = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** {@code jira.hierarchy} : niveau Feature absent (defaut) ou configure au-dessus de la Story (Premium). */
    public enum Hierarchy {
        TWO_LEVELS, THREE_LEVELS;

        public static Hierarchy of(String value) {
            String normalized = value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT);
            return switch (normalized) {
                case "", "two-levels" -> TWO_LEVELS;
                case "three-levels" -> THREE_LEVELS;
                default -> throw new IllegalStateException("jira.hierarchy must be two-levels or three-levels, got " + value);
            };
        }
    }

    /** Noms des types de tickets (jira.types.*), avec leurs defauts. */
    public record IssueTypes(String epic, String feature, String story, String subtask) {
        public static final IssueTypes DEFAULT = new IssueTypes("Epic", "Feature", "Story", "Subtask");

        public IssueTypes {
            epic = orDefault(epic, "Epic");
            feature = orDefault(feature, "Feature");
            story = orDefault(story, "Story");
            subtask = orDefault(subtask, "Subtask");
        }

        private static String orDefault(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.strip();
        }
    }

    public JiraIssueTracker(String baseUrl, String username, String token, String projectKey) {
        this(baseUrl, username, token, projectKey, true);
    }

    public JiraIssueTracker(String baseUrl, String username, String token, String projectKey, boolean priorityEnabled) {
        this(baseUrl, username, token, projectKey, priorityEnabled, "");
    }

    public JiraIssueTracker(String baseUrl, String username, String token, String projectKey, boolean priorityEnabled,
                            String storyPointsField) {
        this(baseUrl, username, token, projectKey, priorityEnabled, storyPointsField, Hierarchy.TWO_LEVELS,
                IssueTypes.DEFAULT);
    }

    @Autowired
    public JiraIssueTracker(
            @Value("${jira.base-url}") String baseUrl,
            @Value("${jira.username}") String username,
            @Value("${jira.token}") String token,
            @Value("${jira.project-key}") String projectKey,
            @Value("${jira.priority-enabled:true}") boolean priorityEnabled,
            @Value("${jira.story-points-field:}") String storyPointsField,
            @Value("${jira.hierarchy:two-levels}") String hierarchy,
            @Value("${jira.types.epic:Epic}") String epicType,
            @Value("${jira.types.feature:Feature}") String featureType,
            @Value("${jira.types.story:Story}") String storyType,
            @Value("${jira.types.subtask:Subtask}") String subtaskType) {
        this(baseUrl, username, token, projectKey, priorityEnabled, storyPointsField, Hierarchy.of(hierarchy),
                new IssueTypes(epicType, featureType, storyType, subtaskType));
    }

    public JiraIssueTracker(String baseUrl, String username, String token, String projectKey, boolean priorityEnabled,
                            String storyPointsField, Hierarchy hierarchy, IssueTypes types) {

        this.hierarchy = hierarchy;
        this.epicType = types.epic();
        this.featureType = types.feature();
        this.storyType = types.story();
        this.subtaskType = types.subtask();
        this.projectKey = projectKey;
        this.siteUrl = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        this.priorityEnabled = priorityEnabled;
        this.storyPointsField = storyPointsField == null ? "" : storyPointsField.strip();

        String auth = username + ":" + token;
        String encodedAuth = Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String displayName() {
        return "Jira";
    }

    @Override
    public boolean acceptsProjectId(String project) {
        return JiraProjectKeys.isValid(project);
    }

    @Override
    public String issueUrl(String key) {
        return siteUrl.isEmpty() || key == null || key.isBlank() ? null : siteUrl + "/browse/" + key;
    }

    public String createEpic(Epic epic) {
        return createEpic(epic, null);
    }

    // Pas de @Retryable : POST non idempotent (un 500 peut survenir apres creation -> doublon). Un echec est signale
    // dans la reponse partielle (crees / echoues) de CreateBacklog.
    @Override
    public String createEpic(Epic epic, String project) {
        // Titres et descriptions (sortie du modele) jamais dans les logs : CreateBacklog cite la position
        log.info("Creating Jira Epic");

        Map<String, Object> fields = new HashMap<>();
        fields.put("project", Map.of("key", projet(project)));
        fields.put("summary", epic.getName());
        fields.put("description", convertToADF(epic.getDescription()));
        fields.put("issuetype", Map.of("name", epicType));
        putPriority(fields, epic.getPriority());
        fields.put("labels", LabelNormalizer.normalize(epic.getLabels()));

        Map<String, Object> requestBody = Map.of("fields", fields);

        // Trois niveaux : configuration validee a la premiere creation dans le projet (types, niveaux)
        if (hierarchy == Hierarchy.THREE_LEVELS) {
            validateThreeLevels(projet(project));
        }
        return createIssue("epic", requestBody);
    }

    /** Trois niveaux : niveau Feature natif, et obligatoire (le parent d'une Story est toujours une Feature). */
    @Override
    public FeatureLevel featureLevel(String project) {
        return hierarchy == Hierarchy.THREE_LEVELS ? FeatureLevel.REQUIRED : FeatureLevel.NONE;
    }

    @Override
    public String createFeature(Feature feature, String epicKey, String project) {
        if (hierarchy != Hierarchy.THREE_LEVELS) {
            throw new IssueTrackerException("Jira has no Feature level (jira.hierarchy=two-levels)");
        }
        log.info("Creating Jira Feature under Epic: {}", epicKey);
        Map<String, Object> fields = new HashMap<>();
        fields.put("project", Map.of("key", projet(project)));
        fields.put("summary", feature.getName());
        fields.put("description", convertToADF(feature.getDescription()));
        fields.put("issuetype", Map.of("name", featureType));
        putPriority(fields, feature.getPriority());
        fields.put("labels", LabelNormalizer.normalize(feature.getLabels()));
        fields.put("parent", Map.of("key", epicKey));
        return createIssue("feature", Map.of("fields", fields));
    }

    /**
     * Configuration trois niveaux du projet, verifiee une fois par projet : les quatre types existent, la Feature est
     * au niveau 1 (celui de l'Epic natif de Jira, renomme) et l'Epic juste au-dessus. Sinon, echec qui nomme les
     * proprietes a corriger (rien n'est cree).
     */
    private void validateThreeLevels(String project) {
        if (validatedProjects.contains(project)) {
            return;
        }
        Map<?, ?> response;
        try {
            response = webClient.get().uri("/rest/api/3/project/{key}", project).retrieve().bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new IssueTrackerException("Jira refused to read the issue types of project " + project + " (HTTP "
                    + e.getStatusCode().value() + ")");
        } catch (WebClientRequestException e) {
            throw new IssueTrackerException("Jira unreachable");
        } catch (DecodingException e) {
            throw new IssueTrackerException("Jira returned an unreadable project " + project);
        }
        Map<String, Integer> levels = new HashMap<>();
        if (response != null && response.get("issueTypes") instanceof List<?> issueTypes) {
            for (Object type : issueTypes) {
                if (type instanceof Map<?, ?> map && map.get("name") instanceof String name
                        && map.get("hierarchyLevel") instanceof Number level) {
                    levels.put(name.toLowerCase(java.util.Locale.ROOT), level.intValue());
                }
            }
        }
        List<String> problems = new ArrayList<>();
        Map<String, String> configured = new java.util.LinkedHashMap<>();
        configured.put("jira.types.epic", epicType);
        configured.put("jira.types.feature", featureType);
        configured.put("jira.types.story", storyType);
        configured.put("jira.types.subtask", subtaskType);
        configured.forEach((property, name) -> {
            if (!levels.containsKey(name.toLowerCase(java.util.Locale.ROOT))) {
                problems.add(property + "=" + name + " is not an issue type of project " + project);
            }
        });
        if (problems.isEmpty()) {
            int feature = levels.get(featureType.toLowerCase(java.util.Locale.ROOT));
            int epic = levels.get(epicType.toLowerCase(java.util.Locale.ROOT));
            if (feature != 1) {
                problems.add("jira.types.feature=" + featureType + " must be at hierarchy level 1 (got " + feature + ")");
            }
            if (epic != feature + 1) {
                problems.add("jira.types.epic=" + epicType + " must be one level above " + featureType + " (got level "
                        + epic + ")");
            }
        }
        if (!problems.isEmpty()) {
            String message = "Jira three-level hierarchy (jira.hierarchy=three-levels) not configured: "
                    + String.join("; ", problems);
            log.error(message);
            throw new IssueTrackerException(message);
        }
        validatedProjects.add(project);
    }

    public String createStory(Story story, String epicKey) {
        return createStory(story, epicKey, null);
    }

    // Pas de @Retryable : POST non idempotent (un 500 peut survenir apres creation -> doublon). Un echec est signale
    // dans la reponse partielle (crees / echoues) de CreateBacklog.
    @Override
    public String createStory(Story story, String parentKey, String project) {
        log.info("Creating Jira Story under {}", parentKey);

        String fullDescription = StoryMarkdown.description(story);
        String summary = story.getTitle();
        List<String> labels = story.getLabels();

        Map<String, Object> fields = new HashMap<>();
        fields.put("project", Map.of("key", projet(project)));
        fields.put("summary", summary);
        fields.put("description", convertToADF(fullDescription));
        fields.put("issuetype", Map.of("name", storyType));
        putPriority(fields, story.getPriority());

        // Champ "parent" : la Feature (trois niveaux) ou l'Epic (deux niveaux)
        if (parentKey != null && !parentKey.isEmpty()) {
            fields.put("parent", Map.of("key", parentKey));
        }

        Integer storyPoints = story.getEstimatedStoryPoints();
        if (!storyPointsField.isEmpty() && storyPoints != null) {
            fields.put(storyPointsField, storyPoints);
        }

        List<String> cleanedLabels = new ArrayList<>(LabelNormalizer.normalize(labels));
        // Seul un "true" explicite de l'analyse rend la story codable par l'agent : dans le doute, validation humaine
        cleanedLabels.add(ReadinessPolicy.verdictLabel(story.getAiReady()));
        fields.put("labels", cleanedLabels);

        Map<String, Object> requestBody = Map.of("fields", fields);

        return createIssue("story", requestBody);
    }

    public String createSubtask(Task task, String parentStoryKey) {
        return createSubtask(task, parentStoryKey, null);
    }

    // Pas de @Retryable : POST non idempotent (un 500 peut survenir apres creation -> doublon). Un echec est signale
    // dans la reponse partielle (crees / echoues) de CreateBacklog.
    @Override
    public String createSubtask(Task task, String parentStoryKey, String project) {
        log.info("Creating Jira Task under Story: {}", parentStoryKey);

        Map<String, Object> fields = Map.of(
                "project", Map.of("key", projet(project)),
                "summary", task.getTitle(),
                "description", convertToADF(task.getDescription()),
                "issuetype", Map.of("name", subtaskType),
                "parent", Map.of("key", parentStoryKey),
                "labels", LabelNormalizer.normalize(task.getLabels())  // ✅ BON
        );

        Map<String, Object> requestBody = Map.of("fields", fields);

        return createIssue("subtask", requestBody);
    }

    /**
     * POST de creation d'un ticket, sans nouvel essai (non idempotent). Tout echec connu devient une
     * {@link IssueTrackerException} sans cause : message sur (statut HTTP, champs en cause) pour les logs, et message
     * client complete des erreurs de validation de Jira (qui peuvent citer le contenu envoye).
     */
    private String createIssue(String kind, Map<String, Object> requestBody) {
        try {
            Map<String, Object> response = webClient.post()
                    .uri("/rest/api/3/issue")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            Object key = response != null ? response.get("key") : null;
            if (!(key instanceof String issueKey) || issueKey.isBlank()) {
                throw new IssueTrackerException("Jira returned no key for the " + kind);
            }
            log.info("{} created successfully: {}", kind, issueKey);
            return issueKey;
        } catch (WebClientResponseException e) {
            JiraErrors errors = JiraErrors.of(e);
            String message = "Jira rejected the " + kind + " (HTTP " + e.getStatusCode().value() + ")";
            log.error("{}{}", message, errors.shape());
            throw new IssueTrackerException(message + errors.shape(),
                    message + (errors.details().isEmpty() ? "" : ": " + errors.details()));
        } catch (WebClientRequestException e) {
            log.error("Jira unreachable while creating {}: {}", kind, e.getClass().getSimpleName());
            throw new IssueTrackerException("Jira unreachable");
        } catch (DecodingException e) {
            // Reponse 2xx illisible : le message de Jackson cite le corps recu
            String message = "Jira returned an unreadable response for the " + kind + " (it may have been created)";
            log.error(message);
            throw new IssueTrackerException(message);
        }
    }

    /**
     * Erreurs du corps de reponse de Jira.
     *
     * @param fields   champs en cause (cles de {@code errors}, ex. {@code labels}, {@code customfield_10016}) : surs
     * @param messages nombre de {@code errorMessages}
     * @param details  {@code errorMessages} et {@code errors} (champ: message), joints et tronques : peuvent citer le
     *                 contenu envoye, reserves au client
     */
    record JiraErrors(List<String> fields, int messages, String details) {

        static JiraErrors of(WebClientResponseException e) {
            List<String> fields = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            int messages = 0;
            try {
                Map<?, ?> body = e.getResponseBodyAs(Map.class);
                if (body != null && body.get("errorMessages") instanceof List<?> list) {
                    messages = list.size();
                    list.forEach(message -> texts.add(String.valueOf(message)));
                }
                if (body != null && body.get("errors") instanceof Map<?, ?> errors) {
                    errors.forEach((field, message) -> {
                        fields.add(String.valueOf(field));
                        texts.add(field + ": " + message);
                    });
                }
            } catch (RuntimeException unreadable) {
                return new JiraErrors(List.of(), 0, "");
            }
            String joined = String.join("; ", texts);
            return new JiraErrors(fields, messages, joined.length() > 500 ? joined.substring(0, 500) + "..." : joined);
        }

        /** Forme sure des erreurs : champs en cause et nombre de messages, jamais leur texte. */
        String shape() {
            List<String> parts = new ArrayList<>();
            if (!fields.isEmpty()) {
                parts.add("fields: " + String.join(", ", fields));
            }
            if (messages > 0) {
                parts.add(messages + " error message" + (messages > 1 ? "s" : ""));
            }
            return parts.isEmpty() ? "" : " [" + String.join("; ", parts) + "]";
        }
    }

    private String projet(String project) {
        return project == null || project.isBlank() ? projectKey : project;
    }

    private void putPriority(Map<String, Object> fields, String priority) {
        if (priorityEnabled) {
            fields.put("priority", Map.of("name", priority != null ? priority : "Medium"));
        }
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
                    .uri("/rest/api/3/issue/{issueKey}?fields=summary,description,labels,project,subtasks", issueKey)
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
            // Projet et sous-taches : les sous-taches d'une story devenue prete vont dans son projet, jamais deux fois
            String project = fields.get("project") instanceof Map<?, ?> p && p.get("key") instanceof String k ? k : null;
            List<String> subtasks = fields.get("subtasks") instanceof List<?> raw
                    ? raw.stream().filter(Map.class::isInstance).map(Map.class::cast)
                            .map(m -> m.get("key")).filter(String.class::isInstance).map(String.class::cast).toList()
                    : List.of();
            // Cle canonique rendue par Jira (proj-7 -> PROJ-7) : un seul verrou par story
            String key = response.get("key") instanceof String k && !k.isBlank() ? k : issueKey;
            return new TrackedStory(key, summary != null ? summary.toString() : "",
                    adfToText(fields.get("description")).strip(), labels,
                    descriptionAdf != null ? new JiraAdfContent(descriptionAdf) : null, project, subtasks);
        } catch (WebClientResponseException e) {
            // Sans cause : l'exception WebClient porte l'URL et le corps de la reponse
            throw new RuntimeException("Failed to read issue " + issueKey + ": HTTP " + e.getStatusCode().value());
        } catch (WebClientRequestException e) {
            throw new RuntimeException("Failed to read issue " + issueKey + ": Jira unreachable ("
                    + e.getClass().getSimpleName() + ")");
        } catch (DecodingException e) {
            throw new RuntimeException("Failed to read issue " + issueKey + ": unreadable Jira response");
        }
    }

    /**
     * Ajoute la precision de l'utilisateur et le nouveau verdict a la fin de la description, et remplace le label
     * de verdict (ai-ready ou validation-necessaire). La description d'origine n'est jamais reconstruite depuis le
     * texte brut (images, tableaux, liens et mise en forme gardes) ; les autres labels sont conserves.
     */
    @Override
    public void applyReassessment(TrackedStory story, String precision, Readiness readiness) {
        applyVerdict(story, precision, readiness);
    }

    @Override
    public void applyHumanValidation(TrackedStory story, Readiness readiness) {
        applyVerdict(story, null, readiness);
    }

    /** Description d'origine + precision (si fournie) + ligne du verdict, et label de verdict remplace. */
    private void applyVerdict(TrackedStory story, String precision, Readiness readiness) {
        boolean aiReady = readiness.aiReady();
        String verdict = ReadinessPolicy.verdictLabel(aiReady);
        List<Object> content = new ArrayList<>();
        Map<String, Object> originalAdf = adfOf(story.originalContent());
        if (originalAdf != null && originalAdf.get("content") instanceof List<?> original) {
            content.addAll(original);
        } else if (story.description() != null && !story.description().isBlank()) {
            content.add(adfParagraph(story.description()));
        }
        if (precision != null) {
            content.add(adfParagraph(StoryMarkdown.precisionLine(precision)));
        }
        content.add(adfParagraph(StoryMarkdown.reassessedReadinessLine(readiness)));
        Map<String, Object> description = new HashMap<>();
        description.put("type", "doc");
        description.put("version", 1);
        description.put("content", content);
        List<String> labels = ReadinessPolicy.replaceVerdictLabel(story.labels(), aiReady);

        log.info("Applying {} to {}: {}", precision != null ? "reassessment" : "human validation", story.key(), verdict);
        try {
            webClient.put()
                    .uri("/rest/api/3/issue/{issueKey}", story.key())
                    .bodyValue(Map.of("fields", Map.of("description", description, "labels", labels)))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
        } catch (WebClientResponseException e) {
            // Ni corps de reponse (il peut citer la description envoyee) ni cause
            throw new RuntimeException("Failed to update issue " + story.key() + ": HTTP " + e.getStatusCode().value()
                    + JiraErrors.of(e).shape());
        } catch (WebClientRequestException e) {
            throw new RuntimeException("Failed to update issue " + story.key() + ": Jira unreachable ("
                    + e.getClass().getSimpleName() + ")");
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

    /** Descriptions ecrites en Markdown (analyse et mise en forme ci-dessus) -> ADF structure. */
    private Map<String, Object> convertToADF(String markdown) {
        return MarkdownAdf.toDocument(markdown);
    }

    /** Lecture du projet par defaut (verifie l'acces et la cle de projet), avec un delai court. */
    @Override
    public List<ComponentHealth> health() {
        try {
            webClient.get()
                    .uri("/rest/api/3/project/{key}", projectKey)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .block();
            return List.of(ComponentHealth.up("jira", Map.of()));
        } catch (RuntimeException e) {
            log.warn("Jira health check failed: {}", healthCause(e));
            return List.of(ComponentHealth.down("jira", healthCause(e), Map.of()));
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
