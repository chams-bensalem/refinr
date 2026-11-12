package fr.heliocraft.refinr.application.port.out;

import java.util.Map;

/** Source des specifications a analyser (Confluence aujourd'hui). */
public interface DocumentSourcePort {

    /** Contenu brut du document (HTML de stockage pour Confluence). */
    String getDocumentContent(String documentId);

    /**
     * Recherche de documents par espace et titre. Reponse brute de la source, renvoyee telle quelle au client :
     * dette assumee en phase 3, typage prevu en phase 4 (cf. DECISIONS.md).
     */
    Map<String, Object> searchDocuments(String spaceKey, String title);
}
