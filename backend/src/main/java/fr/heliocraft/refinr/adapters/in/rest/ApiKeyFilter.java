package fr.heliocraft.refinr.adapters.in.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Cle d'API optionnelle (vide = filtre inactif) : si elle est configuree, toute requete doit porter l'en-tete
 * {@value #HEADER} avec cette valeur, sinon {@code 401}. Exemptes : le health check et sa sonde {@code /live}
 * (aucun secret, utilises par les sondes de conteneur) et les requetes de pre-verification CORS (le navigateur n'y joint jamais d'en-tete
 * personnalise). La cle n'est jamais journalisee ni renvoyee.
 * <p>
 * Fail-closed : seule une liste blanche de chemins exacts est exemptee, comparee au chemin tel que Spring le route
 * (decode, sans contenu {@code ;}). Un test sur l'URI brute laisserait passer {@code /%61pi/...} ou {@code /api;x/...},
 * que Spring achemine pourtant vers les controleurs {@code /api/**}.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    static final String HEALTH_PATH = "/api/v1/health";
    private static final Set<String> EXEMPT_PATHS = Set.of(HEALTH_PATH, HEALTH_PATH + "/", HEALTH_PATH + "/live");
    /** Meme normalisation que le routage de Spring MVC : decodage URL et suppression du contenu ';'. */
    private static final UrlPathHelper PATHS = new UrlPathHelper();

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    /** null = protection desactivee. */
    private final byte[] expectedKey;
    private final ObjectMapper mapper;

    public ApiKeyFilter(String apiKey, ObjectMapper mapper) {
        this.expectedKey = apiKey == null || apiKey.isBlank() ? null : apiKey.strip().getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper;
    }

    public boolean isEnabled() {
        return expectedKey != null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!isEnabled() || CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        String raw = request.getRequestURI();
        // Chemins ambigus (segments vides ou relatifs, '\'), jamais exemptes
        if (raw.contains("//") || raw.contains("..") || raw.contains("\\") || raw.toLowerCase().contains("%2e")
                || raw.toLowerCase().contains("%2f") || raw.toLowerCase().contains("%5c")) {
            return false;
        }
        return EXEMPT_PATHS.contains(PATHS.getPathWithinApplication(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        // Comparaison a temps constant : la duree ne revele pas la longueur du prefixe correct
        if (provided != null && MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Rejected {} {}: {} header {}", request.getMethod(), request.getRequestURI(), HEADER,
                provided == null ? "missing" : "invalid");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), ErrorResponses.error("Missing or invalid API key"));
    }
}
