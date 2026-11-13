// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.adapters.out.bookstack.BookStackDocumentSource;
import fr.heliocraft.refinr.adapters.out.confluence.ConfluenceDocumentSource;
import fr.heliocraft.refinr.adapters.out.xwiki.XWikiDocumentSource;
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

    private static final String[] BOOKSTACK_PROPERTIES = {
            "source.provider=bookstack", "bookstack.base-url=http://localhost:9",
            "bookstack.token-id=test-id", "bookstack.token-secret=test-secret"};

    private static final String[] XWIKI_PROPERTIES = {
            "source.provider=xwiki", "xwiki.base-url=http://localhost:9",
            "xwiki.username=refinr_bot", "xwiki.password=test-password"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SourceConfiguration.class, ConfluenceDocumentSource.class, BookStackDocumentSource.class,
                    XWikiDocumentSource.class);

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
                    .hasMessage("Unknown source.provider 'notion': expected one of bookstack, confluence, xwiki");
        });
    }

    @Test
    @DisplayName("An empty source.provider is refused like an unknown value")
    void emptyProviderFails() {
        runner.withPropertyValues(CONFLUENCE_PROPERTIES).withPropertyValues("source.provider=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessage("Unknown source.provider '': expected one of bookstack, confluence, xwiki");
        });
    }

    @Test
    @DisplayName("source.provider=xwiki (any case) selects XWiki as the only source, without any confluence.* property")
    void xwikiSelectedWithoutConfluence() {
        for (String value : new String[] {"xwiki", "XWiki"}) {
            runner.withPropertyValues(XWIKI_PROPERTIES).withPropertyValues("source.provider=" + value).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(DocumentSourcePort.class);
                assertThat(context).hasSingleBean(XWikiDocumentSource.class);
                assertThat(context).doesNotHaveBean(ConfluenceDocumentSource.class);
                assertThat(context.getBean(DocumentSourcePort.class).displayName()).isEqualTo("XWiki");
            });
        }
    }

    @Test
    @DisplayName("Missing XWiki properties are all listed in one startup error; web-url and wiki are optional")
    void missingXWikiPropertiesAreListed() {
        runner.withPropertyValues("source.provider=xwiki", "xwiki.base-url=http://localhost:9").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessage(
                    "Missing configuration for source.provider=xwiki: xwiki.username, xwiki.password");
        });
    }

    @Test
    @DisplayName("source.provider=bookstack (any case) selects BookStack as the only source, without any confluence.* property")
    void bookStackSelectedWithoutConfluence() {
        for (String value : new String[] {"bookstack", "BookStack"}) {
            runner.withPropertyValues(BOOKSTACK_PROPERTIES).withPropertyValues("source.provider=" + value).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(DocumentSourcePort.class);
                assertThat(context).hasSingleBean(BookStackDocumentSource.class);
                assertThat(context).doesNotHaveBean(ConfluenceDocumentSource.class);
                assertThat(context.getBean(DocumentSourcePort.class).displayName()).isEqualTo("BookStack");
            });
        }
    }

    @Test
    @DisplayName("Missing BookStack properties are all listed in one startup error")
    void missingBookStackPropertiesAreListed() {
        runner.withPropertyValues("source.provider=bookstack", "bookstack.base-url=http://localhost:9").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessage(
                    "Missing configuration for source.provider=bookstack: bookstack.token-id, bookstack.token-secret");
        });
    }

    @Test
    @DisplayName("Empty BookStack token values are tolerated (the health check reports them); web-url is optional")
    void emptyBookStackTokenIsTolerated() {
        runner.withPropertyValues(BOOKSTACK_PROPERTIES)
                .withPropertyValues("bookstack.token-id=", "bookstack.token-secret=").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(BookStackDocumentSource.class);
                });
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
