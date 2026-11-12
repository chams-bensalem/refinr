package fr.heliocraft.refinr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient confluenceClient(
            @Value("${confluence.base-url}") String baseUrl,
            @Value("${confluence.username}") String username,
            @Value("${confluence.token}") String token) {

        String auth = username + ":" + token;
        String encodedAuth = Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public WebClient jiraClient(
            @Value("${jira.base-url}") String baseUrl,
            @Value("${jira.username}") String username,
            @Value("${jira.token}") String token) {

        String auth = username + ":" + token;
        String encodedAuth = Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public WebClient geminiClient(
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl) {

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }
}
