package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.CheckHealth;
import fr.heliocraft.refinr.application.port.out.HealthCheckable;
import fr.heliocraft.refinr.domain.model.ComponentHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

/**
 * Verifie les dependances en parallele et garde le resultat {@code cacheTtl} (le header du frontend interroge le
 * health check a chaque affichage : les API des fournisseurs ne sont pas sollicitees a chaque fois).
 */
public class CheckHealthService implements CheckHealth {

    private static final Logger log = LoggerFactory.getLogger(CheckHealthService.class);

    // Borne globale : chaque adaptateur a deja son propre delai court
    private static final Duration MAX_WAIT = Duration.ofSeconds(15);

    private final List<HealthCheckable> checks;
    private final long cacheTtlNanos;
    private final LongSupplier nanoTime;

    private HealthReport cached;
    private long cachedAt;

    public CheckHealthService(List<HealthCheckable> checks, Duration cacheTtl) {
        this(checks, cacheTtl, System::nanoTime);
    }

    CheckHealthService(List<HealthCheckable> checks, Duration cacheTtl, LongSupplier nanoTime) {
        this.checks = List.copyOf(checks);
        this.cacheTtlNanos = cacheTtl.toNanos();
        this.nanoTime = nanoTime;
    }

    @Override
    public synchronized HealthReport check() {
        long now = nanoTime.getAsLong();
        if (cached != null && now - cachedAt < cacheTtlNanos) {
            return cached;
        }
        cached = checkAll();
        cachedAt = now;
        return cached;
    }

    private HealthReport checkAll() {
        List<ComponentHealth> components = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<ComponentHealth>>> futures = checks.stream()
                    .map(check -> executor.submit(check::health))
                    .toList();
            long deadline = System.nanoTime() + MAX_WAIT.toNanos();
            for (int i = 0; i < futures.size(); i++) {
                components.addAll(result(futures.get(i), i, deadline));
            }
            executor.shutdownNow();
        }
        boolean allUp = components.stream().allMatch(ComponentHealth::up);
        return new HealthReport(allUp, components);
    }

    private List<ComponentHealth> result(Future<List<ComponentHealth>> future, int index, long deadline) {
        try {
            return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return List.of(ComponentHealth.down("component-" + index, "timeout", Map.of()));
        } catch (ExecutionException e) {
            log.error("Health check {} failed", index, e.getCause());
            return List.of(ComponentHealth.down("component-" + index, e.getCause().getClass().getSimpleName(), Map.of()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of(ComponentHealth.down("component-" + index, "interrupted", Map.of()));
        }
    }
}
