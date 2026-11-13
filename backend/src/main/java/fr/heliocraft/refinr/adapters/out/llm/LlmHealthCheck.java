// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.HealthCheckable;
import fr.heliocraft.refinr.domain.model.ComponentHealth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Etat des fournisseurs de modele : "ai" (analyse), et "aiVerdict" si un fournisseur de verdict distinct est configure. */
public class LlmHealthCheck implements HealthCheckable {

    private final Map<String, LlmClient> clients = new LinkedHashMap<>();

    public LlmHealthCheck(LlmClient analysis, LlmClient separateVerdict) {
        clients.put("ai", analysis);
        if (separateVerdict != null) {
            clients.put("aiVerdict", separateVerdict);
        }
    }

    @Override
    public List<ComponentHealth> health() {
        return clients.entrySet().stream().map(entry -> entry.getValue().health(entry.getKey())).toList();
    }
}
