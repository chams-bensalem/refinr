package fr.heliocraft.refinr.domain.service;

import java.util.List;
import java.util.stream.Collectors;

/** Normalisation des labels produits par l'analyse. */
public final class LabelNormalizer {

    private LabelNormalizer() {}

    /**
     * Espaces remplaces par des tirets, caracteres speciaux supprimes, minuscules, vides et doublons retires.
     * Les labels de verdict sont exclus : ils sont reserves a la story et a son verdict explicite, sinon un Epic
     * ou une Subtask "AI Ready" serait pris pour une story codable par l'agent.
     */
    public static List<String> normalize(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }

        return labels.stream()
            .map(label -> label
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-zA-Z0-9-_]", "")
                .toLowerCase())
            .filter(label -> !label.isEmpty())
            .filter(label -> !ReadinessPolicy.isReservedLabel(label))
            .distinct()
            .collect(Collectors.toList());
    }
}
