// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.adapters.in.rest.HealthCheckController;
import fr.heliocraft.refinr.adapters.in.rest.SourceController;
import fr.heliocraft.refinr.adapters.in.rest.TrackerController;
import fr.heliocraft.refinr.adapters.in.rest.BacklogController;
import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.CheckHealth;
import fr.heliocraft.refinr.application.port.in.CreateEditedBacklog;
import fr.heliocraft.refinr.application.port.in.DescribeSource;
import fr.heliocraft.refinr.application.port.in.DescribeTracker;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.in.ReassessStory;
import fr.heliocraft.refinr.application.port.in.SearchDocuments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CORS configurable (plus de {@code *}) et cle d'API optionnelle, appliques par les filtres servlet. */
class HttpSecurityConfigurationTest {

    private static final String SEARCH = "/api/v1/confluence/search?spaceKey=SPACE&title=Spec";
    private static final String HEALTH = "/api/v1/health";
    private static final String TRACKER = "/api/v1/tracker";
    private static final String SOURCE = "/api/v1/source";
    private static final String DOCUMENTS_SEARCH = "/api/v1/documents/search?title=Spec";
    private static final String ALLOWED = "http://localhost:3000";

    @WebMvcTest({BacklogController.class, HealthCheckController.class, TrackerController.class, SourceController.class})
    @Import({HttpSecurityConfiguration.class, LoggingFilter.class})
    abstract static class WebLayer {
        @Autowired MockMvc mockMvc;
        @MockitoBean AnalyzeDocument analyzeDocument;
        @MockitoBean PreviewBacklog previewBacklog;
        @MockitoBean CreateEditedBacklog createEditedBacklog;
        @MockitoBean ReassessStory reassessStory;
        @MockitoBean SearchDocuments searchDocuments;
        @MockitoBean DescribeTracker describeTracker;
        @MockitoBean DescribeSource describeSource;
        @MockitoBean CheckHealth checkHealth;

        @BeforeEach
        void stubs() {
            when(searchDocuments.search("SPACE", "Spec")).thenReturn(List.of());
            when(checkHealth.check()).thenReturn(new CheckHealth.HealthReport(true, List.of()));
            when(describeTracker.displayName()).thenReturn("OpenProject");
            when(describeSource.displayName()).thenReturn("Confluence");
            when(describeSource.acceptsSpace("SPACE")).thenReturn(true);
            when(searchDocuments.search(null, "Spec")).thenReturn(List.of());
        }
    }

    @Nested
    @TestPropertySource(properties = {"refinr.cors.allowed-origins=" + ALLOWED + ", https://refinr.example.com/",
            "refinr.security.api-key="})
    class WithoutApiKey extends WebLayer {

        @Test
        @DisplayName("No API key configured: requests pass without X-API-Key (local mode)")
        void requestsPassWithoutKey() throws Exception {
            mockMvc.perform(get(SEARCH)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("An allowed origin gets its own origin back, never '*'")
        void allowedOriginIsEchoed() throws Exception {
            mockMvc.perform(get(SEARCH).header("Origin", ALLOWED))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED));
            // trailing slash of the configured origin is ignored
            mockMvc.perform(get(SEARCH).header("Origin", "https://refinr.example.com"))
                    .andExpect(header().string("Access-Control-Allow-Origin", "https://refinr.example.com"));
        }

        @Test
        @DisplayName("Preflight of an allowed origin accepts the X-API-Key header")
        void preflightAllowsApiKeyHeader() throws Exception {
            mockMvc.perform(options("/api/v1/analyze-preview")
                            .header("Origin", ALLOWED)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "content-type,x-api-key"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED))
                    .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("x-api-key")));
        }

        @Test
        @DisplayName("Another origin is refused (403, no CORS header)")
        void otherOriginIsRefused() throws Exception {
            mockMvc.perform(get(SEARCH).header("Origin", "https://evil.example.org"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
            mockMvc.perform(options(SEARCH)
                            .header("Origin", "https://evil.example.org")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @TestPropertySource(properties = {"refinr.cors.allowed-origins=" + ALLOWED,
            "refinr.security.api-key=test-api-key-123"})
    class WithApiKey extends WebLayer {

        @Test
        @DisplayName("Missing key: 401 with the generic error body and correlationId, key never echoed")
        void missingKeyIsRejected() throws Exception {
            mockMvc.perform(get(SEARCH))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error").value("Missing or invalid API key"))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty())
                    .andExpect(content().string(not(containsString("test-api-key-123"))));
        }

        @Test
        @DisplayName("Wrong key: 401")
        void wrongKeyIsRejected() throws Exception {
            mockMvc.perform(get(SEARCH).header("X-API-Key", "test-api-key-12"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/tracker is protected like the other endpoints: 401 without the key, 200 with it")
        void trackerRequiresTheKey() throws Exception {
            mockMvc.perform(get(TRACKER))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Missing or invalid API key"));
            mockMvc.perform(get(TRACKER).header("X-API-Key", "test-api-key-12")).andExpect(status().isUnauthorized());
            mockMvc.perform(get(TRACKER).header("X-API-Key", "test-api-key-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("OpenProject"));
        }

        @Test
        @DisplayName("GET /api/v1/source and GET /api/v1/documents/search are protected like the other endpoints")
        void sourceEndpointsRequireTheKey() throws Exception {
            for (String uri : List.of(SOURCE, DOCUMENTS_SEARCH)) {
                mockMvc.perform(get(uri))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.error").value("Missing or invalid API key"));
                mockMvc.perform(get(uri).header("X-API-Key", "test-api-key-12")).andExpect(status().isUnauthorized());
            }
            mockMvc.perform(get(SOURCE).header("X-API-Key", "test-api-key-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Confluence"));
            mockMvc.perform(get(DOCUMENTS_SEARCH).header("X-API-Key", "test-api-key-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Text input (content) is protected like the other endpoints: 401 without or with a wrong key, 200 with it")
        void textInputRequiresTheKey() throws Exception {
            when(previewBacklog.previewText(null, "Gestion des stocks")).thenReturn(List.of());
            String body = "{\"content\":\"Gestion des stocks\"}";
            for (String uri : List.of("/api/v1/analyze-preview", "/api/v1/analyze-and-create")) {
                mockMvc.perform(post(uri).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.error").value("Missing or invalid API key"));
                mockMvc.perform(post(uri).header("X-API-Key", "test-api-key-12").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isUnauthorized());
            }
            verifyNoInteractions(previewBacklog, analyzeDocument);
            mockMvc.perform(post("/api/v1/analyze-preview").header("X-API-Key", "test-api-key-123")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            verify(previewBacklog).previewText(null, "Gestion des stocks");
        }

        @Test
        @DisplayName("Right key: the request reaches the controller")
        void rightKeyPasses() throws Exception {
            mockMvc.perform(get(SEARCH).header("X-API-Key", "test-api-key-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Encoded or ';'-decorated paths that Spring routes to /api/** still require the key")
        void obfuscatedPathsRequireKey() throws Exception {
            for (String uri : List.of("/%61pi/v1/confluence/search?spaceKey=SPACE&title=Spec",
                    "/api;x=1/v1/confluence/search?spaceKey=SPACE&title=Spec",
                    "/api/v1/confluence;x/search?spaceKey=SPACE&title=Spec",
                    "/api/v1/health/../confluence/search?spaceKey=SPACE&title=Spec",
                    "/api/v1/health;/../confluence/search?spaceKey=SPACE&title=Spec")) {
                mockMvc.perform(get(java.net.URI.create(uri)))
                        .andExpect(status().isUnauthorized());
            }
            // the exemption itself is matched on the normalized path
            mockMvc.perform(get(java.net.URI.create("/api/v1/%68ealth"))).andExpect(status().isOk());
        }

        @Test
        @DisplayName("A 401 to an allowed origin keeps its CORS header (readable by the browser)")
        void unauthorizedKeepsCorsHeader() throws Exception {
            mockMvc.perform(get(SEARCH).header("Origin", ALLOWED))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED));
        }

        @Test
        @DisplayName("Health check and CORS preflight need no key")
        void healthAndPreflightAreExempt() throws Exception {
            mockMvc.perform(get(HEALTH)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
            mockMvc.perform(get(HEALTH + "/live")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
            mockMvc.perform(options("/api/v1/analyze-preview")
                            .header("Origin", ALLOWED)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "content-type,x-api-key"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @TestPropertySource(properties = {"refinr.security.api-key=",
            "refinr.security.allowed-hosts=localhost,127.0.0.1,[::1],refinr.example.com"})
    class AllowedHosts extends WebLayer {

        @Test
        @DisplayName("Legitimate Host headers are accepted, with or without port, any case")
        void legitimateHostsAreAccepted() throws Exception {
            for (String host : List.of("localhost", "localhost:8080", "127.0.0.1:8080", "[::1]", "[::1]:3000",
                    "Refinr.Example.com", "refinr.example.com:443")) {
                mockMvc.perform(get(SEARCH).header("Host", host)).andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("Any other Host (DNS rebinding) gets 403, even without API key and on the health probes")
        void otherHostsAreRefused() throws Exception {
            for (String path : List.of(SEARCH, HEALTH, HEALTH + "/live")) {
                mockMvc.perform(get(path).header("Host", "evil.example"))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.success").value(false))
                        .andExpect(jsonPath("$.error").value("Host not allowed"))
                        .andExpect(jsonPath("$.correlationId").isNotEmpty());
            }
            for (String host : List.of("evil.example:8080", "localhost.evil.example", "127.0.0.1.nip.io", "[::2]")) {
                mockMvc.perform(get(HEALTH + "/live").header("Host", host)).andExpect(status().isForbidden());
            }
        }
    }

    @Test
    @DisplayName("'*' or an empty allowed-hosts list is refused at startup")
    void wildcardOrEmptyHostsAreRefused() {
        HttpSecurityConfiguration config = new HttpSecurityConfiguration();
        assertThatThrownBy(() -> config.allowedHostFilter("localhost,*", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("'*' is not allowed");
        assertThatThrownBy(() -> config.allowedHostFilter("localhost,*.example.com", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("'*' is not allowed");
        assertThatThrownBy(() -> config.allowedHostFilter(" , ", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("is empty");
    }

    @Nested
    @TestPropertySource(properties = {"refinr.cors.allowed-origins=", "refinr.security.api-key="})
    class WithoutCorsOrigins extends WebLayer {

        @Test
        @DisplayName("No origin configured: same-origin calls work, cross-origin calls get no CORS header")
        void noCrossOrigin() throws Exception {
            mockMvc.perform(get(SEARCH)).andExpect(status().isOk());
            mockMvc.perform(get(SEARCH).header("Origin", ALLOWED))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Test
    @DisplayName("'*' is refused: origins must be explicit")
    void wildcardOriginIsRefused() {
        assertThatThrownBy(() -> HttpSecurityConfiguration.parseOrigins("http://localhost:3000, *"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'*' is not allowed");
        assertThat(HttpSecurityConfiguration.parseOrigins(" ")).isEmpty();
    }

    @Nested
    @TestPropertySource(properties = {"refinr.cors.allowed-origins=" + ALLOWED,
            "refinr.security.api-key=test-api-key-123", "refinr.http.max-body-bytes=64"})
    class BodyLimit extends WebLayer {

        @Test
        @DisplayName("Body over refinr.http.max-body-bytes: 413 before the API key check and the controller, CORS headers kept")
        void bodyOverTheLimitIs413() throws Exception {
            String big = "{\"content\":\"" + "a".repeat(64) + "\"}";
            mockMvc.perform(post("/api/v1/analyze-preview").header("Origin", ALLOWED)
                            .contentType(MediaType.APPLICATION_JSON).content(big))
                    .andExpect(status().isContentTooLarge())
                    .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED))
                    .andExpect(jsonPath("$.error").value("The request body exceeds the maximum size of 64 bytes"));
            verifyNoInteractions(previewBacklog);
        }

        @Test
        @DisplayName("Body under the limit: unchanged behaviour (API key still required)")
        void bodyUnderTheLimitPasses() throws Exception {
            when(previewBacklog.previewText(null, "Stocks")).thenReturn(List.of());
            String small = "{\"content\":\"Stocks\"}";
            mockMvc.perform(post("/api/v1/analyze-preview").contentType(MediaType.APPLICATION_JSON).content(small))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/analyze-preview").header("X-API-Key", "test-api-key-123")
                            .contentType(MediaType.APPLICATION_JSON).content(small))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("A non-positive refinr.http.max-body-bytes fails at startup with the property name")
    void nonPositiveBodyLimitFailsAtStartup() {
        assertThatThrownBy(() -> new HttpSecurityConfiguration().requestBodyLimitFilter(0, new tools.jackson.databind.ObjectMapper()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refinr.http.max-body-bytes");
    }
}
