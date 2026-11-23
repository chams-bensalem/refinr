// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.ReadinessSource;
import fr.heliocraft.refinr.domain.model.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StoryVerdictsTest {

    private static List<Epic> backlog(int stories) {
        Epic epic = new Epic();
        List<Story> list = new ArrayList<>();
        for (int i = 0; i < stories; i++) {
            Story s = new Story();
            s.setTitle("S" + i);
            list.add(s);
        }
        epic.setStories(list);
        return List.of(epic);
    }

    private static ReadinessAssessorPort answering(Readiness... verdicts) {
        return new ReadinessAssessorPort() {
            @Override
            public List<Readiness> assess(List<Story> stories, List<Language> languages) {
                return Arrays.asList(verdicts);
            }

            @Override
            public Readiness reassess(String summary, String description, String precision, String previousQuestion, Language language) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static List<ReadinessSource> sources(List<Epic> epics) {
        return epics.getFirst().getStories().stream().map(Story::getAiReadySource).toList();
    }

    @Test
    @DisplayName("Every verdict from the model: source model on every story")
    void complete() {
        List<Epic> epics = backlog(2);
        StoryVerdicts.apply(epics, answering(new Readiness(true, "ok"), new Readiness(false, "flou")), s -> Language.FR);
        assertEquals(List.of(ReadinessSource.MODEL, ReadinessSource.MODEL), sources(epics));
    }

    @Test
    @DisplayName("Partial answer: the missing story gets the fallback, never ai-ready")
    void partial() {
        List<Epic> epics = backlog(2);
        StoryVerdicts.apply(epics, answering(new Readiness(true, "ok"), null), s -> Language.FR);
        assertEquals(List.of(ReadinessSource.MODEL, ReadinessSource.FALLBACK), sources(epics));
        assertFalse(epics.getFirst().getStories().get(1).getAiReady());
    }

    @Test
    @DisplayName("Assessor failing entirely: fallback on every story")
    void allFailing() {
        List<Epic> epics = backlog(2);
        ReadinessAssessorPort failing = new ReadinessAssessorPort() {
            @Override
            public List<Readiness> assess(List<Story> stories, List<Language> languages) {
                throw new IllegalStateException("boom");
            }

            @Override
            public Readiness reassess(String summary, String description, String precision, String previousQuestion, Language language) {
                throw new UnsupportedOperationException();
            }
        };
        StoryVerdicts.apply(epics, failing, s -> Language.FR);
        assertEquals(List.of(ReadinessSource.FALLBACK, ReadinessSource.FALLBACK), sources(epics));
        assertTrue(epics.getFirst().getStories().getFirst().getAiReadyReason().startsWith("verdict indisponible ("));
    }
}
