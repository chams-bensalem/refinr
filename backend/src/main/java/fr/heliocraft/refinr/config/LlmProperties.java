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

    /** Parametres d'un role ; {@code extraBody} : proprietes JSON ajoutees au corps (openai-compatible). */
    public record Role(
            String provider,
            String baseUrl,
            String model,
            String apiKey,
            Duration timeout,
            Integer maxOutputTokens,
            Double temperature,
            String jsonMode,
            Map<String, String> extraBody,
            Integer maxRetries,
            Duration retryBaseDelay,
            Duration retryMaxDelay) {

        static final Role EMPTY = new Role(null, null, null, null, null, null, null, null, null, null, null, null);

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
