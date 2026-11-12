package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.in.SearchDocuments;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.domain.model.DocumentSummary;

import java.util.List;
import java.util.regex.Pattern;

public class SearchDocumentsService implements SearchDocuments {

    // Cle d'espace : alphanumerique, ou espace personnel "~" + identifiant de compte (ex. ~557058:uuid)
    private static final Pattern SPACE_KEY = Pattern.compile("^~?[A-Za-z0-9][A-Za-z0-9:_-]{0,254}$");

    private final DocumentSourcePort documentSource;

    public SearchDocumentsService(DocumentSourcePort documentSource) {
        this.documentSource = documentSource;
    }

    @Override
    public List<DocumentSummary> search(String spaceKey, String title) {
        if (spaceKey == null || !SPACE_KEY.matcher(spaceKey).matches()) {
            throw new InvalidRequestException("Invalid spaceKey");
        }
        if (title == null || title.isBlank()) {
            throw new InvalidRequestException("title is required");
        }
        return documentSource.searchDocuments(spaceKey, title);
    }
}
