package fr.heliocraft.refinr.confluence;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ConfluenceServiceTest {

    private MockWebServer mockWebServer;
    private ConfluenceService confluenceService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();

        confluenceService = new ConfluenceService(
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
        String content = confluenceService.getPageContent(pageId);

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
        String content = confluenceService.getPageContent(pageId);

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
            () -> confluenceService.getPageContent(pageId));
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
            () -> confluenceService.getPageContent(pageId));
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
        String content = confluenceService.getPageContent(pageId);

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
        String content = confluenceService.getPageContent(pageId);

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
        confluenceService.getPageContent(pageId);

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
        String content = confluenceService.getPageContent(pageId);

        // Then
        assertNotNull(content);
        assertTrue(content.length() > 5000);
        assertTrue(content.contains("This is line 999"));
    }
}
