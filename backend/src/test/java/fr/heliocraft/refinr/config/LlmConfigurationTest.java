package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.adapters.out.llm.JsonMode;
import fr.heliocraft.refinr.adapters.out.llm.LlmProvider;
import fr.heliocraft.refinr.adapters.out.llm.LlmSettings;
import fr.heliocraft.refinr.adapters.out.llm.MaxTokensParam;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmConfigurationTest {

    private static LlmProperties.Role role(String provider, String baseUrl, String model, String apiKey) {
        return new LlmProperties.Role(provider, baseUrl, model, apiKey, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("Without configuration, the analysis uses the local Ollama through its OpenAI-compatible API")
    void defaultAnalysisIsLocalOllama() {
        LlmSettings settings = LlmConfiguration.analysisSettings(LlmProperties.Role.EMPTY);

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, settings.provider());
        assertEquals("http://localhost:11434/v1", settings.baseUrl());
        assertEquals("qwen3:8b", settings.model());
        assertEquals(Duration.ofSeconds(300), settings.timeout());
        assertEquals(32768, settings.maxOutputTokens());
        assertEquals(JsonMode.JSON_SCHEMA, settings.jsonMode());
    }

    @Test
    @DisplayName("google and anthropic get their official base URL; verdict defaults: 90 s, 4096 tokens")
    void providerDefaults() {
        LlmSettings google = LlmConfiguration.analysisSettings(role("google", "", "gemini-2.5-flash", "k"));
        LlmSettings anthropic = LlmConfiguration.verdictSettings(role("anthropic", null, "claude-opus-5-5", "k"));

        assertEquals("https://generativelanguage.googleapis.com", google.baseUrl());
        assertEquals("https://api.anthropic.com", anthropic.baseUrl());
        assertEquals(Duration.ofSeconds(90), anthropic.timeout());
        assertEquals(4096, anthropic.maxOutputTokens());
    }

    @Test
    @DisplayName("An explicit provider never inherits the Ollama defaults: google keeps its official URL, openai-compatible needs a base-url")
    void explicitProviderGetsNoOllamaDefault() {
        // Seul le fournisseur est renseigne (cas d'un .env qui passe de Ollama a google)
        LlmSettings google = LlmConfiguration.analysisSettings(role("google", null, "gemini-2.5-flash", "k"));
        assertEquals("https://generativelanguage.googleapis.com", google.baseUrl());

        IllegalStateException googleWithoutModel = assertThrows(IllegalStateException.class,
                () -> LlmConfiguration.analysisSettings(role("google", null, null, "k")));
        assertTrue(googleWithoutModel.getMessage().contains("model is required"), googleWithoutModel.getMessage());

        IllegalStateException openAiWithoutUrl = assertThrows(IllegalStateException.class,
                () -> LlmConfiguration.analysisSettings(role("openai-compatible", "", "qwen3:8b", "")));
        assertEquals("Invalid refinr.llm.analysis configuration: base-url is required for the openai-compatible provider (analysis)",
                openAiWithoutUrl.getMessage());

        // base-url explicite vers Ollama : accepte, mais le modele reste a choisir
        assertEquals("http://localhost:11434/v1",
                LlmConfiguration.analysisSettings(role("openai-compatible", "http://localhost:11434/v1", "qwen3:8b", "")).baseUrl());
        assertThrows(IllegalStateException.class,
                () -> LlmConfiguration.analysisSettings(role("openai-compatible", "http://localhost:11434/v1", null, "")));
    }

    @Test
    @DisplayName("Retry defaults: 2 retries, 2 s then 8 s (x4) with 20 % jitter, capped at 30 s")
    void retryDefaults() {
        var retry = LlmConfiguration.analysisSettings(LlmProperties.Role.EMPTY).retry();

        assertEquals(2, retry.maxRetries());
        assertEquals(Duration.ofSeconds(2), retry.baseDelay());
        assertEquals(4, retry.multiplier());
        assertEquals(0.2, retry.jitter());
        assertEquals(Duration.ofSeconds(30), retry.maxDelay());
    }

    @Test
    @DisplayName("Retry settings are configurable per role and validated at startup")
    void retryIsConfigurable() {
        LlmProperties.Role custom = new LlmProperties.Role("google", null, "gemini-2.5-flash", "k", null, null, null, null,
                null, null, 4, Duration.ofSeconds(1), 3.0, 0.0, Duration.ofSeconds(10));
        var retry = LlmConfiguration.verdictSettings(custom).retry();
        assertEquals(4, retry.maxRetries());
        assertEquals(Duration.ofSeconds(1), retry.baseDelay());
        assertEquals(3, retry.multiplier());
        assertEquals(0, retry.jitter());
        assertEquals(Duration.ofSeconds(10), retry.maxDelay());

        LlmProperties.Role badJitter = new LlmProperties.Role("google", null, "gemini-2.5-flash", "k", null, null, null, null,
                null, null, null, null, null, 1.5, null);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> LlmConfiguration.verdictSettings(badJitter));
        assertTrue(error.getMessage().contains("retry-jitter must be between 0 and 1"), error.getMessage());
    }

    private static LlmProperties.Role openAi(String temperature, String maxTokensParam) {
        return new LlmProperties.Role("openai-compatible", "https://api.openai.com/v1", "your-openai-model", "k", null, null,
                temperature, null, maxTokensParam, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("temperature: absent = role default (0.2 / 0.1, unchanged), empty = not sent, otherwise the number")
    void temperatureIsConfigurable() {
        assertEquals(0.2, LlmConfiguration.analysisSettings(openAi(null, null)).temperature());
        assertEquals(0.1, LlmConfiguration.verdictSettings(openAi(null, null)).temperature());
        assertNull(LlmConfiguration.analysisSettings(openAi("", null)).temperature());
        assertNull(LlmConfiguration.verdictSettings(openAi("  ", null)).temperature());
        assertEquals(1.0, LlmConfiguration.analysisSettings(openAi("1", null)).temperature());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> LlmConfiguration.analysisSettings(openAi("warm", null)));
        assertEquals("Invalid refinr.llm.analysis configuration: temperature must be a number >= 0, or empty to not send it",
                error.getMessage());
        assertThrows(IllegalStateException.class, () -> LlmConfiguration.analysisSettings(openAi("-0.5", null)));
    }

    @Test
    @DisplayName("max-tokens-param: max_tokens by default, max_completion_tokens or none on request, anything else refused")
    void maxTokensParamIsConfigurable() {
        assertEquals(MaxTokensParam.MAX_TOKENS, LlmConfiguration.analysisSettings(openAi(null, null)).maxTokensParam());
        assertEquals(MaxTokensParam.MAX_TOKENS, LlmConfiguration.analysisSettings(openAi(null, "")).maxTokensParam());
        assertEquals(MaxTokensParam.MAX_COMPLETION_TOKENS,
                LlmConfiguration.analysisSettings(openAi(null, "max_completion_tokens")).maxTokensParam());
        assertEquals(MaxTokensParam.NONE, LlmConfiguration.verdictSettings(openAi(null, "none")).maxTokensParam());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> LlmConfiguration.analysisSettings(openAi(null, "max_output_tokens")));
        assertTrue(error.getMessage().contains("expected max_tokens, max_completion_tokens or none"), error.getMessage());
    }

    @Test
    @DisplayName("Spring binding: an empty LLM_ANALYSIS_TEMPERATURE reaches the code as empty (not sent), an absent one as null (default)")
    void emptyTemperatureBindsAsEmpty() {
        LlmProperties empty = new Binder(new MapConfigurationPropertySource(Map.of(
                "refinr.llm.analysis.temperature", "", "refinr.llm.analysis.max-tokens-param", "max_completion_tokens")))
                .bind("refinr.llm", LlmProperties.class).get();
        assertEquals("", empty.analysis().temperature());
        assertEquals("max_completion_tokens", empty.analysis().maxTokensParam());
        assertNull(LlmConfiguration.analysisSettings(empty.analysis()).temperature());

        LlmProperties absent = new Binder(new MapConfigurationPropertySource(Map.of("refinr.llm.analysis.model", "m")))
                .bind("refinr.llm", LlmProperties.class).get();
        assertNull(absent.analysis().temperature());
        assertEquals(0.2, LlmConfiguration.analysisSettings(absent.analysis()).temperature());
    }

    @Test
    @DisplayName("Invalid configurations fail at startup with a clear message")
    void invalidConfigurationsFailFast() {
        IllegalStateException unknown = assertThrows(IllegalStateException.class,
                () -> LlmConfiguration.analysisSettings(role("gemini", null, "m", "k")));
        assertTrue(unknown.getMessage().contains("anthropic, google, openai-compatible"), unknown.getMessage());

        IllegalStateException noKey = assertThrows(IllegalStateException.class,
                () -> LlmConfiguration.verdictSettings(role("google", null, "gemini-2.5-flash", "")));
        assertTrue(noKey.getMessage().contains("api-key is required"), noKey.getMessage());

        IllegalStateException noUrl = assertThrows(IllegalStateException.class,
                () -> LlmConfiguration.verdictSettings(role("openai-compatible", "", "m", "")));
        assertTrue(noUrl.getMessage().contains("base-url is required"), noUrl.getMessage());

        IllegalStateException noModel = assertThrows(IllegalStateException.class,
                () -> LlmConfiguration.analysisSettings(role("anthropic", null, "", "k")));
        assertTrue(noModel.getMessage().contains("model is required"), noModel.getMessage());
    }

    @Test
    @DisplayName("anthropic and google need a positive max-output-tokens")
    void positiveOutputLimit() {
        LlmProperties.Role zero = new LlmProperties.Role("anthropic", null, "claude-opus-5-5", "k", null, 0, null, null,
                null, null, null, null, null, null, null);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> LlmConfiguration.verdictSettings(zero));
        assertTrue(error.getMessage().contains("max-output-tokens must be > 0"), error.getMessage());
    }

    @Test
    @DisplayName("extra-body values written as JSON keep their type")
    void extraBodyTypes() {
        assertEquals(Map.of("think", false, "top_k", 20, "name", "x"),
                LlmConfiguration.extraBody(Map.of("think", "false", "top_k", "20", "name", "x")));
        // ${LLM_ANALYSIS_REASONING_EFFORT:} non renseignee : rien n'est envoye
        assertEquals(Map.of(), LlmConfiguration.extraBody(Map.of("reasoning_effort", "")));
    }

    @Test
    @DisplayName("Legacy keys in a configuration file are refused with the mapping, never with their values")
    void legacyKeysAreRefused() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("Config resource 'file [../.env]'",
                Map.of("AI_PROVIDER", "ollama", "GEMINI_API_KEY", "secret-value-123", "LLM_ANALYSIS_MODEL", "m")));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> LegacyLlmProperties.rejectIn(environment));

        assertTrue(error.getMessage().contains("AI_PROVIDER -> LLM_ANALYSIS_PROVIDER"), error.getMessage());
        assertTrue(error.getMessage().contains("GEMINI_API_KEY -> LLM_ANALYSIS_API_KEY"), error.getMessage());
        assertFalse(error.getMessage().contains("secret-value-123"), error.getMessage());
        assertFalse(error.getMessage().contains("LLM_ANALYSIS_MODEL"), "new keys are accepted");
    }

    @Test
    @DisplayName("System environment variables are not checked (GEMINI_API_KEY may serve other tools)")
    void systemEnvironmentIsIgnored() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("GEMINI_API_KEY", "x", "AI_PROVIDER", "gemini")));

        assertDoesNotThrow(() -> LegacyLlmProperties.rejectIn(environment));
    }
}
