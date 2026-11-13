// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import java.util.Locale;

/** Nom du parametre de limite de tokens de sortie envoye a un fournisseur openai-compatible. */
public enum MaxTokensParam {

    /** {@code max_tokens} : OpenAI (modeles classiques), DeepSeek, Mistral, Groq, Ollama, vLLM, LiteLLM... (defaut). */
    MAX_TOKENS("max_tokens"),
    /** {@code max_completion_tokens} : modeles OpenAI a raisonnement, qui refusent {@code max_tokens}. */
    MAX_COMPLETION_TOKENS("max_completion_tokens"),
    /** Aucune limite envoyee. */
    NONE(null);

    private final String field;

    MaxTokensParam(String field) {
        this.field = field;
    }

    /** Nom du champ JSON, ou null si aucune limite n'est envoyee. */
    public String field() {
        return field;
    }

    public static MaxTokensParam fromId(String id) {
        if (id == null || id.isBlank()) {
            return MAX_TOKENS;
        }
        return switch (id.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "max_tokens" -> MAX_TOKENS;
            case "max_completion_tokens" -> MAX_COMPLETION_TOKENS;
            case "none" -> NONE;
            default -> throw new IllegalArgumentException(
                    "Unknown max-tokens-param '" + id + "', expected max_tokens, max_completion_tokens or none");
        };
    }
}
