// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DescribeSourceServiceTest {

    private final DocumentSourcePort source = mock(DocumentSourcePort.class);
    private final DescribeSourceService service = new DescribeSourceService(source);

    @Test
    @DisplayName("The name and the space format come from the active document source")
    void delegatesToTheActiveSource() {
        when(source.displayName()).thenReturn("Some source");
        when(source.acceptsSpace("ok")).thenReturn(true);

        assertEquals("Some source", service.displayName());
        assertTrue(service.acceptsSpace("ok"));
        assertFalse(service.acceptsSpace("ko"));
    }

    @Test
    @DisplayName("A null or blank space is never a space of the source")
    void nullOrBlankIsRefusedWithoutAskingTheSource() {
        assertFalse(service.acceptsSpace(null));
        assertFalse(service.acceptsSpace(" "));
        verifyNoInteractions(source);
    }

    @Test
    @DisplayName("documentUrl comes from the source, only for an id it accepts (no call for an invalid id)")
    void documentUrl() {
        when(source.acceptsDocumentId("42")).thenReturn(true);
        when(source.documentUrl("42")).thenReturn("https://docs.example.com/link/42");

        assertEquals("https://docs.example.com/link/42", service.documentUrl("42"));
        assertNull(service.documentUrl("x"));
        assertNull(service.documentUrl(null));
        verify(source, never()).documentUrl("x");
    }
}
