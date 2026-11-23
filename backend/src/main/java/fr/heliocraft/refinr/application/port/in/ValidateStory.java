// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Readiness;

/** Marque prete une story en attente de validation, par une decision humaine, sans appel au modele. */
public interface ValidateStory {

    /**
     * @return le verdict ecrit : ai-ready, source {@code human}
     * @throws StoryNotAwaitingValidationException si la story n'est pas en attente de validation
     */
    default Readiness validate(String storyKey) {
        return validate(storyKey, null);
    }

    /**
     * @param language langue de la raison ecrite (null : propriete, puis detection sur la story)
     * @return le verdict ecrit : ai-ready, source {@code human}
     * @throws StoryNotAwaitingValidationException si la story n'est pas en attente de validation
     */
    Readiness validate(String storyKey, fr.heliocraft.refinr.domain.model.Language language);
}
