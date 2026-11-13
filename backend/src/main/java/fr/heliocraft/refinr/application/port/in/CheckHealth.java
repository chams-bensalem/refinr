// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.ComponentHealth;

import java.util.List;

/** Etat des dependances externes (source documentaire, gestionnaire de tickets actif, fournisseurs de modele). */
public interface CheckHealth {

    HealthReport check();

    /** @param allUp vrai si tous les composants sont UP */
    record HealthReport(boolean allUp, List<ComponentHealth> components) {

        public HealthReport {
            components = List.copyOf(components);
        }
    }
}
