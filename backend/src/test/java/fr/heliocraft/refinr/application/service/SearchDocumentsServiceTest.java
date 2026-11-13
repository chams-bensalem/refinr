// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.domain.model.DocumentSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchDocumentsServiceTest {

    /** Source simulee : journalise les recherches recues ; seuls les espaces "SPACE" et "~..." ont son format. */
    static class RecordingSource implements DocumentSourcePort {
        final List<String> searches = new ArrayList<>();
        final List<String> checkedSpaces = new ArrayList<>();

        @Override
        public String displayName() {
            return "Test source";
        }

        @Override
        public boolean acceptsSpace(String space) {
            checkedSpaces.add(space);
            return space.equals("SPACE") || space.startsWith("~");
        }

        @Override
        public boolean acceptsDocumentId(String documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String documentUrl(String documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDocumentContent(String documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DocumentSummary> searchDocuments(String space, String title) {
            searches.add(space + "|" + title);
            return List.of(new DocumentSummary("1", title, null));
        }
    }

    @Test
    @DisplayName("Rejects a space refused by the source or a blank title with InvalidRequestException, before any search")
    void rejectsInvalidInput() {
        RecordingSource source = new RecordingSource();
        SearchDocumentsService service = new SearchDocumentsService(source);

        assertEquals("Invalid space",
            assertThrows(InvalidRequestException.class, () -> service.search("SPACE OR type=page", "t")).getMessage());
        assertEquals("title is required",
            assertThrows(InvalidRequestException.class, () -> service.search("SPACE", " ")).getMessage());
        assertEquals("title is required",
            assertThrows(InvalidRequestException.class, () -> service.search(null, null)).getMessage());
        assertEquals(List.of("SPACE OR type=page", "SPACE"), source.checkedSpaces);
        assertTrue(source.searches.isEmpty());
    }

    @Test
    @DisplayName("Passes valid input through, including personal space keys")
    void passesValidInput() {
        RecordingSource source = new RecordingSource();

        new SearchDocumentsService(source).search("~557058:0a1b2c3d-4e5f", "notes");

        assertEquals(List.of("~557058:0a1b2c3d-4e5f|notes"), source.searches);
    }

    @Test
    @DisplayName("Without a space (absent or blank), searches every space: null is passed to the source, no format check")
    void searchesEverySpaceWithoutSpace() {
        RecordingSource source = new RecordingSource();
        SearchDocumentsService service = new SearchDocumentsService(source);

        service.search(null, "notes");
        service.search("  ", "spec");

        assertEquals(List.of("null|notes", "null|spec"), source.searches);
        assertTrue(source.checkedSpaces.isEmpty());
    }
}
