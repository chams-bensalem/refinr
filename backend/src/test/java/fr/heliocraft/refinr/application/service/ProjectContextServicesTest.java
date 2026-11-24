// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.ContextRequest;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.application.port.out.TaskGeneratorPort;
import fr.heliocraft.refinr.config.UseCaseConfig;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import fr.heliocraft.refinr.domain.service.OutputLanguagePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** X1 : pour reassess-story et story-tasks, la cle de projet vient du ticket ; configuration lue par projet. */
class ProjectContextServicesTest {

    private final DocumentSourcePort source = mock(DocumentSourcePort.class);
    private final IssueTrackerPort tracker = mock(IssueTrackerPort.class);

    private ProjectContextResolver resolver() {
        when(source.acceptsDocumentId(anyString())).thenReturn(true);
        when(source.getDocumentContent("20")).thenReturn("Contexte RFT");
        when(source.getDocumentContent("10")).thenReturn("Contexte par defaut");
        return new ProjectContextResolver(source, "10", Map.of("RFT", "20"), 20_000);
    }

    @Test
    @DisplayName("reassess-story: the ticket's project selects the project context; another project gets the default one")
    void reassessUsesTheTicketProject() {
        ReadinessAssessorPort assessor = mock(ReadinessAssessorPort.class);
        when(assessor.withContext(anyString())).thenReturn(assessor);
        when(assessor.reassess(any(), any(), any(), any(), any())).thenReturn(new Readiness(true, "ok"));
        when(tracker.findStory("RFT-1")).thenReturn(new TrackedStory("RFT-1", "S", "d", List.of("validation-necessaire"),
                null, "RFT", List.of()));
        when(tracker.findStory("OTH-1")).thenReturn(new TrackedStory("OTH-1", "S", "d", List.of("validation-necessaire"),
                null, "OTH", List.of()));
        ReassessStoryService service = new ReassessStoryService(tracker, assessor, OutputLanguagePolicy.DETECT, resolver());
        service.reassess("RFT-1", "p", Language.FR);
        verify(assessor).withContext("Contexte RFT");
        service.reassess("OTH-1", "p", Language.FR);
        verify(assessor).withContext("Contexte par defaut");
        // Champ de la requete prioritaire
        service.reassess("RFT-1", "p", Language.FR, new ContextRequest("Texte demande", null));
        verify(assessor).withContext("Texte demande");
    }

    @Test
    @DisplayName("story-tasks: the ticket's project selects the context of prompt 4; without any context, the port is not bound")
    void storyTasksUseTheTicketProject() {
        TaskGeneratorPort tasks = mock(TaskGeneratorPort.class);
        when(tasks.withContext(anyString())).thenReturn(tasks);
        when(tasks.generateTasks(anyList(), anyList(), any())).thenReturn(List.of(List.of()));
        when(tracker.findStory("RFT-2")).thenReturn(new TrackedStory("RFT-2", "S", "d", List.of("ai-ready"), null, "RFT",
                List.of()));
        new StoryTasksService(tracker, tasks, OutputLanguagePolicy.DETECT, new StoryLocks(), null, resolver())
                .generate("RFT-2", null, Language.FR);
        verify(tasks).withContext("Contexte RFT");
        TaskGeneratorPort plain = mock(TaskGeneratorPort.class);
        when(plain.generateTasks(anyList(), anyList(), any())).thenReturn(List.of(List.of()));
        new StoryTasksService(tracker, plain, OutputLanguagePolicy.DETECT, new StoryLocks(), null,
                ProjectContextResolver.requestOnly(source)).generate("RFT-2", null, Language.FR);
        verify(plain, never()).withContext(anyString());
    }

    @Test
    @DisplayName("Configuration: refinr.context.projects.<key> read as a map, refinr.context.document-id as the default")
    void configurationBinding() {
        when(source.acceptsDocumentId(anyString())).thenReturn(true);
        when(source.getDocumentContent("77")).thenReturn("Contexte PROJ");
        MockEnvironment env = new MockEnvironment().withProperty("refinr.context.projects.PROJ", "77");
        ProjectContextResolver r = new UseCaseConfig(null, 1000, "auto").projectContextResolver(source, env, "", 20_000);
        assertEquals("Contexte PROJ", r.resolve(ContextRequest.NONE, "PROJ").text());
        assertFalse(r.resolve(ContextRequest.NONE, "OTHER").isPresent());
    }
}
