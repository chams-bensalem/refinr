// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.ContentTooLargeException;
import fr.heliocraft.refinr.application.port.in.ContextRequest;
import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.domain.model.ProjectContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** X1 : priorite du contexte projet, limites, refus locaux, configuration et journaux sans le texte. */
@ExtendWith(OutputCaptureExtension.class)
class ProjectContextResolverTest {

    private final DocumentSourcePort source = mock(DocumentSourcePort.class);

    @BeforeEach
    void setUp() {
        when(source.acceptsDocumentId(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).matches("\\d+"));
        when(source.getDocumentContent("10")).thenReturn("Contexte par defaut");
        when(source.getDocumentContent("20")).thenReturn("Contexte du projet PROJ");
        when(source.getDocumentContent("30")).thenReturn("Contexte demande");
    }

    private ProjectContextResolver resolver(int max) {
        return new ProjectContextResolver(source, "10", Map.of("PROJ", "20"), max);
    }

    @Test
    @DisplayName("Priority: request text, then request page, then the project property (case ignored), then the default, then none")
    void priority() {
        ProjectContextResolver r = resolver(20_000);
        assertEquals(new ProjectContext("Texte", ProjectContext.Source.REQUEST), r.resolve(new ContextRequest(" Texte ", null), "PROJ"));
        assertEquals(new ProjectContext("Contexte demande", ProjectContext.Source.REQUEST), r.resolve(new ContextRequest(null, "30"), "PROJ"));
        assertEquals(ProjectContext.Source.PROJECT, r.resolve(ContextRequest.NONE, "proj").source());
        assertEquals("Contexte du projet PROJ", r.resolve(ContextRequest.NONE, "PROJ").text());
        assertEquals(new ProjectContext("Contexte par defaut", ProjectContext.Source.DEFAULT), r.resolve(ContextRequest.NONE, "OTHER"));
        assertEquals(ProjectContext.Source.DEFAULT, r.resolve(ContextRequest.NONE, null).source());
        assertSame(ProjectContext.NONE, ProjectContextResolver.requestOnly(source).resolve(ContextRequest.NONE, "PROJ"));
        assertTrue(r.configured());
        assertFalse(ProjectContextResolver.requestOnly(source).configured());
    }

    @Test
    @DisplayName("Blank values are absent; both fields -> 400; invalid page id -> 400, before any call to the source")
    void localRefusals() {
        ProjectContextResolver r = ProjectContextResolver.requestOnly(source);
        assertTrue(new ContextRequest("  ", " ").isEmpty());
        assertEquals("context and contextDocumentId cannot be combined",
                assertThrows(InvalidRequestException.class, () -> r.check(new ContextRequest("t", "30"))).getMessage());
        assertEquals("Invalid contextDocumentId",
                assertThrows(InvalidRequestException.class, () -> r.check(new ContextRequest(null, "abc"))).getMessage());
        verify(source, never()).getDocumentContent(anyString());
    }

    @Test
    @DisplayName("Size: exactly the limit is accepted, one more character -> 413 (text or page), never truncated")
    void sizeLimit() {
        ProjectContextResolver r = resolver(5);
        assertEquals("abcd\u00e9", r.resolve(new ContextRequest("abcd\u00e9", null), null).text());
        ContentTooLargeException e = assertThrows(ContentTooLargeException.class,
                () -> r.check(new ContextRequest("abcdef", null)));
        assertEquals("The project context exceeds the maximum size of 5 characters", e.getMessage());
        assertThrows(ContentTooLargeException.class, () -> r.resolve(new ContextRequest(null, "30"), null));
    }

    @Test
    @DisplayName("A context page without text -> 400; a page id of the wrong format in the configuration -> startup refused")
    void emptyPageAndConfiguration() {
        when(source.getDocumentContent("40")).thenReturn("<p> </p>");
        ProjectContextResolver r = ProjectContextResolver.requestOnly(source);
        assertThrows(InvalidRequestException.class, () -> r.resolve(new ContextRequest(null, "40"), null));
        assertThrows(IllegalStateException.class, () -> new ProjectContextResolver(source, "abc", Map.of(), 10));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ProjectContextResolver(source, null, Map.of("PROJ", "secret-value"), 10));
        assertFalse(e.getMessage().contains("secret-value"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> new ProjectContextResolver(source, null, Map.of(), 0));
    }

    @Test
    @DisplayName("Logs give the source and the size of the context, never its text")
    void noLeak(CapturedOutput output) {
        resolver(20_000).resolve(new ContextRequest("MARQUEUR-CONFIDENTIEL", null), null);
        resolver(20_000).resolve(ContextRequest.NONE, "PROJ");
        assertTrue(output.getAll().contains("Project context: source request, 21 characters"), output.getAll());
        assertFalse(output.getAll().contains("MARQUEUR-CONFIDENTIEL"));
        assertFalse(output.getAll().contains("Contexte du projet"));
    }
}
