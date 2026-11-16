// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.domain.model.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutputLanguagePolicyTest {

    private static final String ENGLISH = "The customer can cancel an order from the account page as long as it has not been shipped.";
    private static final String FRENCH = "Le client peut annuler une commande depuis la page de son compte tant qu'elle n'est pas expédiée.";

    @Test
    @DisplayName("Priority: the request, then the property, then detection (French by default)")
    void priority() {
        OutputLanguagePolicy english = OutputLanguagePolicy.fromProperty("en");
        assertEquals(Language.of("de"), english.resolve(Language.of("de"), FRENCH), "the request wins over the property");
        assertEquals(Language.EN, english.resolve(null, FRENCH), "the property wins over detection");
        OutputLanguagePolicy auto = OutputLanguagePolicy.fromProperty("auto");
        assertEquals(Language.of("de"), auto.resolve(Language.of("de"), ENGLISH), "the request wins over detection");
        assertEquals(Language.EN, auto.resolve(null, ENGLISH));
        assertEquals(Language.FR, auto.resolve(null, FRENCH));
        assertEquals(Language.FR, auto.resolve(null, ""), "French by default");
    }

    @Test
    @DisplayName("Property: auto or an ISO 639-1 code (case ignored), anything else refused at startup with the property name")
    void property() {
        assertEquals(Language.EN, OutputLanguagePolicy.fromProperty("EN").resolve(null, FRENCH));
        for (String invalid : new String[] {"xx", "fr-FR", "", "english", "Auto"}) {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> OutputLanguagePolicy.fromProperty(invalid));
            assertEquals("refinr.llm.output-language must be auto or an ISO 639-1 code (e.g. fr, en)", e.getMessage());
        }
    }
}
