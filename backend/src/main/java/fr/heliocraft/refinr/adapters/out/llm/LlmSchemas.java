// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Schemas JSON des sorties attendues ({@code resources/llm/schemas}) : une seule source de verite, envoyee au
 * fournisseur pour son mode JSON natif et utilisee pour valider la reponse.
 */
final class LlmSchemas {

    static final String BACKLOG = "backlog";
    static final String READINESS = "readiness";
    static final String READINESS_BATCH = "readiness-batch";

    private LlmSchemas() {}

    /** Schema complet (avec {@code $schema}), pour la validation locale. */
    static JsonNode load(String name) {
        String path = "llm/schemas/" + name + ".schema.json";
        try (InputStream in = LlmSchemas.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing JSON schema " + path);
            }
            return JsonMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Schema envoye au fournisseur : sans {@code $schema}, que certaines API refusent. */
    static JsonNode forProvider(JsonNode schema) {
        ObjectNode copy = (ObjectNode) schema.deepCopy();
        copy.remove("$schema");
        return copy;
    }
}
