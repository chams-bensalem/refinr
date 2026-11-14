// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.openproject;

import fr.heliocraft.refinr.application.port.out.HealthCheckable;
import fr.heliocraft.refinr.application.port.out.IssueTrackerException;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.domain.model.ComponentHealth;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import fr.heliocraft.refinr.domain.service.LabelNormalizer;
import fr.heliocraft.refinr.domain.service.ReadinessPolicy;
import fr.heliocraft.refinr.domain.service.StoryMarkdown;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Gestionnaire de tickets OpenProject (API v3), actif quand {@code tracker.provider=openproject}. Faits de l'API
 * verifies en v1.1 phase 0 (cf. DECISIONS.md) : un POST par niveau avec {@code _links.parent}, description Markdown
 * native, verdict dans un champ personnalise de type liste, {@code lockVersion} obligatoire au PATCH, erreurs lues
 * sur {@code errorIdentifier}. La cle renvoyee est l'id du work package (ex. {@code "39"}).
 */
@Service
@Order(2)
// Seul gestionnaire de tickets instancie quand tracker.provider vaut openproject ; valeur controlee au demarrage
@ConditionalOnProperty(name = "tracker.provider", havingValue = "openproject")
public class OpenProjectIssueTracker implements IssueTrackerPort, HealthCheckable {

    private static final Logger log = LoggerFactory.getLogger(OpenProjectIssueTracker.class);

    static final String HEALTH_NAME = "openproject";
    static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);
    private static final Pattern WORK_PACKAGE_ID = Pattern.compile("^[0-9]{1,18}$");

    // Noms de priorite produits par l'analyse (High / Medium / Low) sans equivalent direct dans OpenProject
    private static final Map<String, String> PRIORITY_ALIASES = Map.of(
            "medium", "Normal", "highest", "Immediate", "critical", "Immediate", "lowest", "Low");

    private final WebClient webClient;
    // Adresse web de l'instance (sans / final) pour les liens vers les work packages : {instance}/work_packages/{id} ;
    // openproject.web-url si renseignee (ex. Refinr joint OpenProject par une adresse interne), sinon base-url
    private final String siteUrl;
    private final String defaultProject;
    private final String epicType;
    private final String storyType;
    private final String subtaskType;
    private final String readinessField;
    private final boolean storyPointsEnabled;
    private final boolean priorityEnabled;
    private final Duration timeout;

    // Ids resolus par projet (identifiant ou id, tel que demande) ; une resolution en echec n'est pas memorisee
    private final Map<String, ProjectSetup> setups = new ConcurrentHashMap<>();

    @Autowired
    public OpenProjectIssueTracker(
            @Value("${openproject.base-url}") String baseUrl,
            @Value("${openproject.api-key}") String apiKey,
            @Value("${openproject.project}") String defaultProject,
            @Value("${openproject.types.epic:Epic}") String epicType,
            @Value("${openproject.types.story:User story}") String storyType,
            @Value("${openproject.types.subtask:Task}") String subtaskType,
            @Value("${openproject.readiness-field:Agent readiness}") String readinessField,
            @Value("${openproject.story-points-enabled:true}") boolean storyPointsEnabled,
            @Value("${openproject.priority-enabled:true}") boolean priorityEnabled,
            @Value("${openproject.web-url:}") String webUrl) {
        this(baseUrl, apiKey, defaultProject, epicType, storyType, subtaskType, readinessField, storyPointsEnabled,
                priorityEnabled, TIMEOUT, webUrl);
    }

    /** Delai par appel parametrable pour les tests ; adresse web = base-url. */
    OpenProjectIssueTracker(String baseUrl, String apiKey, String defaultProject, String epicType, String storyType,
                            String subtaskType, String readinessField, boolean storyPointsEnabled,
                            boolean priorityEnabled, Duration timeout) {
        this(baseUrl, apiKey, defaultProject, epicType, storyType, subtaskType, readinessField, storyPointsEnabled,
                priorityEnabled, timeout, null);
    }

    OpenProjectIssueTracker(String baseUrl, String apiKey, String defaultProject, String epicType, String storyType,
                            String subtaskType, String readinessField, boolean storyPointsEnabled,
                            boolean priorityEnabled, Duration timeout, String webUrl) {
        String site = webUrl == null || webUrl.isBlank() ? baseUrl : webUrl;
        this.siteUrl = site == null ? "" : site.strip().replaceAll("/+$", "");
        this.defaultProject = defaultProject;
        this.epicType = epicType;
        this.storyType = storyType;
        this.subtaskType = subtaskType;
        this.readinessField = readinessField;
        this.storyPointsEnabled = storyPointsEnabled;
        this.priorityEnabled = priorityEnabled;
        this.timeout = timeout;
        String credentials = Base64.getEncoder()
                .encodeToString(("apikey:" + apiKey).getBytes(StandardCharsets.UTF_8));
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, "application/hal+json, application/json")
                .build();
    }

    @Override
    public String displayName() {
        return "OpenProject";
    }

    @Override
    public boolean acceptsProjectId(String project) {
        return OpenProjectProjectIds.isValid(project);
    }

    @Override
    public String issueUrl(String key) {
        return siteUrl.isEmpty() || key == null || !WORK_PACKAGE_ID.matcher(key).matches()
                ? null : siteUrl + "/work_packages/" + key;
    }

    // --- Creation : pas de @Retryable (POST non idempotent : un echec tardif pourrait creer un doublon). Un echec
    // est signale dans la reponse partielle (crees / echoues) de CreateBacklog.

    @Override
    public String createEpic(Epic epic, String project) {
        String target = project(project);
        // Titres et descriptions (sortie du modele) jamais dans les logs : CreateBacklog cite la position
        log.info("Creating OpenProject epic");
        ProjectSetup setup = setup(target);
        Map<String, Object> links = baseLinks(target, setup.typeHref(epicType));
        putPriority(links, setup, epic.getPriority());
        return create("epic", target, epic.getName(), withLabels(epic.getDescription(), epic.getLabels()), links, null);
    }

    @Override
    public String createStory(Story story, String epicKey, String project) {
        String target = project(project);
        log.info("Creating OpenProject story under epic {}", epicKey);
        ProjectSetup setup = setup(target);
        Map<String, Object> links = baseLinks(target, setup.typeHref(storyType));
        putParent(links, epicKey);
        putPriority(links, setup, story.getPriority());
        // Seul un "true" explicite de l'analyse rend la story codable par l'agent : dans le doute, validation humaine
        links.put(setup.readinessKey(),
                Map.of("href", setup.optionHref(ReadinessPolicy.verdictLabel(story.getAiReady()))));
        // Story points sur les stories uniquement (meme perimetre que Jira), si actives et estimes
        Integer storyPoints = storyPointsEnabled ? story.getEstimatedStoryPoints() : null;
        return create("story", target, story.getTitle(),
                withLabels(StoryMarkdown.description(story), story.getLabels()), links, storyPoints);
    }

    @Override
    public String createSubtask(Task task, String storyKey, String project) {
        String target = project(project);
        log.info("Creating OpenProject task under story {}", storyKey);
        ProjectSetup setup = setup(target);
        Map<String, Object> links = baseLinks(target, setup.typeHref(subtaskType));
        putParent(links, storyKey);
        return create("subtask", target, task.getTitle(), withLabels(task.getDescription(), task.getLabels()),
                links, null);
    }

    private String create(String kind, String project, String subject, String markdown, Map<String, Object> links,
                          Integer storyPoints) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subject", subject);
        body.put("description", Map.of("format", "markdown", "raw", markdown));
        if (storyPoints != null) {
            body.put("storyPoints", storyPoints);
        }
        body.put("_links", links);
        try {
            Map<?, ?> response = block(webClient.post()
                    .uri(uri -> uri.path("/api/v3/work_packages").queryParam("notify", "false").build())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class));
            Object id = response != null ? response.get("id") : null;
            if (!(id instanceof Number number)) {
                throw new IssueTrackerException("OpenProject returned no id for the " + kind);
            }
            String key = String.valueOf(number.longValue());
            log.info("{} created successfully: {}", kind, key);
            return key;
        } catch (WebClientResponseException e) {
            OpenProjectError error = OpenProjectError.of(e);
            log.error("OpenProject rejected the {}: {}", kind, error.safeSummary());
            String safe = "OpenProject rejected the " + kind + " (" + error.safeSummary() + ")";
            String client = "OpenProject rejected the " + kind + " (" + error.summary() + ")";
            if (error.invalidatesProjectSetup()) {
                // Type ou champ modifie cote admin depuis la resolution : la requete suivante re-resout (pas de relance)
                setups.remove(project);
                throw new IssueTrackerException(safe + ". " + setupHint(project), client + ". " + setupHint(project));
            }
            throw new IssueTrackerException(safe, client);
        } catch (WebClientRequestException e) {
            log.error("OpenProject unreachable while creating {}: {}", kind, e.getClass().getSimpleName());
            throw new IssueTrackerException("OpenProject unreachable");
        } catch (RuntimeException e) {
            throw timeoutOr(e, "OpenProject did not answer within " + timeout.toSeconds()
                    + "s while creating the " + kind + " (it may have been created)",
                    "OpenProject returned an unreadable response for the " + kind + " (it may have been created)");
        }
    }

    // --- Reevaluation

    /** Lecture seule, donc relancee sur 429 et 500 comme la lecture Confluence (les causes sont suivies). */
    @Retryable(
        includes = {WebClientResponseException.TooManyRequests.class,
                    WebClientResponseException.InternalServerError.class},
        maxRetries = 2,
        delay = 1000,
        multiplier = 2
    )
    @Override
    public TrackedStory findStory(String key) {
        log.info("Reading OpenProject work package {} for reassessment", key);
        WorkPackage workPackage = read(key);
        ProjectSetup setup = setup(workPackage.projectId());
        // Le verdict (option du champ liste) tient lieu de label pour la regle "en attente de validation"
        List<String> labels = new ArrayList<>();
        String verdict = workPackage.linkTitle(setup.readinessKey());
        if (verdict != null) {
            labels.add(verdict);
        }
        return new TrackedStory(key, workPackage.subject(), workPackage.description(), labels, null);
    }

    /**
     * Precision et nouveau verdict ajoutes en commentaire (la description d'origine n'est pas touchee), puis verdict
     * ecrit dans le champ liste. Le commentaire incremente le {@code lockVersion} : relecture avant le PATCH, et une
     * seule nouvelle tentative (relecture + PATCH) sur conflit 409.
     */
    @Override
    public void applyReassessment(TrackedStory story, String precision, Readiness readiness) {
        String key = story.key();
        String verdict = ReadinessPolicy.verdictLabel(readiness.aiReady());
        log.info("Applying reassessment to {}: {}", key, verdict);
        comment(key, StoryMarkdown.precisionLine(precision) + "\n\n" + StoryMarkdown.reassessedReadinessLine(readiness));
        try {
            patchVerdict(key, verdict);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() != 409) {
                throw failure("update the verdict of work package " + key, e);
            }
            log.warn("Update conflict on work package {}: reading it again and retrying once", key);
            try {
                patchVerdict(key, verdict);
            } catch (WebClientResponseException again) {
                throw failure("update the verdict of work package " + key
                        + (again.getStatusCode().value() == 409 ? " (conflict persisted after one retry)" : ""), again);
            }
        }
    }

    private void comment(String key, String text) {
        String id = workPackageId(key);
        try {
            block(webClient.post()
                    .uri(uri -> uri.path("/api/v3/work_packages/{id}/activities").queryParam("notify", "false")
                            .build(id))
                    .bodyValue(Map.of("comment", Map.of("raw", text)))
                    .retrieve()
                    .toBodilessEntity());
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                // OpenProject repond 404 (et non 403) quand le compte ne peut pas commenter
                log.error("OpenProject answered 404 when commenting work package {}", key);
                throw new IssueTrackerException("OpenProject could not comment work package " + key + " (HTTP 404): "
                        + "check that it exists and that the Refinr account has the add_work_package_comments "
                        + "permission in its project");
            }
            throw failure("comment work package " + key, e);
        } catch (WebClientRequestException e) {
            throw new IssueTrackerException("OpenProject unreachable");
        } catch (RuntimeException e) {
            throw timeoutOr(e, "OpenProject did not answer within " + timeout.toSeconds() + "s while commenting");
        }
    }

    /** Relit le work package (lockVersion a jour) puis PATCH du verdict ; laisse passer les erreurs HTTP du PATCH. */
    private void patchVerdict(String key, String verdict) {
        WorkPackage current = read(key);
        ProjectSetup setup = setup(current.projectId());
        Map<String, Object> body = Map.of(
                "lockVersion", current.lockVersion(),
                "_links", Map.of(setup.readinessKey(), Map.of("href", setup.optionHref(verdict))));
        String id = workPackageId(key);
        try {
            block(webClient.patch()
                    .uri(uri -> uri.path("/api/v3/work_packages/{id}").queryParam("notify", "false").build(id))
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity());
        } catch (WebClientResponseException e) {
            if (OpenProjectError.of(e).invalidatesProjectSetup()) {
                // Champ ou options recrees cote admin : la reevaluation suivante re-resout (pas de relance)
                setups.remove(current.projectId());
                IssueTrackerException failure = failure("update the verdict of work package " + key, e);
                String hint = ". " + setupHint(current.projectId());
                throw new IssueTrackerException(failure.getMessage() + hint, failure.clientMessage() + hint);
            }
            throw e;
        } catch (WebClientRequestException e) {
            throw new IssueTrackerException("OpenProject unreachable");
        } catch (RuntimeException e) {
            throw timeoutOr(e, "OpenProject did not answer within " + timeout.toSeconds() + "s while updating");
        }
    }

    private WorkPackage read(String key) {
        String id = workPackageId(key);
        try {
            Map<?, ?> response = block(webClient.get()
                    .uri("/api/v3/work_packages/{id}", id)
                    .retrieve()
                    .bodyToMono(Map.class));
            return WorkPackage.of(key, response);
        } catch (WebClientResponseException e) {
            throw failure("read work package " + key, e);
        } catch (WebClientRequestException e) {
            throw new IssueTrackerException("OpenProject unreachable");
        } catch (RuntimeException e) {
            throw timeoutOr(e, "OpenProject did not answer within " + timeout.toSeconds() + "s while reading");
        }
    }

    private static String workPackageId(String key) {
        if (key == null || !WORK_PACKAGE_ID.matcher(key).matches()) {
            throw new IssueTrackerException("Invalid OpenProject work package id: expected a number");
        }
        return key;
    }

    // --- Resolution des noms en ids (types, champ du verdict, options, priorites), memorisee par projet

    private ProjectSetup setup(String project) {
        ProjectSetup cached = setups.get(project);
        if (cached != null) {
            return cached;
        }
        ProjectSetup resolved = resolve(project);
        setups.put(project, resolved);
        return resolved;
    }

    private ProjectSetup resolve(String project) {
        log.info("Resolving OpenProject types and fields of project {}", project);
        Map<String, String> types = new LinkedHashMap<>();
        for (Map<?, ?> type : elements(get("read the types of project " + project,
                webClient.get().uri("/api/v3/projects/{project}/types", project)))) {
            if (type.get("name") instanceof String name) {
                types.put(name.toLowerCase(Locale.ROOT), selfHref(type));
            }
        }
        for (String required : List.of(epicType, storyType, subtaskType)) {
            if (!types.containsKey(required.toLowerCase(Locale.ROOT))) {
                throw new IssueTrackerException("OpenProject type '" + required + "' is not enabled in project "
                        + project + " (Project settings > Work packages > Types). " + setupHint(project));
            }
        }

        Map<?, ?> form = get("read the work package form of project " + project, webClient.post()
                .uri("/api/v3/projects/{project}/work_packages/form", project)
                .bodyValue(Map.of("_links", Map.of("type", Map.of("href",
                        types.get(storyType.toLowerCase(Locale.ROOT)))))));
        Map<?, ?> schema = form.get("_embedded") instanceof Map<?, ?> embedded
                && embedded.get("schema") instanceof Map<?, ?> s ? s : Map.of();

        String readinessKey = null;
        Map<String, String> options = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : schema.entrySet()) {
            if (entry.getKey() instanceof String key && key.startsWith("customField")
                    && entry.getValue() instanceof Map<?, ?> field
                    && readinessField.equalsIgnoreCase(String.valueOf(field.get("name")))) {
                readinessKey = key;
                for (Map<?, ?> option : allowedValues(field)) {
                    if (option.get("value") instanceof String value) {
                        options.put(value, selfHref(option));
                    }
                }
            }
        }
        if (readinessKey == null) {
            throw new IssueTrackerException("OpenProject field '" + readinessField + "' not found for type '"
                    + storyType + "' in project " + project + ". " + setupHint(project));
        }
        for (String option : List.of(ReadinessPolicy.LABEL_AI_READY, ReadinessPolicy.LABEL_VALIDATION)) {
            if (!options.containsKey(option)) {
                throw new IssueTrackerException("OpenProject field '" + readinessField + "' has no option '" + option
                        + "' in project " + project + ". " + setupHint(project));
            }
        }

        Map<String, String> priorities = new LinkedHashMap<>();
        if (schema.get("priority") instanceof Map<?, ?> priority) {
            for (Map<?, ?> value : allowedValues(priority)) {
                if (value.get("name") instanceof String name) {
                    priorities.put(name.toLowerCase(Locale.ROOT), selfHref(value));
                }
            }
        }
        return new ProjectSetup(types, readinessKey, options, priorities);
    }

    /** Les 3 reglages admin necessaires au verdict (cf. DECISIONS.md). */
    private String setupHint(String project) {
        return "Check the OpenProject setup: (1) a list custom field '" + readinessField + "' exists with the options "
                + ReadinessPolicy.LABEL_AI_READY + " and " + ReadinessPolicy.LABEL_VALIDATION
                + " (Administration > Custom fields > Work packages); (2) it is enabled in project " + project
                + " (Project settings > Work packages > Custom fields); (3) it is added to the form of the types "
                + epicType + ", " + storyType + " and " + subtaskType
                + " (Administration > Types > {type} > Form configuration)";
    }

    private Map<?, ?> get(String action, WebClient.RequestHeadersSpec<?> request) {
        try {
            Map<?, ?> response = block(request.retrieve().bodyToMono(Map.class));
            return response != null ? response : Map.of();
        } catch (WebClientResponseException e) {
            throw failure(action, e);
        } catch (WebClientRequestException e) {
            throw new IssueTrackerException("OpenProject unreachable");
        } catch (RuntimeException e) {
            throw timeoutOr(e, "OpenProject did not answer within " + timeout.toSeconds() + "s");
        }
    }

    private static List<Map<?, ?>> elements(Map<?, ?> collection) {
        return collection.get("_embedded") instanceof Map<?, ?> embedded ? maps(embedded.get("elements")) : List.of();
    }

    private static List<Map<?, ?>> allowedValues(Map<?, ?> field) {
        return field.get("_embedded") instanceof Map<?, ?> embedded ? maps(embedded.get("allowedValues")) : List.of();
    }

    private static List<Map<?, ?>> maps(Object list) {
        List<Map<?, ?>> result = new ArrayList<>();
        if (list instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> map) {
                    result.add(map);
                }
            }
        }
        return result;
    }

    private static String selfHref(Map<?, ?> resource) {
        if (resource.get("_links") instanceof Map<?, ?> links && links.get("self") instanceof Map<?, ?> self
                && self.get("href") instanceof String href) {
            return href;
        }
        throw new IssueTrackerException("OpenProject returned a resource without a self link");
    }

    // --- Utilitaires

    private String project(String project) {
        return project == null || project.isBlank() ? defaultProject : project;
    }

    private static Map<String, Object> baseLinks(String project, String typeHref) {
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("project", Map.of("href", "/api/v3/projects/" + project));
        links.put("type", Map.of("href", typeHref));
        return links;
    }

    private static void putParent(Map<String, Object> links, String parentKey) {
        if (parentKey != null && !parentKey.isBlank()) {
            links.put("parent", Map.of("href", "/api/v3/work_packages/" + workPackageId(parentKey)));
        }
    }

    /** Priorite par nom (insensible a la casse, alias Medium -> Normal) ; absente ou inconnue : defaut d'OpenProject. */
    private void putPriority(Map<String, Object> links, ProjectSetup setup, String priority) {
        if (!priorityEnabled || priority == null || priority.isBlank()) {
            return;
        }
        String name = priority.strip().toLowerCase(Locale.ROOT);
        String href = setup.priorities().get(name);
        if (href == null && PRIORITY_ALIASES.containsKey(name)) {
            href = setup.priorities().get(PRIORITY_ALIASES.get(name).toLowerCase(Locale.ROOT));
        }
        if (href == null) {
            log.warn("Unknown OpenProject priority (not among the project's priorities): the project default applies");
            return;
        }
        links.put("priority", Map.of("href", href));
    }

    /** OpenProject n'a pas de labels : labels normalises (hors verdict) en derniere ligne de la description. */
    static String withLabels(String markdown, List<String> labels) {
        List<String> kept = LabelNormalizer.normalize(labels).stream()
                .filter(label -> !ReadinessPolicy.isReservedLabel(label))
                .toList();
        String text = markdown == null ? "" : markdown;
        if (kept.isEmpty()) {
            return text;
        }
        String line = "**Labels:** " + String.join(", ", kept);
        return text.isBlank() ? line : text + "\n\n" + line;
    }

    private <T> T block(Mono<T> mono) {
        return mono.timeout(timeout).block();
    }

    private static IssueTrackerException failure(String action, WebClientResponseException e) {
        OpenProjectError error = OpenProjectError.of(e);
        log.error("OpenProject could not {}: {}", action, error.safeSummary());
        return new IssueTrackerException("OpenProject could not " + action + " (" + error.safeSummary() + ")",
                "OpenProject could not " + action + " (" + error.summary() + ")");
    }

    private static RuntimeException timeoutOr(RuntimeException e, String timeoutMessage) {
        return timeoutOr(e, timeoutMessage, "OpenProject returned an unreadable response");
    }

    /**
     * Delai depasse ou reponse 2xx illisible (le message de Jackson cite le corps recu) -> {@link IssueTrackerException}
     * sans cause ; toute autre erreur est rendue telle quelle (erreur inattendue, tracee par l'appelant).
     */
    private static RuntimeException timeoutOr(RuntimeException e, String timeoutMessage, String unreadableMessage) {
        if (e instanceof IssueTrackerException) {
            return e;
        }
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException) {
                log.error(timeoutMessage);
                return new IssueTrackerException(timeoutMessage);
            }
            if (t instanceof DecodingException) {
                log.error(unreadableMessage);
                return new IssueTrackerException(unreadableMessage);
            }
        }
        return e;
    }

    // --- Health

    /** Lecture du projet par defaut (verifie la cle et l'acces au projet), avec un delai court. */
    @Override
    public List<ComponentHealth> health() {
        if (defaultProject == null || defaultProject.isBlank()) {
            return List.of(ComponentHealth.down(HEALTH_NAME, "openproject.project not set", Map.of()));
        }
        try {
            webClient.get()
                    .uri("/api/v3/projects/{project}", defaultProject)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(HEALTH_TIMEOUT)
                    .block();
            return List.of(ComponentHealth.up(HEALTH_NAME, Map.of()));
        } catch (RuntimeException e) {
            log.warn("OpenProject health check failed: {}", healthCause(e));
            return List.of(ComponentHealth.down(HEALTH_NAME, healthCause(e), Map.of()));
        }
    }

    /** Cause courte et sure d'un echec : statut HTTP, delai depasse ou service injoignable (jamais d'URL ni de corps). */
    private static String healthCause(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof WebClientResponseException response) {
                return "HTTP " + response.getStatusCode().value();
            }
            if (t instanceof TimeoutException) {
                return "timeout";
            }
            if (t instanceof WebClientRequestException) {
                return "unreachable";
            }
        }
        return error.getClass().getSimpleName();
    }

    /** Ids resolus d'un projet : types par nom (minuscules), champ du verdict et ses options, priorites par nom. */
    record ProjectSetup(Map<String, String> types, String readinessKey, Map<String, String> options,
                        Map<String, String> priorities) {

        String typeHref(String name) {
            return types.get(name.toLowerCase(Locale.ROOT));
        }

        String optionHref(String value) {
            return options.get(value);
        }
    }

    /** Champs lus d'un work package. */
    record WorkPackage(String subject, String description, int lockVersion, String projectId, Map<?, ?> links) {

        static WorkPackage of(String key, Map<?, ?> body) {
            if (body == null || !(body.get("_links") instanceof Map<?, ?> links)) {
                throw new IssueTrackerException("OpenProject returned an unreadable work package " + key);
            }
            String description = body.get("description") instanceof Map<?, ?> d && d.get("raw") instanceof String raw
                    ? raw.strip() : "";
            int lockVersion = body.get("lockVersion") instanceof Number n ? n.intValue() : 0;
            String projectHref = links.get("project") instanceof Map<?, ?> p && p.get("href") instanceof String h
                    ? h : "";
            String projectId = projectHref.substring(projectHref.lastIndexOf('/') + 1);
            if (projectId.isBlank()) {
                throw new IssueTrackerException("OpenProject returned a work package without project: " + key);
            }
            Object subject = body.get("subject");
            return new WorkPackage(subject != null ? subject.toString() : "", description, lockVersion, projectId,
                    links);
        }

        /** Titre d'un lien (ex. valeur de l'option du champ liste), ou null s'il est vide. */
        String linkTitle(String name) {
            return links.get(name) instanceof Map<?, ?> link && link.get("title") instanceof String title
                    ? title : null;
        }
    }
}
