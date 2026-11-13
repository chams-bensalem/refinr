// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Corps d'erreur renvoyes au client : un message generique choisi par l'application, jamais le message brut d'une
 * exception (il peut contenir des URL internes, des corps de reponse ou des parametres sensibles). Le detail reste
 * dans les logs, retrouvable grace au correlationId renvoye.
 */
final class ErrorResponses {

    private ErrorResponses() {}

    static Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", message);
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            body.put("correlationId", correlationId);
        }
        return body;
    }
}
