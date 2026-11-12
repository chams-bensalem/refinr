package fr.heliocraft.refinr.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LabelNormalizerTest {

    @Test
    @DisplayName("Spaces become dashes, special characters are dropped, everything is lower case")
    void normalizesLabels() {
        assertEquals(List.of("spring-boot", "c", "kafka_streams"),
            LabelNormalizer.normalize(List.of("Spring Boot", "C#", "Kafka_Streams")));
    }

    @Test
    @DisplayName("Empty labels and duplicates are removed, order is kept")
    void removesEmptyAndDuplicateLabels() {
        assertEquals(List.of("api", "backend"), LabelNormalizer.normalize(List.of("API", "!!!", "backend", "api")));
    }

    @Test
    @DisplayName("Readiness labels are reserved and never kept from the analysis")
    void dropsReservedLabels() {
        assertEquals(List.of("backend"),
            LabelNormalizer.normalize(List.of("AI Ready", "validation-necessaire", "backend")));
    }

    @Test
    @DisplayName("Null or empty input yields an empty list; the result can be extended")
    void handlesNullAndEmpty() {
        assertEquals(List.of(), LabelNormalizer.normalize(null));
        assertEquals(List.of(), LabelNormalizer.normalize(new ArrayList<>()));
        List<String> result = LabelNormalizer.normalize(Arrays.asList("backend"));
        assertDoesNotThrow(() -> result.add("ai-ready"));
    }
}
