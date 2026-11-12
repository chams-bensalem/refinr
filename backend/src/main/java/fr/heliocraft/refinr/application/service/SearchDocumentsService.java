package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.SearchDocuments;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;

import java.util.Map;

public class SearchDocumentsService implements SearchDocuments {

    private final DocumentSourcePort documentSource;

    public SearchDocumentsService(DocumentSourcePort documentSource) {
        this.documentSource = documentSource;
    }

    @Override
    public Map<String, Object> search(String spaceKey, String title) {
        return documentSource.searchDocuments(spaceKey, title);
    }
}
