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

    /** Source simulee : journalise les recherches recues. */
    static class RecordingSource implements DocumentSourcePort {
        final List<String> searches = new ArrayList<>();

        @Override
        public String getDocumentContent(String documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DocumentSummary> searchDocuments(String spaceKey, String title) {
            searches.add(spaceKey + "|" + title);
            return List.of(new DocumentSummary("1", title, null));
        }
    }

    @Test
    @DisplayName("Rejects an invalid space key or a blank title with InvalidRequestException, before any search")
    void rejectsInvalidInput() {
        RecordingSource source = new RecordingSource();
        SearchDocumentsService service = new SearchDocumentsService(source);

        assertEquals("Invalid spaceKey",
            assertThrows(InvalidRequestException.class, () -> service.search("SPACE OR type=page", "t")).getMessage());
        assertEquals("Invalid spaceKey",
            assertThrows(InvalidRequestException.class, () -> service.search(null, "t")).getMessage());
        assertEquals("title is required",
            assertThrows(InvalidRequestException.class, () -> service.search("SPACE", " ")).getMessage());
        assertTrue(source.searches.isEmpty());
    }

    @Test
    @DisplayName("Passes valid input through, including personal space keys")
    void passesValidInput() {
        RecordingSource source = new RecordingSource();

        new SearchDocumentsService(source).search("~557058:0a1b2c3d-4e5f", "notes");

        assertEquals(List.of("~557058:0a1b2c3d-4e5f|notes"), source.searches);
    }
}
