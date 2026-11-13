package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.adapters.in.rest.AllowedHostFilter;
import fr.heliocraft.refinr.adapters.in.rest.ApiKeyFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

/**
 * Securite HTTP minimale, sans Spring Security :
 * <ul>
 *   <li>en-tete {@code Host} limite a {@code refinr.security.allowed-hosts} (anti DNS rebinding, toujours actif) ;</li>
 *   <li>CORS limite aux origines de {@code refinr.cors.allowed-origins} (liste separee par des virgules, defaut
 *       {@code http://localhost:3000} ; vide = aucune origine tierce ; {@code *} refuse au demarrage) ;</li>
 *   <li>cle d'API optionnelle {@code refinr.security.api-key} (vide = desactivee, cas du poste local).</li>
 * </ul>
 * Ordre des filtres : correlationId ({@link LoggingFilter}), puis hote autorise, puis CORS (une reponse 401 garde ses en-tetes CORS et
 * reste lisible par le navigateur), puis cle d'API.
 */
@Configuration
public class HttpSecurityConfiguration {

    static final int HOST_ORDER = Ordered.HIGHEST_PRECEDENCE + 5;
    static final int CORS_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    static final int API_KEY_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    private static final Logger log = LoggerFactory.getLogger(HttpSecurityConfiguration.class);

    @Bean
    FilterRegistrationBean<AllowedHostFilter> allowedHostFilter(
            @Value("${refinr.security.allowed-hosts:localhost,127.0.0.1,[::1]}") String allowedHosts, ObjectMapper mapper) {
        List<String> hosts = parseList(allowedHosts);
        if (hosts.stream().anyMatch(host -> host.contains("*"))) {
            throw new IllegalStateException("refinr.security.allowed-hosts must list explicit host names "
                    + "(e.g. localhost,127.0.0.1,[::1],refinr.example.com), '*' is not allowed");
        }
        if (hosts.isEmpty()) {
            throw new IllegalStateException("refinr.security.allowed-hosts is empty: every request would be rejected "
                    + "(default: localhost,127.0.0.1,[::1])");
        }
        AllowedHostFilter filter = new AllowedHostFilter(hosts, mapper);
        log.info("Allowed Host headers: {}", filter.allowedHosts());
        FilterRegistrationBean<AllowedHostFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(HOST_ORDER);
        return registration;
    }

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilter(@Value("${refinr.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        List<String> origins = parseOrigins(allowedOrigins);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!origins.isEmpty()) {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(origins);
            cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
            cors.setAllowedHeaders(List.of("Content-Type", ApiKeyFilter.HEADER));
            cors.setAllowCredentials(false);
            cors.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", cors);
        }
        log.info("CORS allowed origins: {}", origins.isEmpty() ? "none (same origin only)" : origins);
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(CORS_ORDER);
        return registration;
    }

    @Bean
    FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(@Value("${refinr.security.api-key:}") String apiKey,
                                                      ObjectMapper mapper) {
        ApiKeyFilter filter = new ApiKeyFilter(apiKey, mapper);
        FilterRegistrationBean<ApiKeyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(API_KEY_ORDER);
        log.info("API key protection: {}",
                filter.isEnabled() ? "enabled (header " + ApiKeyFilter.HEADER + ")" : "disabled");
        return registration;
    }

    static List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::strip).filter(item -> !item.isEmpty()).toList();
    }

    static List<String> parseOrigins(String value) {
        List<String> origins = parseList(value).stream()
                .map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
                .toList();
        if (origins.contains("*")) {
            throw new IllegalStateException("refinr.cors.allowed-origins must list explicit origins "
                    + "(e.g. http://localhost:3000), '*' is not allowed");
        }
        return origins;
    }
}
