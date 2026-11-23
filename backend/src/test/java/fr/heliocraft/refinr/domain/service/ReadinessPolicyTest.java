// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.ReadinessSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReadinessPolicyTest {

    @Test
    @DisplayName("Only an explicit true is ai-ready")
    void onlyExplicitTrueIsAiReady() {
        assertEquals("ai-ready", ReadinessPolicy.verdictLabel(Boolean.TRUE));
        assertEquals("validation-necessaire", ReadinessPolicy.verdictLabel(Boolean.FALSE));
        assertEquals("validation-necessaire", ReadinessPolicy.verdictLabel(null));
        assertEquals("validation-necessaire", ReadinessPolicy.verdictLabel("true"));
    }

    @Test
    @DisplayName("A model answer is ai-ready only on a boolean true; a missing reason becomes empty")
    void readsModelAnswer() {
        assertEquals(new Readiness(true, "ok"), ReadinessPolicy.fromModelAnswer(true, "ok"));
        assertEquals(new Readiness(false, ""), ReadinessPolicy.fromModelAnswer("yes", null));
        assertEquals(new Readiness(false, ""), ReadinessPolicy.fromModelAnswer(null, 42));
    }

    @Test
    @DisplayName("Without a reliable verdict a story is never ai-ready; source fallback; the text is frozen until v2.0")
    void unavailableVerdictRequiresHumanValidation() {
        // Texte fige : le plugin 0.1.0 le reconnait par une regex exacte (src/verdict.ts) ; ne change qu'en v2.0
        assertEquals(new Readiness(false, "verdict indisponible (TimeoutException) : validation humaine necessaire",
                ReadinessSource.FALLBACK), ReadinessPolicy.unavailable("TimeoutException"));
        assertTrue(ReadinessPolicy.unavailable("no verdict").aiReadyReason()
                .matches("^verdict indisponible \\(no verdict\\) : validation humaine necessaire$"));
    }

    @Test
    @DisplayName("Readiness: two-argument constructor means model; source required; REST ids model / fallback / human")
    void readinessSource() {
        assertEquals(ReadinessSource.MODEL, new Readiness(true, "ok").source());
        assertThrows(IllegalArgumentException.class, () -> new Readiness(true, "ok", null));
        assertEquals(List.of("model", "fallback", "human"),
                java.util.Arrays.stream(ReadinessSource.values()).map(ReadinessSource::id).toList());
    }

    @Test
    @DisplayName("Human validation: ai-ready, source human, fixed reason")
    void humanValidation() {
        Readiness verdict = ReadinessPolicy.humanValidation();
        assertTrue(verdict.aiReady());
        assertEquals(ReadinessSource.HUMAN, verdict.source());
        assertEquals("validée par un humain, sans appel au modèle", verdict.aiReadyReason());
    }

    @Test
    @DisplayName("Replacing the verdict label keeps the other labels in order")
    void replacesVerdictLabel() {
        assertEquals(List.of("backend", "pdf", "ai-ready"),
            ReadinessPolicy.replaceVerdictLabel(List.of("backend", "validation-necessaire", "pdf"), true));
        assertEquals(List.of("backend", "validation-necessaire"),
            ReadinessPolicy.replaceVerdictLabel(List.of("ai-ready", "backend"), false));
    }

    @Test
    @DisplayName("Only stories labelled validation-necessaire are awaiting validation")
    void detectsStoriesAwaitingValidation() {
        assertTrue(ReadinessPolicy.isAwaitingValidation(List.of("backend", "validation-necessaire")));
        assertFalse(ReadinessPolicy.isAwaitingValidation(List.of("ai-ready")));
    }
}
