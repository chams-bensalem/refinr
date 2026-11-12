package fr.heliocraft.refinr.adapters.out.jira;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownAdfTest {

    private static Map<String, Object> text(String value) {
        return Map.of("type", "text", "text", value);
    }

    private static Map<String, Object> marked(String value, String mark) {
        return Map.of("type", "text", "text", value, "marks", List.of(Map.of("type", mark)));
    }

    private static Map<String, Object> paragraph(Object... inline) {
        return Map.of("type", "paragraph", "content", List.of(inline));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> blocks(String markdown) {
        Map<String, Object> doc = MarkdownAdf.toDocument(markdown);
        assertEquals("doc", doc.get("type"));
        assertEquals(1, doc.get("version"));
        return (List<Object>) doc.get("content");
    }

    @Test
    @DisplayName("Plain text becomes a paragraph; blank lines separate paragraphs; line breaks are kept")
    void paragraphs() {
        assertEquals(List.of(
                paragraph(text("Ligne 1"), Map.of("type", "hardBreak"), text("Ligne 2")),
                paragraph(text("Autre paragraphe"))),
            blocks("Ligne 1\nLigne 2\n\nAutre paragraphe"));
    }

    @Test
    @DisplayName("# to ###### become headings of the matching level")
    void headings() {
        assertEquals(List.of(
                Map.of("type", "heading", "attrs", Map.of("level", 1), "content", List.of(text("Titre"))),
                Map.of("type", "heading", "attrs", Map.of("level", 3), "content", List.of(text("Sous-titre")))),
            blocks("# Titre\n### Sous-titre"));
    }

    @Test
    @DisplayName("**bold**, *italic* and `code` become strong, em and code marks")
    void inlineMarks() {
        assertEquals(List.of(paragraph(
                marked("As a", "strong"), text(" dev, "), marked("vite", "em"), text(" avec "), marked("mvn test", "code"))),
            blocks("**As a** dev, *vite* avec `mvn test`"));
    }

    @Test
    @DisplayName("Unclosed markers stay literal text")
    void unclosedMarkers() {
        assertEquals(List.of(paragraph(text("2 * 3 = 6 et **pas fini"))), blocks("2 * 3 = 6 et **pas fini"));
    }

    @Test
    @DisplayName("- / * items become a bullet list, 1. items an ordered list, with inline marks")
    void lists() {
        assertEquals(List.of(
                Map.of("type", "bulletList", "content", List.of(
                    Map.of("type", "listItem", "content", List.of(paragraph(marked("AC1:", "strong"), text(" ok")))),
                    Map.of("type", "listItem", "content", List.of(paragraph(text("deux")))))),
                Map.of("type", "orderedList", "content", List.of(
                    Map.of("type", "listItem", "content", List.of(paragraph(text("premier")))),
                    Map.of("type", "listItem", "content", List.of(paragraph(text("second"))))))),
            blocks("- **AC1:** ok\n* deux\n\n1. premier\n2. second"));
    }

    @Test
    @DisplayName("Fenced code becomes a code block, with its language and without interpreting markdown")
    void codeBlock() {
        assertEquals(List.of(
                Map.of("type", "codeBlock", "attrs", Map.of("language", "java"),
                    "content", List.of(text("int a = 1;\n// **pas gras**"))),
                paragraph(text("fin"))),
            blocks("```java\nint a = 1;\n// **pas gras**\n```\nfin"));
    }

    @Test
    @DisplayName("Empty or missing text gives a placeholder paragraph (Jira rejects an empty document)")
    void emptyText() {
        assertEquals(List.of(paragraph(text("No description provided"))), blocks(null));
        assertEquals(List.of(paragraph(text("No description provided"))), blocks("  \n "));
    }
}
