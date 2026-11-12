package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.adapters.out.llm.JsonMode;
import fr.heliocraft.refinr.adapters.out.llm.LlmProvider;
import fr.heliocraft.refinr.adapters.out.llm.LlmSettings;
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
        return new LlmProperties.Role(provider, baseUrl, model, apiKey, null, null, null, null, null, null, null, null);
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
                null, null, null, null);

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
