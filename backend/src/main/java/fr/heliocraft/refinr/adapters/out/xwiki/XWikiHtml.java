// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.xwiki;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML rendu par XWiki : une macro en echec y injecte un message (« Failed to execute the [include] macro... ») et une
 * pile Java complete ({@code <div class="box xwikirenderingerror">} puis {@code <div class="... xwikirenderingerrordescription
 * hidden">}, ou {@code <span>} pour une macro en ligne). Ces blocs sont retires avant l'analyse : ce n'est pas du
 * contenu de la specification, et ils nommeraient des pages que le compte ne peut pas lire.
 */
final class XWikiHtml {

    // Balise ouvrante div/span dont l'attribut class contient xwikirenderingerror ou xwikirenderingerrordescription
    private static final Pattern ERROR_OPEN = Pattern.compile(
            "<(div|span)\\b[^>]*\\bclass\\s*=\\s*([\"'])[^\"']*\\bxwikirenderingerror(?:description)?\\b[^\"']*\\2[^>]*>",
            Pattern.CASE_INSENSITIVE);

    private XWikiHtml() {}

    /** HTML sans blocs d'erreur de rendu, et nombre de blocs retires. */
    record Cleaned(String html, int removed) {}

    static Cleaned withoutRenderingErrors(String html) {
        if (html == null || html.isEmpty()) {
            return new Cleaned(html == null ? "" : html, 0);
        }
        StringBuilder out = new StringBuilder(html.length());
        int removed = 0;
        int from = 0;
        Matcher open = ERROR_OPEN.matcher(html);
        while (from < html.length() && open.find(from)) {
            out.append(html, from, open.start());
            int end = closingEnd(html, open.group(1).toLowerCase(Locale.ROOT), open.end());
            removed++;
            from = end;
        }
        out.append(html, Math.min(from, html.length()), html.length());
        return new Cleaned(out.toString(), removed);
    }

    /**
     * Fin de la balise fermante qui equilibre la balise ouverte juste avant {@code start} (balises imbriquees du meme
     * nom comptees) ; fin du texte si le HTML est tronque.
     */
    private static int closingEnd(String html, String tag, int start) {
        Matcher tags = Pattern.compile("<(/?)" + tag + "\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        int depth = 1;
        int position = start;
        while (tags.find(position)) {
            depth += tags.group(1).isEmpty() ? 1 : -1;
            position = tags.end();
            if (depth == 0) {
                return position;
            }
        }
        return html.length();
    }
}
