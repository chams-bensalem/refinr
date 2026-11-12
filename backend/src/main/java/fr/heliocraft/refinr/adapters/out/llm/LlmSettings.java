package fr.heliocraft.refinr.adapters.out.llm;

import java.time.Duration;
import java.util.Map;

/**
 * Parametres d'un fournisseur de modele pour un role (analyse ou verdict).
 *
 * @param role            nom du role, pour les logs et le health check (ex. "analysis", "verdict")
 * @param maxOutputTokens limite de tokens de sortie ; 0 = ne pas l'envoyer (openai-compatible uniquement)
 * @param temperature     null = valeur par defaut du fournisseur (jamais envoyee a Anthropic)
 * @param extraBody       proprietes ajoutees telles quelles au corps de la requete (openai-compatible), ex. {@code think: false}
 * @param maxRetries      nouveaux essais sur 429, 5xx, 529 et connexion refusee (jamais apres un delai depasse)
 */
public record LlmSettings(
        String role,
        LlmProvider provider,
        String baseUrl,
        String model,
        String apiKey,
        Duration timeout,
        int maxOutputTokens,
        Double temperature,
        JsonMode jsonMode,
        Map<String, Object> extraBody,
        int maxRetries,
        Duration retryBaseDelay,
        Duration retryMaxDelay) {

    public LlmSettings {
        if (provider == null) {
            throw new IllegalArgumentException("LLM provider is required (" + role + ")");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = provider.defaultBaseUrl();
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("base-url is required for the " + provider.id() + " provider (" + role + ")");
        }
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model is required (" + role + ")");
        }
        if (provider != LlmProvider.OPENAI_COMPATIBLE && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalArgumentException("api-key is required for the " + provider.id() + " provider (" + role + ")");
        }
        apiKey = apiKey == null ? "" : apiKey;
        jsonMode = jsonMode == null ? JsonMode.JSON_SCHEMA : jsonMode;
        extraBody = extraBody == null ? Map.of() : Map.copyOf(extraBody);
    }

    /** Description sans secret : fournisseur et modele. */
    public String describe() {
        return provider.id() + " (" + model + ")";
    }

    @Override
    public String toString() {
        // Jamais la cle ni l'URL (qui peut contenir des identifiants) dans un log
        return "LlmSettings[role=" + role + ", provider=" + provider.id() + ", model=" + model + "]";
    }
}
