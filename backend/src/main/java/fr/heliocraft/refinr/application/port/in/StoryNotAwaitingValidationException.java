// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.service.ReadinessPolicy;

/** Seules les stories en attente de validation sont reevaluees : une story ai-ready ne regresse pas ici. */
public class StoryNotAwaitingValidationException extends RuntimeException {

    public StoryNotAwaitingValidationException(String storyKey) {
        super("Issue " + storyKey + " is not awaiting validation (label " + ReadinessPolicy.LABEL_VALIDATION + " absent)");
    }
}
