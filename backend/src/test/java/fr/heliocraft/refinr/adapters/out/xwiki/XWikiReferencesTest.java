// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.xwiki;

import fr.heliocraft.refinr.adapters.out.xwiki.XWikiReferences.DocumentRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class XWikiReferencesTest {

    @Test
    @DisplayName("Segments: dots split, \\. \\: \\\\ are escapes, optional wiki prefix removed")
    void segments() {
        assertEquals(Optional.of(List.of("ProjetStocks", "CDC-GestionDesStocks", "WebHome")),
                XWikiReferences.segments("ProjetStocks.CDC-GestionDesStocks.WebHome", "xwiki"));
        assertEquals(Optional.of(List.of("ProjetStocks", "Spec v1.2", "WebHome")),
                XWikiReferences.segments("xwiki:ProjetStocks.Spec v1\\.2.WebHome", "xwiki"));
        assertEquals(Optional.of(List.of("A:b", "c\\d")), XWikiReferences.segments("A\\:b.c\\\\d", "xwiki"));
        assertEquals(Optional.of(List.of("Main")), XWikiReferences.segments("Main", "xwiki"));
    }

    @Test
    @DisplayName("Invalid references: empty segment, trailing escape, slash, control char, other wiki, second colon")
    void invalid() {
        for (String value : new String[] {null, "", " ", "A..B", ".A", "A.", "A\\", "A/B", "A.B\nC", "other:A.B",
                "xwiki:A:B", " A.B", "A .B"}) {
            assertTrue(XWikiReferences.segments(value, "xwiki").isEmpty(), String.valueOf(value));
            assertFalse(XWikiReferences.isDocument(value, "xwiki"), String.valueOf(value));
        }
        assertTrue(XWikiReferences.isDocument("sub:A.WebHome", "sub"));
        assertFalse(XWikiReferences.isDocument("xwiki:A.WebHome", "sub"));
        assertFalse(XWikiReferences.isDocument("A".repeat(800), "xwiki"));
    }

    @Test
    @DisplayName("Candidates: exact reference ending in WebHome; else nested page first, then terminal page")
    void candidates() {
        assertEquals(List.of(new DocumentRef(List.of("ProjetStocks", "CDC"), "WebHome")),
                XWikiReferences.candidates("ProjetStocks.CDC.WebHome", "xwiki"));
        assertEquals(List.of(new DocumentRef(List.of("ProjetStocks", "CDC"), "WebHome"),
                        new DocumentRef(List.of("ProjetStocks"), "CDC")),
                XWikiReferences.candidates("xwiki:ProjetStocks.CDC", "xwiki"));
        // Un seul segment : seulement la page imbriquee (une page terminale a besoin d'un espace)
        assertEquals(List.of(new DocumentRef(List.of("Main"), "WebHome")), XWikiReferences.candidates("Main", "xwiki"));
        // "WebHome" seul n'est pas un document : traite comme un nom d'espace
        assertEquals(List.of(new DocumentRef(List.of("WebHome"), "WebHome")), XWikiReferences.candidates("WebHome", "xwiki"));
    }

    @Test
    @DisplayName("Serialization escapes dots, colons and backslashes like the Information tab")
    void serialize() {
        assertEquals("ProjetStocks.Spec v1\\.2.WebHome",
                new DocumentRef(List.of("ProjetStocks", "Spec v1.2"), "WebHome").serialize());
        assertEquals("A\\:b.c\\\\d", XWikiReferences.join(List.of("A:b", "c\\d")));
        assertEquals("ProjetStocks.Spec v1\\.2", XWikiReferences.spaceReference("xwiki:ProjetStocks.Spec v1\\.2", "xwiki"));
        assertEquals("ProjetStocks.CDC.WebHome", XWikiReferences.localId("xwiki:ProjetStocks.CDC.WebHome", "xwiki"));
    }
}
