package fr.heliocraft.refinr.adapters.in.rest;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Health check : l'etat de chaque service, sans jamais renvoyer le message brut d'une exception (URL, corps...). */
@WebMvcTest(HealthCheckController.class)
class HealthCheckControllerTest {

    private static final MockWebServer server = new MockWebServer();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath().startsWith("/wiki")) {
                    return new MockResponse().setResponseCode(401).setBody("token=abc rejected");
                }
                return new MockResponse().setResponseCode(404).setBody("{\"errorMessages\":[\"no project\"]}");
            }
        });
        server.start();
        registry.add("jira.project-key", () -> "TEST");
        registry.add("ai.provider", () -> "ollama");
        // Port ferme : Ollama injoignable
        registry.add("ollama.base-url", () -> "http://127.0.0.1:1");
    }

    @AfterAll
    static void stop() throws IOException {
        server.shutdown();
    }

    @TestConfiguration
    static class Clients {
        @Bean
        WebClient confluenceClient() {
            return WebClient.create(server.url("/").toString());
        }

        @Bean
        WebClient jiraClient() {
            return WebClient.create(server.url("/").toString());
        }

        @Bean
        WebClient geminiClient() {
            return WebClient.create(server.url("/").toString());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Reports DOWN services with a sanitized cause (HTTP status or unreachable), never the raw message")
    void reportsSanitizedCauses() throws Exception {
        String body = mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.services.confluence.status").value("DOWN"))
                .andExpect(jsonPath("$.services.confluence.error").value("HTTP 401"))
                .andExpect(jsonPath("$.services.jira.error").value("HTTP 404"))
                .andExpect(jsonPath("$.services.ai.error").value("unreachable"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("127.0.0.1"), body);
        assertFalse(body.contains(String.valueOf(server.getPort())), body);
        assertFalse(body.contains("token=abc"), body);
    }
}
