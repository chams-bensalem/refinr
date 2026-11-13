package fr.heliocraft.refinr.adapters.in.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Protection contre le DNS rebinding : seules les requetes dont l'en-tete {@code Host} designe un hote autorise
 * ({@code refinr.security.allowed-hosts}, port ignore) sont traitees, sinon {@code 403}. Toujours actif, y compris
 * sans cle d'API et sur les health checks : une page malveillante qui fait pointer son propre nom de domaine sur
 * {@code 127.0.0.1} envoie ce nom dans {@code Host} et ne peut donc pas atteindre l'API locale.
 * <p>
 * L'en-tete brut est lu (et non un {@code X-Forwarded-Host}, qu'un script peut fixer une fois le rebinding fait).
 */
public class AllowedHostFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AllowedHostFilter.class);

    private final Set<String> allowedHosts;
    private final ObjectMapper mapper;

    public AllowedHostFilter(Collection<String> allowedHosts, ObjectMapper mapper) {
        this.allowedHosts = allowedHosts.stream().map(AllowedHostFilter::hostName).collect(Collectors.toUnmodifiableSet());
        if (this.allowedHosts.isEmpty() || this.allowedHosts.contains("")) {
            throw new IllegalArgumentException("refinr.security.allowed-hosts must list at least one host name");
        }
        this.mapper = mapper;
    }

    public Set<String> allowedHosts() {
        return allowedHosts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String host = request.getHeader("Host");
        if (host == null) {
            // Requete sans en-tete Host (HTTP/1.0, requete simulee) : nom determine par le conteneur de servlets
            host = request.getServerName();
        }
        if (host != null && allowedHosts.contains(hostName(host))) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Rejected {} {}: Host not allowed ({})", request.getMethod(), request.getRequestURI(),
                host == null ? "missing" : host.length() > 100 ? host.substring(0, 100) + "..." : host);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), ErrorResponses.error("Host not allowed"));
    }

    /** {@code Localhost:8080} -> {@code localhost} ; {@code [::1]:8080} -> {@code [::1]} ; port, casse et point final ignores. */
    static String hostName(String hostHeader) {
        String value = hostHeader.strip().toLowerCase(Locale.ROOT);
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            return end < 0 ? value : value.substring(0, end + 1);
        }
        if (value.indexOf(':') != value.lastIndexOf(':')) {
            // IPv6 sans crochets (forme toleree dans la configuration) : ::1 -> [::1]
            return "[" + value + "]";
        }
        int colon = value.indexOf(':');
        String name = colon < 0 ? value : value.substring(0, colon);
        return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
    }
}
