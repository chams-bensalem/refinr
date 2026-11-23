// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

/** Une autre demande cree deja les sous-taches de cette story (verrou par story, une seule instance). */
public class StoryTasksInProgressException extends RuntimeException {

    public StoryTasksInProgressException(String storyKey) {
        super("The sub-tasks of issue " + storyKey + " are being created by another request");
    }
}
