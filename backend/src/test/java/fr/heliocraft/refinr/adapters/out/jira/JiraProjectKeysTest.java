// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.jira;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JiraProjectKeysTest {

    @ParameterizedTest
    @ValueSource(strings = {"PROJ", "DEMO2", "AB", "A123456789"})
    @DisplayName("Jira project keys: an uppercase letter then 1 to 9 uppercase letters or digits")
    void acceptsJiraKeys(String key) {
        assertTrue(JiraProjectKeys.isValid(key));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"A", "A1234567890", "demo", "Proj", "2PROJ", "PR-OJ", "demo; drop", " PROJ"})
    @DisplayName("Anything else is refused")
    void refusesOthers(String key) {
        assertFalse(JiraProjectKeys.isValid(key));
    }
}
