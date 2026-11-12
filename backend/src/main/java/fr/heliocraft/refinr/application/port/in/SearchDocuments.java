package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.DocumentSummary;

import java.util.List;

/** Recherche de documents source par espace et titre. */
public interface SearchDocuments {

    /** @throws InvalidRequestException si la cle d'espace est invalide ou le titre vide */
    List<DocumentSummary> search(String spaceKey, String title);
}
