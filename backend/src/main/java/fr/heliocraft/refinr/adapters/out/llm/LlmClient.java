// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.application.port.out.LlmException.Reason;
import fr.heliocraft.refinr.domain.model.ComponentHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client HTTP d'un fournisseur de modele : envoie une requete de sortie structuree et renvoie le texte produit.
 * <p>
 * Nouveaux essais de transport : HTTP 429, 5xx (dont 529 d'Anthropic) et connexion refusee, au plus
 * {@code maxRetries} fois. Jamais apres un delai depasse, ni sur une autre erreur 4xx. Delai annonce par le fournisseur
 * ({@link #requestedDelay}) : respecte s'il ne depasse pas {@code retry-max-delay}, sinon echec immediat (aucun nouvel
 * essai) ; il est porte par l'exception. Echec final : {@link Reason#RATE_LIMITED} (429), {@link Reason#OVERLOADED}
 * (503, 529), sinon {@link Reason#UNAVAILABLE}. Une sortie coupee par la limite de tokens leve
 * {@link Reason#TRUNCATED}, sans nouvel essai.
 * Les messages d'erreur et les logs ne contiennent ni URL, ni cle, ni corps de reponse, ni texte du modele : seulement
 * le fournisseur, le modele, le statut HTTP et une categorie d'erreur. Les {@link LlmException} levees ici n'ont
 * jamais de cause (les exceptions de WebClient et de Jackson portent l'URL, les en-tetes ou des extraits du corps).
 * <p>
 * Parametres optionnels (temperature, {@link ExtraBody}) : envoyes seulement s'ils sont configures ; aucune valeur ni
 * logique fondee sur le nom du modele. Sur un HTTP 400, le message nomme le parametre optionnel refuse quand le
 * fournisseur le designe par un champ structure ({@link #rejectedFields}), sinon liste les parametres optionnels
 * envoyes ; il ne cite que nos propres noms de cles, jamais le texte du fournisseur.
 */
public abstract class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    protected final LlmSettings settings;
    protected final ObjectMapper mapper = JsonMapper.builder().build();
    private final WebClient webClient;

    protected LlmClient(LlmSettings settings) {
        // Refuse au demarrage un extra-body qui ecraserait un champ gere par Refinr
        settings.extraBody().checkReserved(reservedPaths());
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

    /** Corps de la requete de generation, dans le format du fournisseur (sans l'extra-body, ajoute ensuite). */
    protected abstract Map<String, Object> body(LlmRequest request);

    /** Chemins pointes des champs geres par Refinr dans le corps : un extra-body ne peut pas les viser. */
    protected abstract Set<String> reservedPaths();

    /** Chemin pointe de la temperature dans le corps du fournisseur. */
    protected abstract String temperaturePath();

    /**
     * Champs du corps que le fournisseur designe, dans un champ structure de sa reponse d'erreur, comme la cause d'un
     * HTTP 400 (ex. {@code error.param}) ; vide s'il n'en designe aucun. Ces valeurs servent seulement a reconnaitre un
     * parametre envoye par Refinr : elles ne sont jamais recopiees dans un message ni dans un log.
     */
    protected List<String> rejectedFields(JsonNode errorBody) {
        return List.of();
    }

    /**
     * Delai avant un nouvel essai annonce dans le corps d'erreur du fournisseur, quand il n'envoie pas d'en-tete
     * {@code Retry-After} (ex. {@code google.rpc.RetryInfo}) ; null s'il n'en annonce aucun ou s'il est illisible.
     */
    protected Duration retryDelayInBody(JsonNode errorBody) {
        return null;
    }

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
        String payload = mapper.writeValueAsString(settings.extraBody().applyTo(body(request)));
        int attempts = settings.retry().maxRetries() + 1;
        for (int attempt = 1; ; attempt++) {
            try {
                log.info("LLM {} call: {}, attempt {}/{}", settings.role(), settings.describe(), attempt, attempts);
                String response = post(generatePath(), payload);
                return text(mapper.readTree(response));
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                // Jamais le corps : certains fournisseurs y recopient une partie de la cle refusee
                String errorBody = e.getResponseBodyAsString();
                Duration requested = isRetryable(status)
                        ? requestedDelay(e.getHeaders().getFirst(HttpHeaders.RETRY_AFTER), errorBody) : null;
                boolean tooLong = requested != null && requested.compareTo(settings.retry().maxDelay()) > 0;
                if (!isRetryable(status) || attempt >= attempts || tooLong) {
                    if (tooLong && attempt < attempts) {
                        log.warn("LLM {} busy (HTTP {}): requested delay of {} s exceeds retry-max-delay, no new attempt",
                                settings.role(), status, requested.toSeconds());
                    }
                    log.warn("LLM {} call failed: HTTP {} ({}), error type: {}", settings.role(), status,
                            settings.describe(), errorType(errorBody));
                    throw new LlmException(reasonFor(status),
                            status == 400 ? badRequestMessage(errorBody) : failureMessage(status), requested);
                }
                Duration delay = requested != null ? requested : retryDelay(attempt, null);
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
                // Jamais le message : Jackson y cite des extraits du corps de la reponse
                log.warn("LLM {} call failed ({}): {}", settings.role(), settings.describe(), failureCategory(e));
                throw new LlmException(Reason.UNAVAILABLE,
                        "Unexpected response from the " + settings.provider().id() + " LLM provider");
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

    private static Reason reasonFor(int status) {
        return switch (status) {
            case 429 -> Reason.RATE_LIMITED;
            case 503, 529 -> Reason.OVERLOADED;
            default -> Reason.UNAVAILABLE;
        };
    }

    static final Duration MIN_REQUESTED_DELAY = Duration.ofSeconds(1);
    static final Duration MAX_REQUESTED_DELAY = Duration.ofDays(1);

    /**
     * Delai annonce par le fournisseur : en-tete {@code Retry-After} (secondes ou date HTTP), sinon champ du corps
     * d'erreur ({@link #retryDelayInBody}) ; arrondi a la seconde superieure et borne a 1 s – 86 400 s ; null si absent
     * ou illisible. Le corps n'est jamais recopie.
     */
    Duration requestedDelay(String retryAfterHeader, String errorBody) {
        Duration requested = RetryPolicy.parseRetryAfter(retryAfterHeader, Instant.now());
        if (requested == null && errorBody != null && !errorBody.isBlank()) {
            try {
                requested = retryDelayInBody(mapper.readTree(errorBody));
            } catch (RuntimeException ignored) {
                requested = null;  // Corps non JSON : aucun delai structure
            }
        }
        if (requested == null || requested.isNegative()) {
            return null;
        }
        long seconds = requested.toSeconds() + (requested.toNanosPart() > 0 ? 1 : 0);
        return Duration.ofSeconds(Math.clamp(seconds, MIN_REQUESTED_DELAY.toSeconds(), MAX_REQUESTED_DELAY.toSeconds()));
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

    /** Parametres optionnels envoyes : nom montre a l'utilisateur -> chemin dans le corps, dans l'ordre d'envoi. */
    Map<String, String> optionalParameters() {
        Map<String, String> sent = new LinkedHashMap<>();
        if (settings.temperature() != null) {
            sent.put("temperature", temperaturePath());
        }
        settings.extraBody().paths().forEach(path -> sent.put(path, path));
        return sent;
    }

    /**
     * HTTP 400 : parametre optionnel designe par le fournisseur (champ structure), sinon liste des parametres optionnels
     * envoyes ; sans parametre optionnel, message inchange. Seuls nos noms de cles apparaissent.
     */
    private String badRequestMessage(String errorBody) {
        Map<String, String> sent = optionalParameters();
        if (sent.isEmpty()) {
            return failureMessage(400);
        }
        List<String> fields;
        try {
            fields = rejectedFields(mapper.readTree(errorBody == null || errorBody.isBlank() ? "{}" : errorBody));
        } catch (RuntimeException ignored) {
            fields = List.of();  // Corps non JSON : aucun champ structure
        }
        List<String> rejected = new ArrayList<>();
        for (Map.Entry<String, String> parameter : sent.entrySet()) {
            if (fields.stream().anyMatch(field -> ExtraBody.overlaps(field, parameter.getValue()))) {
                rejected.add(parameter.getKey());
            }
        }
        String provider = "The LLM provider " + settings.describe();
        if (!rejected.isEmpty()) {
            return provider + " rejected the optional parameter " + String.join(", ", rejected)
                    + " (HTTP 400): remove it from the configuration or check the provider documentation";
        }
        return provider + " rejected the request (HTTP 400); sent optional parameters: " + String.join(", ", sent.keySet());
    }

    private LlmException unreachableOrTimeout(RuntimeException e) {
        if (isTimeout(e)) {
            log.warn("LLM {} call timed out after {} ({})", settings.role(), settings.timeout(), settings.describe());
            return new LlmException(Reason.UNAVAILABLE, "The " + settings.provider().id()
                    + " LLM provider did not answer within " + settings.timeout().toSeconds() + " s");
        }
        log.warn("LLM {} unreachable ({}): {}", settings.role(), settings.describe(), failureCategory(e));
        return new LlmException(Reason.UNAVAILABLE, "The " + settings.provider().id() + " LLM provider is unreachable");
    }

    /**
     * Categorie d'un echec, deduite du type des exceptions de la chaine (jamais de leur message, qui peut citer
     * l'hote, l'URL ou le corps de la reponse).
     */
    static String failureCategory(Throwable error) {
        Throwable root = error;
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof JacksonException) {
                return "invalid-json";
            }
            if (t instanceof DataBufferLimitException) {
                return "response-too-large";
            }
            if (t instanceof TimeoutException) {
                return "timeout";
            }
            if (t instanceof ConnectException) {
                return "connection-refused";
            }
            if (t instanceof UnknownHostException) {
                return "unknown-host";
            }
            if (t instanceof SSLException) {
                return "tls";
            }
            root = t;
        }
        return root instanceof IOException ? "io" : root.getClass().getSimpleName();
    }

    static boolean isTimeout(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** Delai avant le prochain essai quand le fournisseur n'en annonce pas : backoff exponentiel avec jitter, plafonne. */
    Duration retryDelay(int attempt, String retryAfter) {
        return settings.retry().delay(attempt, retryAfter, ThreadLocalRandom.current().nextDouble(), Instant.now());
    }

    private static void pause(Duration delay) {
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(Reason.UNAVAILABLE, "LLM call interrupted");
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
