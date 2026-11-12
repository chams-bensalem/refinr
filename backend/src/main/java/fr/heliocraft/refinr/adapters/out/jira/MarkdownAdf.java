package fr.heliocraft.refinr.adapters.out.jira;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversion Markdown -> ADF (Atlassian Document Format) pour les descriptions Jira. Sous-ensemble pris en charge :
 * paragraphes (retours a la ligne conserves), titres {@code #}..{@code ######}, listes a puces ({@code -}, {@code *},
 * {@code +}) et numerotees ({@code 1.}), blocs de code delimites par {@code ```}, et en ligne {@code **gras**},
 * {@code *italique*} et {@code `code`}. Le reste (listes imbriquees, liens, tableaux) reste du texte.
 */
final class MarkdownAdf {

    static final String EMPTY_PLACEHOLDER = "No description provided";

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*+]\\s+(.*)$");
    private static final Pattern ORDERED = Pattern.compile("^\\s*\\d+[.)]\\s+(.*)$");
    private static final Pattern FENCE = Pattern.compile("^\\s*```\\s*([\\w+#.-]*)\\s*$");

    private MarkdownAdf() {}

    /** Document ADF ; un texte vide donne un paragraphe de remplacement (Jira refuse un document vide). */
    static Map<String, Object> toDocument(String markdown) {
        List<Object> content = blocks(markdown == null ? "" : markdown);
        if (content.isEmpty()) {
            content = List.of(paragraph(List.of(text(EMPTY_PLACEHOLDER, List.of()))));
        }
        return Map.of("type", "doc", "version", 1, "content", content);
    }

    private static List<Object> blocks(String markdown) {
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        List<Object> blocks = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            Matcher fence = FENCE.matcher(line);
            Matcher heading = HEADING.matcher(line);
            if (line.isBlank()) {
                i++;
            } else if (fence.matches()) {
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].strip().startsWith("```")) {
                    code.append(code.isEmpty() ? "" : "\n").append(lines[i]);
                    i++;
                }
                i++; // fence fermante (ou fin du texte)
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("type", "codeBlock");
                if (!fence.group(1).isEmpty()) {
                    block.put("attrs", Map.of("language", fence.group(1)));
                }
                if (!code.isEmpty()) {
                    block.put("content", List.of(text(code.toString(), List.of())));
                }
                blocks.add(block);
            } else if (heading.matches()) {
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("type", "heading");
                block.put("attrs", Map.of("level", heading.group(1).length()));
                List<Object> inline = inline(heading.group(2), List.of());
                if (!inline.isEmpty()) {
                    block.put("content", inline);
                }
                blocks.add(block);
                i++;
            } else if (BULLET.matcher(line).matches() || ORDERED.matcher(line).matches()) {
                Pattern item = BULLET.matcher(line).matches() ? BULLET : ORDERED;
                List<Object> items = new ArrayList<>();
                Matcher matcher;
                while (i < lines.length && (matcher = item.matcher(lines[i])).matches()) {
                    items.add(Map.of("type", "listItem", "content", List.of(paragraph(inline(matcher.group(1), List.of())))));
                    i++;
                }
                blocks.add(Map.of("type", item == BULLET ? "bulletList" : "orderedList", "content", items));
            } else {
                List<Object> inline = new ArrayList<>();
                while (i < lines.length && !lines[i].isBlank() && !startsBlock(lines[i])) {
                    if (!inline.isEmpty()) {
                        inline.add(Map.of("type", "hardBreak"));
                    }
                    inline.addAll(inline(lines[i].strip(), List.of()));
                    i++;
                }
                blocks.add(paragraph(inline));
            }
        }
        return blocks;
    }

    private static boolean startsBlock(String line) {
        return FENCE.matcher(line).matches() || HEADING.matcher(line).matches()
                || BULLET.matcher(line).matches() || ORDERED.matcher(line).matches();
    }

    /** Texte en ligne -> noeuds texte marques ; un marqueur sans fermeture reste du texte. */
    private static List<Object> inline(String source, List<String> marks) {
        List<Object> nodes = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '`') {
                int end = source.indexOf('`', i + 1);
                if (end > i + 1) {
                    flush(plain, marks, nodes);
                    // La marque code ne se combine pas avec les autres en ADF
                    nodes.add(text(source.substring(i + 1, end), List.of("code")));
                    i = end + 1;
                    continue;
                }
            } else if (source.startsWith("**", i)) {
                int end = source.indexOf("**", i + 2);
                if (end > i + 2) {
                    flush(plain, marks, nodes);
                    nodes.addAll(inline(source.substring(i + 2, end), with(marks, "strong")));
                    i = end + 2;
                    continue;
                }
            } else if (c == '*' && i + 1 < source.length() && !Character.isWhitespace(source.charAt(i + 1))) {
                int end = source.indexOf('*', i + 1);
                if (end > i + 1 && !Character.isWhitespace(source.charAt(end - 1))) {
                    flush(plain, marks, nodes);
                    nodes.addAll(inline(source.substring(i + 1, end), with(marks, "em")));
                    i = end + 1;
                    continue;
                }
            }
            plain.append(c);
            i++;
        }
        flush(plain, marks, nodes);
        return nodes;
    }

    private static List<String> with(List<String> marks, String mark) {
        List<String> result = new ArrayList<>(marks);
        if (!result.contains(mark)) {
            result.add(mark);
        }
        return result;
    }

    private static void flush(StringBuilder plain, List<String> marks, List<Object> nodes) {
        if (!plain.isEmpty()) {
            nodes.add(text(plain.toString(), marks));
            plain.setLength(0);
        }
    }

    private static Map<String, Object> text(String value, List<String> marks) {
        if (marks.isEmpty()) {
            return Map.of("type", "text", "text", value);
        }
        return Map.of("type", "text", "text", value, "marks", marks.stream().map(m -> Map.of("type", m)).toList());
    }

    private static Map<String, Object> paragraph(List<Object> inline) {
        return Map.of("type", "paragraph", "content", inline);
    }
}
