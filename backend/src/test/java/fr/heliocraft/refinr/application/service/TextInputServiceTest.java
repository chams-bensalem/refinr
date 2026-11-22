// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.AnalysisProgress;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.JobView;
import fr.heliocraft.refinr.application.port.in.ContentTooLargeException;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Document fourni en texte : aucun appel a la source active, puis le meme chemin que pour un document lu. */
class TextInputServiceTest {

    private static final int MAX = 50;

    private final DocumentSourcePort source = mock(DocumentSourcePort.class);
    private final BacklogGeneratorPort generator = mock(BacklogGeneratorPort.class);
    private final ReadinessAssessorPort assessor = mock(ReadinessAssessorPort.class);
    private final CreateBacklog createBacklog = mock(CreateBacklog.class);
    private final BacklogCreationResult created = new BacklogCreationResult(List.of(), List.of(), List.of());

    @BeforeEach
    void setUp() {
        when(generator.generate(any(), any())).thenAnswer(call -> epics());
        when(createBacklog.create(anyList(), any())).thenReturn(created);
    }

    private static List<Epic> epics() {
        Story story = new Story();
        story.setTitle("Story");
        Epic epic = new Epic();
        epic.setName("Epic");
        epic.setStories(new ArrayList<>(List.of(story)));
        return new ArrayList<>(List.of(epic));
    }

    private PreviewBacklogService preview(ReadinessAssessorPort separate) {
        return new PreviewBacklogService(source, generator, separate, MAX);
    }

    private AnalyzeDocumentService analyze(ReadinessAssessorPort separate) {
        return new AnalyzeDocumentService(source, generator, separate, createBacklog, MAX);
    }

    @Test
    @DisplayName("Text only: sent to the model unchanged, the document source is never called")
    void textOnly() {
        preview(null).previewText(null, "Gestion des stocks");
        preview(null).previewText("  ", "Gestion des stocks");
        verify(generator, times(2)).generate(eq("Gestion des stocks"), any());
        verifyNoInteractions(source);
    }

    @Test
    @DisplayName("Text + title: '# title' on a single line heads the text sent to the model")
    void textWithTitle() {
        preview(null).previewText("  CDC\n  Stocks\t v2 ", "- Créer un article");
        verify(generator).generate(eq("# CDC Stocks v2\n\n- Créer un article"), any());
        verifyNoInteractions(source);
    }

    @Test
    @DisplayName("Empty or blank text is a 400 without model call, even with a title")
    void emptyTextIs400() {
        for (String content : new String[] {null, "", "  \n\t ", "# \n- \n|---|"}) {
            InvalidRequestException e = assertThrows(InvalidRequestException.class,
                    () -> preview(null).previewText("Titre", content));
            assertEquals(DocumentAnalysis.EMPTY_DOCUMENT, e.getMessage());
            assertThrows(InvalidRequestException.class, () -> analyze(null).analyzeTextAndCreate(null, content, null));
        }
        verifyNoInteractions(source, generator, createBacklog);
    }

    @Test
    @DisplayName("Size limit: exactly max characters (title included, code points) passes, one more is a 413 without model call")
    void sizeLimit() {
        String atLimit = "a".repeat(MAX);
        preview(null).previewText(null, atLimit);
        // "# T\n\n" = 5 caracteres, compte dans la limite
        preview(null).previewText("T", "a".repeat(MAX - 5));
        // Lettre hors BMP (U+1D400) : un caractere, deux unites UTF-16
        preview(null).previewText(null, "\uD835\uDC00".repeat(MAX));
        verify(generator, times(3)).generate(any(), any());

        ContentTooLargeException e = assertThrows(ContentTooLargeException.class,
                () -> preview(null).previewText(null, atLimit + "a"));
        assertEquals("The document exceeds the maximum size of 50 characters", e.getMessage());
        assertThrows(ContentTooLargeException.class, () -> preview(null).previewText("T", "a".repeat(MAX - 4)));
        assertThrows(ContentTooLargeException.class, () -> analyze(null).analyzeTextAndCreate(null, atLimit + "a", "PROJ"));
        verifyNoMoreInteractions(generator);
        verifyNoInteractions(source, createBacklog);
    }

    @Test
    @DisplayName("analyze-and-create with text: same path (generation, separate verdict, creation in the requested project)")
    void analyzeTextAndCreate() {
        when(assessor.assess(anyList(), anyList())).thenReturn(List.of(new Readiness(true, "clear")));
        BacklogCreationResult result = analyze(assessor).analyzeTextAndCreate("Stocks", "Créer un article", "PROJ");

        assertSame(created, result);
        verify(generator).generate(eq("# Stocks\n\nCréer un article"), any());
        verify(assessor).assess(anyList(), anyList());
        verify(createBacklog).create(argThat(epics -> epics.getFirst().getStories().getFirst().getAiReady()), eq("PROJ"));
        verifyNoInteractions(source);
    }

    @Test
    @DisplayName("Preview with text applies the separate verdict like a document read from the source")
    void previewTextAppliesSeparateVerdict() {
        when(assessor.assess(anyList(), anyList())).thenReturn(List.of(new Readiness(false, "vague")));
        List<Epic> epics = preview(assessor).previewText(null, "Créer un article");
        assertEquals(Boolean.FALSE, epics.getFirst().getStories().getFirst().getAiReady());
        verifyNoInteractions(source);
    }

    @Test
    @DisplayName("Jobs: steps are reported in order (reading, generating, assessing, creating) for each input")
    void stepsAreReportedInOrder() {
        when(assessor.assess(anyList(), anyList())).thenReturn(List.of(new Readiness(true, "clear")));
        when(source.acceptsDocumentId("DOC-1")).thenReturn(true);
        when(source.getDocumentContent("DOC-1")).thenReturn("Créer un article");
        List<JobView.Step> steps = new ArrayList<>();

        analyze(assessor).analyzeAndCreate("DOC-1", null, null, steps::add);
        assertEquals(List.of(JobView.Step.READING_DOCUMENT, JobView.Step.GENERATING, JobView.Step.ASSESSING,
                JobView.Step.CREATING_ISSUES), steps);

        steps.clear();
        preview(null).previewText(null, "Créer un article", null, steps::add);
        assertEquals(List.of(JobView.Step.GENERATING), steps);

        steps.clear();
        preview(assessor).preview("DOC-1", null, steps::add);
        assertEquals(List.of(JobView.Step.READING_DOCUMENT, JobView.Step.GENERATING, JobView.Step.ASSESSING), steps);
    }

    @Test
    @DisplayName("Jobs: a progress that stops before the creation step means no issue is created")
    void stopBeforeCreationCreatesNothing() {
        AnalysisProgress stopAtCreation = step -> {
            if (step == JobView.Step.CREATING_ISSUES) {
                throw new IllegalStateException("stopped");
            }
        };

        assertThrows(IllegalStateException.class,
                () -> analyze(null).analyzeTextAndCreate(null, "Créer un article", null, null, stopAtCreation));
        verify(generator).generate(any(), any());
        verifyNoInteractions(createBacklog);
    }

    @Test
    @DisplayName("A non-positive maximum size is a configuration error")
    void nonPositiveLimitIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new PreviewBacklogService(source, generator, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyzeDocumentService(source, generator, null, createBacklog, -1));
    }
}
