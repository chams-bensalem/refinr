// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.openproject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cas releves contre OpenProject 17.9.7 en v1.1 phase 0 (cf. DECISIONS.md). */
class OpenProjectProjectIdsTest {

    @ParameterizedTest
    @ValueSource(strings = {"refinr-spike", "a_b-c", "1abc", "-abc", "x", "3", "42"})
    @DisplayName("OpenProject identifiers (lowercase, digits, - and _) and numeric ids are accepted")
    void acceptsIdentifiersAndIds(String id) {
        assertTrue(OpenProjectProjectIds.isValid(id));
    }

    @Test
    @DisplayName("Up to 100 characters")
    void lengthLimit() {
        assertTrue(OpenProjectProjectIds.isValid("a".repeat(100)));
        assertFalse(OpenProjectProjectIds.isValid("a".repeat(101)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Mon Projet", "UPPER", "Refinr-spike", "mon projet", "mon.projet", "demo; drop", "projet/1"})
    @DisplayName("Uppercase, spaces and other characters are refused")
    void refusesOthers(String id) {
        assertFalse(OpenProjectProjectIds.isValid(id));
    }
}
