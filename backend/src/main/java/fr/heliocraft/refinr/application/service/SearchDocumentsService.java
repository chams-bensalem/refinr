// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.in.SearchDocuments;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.domain.model.DocumentSummary;

import java.util.List;

public class SearchDocumentsService implements SearchDocuments {

    private final DocumentSourcePort documentSource;

    public SearchDocumentsService(DocumentSourcePort documentSource) {
        this.documentSource = documentSource;
    }

    @Override
    public List<DocumentSummary> search(String space, String title) {
        // Espace optionnel (absent ou vide : tous les espaces) ; son format est celui de la source active
        boolean anySpace = space == null || space.isBlank();
        if (!anySpace && !documentSource.acceptsSpace(space)) {
            throw new InvalidRequestException("Invalid space");
        }
        if (title == null || title.isBlank()) {
            throw new InvalidRequestException("title is required");
        }
        return documentSource.searchDocuments(anySpace ? null : space, title);
    }
}
