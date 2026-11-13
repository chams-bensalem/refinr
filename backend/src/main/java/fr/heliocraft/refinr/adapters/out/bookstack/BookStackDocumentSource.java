// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.bookstack;

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
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Source BookStack (API REST a jetons), active quand {@code source.provider=bookstack}. Faits de l'API verifies en v1.2
 * phase 0 (cf. DECISIONS.md) : contenu lu dans le champ {@code html} de {@code GET /api/pages/{id}} (inclusions
 * developpees, sans permission d'export), {@code Accept: application/json} obligatoire (sinon un refus redirige vers
 * l'accueil), page non visible = 404, lien stable {@code /link/{id}}.
 */
@Service
@Order(1)
// Seule source instanciee quand source.provider vaut bookstack ; valeur controlee au demarrage
@ConditionalOnProperty(name = "source.provider", havingValue = "bookstack")
public class BookStackDocumentSource implements DocumentSourcePort, HealthCheckable {

    private static final Logger log = LoggerFactory.getLogger(BookStackDocumentSource.class);

    static final String HEALTH_NAME = "bookstack";
    static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);

    // Nombre maximal de resultats d'une recherche, avec ou sans livre (meme limite que les autres sources)
    static final int SEARCH_LIMIT = 25;

    private final WebClient webClient;
    // Adresse web de l'instance (sans / final) pour les liens : bookstack.web-url si renseignee, sinon base-url
    private final String siteUrl;
    private final boolean baseUrlSet;
    private final boolean tokenSet;
    private final Duration timeout;

    @Autowired
    public BookStackDocumentSource(
            @Value("${bookstack.base-url}") String baseUrl,
            @Value("${bookstack.token-id}") String tokenId,
            @Value("${bookstack.token-secret}") String tokenSecret,
            @Value("${bookstack.web-url:}") String webUrl) {
        this(baseUrl, tokenId, tokenSecret, webUrl, TIMEOUT);
    }

    /** Delai par appel parametrable pour les tests. */
    BookStackDocumentSource(String baseUrl, String tokenId, String tokenSecret, String webUrl, Duration timeout) {
        String site = webUrl == null || webUrl.isBlank() ? baseUrl : webUrl;
        this.siteUrl = site == null ? "" : site.strip().replaceAll("/+$", "");
        this.baseUrlSet = baseUrl != null && !baseUrl.isBlank();
        this.tokenSet = tokenId != null && !tokenId.isBlank() && tokenSecret != null && !tokenSecret.isBlank();
        this.timeout = timeout;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Token " + tokenId + ":" + tokenSecret)
                // Sans lui, BookStack repond a un refus de permission par une redirection 302 vers l'accueil
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String displayName() {
        return "BookStack";
    }

    @Override
    public boolean acceptsSpace(String space) {
        return BookStackIds.isBook(space);
    }

    @Override
    public boolean acceptsDocumentId(String documentId) {
        return BookStackIds.isPageId(documentId);
    }

    /** Permalien stable par id ({@code /link/{id}} redirige vers l'adresse courante de la page, meme apres renommage). */
    @Override
    public String documentUrl(String documentId) {
        if (siteUrl.isEmpty() || !BookStackIds.isPageId(documentId)) {
            return null;
        }
        return siteUrl + "/link/" + documentId;
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
    public String getDocumentContent(String pageId) {
        log.info("Reading BookStack page: {}", pageId);
        String action = "read page " + pageId;
        try {
            Map<?, ?> page = call(webClient.get().uri("/api/pages/{id}", pageId));
            if (page == null || !(page.get("html") instanceof String html)) {
                throw unexpected(action);
            }
            log.info("Page content retrieved successfully");
            return html;
        } catch (DocumentSourceException e) {
            throw e;
        } catch (RuntimeException e) {
            DocumentSourceException failure = failure(action, e, true);
            log.warn("BookStack read failed: {}", failure.getMessage());
            throw failure;
        }
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
    public List<DocumentSummary> searchDocuments(String book, String title) {
        // Gardes defensives (le cas d'usage valide deja l'entree)
        boolean anyBook = book == null || book.isBlank();
        if (!anyBook && !BookStackIds.isBook(book)) {
            throw new IllegalArgumentException("Invalid book");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        log.info("Searching BookStack pages in book {} with title: {}", anyBook ? "(all)" : book, title);
        try {
            String bookId = anyBook ? null : bookId(book);
            if (!anyBook && bookId == null) {
                // Slug inconnu ou livre non visible (droit book-view-all) : aucun resultat, signale cote serveur
                log.warn("BookStack book '{}' not found or not visible to the account (a book slug needs the "
                        + "book-view-all permission; prefer the numeric book id)", book);
                return List.of();
            }
            Map<?, ?> response = call(webClient.get().uri(uri -> {
                uri.path("/api/pages")
                        .queryParam("filter[name:like]", "{like}")
                        .queryParam("count", SEARCH_LIMIT);
                if (bookId != null) {
                    uri.queryParam("filter[book_id]", "{book}");
                }
                return uri.build(bookId != null
                        ? Map.of("like", likePattern(title), "book", bookId)
                        : Map.of("like", likePattern(title)));
            }));
            List<DocumentSummary> results = toSummaries(response);
            log.info("Search completed: {} result(s)", results.size());
            return results;
        } catch (DocumentSourceException e) {
            throw e;
        } catch (RuntimeException e) {
            DocumentSourceException failure = failure("search pages", e, false);
            log.warn("BookStack search failed: {}", failure.getMessage());
            throw failure;
        }
    }

    /** Id du livre : tel quel s'il est numerique, sinon resolu par son slug ; null si aucun livre visible ne correspond. */
    private String bookId(String book) {
        if (BookStackIds.isNumericId(book)) {
            return book;
        }
        Map<?, ?> response = call(webClient.get().uri(uri -> uri.path("/api/books")
                .queryParam("filter[slug]", "{slug}")
                .queryParam("count", 1)
                .build(Map.of("slug", book))));
        if (response != null && response.get("data") instanceof List<?> data && !data.isEmpty()
                && data.getFirst() instanceof Map<?, ?> found && found.get("id") != null) {
            return String.valueOf(found.get("id"));
        }
        return null;
    }

    /**
     * Motif {@code LIKE} « contient » : les jokers saisis ({@code %}, {@code _}) et l'echappement ({@code \}) sont
     * echappes pour etre cherches tels quels (verifie contre BookStack : {@code %\_%} ne trouve que les titres avec _).
     */
    static String likePattern(String title) {
        String escaped = title.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** Pages BookStack -> resumes, lien = permalien stable {@code /link/{id}}. */
    private List<DocumentSummary> toSummaries(Map<?, ?> response) {
        if (response == null || !(response.get("data") instanceof List<?> data)) {
            return List.of();
        }
        List<DocumentSummary> summaries = new ArrayList<>();
        for (Object item : data) {
            if (item instanceof Map<?, ?> page && page.get("id") != null) {
                String id = String.valueOf(page.get("id"));
                summaries.add(new DocumentSummary(id, page.get("name") instanceof String n ? n : "", documentUrl(id)));
            }
        }
        return summaries;
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
     * Erreur sure pour le client : action, statut HTTP et droit en cause, jamais d'URL ni de corps de reponse.
     * 404 sur une page : introuvable ou non visible (indiscernables pour BookStack) ; le reste : source indisponible.
     */
    static DocumentSourceException failure(String action, Throwable error, boolean pageRead) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof WebClientResponseException response) {
                int status = response.getStatusCode().value();
                return switch (status) {
                    case 404 -> pageRead
                            ? new DocumentSourceException(DocumentSourceException.Reason.NOT_FOUND, "BookStack could not "
                                    + action + ": not found or not visible to the account (it needs the View permission "
                                    + "on the page) (HTTP 404)", error)
                            : unavailable("BookStack could not " + action + " (HTTP 404)", error);
                    case 401 -> unavailable("BookStack rejected the API token (HTTP 401)", error);
                    case 403 -> unavailable("BookStack could not " + action + ": the account lacks the "
                            + "'Access system API' permission (HTTP 403)", error);
                    case 429 -> unavailable("BookStack could not " + action + ": rate limit reached (HTTP 429)", error);
                    default -> unavailable("BookStack could not " + action + " (HTTP " + status + ")", error);
                };
            }
            if (t instanceof TimeoutException) {
                return unavailable("BookStack could not " + action + ": no answer in time", error);
            }
            if (t instanceof WebClientRequestException) {
                return unavailable("BookStack is unreachable", error);
            }
        }
        return unavailable("BookStack could not " + action + ": unexpected response", error);
    }

    private static DocumentSourceException unexpected(String action) {
        return unavailable("BookStack could not " + action + ": unexpected response", null);
    }

    private static DocumentSourceException unavailable(String message, Throwable cause) {
        return new DocumentSourceException(DocumentSourceException.Reason.UNAVAILABLE, message, cause);
    }

    /** Lecture de {@code /api/system} (verifie le jeton et le droit d'acces a l'API), avec un delai court. */
    @Override
    public List<ComponentHealth> health() {
        if (!baseUrlSet) {
            return List.of(ComponentHealth.down(HEALTH_NAME, "bookstack.base-url not set", Map.of()));
        }
        if (!tokenSet) {
            return List.of(ComponentHealth.down(HEALTH_NAME, "bookstack.token-id/token-secret not set", Map.of()));
        }
        try {
            webClient.get()
                    .uri("/api/system")
                    .retrieve()
                    .onStatus(HttpStatusCode::is3xxRedirection, response -> response.createException().flatMap(Mono::error))
                    .toBodilessEntity()
                    .timeout(HEALTH_TIMEOUT)
                    .block();
            return List.of(ComponentHealth.up(HEALTH_NAME, Map.of()));
        } catch (RuntimeException e) {
            log.warn("BookStack health check failed: {}", healthCause(e));
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
