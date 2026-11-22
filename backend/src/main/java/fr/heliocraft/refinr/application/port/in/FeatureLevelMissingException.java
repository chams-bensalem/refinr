// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

/**
 * Creation refusee avant tout appel au gestionnaire de tickets : le backlog a des Features explicites, le gestionnaire
 * n'a pas de niveau Feature dans ce projet, et {@code refinr.features.without-level=refuse}. Repondu 422.
 */
public class FeatureLevelMissingException extends RuntimeException {

    public static final String MESSAGE = "The tracker has no Feature level in this project: enable it, or set "
            + "refinr.features.without-level=label to keep the features as labels on their stories";

    public FeatureLevelMissingException() {
        super(MESSAGE);
    }
}
