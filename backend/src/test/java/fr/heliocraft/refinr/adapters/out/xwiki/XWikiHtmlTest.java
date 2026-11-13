// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.xwiki;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XWikiHtmlTest {

    // Forme reelle observee en phase 0 : {{include}} d'une page interdite au compte (pile Java abregee)
    private static final String FAILED_INCLUDE = "<p>Avant.</p><div class=\"box xwikirenderingerror\"><p>Failed to execute "
            + "the [include] macro. Cause: [Current user [xwiki:XWiki.refinr_bot] doesn't have view rights on document "
            + "[xwiki:ProjetRH.CDC-Confidentiel.WebHome]]. Click on this message for details.</p></div>"
            + "<div class=\"box xwikirenderingerrordescription hidden\"><p>org.xwiki.rendering.macro.MacroExecutionException: "
            + "Current user ...<br/>&nbsp;at org.xwiki.rendering.internal.macro.include.IncludeMacro.execute(IncludeMacro.java:124)"
            + "</p></div><p>Apres.</p>";

    @Test
    @DisplayName("Removes the error message and the Java stack trace of a failed macro, keeps the text around")
    void removesFailedMacroBlocks() {
        XWikiHtml.Cleaned cleaned = XWikiHtml.withoutRenderingErrors(FAILED_INCLUDE);

        assertEquals("<p>Avant.</p><p>Apres.</p>", cleaned.html());
        assertEquals(2, cleaned.removed());
    }

    @Test
    @DisplayName("Nested divs inside an error block are removed with it; inline span errors too; other blocks are kept")
    void nestedAndInline() {
        String html = "<div class=\"box infomessage\"><div><p>Info</p></div></div>"
                + "<div class='xwikirenderingerror box'><div><div>deep</div></div><p>x</p></div>"
                + "<p>texte <span class=\"xwikirenderingerror\">Failed to execute the [code] macro<span>!</span></span> suite</p>";

        XWikiHtml.Cleaned cleaned = XWikiHtml.withoutRenderingErrors(html);

        assertEquals("<div class=\"box infomessage\"><div><p>Info</p></div></div><p>texte  suite</p>", cleaned.html());
        assertEquals(2, cleaned.removed());
    }

    @Test
    @DisplayName("No error block: unchanged; null or empty: empty; truncated error block: removed to the end")
    void edgeCases() {
        assertEquals(new XWikiHtml.Cleaned("<p>ok</p>", 0), XWikiHtml.withoutRenderingErrors("<p>ok</p>"));
        assertEquals(new XWikiHtml.Cleaned("", 0), XWikiHtml.withoutRenderingErrors(null));
        assertEquals(new XWikiHtml.Cleaned("<p>a</p>", 1),
                XWikiHtml.withoutRenderingErrors("<p>a</p><div class=\"xwikirenderingerror\"><p>pile"));
        // Une classe qui contient seulement le mot en prefixe n'est pas un bloc d'erreur
        assertEquals(0, XWikiHtml.withoutRenderingErrors("<div class=\"xwikirenderingerrorx\">x</div>").removed());
    }
}
