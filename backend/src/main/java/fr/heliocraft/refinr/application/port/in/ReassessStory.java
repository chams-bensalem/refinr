// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Readiness;

/**
 * Reevalue une story en attente de validation avec une precision de l'utilisateur : la story existante est mise
 * a jour, aucun ticket n'est recree.
 */
public interface ReassessStory {

    /** @throws StoryNotAwaitingValidationException si la story n'est pas en attente de validation */
    default Readiness reassess(String storyKey, String precision) {
        return reassess(storyKey, precision, null);
    }

    /**
     * @param language langue de la raison demandee, ou null : propriete, puis detection sur le titre et la description
     * @throws StoryNotAwaitingValidationException si la story n'est pas en attente de validation
     */
    Readiness reassess(String storyKey, String precision, Language language);
}
