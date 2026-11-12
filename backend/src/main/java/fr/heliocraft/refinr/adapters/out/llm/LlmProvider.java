package fr.heliocraft.refinr.adapters.out.llm;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Familles d'API de modeles de langage prises en charge. */
public enum LlmProvider {

    /** API Messages d'Anthropic (Claude). */
    ANTHROPIC("anthropic", "https://api.anthropic.com"),
    /** API Gemini de Google AI Studio. */
    GOOGLE("google", "https://generativelanguage.googleapis.com"),
    /** Toute API compatible OpenAI Chat Completions : OpenAI, DeepSeek, Mistral, Groq, OpenRouter, Ollama, LiteLLM, vLLM... */
    OPENAI_COMPATIBLE("openai-compatible", null);

    private final String id;
    private final String defaultBaseUrl;

    LlmProvider(String id, String defaultBaseUrl) {
        this.id = id;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public String id() {
        return id;
    }

    /** URL de base par defaut, ou null si elle doit etre configuree (openai-compatible). */
    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public static LlmProvider fromId(String id) {
        String wanted = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (LlmProvider provider : values()) {
            if (provider.id.equals(wanted)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown LLM provider '" + id + "', expected one of: " + ids());
    }

    public static String ids() {
        return Arrays.stream(values()).map(LlmProvider::id).collect(Collectors.joining(", "));
    }
}
