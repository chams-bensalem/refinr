// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.jobs;

import fr.heliocraft.refinr.application.port.out.JobStorePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryJobStoreTest {

    private static final Instant T0 = Instant.parse("2026-11-22T10:00:00Z");

    record TestJob(String id, boolean isFinished, Instant finishedAt) implements JobStorePort.Entry {
        static TestJob active(String id) {
            return new TestJob(id, false, null);
        }

        static TestJob finished(String id, Instant at) {
            return new TestJob(id, true, at);
        }
    }

    /** Horloge reglable. */
    static final class MutableClock extends Clock {
        Instant now = T0;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();

    @Test
    @DisplayName("A job is found until its retention, counted from its end, is over; an active job never expires")
    void retentionCountsFromTheEnd() {
        InMemoryJobStore<TestJob> store = new InMemoryJobStore<>(10, Duration.ofHours(24), clock);
        store.save(TestJob.active("running"));
        store.save(TestJob.finished("done", T0));

        clock.now = T0.plus(Duration.ofHours(24)).minusSeconds(1);
        assertTrue(store.find("done").isPresent());

        clock.now = T0.plus(Duration.ofHours(24));
        assertTrue(store.find("done").isEmpty());
        clock.now = T0.plus(Duration.ofDays(30));
        assertTrue(store.find("running").isPresent());
    }

    @Test
    @DisplayName("evictExpired removes only finished jobs past their retention")
    void evictExpired() {
        InMemoryJobStore<TestJob> store = new InMemoryJobStore<>(10, Duration.ofHours(1), clock);
        store.save(TestJob.finished("old", T0));
        store.save(TestJob.finished("recent", T0.plus(Duration.ofMinutes(50))));
        store.save(TestJob.active("running"));

        clock.now = T0.plus(Duration.ofMinutes(70));
        store.evictExpired();

        assertEquals(2, store.size());
        assertTrue(store.find("old").isEmpty());
        assertTrue(store.find("recent").isPresent());
    }

    @Test
    @DisplayName("Full store: the oldest finished job is evicted for a new one, never an active job")
    void fullStoreEvictsOldestFinished() {
        InMemoryJobStore<TestJob> store = new InMemoryJobStore<>(3, Duration.ofHours(24), clock);
        store.save(TestJob.finished("second", T0.plusSeconds(10)));
        store.save(TestJob.finished("first", T0));
        store.save(TestJob.active("running"));

        assertTrue(store.save(TestJob.active("new")));

        assertTrue(store.find("first").isEmpty());
        assertTrue(store.find("second").isPresent());
        assertTrue(store.find("running").isPresent());
        assertTrue(store.find("new").isPresent());
    }

    @Test
    @DisplayName("Full store of active jobs: a new job is refused, an update of a known job is accepted")
    void fullStoreOfActiveJobs() {
        InMemoryJobStore<TestJob> store = new InMemoryJobStore<>(2, Duration.ofHours(24), clock);
        store.save(TestJob.active("a"));
        store.save(TestJob.active("b"));

        assertFalse(store.save(TestJob.active("c")));
        assertTrue(store.save(TestJob.finished("a", T0)));
        assertEquals(2, store.size());
    }

    @Test
    @DisplayName("remove forgets the job; invalid bounds are refused")
    void removeAndBounds() {
        InMemoryJobStore<TestJob> store = new InMemoryJobStore<>(2, Duration.ofHours(24), clock);
        store.save(TestJob.active("a"));
        store.remove("a");
        assertTrue(store.find("a").isEmpty());

        assertThrows(IllegalArgumentException.class, () -> new InMemoryJobStore<TestJob>(0, Duration.ofHours(1), clock));
        assertThrows(IllegalArgumentException.class, () -> new InMemoryJobStore<TestJob>(1, Duration.ZERO, clock));
    }
}
