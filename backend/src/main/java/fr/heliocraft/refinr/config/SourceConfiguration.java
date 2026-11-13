// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Source documentaire de l'instance ({@code source.provider}, defaut {@code confluence}) : un seul adaptateur
 * {@code DocumentSourcePort} est instancie (condition portee par l'adaptateur). Ce controle arrete le demarrage avec un
 * message explicite, avant la creation de tout bean, si la valeur est inconnue, si la source n'est pas encore
 * disponible ou si une propriete de la source choisie est absente. Une propriete presente mais vide
 * (ex. {@code CONFLUENCE_TOKEN=}) reste toleree : le health la signale.
 */
@Configuration
public class SourceConfiguration {

    static final String PROPERTY = "source.provider";
    static final String DEFAULT_PROVIDER = "confluence";

    /** Proprietes obligatoires par source disponible. */
    static final Map<String, List<String>> REQUIRED_PROPERTIES = Map.of(
            "confluence", List.of("confluence.base-url", "confluence.username", "confluence.token"));

    /** Sources annoncees pour la v1.2, refusees tant que leur adaptateur n'existe pas. */
    static final Set<String> PLANNED = Set.of("bookstack", "xwiki");

    /** Statique : execute avant l'instanciation des beans, donc avant les erreurs de placeholder des adaptateurs. */
    @Bean
    static BeanFactoryPostProcessor sourceSettingsCheck(Environment environment) {
        return beanFactory -> check(environment);
    }

    static void check(Environment environment) {
        String provider = environment.getProperty(PROPERTY, DEFAULT_PROVIDER).toLowerCase(Locale.ROOT);
        if (PLANNED.contains(provider)) {
            throw new IllegalStateException(PROPERTY + " '" + provider + "' is not available yet: expected "
                    + DEFAULT_PROVIDER);
        }
        List<String> required = REQUIRED_PROPERTIES.get(provider);
        if (required == null) {
            throw new IllegalStateException("Unknown " + PROPERTY + " '" + environment.getProperty(PROPERTY)
                    + "': expected one of " + DEFAULT_PROVIDER);
        }
        List<String> missing = required.stream().filter(name -> !environment.containsProperty(name)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing configuration for " + PROPERTY + "=" + provider + ": "
                    + String.join(", ", missing));
        }
    }
}
