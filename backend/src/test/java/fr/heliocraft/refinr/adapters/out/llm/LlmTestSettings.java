package fr.heliocraft.refinr.adapters.out.llm;

import java.time.Duration;
import java.util.Map;

/** Parametres de test : delais de nouvel essai nuls, delai court, cle factice. */
final class LlmTestSettings {

    private LlmTestSettings() {}

    static LlmSettings of(LlmProvider provider, String baseUrl) {
        return of(provider, baseUrl, Duration.ofSeconds(2), 1000);
    }

    static LlmSettings of(LlmProvider provider, String baseUrl, Duration timeout, int maxOutputTokens) {
        String model = switch (provider) {
            case ANTHROPIC -> "claude-opus-5-5";
            case GOOGLE -> "gemini-2.5-flash";
            case OPENAI_COMPATIBLE -> "qwen3:8b";
        };
        return new LlmSettings("analysis", provider, baseUrl, model, "fake-api-key", timeout, maxOutputTokens, 0.2,
                JsonMode.JSON_SCHEMA, MaxTokensParam.MAX_TOKENS, Map.of(), new RetryPolicy(2, Duration.ZERO, 1, 0, Duration.ofMillis(50)));
    }
}
