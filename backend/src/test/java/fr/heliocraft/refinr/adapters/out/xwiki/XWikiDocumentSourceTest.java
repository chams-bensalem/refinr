// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.xwiki;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class XWikiDocumentSourceTest {

    private static final String BOT = "xwiki:XWiki.refinr_bot";
    private static final String CDC_PATH = "/rest/wikis/xwiki/spaces/ProjetStocks/spaces/CDC/pages/WebHome";

    private MockWebServer server;
    private XWikiDocumentSource source;

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

    private XWikiDocumentSource newSource(Duration timeout) {
        return new XWikiDocumentSource(server.url("/").toString(), "refinr_bot", "test-password",
                "https://wiki.example.com/", "xwiki", timeout);
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).addHeader("Content-Type", "application/json")
                .addHeader("XWiki-User", BOT).setBody(body);
    }

    /** Page d'erreur Tomcat (HTML, quel que soit Accept), avec ou sans utilisateur reconnu. */
    private static MockResponse tomcat(int status, boolean authenticated) {
        MockResponse response = new MockResponse().setResponseCode(status).addHeader("Content-Type", "text/html")
                .setBody("<!doctype html><html><head><title>HTTP Status " + status + "</title></head>"
                        + "<body>http://internal.example.net secret detail</body></html>");
        return authenticated ? response.addHeader("XWiki-User", BOT) : response;
    }

    private static String page(String rendered, String content) {
        return "{\"id\":\"xwiki:ProjetStocks.CDC.WebHome\",\"title\":\"CDC\",\"syntax\":\"xwiki/2.1\",\"content\":"
                + quote(content) + ",\"renderedContent\":" + (rendered == null ? "null" : quote(rendered)) + "}";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Nested
    class Read {

        @Test
        @DisplayName("Reads renderedContent with supportedSyntax=html/5.0, preemptive Basic auth and Accept: application/json")
        void readsRenderedContent() throws InterruptedException {
            server.enqueue(json(200, page("<h1>CDC</h1><p>Texte</p>", "= CDC =")));

            assertEquals("<h1>CDC</h1><p>Texte</p>", source.getDocumentContent("ProjetStocks.CDC.WebHome"));

            RecordedRequest request = server.takeRequest();
            assertEquals(CDC_PATH, request.getRequestUrl().encodedPath());
            assertEquals("html/5.0", request.getRequestUrl().queryParameter("supportedSyntax"));
            assertEquals("Basic " + Base64.getEncoder().encodeToString("refinr_bot:test-password"
                    .getBytes(StandardCharsets.UTF_8)), request.getHeader("Authorization"));
            assertEquals("application/json", request.getHeader("Accept"));
            assertEquals(1, server.getRequestCount(), "credentials sent upfront, no challenge round-trip");
        }

        @Test
        @DisplayName("Escaped dots, spaces and the wiki prefix become encoded REST path segments")
        void restPathOfAnEscapedReference() throws InterruptedException {
            server.enqueue(json(200, page("<p>x</p>", "x")));

            source.getDocumentContent("xwiki:ProjetStocks.Spec v1\\.2.WebHome");

            assertEquals("/rest/wikis/xwiki/spaces/ProjetStocks/spaces/Spec%20v1.2/pages/WebHome",
                    server.takeRequest().getRequestUrl().encodedPath());
        }

        @Test
        @DisplayName("Rendering error blocks of failed macros (Java stack trace) are removed before the analysis")
        void removesRenderingErrors() {
            server.enqueue(json(200, page("<p>Avant.</p><div class=\"box xwikirenderingerror\"><p>Failed to execute the "
                    + "[include] macro</p></div><div class=\"box xwikirenderingerrordescription hidden\"><p>org.xwiki..."
                    + "Exception<br/>at Foo.java:1</p></div><p>Apres.</p>", "x")));

            assertEquals("<p>Avant.</p><p>Apres.</p>", source.getDocumentContent("ProjetStocks.CDC.WebHome"));
        }

        @Test
        @DisplayName("A page written in HTML syntax has no renderedContent: its content is used")
        void htmlSyntaxPage() {
            server.enqueue(json(200, "{\"syntax\":\"html/5.0\",\"content\":\"<p>Spec</p>\",\"renderedContent\":null}"));

            assertEquals("<p>Spec</p>", source.getDocumentContent("ProjetStocks.CDC.WebHome"));
        }

        @Test
        @DisplayName("Without WebHome: nested page Space.Page.WebHome is tried first and used when it exists")
        void withoutWebHomeNestedFirst() throws InterruptedException {
            server.enqueue(json(200, page("<p>nested</p>", "x")));

            assertEquals("<p>nested</p>", source.getDocumentContent("ProjetStocks.CDC"));
            assertTrue(server.takeRequest().getPath().startsWith(CDC_PATH + "?"));
            assertEquals(1, server.getRequestCount());
        }

        @Test
        @DisplayName("Without WebHome: on 404 of the nested page, the terminal page Space.Page is read")
        void withoutWebHomeTerminalFallback() throws InterruptedException {
            server.enqueue(tomcat(404, true));
            server.enqueue(json(200, page("<p>terminal</p>", "x")));

            assertEquals("<p>terminal</p>", source.getDocumentContent("ProjetStocks.CDC"));
            assertTrue(server.takeRequest().getPath().startsWith(CDC_PATH + "?"));
            assertTrue(server.takeRequest().getPath().startsWith("/rest/wikis/xwiki/spaces/ProjetStocks/pages/CDC?"));
        }

        @Test
        @DisplayName("Without WebHome, both missing: 404 citing the two references tried; credentials errors stop at once")
        void withoutWebHomeBothMissing() {
            server.enqueue(tomcat(404, true));
            server.enqueue(tomcat(401, true));

            DocumentSourceException e = assertThrows(DocumentSourceException.class,
                    () -> source.getDocumentContent("ProjetStocks.CDC"));

            assertEquals(DocumentSourceException.Reason.NOT_FOUND, e.reason());
            assertEquals("XWiki could not read page ProjetStocks.CDC: neither ProjetStocks.CDC.WebHome nor ProjetStocks.CDC "
                    + "was found or visible to the account (it needs the View right on the page)", e.getMessage());

            server.enqueue(tomcat(401, false));
            DocumentSourceException credentials = assertThrows(DocumentSourceException.class,
                    () -> source.getDocumentContent("ProjetStocks.CDC"));
            assertEquals("XWiki rejected the credentials (HTTP 401)", credentials.getMessage());
            assertEquals(3, server.getRequestCount(), "no terminal attempt after refused credentials");
        }

        @Test
        @DisplayName("Errors: 404, 401 with XWiki-User (no View right) -> not found; 401 without -> credentials; safe text")
        void errorsAreSafe() {
            record Case(MockResponse response, DocumentSourceException.Reason reason, String message) {}
            String notVisible = "XWiki could not read page ProjetStocks.CDC.WebHome: not found or not visible to the account "
                    + "(it needs the View right on the page) (HTTP ";
            for (Case c : List.of(
                    new Case(tomcat(404, true), DocumentSourceException.Reason.NOT_FOUND, notVisible + "404)"),
                    new Case(tomcat(401, true), DocumentSourceException.Reason.NOT_FOUND, notVisible + "401)"),
                    new Case(tomcat(401, false), DocumentSourceException.Reason.UNAVAILABLE,
                            "XWiki rejected the credentials (HTTP 401)"),
                    new Case(new MockResponse().setResponseCode(302).addHeader("Location", "http://internal.example.net"),
                            DocumentSourceException.Reason.UNAVAILABLE,
                            "XWiki could not read page ProjetStocks.CDC.WebHome (HTTP 302)"),
                    new Case(tomcat(400, true), DocumentSourceException.Reason.UNAVAILABLE,
                            "XWiki could not read page ProjetStocks.CDC.WebHome (HTTP 400)"))) {
                server.enqueue(c.response());

                DocumentSourceException e = assertThrows(DocumentSourceException.class,
                        () -> source.getDocumentContent("ProjetStocks.CDC.WebHome"));

                assertEquals(c.reason(), e.reason(), c.message());
                assertEquals(c.message(), e.getMessage());
                assertFalse(e.getMessage().contains("internal") || e.getMessage().contains("localhost"), e.getMessage());
            }
        }

        @Test
        @DisplayName("Timeout and unreachable XWiki are safe unavailable failures")
        void timeoutAndUnreachable() throws IOException {
            XWikiDocumentSource slow = newSource(Duration.ofMillis(200));
            server.enqueue(json(200, page("<p>x</p>", "x")).setBodyDelay(2, TimeUnit.SECONDS));
            assertEquals("XWiki could not read page ProjetStocks.CDC.WebHome: no answer in time",
                    assertThrows(DocumentSourceException.class, () -> slow.getDocumentContent("ProjetStocks.CDC.WebHome"))
                            .getMessage());

            String url = server.url("/").toString();
            server.shutdown();
            XWikiDocumentSource down = new XWikiDocumentSource(url, "u", "p", "", "xwiki", Duration.ofSeconds(2));
            assertEquals("XWiki is unreachable", assertThrows(DocumentSourceException.class,
                    () -> down.getDocumentContent("ProjetStocks.CDC.WebHome")).getMessage());
            server = new MockWebServer();
        }

        @Test
        @DisplayName("Empty page and image-only page reach the existing 400 without calling the model")
        void emptyPagesAreRefusedBeforeTheModel() {
            // Page vide : renderedContent vide, pas d'objet de redirection (404)
            server.enqueue(json(200, page("", "")));
            server.enqueue(tomcat(404, true));
            server.enqueue(json(200, page("<p><img src=\"http://x/schema.png\" alt=\"schema.png\"/></p>", "[[image:schema.png]]")));
            BacklogGeneratorPort generator = mock(BacklogGeneratorPort.class);
            PreviewBacklogService preview = new PreviewBacklogService(source, generator, null);

            for (String id : List.of("ProjetStocks.PageVide.WebHome", "ProjetStocks.ImageSeule.WebHome")) {
                InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> preview.preview(id));
                assertEquals("The document is empty or contains no text to analyze", e.getMessage());
            }
            verifyNoInteractions(generator);
        }
    }

    @Nested
    class Redirects {

        private static final String LOCATION = "/objects/XWiki.RedirectClass/0/properties/location";

        @Test
        @DisplayName("A renamed page (empty page with XWiki.RedirectClass) is followed to its new reference")
        void followsRenameRedirect() throws InterruptedException {
            String old = "/rest/wikis/xwiki/spaces/ProjetRH/spaces/CDC-GestionDesConges/pages/WebHome";
            server.enqueue(json(200, page("", "")));
            server.enqueue(json(200, "{\"name\":\"location\",\"value\":\"xwiki:ProjetRH.CDC-CongesV2.WebHome\"}"));
            server.enqueue(json(200, page("<h1>Congés</h1>", "= Congés =")));

            assertEquals("<h1>Congés</h1>", source.getDocumentContent("ProjetRH.CDC-GestionDesConges.WebHome"));

            assertEquals(old, server.takeRequest().getRequestUrl().encodedPath());
            assertEquals(old + LOCATION, server.takeRequest().getPath());
            assertEquals("/rest/wikis/xwiki/spaces/ProjetRH/spaces/CDC-CongesV2/pages/WebHome",
                    server.takeRequest().getRequestUrl().encodedPath());
        }

        @Test
        @DisplayName("Renamed to a missing target: 404 naming the target, and no terminal-page attempt for an id without WebHome")
        void redirectToMissingTarget() {
            server.enqueue(json(200, page("", "")));
            server.enqueue(json(200, "{\"value\":\"xwiki:ProjetRH.Disparue.WebHome\"}"));
            server.enqueue(tomcat(404, true));

            DocumentSourceException e = assertThrows(DocumentSourceException.class,
                    () -> source.getDocumentContent("ProjetRH.CDC-GestionDesConges"));

            assertEquals(DocumentSourceException.Reason.NOT_FOUND, e.reason());
            assertEquals("XWiki could not read page ProjetRH.CDC-GestionDesConges.WebHome: the page was renamed to "
                    + "ProjetRH.Disparue.WebHome, which is not found or not visible to the account (it needs the View "
                    + "right on the page)", e.getMessage());
            assertEquals(3, server.getRequestCount(), "no terminal-page attempt");
        }

        @Test
        @DisplayName("Redirect chains stop after the limit; a redirect to another wiki is not followed")
        void limits() {
            for (int i = 0; i <= XWikiDocumentSource.MAX_REDIRECTS; i++) {
                server.enqueue(json(200, page("", "")));
                server.enqueue(json(200, "{\"value\":\"xwiki:A.B" + i + ".WebHome\"}"));
            }
            assertTrue(assertThrows(DocumentSourceException.class, () -> source.getDocumentContent("A.Start.WebHome"))
                    .getMessage().endsWith(": too many redirects"));

            server.enqueue(json(200, page("", "")));
            server.enqueue(json(200, "{\"value\":\"other:A.B.WebHome\"}"));
            assertEquals("XWiki could not read page A.Start.WebHome: the page redirects outside the configured wiki",
                    assertThrows(DocumentSourceException.class, () -> source.getDocumentContent("A.Start.WebHome"))
                            .getMessage());
        }
    }

    @Nested
    class Retries {

        private ConfigurableApplicationContext context;
        private DocumentSourcePort proxied;

        @BeforeEach
        void proxy() {
            // @Retryable ne s'applique qu'a travers le proxy Spring : meme configuration AOP que l'application
            context = new SpringApplicationBuilder(RetryConfiguration.class, AopAutoConfiguration.class)
                    .web(WebApplicationType.NONE)
                    .properties("source.provider=xwiki", "spring.main.banner-mode=off")
                    .initializers(ctx -> ((GenericApplicationContext) ctx).registerBean(
                            XWikiDocumentSource.class, () -> newSource(Duration.ofSeconds(5))))
                    .run();
            proxied = context.getBean(DocumentSourcePort.class);
        }

        @AfterEach
        void close() {
            context.close();
        }

        @Test
        @DisplayName("429 then 200: the read is retried and succeeds")
        void rateLimitIsRetried() {
            server.enqueue(tomcat(429, true));
            server.enqueue(json(200, page("<p>ok</p>", "x")));

            assertEquals("<p>ok</p>", proxied.getDocumentContent("ProjetStocks.CDC.WebHome"));
            assertEquals(2, server.getRequestCount());
        }

        @Test
        @DisplayName("Persistent 503: 1 call + 2 retries, then a safe failure; 404 and 401 are not retried")
        void retriesAndNoRetries() {
            for (int i = 0; i < 3; i++) {
                server.enqueue(tomcat(503, true));
            }
            assertEquals("XWiki could not read page ProjetStocks.CDC.WebHome (HTTP 503)",
                    assertThrows(DocumentSourceException.class,
                            () -> proxied.getDocumentContent("ProjetStocks.CDC.WebHome")).getMessage());
            assertEquals(3, server.getRequestCount());

            server.enqueue(tomcat(404, true));
            server.enqueue(tomcat(401, false));
            assertThrows(DocumentSourceException.class, () -> proxied.getDocumentContent("ProjetStocks.CDC.WebHome"));
            assertThrows(DocumentSourceException.class, () -> proxied.getDocumentContent("ProjetStocks.CDC.WebHome"));
            assertEquals(5, server.getRequestCount());
        }
    }

    @Nested
    class Search {

        private static final String RESULTS = """
                {"searchResults":[
                  {"type":"page","id":"xwiki:ProjetStocks.CDC-GestionDesStocks.WebHome","title":"CDC - Gestion des stocks"},
                  {"type":"attachment","id":"xwiki:ProjetStocks.CDC.WebHome@schema.png","title":"schema.png"},
                  {"type":"page","id":"xwiki:ProjetStocks.Spec v1\\\\.2.WebHome","title":"Spec v1.2"}]}
                """;

        @Test
        @DisplayName("Without a space: database title search, raw query encoded once, same limit, pages only")
        void searchWithoutSpace() throws InterruptedException {
            server.enqueue(json(200, RESULTS));

            List<DocumentSummary> results = source.searchDocuments(null, " gestion des ");

            assertEquals(List.of(
                    new DocumentSummary("ProjetStocks.CDC-GestionDesStocks.WebHome", "CDC - Gestion des stocks",
                            "https://wiki.example.com/bin/view/ProjetStocks/CDC-GestionDesStocks/"),
                    new DocumentSummary("ProjetStocks.Spec v1\\.2.WebHome", "Spec v1.2",
                            "https://wiki.example.com/bin/view/ProjetStocks/Spec%20v1.2/")), results);
            RecordedRequest request = server.takeRequest();
            assertTrue(request.getPath().startsWith("/rest/wikis/xwiki/search?"), request.getPath());
            assertEquals("gestion des", request.getRequestUrl().queryParameter("q"));
            assertEquals("title", request.getRequestUrl().queryParameter("scope"));
            assertEquals(String.valueOf(XWikiDocumentSource.SEARCH_LIMIT), request.getRequestUrl().queryParameter("number"));
            assertFalse(request.getPath().contains("%25"), request.getPath());
        }

        @Test
        @DisplayName("With a space: Solr query, one clause per word, quoted escaped space prefix, same limit")
        void searchWithSpace() throws InterruptedException {
            server.enqueue(json(200, "{\"searchResults\":[]}"));

            source.searchDocuments("ProjetStocks.Spec v1\\.2", "CDC - Gestion");

            RecordedRequest request = server.takeRequest();
            assertTrue(request.getPath().startsWith("/rest/wikis/xwiki/query?"), request.getPath());
            assertEquals("title:*cdc* AND title:*gestion* AND space_prefix:\"ProjetStocks.Spec v1\\\\.2\"",
                    request.getRequestUrl().queryParameter("q"));
            assertEquals("solr", request.getRequestUrl().queryParameter("type"));
            assertEquals(String.valueOf(XWikiDocumentSource.SEARCH_LIMIT), request.getRequestUrl().queryParameter("number"));
            assertFalse(request.getPath().contains("%2525"), request.getPath());
        }

        @Test
        @DisplayName("A crafted title cannot add Solr clauses: only letters and digits reach the query")
        void solrInjection() {
            assertEquals("title:*x* AND title:*or* AND title:*space* AND title:*prefix* AND title:*secret* "
                    + "AND space_prefix:\"A\"", XWikiDocumentSource.solrQuery("x\" OR space_prefix:Secret", "A"));
            assertNull(XWikiDocumentSource.solrQuery("*:* \" ()", "A"));
            assertEquals("title:*congés* AND space_prefix:\"A\\\"B\"", XWikiDocumentSource.solrQuery("Congés", "A\"B"));
        }

        @Test
        @DisplayName("Search errors are safe unavailable failures (never not-found)")
        void searchErrors() {
            server.enqueue(tomcat(401, true));
            DocumentSourceException e = assertThrows(DocumentSourceException.class, () -> source.searchDocuments(null, "x"));
            assertEquals(DocumentSourceException.Reason.UNAVAILABLE, e.reason());
            assertEquals("XWiki could not search pages: the account lacks the View right (HTTP 401)", e.getMessage());

            server.enqueue(tomcat(404, true));
            assertEquals(DocumentSourceException.Reason.UNAVAILABLE,
                    assertThrows(DocumentSourceException.class, () -> source.searchDocuments("A", "x")).reason());
        }
    }

    @Nested
    class Health {

        @Test
        @DisplayName("UP only when XWiki-User is the configured account")
        void up() throws InterruptedException {
            server.enqueue(json(200, "{}"));

            assertEquals(List.of(ComponentHealth.up("xwiki", Map.of())), source.health());
            assertEquals("/rest/wikis/xwiki", server.takeRequest().getPath());
        }

        @Test
        @DisplayName("DOWN: guest access (wrong credentials answer 200 without XWiki-User), other user, HTTP status, settings")
        void down() {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
            assertEquals(List.of(ComponentHealth.down("xwiki", "credentials refused (guest access)", Map.of())), source.health());

            server.enqueue(new MockResponse().setResponseCode(200).addHeader("XWiki-User", "xwiki:XWiki.someone").setBody("{}"));
            assertEquals(List.of(ComponentHealth.down("xwiki", "unexpected XWiki user", Map.of())), source.health());

            server.enqueue(tomcat(404, true));
            assertEquals(List.of(ComponentHealth.down("xwiki", "HTTP 404", Map.of())), source.health());

            assertEquals(List.of(ComponentHealth.down("xwiki", "xwiki.username/password not set", Map.of())),
                    new XWikiDocumentSource(server.url("/").toString(), "u", "", "", "xwiki", Duration.ofSeconds(1)).health());
            assertEquals(List.of(ComponentHealth.down("xwiki", "xwiki.base-url not set", Map.of())),
                    new XWikiDocumentSource(" ", "u", "p", "", "xwiki", Duration.ofSeconds(1)).health());
            assertEquals(3, server.getRequestCount());
        }
    }

    @Test
    @DisplayName("Formats (local checks) and web links: /bin/view/A/B/ for A.B.WebHome, /bin/view/A/B without WebHome")
    void formatsAndLinks() {
        assertEquals("XWiki", source.displayName());
        assertTrue(source.acceptsDocumentId("ProjetStocks.CDC.WebHome"));
        assertTrue(source.acceptsDocumentId("xwiki:ProjetStocks.Spec v1\\.2.WebHome"));
        assertFalse(source.acceptsDocumentId("other:ProjetStocks.CDC.WebHome"));
        assertFalse(source.acceptsDocumentId("ProjetStocks/CDC"));
        assertTrue(source.acceptsSpace("ProjetStocks.Sous-espace"));
        assertFalse(source.acceptsSpace("A..B"));

        assertEquals("https://wiki.example.com/bin/view/ProjetStocks/CDC/", source.documentUrl("ProjetStocks.CDC.WebHome"));
        assertEquals("https://wiki.example.com/bin/view/ProjetStocks/CDC", source.documentUrl("ProjetStocks.CDC"));
        assertEquals("https://wiki.example.com/bin/view/ProjetStocks/Spec%20v1.2/",
                source.documentUrl("ProjetStocks.Spec v1\\.2.WebHome"));
        assertNull(source.documentUrl("A/B"));
        assertNull(new XWikiDocumentSource("http://x", "u", "p", "", "sub", Duration.ofSeconds(1)).documentUrl("A.WebHome"));
        assertEquals(0, server.getRequestCount());
    }
}
