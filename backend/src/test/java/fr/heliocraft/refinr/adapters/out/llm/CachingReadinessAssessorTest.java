// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CachingReadinessAssessorTest {

    /** Evaluateur simule : verdict selon le titre ("down" = pas de verdict), journal des stories jugees. */
    static class Recording implements ReadinessAssessorPort {
        final List<String> assessed = new ArrayList<>();
        int reassessCalls;

        @Override
        public List<Readiness> assess(List<Story> stories) {
            List<Readiness> verdicts = new ArrayList<>();
            for (Story story : stories) {
                assessed.add(story.getTitle());
                verdicts.add(story.getTitle().startsWith("down") ? null : new Readiness(true, "ok " + story.getDescription()));
            }
            return verdicts;
        }

        @Override
        public Readiness reassess(String summary, String description, String precision) {
            reassessCalls++;
            return new Readiness(true, precision);
        }
    }

    private final Recording delegate = new Recording();
    private final AtomicLong now = new AtomicLong();

    private CachingReadinessAssessor cache(String fingerprint, int maxEntries) {
        return new CachingReadinessAssessor(delegate, fingerprint, Duration.ofHours(24), maxEntries, now::get);
    }

    private static Story story(String title, String description) {
        Story story = new Story();
        story.setTitle(title);
        story.setDescription(description);
        story.setAcceptanceCriteria(List.of("AC"));
        return story;
    }

    @Test
    @DisplayName("create-jira after a preview: only the stories modified since the preview are assessed again")
    void onlyModifiedStoriesAreAssessed() {
        CachingReadinessAssessor cache = cache("google|m|v1", 100);
        cache.assess(List.of(story("A", "a"), story("B", "b"), story("C", "c")));  // apercu
        delegate.assessed.clear();

        List<Readiness> verdicts = cache.assess(List.of(story("A", "a"), story("B", "b modifiee"), story("C", "c")));

        assertEquals(List.of("B"), delegate.assessed);
        assertEquals(new Readiness(true, "ok a"), verdicts.get(0));
        assertEquals(new Readiness(true, "ok b modifiee"), verdicts.get(1));
        assertEquals(new Readiness(true, "ok c"), verdicts.get(2));
    }

    @Test
    @DisplayName("A missing verdict is never cached: the story is assessed again next time")
    void missingVerdictIsNotCached() {
        CachingReadinessAssessor cache = cache("p", 100);

        assertEquals(Arrays.asList((Readiness) null), cache.assess(List.of(story("down", "x"))));
        cache.assess(List.of(story("down", "x")));

        assertEquals(List.of("down", "down"), delegate.assessed);
    }

    @Test
    @DisplayName("A failing assessment (exception) caches nothing and propagates")
    void failureIsNotCached() {
        ReadinessAssessorPort failing = new ReadinessAssessorPort() {
            int calls;

            @Override
            public List<Readiness> assess(List<Story> stories) {
                if (calls++ == 0) {
                    throw new IllegalStateException("provider down");
                }
                return List.of(new Readiness(false, "flou"));
            }

            @Override
            public Readiness reassess(String s, String d, String p) {
                throw new UnsupportedOperationException();
            }
        };
        CachingReadinessAssessor cache = new CachingReadinessAssessor(failing, "p", Duration.ofHours(1), 10, now::get);

        assertThrows(IllegalStateException.class, () -> cache.assess(List.of(story("A", "a"))));
        assertEquals(List.of(new Readiness(false, "flou")), cache.assess(List.of(story("A", "a"))));
    }

    @Test
    @DisplayName("Entries expire after the TTL")
    void entriesExpire() {
        CachingReadinessAssessor cache = cache("p", 100);
        cache.assess(List.of(story("A", "a")));
        now.addAndGet(Duration.ofHours(25).toNanos());

        cache.assess(List.of(story("A", "a")));

        assertEquals(List.of("A", "A"), delegate.assessed);
    }

    @Test
    @DisplayName("The cache is bounded (least recently used entry evicted)")
    void cacheIsBounded() {
        CachingReadinessAssessor cache = cache("p", 2);
        cache.assess(List.of(story("A", "a"), story("B", "b"), story("C", "c")));
        delegate.assessed.clear();

        cache.assess(List.of(story("A", "a"), story("C", "c")));

        assertEquals(List.of("A"), delegate.assessed);
    }

    @Test
    @DisplayName("Another provider, model or prompt version gives another key; the precision reassessment is never cached")
    void keyAndReassess() {
        Story story = story("A", "a");
        assertNotEquals(cache("google|m1|v1", 10).key(story), cache("google|m2|v1", 10).key(story));
        assertEquals(cache("p", 10).key(story), cache("p", 10).key(story("A", "a")));

        CachingReadinessAssessor cache = cache("p", 10);
        cache.reassess("A", "a", "precision");
        cache.reassess("A", "a", "precision");
        assertEquals(2, delegate.reassessCalls);
    }
}
