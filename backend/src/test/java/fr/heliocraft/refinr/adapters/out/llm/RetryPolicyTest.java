package fr.heliocraft.refinr.adapters.out.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private final RetryPolicy policy = new RetryPolicy(2, Duration.ofSeconds(2), 4, 0.2, Duration.ofSeconds(30));

    @Test
    @DisplayName("Exponential backoff: 2 s then 8 s, within +/- 20 % jitter")
    void exponentialWithJitter() {
        assertEquals(Duration.ofSeconds(2), policy.delay(1, null, 0.5, NOW));
        assertEquals(Duration.ofSeconds(8), policy.delay(2, null, 0.5, NOW));
        assertEquals(Duration.ofMillis(1600), policy.delay(1, null, 0.0, NOW));
        assertEquals(Duration.ofMillis(9600), policy.delay(2, null, 1.0, NOW));
        for (double random = 0; random < 1; random += 0.05) {
            Duration second = policy.delay(2, null, random, NOW);
            assertTrue(second.compareTo(Duration.ofMillis(6400)) >= 0 && second.compareTo(Duration.ofMillis(9600)) <= 0, second.toString());
        }
    }

    @Test
    @DisplayName("Delays are capped by maxDelay")
    void capped() {
        assertEquals(Duration.ofSeconds(30), policy.delay(3, null, 0.5, NOW)); // 32 s -> 30 s
        assertEquals(Duration.ofSeconds(30), policy.delay(40, null, 1.0, NOW));
    }

    @Test
    @DisplayName("Retry-After wins over the backoff, without jitter, capped by maxDelay")
    void retryAfterWins() {
        assertEquals(Duration.ofSeconds(5), policy.delay(1, "5", 0.0, NOW));
        assertEquals(Duration.ofSeconds(0), policy.delay(2, "0", 1.0, NOW));
        assertEquals(Duration.ofSeconds(30), policy.delay(1, "120", 0.5, NOW));
        assertEquals(Duration.ofSeconds(12), policy.delay(1, "Fri, 13 Nov 2026 10:00:12 GMT", 0.5, NOW));
        assertEquals(Duration.ZERO, policy.delay(1, "Fri, 13 Nov 2026 09:59:00 GMT", 0.5, NOW));
        assertEquals(Duration.ofSeconds(30), policy.delay(1, "Fri, 13 Nov 2026 11:00:00 GMT", 0.5, NOW));
    }

    @Test
    @DisplayName("An unreadable or negative Retry-After falls back to the backoff")
    void unreadableRetryAfter() {
        assertEquals(Duration.ofSeconds(2), policy.delay(1, "soon", 0.5, NOW));
        assertEquals(Duration.ofSeconds(2), policy.delay(1, "-3", 0.5, NOW));
        assertEquals(Duration.ofSeconds(2), policy.delay(1, " ", 0.5, NOW));
    }

    @Test
    @DisplayName("Invalid policies are refused")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(-1, Duration.ZERO, 1, 0, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(1, Duration.ZERO, 0.5, 0, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(1, Duration.ZERO, 1, 1.5, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(1, Duration.ofSeconds(-1), 1, 0, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RetryPolicy(1, Duration.ZERO, Double.NaN, 0, Duration.ZERO));
    }
}
