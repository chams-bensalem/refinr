// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentTextTest {

    @Test
    @DisplayName("Empty, blank, markup-only or image-only content has no useful text")
    void noUsefulText() {
        assertFalse(DocumentText.hasUsefulText(null));
        assertFalse(DocumentText.hasUsefulText(""));
        assertFalse(DocumentText.hasUsefulText(" \n\t "));
        assertFalse(DocumentText.hasUsefulText("<p></p><p><br /></p>"));
        assertFalse(DocumentText.hasUsefulText("<p>&nbsp;&#160;&#xA0;</p>"));
        assertFalse(DocumentText.hasUsefulText("<p> - . * </p>"));
        assertFalse(DocumentText.hasUsefulText("<ac:image><ri:attachment ri:filename=\"schema.png\" /></ac:image>"));
    }

    @Test
    @DisplayName("Any letter or digit outside the markup is useful text")
    void usefulText() {
        assertTrue(DocumentText.hasUsefulText("<h1>Inventaire</h1>"));
        assertTrue(DocumentText.hasUsefulText("<p>&nbsp;Export PDF</p>"));
        assertTrue(DocumentText.hasUsefulText("Plain text spec"));
        assertTrue(DocumentText.hasUsefulText("<p>42</p>"));
        assertTrue(DocumentText.hasUsefulText("<p>Créer un élément</p>"));
        // Macro de code Confluence : son contenu (CDATA) est du texte utile
        assertTrue(DocumentText.hasUsefulText("<ac:structured-macro ac:name=\"code\"><ac:plain-text-body>"
                + "<![CDATA[openapi: 3.0.0\npaths:\n  /orders:\n    get: {}]]></ac:plain-text-body></ac:structured-macro>"));
        assertTrue(DocumentText.hasUsefulText("<ac:plain-text-body><![CDATA[if (a > b) { return a; }]]></ac:plain-text-body>"));
        assertFalse(DocumentText.hasUsefulText("<ac:plain-text-body><![CDATA[ \n ]]></ac:plain-text-body>"));
    }

    @Test
    @DisplayName("Rich Markdown (headings, lists, table, code block) is useful text")
    void richMarkdownHasUsefulText() {
        String markdown = """
                # Gestion des stocks

                ## Exigences
                - Créer un article
                - Supprimer un article
                1. Saisir la quantité
                2. Valider

                | Champ | Type | Obligatoire |
                |-------|------|:-----------:|
                | sku   | text | oui         |

                ```java
                List<String> skus = load();
                if (a < b && b > c) { return; }
                ```
                > Note : l'export PDF est hors périmètre.
                """;
        assertTrue(DocumentText.hasUsefulText(markdown));
        // Chaque construction seule porte encore son texte
        assertTrue(DocumentText.hasUsefulText("| sku | qty |\n|---|---|\n| A1 | 3 |"));
        assertTrue(DocumentText.hasUsefulText("```\nList<String> a;\n```"));
        assertTrue(DocumentText.hasUsefulText("- [ ] tâche"));
    }

    @Test
    @DisplayName("Markdown made only of syntax (no letter or digit) has no useful text")
    void markdownSyntaxOnlyHasNoUsefulText() {
        assertFalse(DocumentText.hasUsefulText("# \n## \n- \n* \n> \n---\n|---|---|\n| | |\n```\n```\n"));
    }
}
