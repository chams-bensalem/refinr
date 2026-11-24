// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** X1 : l'empreinte du contexte entre dans la cle du cache des verdicts. */
class CachingReadinessAssessorContextTest {

    /** Evaluateur simule : journal des appels (contexte compris). */
    static final class Fake implements ReadinessAssessorPort {
        final List<String> calls;
        final String context;

        Fake(List<String> calls, String context) {
            this.calls = calls;
            this.context = context;
        }

        @Override
        public List<Readiness> assess(List<Story> stories, List<Language> languages) {
            calls.add(String.valueOf(context));
            return stories.stream().map(s -> new Readiness(context != null, "ctx " + context)).toList();
        }

        @Override
        public Readiness reassess(String summary, String description, String precision, String previousQuestion,
                                  Language language) {
            return null;
        }

        @Override
        public ReadinessAssessorPort withContext(String context) {
            return new Fake(calls, context);
        }
    }

    private static Story story() {
        Story s = new Story();
        s.setTitle("T");
        s.setDescription("D");
        return s;
    }

    @Test
    @DisplayName("Same story: a verdict judged without context, with context A or with context B is never reused for another")
    void contextIsPartOfTheKey() {
        List<String> calls = new ArrayList<>();
        CachingReadinessAssessor cache = new CachingReadinessAssessor(new Fake(calls, null), "fp", Duration.ofHours(1), 100,
                System::nanoTime);
        List<Language> fr = List.of(Language.FR);
        assertFalse(cache.assess(List.of(story()), fr).getFirst().aiReady());
        assertTrue(cache.withContext("A").assess(List.of(story()), fr).getFirst().aiReady());
        assertEquals("ctx B", cache.withContext("B").assess(List.of(story()), fr).getFirst().aiReadyReason());
        assertEquals(List.of("null", "A", "B"), calls);
        // Meme contexte : verdict du cache partage (aucun nouvel appel) ; sans contexte : toujours celui sans contexte
        cache.withContext(" A ").assess(List.of(story()), fr);
        cache.assess(List.of(story()), fr);
        assertEquals(3, calls.size());
        assertSame(cache, cache.withContext(" "));
    }
}
