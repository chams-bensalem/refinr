// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

/** Apercu du backlog d'un document, sans rien creer. */
public interface PreviewBacklog {

    /** Document lu dans la source active par son identifiant. */
    List<Epic> preview(String documentId);

    /**
     * Document fourni en texte (Markdown ou texte brut), sans appel a la source active.
     *
     * @param title titre optionnel (null ou vide : aucun), donne au modele en tete du texte
     */
    List<Epic> previewText(String title, String content);
}
