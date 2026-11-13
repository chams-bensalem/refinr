// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.application.port.in.DescribeSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Source documentaire active de l'instance (une seule, choisie par {@code source.provider}), pour les libelles de
 * l'interface : {@code {"name": "Confluence"}}. Ni URL, ni espace, ni cle.
 */
@RestController
@RequestMapping("/api/v1/source")
public class SourceController {

    private final DescribeSource describeSource;

    public SourceController(DescribeSource describeSource) {
        this.describeSource = describeSource;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> source() {
        // maxContentChars : limite du mode texte (content), pour le compteur de l'interface ; ajout retrocompatible
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", describeSource.displayName());
        body.put("maxContentChars", describeSource.maxContentChars());
        return ResponseEntity.ok(body);
    }
}
