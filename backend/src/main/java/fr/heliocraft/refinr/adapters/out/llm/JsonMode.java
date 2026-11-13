// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import java.util.Locale;

/** Mode JSON natif demande a un fournisseur openai-compatible (les autres fournisseurs utilisent toujours le schema). */
public enum JsonMode {

    /** {@code response_format: {type: json_schema}} : sortie contrainte par le schema (OpenAI, Ollama, vLLM, LiteLLM...). */
    JSON_SCHEMA,
    /** {@code response_format: {type: json_object}} : JSON valide sans schema (ex. DeepSeek). */
    JSON_OBJECT,
    /** Aucun {@code response_format} : le prompt seul demande du JSON. */
    NONE;

    public static JsonMode fromId(String id) {
        if (id == null || id.isBlank()) {
            return JSON_SCHEMA;
        }
        return switch (id.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "json_schema" -> JSON_SCHEMA;
            case "json_object" -> JSON_OBJECT;
            case "none" -> NONE;
            default -> throw new IllegalArgumentException("Unknown json-mode '" + id + "', expected json_schema, json_object or none");
        };
    }
}
