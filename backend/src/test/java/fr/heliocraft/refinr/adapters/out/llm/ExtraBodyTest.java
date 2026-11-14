// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExtraBodyTest {

    private static Map<String, Object> ordered(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("A dotted key becomes a nested object; a plain key stays at the top level")
    void dottedKeyIsNested() {
        ExtraBody extra = ExtraBody.of(ordered(
                "generationConfig.thinkingConfig.thinkingLevel", "low",
                "reasoning_effort", "none"));

        Map<String, Object> body = extra.applyTo(new LinkedHashMap<>());

        assertEquals(Map.of("generationConfig", Map.of("thinkingConfig", Map.of("thinkingLevel", "low")),
                "reasoning_effort", "none"), body);
        assertEquals(List.of("generationConfig.thinkingConfig.thinkingLevel", "reasoning_effort"), List.copyOf(extra.paths()));
    }

    @Test
    @DisplayName("Merged into an existing object without touching its other fields, nor the original body")
    void mergedIntoExistingObject() {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("generationConfig", Map.of("maxOutputTokens", 1000)));

        Map<String, Object> merged = ExtraBody.of(Map.of("generationConfig.thinkingConfig.thinkingLevel", "low")).applyTo(body);

        assertEquals(Map.of("maxOutputTokens", 1000, "thinkingConfig", Map.of("thinkingLevel", "low")),
                merged.get("generationConfig"));
        assertEquals(Map.of("generationConfig", Map.of("maxOutputTokens", 1000)), body, "the original body is unchanged");
    }

    @Test
    @DisplayName("Values keep their type (JSON objects, booleans, numbers)")
    void valuesKeepTheirType() {
        Map<String, Object> thinking = Map.of("type", "enabled", "budget_tokens", 2048);

        Map<String, Object> body = ExtraBody.of(ordered("thinking", thinking, "think", false, "top_k", 20))
                .applyTo(new LinkedHashMap<>());

        assertEquals(thinking, body.get("thinking"));
        assertEquals(false, body.get("think"));
        assertEquals(20, body.get("top_k"));
    }

    @Test
    @DisplayName("No extra-body: nothing added")
    void emptyAddsNothing() {
        assertTrue(ExtraBody.NONE.isEmpty());
        assertEquals(Map.of("model", "m"), ExtraBody.NONE.applyTo(Map.of("model", "m")));
        assertTrue(ExtraBody.of(null).isEmpty());
    }

    @Test
    @DisplayName("Two keys that overlap (a and a.b) are refused, with both keys named")
    void overlappingKeysAreRefused() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ExtraBody.of(ordered("thinking", Map.of("type", "enabled"), "thinking.budget_tokens", 1024)));

        assertEquals("extra-body keys 'thinking' and 'thinking.budget_tokens' overlap: keep only one of them", error.getMessage());
    }

    @Test
    @DisplayName("A malformed key (empty segment) is refused")
    void malformedKeyIsRefused() {
        for (String key : new String[] {"a..b", ".a", "a.", " "}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ExtraBody.of(Map.of(key, "x")), key);
            assertTrue(error.getMessage().startsWith("invalid extra-body key '" + key + "'"), error.getMessage());
        }
    }

    @Test
    @DisplayName("A key that would override a field managed by Refinr is refused: same path, parent or child")
    void reservedPathsAreRefused() {
        Set<String> reserved = Set.of("messages", "generationConfig.maxOutputTokens");

        for (String key : new String[] {"messages", "messages.0", "generationConfig", "generationConfig.maxOutputTokens",
                "generation_config.max_output_tokens", "Messages"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ExtraBody.of(Map.of(key, "x")).checkReserved(reserved), key);
            assertTrue(error.getMessage().startsWith("extra-body key '" + key + "' overrides the field '"), error.getMessage());
            assertTrue(error.getMessage().endsWith("' managed by Refinr"), error.getMessage());
        }
        // Un champ voisin d'un champ reserve reste permis
        assertDoesNotThrow(() -> ExtraBody.of(Map.of("generationConfig.thinkingConfig.thinkingLevel", "low")).checkReserved(reserved));
        assertDoesNotThrow(() -> ExtraBody.of(Map.of("messagesExtra", "x")).checkReserved(reserved));
    }

    @Test
    @DisplayName("Matching a provider field path to a sent path ignores case and underscores, on segment boundaries")
    void pathMatching() {
        assertTrue(ExtraBody.overlaps("generation_config.thinking_config.thinking_level", "generationConfig.thinkingConfig.thinkingLevel"));
        assertTrue(ExtraBody.overlaps("thinking", "thinking.budget_tokens"));
        assertTrue(ExtraBody.overlaps("generation_config.thinking_config", "generationConfig.thinkingConfig.thinkingLevel"));
        assertFalse(ExtraBody.overlaps("think", "thinking"));
        assertFalse(ExtraBody.overlaps("generationConfig.temperature", "generationConfig.thinkingConfig"));
    }
}
