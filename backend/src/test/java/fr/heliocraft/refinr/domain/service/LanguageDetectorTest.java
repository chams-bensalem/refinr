// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.domain.model.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageDetectorTest {

    @Test
    @DisplayName("Short story (title and description, about 20 words) detected in both languages")
    void shortStoryInBothLanguages() {
        assertEquals(Language.EN, LanguageDetector.detect(
                "Cancel an order\nThe customer can cancel an order from the account page as long as it has not been shipped."));
        assertEquals(Language.FR, LanguageDetector.detect(
                "Annuler une commande\nLe client peut annuler une commande depuis la page de son compte tant qu'elle n'est pas expédiée."));
    }

    @Test
    @DisplayName("Given / When / Then in French criteria do not make a French story English")
    void gherkinKeywordsDoNotCount() {
        assertEquals(Language.FR, LanguageDetector.detect("""
                Export CSV des commandes payées
                Le client télécharge ses commandes payées depuis la page de son compte.
                Given un client avec deux commandes payées, When il exporte, Then le fichier contient deux lignes
                Given une commande annulée, When il exporte, Then elle n'apparaît pas dans le fichier"""));
    }

    @Test
    @DisplayName("The three generation specs: two in English, one in French")
    void generationSpecs() throws Exception {
        Path specs = Path.of("..", "eval", "generation", "specs");
        assertEquals(Language.EN, LanguageDetector.detect(Files.readString(specs.resolve("order-export.md"), StandardCharsets.UTF_8)));
        assertEquals(Language.EN, LanguageDetector.detect(Files.readString(specs.resolve("customer-data-export.md"), StandardCharsets.UTF_8)));
        assertEquals(Language.FR, LanguageDetector.detect(Files.readString(specs.resolve("reservation-salles.md"), StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Empty, too short, code-only or undecided text: French by default")
    void fallbackIsFrench() {
        assertEquals(Language.FR, LanguageDetector.detect(null));
        assertEquals(Language.FR, LanguageDetector.detect(""));
        assertEquals(Language.FR, LanguageDetector.detect("Export of the orders"));          // trop peu de mots reconnus
        assertEquals(Language.FR, LanguageDetector.detect("```java\nthe and of to is are for in that with not be this it\n```"));
        assertEquals(Language.FR, LanguageDetector.detect(
                "The customer and the seller are in the same shop. Le client et le vendeur sont dans la même boutique, avec les mêmes droits."));
    }

    @Test
    @DisplayName("Code, inline code and URLs are ignored")
    void noiseIsIgnored() {
        assertEquals(Language.FR, LanguageDetector.detect("""
                Le service expose une route de lecture pour les gestionnaires.
                `the and of to is are for in that with` https://example.com/the/and/of/to/is/are/for/in/that"""));
    }
}
