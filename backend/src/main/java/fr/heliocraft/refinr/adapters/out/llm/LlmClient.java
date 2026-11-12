package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.application.port.out.LlmException.Reason;
import fr.heliocraft.refinr.domain.model.ComponentHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Client HTTP d'un fournisseur de modele : envoie une requete de sortie structuree et renvoie le texte produit.
 * <p>
 * Nouveaux essais de transport : HTTP 429, 5xx (dont 529 d'Anthropic) et connexion refusee, au plus
 * {@code maxRetries} fois, en respectant {@code Retry-After} (plafonne). Jamais apres un delai depasse, ni sur une
 * autre erreur 4xx. Une sortie coupee par la limite de tokens leve {@link Reason#TRUNCATED}, sans nouvel essai.
 * Les messages d'erreur ne contiennent ni URL, ni cle, ni corps de reponse.
 */
public abstract class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    protected final LlmSettings settings;
    protected final ObjectMapper mapper = JsonMapper.builder().build();
    private final WebClient webClient;

    protected LlmClient(LlmSettings settings) {
        this.settings = settings;
        this.webClient = WebClient.builder()
                .baseUrl(settings.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
    }

    public LlmSettings settings() {
        return settings;
    }

    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);

    /** Chemin (relatif a l'URL de base) de l'appel de generation. */
    protected abstract String generatePath();

    /** En-tetes d'authentification (et de version d'API) du fournisseur. */
    protected abstract Map<String, String> headers();

    /** Corps de la requete de generation, dans le format du fournisseur. */
    protected abstract Map<String, Object> body(LlmRequest request);

    /**
     * Texte produit, extrait de la reponse du fournisseur.
     *
     * @throws LlmException {@link Reason#TRUNCATED} si la limite de tokens a coupe la sortie
     */
    protected abstract String text(JsonNode response);

    /** Chemin d'une lecture gratuite (sans generation) qui verifie la cle et le modele. */
    protected abstract String healthPath();

    /** Probleme detecte dans la reponse de {@link #healthPath()} (ex. modele absent de la liste), ou null. */
    protected String modelProblem(JsonNode response) {
        return null;
    }

    /**
     * Etat du fournisseur sous le nom {@code name} : lecture du modele (ou de la liste des modeles), delai court, sans
     * nouvel essai et sans aucun token consomme. Details : fournisseur et modele, jamais la cle ni l'URL.
     */
    public ComponentHealth health(String name) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("provider", settings.provider().id());
        details.put("model", settings.model());
        try {
            String problem = modelProblem(mapper.readTree(get(healthPath(), HEALTH_TIMEOUT)));
            return problem == null ? ComponentHealth.up(name, details) : ComponentHealth.down(name, problem, details);
        } catch (RuntimeException e) {
            String cause = healthCause(e);
            log.warn("LLM {} health check failed ({}): {}", name, settings.describe(), cause);
            return ComponentHealth.down(name, cause, details);
        }
    }

    private static String healthCause(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof WebClientResponseException response) {
                return "HTTP " + response.getStatusCode().value();
            }
            if (t instanceof TimeoutException) {
                return "timeout";
            }
            if (t instanceof WebClientRequestException) {
                return "unreachable";
            }
        }
        return error.getClass().getSimpleName();
    }

    /** Envoie la requete et renvoie le texte du modele. */
    public String complete(LlmRequest request) {
        String payload = mapper.writeValueAsString(body(request));
        int attempts = settings.maxRetries() + 1;
        for (int attempt = 1; ; attempt++) {
            try {
                log.info("LLM {} call: {}, attempt {}/{}", settings.role(), settings.describe(), attempt, attempts);
                String response = post(generatePath(), payload);
                return text(mapper.readTree(response));
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                if (!isRetryable(status) || attempt >= attempts) {
                    // Jamais le corps : certains fournisseurs y recopient une partie de la cle refusee
                    log.warn("LLM {} call failed: HTTP {} ({}), error type: {}", settings.role(), status,
                            settings.describe(), errorType(e.getResponseBodyAsString()));
                    throw new LlmException(Reason.UNAVAILABLE, failureMessage(status), e);
                }
                Duration delay = retryDelay(attempt, e.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
                log.warn("LLM {} busy (HTTP {}), new attempt in {} ms", settings.role(), status, delay.toMillis());
                pause(delay);
            } catch (WebClientRequestException e) {
                if (isTimeout(e) || attempt >= attempts) {
                    throw unreachableOrTimeout(e);
                }
                Duration delay = retryDelay(attempt, null);
                log.warn("LLM {} unreachable ({}), new attempt in {} ms", settings.role(), settings.describe(), delay.toMillis());
                pause(delay);
            } catch (LlmException e) {
                throw e;
            } catch (RuntimeException e) {
                if (isTimeout(e)) {
                    throw unreachableOrTimeout(e);
                }
                log.warn("LLM {} call failed ({}): {}", settings.role(), settings.describe(), e.toString());
                throw new LlmException(Reason.UNAVAILABLE,
                        "Unexpected response from the " + settings.provider().id() + " LLM provider", e);
            }
        }
    }

    private String post(String path, String payload) {
        return webClient.post()
                .uri(path)
                .headers(h -> headers().forEach(h::set))
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(settings.timeout())
                .block();
    }

    /** GET authentifie (health check), avec un delai court. */
    protected String get(String path, Duration timeout) {
        return webClient.get()
                .uri(path)
                .headers(h -> headers().forEach(h::set))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(timeout)
                .block();
    }

    /** Sortie coupee par la limite de tokens : erreur explicite, sans relance (le meme appel serait coupe pareil). */
    protected LlmException truncated() {
        log.warn("LLM {} output truncated by the token limit ({}, max-output-tokens={})",
                settings.role(), settings.describe(), settings.maxOutputTokens());
        return new LlmException(Reason.TRUNCATED, "The " + settings.role()
                + " model output was truncated by the token limit: increase max-output-tokens");
    }

    protected LlmException emptyOutput(String detail) {
        log.warn("LLM {} returned no usable output ({}): {}", settings.role(), settings.describe(), detail);
        return new LlmException(Reason.INVALID_OUTPUT, "The " + settings.role() + " model returned no usable output");
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private String failureMessage(int status) {
        String provider = "The " + settings.provider().id() + " LLM provider";
        return switch (status) {
            case 401, 403 -> provider + " rejected the credentials (HTTP " + status + ")";
            case 404 -> provider + " does not know the model or endpoint (HTTP 404)";
            case 429 -> provider + " is rate limiting the requests (HTTP 429)";
            default -> status >= 500
                    ? provider + " is unavailable (HTTP " + status + ")"
                    : provider + " rejected the request (HTTP " + status + ")";
        };
    }

    private LlmException unreachableOrTimeout(RuntimeException e) {
        if (isTimeout(e)) {
            log.warn("LLM {} call timed out after {} ({})", settings.role(), settings.timeout(), settings.describe());
            return new LlmException(Reason.UNAVAILABLE, "The " + settings.provider().id()
                    + " LLM provider did not answer within " + settings.timeout().toSeconds() + " s", e);
        }
        log.warn("LLM {} unreachable ({}): {}", settings.role(), settings.describe(), e.getMessage());
        return new LlmException(Reason.UNAVAILABLE, "The " + settings.provider().id() + " LLM provider is unreachable", e);
    }

    static boolean isTimeout(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** Delai avant le prochain essai : Retry-After (secondes) s'il est fourni, sinon backoff exponentiel ; plafonne. */
    Duration retryDelay(int attempt, String retryAfter) {
        Duration delay = settings.retryBaseDelay().multipliedBy(1L << Math.min(attempt - 1, 16));
        if (retryAfter != null) {
            try {
                delay = Duration.ofSeconds(Long.parseLong(retryAfter.trim()));
            } catch (NumberFormatException ignored) {
                // Date HTTP : backoff exponentiel
            }
        }
        return delay.compareTo(settings.retryMaxDelay()) > 0 ? settings.retryMaxDelay() : delay;
    }

    private static void pause(Duration delay) {
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(Reason.UNAVAILABLE, "LLM call interrupted", e);
        }
    }

    /**
     * Type d'erreur annonce par le fournisseur, lu dans un champ connu ({@code error.type}, {@code error.status},
     * {@code error.code}) et limite a un identifiant court ; jamais le message, qui peut contenir des donnees.
     */
    String errorType(String body) {
        try {
            JsonNode error = mapper.readTree(body == null || body.isBlank() ? "{}" : body).path("error");
            for (String field : new String[] {"type", "status", "code"}) {
                String value = error.path(field).asString("");
                if (value.matches("[A-Za-z0-9_.-]{1,64}")) {
                    return value;
                }
            }
        } catch (RuntimeException ignored) {
            // Corps non JSON : rien a en extraire
        }
        return "unknown";
    }
}
