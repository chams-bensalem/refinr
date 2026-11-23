// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort.FeatureLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

/**
 * Lecture du niveau Feature pour l'apercu (information d'affichage) : bornee a {@code timeout} au total, gardee en
 * cache par projet ({@code successTtl} pour un niveau lu, {@code failureTtl} pour un echec, un niveau inconnu ou un
 * delai depasse). La creation ne passe pas par ici : elle relit le niveau avec ses propres delais.
 */
final class FeatureLevelLookup {

    static final Duration TIMEOUT = Duration.ofSeconds(5);
    static final Duration SUCCESS_TTL = Duration.ofHours(1);
    static final Duration FAILURE_TTL = Duration.ofMinutes(1);

    private static final Logger log = LoggerFactory.getLogger(FeatureLevelLookup.class);
    // Fils virtuels : un appel lent au gestionnaire ne bloque ni l'apercu ni un fil du pool HTTP
    private static final ExecutorService LOOKUPS = Executors.newVirtualThreadPerTaskExecutor();

    private record Entry(FeatureLevel level, long expiresAtNanos) {}

    private final IssueTrackerPort tracker;
    private final Duration timeout;
    private final Duration successTtl;
    private final Duration failureTtl;
    private final LongSupplier nanoTime;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    FeatureLevelLookup(IssueTrackerPort tracker) {
        this(tracker, TIMEOUT, SUCCESS_TTL, FAILURE_TTL, System::nanoTime);
    }

    FeatureLevelLookup(IssueTrackerPort tracker, Duration timeout, Duration successTtl, Duration failureTtl,
                       LongSupplier nanoTime) {
        this.tracker = tracker;
        this.timeout = timeout;
        this.successTtl = successTtl;
        this.failureTtl = failureTtl;
        this.nanoTime = nanoTime;
    }

    /** Niveau du projet ({@code UNKNOWN} si la lecture echoue ou depasse le delai) ; jamais plus de {@code timeout}. */
    FeatureLevel level(String project) {
        String key = project == null ? "" : project;
        Entry cached = cache.get(key);
        long now = nanoTime.getAsLong();
        if (cached != null && now < cached.expiresAtNanos()) {
            return cached.level();
        }
        CompletableFuture<FeatureLevel> lookup = CompletableFuture.supplyAsync(
                () -> FeatureLevels.levelOf(tracker, project), LOOKUPS);
        FeatureLevel level;
        try {
            level = lookup.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Feature level of the tracker not read within {}: preview sent without it", timeout);
            // Echec garde d'abord ; une lecture tardive reussie le remplace ensuite pour les apercus suivants
            remember(key, FeatureLevel.UNKNOWN);
            lookup.thenAccept(late -> {
                if (late != FeatureLevel.UNKNOWN) {
                    remember(key, late);
                }
            });
            return FeatureLevel.UNKNOWN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            level = FeatureLevel.UNKNOWN;
        } catch (java.util.concurrent.ExecutionException e) {
            level = FeatureLevel.UNKNOWN;
        }
        remember(key, level);
        return level;
    }

    private void remember(String key, FeatureLevel level) {
        Duration ttl = level == FeatureLevel.UNKNOWN ? failureTtl : successTtl;
        cache.put(key, new Entry(level, nanoTime.getAsLong() + ttl.toNanos()));
    }
}
