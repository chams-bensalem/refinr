package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.adapters.in.rest.HealthCheckController;
import fr.heliocraft.refinr.adapters.in.rest.MvpController;
import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.CheckHealth;
import fr.heliocraft.refinr.application.port.in.CreateEditedBacklog;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CORS configurable (plus de {@code *}) et cle d'API optionnelle, appliques par les filtres servlet. */
class HttpSecurityConfigurationTest {

    private static final String SEARCH = "/api/v1/mvp/confluence/search?spaceKey=SPACE&title=Spec";
    private static final String HEALTH = "/api/v1/health";
    private static final String ALLOWED = "http://localhost:3000";

    @WebMvcTest({MvpController.class, HealthCheckController.class})
    @Import({HttpSecurityConfiguration.class, LoggingFilter.class})
    abstract static class WebLayer {
        @Autowired MockMvc mockMvc;
        @MockitoBean AnalyzeDocument analyzeDocument;
        @MockitoBean PreviewBacklog previewBacklog;
        @MockitoBean CreateEditedBacklog createEditedBacklog;
        @MockitoBean ReassessStory reassessStory;
        @MockitoBean SearchDocuments searchDocuments;
        @MockitoBean CheckHealth checkHealth;

        @BeforeEach
        void stubs() {
            when(searchDocuments.search("SPACE", "Spec")).thenReturn(List.of());
            when(checkHealth.check()).thenReturn(new CheckHealth.HealthReport(true, List.of()));
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
            mockMvc.perform(options("/api/v1/mvp/analyze-preview")
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
        @DisplayName("Right key: the request reaches the controller")
        void rightKeyPasses() throws Exception {
            mockMvc.perform(get(SEARCH).header("X-API-Key", "test-api-key-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Encoded or ';'-decorated paths that Spring routes to /api/** still require the key")
        void obfuscatedPathsRequireKey() throws Exception {
            for (String uri : List.of("/%61pi/v1/mvp/confluence/search?spaceKey=SPACE&title=Spec",
                    "/api;x=1/v1/mvp/confluence/search?spaceKey=SPACE&title=Spec",
                    "/api/v1/mvp;x/confluence/search?spaceKey=SPACE&title=Spec",
                    "/api/v1/health/../mvp/confluence/search?spaceKey=SPACE&title=Spec",
                    "/api/v1/health;/../mvp/confluence/search?spaceKey=SPACE&title=Spec")) {
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
            mockMvc.perform(options("/api/v1/mvp/analyze-preview")
                            .header("Origin", ALLOWED)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "content-type,x-api-key"))
                    .andExpect(status().isOk());
        }
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
}
