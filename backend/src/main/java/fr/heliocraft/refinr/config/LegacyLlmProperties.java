// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Refus au demarrage de l'ancienne configuration LLM (avant la phase 5) : elle serait ignoree en silence. Seuls les
 * fichiers de configuration (application*.properties, .env importe, proprietes de test) sont examines, pas les
 * variables d'environnement du systeme (une variable GEMINI_API_KEY peut servir a d'autres outils). Seuls les noms
 * des cles sont affiches, jamais leurs valeurs.
 */
final class LegacyLlmProperties {

    // Ancienne cle -> remplacement (proprietes, puis variables du .env)
    private static final Map<String, String> MIGRATION = new LinkedHashMap<>();

    static {
        MIGRATION.put("ai.provider", "refinr.llm.analysis.provider (ollama -> openai-compatible, gemini -> google)");
        MIGRATION.put("AI_PROVIDER", "LLM_ANALYSIS_PROVIDER (ollama -> openai-compatible, gemini -> google)");
        MIGRATION.put("ollama.base-url", "refinr.llm.analysis.base-url, suffixe /v1 (ex. http://localhost:11434/v1)");
        MIGRATION.put("OLLAMA_BASE_URL", "LLM_ANALYSIS_BASE_URL, suffixe /v1 (ex. http://localhost:11434/v1)");
        MIGRATION.put("ollama.model", "refinr.llm.analysis.model");
        MIGRATION.put("OLLAMA_MODEL", "LLM_ANALYSIS_MODEL");
        MIGRATION.put("gemini.api-key", "refinr.llm.analysis.api-key (ou refinr.llm.verdict.api-key)");
        MIGRATION.put("GEMINI_API_KEY", "LLM_ANALYSIS_API_KEY (ou LLM_VERDICT_API_KEY) avec le fournisseur google");
        MIGRATION.put("gemini.model", "refinr.llm.analysis.model");
        MIGRATION.put("GEMINI_MODEL", "LLM_ANALYSIS_MODEL");
        MIGRATION.put("gemini.base-url", "refinr.llm.analysis.base-url");
        MIGRATION.put("ai.verdict.provider", "refinr.llm.verdict.provider (litellm -> openai-compatible, gemini -> google)");
        MIGRATION.put("AI_VERDICT_PROVIDER", "LLM_VERDICT_PROVIDER (litellm -> openai-compatible, gemini -> google)");
        MIGRATION.put("ai.verdict.base-url", "refinr.llm.verdict.base-url, suffixe /v1 pour LiteLLM");
        MIGRATION.put("AI_VERDICT_BASE_URL", "LLM_VERDICT_BASE_URL, suffixe /v1 pour LiteLLM (ex. http://localhost:4000/v1)");
        MIGRATION.put("ai.verdict.model", "refinr.llm.verdict.model");
        MIGRATION.put("AI_VERDICT_MODEL", "LLM_VERDICT_MODEL");
        MIGRATION.put("ai.verdict.api-key", "refinr.llm.verdict.api-key");
        MIGRATION.put("AI_VERDICT_API_KEY", "LLM_VERDICT_API_KEY");
        MIGRATION.put("ai.verdict.timeout-seconds", "refinr.llm.verdict.timeout (ex. 90s)");
        MIGRATION.put("ai.verdict.retry-delay-ms", "refinr.llm.verdict.retry-base-delay (ex. 2s)");
        MIGRATION.put("groq.api-key", "refinr.llm.*.provider=openai-compatible, base-url https://api.groq.com/openai/v1");
        MIGRATION.put("groq.model", "refinr.llm.*.model");
        MIGRATION.put("anthropic.api-key", "refinr.llm.*.provider=anthropic et refinr.llm.*.api-key");
    }

    private LegacyLlmProperties() {}

    /** @throws IllegalStateException si une ancienne cle est presente, avec la correspondance a appliquer */
    static void rejectIn(ConfigurableEnvironment environment) {
        Set<String> found = new TreeSet<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (isSystemSource(source) || !(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                if (MIGRATION.containsKey(name)) {
                    found.add(name);
                }
            }
        }
        if (!found.isEmpty()) {
            throw new IllegalStateException(message(found));
        }
    }

    static String message(Set<String> found) {
        StringBuilder message = new StringBuilder(
                "Obsolete LLM configuration (replaced in Refinr phase 5). Rename these keys (values are never shown):");
        for (String key : found) {
            message.append("\n  ").append(key).append(" -> ").append(MIGRATION.get(key));
        }
        message.append("\nSee .env.example and application-example.properties.");
        return message.toString();
    }

    private static boolean isSystemSource(PropertySource<?> source) {
        return StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME.equals(source.getName())
                || StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME.equals(source.getName());
    }
}
