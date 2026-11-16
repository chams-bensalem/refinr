// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.domain.model.Language;

/**
 * Langue de sortie d'une analyse, d'un verdict ou d'une reevaluation. Priorite : la langue demandee dans la requete,
 * puis la propriete {@code refinr.llm.output-language} si elle ne vaut pas {@code auto}, puis la detection
 * francais / anglais du texte ({@link LanguageDetector}), francais par defaut.
 */
public final class OutputLanguagePolicy {

    public static final String PROPERTY = "refinr.llm.output-language";
    public static final String AUTO = "auto";

    /** Mode auto : ni langue configuree ni langue demandee, detection seule. */
    public static final OutputLanguagePolicy DETECT = new OutputLanguagePolicy(null);

    private final Language configured;

    private OutputLanguagePolicy(Language configured) {
        this.configured = configured;
    }

    /**
     * @param value {@code auto} ou un code ISO 639-1 (casse ignoree)
     * @throws IllegalStateException si la valeur n'est ni {@code auto} ni un code ISO 639-1 connu (refus au demarrage)
     */
    public static OutputLanguagePolicy fromProperty(String value) {
        if (value == null || AUTO.equals(value)) {
            return DETECT;
        }
        try {
            return new OutputLanguagePolicy(Language.of(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(PROPERTY + " must be auto or an ISO 639-1 code (e.g. fr, en)");
        }
    }

    /**
     * @param requested langue de la requete, ou null si elle n'en precise pas
     * @param text      texte dont on detecte la langue en mode auto (document, ou titre et description d'une story)
     */
    public Language resolve(Language requested, String text) {
        if (requested != null) return requested;
        if (configured != null) return configured;
        return LanguageDetector.detect(text);
    }
}
