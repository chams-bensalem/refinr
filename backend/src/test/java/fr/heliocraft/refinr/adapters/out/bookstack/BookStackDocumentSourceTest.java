// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.bookstack;

import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.DocumentSourceException;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.service.PreviewBacklogService;
import fr.heliocraft.refinr.config.RetryConfiguration;
import fr.heliocraft.refinr.domain.model.ComponentHealth;
import fr.heliocraft.refinr.domain.model.DocumentSummary;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookStackDocumentSourceTest {

    private MockWebServer server;
    private BookStackDocumentSource source;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        source = newSource(Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private BookStackDocumentSource newSource(Duration timeout) {
        return new BookStackDocumentSource(server.url("/").toString(), "test-id", "test-secret",
                "https://docs.example.com/", timeout);
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).addHeader("Content-Type", "application/json").setBody(body);
    }

    private static final String PAGE_42 = """
            {"id":42,"name":"CDC - Gestion des stocks","slug":"cdc","book_id":1,"editor":"markdown",
             "html":"<h1 id=\\"bkmrk-x\\">Gestion des stocks</h1><p>Texte</p>","raw_html":"raw","markdown":"# md"}
            """;

    @Nested
    class Read {

        @Test
        @DisplayName("Reads the html field of GET /api/pages/{id}, with the token and Accept: application/json headers")
        void readsHtmlField() throws InterruptedException {
            server.enqueue(json(200, PAGE_42));

            assertEquals("<h1 id=\"bkmrk-x\">Gestion des stocks</h1><p>Texte</p>", source.getDocumentContent("42"));

            RecordedRequest request = server.takeRequest();
            assertEquals("GET", request.getMethod());
            assertEquals("/api/pages/42", request.getPath());
            assertEquals("Token test-id:test-secret", request.getHeader("Authorization"));
            assertEquals("application/json", request.getHeader("Accept"));
        }

        @Test
        @DisplayName("Errors become safe DocumentSourceExceptions: 404 not found, 401/403/302/5xx unavailable, no URL or body")
        void errorsAreSafe() {
            record Case(MockResponse response, DocumentSourceException.Reason reason, String message) {}
            for (Case c : List.of(
                    new Case(json(404, "{\"error\":{\"message\":\"Page not found.\",\"code\":404}}"),
                            DocumentSourceException.Reason.NOT_FOUND, "BookStack could not read page 42: not found or "
                            + "not visible to the account (it needs the View permission on the page) (HTTP 404)"),
                    new Case(json(401, "{\"error\":{\"message\":\"The secret provided ... is incorrect\",\"code\":401}}"),
                            DocumentSourceException.Reason.UNAVAILABLE, "BookStack rejected the API token (HTTP 401)"),
                    new Case(json(403, "{\"error\":{\"message\":\"no API permission\",\"code\":403}}"),
                            DocumentSourceException.Reason.UNAVAILABLE, "BookStack could not read page 42: the account "
                            + "lacks the 'Access system API' permission (HTTP 403)"),
                    new Case(new MockResponse().setResponseCode(302).addHeader("Location", "http://internal.example.net"),
                            DocumentSourceException.Reason.UNAVAILABLE, "BookStack could not read page 42 (HTTP 302)"),
                    new Case(json(400, "{\"error\":{\"message\":\"secret detail\"}}"),
                            DocumentSourceException.Reason.UNAVAILABLE, "BookStack could not read page 42 (HTTP 400)"),
                    new Case(json(200, "{\"id\":42}"),
                            DocumentSourceException.Reason.UNAVAILABLE, "BookStack could not read page 42: unexpected response"))) {
                server.enqueue(c.response());

                DocumentSourceException e = assertThrows(DocumentSourceException.class, () -> source.getDocumentContent("42"));

                assertEquals(c.reason(), e.reason(), c.message());
                assertEquals(c.message(), e.getMessage());
                assertFalse(e.getMessage().contains("localhost") || e.getMessage().contains("internal"), e.getMessage());
            }
        }

        @Test
        @DisplayName("A slow answer is a safe 'no answer in time' failure")
        void timeout() {
            BookStackDocumentSource slow = newSource(Duration.ofMillis(200));
            server.enqueue(json(200, PAGE_42).setBodyDelay(2, TimeUnit.SECONDS));

            DocumentSourceException e = assertThrows(DocumentSourceException.class, () -> slow.getDocumentContent("42"));

            assertEquals(DocumentSourceException.Reason.UNAVAILABLE, e.reason());
            assertEquals("BookStack could not read page 42: no answer in time", e.getMessage());
        }

        @Test
        @DisplayName("Unreachable BookStack is a safe failure without the address")
        void unreachable() throws IOException {
            String url = server.url("/").toString();
            server.shutdown();
            BookStackDocumentSource down = new BookStackDocumentSource(url, "id", "secret", "", Duration.ofSeconds(2));

            DocumentSourceException e = assertThrows(DocumentSourceException.class, () -> down.getDocumentContent("42"));

            assertEquals("BookStack is unreachable", e.getMessage());
            server = new MockWebServer();
        }

        @Test
        @DisplayName("An empty page (or an image only) reaches the existing 400, without calling the model")
        void emptyPageIsRefusedBeforeTheModel() {
            server.enqueue(json(200, "{\"id\":7,\"html\":\"<p id=\\\"bkmrk-\\\"></p>\"}"));
            server.enqueue(json(200, "{\"id\":6,\"html\":\"<p><img src=\\\"http://x/schema.png\\\" alt=\\\"\\\"></p>\"}"));
            BacklogGeneratorPort generator = mock(BacklogGeneratorPort.class);
            PreviewBacklogService preview = new PreviewBacklogService(source, generator, null);

            for (String id : List.of("7", "6")) {
                InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> preview.preview(id));
                assertEquals("The document is empty or contains no text to analyze", e.getMessage());
            }
            verifyNoInteractions(generator);
        }
    }

    @Nested
    class Retries {

        private ConfigurableApplicationContext context;
        private DocumentSourcePort proxied;

        @BeforeEach
        void proxy() {
            // @Retryable ne s'applique qu'a travers le proxy Spring : meme configuration AOP que l'application
            // (auto-configuration Spring Boot + RetryConfiguration), adaptateur cree par sa condition source.provider
            context = new SpringApplicationBuilder(RetryConfiguration.class, AopAutoConfiguration.class)
                    .web(WebApplicationType.NONE)
                    .properties("source.provider=bookstack", "spring.main.banner-mode=off")
                    .initializers(ctx -> ((GenericApplicationContext) ctx).registerBean(
                            BookStackDocumentSource.class, () -> newSource(Duration.ofSeconds(5))))
                    .run();
            proxied = context.getBean(DocumentSourcePort.class);
            assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(proxied), "no retry proxy: " + proxied.getClass());
        }

        @AfterEach
        void close() {
            context.close();
        }

        @Test
        @DisplayName("429 then 200: the read is retried and succeeds")
        void rateLimitIsRetried() {
            server.enqueue(json(429, "{\"error\":{\"message\":\"Too many\",\"code\":429}}"));
            server.enqueue(json(200, PAGE_42));

            assertTrue(proxied.getDocumentContent("42").contains("Gestion des stocks"));
            assertEquals(2, server.getRequestCount());
        }

        @Test
        @DisplayName("Persistent 429: 1 call + 2 retries, then a safe rate-limit failure")
        void persistentRateLimit() {
            for (int i = 0; i < 3; i++) {
                server.enqueue(json(429, "{}"));
            }

            DocumentSourceException e = assertThrows(DocumentSourceException.class, () -> proxied.getDocumentContent("42"));

            assertEquals("BookStack could not read page 42: rate limit reached (HTTP 429)", e.getMessage());
            assertEquals(3, server.getRequestCount());
        }

        @Test
        @DisplayName("404 and 401 are not retried")
        void clientErrorsAreNotRetried() {
            server.enqueue(json(404, "{}"));
            server.enqueue(json(401, "{}"));

            assertThrows(DocumentSourceException.class, () -> proxied.getDocumentContent("42"));
            assertThrows(DocumentSourceException.class, () -> proxied.getDocumentContent("42"));
            assertEquals(2, server.getRequestCount());
        }
    }

    @Nested
    class Search {

        @Test
        @DisplayName("Without a book: raw query encoded once (%25…%25), same limit, results linked to /link/{id}")
        void searchWithoutBook() throws InterruptedException {
            server.enqueue(json(200, """
                    {"data":[{"id":4,"name":"CDC - Gestion des stocks","book_id":1},
                             {"id":8,"name":"CDC - Conges v2","book_id":2}],"total":2}
                    """));

            List<DocumentSummary> results = source.searchDocuments(null, " cdc ");

            assertEquals(List.of(
                    new DocumentSummary("4", "CDC - Gestion des stocks", "https://docs.example.com/link/4"),
                    new DocumentSummary("8", "CDC - Conges v2", "https://docs.example.com/link/8")), results);
            RecordedRequest request = server.takeRequest();
            String raw = request.getPath();
            assertTrue(raw.startsWith("/api/pages?"), raw);
            assertTrue(raw.contains("=%25cdc%25"), raw);
            assertFalse(raw.contains("%2525"), "double encoding: " + raw);
            assertEquals("%cdc%", request.getRequestUrl().queryParameter("filter[name:like]"));
            assertEquals(String.valueOf(BookStackDocumentSource.SEARCH_LIMIT), request.getRequestUrl().queryParameter("count"));
            assertNull(request.getRequestUrl().queryParameter("filter[book_id]"));
        }

        @Test
        @DisplayName("LIKE wildcards and backslash typed in the title are escaped, and encoded once on the wire")
        void searchEscapesLikeWildcards() throws InterruptedException {
            server.enqueue(json(200, "{\"data\":[],\"total\":0}"));

            source.searchDocuments(null, "50%_off\\");

            RecordedRequest request = server.takeRequest();
            assertEquals("%50\\%\\_off\\\\%", request.getRequestUrl().queryParameter("filter[name:like]"));
            assertFalse(request.getPath().contains("%2525"), request.getPath());
            assertEquals("%50\\%\\_off\\\\%", BookStackDocumentSource.likePattern("50%_off\\"));
        }

        @Test
        @DisplayName("With a numeric book id: one request filtered by filter[book_id], same limit as without a book")
        void searchWithBookId() throws InterruptedException {
            server.enqueue(json(200, "{\"data\":[{\"id\":4,\"name\":\"CDC\"}],\"total\":1}"));

            assertEquals(1, source.searchDocuments("1", "cdc").size());

            RecordedRequest request = server.takeRequest();
            assertTrue(request.getPath().startsWith("/api/pages?"), request.getPath());
            assertEquals("1", request.getRequestUrl().queryParameter("filter[book_id]"));
            assertEquals("%cdc%", request.getRequestUrl().queryParameter("filter[name:like]"));
            assertEquals(String.valueOf(BookStackDocumentSource.SEARCH_LIMIT), request.getRequestUrl().queryParameter("count"));
            assertEquals(1, server.getRequestCount());
        }

        @Test
        @DisplayName("With a book slug: resolved through /api/books?filter[slug]=, then pages filtered by its id")
        void searchWithBookSlug() throws InterruptedException {
            server.enqueue(json(200, "{\"data\":[{\"id\":2,\"name\":\"Projet RH\",\"slug\":\"projet-rh\"}],\"total\":1}"));
            server.enqueue(json(200, "{\"data\":[{\"id\":8,\"name\":\"CDC - Conges v2\"}],\"total\":1}"));

            assertEquals(List.of(new DocumentSummary("8", "CDC - Conges v2", "https://docs.example.com/link/8")),
                    source.searchDocuments("projet-rh", "conges"));

            RecordedRequest books = server.takeRequest();
            assertTrue(books.getPath().startsWith("/api/books?"), books.getPath());
            assertEquals("projet-rh", books.getRequestUrl().queryParameter("filter[slug]"));
            RecordedRequest pages = server.takeRequest();
            assertEquals("2", pages.getRequestUrl().queryParameter("filter[book_id]"));
            assertEquals("%conges%", pages.getRequestUrl().queryParameter("filter[name:like]"));
        }

        @Test
        @DisplayName("Unknown or invisible book slug: empty result, no page search (a warning is logged server-side)")
        void unknownBookSlug() {
            server.enqueue(json(200, "{\"data\":[],\"total\":0}"));

            assertEquals(List.of(), source.searchDocuments("inconnu", "cdc"));
            assertEquals(1, server.getRequestCount());
        }

        @Test
        @DisplayName("Search errors are safe unavailable failures (no not-found on a search)")
        void searchErrors() {
            server.enqueue(json(403, "{\"error\":{\"message\":\"no\"}}"));

            DocumentSourceException e = assertThrows(DocumentSourceException.class, () -> source.searchDocuments(null, "x"));

            assertEquals(DocumentSourceException.Reason.UNAVAILABLE, e.reason());
            assertEquals("BookStack could not search pages: the account lacks the 'Access system API' permission (HTTP 403)",
                    e.getMessage());
        }

        @Test
        @DisplayName("Defensive guards: invalid book or blank title are refused without calling BookStack")
        void guards() {
            assertThrows(IllegalArgumentException.class, () -> source.searchDocuments("Projet RH", "x"));
            assertThrows(IllegalArgumentException.class, () -> source.searchDocuments(null, " "));
            assertEquals(0, server.getRequestCount());
        }
    }

    @Nested
    class Health {

        @Test
        @DisplayName("UP when GET /api/system answers 200 with the token")
        void up() throws InterruptedException {
            server.enqueue(json(200, "{\"version\":\"v26.09.3\"}"));

            assertEquals(List.of(ComponentHealth.up("bookstack", java.util.Map.of())), source.health());
            RecordedRequest request = server.takeRequest();
            assertEquals("/api/system", request.getPath());
            assertEquals("Token test-id:test-secret", request.getHeader("Authorization"));
            assertEquals("application/json", request.getHeader("Accept"));
        }

        @Test
        @DisplayName("DOWN with a short cause: HTTP status, redirect, unreachable")
        void down() throws IOException {
            server.enqueue(json(401, "{}"));
            assertEquals(List.of(ComponentHealth.down("bookstack", "HTTP 401", java.util.Map.of())), source.health());
            server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/"));
            assertEquals(List.of(ComponentHealth.down("bookstack", "HTTP 302", java.util.Map.of())), source.health());

            String url = server.url("/").toString();
            server.shutdown();
            BookStackDocumentSource down = new BookStackDocumentSource(url, "id", "secret", "", Duration.ofSeconds(2));
            assertEquals(List.of(ComponentHealth.down("bookstack", "unreachable", java.util.Map.of())), down.health());
            server = new MockWebServer();
        }

        @Test
        @DisplayName("DOWN without any call when the token id or secret is empty")
        void emptyToken() {
            BookStackDocumentSource noToken = new BookStackDocumentSource(server.url("/").toString(), "", " ", "",
                    Duration.ofSeconds(2));

            assertEquals(List.of(ComponentHealth.down("bookstack", "bookstack.token-id/token-secret not set",
                    java.util.Map.of())), noToken.health());
            BookStackDocumentSource noBaseUrl = new BookStackDocumentSource(" ", "id", "secret", "", Duration.ofSeconds(2));
            assertEquals(List.of(ComponentHealth.down("bookstack", "bookstack.base-url not set", java.util.Map.of())),
                    noBaseUrl.health());
            assertEquals(0, server.getRequestCount());
        }
    }

    @Test
    @DisplayName("Formats (local checks): numeric page ids; book = numeric id or slug; display name BookStack")
    void formats() {
        assertEquals("BookStack", source.displayName());
        assertTrue(source.acceptsDocumentId("42"));
        for (String id : new String[] {null, "", " ", "4a", "-1", "../4", "1234567890123456789"}) {
            assertFalse(source.acceptsDocumentId(id), String.valueOf(id));
        }
        for (String book : new String[] {"1", "projet-rh", "projet_rh2"}) {
            assertTrue(source.acceptsSpace(book), book);
        }
        for (String book : new String[] {null, "", "Projet RH", "projet-rh/x", "-rh", "%", "projet.rh"}) {
            assertFalse(source.acceptsSpace(book), String.valueOf(book));
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("Web link: {web-url}/link/{id} (stable after renaming); base-url without web-url; null for an invalid id")
    void documentUrl() {
        assertEquals("https://docs.example.com/link/42", source.documentUrl("42"));
        assertNull(source.documentUrl("4a"));
        BookStackDocumentSource noWebUrl = new BookStackDocumentSource("http://bookstack:80/", "id", "secret", null,
                Duration.ofSeconds(1));
        assertEquals("http://bookstack:80/link/42", noWebUrl.documentUrl("42"));
    }
}
