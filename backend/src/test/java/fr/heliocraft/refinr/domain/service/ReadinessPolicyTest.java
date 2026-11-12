package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.domain.model.Readiness;
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
    @DisplayName("Without a reliable verdict a story is never ai-ready")
    void unavailableVerdictRequiresHumanValidation() {
        assertEquals(new Readiness(false, "verdict indisponible (TimeoutException) : validation humaine necessaire"),
            ReadinessPolicy.unavailable("TimeoutException"));
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
