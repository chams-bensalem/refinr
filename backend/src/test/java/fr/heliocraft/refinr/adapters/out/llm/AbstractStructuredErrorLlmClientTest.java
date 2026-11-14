// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.LlmException;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contrat des fournisseurs qui designent le parametre refuse d'un HTTP 400 par un champ structure de leur reponse
 * ({@code error.param}, {@code fieldViolations}) : le message nomme alors ce parametre, s'il a ete envoye par Refinr.
 * Les fournisseurs sans champ structure (Anthropic) n'heritent que du contrat commun, ou le cas « 400 sans champ
 * structure » les couvre.
 */
abstract class AbstractStructuredErrorLlmClientTest extends AbstractLlmClientTest {

    /** Reponse 400 qui designe {@code field} (chemin pointe de Refinr) par le champ structure du fournisseur. */
    abstract MockResponse badRequestNaming(String field, String message);

    @Test
    @DisplayName("HTTP 400 whose structured field names a sent optional parameter: message names the model and our key")
    void badRequestNamesTheRejectedParameter(CapturedOutput output) {
        String key = extraBodyExample().keySet().iterator().next();
        String marker = marker();
        server.enqueue(badRequestNaming(key, "unsupported " + marker));
        LlmClient client = client(0.7, extraBodyExample());

        LlmException error = assertThrows(LlmException.class, () -> client.complete(request()));

        assertEquals(LlmException.Reason.UNAVAILABLE, error.reason());
        assertEquals("The LLM provider " + client.settings().describe() + " rejected the optional parameter " + key
                + " (HTTP 400): remove it from the configuration or check the provider documentation", error.getMessage());
        assertNoLeak(marker, output, error);
        assertEquals(1, server.getRequestCount(), "a 400 is never retried");
    }

    @Test
    @DisplayName("HTTP 400 naming a field we did not send: the sent optional parameters are listed, the field is not echoed")
    void badRequestOnForeignFieldListsSentParameters(CapturedOutput output) {
        String marker = marker();
        server.enqueue(badRequestNaming("contents." + marker, "bad " + marker));
        LlmClient client = client(0.7, extraBodyExample());

        LlmException error = assertThrows(LlmException.class, () -> client.complete(request()));

        assertEquals("The LLM provider " + client.settings().describe() + " rejected the request (HTTP 400); "
                + "sent optional parameters: " + sentOptionalParameters(), error.getMessage());
        assertNoLeak(marker, output, error);
    }
}
