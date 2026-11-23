// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

import java.util.List;

/**
 * Question bloquante a poser au Product Owner (tiree de {@code aiReadyReason}) et les stories non pretes qu'elle
 * concerne, dans l'ordre du backlog.
 */
public record PoQuestion(String question, List<Story> stories) {

    public PoQuestion {
        stories = List.copyOf(stories);
    }
}
