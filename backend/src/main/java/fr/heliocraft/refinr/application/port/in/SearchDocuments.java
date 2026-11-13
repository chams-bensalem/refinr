// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.DocumentSummary;

import java.util.List;

/** Recherche de documents source par titre, dans un espace optionnel. */
public interface SearchDocuments {

    /**
     * {@code space} null ou vide : tous les espaces lisibles par le compte de la source.
     * @throws InvalidRequestException si l'espace n'a pas le format de la source active, ou si le titre est vide
     */
    List<DocumentSummary> search(String space, String title);
}
