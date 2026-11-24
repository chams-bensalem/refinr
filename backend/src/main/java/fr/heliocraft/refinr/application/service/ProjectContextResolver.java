// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.ContentTooLargeException;
import fr.heliocraft.refinr.application.port.in.ContextRequest;
import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.domain.model.ProjectContext;
import fr.heliocraft.refinr.domain.service.DocumentText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Contexte projet d'une requete (X1). Priorite : champ de la requete ({@code context} ou {@code contextDocumentId},
 * exclusifs), puis {@code refinr.context.projects.<projectKey>} (cle comparee sans tenir compte de la casse), puis
 * {@code refinr.context.document-id}, puis aucun contexte. Une page est lue dans la source documentaire active, comme un
 * document. Au-dela de {@code maxCharacters} caracteres Unicode : 413, jamais de troncature. Le texte n'est jamais
 * journalise : seulement sa source et sa taille.
 */
public class ProjectContextResolver {

    private static final Logger log = LoggerFactory.getLogger(ProjectContextResolver.class);

    public static final int DEFAULT_MAX_CHARACTERS = 20_000;
    static final String BOTH = "context and contextDocumentId cannot be combined";
    static final String INVALID_ID = "Invalid contextDocumentId";
    static final String EMPTY = "The project context is empty or contains no text";

    /** Aucune configuration : seul le contexte de la requete s'applique (tests, outils). */
    public static ProjectContextResolver requestOnly(DocumentSourcePort documentSource) {
        return new ProjectContextResolver(documentSource, null, Map.of(), DEFAULT_MAX_CHARACTERS);
    }

    private final DocumentSourcePort documentSource;
    private final String defaultDocumentId;
    private final Map<String, String> projectDocuments = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final int maxCharacters;

    /**
     * @param defaultDocumentId page du contexte par defaut, ou null / vide
     * @param projectDocuments  page du contexte par projet (cle : cle ou identifiant du projet du gestionnaire actif)
     * @throws IllegalStateException configuration invalide (refus au demarrage)
     */
    public ProjectContextResolver(DocumentSourcePort documentSource, String defaultDocumentId,
                                  Map<String, String> projectDocuments, int maxCharacters) {
        if (maxCharacters <= 0) {
            throw new IllegalStateException("refinr.context.max-characters must be positive, got " + maxCharacters);
        }
        this.documentSource = documentSource;
        this.maxCharacters = maxCharacters;
        this.defaultDocumentId = defaultDocumentId == null || defaultDocumentId.isBlank() ? null : defaultDocumentId.strip();
        if (this.defaultDocumentId != null && !documentSource.acceptsDocumentId(this.defaultDocumentId)) {
            throw new IllegalStateException("refinr.context.document-id is not a document id of the active source");
        }
        projectDocuments.forEach((project, id) -> {
            if (id == null || id.isBlank() || !documentSource.acceptsDocumentId(id.strip())) {
                // Cle du projet seulement : la valeur peut etre n'importe quoi
                throw new IllegalStateException("refinr.context.projects." + project
                        + " is not a document id of the active source");
            }
            this.projectDocuments.put(project.strip(), id.strip());
        });
    }

    /** Vrai si un contexte peut s'appliquer sans champ dans la requete (propriete de projet ou par defaut). */
    public boolean configured() {
        return defaultDocumentId != null || !projectDocuments.isEmpty();
    }

    /**
     * Controles locaux, avant tout appel (source, modele, gestionnaire) : 400 si les deux champs sont donnes ou si
     * l'identifiant n'a pas le format de la source active ; 413 si le texte depasse la limite.
     */
    public void check(ContextRequest request) {
        if (request.text() != null && request.documentId() != null) {
            throw new InvalidRequestException(BOTH);
        }
        if (request.documentId() != null && !documentSource.acceptsDocumentId(request.documentId())) {
            throw new InvalidRequestException(INVALID_ID);
        }
        if (request.text() != null) {
            requireSize(request.text());
        }
    }

    /**
     * Contexte a envoyer au modele pour un travail sur le projet {@code projectKey} (null : aucun projet nomme, la
     * propriete de projet ne s'applique pas).
     */
    public ProjectContext resolve(ContextRequest request, String projectKey) {
        check(request);
        ProjectContext context;
        if (request.text() != null) {
            context = new ProjectContext(request.text().strip(), ProjectContext.Source.REQUEST);
        } else if (request.documentId() != null) {
            context = fetch(request.documentId(), ProjectContext.Source.REQUEST);
        } else if (projectKey != null && !projectKey.isBlank() && projectDocuments.containsKey(projectKey.strip())) {
            context = fetch(projectDocuments.get(projectKey.strip()), ProjectContext.Source.PROJECT);
        } else if (defaultDocumentId != null) {
            context = fetch(defaultDocumentId, ProjectContext.Source.DEFAULT);
        } else {
            context = ProjectContext.NONE;
        }
        if (context.isPresent()) {
            log.info("Project context: source {}, {} characters", context.source().name().toLowerCase(Locale.ROOT),
                    context.characters());
        }
        return context;
    }

    private ProjectContext fetch(String documentId, ProjectContext.Source source) {
        String content = SourceFailures.translate(() -> documentSource.getDocumentContent(documentId));
        if (!DocumentText.hasUsefulText(content)) {
            throw new InvalidRequestException(EMPTY);
        }
        requireSize(content);
        return new ProjectContext(content.strip(), source, documentId);
    }

    private void requireSize(String text) {
        if (text.strip().codePointCount(0, text.strip().length()) > maxCharacters) {
            throw new ContentTooLargeException("The project context exceeds the maximum size of " + maxCharacters
                    + " characters");
        }
    }
}
