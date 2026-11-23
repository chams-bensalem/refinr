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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Lecture du niveau Feature pendant l'apercu : 5 s au plus, cache par projet (succes 1 h, echec 1 min). */
class FeatureLevelLookupTest {

    private static List<Epic> explicitFeatures() {
        Epic epic = new Epic();
        epic.setName("Rooms");
        List<Feature> features = new ArrayList<>();
        for (String name : List.of("Booking", "Administration")) {
            Feature feature = new Feature();
            feature.setName(name);
            feature.setStories(new ArrayList<>(List.of(new Story())));
            features.add(feature);
        }
        epic.setFeatures(features);
        return List.of(epic);
    }

    @Test
    @DisplayName("Slow tracker: the preview gets its warnings in less than 6 s, with 'could not be checked'")
    void slowTrackerIsBounded() {
        IssueTrackerPort tracker = mock(IssueTrackerPort.class);
        when(tracker.previewFeatureLevel(any())).thenAnswer(call -> {
            Thread.sleep(20_000);
            return FeatureLevel.OPTIONAL;
        });
        DescribeTrackerService describe = new DescribeTrackerService(tracker, FeatureLevels.Policy.LABEL);

        long start = System.nanoTime();
        List<String> warnings = describe.featureWarnings(explicitFeatures(), null);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(millis < 6_000, "preview waited " + millis + " ms");
        assertTrue(millis >= 4_900, "the lookup gets its 5 s: " + millis + " ms");
        assertEquals(List.of(FeatureLevels.UNCHECKED_WARNING), warnings);
    }

    @Test
    @DisplayName("Failure cached: two close previews make one tracker call, both with 'could not be checked'")
    void failureIsCached() {
        IssueTrackerPort tracker = mock(IssueTrackerPort.class);
        when(tracker.previewFeatureLevel(any())).thenThrow(new IllegalStateException("tracker down"));
        DescribeTrackerService describe = new DescribeTrackerService(tracker, FeatureLevels.Policy.LABEL);

        assertEquals(List.of(FeatureLevels.UNCHECKED_WARNING), describe.featureWarnings(explicitFeatures(), null));
        assertEquals(List.of(FeatureLevels.UNCHECKED_WARNING), describe.featureWarnings(explicitFeatures(), null));

        verify(tracker, times(1)).previewFeatureLevel(any());
    }

    @Test
    @DisplayName("Success cached: two previews make one tracker call; the cache is per project")
    void successIsCached() {
        IssueTrackerPort tracker = mock(IssueTrackerPort.class);
        when(tracker.previewFeatureLevel(any())).thenReturn(FeatureLevel.NONE);
        DescribeTrackerService describe = new DescribeTrackerService(tracker, FeatureLevels.Policy.LABEL);

        assertEquals(List.of(FeatureLevels.LABELS_WARNING), describe.featureWarnings(explicitFeatures(), null));
        assertEquals(List.of(FeatureLevels.LABELS_WARNING), describe.featureWarnings(explicitFeatures(), null));
        describe.featureWarnings(explicitFeatures(), "OTHER");

        verify(tracker, times(1)).previewFeatureLevel(null);
        verify(tracker, times(1)).previewFeatureLevel("OTHER");
    }

    @Test
    @DisplayName("Missing permission: the warning says it (not 'could not be checked'), kept 1 min like a failure")
    void missingPermissionIsSaid() {
        AtomicLong now = new AtomicLong();
        IssueTrackerPort tracker = mock(IssueTrackerPort.class);
        String noPermission = "Refinr has no permission to create work packages in project refinr-spike-ro";
        when(tracker.previewFeatureLevel("refinr-spike-ro"))
                .thenThrow(new fr.heliocraft.refinr.application.port.out.TrackerPermissionException(noPermission))
                .thenReturn(FeatureLevel.NONE);
        FeatureLevelLookup lookup = new FeatureLevelLookup(tracker, Duration.ofSeconds(5), Duration.ofHours(1),
                Duration.ofMinutes(1), now::get);
        DescribeTrackerService describe = new DescribeTrackerService(tracker, FeatureLevels.Policy.LABEL, lookup);

        assertEquals(List.of(noPermission), describe.featureWarnings(explicitFeatures(), "refinr-spike-ro"));
        assertEquals(List.of(noPermission), describe.featureWarnings(explicitFeatures(), "refinr-spike-ro"), "cached");
        verify(tracker, times(1)).previewFeatureLevel("refinr-spike-ro");
        now.addAndGet(Duration.ofSeconds(61).toNanos());
        assertEquals(List.of(FeatureLevels.LABELS_WARNING), describe.featureWarnings(explicitFeatures(), "refinr-spike-ro"),
                "permission granted later: read again after 1 min");
    }

    @Test
    @DisplayName("Cache lifetimes: a failure is read again after 1 min, a success after 1 h")
    void cacheExpiry() {
        AtomicLong now = new AtomicLong();
        IssueTrackerPort tracker = mock(IssueTrackerPort.class);
        when(tracker.previewFeatureLevel(any())).thenThrow(new IllegalStateException("down")).thenReturn(FeatureLevel.OPTIONAL);
        FeatureLevelLookup lookup = new FeatureLevelLookup(tracker, Duration.ofSeconds(5), Duration.ofHours(1),
                Duration.ofMinutes(1), now::get);

        assertEquals(FeatureLevel.UNKNOWN, lookup.level(null));
        now.addAndGet(Duration.ofSeconds(59).toNanos());
        assertEquals(FeatureLevel.UNKNOWN, lookup.level(null), "failure still cached");
        now.addAndGet(Duration.ofSeconds(2).toNanos());
        assertEquals(FeatureLevel.OPTIONAL, lookup.level(null), "failure expired after 1 min");
        now.addAndGet(Duration.ofMinutes(59).toNanos());
        assertEquals(FeatureLevel.OPTIONAL, lookup.level(null));
        verify(tracker, times(2)).previewFeatureLevel(any());
        now.addAndGet(Duration.ofMinutes(2).toNanos());
        lookup.level(null);
        verify(tracker, times(3)).previewFeatureLevel(any());
    }
}
