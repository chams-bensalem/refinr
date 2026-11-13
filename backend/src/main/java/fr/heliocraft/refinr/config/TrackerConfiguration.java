// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.adapters.out.openproject.OpenProjectProjectIds;
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
 * message explicite, avant la creation de tout bean, si la valeur est inconnue, si une propriete du gestionnaire
 * choisi est absente ou si le projet OpenProject par defaut n'a pas un format valide. Une propriete presente mais
 * vide (ex. {@code JIRA_TOKEN=}) reste toleree : le health la signale.
 */
@Configuration
public class TrackerConfiguration {

    static final String PROPERTY = "tracker.provider";
    static final String DEFAULT_PROVIDER = "jira";

    /** Proprietes obligatoires par gestionnaire disponible. */
    static final Map<String, List<String>> REQUIRED_PROPERTIES = Map.of(
            "jira", List.of("jira.base-url", "jira.username", "jira.token", "jira.project-key"),
            "openproject", List.of("openproject.base-url", "openproject.api-key", "openproject.project"));

    private static final String OPENPROJECT_PROJECT = "openproject.project";

    /** Statique : execute avant l'instanciation des beans, donc avant les erreurs de placeholder des adaptateurs. */
    @Bean
    static BeanFactoryPostProcessor trackerSettingsCheck(Environment environment) {
        return beanFactory -> check(environment);
    }

    static void check(Environment environment) {
        String provider = environment.getProperty(PROPERTY, DEFAULT_PROVIDER).toLowerCase(Locale.ROOT);
        List<String> required = REQUIRED_PROPERTIES.get(provider);
        if (required == null) {
            throw new IllegalStateException("Unknown " + PROPERTY + " '" + environment.getProperty(PROPERTY)
                    + "': expected one of jira, openproject");
        }
        List<String> missing = required.stream().filter(name -> !environment.containsProperty(name)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing configuration for " + PROPERTY + "=" + provider + ": "
                    + String.join(", ", missing));
        }
        if (provider.equals("openproject")) {
            // Projet par defaut : vide tolere (le health le signale), sinon au format d'un identifiant ou id OpenProject
            String project = environment.getProperty(OPENPROJECT_PROJECT, "");
            if (!project.isBlank() && !OpenProjectProjectIds.isValid(project)) {
                throw new IllegalStateException("Invalid " + OPENPROJECT_PROJECT + " '" + project + "': expected an "
                        + "OpenProject project identifier (lowercase letters, digits, - and _, up to 100 characters) "
                        + "or a numeric project id");
            }
        }
    }
}
