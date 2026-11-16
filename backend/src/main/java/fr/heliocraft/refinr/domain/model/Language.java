// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Langue de sortie (backlog, raisons du verdict) : un code ISO 639-1 connu de {@link Locale}, en minuscules.
 * La valeur recue n'est jamais recopiee dans un prompt : seul {@link #displayName(Locale)} y est injecte.
 */
public record Language(String code) {

    /** Message d'erreur commun (REST, configuration), sans recopier la valeur refusee. */
    public static final String INVALID = "must be an ISO 639-1 code (e.g. fr, en)";

    // Avant FR et EN : le constructeur les utilise pendant l'initialisation de la classe
    private static final Pattern TWO_LETTERS = Pattern.compile("[a-z]{2}");
    private static final Set<String> ISO_639_1 = Set.of(Locale.getISOLanguages());

    public static final Language FR = new Language("fr");
    public static final Language EN = new Language("en");

    public Language {
        if (code == null || !TWO_LETTERS.matcher(code).matches() || !ISO_639_1.contains(code)) {
            throw new IllegalArgumentException("language " + INVALID);
        }
    }

    /**
     * Code saisi par un utilisateur : mis en minuscules (EN → en), sans autre normalisation (pas de suppression des
     * espaces) ; {@code xx}, {@code fr-FR}, une chaine vide ou un texte arbitraire sont refuses.
     *
     * @throws IllegalArgumentException si ce n'est pas un code ISO 639-1 connu
     */
    public static Language of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("language " + INVALID);
        }
        return new Language(raw.toLowerCase(Locale.ROOT));
    }

    public boolean isFrench() {
        return FR.equals(this);
    }

    /** Nom de la langue dans la langue {@code in} (ex. {@code de} en anglais : German). */
    public String displayName(Locale in) {
        return new Locale.Builder().setLanguage(code).build().getDisplayLanguage(in);
    }
}
