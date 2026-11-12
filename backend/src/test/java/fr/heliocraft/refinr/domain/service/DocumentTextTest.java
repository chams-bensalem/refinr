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
    }
}
