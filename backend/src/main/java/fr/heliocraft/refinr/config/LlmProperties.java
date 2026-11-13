// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Fournisseurs de modele ({@code refinr.llm.*}) : analyse et verdict configures independamment. Les champs absents
 * prennent les valeurs de {@link LlmConfiguration} (defauts par role).
 *
 * @param verdict           vide ({@code provider} non renseigne) = le verdict vient de l'analyse et create-jira /
 *                          reassess-story utilisent le fournisseur d'analyse
 * @param maxRepairAttempts relances envoyees au modele quand sa sortie ne respecte pas le schema
 */
@ConfigurationProperties(prefix = "refinr.llm")
public record LlmProperties(Role analysis, Role verdict, Integer maxRepairAttempts) {

    public LlmProperties {
        analysis = analysis == null ? Role.EMPTY : analysis;
        verdict = verdict == null ? Role.EMPTY : verdict;
    }

    /**
     * Parametres d'un role ; {@code extraBody} : proprietes JSON ajoutees au corps (openai-compatible).
     * {@code temperature} : absente = defaut du role, vide = non envoyee (defaut du fournisseur), sinon un nombre.
     * {@code maxTokensParam} (openai-compatible) : max_tokens (defaut) | max_completion_tokens | none.
     */
    public record Role(
            String provider,
            String baseUrl,
            String model,
            String apiKey,
            Duration timeout,
            Integer maxOutputTokens,
            String temperature,
            String jsonMode,
            String maxTokensParam,
            Map<String, String> extraBody,
            Integer maxRetries,
            Duration retryBaseDelay,
            Double retryMultiplier,
            Double retryJitter,
            Duration retryMaxDelay) {

        static final Role EMPTY = new Role(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        boolean isConfigured() {
            return provider != null && !provider.isBlank();
        }

        @Override
        public String toString() {
            // Jamais la cle ni l'URL dans un log
            return "Role[provider=" + provider + ", model=" + model + "]";
        }
    }
}
