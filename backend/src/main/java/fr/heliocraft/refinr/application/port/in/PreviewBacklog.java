// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

/** Apercu du backlog d'un document, sans rien creer. */
public interface PreviewBacklog {

    /** Document lu dans la source active par son identifiant. */
    default List<Epic> preview(String documentId) {
        return preview(documentId, null);
    }

    /** @param language langue de sortie demandee, ou null : propriete, puis detection */
    List<Epic> preview(String documentId, Language language);

    /**
     * Document fourni en texte (Markdown ou texte brut), sans appel a la source active.
     *
     * @param title titre optionnel (null ou vide : aucun), donne au modele en tete du texte
     */
    default List<Epic> previewText(String title, String content) {
        return previewText(title, content, null);
    }

    /** @param language langue de sortie demandee, ou null : propriete, puis detection */
    List<Epic> previewText(String title, String content, Language language);
}
