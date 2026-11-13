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

    // Lineaire (pas de retour arriere) : balise div/span aux attributs bornes et possessifs, puis classe verifiee a part
    // sur le texte de la balise ; un contenu de page piege (balise geante ou non fermee) ne peut pas faire exploser
    // le temps de traitement
    private static final int MAX_TAG = 4096;
    private static final Pattern OPEN_TAG = Pattern.compile("<(div|span)\\b([^<>]{0," + MAX_TAG + "}+)>",
            Pattern.CASE_INSENSITIVE);
    // Valeur de l'attribut class (entre guillemets), lue d'un bloc puis decoupee en jetons
    private static final Pattern CLASS_VALUE = Pattern.compile("\\bclass\\s*+=\\s*+([\"'])([^\"'<>]*+)\\1",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIV_TAG = Pattern.compile("<(/?)div\\b[^<>]{0," + MAX_TAG + "}+>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPAN_TAG = Pattern.compile("<(/?)span\\b[^<>]{0," + MAX_TAG + "}+>", Pattern.CASE_INSENSITIVE);

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
        Matcher open = OPEN_TAG.matcher(html);
        int search = 0;
        while (search < html.length() && open.find(search)) {
            if (!isRenderingError(open.group(2))) {
                search = open.end();
                continue;
            }
            out.append(html, from, open.start());
            from = closingEnd(html, open.group(1).toLowerCase(Locale.ROOT), open.end());
            search = from;
            removed++;
        }
        out.append(html, Math.min(from, html.length()), html.length());
        return new Cleaned(out.toString(), removed);
    }

    /** Vrai si les attributs de la balise portent la classe xwikirenderingerror ou xwikirenderingerrordescription. */
    private static boolean isRenderingError(String attributes) {
        Matcher value = CLASS_VALUE.matcher(attributes);
        if (!value.find()) {
            return false;
        }
        for (String token : value.group(2).trim().split("\\s++")) {
            String name = token.toLowerCase(Locale.ROOT);
            if (name.equals("xwikirenderingerror") || name.equals("xwikirenderingerrordescription")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fin de la balise fermante qui equilibre la balise ouverte juste avant {@code start} (balises imbriquees du meme
     * nom comptees) ; fin du texte si le HTML est tronque.
     */
    private static int closingEnd(String html, String tag, int start) {
        Matcher tags = (tag.equals("div") ? DIV_TAG : SPAN_TAG).matcher(html);
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
