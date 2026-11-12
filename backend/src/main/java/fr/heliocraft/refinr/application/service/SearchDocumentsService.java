package fr.heliocraft.refinr.application.service;

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
    public List<DocumentSummary> search(String spaceKey, String title) {
        return documentSource.searchDocuments(spaceKey, title);
    }
}
