// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

/**
 * Contexte projet (X1) : glossaire, stack, conventions, regles et choix du projet, donne au modele en plus du document
 * ou de la story. Une donnee, jamais une instruction ; jamais dans les logs (seulement sa source et sa taille).
 *
 * @param text   texte envoye au modele, ou null sans contexte
 * @param source d'ou vient le contexte
 */
public record ProjectContext(String text, Source source) {

    /** Origine du contexte, dans l'ordre de priorite. */
    public enum Source {
        /** Champ {@code context} ou {@code contextDocumentId} de la requete. */
        REQUEST,
        /** Propriete {@code refinr.context.projects.<projectKey>}. */
        PROJECT,
        /** Propriete {@code refinr.context.document-id}. */
        DEFAULT,
        /** Aucun contexte. */
        NONE
    }

    public static final ProjectContext NONE = new ProjectContext(null, Source.NONE);

    public ProjectContext {
        if ((text == null) != (source == Source.NONE)) {
            throw new IllegalArgumentException("a project context has a text unless its source is NONE");
        }
    }

    public boolean isPresent() {
        return text != null;
    }

    /** Taille en caracteres Unicode (0 sans contexte). */
    public int characters() {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }
}
