package fr.heliocraft.refinr.adapters.in.rest;

import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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

    /** Cause courte et sure d'un echec d'appel sortant : statut HTTP, service injoignable ou type d'erreur. */
    static String cause(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof WebClientResponseException response) {
                return "HTTP " + response.getStatusCode().value();
            }
            if (t instanceof WebClientRequestException) {
                return "unreachable";
            }
        }
        return error.getClass().getSimpleName();
    }
}
