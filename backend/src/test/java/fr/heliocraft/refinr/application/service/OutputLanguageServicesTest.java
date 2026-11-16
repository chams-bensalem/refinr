// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.TrackedStory;
import fr.heliocraft.refinr.domain.service.OutputLanguagePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Langue de sortie dans les cas d'usage : requete, puis propriete, puis detection (francais par defaut). */
class OutputLanguageServicesTest {

    private static final String ENGLISH_SPEC = "# Order export\nCustomers can export their paid orders as a CSV file from"
            + " their account page. The export contains one row per order and the cancelled orders are excluded.";
    private static final String FRENCH_SPEC = "# Export des commandes\nLe client exporte ses commandes payées depuis la page"
            + " de son compte. Le fichier contient une ligne par commande et les commandes annulées sont exclues.";

    private final BacklogGeneratorPort generator = mock(BacklogGeneratorPort.class);
    private final ReadinessAssessorPort assessor = mock(ReadinessAssessorPort.class);

    private static Story story(String title, String description) {
        Story story = new Story();
        story.setTitle(title);
        story.setDescription(description);
        return story;
    }

    private static List<Epic> epics(Story... stories) {
        Epic epic = new Epic();
        epic.setName("Epic");
        epic.setStories(new ArrayList<>(List.of(stories)));
        return new ArrayList<>(List.of(epic));
    }

    private Language previewLanguage(String property, Language requested, String spec) {
        when(generator.generate(any(), any())).thenAnswer(call -> epics(story("S", "d")));
        new PreviewBacklogService(mock(DocumentSourcePort.class), generator, null, 10_000,
                OutputLanguagePolicy.fromProperty(property)).previewText(null, spec, requested);
        var captor = org.mockito.ArgumentCaptor.forClass(Language.class);
        verify(generator).generate(any(), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Preview: the request wins over the property, the property over detection")
    void previewPriority() {
        assertEquals(Language.of("de"), previewLanguage("en", Language.of("de"), FRENCH_SPEC));
    }

    @Test
    void previewPropertyBeatsDetection() {
        assertEquals(Language.EN, previewLanguage("en", null, FRENCH_SPEC));
    }

    @Test
    @DisplayName("Preview in auto mode: an English spec gives English, a French spec French")
    void previewDetection() {
        assertEquals(Language.EN, previewLanguage("auto", null, ENGLISH_SPEC));
    }

    @Test
    void previewDetectionFrench() {
        assertEquals(Language.FR, previewLanguage("auto", null, FRENCH_SPEC));
    }

    @Test
    @DisplayName("Preview with a separate verdict provider: the verdicts get the document's language for every story")
    void separateVerdictSharesTheDocumentLanguage() {
        when(generator.generate(any(), any())).thenAnswer(call -> epics(story("Export", "Exporter les commandes"),
                story("Import", "The import reads the file and stores each row in the table.")));
        when(assessor.assess(anyList(), anyList())).thenReturn(List.of(new Readiness(true, "a"), new Readiness(true, "b")));

        new PreviewBacklogService(mock(DocumentSourcePort.class), generator, assessor, 10_000, OutputLanguagePolicy.DETECT)
                .previewText(null, ENGLISH_SPEC, null);

        verify(assessor).assess(anyList(), eq(List.of(Language.EN, Language.EN)));
    }

    @Test
    @DisplayName("create-jira: the requested language for every story; without it, the property, then detection story by story")
    void createEditedBacklogLanguages() {
        CreateBacklog create = mock(CreateBacklog.class);
        when(create.create(anyList(), any())).thenReturn(new BacklogCreationResult(List.of(), List.of(), List.of()));
        when(assessor.assess(anyList(), anyList())).thenReturn(List.of(new Readiness(true, "a"), new Readiness(true, "b")));
        Story french = story("Annuler une commande", "Le client peut annuler une commande depuis la page de son compte tant qu'elle n'est pas expédiée.");
        Story english = story("Cancel an order", "The customer can cancel an order from the account page as long as it has not been shipped.");

        new CreateEditedBacklogService(assessor, create, OutputLanguagePolicy.DETECT).create(epics(french, english), null, null);
        verify(assessor).assess(anyList(), eq(List.of(Language.FR, Language.EN)));

        ReadinessAssessorPort german = mock(ReadinessAssessorPort.class);
        when(german.assess(anyList(), anyList())).thenReturn(List.of(new Readiness(true, "a"), new Readiness(true, "b")));
        new CreateEditedBacklogService(german, create, OutputLanguagePolicy.DETECT).create(epics(french, english), null, Language.of("de"));
        verify(german).assess(anyList(), eq(List.of(Language.of("de"), Language.of("de"))));

        ReadinessAssessorPort configured = mock(ReadinessAssessorPort.class);
        when(configured.assess(anyList(), anyList())).thenReturn(List.of(new Readiness(true, "a"), new Readiness(true, "b")));
        new CreateEditedBacklogService(configured, create, OutputLanguagePolicy.fromProperty("en")).create(epics(french, english), null, null);
        verify(configured).assess(anyList(), eq(List.of(Language.EN, Language.EN)));
    }

    @Test
    @DisplayName("Reassessment: requested language, else detection on the title and the description")
    void reassessLanguage() {
        IssueTrackerPort tracker = mock(IssueTrackerPort.class);
        TrackedStory story = new TrackedStory("PROJ-7", "Cancel an order",
                "The customer can cancel an order from the account page as long as it has not been shipped.",
                List.of("validation-necessaire"));
        when(tracker.findStory("PROJ-7")).thenReturn(story);
        when(assessor.reassess(any(), any(), any(), any())).thenReturn(new Readiness(true, "ok"));
        ReassessStoryService service = new ReassessStoryService(tracker, assessor, OutputLanguagePolicy.DETECT);

        service.reassess("PROJ-7", "precision", null);
        verify(assessor).reassess(any(), any(), eq("precision"), eq(Language.EN));
        service.reassess("PROJ-7", "autre", Language.of("es"));
        verify(assessor).reassess(any(), any(), eq("autre"), eq(Language.of("es")));
    }
}
