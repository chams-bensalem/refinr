// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.CheckHealth.HealthReport;
import fr.heliocraft.refinr.application.port.out.HealthCheckable;
import fr.heliocraft.refinr.domain.model.ComponentHealth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CheckHealthServiceTest {

    /** Composant simule qui compte ses verifications. */
    static class Counting implements HealthCheckable {
        final AtomicInteger calls = new AtomicInteger();
        final ComponentHealth result;

        Counting(ComponentHealth result) {
            this.result = result;
        }

        @Override
        public List<ComponentHealth> health() {
            calls.incrementAndGet();
            return List.of(result);
        }
    }

    @Test
    @DisplayName("Aggregates every component in order; one DOWN component makes the report not all-up")
    void aggregates() {
        Counting confluence = new Counting(ComponentHealth.up("confluence", Map.of()));
        Counting ai = new Counting(ComponentHealth.down("ai", "HTTP 401", Map.of()));

        HealthReport report = new CheckHealthService(List.of(confluence, ai), Duration.ZERO).check();

        assertFalse(report.allUp());
        assertEquals(List.of("confluence", "ai"), report.components().stream().map(ComponentHealth::name).toList());
    }

    @Test
    @DisplayName("The report is cached for the configured TTL, then refreshed")
    void cachesForTtl() {
        Counting jira = new Counting(ComponentHealth.up("jira", Map.of()));
        AtomicLong now = new AtomicLong();
        CheckHealthService service = new CheckHealthService(List.of(jira), Duration.ofSeconds(45), now::get);

        service.check();
        now.addAndGet(Duration.ofSeconds(44).toNanos());
        service.check();
        assertEquals(1, jira.calls.get(), "within the TTL the providers are not called again");

        now.addAndGet(Duration.ofSeconds(2).toNanos());
        service.check();
        assertEquals(2, jira.calls.get());
    }

    @Test
    @DisplayName("A check that throws is reported DOWN instead of failing the whole report")
    void failingCheckIsDown() {
        HealthCheckable broken = () -> {
            throw new IllegalStateException("boom");
        };

        HealthReport report = new CheckHealthService(List.of(broken), Duration.ZERO).check();

        assertFalse(report.allUp());
        assertEquals("IllegalStateException", report.components().get(0).details().get("error"));
    }
}
