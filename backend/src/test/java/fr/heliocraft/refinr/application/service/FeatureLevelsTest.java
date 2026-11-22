// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort.FeatureLevel;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Feature;
import fr.heliocraft.refinr.domain.model.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureLevelsTest {

    private static Feature feature(String name) {
        Feature feature = new Feature();
        feature.setName(name);
        feature.setStories(new ArrayList<>(List.of(new Story())));
        return feature;
    }

    private static Epic epic(Feature... features) {
        Epic epic = new Epic();
        epic.setFeatures(new ArrayList<>(List.of(features)));
        return epic;
    }

    @Test
    @DisplayName("Label feature-<normalized name>; none without a usable name")
    void label() {
        assertEquals("feature-room-booking", FeatureLevels.label(feature("Room Booking")));
        assertEquals("feature-reservation-de-salles", FeatureLevels.label(feature("  Réservation de salles ")));
        assertNull(FeatureLevels.label(feature(" ")));
        assertNull(FeatureLevels.label(feature("!!!")));
    }

    @Test
    @DisplayName("Policy: label by default, refuse, anything else refused with the property named")
    void policy() {
        assertEquals(FeatureLevels.Policy.LABEL, FeatureLevels.Policy.of(null));
        assertEquals(FeatureLevels.Policy.LABEL, FeatureLevels.Policy.of(" Label "));
        assertEquals(FeatureLevels.Policy.REFUSE, FeatureLevels.Policy.of("refuse"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> FeatureLevels.Policy.of("drop"));
        assertTrue(error.getMessage().contains("refinr.features.without-level"));
    }

    @Test
    @DisplayName("Preview warnings: only explicit features without a native level; nothing when the level is unknown")
    void previewWarnings() {
        IssueTrackerPort tracker = mock(IssueTrackerPort.class);
        DescribeTrackerService describe = new DescribeTrackerService(tracker);
        List<Epic> explicit = List.of(epic(feature("A"), feature("B")));
        List<Epic> implicit = List.of(epic(feature("A")));

        when(tracker.featureLevel(null)).thenReturn(FeatureLevel.NONE);
        assertEquals(List.of(FeatureLevels.LABELS_WARNING), describe.featureWarnings(explicit, null));
        assertTrue(describe.featureWarnings(implicit, null).isEmpty());
        assertEquals(List.of(FeatureLevels.REFUSE_WARNING),
                new DescribeTrackerService(tracker, FeatureLevels.Policy.REFUSE).featureWarnings(explicit, null));

        when(tracker.featureLevel(null)).thenReturn(FeatureLevel.OPTIONAL);
        assertTrue(describe.featureWarnings(explicit, null).isEmpty());
        when(tracker.featureLevel(null)).thenReturn(FeatureLevel.UNKNOWN);
        assertTrue(describe.featureWarnings(explicit, null).isEmpty());
        when(tracker.featureLevel(null)).thenThrow(new IllegalStateException("down"));
        assertTrue(describe.featureWarnings(explicit, null).isEmpty());
    }
}
