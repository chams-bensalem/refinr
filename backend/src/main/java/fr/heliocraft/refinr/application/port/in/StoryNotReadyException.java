// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.service.ReadinessPolicy;

/** Les sous-taches ne sont generees que pour une story prete (label {@code ai-ready}). */
public class StoryNotReadyException extends RuntimeException {

    public StoryNotReadyException(String storyKey) {
        super("Issue " + storyKey + " is not ready (label " + ReadinessPolicy.LABEL_AI_READY + " absent)");
    }
}
