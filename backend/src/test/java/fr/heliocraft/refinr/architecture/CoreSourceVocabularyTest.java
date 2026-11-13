// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Complement source de la regle ArchUnit {@code no_tool_identifier_in_domain_and_application} : ArchUnit ne voit
 * ni les noms de parametres ni les variables locales. Ce test lit les sources de domain/ et application/ et refuse
 * tout identifiant contenant le nom d'un gestionnaire de tickets ("jira", "openproject") ou d'une source documentaire
 * ("confluence", "bookstack", "xwiki"), insensible a la casse, commentaires et litteraux exclus.
 */
class CoreSourceVocabularyTest {

    private static final List<String> FORBIDDEN = List.of("jira", "openproject", "confluence", "bookstack", "xwiki");
    private static final Path SOURCES = Path.of(System.getProperty("basedir", "."), "src/main/java/fr/heliocraft/refinr");
    private static final Pattern IDENTIFIER = Pattern.compile("[\\p{L}_$][\\p{L}\\p{N}_$]*");

    @Test
    @DisplayName("No identifier of domain/ or application/ sources names a tracker or a document source")
    void coreSourcesDoNotSpeakJira() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String layer : List.of("domain", "application")) {
            try (Stream<Path> walk = Files.walk(SOURCES.resolve(layer))) {
                walk.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
            }
        }
        assertTrue(files.size() >= 10, "sources not found under " + SOURCES.toAbsolutePath() + ": " + files);

        List<String> violations = new ArrayList<>();
        for (Path file : files) {
            String code = stripCommentsAndLiterals(Files.readString(file, StandardCharsets.UTF_8));
            for (String hit : forbiddenIdentifiers(code)) {
                violations.add(SOURCES.relativize(file) + ":" + hit);
            }
        }
        assertEquals(List.of(), violations);
    }

    @Test
    @DisplayName("The scanner ignores comments and literals but catches parameters and locals")
    void scannerSeesOnlyCode() {
        String sample = """
                package demo; // jira in a line comment
                /* Jira in a block comment */
                /** JIRA in a javadoc */
                class Sample {
                    String log = "Creating Jira issue \\" jira";
                    char quote = '"';
                    char escaped = '\\'';
                    String block = \"""
                        jira in a text block
                        \""";
                    void create(String jiraEpicKey) {
                        int localJira = 1;
                    }
                }
                """;

        assertEquals(List.of("11:jiraEpicKey", "12:localJira"), forbiddenIdentifiers(stripCommentsAndLiterals(sample)));
    }

    @Test
    @DisplayName("The scanner also catches OpenProject identifiers, not the word in comments or literals")
    void scannerSeesOpenProjectIdentifiers() {
        String sample = """
                class Sample { // OpenProject in a comment
                    String url = "openproject";
                    void create(String openProjectId) {
                    }
                }
                """;
        assertEquals(List.of("3:openProjectId"), forbiddenIdentifiers(stripCommentsAndLiterals(sample)));
    }

    @Test
    @DisplayName("The scanner also catches document source identifiers (Confluence, BookStack, XWiki)")
    void scannerSeesDocumentSourceIdentifiers() {
        String sample = """
                class Sample { // Confluence page, BookStack book, XWiki space
                    String name = "confluence bookstack xwiki";
                    void read(String confluencePageId, String bookStackId) {
                        String xwikiReference = name;
                    }
                }
                """;
        assertEquals(List.of("3:confluencePageId", "3:bookStackId", "4:xwikiReference"),
                forbiddenIdentifiers(stripCommentsAndLiterals(sample)));
    }

    /** "ligne:identifiant" pour chaque identifiant interdit du code (lignes numerotees a partir de 1). */
    private static List<String> forbiddenIdentifiers(String code) {
        List<String> hits = new ArrayList<>();
        String[] lines = code.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = IDENTIFIER.matcher(lines[i]);
            while (matcher.find()) {
                String identifier = matcher.group().toLowerCase(Locale.ROOT);
                if (FORBIDDEN.stream().anyMatch(identifier::contains)) {
                    hits.add((i + 1) + ":" + matcher.group());
                }
            }
        }
        return hits;
    }

    /** Remplace commentaires, chaines, blocs de texte et caracteres par des espaces, en gardant les sauts de ligne. */
    static String stripCommentsAndLiterals(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (source.startsWith("//", i)) {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                blank(source, i, stop, out);
                i = stop;
            } else if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                while (end > 0 && source.charAt(end - 1) == '\\') {
                    end = source.indexOf("\"\"\"", end + 1);
                }
                int stop = end < 0 ? n : end + 3;
                blank(source, i, stop, out);
                i = stop;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && source.charAt(j) != c && source.charAt(j) != '\n') {
                    j += source.charAt(j) == '\\' ? 2 : 1;
                }
                int stop = Math.min(n, j + 1);
                blank(source, i, stop, out);
                i = stop;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static void blank(String source, int from, int to, StringBuilder out) {
        for (int k = from; k < to; k++) {
            out.append(source.charAt(k) == '\n' ? '\n' : ' ');
        }
    }
}
