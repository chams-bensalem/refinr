package fr.heliocraft.refinr.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Etat d'une dependance externe (source de documents, gestionnaire de tickets, fournisseur de modele).
 * Les details ne contiennent jamais de secret ni d'URL : fournisseur, modele, cause courte (ex. "HTTP 401").
 */
public record ComponentHealth(String name, boolean up, Map<String, String> details) {

    public ComponentHealth {
        details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static ComponentHealth up(String name, Map<String, String> details) {
        return new ComponentHealth(name, true, details);
    }

    public static ComponentHealth down(String name, String error, Map<String, String> details) {
        Map<String, String> all = new LinkedHashMap<>(details);
        all.put("error", error);
        return new ComponentHealth(name, false, all);
    }
}
