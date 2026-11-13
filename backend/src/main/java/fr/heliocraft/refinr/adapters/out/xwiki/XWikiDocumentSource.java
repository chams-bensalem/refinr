// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.xwiki;

import fr.heliocraft.refinr.adapters.out.xwiki.XWikiReferences.DocumentRef;
import fr.heliocraft.refinr.application.port.out.DocumentSourceException;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.HealthCheckable;
import fr.heliocraft.refinr.domain.model.ComponentHealth;
import fr.heliocraft.refinr.domain.model.DocumentSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Source XWiki (API REST, compte technique en HTTP Basic), active quand {@code source.provider=xwiki}. Faits de l'API
 * verifies en v1.2 phases 0 et 3 (cf. DECISIONS.md) : HTML rendu dans {@code renderedContent} avec
 * {@code supportedSyntax=html/5.0} (macros executees, droits du compte appliques), Basic preemptif (aucun
 * {@code WWW-Authenticate}), utilisateur effectif dans l'en-tete {@code XWiki-User} (identifiants refuses = invite
 * silencieux), page interdite = 401 (jamais 403), corps d'erreur en HTML Tomcat, page renommee = page vide portant un
 * objet {@code XWiki.RedirectClass}.
 */
@Service
@Order(1)
// Seule source instanciee quand source.provider vaut xwiki ; valeur controlee au demarrage
@ConditionalOnProperty(name = "source.provider", havingValue = "xwiki")
public class XWikiDocumentSource implements DocumentSourcePort, HealthCheckable {

    private static final Logger log = LoggerFactory.getLogger(XWikiDocumentSource.class);

    static final String HEALTH_NAME = "xwiki";
    static final String MAIN_WIKI = "xwiki";
    static final String USER_HEADER = "XWiki-User";
    static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);

    // Nombre maximal de resultats d'une recherche, avec ou sans espace (meme limite que les autres sources)
    static final int SEARCH_LIMIT = 25;
    // Redirections suivies au plus (renommages successifs d'une meme page)
    static final int MAX_REDIRECTS = 3;
    // Mots du titre retenus pour la recherche Solr (un titre tres long ne fabrique pas une requete sans fin)
    private static final int MAX_WORDS = 10;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private final WebClient webClient;
    private final String wiki;
    private final String username;
    // Adresse web de l'instance (sans / final) pour les liens : xwiki.web-url si renseignee, sinon base-url
    private final String siteUrl;
    private final String missingSetting;
    private final Duration timeout;

    @Autowired
    public XWikiDocumentSource(
            @Value("${xwiki.base-url}") String baseUrl,
            @Value("${xwiki.username}") String username,
            @Value("${xwiki.password}") String password,
            @Value("${xwiki.web-url:}") String webUrl,
            @Value("${xwiki.wiki:xwiki}") String wiki) {
        this(baseUrl, username, password, webUrl, wiki, TIMEOUT);
    }

    /** Delai par appel parametrable pour les tests. */
    XWikiDocumentSource(String baseUrl, String username, String password, String webUrl, String wiki, Duration timeout) {
        String site = webUrl == null || webUrl.isBlank() ? baseUrl : webUrl;
        this.siteUrl = site == null ? "" : site.strip().replaceAll("/+$", "");
        this.wiki = wiki == null || wiki.isBlank() ? MAIN_WIKI : wiki.strip();
        this.username = username == null ? "" : username.strip();
        this.missingSetting = baseUrl == null || baseUrl.isBlank() ? "xwiki.base-url not set"
                : this.username.isEmpty() || password == null || password.isEmpty() ? "xwiki.username/password not set"
                : null;
        this.timeout = timeout;
        String credentials = Base64.getEncoder()
                .encodeToString((this.username + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl == null ? "" : baseUrl)
                // Preemptif : XWiki ne renvoie jamais de WWW-Authenticate, un client qui attend le defi resterait invite
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String displayName() {
        return "XWiki";
    }

    @Override
    public boolean acceptsSpace(String space) {
        return XWikiReferences.isSpace(space, wiki);
    }

    @Override
    public boolean acceptsDocumentId(String documentId) {
        return XWikiReferences.isDocument(documentId, wiki);
    }

    /**
     * Lien web sans appel : {@code /bin/view/A/B/} pour {@code A.B.WebHome} ; {@code /bin/view/A/B} pour un identifiant
     * sans {@code WebHome} (XWiki y resout la page imbriquee ou terminale). Wiki secondaire : forme d'URL non verifiee,
     * null.
     */
    @Override
    public String documentUrl(String documentId) {
        Optional<List<String>> segments = XWikiReferences.segments(documentId, wiki);
        if (siteUrl.isEmpty() || !wiki.equals(MAIN_WIKI) || segments.isEmpty()) {
            return null;
        }
        List<String> path = segments.get();
        boolean webHome = path.size() > 1 && path.getLast().equals(XWikiReferences.WEB_HOME);
        List<String> shown = webHome ? path.subList(0, path.size() - 1) : path;
        StringBuilder url = new StringBuilder(siteUrl).append("/bin/view");
        for (String segment : shown) {
            url.append('/').append(UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8));
        }
        return webHome ? url.append('/').toString() : url.toString();
    }

    @Retryable(
        includes = {WebClientResponseException.TooManyRequests.class,
                    WebClientResponseException.InternalServerError.class,
                    WebClientResponseException.BadGateway.class,
                    WebClientResponseException.ServiceUnavailable.class,
                    WebClientResponseException.GatewayTimeout.class},
        maxRetries = 2,
        delay = 1000,
        multiplier = 2
    )
    @Override
    public String getDocumentContent(String documentId) {
        log.info("Reading XWiki page: {}", documentId);
        List<DocumentRef> candidates = XWikiReferences.candidates(documentId, wiki);
        DocumentSourceException notFound = null;
        for (DocumentRef candidate : candidates) {
            try {
                String html = read(candidate, 0);
                log.info("Page content retrieved successfully ({})", candidate.serialize());
                return html;
            } catch (DocumentSourceException e) {
                // Echec definitif (source indisponible, ou page trouvee mais redirigee vers une cible absente) :
                // pas d'autre candidat a essayer
                if (e.reason() != DocumentSourceException.Reason.NOT_FOUND || e instanceof RedirectTargetNotFound) {
                    log.warn("XWiki read failed: {}", e.getMessage());
                    throw e;
                }
                notFound = e;
            }
        }
        if (candidates.size() > 1) {
            // Identifiant sans WebHome : page imbriquee puis page terminale essayees, les deux absentes ou invisibles
            notFound = new DocumentSourceException(DocumentSourceException.Reason.NOT_FOUND, "XWiki could not read page "
                    + documentId + ": neither " + candidates.get(0).serialize() + " nor " + candidates.get(1).serialize()
                    + " was found or visible to the account (it needs the View right on the page)", notFound);
        }
        log.warn("XWiki read failed: {}", notFound.getMessage());
        throw notFound;
    }

    /** HTML rendu d'un document (redirection de renommage suivie), sans blocs d'erreur de macro. */
    private String read(DocumentRef ref, int redirects) {
        String action = "read page " + ref.serialize();
        Map<?, ?> page;
        try {
            page = call(webClient.get().uri(pageUri(ref, "", Map.of("supportedSyntax", "html/5.0"))));
        } catch (RuntimeException e) {
            throw failure(action, e, true);
        }
        if (page == null) {
            throw unavailable("XWiki could not " + action + ": unexpected response", null);
        }
        String html = text(page.get("renderedContent"));
        if (html.isBlank()) {
            String content = text(page.get("content"));
            String syntax = text(page.get("syntax")).toLowerCase(Locale.ROOT);
            if (!content.isBlank() && syntax.startsWith("html")) {
                html = content;
            } else if (content.isBlank()) {
                Optional<DocumentRef> target = redirectTarget(ref, action);
                if (target.isPresent()) {
                    if (redirects >= MAX_REDIRECTS) {
                        throw unavailable("XWiki could not " + action + ": too many redirects", null);
                    }
                    log.info("XWiki page {} redirects to {}", ref.serialize(), target.get().serialize());
                    try {
                        return read(target.get(), redirects + 1);
                    } catch (RedirectTargetNotFound e) {
                        throw e;
                    } catch (DocumentSourceException e) {
                        if (e.reason() != DocumentSourceException.Reason.NOT_FOUND) {
                            throw e;
                        }
                        // La page existe mais a ete renommee vers une cible absente ou invisible : le dire tel quel
                        throw new RedirectTargetNotFound("XWiki could not " + action + ": the page was renamed to "
                                + target.get().serialize() + ", which is not found or not visible to the account (it "
                                + "needs the View right on the page)", e);
                    }
                }
            }
        }
        XWikiHtml.Cleaned cleaned = XWikiHtml.withoutRenderingErrors(html);
        if (cleaned.removed() > 0) {
            log.warn("Removed {} macro rendering error block(s) from XWiki page {}", cleaned.removed(), ref.serialize());
        }
        return cleaned.html();
    }

    /**
     * Cible d'une page renommee : une page vide portant un objet {@code XWiki.RedirectClass} (redirection automatique
     * de XWiki). Absent (404) : vraie page vide. Cible dans un autre wiki : non suivie.
     */
    private Optional<DocumentRef> redirectTarget(DocumentRef ref, String action) {
        Map<?, ?> property;
        try {
            property = call(webClient.get().uri(pageUri(ref, "/objects/XWiki.RedirectClass/0/properties/location", Map.of())));
        } catch (RuntimeException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof WebClientResponseException response && response.getStatusCode().value() == 404) {
                    return Optional.empty();
                }
            }
            throw failure(action, e, true);
        }
        String location = property == null ? "" : text(property.get("value"));
        if (location.isBlank()) {
            return Optional.empty();
        }
        Optional<List<String>> segments = XWikiReferences.segments(location, wiki);
        if (segments.isEmpty() || segments.get().size() < 2) {
            throw unavailable("XWiki could not " + action + ": the page redirects outside the configured wiki", null);
        }
        List<String> target = segments.get();
        return Optional.of(new DocumentRef(target.subList(0, target.size() - 1), target.getLast()));
    }

    @Retryable(
        includes = {WebClientResponseException.TooManyRequests.class,
                    WebClientResponseException.InternalServerError.class,
                    WebClientResponseException.BadGateway.class,
                    WebClientResponseException.ServiceUnavailable.class,
                    WebClientResponseException.GatewayTimeout.class},
        maxRetries = 2,
        delay = 1000,
        multiplier = 2
    )
    @Override
    public List<DocumentSummary> searchDocuments(String space, String title) {
        // Gardes defensives (le cas d'usage valide deja l'entree)
        boolean anySpace = space == null || space.isBlank();
        if (!anySpace && !XWikiReferences.isSpace(space, wiki)) {
            throw new IllegalArgumentException("Invalid space");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        log.info("Searching XWiki pages in space {} with title: {}", anySpace ? "(all)" : space, title);
        try {
            Map<?, ?> response;
            if (anySpace) {
                // Recherche en base sur le titre (sous-chaine, insensible a la casse), immediate
                response = call(webClient.get().uri(uri -> uri.path("/rest/wikis/{wiki}/search")
                        .queryParam("q", "{q}").queryParam("scope", "title").queryParam("number", SEARCH_LIMIT)
                        .build(Map.of("wiki", wiki, "q", title.strip()))));
            } else {
                // Seule recherche qui descend dans les sous-espaces : Solr, une clause par mot du titre
                String query = solrQuery(title, XWikiReferences.spaceReference(space, wiki));
                if (query == null) {
                    return List.of();
                }
                response = call(webClient.get().uri(uri -> uri.path("/rest/wikis/{wiki}/query")
                        .queryParam("q", "{q}").queryParam("type", "solr").queryParam("number", SEARCH_LIMIT)
                        .build(Map.of("wiki", wiki, "q", query))));
            }
            List<DocumentSummary> results = toSummaries(response);
            log.info("Search completed: {} result(s)", results.size());
            return results;
        } catch (DocumentSourceException e) {
            throw e;
        } catch (RuntimeException e) {
            DocumentSourceException failure = failure("search pages", e, false);
            log.warn("XWiki search failed: {}", failure.getMessage());
            throw failure;
        }
    }

    /**
     * Requete Solr : {@code title:*mot*} pour chaque mot (lettres et chiffres, en minuscules) — le champ titre est
     * decoupe en mots, et ne garder que lettres et chiffres exclut tout caractere de la syntaxe Solr — puis l'espace
     * entre guillemets ({@code \} et {@code "} echappes). Null si le titre n'a aucun mot.
     */
    static String solrQuery(String title, String spaceReference) {
        List<String> clauses = new ArrayList<>();
        Matcher words = WORD.matcher(title);
        while (words.find() && clauses.size() < MAX_WORDS) {
            clauses.add("title:*" + words.group().toLowerCase(Locale.ROOT) + "*");
        }
        if (clauses.isEmpty()) {
            return null;
        }
        String quotedSpace = spaceReference.replace("\\", "\\\\").replace("\"", "\\\"");
        return String.join(" AND ", clauses) + " AND space_prefix:\"" + quotedSpace + "\"";
    }

    /** Resultats XWiki -> resumes : pages seulement, identifiant local (forme de l'onglet Information). */
    private List<DocumentSummary> toSummaries(Map<?, ?> response) {
        if (response == null || !(response.get("searchResults") instanceof List<?> results)) {
            return List.of();
        }
        List<DocumentSummary> summaries = new ArrayList<>();
        for (Object item : results) {
            if (item instanceof Map<?, ?> result && "page".equals(result.get("type")) && result.get("id") instanceof String id) {
                String local = XWikiReferences.localId(id, wiki);
                summaries.add(new DocumentSummary(local, text(result.get("title")), documentUrl(local)));
            }
        }
        return summaries;
    }

    /** URI REST d'un document : chaque espace est un segment {@code spaces/{nom}}, valeurs encodees une seule fois. */
    private java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> pageUri(
            DocumentRef ref, String suffix, Map<String, String> query) {
        return uri -> {
            StringBuilder template = new StringBuilder("/rest/wikis/{wiki}");
            Map<String, Object> variables = new HashMap<>();
            variables.put("wiki", wiki);
            for (int i = 0; i < ref.spaces().size(); i++) {
                template.append("/spaces/{s").append(i).append('}');
                variables.put("s" + i, ref.spaces().get(i));
            }
            template.append("/pages/{page}").append(suffix);
            variables.put("page", ref.page());
            uri.path(template.toString());
            query.forEach((name, value) -> {
                uri.queryParam(name, "{q_" + name + "}");
                variables.put("q_" + name, value);
            });
            return uri.build(variables);
        };
    }

    /** GET JSON avec delai ; toute reponse hors 2xx (redirection comprise) devient une WebClientResponseException. */
    private Map<?, ?> call(WebClient.RequestHeadersSpec<?> request) {
        return request.retrieve()
                .onStatus(HttpStatusCode::is3xxRedirection, response -> response.createException().flatMap(Mono::error))
                .bodyToMono(Map.class)
                .timeout(timeout)
                .block();
    }

    /**
     * Erreur sure pour le client : action, statut HTTP et droit en cause, jamais d'URL ni de corps (pages HTML Tomcat).
     * A la lecture, 404 ou 401 <em>avec</em> {@code XWiki-User} (compte reconnu, droit de vue absent) : introuvable ou
     * invisible ; 401 <em>sans</em> {@code XWiki-User} : identifiants refuses ; le reste : source indisponible.
     */
    static DocumentSourceException failure(String action, Throwable error, boolean pageRead) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof DocumentSourceException known) {
                return known;
            }
            if (t instanceof WebClientResponseException response) {
                int status = response.getStatusCode().value();
                String user = response.getHeaders().getFirst(USER_HEADER);
                boolean authenticated = user != null && !user.isBlank();
                if (pageRead && (status == 404 || (status == 401 && authenticated))) {
                    return new DocumentSourceException(DocumentSourceException.Reason.NOT_FOUND, "XWiki could not " + action
                            + ": not found or not visible to the account (it needs the View right on the page) (HTTP "
                            + status + ")", error);
                }
                return switch (status) {
                    case 401 -> authenticated
                            ? unavailable("XWiki could not " + action + ": the account lacks the View right (HTTP 401)", error)
                            : unavailable("XWiki rejected the credentials (HTTP 401)", error);
                    case 429 -> unavailable("XWiki could not " + action + ": rate limit reached (HTTP 429)", error);
                    default -> unavailable("XWiki could not " + action + " (HTTP " + status + ")", error);
                };
            }
            if (t instanceof TimeoutException) {
                return unavailable("XWiki could not " + action + ": no answer in time", error);
            }
            if (t instanceof WebClientRequestException) {
                return unavailable("XWiki is unreachable", error);
            }
        }
        return unavailable("XWiki could not " + action + ": unexpected response", error);
    }

    /** Page trouvee, mais redirigee (renommage) vers une cible absente ou invisible : 404 definitif, sans autre essai. */
    static final class RedirectTargetNotFound extends DocumentSourceException {
        RedirectTargetNotFound(String message, Throwable cause) {
            super(Reason.NOT_FOUND, message, cause);
        }
    }

    private static DocumentSourceException unavailable(String message, Throwable cause) {
        return new DocumentSourceException(DocumentSourceException.Reason.UNAVAILABLE, message, cause);
    }

    private static String text(Object value) {
        return value instanceof String s ? s : "";
    }

    /**
     * Lecture du wiki ({@code /rest/wikis/{wiki}}) : UP seulement si {@code XWiki-User} designe le compte configure —
     * des identifiants refuses ne donnent pas d'erreur sur cette ressource, juste un acces invite.
     */
    @Override
    public List<ComponentHealth> health() {
        if (missingSetting != null) {
            return List.of(ComponentHealth.down(HEALTH_NAME, missingSetting, Map.of()));
        }
        try {
            ResponseEntity<Void> response = webClient.get()
                    .uri("/rest/wikis/{wiki}", wiki)
                    .retrieve()
                    .onStatus(HttpStatusCode::is3xxRedirection, r -> r.createException().flatMap(Mono::error))
                    .toBodilessEntity()
                    .timeout(HEALTH_TIMEOUT)
                    .block();
            String user = response == null ? null : response.getHeaders().getFirst(USER_HEADER);
            if (user == null || user.isBlank()) {
                return List.of(ComponentHealth.down(HEALTH_NAME, "credentials refused (guest access)", Map.of()));
            }
            if (!user.endsWith(":XWiki." + username)) {
                return List.of(ComponentHealth.down(HEALTH_NAME, "unexpected XWiki user", Map.of()));
            }
            return List.of(ComponentHealth.up(HEALTH_NAME, Map.of()));
        } catch (RuntimeException e) {
            log.warn("XWiki health check failed: {}", healthCause(e));
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
}
