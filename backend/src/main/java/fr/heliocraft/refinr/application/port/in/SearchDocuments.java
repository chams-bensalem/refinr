package fr.heliocraft.refinr.application.port.in;

import java.util.Map;

/**
 * Recherche de documents source par espace et titre. Reponse brute de la source : dette assumee en phase 3,
 * typage prevu en phase 4 (cf. DECISIONS.md).
 */
public interface SearchDocuments {

    Map<String, Object> search(String spaceKey, String title);
}
