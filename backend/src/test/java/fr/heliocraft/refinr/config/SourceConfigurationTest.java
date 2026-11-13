// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.adapters.out.confluence.ConfluenceDocumentSource;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Selection de la source documentaire ({@code source.provider}) et controle de sa configuration au demarrage. */
class SourceConfigurationTest {

    private static final String[] CONFLUENCE_PROPERTIES = {
            "confluence.base-url=http://localhost:9", "confluence.username=user@example.com",
            "confluence.token=test-token"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SourceConfiguration.class, ConfluenceDocumentSource.class);

    @Test
    @DisplayName("Without source.provider, Confluence is the only document source (backward compatible default)")
    void confluenceByDefault() {
        runner.withPropertyValues(CONFLUENCE_PROPERTIES).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DocumentSourcePort.class);
            assertThat(context.getBean(DocumentSourcePort.class)).isInstanceOf(ConfluenceDocumentSource.class);
            assertThat(context.getBean(DocumentSourcePort.class).displayName()).isEqualTo("Confluence");
        });
    }

    @Test
    @DisplayName("source.provider=confluence (any case) selects the Confluence adapter")
    void confluenceExplicit() {
        for (String value : new String[] {"confluence", "CONFLUENCE"}) {
            runner.withPropertyValues(CONFLUENCE_PROPERTIES).withPropertyValues("source.provider=" + value).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ConfluenceDocumentSource.class);
            });
        }
    }

    @Test
    @DisplayName("An unknown source.provider stops the startup with the accepted values")
    void unknownProviderFails() {
        runner.withPropertyValues(CONFLUENCE_PROPERTIES).withPropertyValues("source.provider=notion").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessage("Unknown source.provider 'notion': expected one of confluence");
        });
    }

    @Test
    @DisplayName("An empty source.provider is refused like an unknown value")
    void emptyProviderFails() {
        runner.withPropertyValues(CONFLUENCE_PROPERTIES).withPropertyValues("source.provider=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessage("Unknown source.provider '': expected one of confluence");
        });
    }

    @Test
    @DisplayName("BookStack and XWiki are announced but refused until their adapter exists ('not available yet')")
    void plannedSourcesAreNotAvailableYet() {
        for (String value : new String[] {"bookstack", "XWiki"}) {
            runner.withPropertyValues("source.provider=" + value).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessage("source.provider '" + value.toLowerCase()
                        + "' is not available yet: expected confluence");
            });
        }
    }

    @Test
    @DisplayName("Missing Confluence properties are all listed in one startup error")
    void missingConfluencePropertiesAreListed() {
        runner.withPropertyValues("confluence.base-url=http://localhost:9").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessage(
                    "Missing configuration for source.provider=confluence: confluence.username, confluence.token");
        });
    }

    @Test
    @DisplayName("An empty but present Confluence token (CONFLUENCE_TOKEN=) is tolerated: the health check reports it")
    void emptyTokenIsTolerated() {
        runner.withPropertyValues("confluence.base-url=http://localhost:9", "confluence.username=user@example.com",
                "confluence.token=").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ConfluenceDocumentSource.class);
        });
    }

    @Test
    @DisplayName("Confluence is optional: another source starts without any confluence.* property and no Confluence bean")
    void confluenceIsOptionalForAnotherSource() {
        // Sans le controle de demarrage (qui refuse encore bookstack/xwiki en phase 1) : seule la condition de
        // l'adaptateur est en jeu. Sans elle, ses placeholders ${confluence.*} feraient echouer le demarrage.
        new ApplicationContextRunner()
                .withUserConfiguration(ConfluenceDocumentSource.class)
                .withPropertyValues("source.provider=bookstack")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ConfluenceDocumentSource.class);
                    assertThat(context).doesNotHaveBean(DocumentSourcePort.class);
                });
    }
}
