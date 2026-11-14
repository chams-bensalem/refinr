// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.adapters.out.llm.CachingReadinessAssessor;
import fr.heliocraft.refinr.adapters.out.llm.ExtraBody;
import fr.heliocraft.refinr.adapters.out.llm.JsonMode;
import fr.heliocraft.refinr.adapters.out.llm.LlmBacklogGenerator;
import fr.heliocraft.refinr.adapters.out.llm.LlmClient;
import fr.heliocraft.refinr.adapters.out.llm.LlmClients;
import fr.heliocraft.refinr.adapters.out.llm.LlmHealthCheck;
import fr.heliocraft.refinr.adapters.out.llm.LlmProvider;
import fr.heliocraft.refinr.adapters.out.llm.LlmReadinessAssessor;
import fr.heliocraft.refinr.adapters.out.llm.LlmSettings;
import fr.heliocraft.refinr.adapters.out.llm.MaxTokensParam;
import fr.heliocraft.refinr.adapters.out.llm.RetryPolicy;
import fr.heliocraft.refinr.adapters.out.llm.StructuredOutput;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cablage des fournisseurs de modele. Analyse : {@code refinr.llm.analysis.*}, fournisseur et modele obligatoires.
 * Verdict : {@code refinr.llm.verdict.*}, optionnel ; sans fournisseur de verdict, le verdict vient de l'analyse et
 * create-jira / reassess-story utilisent le fournisseur d'analyse.
 * <p>
 * Aucune hypothese sur le modele : ni fournisseur, ni modele, ni temperature par defaut ; un parametre optionnel n'est
 * envoye que s'il est configure.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LlmConfiguration.class);

    // Defauts fonctionnels (memes valeurs dans application-example.properties et le README) ; aucun ne depend du modele
    static final Duration DEFAULT_ANALYSIS_TIMEOUT = Duration.ofSeconds(300);
    static final Duration DEFAULT_VERDICT_TIMEOUT = Duration.ofSeconds(90);
    static final int DEFAULT_ANALYSIS_MAX_OUTPUT_TOKENS = 32768;
    static final int DEFAULT_VERDICT_MAX_OUTPUT_TOKENS = 4096;
    static final int DEFAULT_MAX_RETRIES = 2;
    static final Duration DEFAULT_RETRY_BASE_DELAY = Duration.ofSeconds(2);
    static final double DEFAULT_RETRY_MULTIPLIER = 4;
    static final double DEFAULT_RETRY_JITTER = 0.2;
    static final Duration DEFAULT_RETRY_MAX_DELAY = Duration.ofSeconds(30);
    static final int DEFAULT_MAX_REPAIR_ATTEMPTS = 2;

    private final LlmProperties properties;
    private final LlmClient analysisClient;
    private final LlmClient verdictClient;

    public LlmConfiguration(LlmProperties properties, ConfigurableEnvironment environment) {
        LegacyLlmProperties.rejectIn(environment);
        this.properties = properties;
        this.analysisClient = createClient("analysis", analysisSettings(properties.analysis()));
        this.verdictClient = properties.verdict().isConfigured()
                ? createClient("verdict", verdictSettings(properties.verdict()))
                : analysisClient;
        log.info("LLM analysis: {}", analysisClient.settings().describe());
        log.info("LLM readiness verdict: {}", properties.verdict().isConfigured()
                ? verdictClient.settings().describe() : "same provider as the analysis");
    }

    @Bean
    public StructuredOutput structuredOutput() {
        Integer repairs = properties.maxRepairAttempts();
        return new StructuredOutput(repairs != null ? repairs : DEFAULT_MAX_REPAIR_ATTEMPTS);
    }

    @Bean
    public BacklogGeneratorPort backlogGenerator(StructuredOutput structuredOutput) {
        return new LlmBacklogGenerator(analysisClient, structuredOutput);
    }

    /**
     * Verdict par lots de {@code refinr.llm.verdict.batch-size} stories, derriere un cache des verdicts reussis
     * ({@code refinr.llm.verdict.cache.*}) : a la creation, seules les stories modifiees depuis l'apercu sont rejugees.
     */
    @Bean
    public ReadinessAssessorPort readinessAssessor(
            StructuredOutput structuredOutput,
            @Value("${refinr.llm.verdict.batch-size:10}") int batchSize,
            @Value("${refinr.llm.verdict.cache.ttl:24h}") Duration cacheTtl,
            @Value("${refinr.llm.verdict.cache.max-entries:1000}") int cacheMaxEntries) {
        LlmReadinessAssessor assessor = new LlmReadinessAssessor(verdictClient, structuredOutput, batchSize);
        if (cacheTtl.isZero() || cacheMaxEntries <= 0) {
            return assessor;
        }
        return new CachingReadinessAssessor(assessor, cacheTtl, cacheMaxEntries);
    }

    @Bean
    @Order(3)
    public LlmHealthCheck llmHealthCheck() {
        return new LlmHealthCheck(analysisClient, verdictClient == analysisClient ? null : verdictClient);
    }

    static LlmSettings analysisSettings(LlmProperties.Role role) {
        return settings("analysis", role, DEFAULT_ANALYSIS_TIMEOUT, DEFAULT_ANALYSIS_MAX_OUTPUT_TOKENS);
    }

    static LlmSettings verdictSettings(LlmProperties.Role role) {
        return settings("verdict", role, DEFAULT_VERDICT_TIMEOUT, DEFAULT_VERDICT_MAX_OUTPUT_TOKENS);
    }

    /** Client du role ; un extra-body qui viserait un champ gere par Refinr est refuse ici, au demarrage. */
    static LlmClient createClient(String name, LlmSettings settings) {
        try {
            return LlmClients.create(settings);
        } catch (IllegalArgumentException e) {
            throw invalid(name, e);
        }
    }

    private static IllegalStateException invalid(String name, IllegalArgumentException e) {
        return new IllegalStateException("Invalid refinr.llm." + name + " configuration: " + e.getMessage(), e);
    }

    /**
     * Variables a renseigner pour un role actif : fournisseur, modele, et base-url quand le fournisseur n'a pas d'URL
     * officielle (openai-compatible). Message sans aucune valeur configuree.
     */
    private static void requireProviderAndModel(String name, LlmProperties.Role role) {
        String variable = "LLM_" + name.toUpperCase(Locale.ROOT) + "_";
        List<String> missing = new ArrayList<>();
        LlmProvider provider = null;
        if (!role.isConfigured()) {
            missing.add(variable + "PROVIDER");
        } else {
            try {
                provider = LlmProvider.fromId(role.provider());
            } catch (IllegalArgumentException e) {
                throw invalid(name, e);
            }
        }
        if (blankToNull(role.model()) == null) {
            missing.add(variable + "MODEL");
        }
        if (provider != null && provider.defaultBaseUrl() == null && blankToNull(role.baseUrl()) == null) {
            missing.add(variable + "BASE_URL");
        }
        if (!missing.isEmpty()) {
            String baseUrlHint = provider == null ? " (and " + variable + "BASE_URL for " + LlmProvider.OPENAI_COMPATIBLE.id() + ")" : "";
            throw new IllegalStateException("Invalid refinr.llm." + name + " configuration: set " + String.join(", ", missing)
                    + baseUrlHint + " in .env, see the examples in .env.example");
        }
    }

    private static LlmSettings settings(String name, LlmProperties.Role role, Duration timeout, int maxOutputTokens) {
        requireProviderAndModel(name, role);
        try {
            return new LlmSettings(
                    name,
                    LlmProvider.fromId(role.provider()),
                    blankToNull(role.baseUrl()),
                    blankToNull(role.model()),
                    role.apiKey(),
                    role.timeout() != null ? role.timeout() : timeout,
                    role.maxOutputTokens() != null ? role.maxOutputTokens() : maxOutputTokens,
                    temperature(role.temperature()),
                    JsonMode.fromId(role.jsonMode()),
                    MaxTokensParam.fromId(role.maxTokensParam()),
                    ExtraBody.of(extraBody(role.extraBody())),
                    new RetryPolicy(
                            role.maxRetries() != null ? role.maxRetries() : DEFAULT_MAX_RETRIES,
                            role.retryBaseDelay() != null ? role.retryBaseDelay() : DEFAULT_RETRY_BASE_DELAY,
                            role.retryMultiplier() != null ? role.retryMultiplier() : DEFAULT_RETRY_MULTIPLIER,
                            role.retryJitter() != null ? role.retryJitter() : DEFAULT_RETRY_JITTER,
                            role.retryMaxDelay() != null ? role.retryMaxDelay() : DEFAULT_RETRY_MAX_DELAY));
        } catch (IllegalArgumentException e) {
            throw invalid(name, e);
        }
    }

    /** Absente ou vide : non envoyee (defaut du fournisseur) ; sinon un nombre >= 0, envoye tel quel. */
    static Double temperature(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(configured.trim());
            if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("temperature must be a number >= 0, or empty to not send it");
        }
    }

    /**
     * Valeurs ecrites en JSON (true, 1, {"a":1}) ; sinon chaines. Valeur vide = propriete non envoyee. Les cles sont
     * des chemins pointes, fusionnes dans le corps par {@link ExtraBody}.
     */
    static Map<String, Object> extraBody(Map<String, String> raw) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (raw == null) {
            return body;
        }
        JsonMapper mapper = JsonMapper.builder().build();
        raw.forEach((key, value) -> {
            if (value == null || value.isBlank()) {
                return;
            }
            try {
                body.put(key, mapper.readValue(value, Object.class));
            } catch (JacksonException e) {
                body.put(key, value);
            }
        });
        return body;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
