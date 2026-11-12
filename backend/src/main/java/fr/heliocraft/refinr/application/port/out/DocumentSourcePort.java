package fr.heliocraft.refinr.application.port.out;

import fr.heliocraft.refinr.domain.model.DocumentSummary;

import java.util.List;

/** Source des specifications a analyser (Confluence aujourd'hui). */
public interface DocumentSourcePort {

    /** Contenu brut du document (HTML de stockage pour Confluence). */
    String getDocumentContent(String documentId);

    /** Recherche de documents par espace et titre ; liste vide si aucun resultat. */
    List<DocumentSummary> searchDocuments(String spaceKey, String title);
}
