// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

/**
 * Cree epics, stories et sous-taches dans le gestionnaire de tickets et affecte a chacun la cle de son ticket.
 * Pas de nouvel essai sur la creation (POST non idempotent : risque de doublon) : un echec est signale dans le
 * resultat et la creation continue pour les autres elements.
 */
public interface CreateBacklog {

    /** @param project projet cible ; null ou vide : projet par defaut */
    BacklogCreationResult create(List<Epic> epics, String project);
}
