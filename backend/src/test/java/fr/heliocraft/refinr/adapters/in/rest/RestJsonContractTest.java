package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.in.ReassessStory;
import fr.heliocraft.refinr.application.port.in.SearchDocuments;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat JSON de l'API REST (caracterisation, phase 3) : fige la forme exacte des Epic/Story/Task
 * echanges avec le frontend, pour qu'aucun refactoring ne la modifie.
 */
@WebMvcTest(MvpController.class)
@TestPropertySource(properties = {
    "jira.base-url=http://localhost:8080",
    "jira.username=test@example.com",
    "jira.token=test-token",
    "jira.project-key=TEST",
    "confluence.base-url=http://localhost:8080",
    "confluence.username=test@example.com",
    "confluence.token=test-token",
    "gemini.api-key=test-api-key"
})
class RestJsonContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyzeDocument analyzeDocument;

    @MockitoBean
    private PreviewBacklog previewBacklog;

    @MockitoBean
    private CreateBacklog createBacklog;

    @MockitoBean
    private ReassessStory reassessStory;

    @MockitoBean
    private SearchDocuments searchDocuments;

    @Test
    @DisplayName("analyze-preview serializes every Epic/Story/Task field exactly as the snapshot")
    void previewResponseMatchesSnapshot() throws Exception {
        when(previewBacklog.preview("42")).thenReturn(List.of(fullEpic()));

        String body = mockMvc.perform(post("/api/v1/mvp/analyze-preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confluencePageId\":\"42\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JSONAssert.assertEquals(resource("contract/analyze-preview-response.json"), body, JSONCompareMode.STRICT);
    }

    @Test
    @DisplayName("create-jira reads every Epic/Story/Task field of the request")
    @SuppressWarnings("unchecked")
    void createJiraRequestReadsEveryField() throws Exception {
        stubCreationResult();
        mockMvc.perform(post("/api/v1/mvp/create-jira")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resource("contract/create-jira-request.json")))
            .andExpect(status().isOk());

        ArgumentCaptor<List<Epic>> captor = ArgumentCaptor.forClass(List.class);
        verify(createBacklog).create(captor.capture(), isNull());
        assertEquals(1, captor.getValue().size());
        assertEpicFields(captor.getValue().get(0));
    }

    @Test
    @DisplayName("create-jira ignores unknown properties sent by the client (Spring Boot defaults)")
    @SuppressWarnings("unchecked")
    void createJiraIgnoresUnknownProperties() throws Exception {
        stubCreationResult();
        mockMvc.perform(post("/api/v1/mvp/create-jira")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"name\":\"E\",\"color\":\"red\",\"stories\":[{\"title\":\"S\",\"mood\":\"ok\","
                    + "\"tasks\":[{\"title\":\"T\",\"owner\":\"x\"}]}]}]"))
            .andExpect(status().isOk());

        ArgumentCaptor<List<Epic>> captor = ArgumentCaptor.forClass(List.class);
        verify(createBacklog).create(captor.capture(), isNull());
        assertEquals("T", captor.getValue().get(0).getStories().get(0).getTasks().get(0).getTitle());
    }

    /** create renvoie desormais le resultat de la creation (crees / echoues) : ici, tout est cree. */
    private void stubCreationResult() {
        when(createBacklog.create(any(), any())).thenAnswer(invocation ->
            new BacklogCreationResult(invocation.getArgument(0), List.of(), List.of()));
    }

    private static void assertEpicFields(Epic epic) {
        assertEquals("Epic: Inventaire", epic.getName());
        assertEquals("Gestion des stocks", epic.getDescription());
        assertEquals("High", epic.getPriority());
        assertEquals(List.of("backend", "api"), epic.getLabels());
        assertEquals("PROJ-1", epic.getTrackerKey());
        assertEquals("https://your-site.atlassian.net/browse/PROJ-1", epic.getTrackerUrl());

        Story story = epic.getStories().get(0);
        assertEquals("Developer", story.getAsA());
        assertEquals("track stock levels", story.getIWant());
        assertEquals("avoid stock-outs", story.getSoThat());
        assertEquals("Track Stock", story.getTitle());
        assertEquals("Story description", story.getDescription());
        assertEquals(List.of("AC1", "AC2"), story.getAcceptanceCriteria());
        assertEquals(3, story.getStoryPoints());
        assertEquals(5, story.getEstimatedStoryPoints());
        assertEquals("Medium", story.getPriority());
        assertEquals(List.of("api"), story.getLabels());
        assertEquals(List.of("PROJ-0"), story.getDependencies());
        assertEquals(Boolean.TRUE, story.getAiReady());
        assertEquals("Perimetre clair", story.getAiReadyReason());
        assertEquals("PROJ-2", story.getTrackerKey());
        assertEquals("https://your-site.atlassian.net/browse/PROJ-2", story.getTrackerUrl());
        assertEquals("PROJ-1", story.getEpicTrackerKey());

        Task task = story.getTasks().get(0);
        assertEquals("Create service", task.getTitle());
        assertEquals("Task description", task.getDescription());
        assertEquals(4, task.getEstimatedHours());
        assertEquals(List.of("backend"), task.getLabels());
        assertEquals("PROJ-3", task.getTrackerKey());
        assertEquals("https://your-site.atlassian.net/browse/PROJ-3", task.getTrackerUrl());
        assertEquals("PROJ-2", task.getParentTrackerKey());
    }

    private static Epic fullEpic() {
        Task task = new Task();
        task.setTitle("Create service");
        task.setDescription("Task description");
        task.setEstimatedHours(4);
        task.setLabels(List.of("backend"));
        task.setTrackerKey("PROJ-3");
        task.setTrackerUrl("https://your-site.atlassian.net/browse/PROJ-3");
        task.setParentTrackerKey("PROJ-2");

        Story story = new Story();
        story.setAsA("Developer");
        story.setIWant("track stock levels");
        story.setSoThat("avoid stock-outs");
        story.setTitle("Track Stock");
        story.setDescription("Story description");
        story.setAcceptanceCriteria(List.of("AC1", "AC2"));
        story.setStoryPoints(3);
        story.setEstimatedStoryPoints(5);
        story.setPriority("Medium");
        story.setLabels(List.of("api"));
        story.setTasks(List.of(task));
        story.setDependencies(List.of("PROJ-0"));
        story.setAiReady(true);
        story.setAiReadyReason("Perimetre clair");
        story.setTrackerKey("PROJ-2");
        story.setTrackerUrl("https://your-site.atlassian.net/browse/PROJ-2");
        story.setEpicTrackerKey("PROJ-1");

        Epic epic = new Epic();
        epic.setName("Epic: Inventaire");
        epic.setDescription("Gestion des stocks");
        epic.setPriority("High");
        epic.setLabels(List.of("backend", "api"));
        epic.setStories(List.of(story));
        epic.setTrackerKey("PROJ-1");
        epic.setTrackerUrl("https://your-site.atlassian.net/browse/PROJ-1");
        return epic;
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = RestJsonContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "missing test resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
