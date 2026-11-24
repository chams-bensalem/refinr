// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.adapters.out.confluence.ConfluenceIds;
import fr.heliocraft.refinr.adapters.out.jira.JiraProjectKeys;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort.FeatureLevel;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.application.port.out.TaskGeneratorPort;
import fr.heliocraft.refinr.config.JobConfiguration;
import fr.heliocraft.refinr.config.UseCaseConfig;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import fr.heliocraft.refinr.domain.model.DocumentSummary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * X1 : contexte de la configuration dans le champ {@code context} des reponses : {@code project} (projet de la requete
 * ou du ticket, {@code refinr.context.projects.TEST}) et {@code default} ({@code refinr.context.document-id}). Source
 * documentaire simulee par une vraie classe (la configuration est validee au demarrage).
 */
@WebMvcTest({BacklogController.class, JobController.class})
@Import({UseCaseConfig.class, JobConfiguration.class, ConfiguredContextContractTest.Source.class})
@TestPropertySource(properties = {"jira.base-url=http://localhost:8080", "jira.project-key=TEST",
        "refinr.context.projects.TEST=777", "refinr.context.document-id=888"})
class ConfiguredContextContractTest {

    static final String PROJECT_TEXT = "Contexte-projet-TEST-marqueur";
    static final String DEFAULT_TEXT = "Contexte-par-defaut-marqueur!";

    /** Source simulee : pages 777 (projet TEST) et 888 (par defaut). */
    @TestConfiguration
    static class Source {
        @Bean
        DocumentSourcePort documentSource() {
            return new DocumentSourcePort() {
                @Override public String displayName() { return "Confluence"; }
                @Override public boolean acceptsSpace(String space) { return false; }
                @Override public boolean acceptsDocumentId(String id) { return id != null && id.matches("\\d+"); }
                @Override public String documentUrl(String id) { return null; }
                @Override public String getDocumentContent(String id) {
                    return switch (id) { case "777" -> PROJECT_TEXT; case "888" -> DEFAULT_TEXT; default -> "Spec"; };
                }
                @Override public List<DocumentSummary> searchDocuments(String space, String title) { return List.of(); }
            };
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mockMvc;
    @MockitoBean BacklogGeneratorPort generator;
    @MockitoBean ReadinessAssessorPort assessor;
    @MockitoBean TaskGeneratorPort taskGenerator;
    @MockitoBean fr.heliocraft.refinr.application.port.out.TestCaseGeneratorPort testCaseGenerator;
    @MockitoBean IssueTrackerPort tracker;

    @BeforeEach
    void stubs() {
        when(tracker.displayName()).thenReturn("Jira");
        when(tracker.acceptsProjectId(any())).thenAnswer(call -> JiraProjectKeys.isValid(call.getArgument(0)));
        when(tracker.previewFeatureLevel(any())).thenReturn(FeatureLevel.NONE);
        when(generator.withContext(anyString())).thenReturn(generator);
        when(assessor.withContext(anyString())).thenReturn(assessor);
        when(taskGenerator.withContext(anyString())).thenReturn(taskGenerator);
        when(testCaseGenerator.withContext(anyString())).thenReturn(testCaseGenerator);
        when(generator.generate(any(), any())).thenAnswer(call -> new ArrayList<>());
        when(assessor.reassess(any(), any(), any(), any(), any())).thenReturn(new Readiness(false, "Which one?"));
        when(taskGenerator.generateTasks(anyList(), anyList(), any())).thenReturn(List.of(List.of()));
    }

    private JsonNode send(String path, String body) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        String text = response.getContentAsString(StandardCharsets.UTF_8);
        assertFalse(text.contains(PROJECT_TEXT) || text.contains(DEFAULT_TEXT), "never the context text: " + text);
        return JSON.readTree(text);
    }

    private static void assertContext(JsonNode body, String source, String documentId, int characters) {
        assertEquals(source, body.get("context").get("source").asString(), body.toString());
        assertEquals(documentId, body.get("context").get("documentId").asString());
        assertEquals(characters, body.get("context").get("characters").asInt());
    }

    @Test
    @DisplayName("Project context: from the request's projectKey (analyze-preview) and from the issue's project (reassess-story, story-tasks)")
    void projectSource() throws Exception {
        assertContext(send("/api/v1/analyze-preview", "{\"content\":\"Spec\",\"projectKey\":\"TEST\"}"), "project", "777",
                PROJECT_TEXT.length());
        when(tracker.findStory("TEST-1")).thenReturn(new TrackedStory("TEST-1", "S", "d", List.of("validation-necessaire"), null,
                "TEST", List.of()));
        assertContext(send("/api/v1/reassess-story", "{\"issueKey\":\"TEST-1\",\"precision\":\"p\"}"), "project", "777",
                PROJECT_TEXT.length());
        when(tracker.findStory("TEST-2")).thenReturn(new TrackedStory("TEST-2", "S", "d", List.of("ai-ready"), null, "TEST", List.of()));
        assertContext(send("/api/v1/story-tasks", "{\"issueKey\":\"TEST-2\"}"), "project", "777", PROJECT_TEXT.length());
        verify(assessor).withContext(PROJECT_TEXT);
    }

    @Test
    @DisplayName("Default context: no projectKey, or an issue of another project; a request field still wins (source request)")
    void defaultSource() throws Exception {
        assertContext(send("/api/v1/analyze-preview", "{\"content\":\"Spec\"}"), "default", "888", DEFAULT_TEXT.length());
        when(tracker.findStory("OTH-1")).thenReturn(new TrackedStory("OTH-1", "S", "d", List.of("ai-ready"), null, "OTH", List.of()));
        assertContext(send("/api/v1/story-tasks", "{\"issueKey\":\"OTH-1\"}"), "default", "888", DEFAULT_TEXT.length());
        JsonNode request = send("/api/v1/analyze-preview", "{\"content\":\"Spec\",\"projectKey\":\"TEST\",\"context\":\"abc\"}");
        assertEquals("request", request.get("context").get("source").asString());
        assertFalse(request.get("context").has("documentId"));
    }
}
