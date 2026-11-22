// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EpicTest {

    private static Story story(String title) {
        Story story = new Story();
        story.setTitle(title);
        return story;
    }

    private static Feature feature(String name, Story... stories) {
        Feature feature = new Feature();
        feature.setName(name);
        feature.setStories(new ArrayList<>(List.of(stories)));
        return feature;
    }

    @Test
    @DisplayName("getStories gives every story of the epic, flat, in the order of its features, read-only")
    void storiesAreFlattened() {
        Epic epic = new Epic();
        epic.setFeatures(new ArrayList<>(Arrays.asList(feature("Booking", story("S1"), story("S2")), null,
                feature("Notifications", story("S3")))));

        assertEquals(List.of("S1", "S2", "S3"), epic.getStories().stream().map(Story::getTitle).toList());
        assertThrows(UnsupportedOperationException.class, () -> epic.getStories().add(story("S4")));
        assertFalse(epic.hasImplicitFeature());
    }

    @Test
    @DisplayName("setStories (form before features) makes one implicit feature without a name; empty or null: none")
    void legacyStoriesMakeOneImplicitFeature() {
        Epic epic = new Epic();
        epic.setStories(List.of(story("S1"), story("S2")));

        assertEquals(1, epic.getFeatures().size());
        assertNull(epic.getFeatures().getFirst().getName());
        assertTrue(epic.hasImplicitFeature());
        assertTrue(epic.isImplicit(epic.getFeatures().getFirst()));
        assertEquals(2, epic.getStories().size());

        epic.setStories(List.of());
        assertTrue(epic.getFeatures().isEmpty());
        epic.setStories(null);
        assertTrue(epic.getStories().isEmpty());
    }

    @Test
    @DisplayName("The only feature of an epic is implicit; with two features, neither is")
    void implicitFeatureRule() {
        Feature only = feature("Export", story("S1"));
        Epic single = new Epic();
        single.setFeatures(new ArrayList<>(List.of(only)));
        assertTrue(single.isImplicit(only));

        Feature first = feature("Booking", story("S1"));
        Epic two = new Epic();
        two.setFeatures(new ArrayList<>(List.of(first, feature("Admin", story("S2")))));
        assertFalse(two.isImplicit(first));
        assertFalse(two.isImplicit(only));
    }
}
