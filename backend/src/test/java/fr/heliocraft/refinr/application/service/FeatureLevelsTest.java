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

    private static List<String> warnings(FeatureLevel level, FeatureLevels.Policy policy, List<Epic> epics) {
        IssueTrackerPort tracker = mock(IssueTrackerPort.class);
        when(tracker.featureLevel(null)).thenReturn(level);
        return new DescribeTrackerService(tracker, policy).featureWarnings(epics, null);
    }

    @Test
    @DisplayName("Preview warnings: explicit features without a native level; level not checked when unknown; none otherwise")
    void previewWarnings() {
        List<Epic> explicit = List.of(epic(feature("A"), feature("B")));
        List<Epic> implicit = List.of(epic(feature("A")));

        assertEquals(List.of(FeatureLevels.LABELS_WARNING), warnings(FeatureLevel.NONE, FeatureLevels.Policy.LABEL, explicit));
        assertTrue(warnings(FeatureLevel.NONE, FeatureLevels.Policy.LABEL, implicit).isEmpty());
        assertEquals(List.of(FeatureLevels.REFUSE_WARNING), warnings(FeatureLevel.NONE, FeatureLevels.Policy.REFUSE, explicit));
        assertTrue(warnings(FeatureLevel.OPTIONAL, FeatureLevels.Policy.LABEL, explicit).isEmpty());
        assertEquals(List.of(FeatureLevels.UNCHECKED_WARNING),
                warnings(FeatureLevel.UNKNOWN, FeatureLevels.Policy.LABEL, explicit));

        IssueTrackerPort down = mock(IssueTrackerPort.class);
        when(down.featureLevel(null)).thenThrow(new IllegalStateException("down"));
        assertEquals(List.of(FeatureLevels.UNCHECKED_WARNING), new DescribeTrackerService(down).featureWarnings(explicit, null));
        assertTrue(new DescribeTrackerService(down).featureWarnings(implicit, null).isEmpty(), "no lookup without explicit features");
    }
}
