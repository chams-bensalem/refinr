// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.ReadinessSource;
import fr.heliocraft.refinr.domain.model.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    private static GeneratedStory generatedStory(String title) {
        GeneratedStory story = new GeneratedStory();
        story.setTitle(title);
        story.setAiReady(true);
        story.setAiReadyReason("clear");
        return story;
    }

    @Test
    @DisplayName("Features of the model are kept in order, with their stories")
    void featuresAreMapped() {
        GeneratedFeature booking = new GeneratedFeature();
        booking.setName("Booking");
        booking.setDescription("Book a room");
        booking.setPriority("High");
        booking.setLabels(List.of("backend"));
        booking.setStories(List.of(generatedStory("Book"), generatedStory("Cancel")));
        GeneratedFeature admin = new GeneratedFeature();
        admin.setName("Administration");
        admin.setStories(List.of(generatedStory("Add a room")));
        GeneratedEpic generated = new GeneratedEpic();
        generated.setName("Rooms");
        generated.setFeatures(List.of(booking, admin));

        Epic epic = GeneratedBacklogMapper.toDomain(generated);

        assertEquals(List.of("Booking", "Administration"), epic.getFeatures().stream().map(f -> f.getName()).toList());
        assertEquals("Book a room", epic.getFeatures().getFirst().getDescription());
        assertEquals(List.of("Book", "Cancel", "Add a room"), epic.getStories().stream().map(Story::getTitle).toList());
        assertFalse(epic.hasImplicitFeature());
    }

    @Test
    @DisplayName("An epic without features (former shape) is read as one implicit feature rather than refused")
    void epicWithoutFeaturesIsOneImplicitFeature() {
        GeneratedEpic generated = new GeneratedEpic();
        generated.setName("Export");
        generated.setFeatures(List.of());
        generated.setStories(List.of(generatedStory("Export orders")));

        Epic epic = GeneratedBacklogMapper.toDomain(generated);

        assertTrue(epic.hasImplicitFeature());
        assertEquals("Export orders", epic.getStories().getFirst().getTitle());
    }
}
