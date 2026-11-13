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

/**
 * Gestionnaire de tickets de l'instance ({@code tracker.provider}, defaut {@code jira}) : un seul adaptateur
 * {@code IssueTrackerPort} est instancie (condition portee par l'adaptateur). Ce controle arrete le demarrage avec un
 * message explicite, avant la creation de tout bean, si la valeur est inconnue ou si une propriete du gestionnaire
 * choisi est absente. Une propriete presente mais vide (ex. {@code JIRA_TOKEN=}) reste toleree : le health la signale.
 */
@Configuration
public class TrackerConfiguration {

    static final String PROPERTY = "tracker.provider";
    static final String DEFAULT_PROVIDER = "jira";

    /** Proprietes obligatoires par gestionnaire disponible. */
    static final Map<String, List<String>> REQUIRED_PROPERTIES = Map.of(
            "jira", List.of("jira.base-url", "jira.username", "jira.token", "jira.project-key"));

    // Valeur reconnue mais sans adaptateur dans cette version (arrive en v1.1 phase 2)
    private static final String UPCOMING_PROVIDER = "openproject";

    /** Statique : execute avant l'instanciation des beans, donc avant les erreurs de placeholder des adaptateurs. */
    @Bean
    static BeanFactoryPostProcessor trackerSettingsCheck(Environment environment) {
        return beanFactory -> check(environment);
    }

    static void check(Environment environment) {
        String provider = environment.getProperty(PROPERTY, DEFAULT_PROVIDER).toLowerCase(Locale.ROOT);
        if (provider.equals(UPCOMING_PROVIDER)) {
            throw new IllegalStateException(PROPERTY + "=" + UPCOMING_PROVIDER
                    + " is not available yet in this version (the OpenProject adapter is not included); use "
                    + PROPERTY + "=" + DEFAULT_PROVIDER);
        }
        List<String> required = REQUIRED_PROPERTIES.get(provider);
        if (required == null) {
            throw new IllegalStateException("Unknown " + PROPERTY + " '" + environment.getProperty(PROPERTY)
                    + "': expected one of " + DEFAULT_PROVIDER + ", " + UPCOMING_PROVIDER);
        }
        List<String> missing = required.stream().filter(name -> !environment.containsProperty(name)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing configuration for " + PROPERTY + "=" + provider + ": "
                    + String.join(", ", missing));
        }
    }
}
