// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.confluence;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;

import fr.heliocraft.refinr.domain.model.DocumentSummary;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfluenceDocumentSourceTest {

    private MockWebServer mockWebServer;
    private ConfluenceDocumentSource confluenceService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();

        confluenceService = new ConfluenceDocumentSource(
            WebClient.builder().baseUrl(baseUrl).build(),
            "test@example.com",
            "fake-token"
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
            .setBody(body)
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200);
    }

    @Test
    @DisplayName("Should fetch page content successfully")
    void shouldFetchPageContentSuccessfully() throws InterruptedException {
        // Given
        String pageId = "123456";
        String mockConfluenceResponse = """
            {
              "id": "123456",
              "type": "page",
              "title": "Refinr - Cahier des Charges",
              "body": {
                "storage": {
                  "value": "<h1>Projet Refinr</h1><p>Module Inventaire</p>",
                  "representation": "storage"
                }
              }
            }
            """;

        mockWebServer.enqueue(jsonResponse(mockConfluenceResponse));

        // When
        String content = confluenceService.getDocumentContent(pageId);

        // Then
        assertNotNull(content);
        assertTrue(content.contains("Projet Refinr"));
        assertTrue(content.contains("Module Inventaire"));

        // Verify request
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertTrue(request.getPath().contains(pageId));
        assertTrue(request.getPath().contains("expand=body.storage"));
    }

    @Test
    @DisplayName("Should return HTML content as-is")
    void shouldReturnHTMLContentAsIs() throws InterruptedException {
        // Given
        String pageId = "123";
        String mockResponse = """
            {
              "id": "123",
              "body": {
                "storage": {
                  "value": "<h1>Title</h1><p>Some <strong>bold</strong> text</p>"
                }
              }
            }
            """;

        mockWebServer.enqueue(jsonResponse(mockResponse));

        // When
        String content = confluenceService.getDocumentContent(pageId);

        // Then
        assertNotNull(content);
        assertTrue(content.contains("Title"));
        assertTrue(content.contains("bold"));
    }

    @Test
    @DisplayName("Should handle page not found error")
    void shouldHandlePageNotFoundError() {
        // Given
        String pageId = "999999";

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(404)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"message\":\"Page not found\"}"));

        // When/Then
        assertThrows(RuntimeException.class,
            () -> confluenceService.getDocumentContent(pageId));
    }

    @Test
    @DisplayName("Should handle authentication error")
    void shouldHandleAuthenticationError() {
        // Given
        String pageId = "123";

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(401)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"message\":\"Unauthorized\"}"));

        // When/Then
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> confluenceService.getDocumentContent(pageId));
        assertTrue(exception.getMessage().contains("401"));
    }

    @Test
    @DisplayName("Should handle empty page content")
    void shouldHandleEmptyPageContent() throws InterruptedException {
        // Given
        String pageId = "123";
        String mockResponse = """
            {
              "id": "123",
              "body": {
                "storage": {
                  "value": ""
                }
              }
            }
            """;

        mockWebServer.enqueue(jsonResponse(mockResponse));

        // When
        String content = confluenceService.getDocumentContent(pageId);

        // Then
        assertNotNull(content);
        assertTrue(content.isEmpty() || content.isBlank());
    }

    @Test
    @DisplayName("Should handle special characters in content")
    void shouldHandleSpecialCharactersInContent() throws InterruptedException {
        // Given
        String pageId = "123";
        String mockResponse = """
            {
              "id": "123",
              "body": {
                "storage": {
                  "value": "<p>Caractères spéciaux: é à ù €</p>"
                }
              }
            }
            """;

        mockWebServer.enqueue(jsonResponse(mockResponse));

        // When
        String content = confluenceService.getDocumentContent(pageId);

        // Then
        assertNotNull(content);
        assertTrue(content.contains("é"));
        assertTrue(content.contains("€"));
    }

    @Test
    @DisplayName("Should use correct request method")
    void shouldUseCorrectRequestMethod() throws InterruptedException {
        // Given
        String pageId = "123";

        mockWebServer.enqueue(jsonResponse("{\"id\":\"123\",\"body\":{\"storage\":{\"value\":\"test\"}}}"));

        // When
        confluenceService.getDocumentContent(pageId);

        // Then
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertTrue(request.getPath().contains(pageId));
    }

    @Test
    @DisplayName("Should handle large page content")
    void shouldHandleLargePageContent() throws InterruptedException {
        // Given
        String pageId = "123";
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("This is line ").append(i).append(". ");
        }

        String mockResponse = """
            {
              "id": "123",
              "body": {
                "storage": {
                  "value": "%s"
                }
              }
            }
            """.formatted(largeContent.toString());

        mockWebServer.enqueue(jsonResponse(mockResponse));

        // When
        String content = confluenceService.getDocumentContent(pageId);

        // Then
        assertNotNull(content);
        assertTrue(content.length() > 5000);
        assertTrue(content.contains("This is line 999"));
    }

    @Test
    @DisplayName("Search maps each Confluence result to a DocumentSummary (id, title, absolute url)")
    void searchMapsResultsToDocumentSummaries() throws InterruptedException {
        mockWebServer.enqueue(jsonResponse("""
            {
              "results": [
                {"id": "111", "type": "page", "title": "Cahier des charges",
                 "_links": {"webui": "/spaces/SPACE/pages/111/Cahier+des+charges"}},
                {"id": "222", "type": "page", "title": "Cahier v2",
                 "_links": {"webui": "/spaces/SPACE/pages/222/Cahier+v2"}}
              ],
              "start": 0, "limit": 25, "size": 2,
              "_links": {"base": "https://your-site.atlassian.net/wiki", "context": "/wiki"}
            }
            """));

        List<DocumentSummary> results = confluenceService.searchDocuments("SPACE", "Cahier");

        assertEquals(List.of(
            new DocumentSummary("111", "Cahier des charges",
                "https://your-site.atlassian.net/wiki/spaces/SPACE/pages/111/Cahier+des+charges"),
            new DocumentSummary("222", "Cahier v2",
                "https://your-site.atlassian.net/wiki/spaces/SPACE/pages/222/Cahier+v2")), results);

        // Le contenu des pages n'est plus demande : seul le resume est renvoye
        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue(request.getPath().startsWith("/wiki/rest/api/content/search?"), request.getPath());
        assertFalse(request.getPath().contains("body.storage"), request.getPath());
    }

    @Test
    @DisplayName("Search without results returns an empty list")
    void searchWithoutResultsReturnsEmptyList() {
        mockWebServer.enqueue(jsonResponse("{\"results\": [], \"size\": 0}"));

        assertEquals(List.of(), confluenceService.searchDocuments("SPACE", "absent"));
    }

    @Test
    @DisplayName("Search escapes quotes and backslashes of the title inside the CQL string")
    void searchEscapesTitleInCql() throws InterruptedException {
        mockWebServer.enqueue(jsonResponse("{\"results\": []}"));

        confluenceService.searchDocuments("SPACE", "x\" OR space=\"OTHER\\");

        String cql = mockWebServer.takeRequest().getRequestUrl().queryParameter("cql");
        assertEquals("space=\"SPACE\" AND title~\"x\\\" OR space=\\\"OTHER\\\\\"", cql);
    }

    @Test
    @DisplayName("Search rejects a space key that is not a Confluence space key, without calling Confluence")
    void searchRejectsInvalidSpaceKey() {
        // Reponses prevues pour ne pas bloquer si la validation manquait : le test echoue alors sur les assertions
        mockWebServer.enqueue(jsonResponse("{\"results\": []}"));
        mockWebServer.enqueue(jsonResponse("{\"results\": []}"));

        assertThrows(IllegalArgumentException.class,
            () -> confluenceService.searchDocuments("SPACE OR type=page", "cahier"));
        assertThrows(IllegalArgumentException.class,
            () -> confluenceService.searchDocuments("SPACE", "  "));
        assertEquals(0, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("Search accepts personal space keys (~accountId)")
    void searchAcceptsPersonalSpaceKey() throws InterruptedException {
        mockWebServer.enqueue(jsonResponse("{\"results\": []}"));

        confluenceService.searchDocuments("~557058:0a1b2c3d-4e5f", "notes");

        assertEquals("space=\"~557058:0a1b2c3d-4e5f\" AND title~\"notes\"",
            mockWebServer.takeRequest().getRequestUrl().queryParameter("cql"));
    }

    @Test
    @DisplayName("Search without a space uses only the title clause, with the same escaping: a crafted title cannot add clauses")
    void searchWithoutSpaceEscapesTitle() throws InterruptedException {
        mockWebServer.enqueue(jsonResponse("{\"results\": []}"));
        mockWebServer.enqueue(jsonResponse("{\"results\": []}"));

        confluenceService.searchDocuments(null, "x\" OR space=\"SECRET");
        confluenceService.searchDocuments("  ", "x\" OR space=\"SECRET\\");

        assertEquals("title~\"x\\\" OR space=\\\"SECRET\"",
            mockWebServer.takeRequest().getRequestUrl().queryParameter("cql"));
        assertEquals("title~\"x\\\" OR space=\\\"SECRET\\\\\"",
            mockWebServer.takeRequest().getRequestUrl().queryParameter("cql"));
    }

    @Test
    @DisplayName("Search sends the same result limit with and without a space")
    void searchLimitIsTheSameWithAndWithoutSpace() throws InterruptedException {
        mockWebServer.enqueue(jsonResponse("{\"results\": []}"));
        mockWebServer.enqueue(jsonResponse("{\"results\": []}"));

        confluenceService.searchDocuments("SPACE", "spec");
        confluenceService.searchDocuments(null, "spec");

        String withSpace = mockWebServer.takeRequest().getRequestUrl().queryParameter("limit");
        String withoutSpace = mockWebServer.takeRequest().getRequestUrl().queryParameter("limit");
        assertEquals(String.valueOf(ConfluenceDocumentSource.SEARCH_LIMIT), withSpace);
        assertEquals(withSpace, withoutSpace);
        assertEquals("25", withoutSpace);
    }

    @Test
    @DisplayName("Search without a space still rejects a blank title, without calling Confluence")
    void searchWithoutSpaceRejectsBlankTitle() {
        assertThrows(IllegalArgumentException.class, () -> confluenceService.searchDocuments(null, " "));
        assertEquals(0, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("Formats: numeric page ids and Confluence space keys only; display name is Confluence")
    void formats() {
        assertEquals("Confluence", confluenceService.displayName());
        assertTrue(confluenceService.acceptsDocumentId("123456789"));
        for (String id : new String[] {null, "", " ", "12a", "-1", "1 2", "../1", "123456789012345678901"}) {
            assertFalse(confluenceService.acceptsDocumentId(id), String.valueOf(id));
        }
        assertTrue(confluenceService.acceptsSpace("SPACE"));
        assertTrue(confluenceService.acceptsSpace("~557058:0a1b2c3d-4e5f"));
        for (String space : new String[] {null, "", "SPACE OR type=page", "Projet.Sous", "\"x\""}) {
            assertFalse(confluenceService.acceptsSpace(space), String.valueOf(space));
        }
    }

    @Test
    @DisplayName("Web link of a page: {site}/wiki/pages/viewpage.action?pageId={id}; null without site URL or for an invalid id")
    void documentUrl() {
        ConfluenceDocumentSource source = new ConfluenceDocumentSource(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(), "https://your-site.atlassian.net/");

        assertEquals("https://your-site.atlassian.net/wiki/pages/viewpage.action?pageId=123",
            source.documentUrl("123"));
        assertNull(source.documentUrl("12a"));
        assertNull(source.documentUrl(null));
        assertNull(confluenceService.documentUrl("123"));
    }
}
