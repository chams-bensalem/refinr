// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.adapters.out.jira.JiraIssueTracker;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Selection du gestionnaire de tickets ({@code tracker.provider}) et controle de sa configuration au demarrage. */
class TrackerConfigurationTest {

    private static final String[] JIRA_PROPERTIES = {
            "jira.base-url=http://localhost:9", "jira.username=user@example.com",
            "jira.token=test-token", "jira.project-key=PROJ"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TrackerConfiguration.class, JiraIssueTracker.class);

    @Test
    @DisplayName("Without tracker.provider, Jira is the only issue tracker (backward compatible default)")
    void jiraByDefault() {
        runner.withPropertyValues(JIRA_PROPERTIES).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(IssueTrackerPort.class);
            assertThat(context.getBean(IssueTrackerPort.class)).isInstanceOf(JiraIssueTracker.class);
        });
    }

    @Test
    @DisplayName("tracker.provider=jira (any case) selects the Jira adapter")
    void jiraExplicit() {
        for (String value : new String[] {"jira", "JIRA"}) {
            runner.withPropertyValues(JIRA_PROPERTIES).withPropertyValues("tracker.provider=" + value).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(JiraIssueTracker.class);
            });
        }
    }

    @Test
    @DisplayName("An unknown tracker.provider stops the startup with the accepted values")
    void unknownProviderFails() {
        runner.withPropertyValues(JIRA_PROPERTIES).withPropertyValues("tracker.provider=foo").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessage("Unknown tracker.provider 'foo': expected one of jira, openproject");
        });
    }

    @Test
    @DisplayName("An empty tracker.provider is refused like an unknown value")
    void emptyProviderFails() {
        runner.withPropertyValues(JIRA_PROPERTIES).withPropertyValues("tracker.provider=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessage("Unknown tracker.provider '': expected one of jira, openproject");
        });
    }

    @Test
    @DisplayName("tracker.provider=openproject is refused explicitly until its adapter exists (no OpenProject bean)")
    void openProjectNotAvailableYet() {
        runner.withPropertyValues(JIRA_PROPERTIES).withPropertyValues("tracker.provider=openproject").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining("tracker.provider=openproject")
                    .hasMessageContaining("not available yet");
        });
    }

    @Test
    @DisplayName("Missing properties of the chosen tracker are all listed in one startup error")
    void missingJiraPropertiesAreListed() {
        runner.withPropertyValues("jira.base-url=http://localhost:9", "jira.username=user@example.com").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessage("Missing configuration for tracker.provider=jira: jira.token, jira.project-key");
        });
    }

    @Test
    @DisplayName("An empty but present secret (e.g. JIRA_TOKEN=) is tolerated: the health check reports it")
    void emptyTokenIsTolerated() {
        runner.withPropertyValues("jira.base-url=http://localhost:9", "jira.username=user@example.com",
                "jira.token=", "jira.project-key=PROJ").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JiraIssueTracker.class);
        });
    }
}
