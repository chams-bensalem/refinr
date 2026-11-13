// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DescribeTrackerServiceTest {

    private final IssueTrackerPort tracker = mock(IssueTrackerPort.class);
    private final DescribeTrackerService service = new DescribeTrackerService(tracker);

    @Test
    @DisplayName("The name and the project format come from the active tracker")
    void delegatesToTheActiveTracker() {
        when(tracker.displayName()).thenReturn("Some tracker");
        when(tracker.acceptsProjectId("ok")).thenReturn(true);

        assertEquals("Some tracker", service.displayName());
        assertTrue(service.acceptsProjectId("ok"));
        assertFalse(service.acceptsProjectId("ko"));
    }

    @Test
    @DisplayName("A null or blank project is never a project of the tracker")
    void nullOrBlankIsRefusedWithoutAskingTheTracker() {
        assertFalse(service.acceptsProjectId(null));
        assertFalse(service.acceptsProjectId(" "));
        verifyNoInteractions(tracker);
    }
}
