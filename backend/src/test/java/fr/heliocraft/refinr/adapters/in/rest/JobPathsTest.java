// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobPathsTest {

    private static final String ID = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";

    @Test
    @DisplayName("A job id is a lowercase UUID; anything else is refused")
    void jobIdFormat() {
        assertTrue(JobPaths.isJobId(ID));
        assertTrue(JobPaths.isJobId(UUID.randomUUID().toString()));
        assertFalse(JobPaths.isJobId(ID.toUpperCase()));
        assertFalse(JobPaths.isJobId(ID + "0"));
        assertFalse(JobPaths.isJobId("../" + ID));
        assertFalse(JobPaths.isJobId(""));
        assertFalse(JobPaths.isJobId(null));
    }

    @Test
    @DisplayName("Logged paths keep only the first 8 characters of a job id; other paths are unchanged")
    void pathsForLogs() {
        assertEquals("/api/v1/jobs/1a2b3c4d…", JobPaths.forLogs("/api/v1/jobs/" + ID));
        assertEquals("/api/v1/jobs/1a2b3c4d…/result", JobPaths.forLogs("/api/v1/jobs/" + ID + "/result"));
        assertEquals("/api/v1/jobs/1A2B3C4D…", JobPaths.forLogs("/api/v1/jobs/" + ID.toUpperCase()));
        assertEquals("/api/v1/jobs/analyze-preview", JobPaths.forLogs("/api/v1/jobs/analyze-preview"));
        assertEquals("/api/v1/analyze-preview", JobPaths.forLogs("/api/v1/analyze-preview"));
        assertNull(JobPaths.forLogs(null));
    }
}
