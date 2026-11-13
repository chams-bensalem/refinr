// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.openproject;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Erreur de l'API v3 d'OpenProject, lue sur {@code errorIdentifier} (le {@code message} est localise dans la langue du
 * compte : il n'est que cite, jamais interprete). {@code MultipleErrors} est aplati : codes et attributs de chaque
 * sous-erreur.
 *
 * @param status     statut HTTP
 * @param codes      fin de chaque {@code errorIdentifier} (ex. {@code PropertyConstraintViolation}), sous-erreurs comprises
 * @param attributes attributs en cause ({@code _embedded.details.attribute}, ex. {@code customField1}, {@code type})
 * @param messages   messages d'OpenProject, cites tels quels
 */
record OpenProjectError(int status, List<String> codes, List<String> attributes, List<String> messages) {

    private static final String PREFIX = "urn:openproject-org:api:v3:errors:";
    private static final int MAX_DETAILS = 500;

    static OpenProjectError of(WebClientResponseException e) {
        List<String> codes = new ArrayList<>();
        List<String> attributes = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            if (body != null) {
                collect(body, codes, attributes, messages);
                if (body.get("_embedded") instanceof Map<?, ?> embedded
                        && embedded.get("errors") instanceof List<?> errors) {
                    for (Object error : errors) {
                        if (error instanceof Map<?, ?> map) {
                            collect(map, codes, attributes, messages);
                        }
                    }
                }
            }
        } catch (RuntimeException unreadable) {
            // Corps illisible (proxy, page HTML) : le statut suffit
        }
        return new OpenProjectError(e.getStatusCode().value(), codes, attributes, messages);
    }

    private static void collect(Map<?, ?> error, List<String> codes, List<String> attributes, List<String> messages) {
        if (error.get("errorIdentifier") instanceof String identifier) {
            codes.add(identifier.startsWith(PREFIX) ? identifier.substring(PREFIX.length()) : identifier);
        }
        if (error.get("message") instanceof String message && !message.isBlank()) {
            messages.add(message);
        }
        if (error.get("_embedded") instanceof Map<?, ?> embedded
                && embedded.get("details") instanceof Map<?, ?> details
                && details.get("attribute") instanceof String attribute) {
            attributes.add(attribute);
        }
    }

    boolean has(String code) {
        return codes.contains(code);
    }

    /** Violation de contrainte sur le type ou sur un champ personnalise : les ids resolus sont peut-etre perimes. */
    boolean invalidatesProjectSetup() {
        return status == 422 && has("PropertyConstraintViolation")
                && attributes.stream().anyMatch(a -> a.equals("type") || a.startsWith("customField"));
    }

    /** Resume sur : statut, codes, attributs et messages d'OpenProject (tronques), jamais d'URL ni de cle. */
    String summary() {
        StringBuilder text = new StringBuilder("HTTP ").append(status);
        if (!codes.isEmpty()) {
            text.append(' ').append(String.join(", ", codes));
        }
        if (!attributes.isEmpty()) {
            text.append(" on ").append(String.join(", ", attributes));
        }
        if (!messages.isEmpty()) {
            text.append(": ").append(String.join("; ", messages));
        }
        return text.length() > MAX_DETAILS ? text.substring(0, MAX_DETAILS) + "..." : text.toString();
    }
}
