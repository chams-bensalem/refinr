// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parametres propres a un fournisseur ({@code refinr.llm.<role>.extra-body.*}), ajoutes tels quels au corps de la
 * requete par les trois clients. Une cle est un chemin pointe : {@code generationConfig.thinkingConfig.thinkingLevel}
 * devient un objet imbrique, fusionne avec les objets deja presents dans le corps.
 * <p>
 * Refinr ne connait aucun de ces parametres et n'en deduit rien : il refuse seulement, au demarrage, une cle qui
 * ecraserait un champ qu'il gere lui-meme ({@link #checkReserved}). Pour comparer des chemins, la casse et les
 * {@code _} sont ignores ({@code generation_config} et {@code generationConfig} designent le meme champ chez Google).
 */
public final class ExtraBody {

    public static final ExtraBody NONE = new ExtraBody(Map.of());

    private final Map<String, Object> values;

    private ExtraBody(Map<String, Object> values) {
        this.values = values;
    }

    /**
     * @param values chemin pointe -> valeur deja typee (chaine, nombre, booleen, objet ou liste JSON) ; null = aucun
     * @throws IllegalArgumentException cle mal formee, ou deux cles qui se recouvrent ({@code a} et {@code a.b})
     */
    public static ExtraBody of(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return NONE;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank() || key.startsWith(".") || key.endsWith(".") || key.contains("..")) {
                throw new IllegalArgumentException("invalid extra-body key '" + key + "': empty path segment");
            }
            for (String previous : copy.keySet()) {
                if (overlaps(previous, key)) {
                    throw new IllegalArgumentException("extra-body keys '" + previous + "' and '" + key
                            + "' overlap: keep only one of them");
                }
            }
            copy.put(key, value);
        });
        return new ExtraBody(Collections.unmodifiableMap(copy));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** Chemins envoyes, dans l'ordre de la configuration. */
    public Set<String> paths() {
        return values.keySet();
    }

    /**
     * @throws IllegalArgumentException une cle egale a un chemin reserve, parente ou enfant d'un chemin reserve
     */
    public void checkReserved(Collection<String> reserved) {
        for (String key : values.keySet()) {
            for (String field : reserved) {
                if (overlaps(key, field)) {
                    throw new IllegalArgumentException("extra-body key '" + key + "' overrides the field '" + field
                            + "' managed by Refinr");
                }
            }
        }
    }

    /** Copie du corps avec ces parametres fusionnes ; le corps recu n'est pas modifie. */
    public Map<String, Object> applyTo(Map<String, Object> body) {
        if (values.isEmpty()) {
            return body;
        }
        Map<String, Object> result = new LinkedHashMap<>(body);
        values.forEach((key, value) -> {
            List<String> segments = List.of(key.split("\\."));
            Map<String, Object> node = result;
            for (String segment : segments.subList(0, segments.size() - 1)) {
                Object existing = node.get(segment);
                Map<String, Object> child = new LinkedHashMap<>();
                if (existing instanceof Map<?, ?> map) {
                    map.forEach((k, v) -> child.put(String.valueOf(k), v));
                } else if (existing != null) {
                    // Inatteignable tant que les champs geres par Refinr sont reserves
                    throw new IllegalStateException("extra-body key '" + key + "' crosses a non-object field");
                }
                node.put(segment, child);
                node = child;
            }
            node.put(segments.getLast(), value);
        });
        return result;
    }

    /**
     * Deux chemins designent le meme champ, ou l'un contient l'autre (sur des limites de segment), casse et
     * {@code _} ignores.
     */
    static boolean overlaps(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        return x.equals(y) || x.startsWith(y + ".") || y.startsWith(x + ".");
    }

    private static String normalize(String path) {
        return path.replace("_", "").toLowerCase(Locale.ROOT);
    }
}
