// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import java.time.Duration;

/** Parametres de test : delais de nouvel essai nuls, delai court, cle factice, aucun parametre optionnel par defaut. */
final class LlmTestSettings {

    private LlmTestSettings() {}

    static LlmSettings of(LlmProvider provider, String baseUrl) {
        return of(provider, baseUrl, Duration.ofSeconds(2), 1000);
    }

    static LlmSettings of(LlmProvider provider, String baseUrl, Duration timeout, int maxOutputTokens) {
        return of(provider, baseUrl, timeout, maxOutputTokens, "fake-api-key");
    }

    static LlmSettings of(LlmProvider provider, String baseUrl, Duration timeout, int maxOutputTokens, String apiKey) {
        return of(provider, baseUrl, timeout, maxOutputTokens, apiKey, null, ExtraBody.NONE);
    }

    /** Parametres optionnels : temperature (null = non envoyee) et extra-body. */
    static LlmSettings of(LlmProvider provider, String baseUrl, Double temperature, ExtraBody extraBody) {
        return of(provider, baseUrl, Duration.ofSeconds(2), 1000, "fake-api-key", temperature, extraBody);
    }

    static LlmSettings of(LlmProvider provider, String baseUrl, Duration timeout, int maxOutputTokens, String apiKey,
                          Double temperature, ExtraBody extraBody) {
        String model = switch (provider) {
            case ANTHROPIC -> "claude-opus-5-5";
            case GOOGLE -> "gemini-3.6-flash";
            case OPENAI_COMPATIBLE -> "qwen3:8b";
        };
        return new LlmSettings("analysis", provider, baseUrl, model, apiKey, timeout, maxOutputTokens, temperature,
                JsonMode.JSON_SCHEMA, MaxTokensParam.MAX_TOKENS, extraBody, new RetryPolicy(2, Duration.ZERO, 1, 0, Duration.ofMillis(50)));
    }
}
