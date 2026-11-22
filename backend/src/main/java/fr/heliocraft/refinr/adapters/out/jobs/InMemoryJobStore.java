// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.jobs;

import fr.heliocraft.refinr.application.port.out.JobStorePort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Jobs gardes en memoire : perdus au redemarrage. Au plus {@code maxRetained} jobs ; un job termine expire
 * {@code retention} apres sa fin. Stockage plein : le job termine le plus ancien est evince, jamais un job actif.
 */
public class InMemoryJobStore<J extends JobStorePort.Entry> implements JobStorePort<J> {

    private final int maxRetained;
    private final Duration retention;
    private final Clock clock;
    private final Map<String, J> jobs = new HashMap<>();

    public InMemoryJobStore(int maxRetained, Duration retention, Clock clock) {
        if (maxRetained < 1) {
            throw new IllegalArgumentException("refinr.jobs.max-retained must be at least 1, got " + maxRetained);
        }
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("refinr.jobs.retention must be positive, got " + retention);
        }
        this.maxRetained = maxRetained;
        this.retention = retention;
        this.clock = clock;
    }

    @Override
    public synchronized boolean save(J job) {
        if (jobs.containsKey(job.id())) {
            jobs.put(job.id(), job);
            return true;
        }
        evictExpired();
        if (jobs.size() >= maxRetained && !evictOldestFinished()) {
            return false;
        }
        jobs.put(job.id(), job);
        return true;
    }

    @Override
    public synchronized Optional<J> find(String id) {
        J job = jobs.get(id);
        if (job != null && isExpired(job, clock.instant())) {
            jobs.remove(id);
            return Optional.empty();
        }
        return Optional.ofNullable(job);
    }

    @Override
    public synchronized void remove(String id) {
        jobs.remove(id);
    }

    @Override
    public synchronized void evictExpired() {
        Instant now = clock.instant();
        jobs.values().removeIf(job -> isExpired(job, now));
    }

    /** Nombre de jobs gardes (actifs et termines non expires). */
    public synchronized int size() {
        return jobs.size();
    }

    private boolean isExpired(J job, Instant now) {
        return job.isFinished() && !job.finishedAt().plus(retention).isAfter(now);
    }

    private boolean evictOldestFinished() {
        Optional<J> oldest = jobs.values().stream()
                .filter(J::isFinished)
                .min(Comparator.comparing(J::finishedAt));
        oldest.ifPresent(job -> jobs.remove(job.id()));
        return oldest.isPresent();
    }
}
