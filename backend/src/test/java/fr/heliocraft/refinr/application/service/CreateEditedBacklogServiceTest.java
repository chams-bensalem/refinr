// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreateEditedBacklogServiceTest {

    /** Modele de langage simule : verdict par titre de story, ou echec si le titre commence par "down". */
    static class FakeLlm implements ReadinessAssessorPort {
        final List<String> assessed = new ArrayList<>();

        @Override
        public Readiness reassess(String summary, String description, String precision) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Readiness> assess(List<Story> stories) {
            List<Readiness> verdicts = new ArrayList<>();
            for (Story story : stories) {
                verdicts.add(assessOne(story));
            }
            return verdicts;
        }

        private Readiness assessOne(Story story) {
            assessed.add(story.getTitle() + "|" + story.getDescription());
            if (story.getTitle().startsWith("down")) {
                return null;  // verdict indisponible pour cette story
            }
            return story.getTitle().startsWith("clear")
                ? new Readiness(true, "Perimetre clair")
                : new Readiness(false, "Critere flou");
        }
    }

    /** Creation simulee : memorise le backlog tel qu'il est transmis a la creation. */
    static class CapturingCreation implements CreateBacklog {
        List<Epic> epics;
        String project;

        @Override
        public BacklogCreationResult create(List<Epic> epics, String project) {
            this.epics = epics;
            this.project = project;
            return new BacklogCreationResult(epics, List.of(), List.of());
        }
    }

    private static Story story(String title, Boolean clientVerdict, String clientReason) {
        Story story = new Story();
        story.setTitle(title);
        story.setDescription("contenu final de " + title);
        story.setAiReady(clientVerdict);
        story.setAiReadyReason(clientReason);
        return story;
    }

    @Test
    @DisplayName("Recomputes each story verdict on its final content, ignoring the verdict sent by the client")
    void recomputesVerdictOnFinalContent() {
        Story edited = story("edited", true, "ai-ready selon l'apercu");
        Story clear = story("clear", false, "ancien verdict");
        Epic epic = new Epic();
        epic.setName("E");
        epic.setStories(List.of(edited, clear));
        FakeLlm llm = new FakeLlm();
        CapturingCreation creation = new CapturingCreation();

        new CreateEditedBacklogService(llm, creation).create(List.of(epic), "DEMO");

        assertEquals(List.of("edited|contenu final de edited", "clear|contenu final de clear"), llm.assessed);
        assertEquals(Boolean.FALSE, edited.getAiReady());
        assertEquals("Critere flou", edited.getAiReadyReason());
        assertEquals(Boolean.TRUE, clear.getAiReady());
        assertEquals("Perimetre clair", clear.getAiReadyReason());
        assertSame(epic, creation.epics.get(0));
        assertEquals("DEMO", creation.project);
    }

    @Test
    @DisplayName("Without a reliable verdict the story is never ai-ready, and the creation still happens")
    void unavailableVerdictIsNeverAiReady() {
        Story down = story("down", true, "ai-ready selon le client");
        Epic epic = new Epic();
        epic.setName("E");
        epic.setStories(List.of(down));
        CapturingCreation creation = new CapturingCreation();

        new CreateEditedBacklogService(new FakeLlm(), creation).create(List.of(epic), null);

        assertEquals(Boolean.FALSE, down.getAiReady());
        assertTrue(down.getAiReadyReason().startsWith("verdict indisponible"), down.getAiReadyReason());
        assertNotNull(creation.epics);
    }
}
