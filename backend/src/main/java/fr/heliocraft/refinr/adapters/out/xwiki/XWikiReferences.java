// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.xwiki;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * References XWiki (controles purement locaux, sans appel) : {@code [wiki:]Espace.SousEspace.Page}, avec les
 * echappements de XWiki ({@code \.}, {@code \:}, {@code \\}). Une page imbriquee est un espace contenant
 * {@code WebHome} : l'onglet <em>Information</em> d'une page donne {@code ProjetStocks.CDC.WebHome}.
 */
public final class XWikiReferences {

    static final String WEB_HOME = "WebHome";
    private static final int MAX_LENGTH = 768;
    private static final int MAX_SEGMENTS = 50;

    private XWikiReferences() {}

    /** Reference d'un document : espaces (du plus haut au plus bas) et nom de page. */
    public record DocumentRef(List<String> spaces, String page) {

        /** Forme locale echappee, celle de l'onglet Information (ex. {@code ProjetStocks.Spec v1\.2.WebHome}). */
        public String serialize() {
            List<String> all = new ArrayList<>(spaces);
            all.add(page);
            return join(all);
        }
    }

    /**
     * Segments d'une reference ({@code wiki:} optionnel, retire), ou vide si la valeur n'est pas une reference valide :
     * vide, trop longue, segment vide, echappement final, {@code /} ou caractere de controle, autre wiki que
     * {@code wiki}.
     */
    static Optional<List<String>> segments(String value, String wiki) {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String prefix = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || c == '/') {
                return Optional.empty();
            }
            if (c == '\\') {
                if (i + 1 >= value.length()) {
                    return Optional.empty();
                }
                current.append(value.charAt(++i));
            } else if (c == ':' && prefix == null && segments.isEmpty()) {
                prefix = current.toString();
                current.setLength(0);
            } else if (c == ':') {
                return Optional.empty();
            } else if (c == '.') {
                segments.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        segments.add(current.toString());
        if (prefix != null && !prefix.equals(wiki)) {
            return Optional.empty();
        }
        if (segments.size() > MAX_SEGMENTS || segments.stream().anyMatch(s -> s.isBlank() || !s.equals(s.strip()))) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(segments));
    }

    /** Vrai si {@code documentId} est une reference de document XWiki du wiki {@code wiki}. */
    static boolean isDocument(String documentId, String wiki) {
        return segments(documentId, wiki).isPresent();
    }

    /** Vrai si {@code space} est une reference d'espace XWiki du wiki {@code wiki}. */
    static boolean isSpace(String space, String wiki) {
        return segments(space, wiki).isPresent();
    }

    /**
     * Documents a essayer, dans l'ordre, pour un identifiant saisi : la reference exacte si elle finit par
     * {@code WebHome} ; sinon la page imbriquee ({@code Espace.Page.WebHome}) puis, s'il y a au moins deux segments,
     * la page terminale ({@code Espace.Page}) — l'URL {@code /bin/view/Espace/Page/} se recopie sans {@code WebHome}.
     */
    static List<DocumentRef> candidates(String documentId, String wiki) {
        List<String> segments = segments(documentId, wiki).orElseThrow(() -> new IllegalArgumentException("Invalid documentId"));
        String last = segments.getLast();
        List<String> head = segments.subList(0, segments.size() - 1);
        if (last.equals(WEB_HOME) && !head.isEmpty()) {
            return List.of(new DocumentRef(head, WEB_HOME));
        }
        List<DocumentRef> candidates = new ArrayList<>();
        candidates.add(new DocumentRef(segments, WEB_HOME));
        if (!head.isEmpty()) {
            candidates.add(new DocumentRef(head, last));
        }
        return List.copyOf(candidates);
    }

    /** Reference d'espace en forme locale echappee (pour {@code space_prefix} de Solr). */
    static String spaceReference(String space, String wiki) {
        return join(segments(space, wiki).orElseThrow(() -> new IllegalArgumentException("Invalid space")));
    }

    /** Segments joints par {@code .}, chacun echappe ({@code \}, {@code .}, {@code :}). */
    static String join(List<String> segments) {
        List<String> escaped = segments.stream()
                .map(s -> s.replace("\\", "\\\\").replace(".", "\\.").replace(":", "\\:"))
                .toList();
        return String.join(".", escaped);
    }

    /** Identifiant d'un resultat ({@code xwiki:A.B.WebHome}) sans le prefixe de wiki, tel que l'onglet Information. */
    static String localId(String id, String wiki) {
        return id != null && id.startsWith(wiki + ":") ? id.substring(wiki.length() + 1) : id;
    }
}
