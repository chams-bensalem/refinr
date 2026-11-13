// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.confluence;

import fr.heliocraft.refinr.domain.model.ComponentHealth;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ConfluenceHealthTest {

    private MockWebServer server;
    private ConfluenceDocumentSource source;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        source = new ConfluenceDocumentSource(WebClient.create(server.url("/").toString()), "u", "t");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("UP when the Confluence REST API answers")
    void up() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        assertEquals(ComponentHealth.up("confluence", java.util.Map.of()), source.health().get(0));
        assertEquals("/wiki/rest/api/content?limit=1", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("DOWN with a sanitized cause: HTTP status only, never the body nor the URL")
    void downSanitized() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("token=abc rejected"));

        ComponentHealth health = source.health().get(0);

        assertFalse(health.up());
        assertEquals("HTTP 401", health.details().get("error"));
        assertFalse(health.toString().contains("token=abc"));
        assertFalse(health.toString().contains(String.valueOf(server.getPort())));
    }
}
