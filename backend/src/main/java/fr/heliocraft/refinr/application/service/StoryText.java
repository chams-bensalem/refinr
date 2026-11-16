// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.domain.model.Story;

/** Texte d'une story pour la detection de sa langue : titre, description et criteres d'acceptation. */
final class StoryText {

    private StoryText() {}

    static String of(Story story) {
        StringBuilder text = new StringBuilder();
        if (story.getTitle() != null) text.append(story.getTitle()).append('\n');
        if (story.getDescription() != null) text.append(story.getDescription()).append('\n');
        if (story.getAcceptanceCriteria() != null) story.getAcceptanceCriteria().forEach(c -> text.append(c).append('\n'));
        return text.toString();
    }
}
