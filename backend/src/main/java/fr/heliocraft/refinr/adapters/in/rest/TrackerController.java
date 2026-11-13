// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.application.port.in.DescribeTracker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Gestionnaire de tickets actif de l'instance (un seul, choisi par {@code tracker.provider}), pour les libelles de
 * l'interface : {@code {"name": "Jira"}} ou {@code {"name": "OpenProject"}}. Ni URL, ni projet, ni cle.
 */
@RestController
@RequestMapping("/api/v1/tracker")
public class TrackerController {

    private final DescribeTracker describeTracker;

    public TrackerController(DescribeTracker describeTracker) {
        this.describeTracker = describeTracker;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> tracker() {
        return ResponseEntity.ok(Map.of("name", describeTracker.displayName()));
    }
}
