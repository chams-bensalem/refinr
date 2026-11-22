// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.ReadinessSource;
import fr.heliocraft.refinr.domain.model.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedBacklogMapperTest {

    @Test
    @DisplayName("Verdict of the generation: source model")
    void verdictFromTheModel() {
        GeneratedStory dto = new GeneratedStory();
        dto.setTitle("S");
        dto.setAiReady(true);
        dto.setAiReadyReason("clair");

        Story story = GeneratedBacklogMapper.toDomain(dto);

        assertEquals(Boolean.TRUE, story.getAiReady());
        assertEquals("clair", story.getAiReadyReason());
        assertEquals(ReadinessSource.MODEL, story.getAiReadySource());
    }

    @Test
    @DisplayName("aiReady missing (model not following the schema): fallback verdict, never ai-ready")
    void missingVerdictIsAFallback() {
        GeneratedStory dto = new GeneratedStory();
        dto.setTitle("S");
        dto.setAiReadyReason("ignored");

        Story story = GeneratedBacklogMapper.toDomain(dto);

        assertEquals(Boolean.FALSE, story.getAiReady());
        assertEquals("verdict indisponible (no verdict) : validation humaine necessaire", story.getAiReadyReason());
        assertEquals(ReadinessSource.FALLBACK, story.getAiReadySource());
    }
}
