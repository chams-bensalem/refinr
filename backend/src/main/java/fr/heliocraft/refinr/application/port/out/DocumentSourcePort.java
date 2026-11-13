// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.out;

import fr.heliocraft.refinr.domain.model.DocumentSummary;

import java.util.List;

/**
 * Source des specifications a analyser, agnostique de l'outil ; un seul adaptateur actif par instance
 * ({@code source.provider}). Les formats d'identifiant et d'espace sont ceux de l'outil : l'adaptateur les valide.
 * <p>
 * Contrat de {@link #acceptsSpace} et {@link #acceptsDocumentId} (a respecter par chaque adaptateur : Confluence,
 * BookStack, XWiki) : controles de format <strong>purement locaux</strong>, sans aucun appel reseau ni acces a l'outil.
 * Ils ne disent pas si l'espace ou le document existe, ni si le compte peut le lire : ces erreurs viennent de
 * {@link #getDocumentContent} et {@link #searchDocuments}. Ils sont appeles avant tout appel a la source, y compris
 * pour refuser une requete invalide (400), et peuvent l'etre a chaque requete.
 */
public interface DocumentSourcePort {

    /** Nom affichable de l'outil (ex. dans l'interface et les journaux). */
    String displayName();

    /**
     * Vrai si {@code space} a le format d'un espace de l'outil (espace, livre...) ; null ou vide : faux.
     * Controle de format local, sans appel reseau (cf. contrat de l'interface).
     */
    boolean acceptsSpace(String space);

    /**
     * Vrai si {@code documentId} a le format d'un identifiant de document de l'outil ; null ou vide : faux.
     * Controle de format local, sans appel reseau (cf. contrat de l'interface).
     */
    boolean acceptsDocumentId(String documentId);

    /** Adresse web du document {@code documentId} pour un navigateur, ou null si inconnue. */
    String documentUrl(String documentId);

    /** Contenu du document, au format lu par l'adaptateur (HTML pour les sources actuelles). */
    String getDocumentContent(String documentId);

    /**
     * Recherche de documents par titre, restreinte a {@code space} s'il est renseigne (null ou vide : tous les espaces
     * lisibles par le compte) ; liste vide si aucun resultat.
     */
    List<DocumentSummary> searchDocuments(String space, String title);
}
