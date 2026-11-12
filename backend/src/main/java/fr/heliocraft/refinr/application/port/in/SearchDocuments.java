package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.DocumentSummary;

import java.util.List;

/** Recherche de documents source par espace et titre. */
public interface SearchDocuments {

    List<DocumentSummary> search(String spaceKey, String title);
}
