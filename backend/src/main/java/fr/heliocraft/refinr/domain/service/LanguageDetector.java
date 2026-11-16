// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.domain.model.Language;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detection deterministe francais / anglais d'un texte, sans dependance : compte des mots outils de chaque langue
 * (blocs de code, code en ligne et URL retires), leger bonus aux lettres accentuees pour le francais. L'anglais gagne
 * avec au moins {@value #MIN_WORDS} mots reconnus et un score au moins {@value #MIN_RATIO} fois celui du francais ;
 * sinon francais (langue historique du produit, et repli en cas de signal insuffisant).
 * <p>
 * Les mots-cles Given / When / Then, presents dans les criteres des stories francaises, ne comptent pas.
 */
public final class LanguageDetector {

    static final int MIN_WORDS = 8;
    static final double MIN_RATIO = 1.5;
    /** Quatre lettres accentuees valent un mot outil francais. */
    static final double ACCENTS_PER_WORD = 4.0;

    private static final Set<String> FRENCH = Set.of("le", "la", "les", "des", "du", "de", "un", "une", "et", "est",
            "sont", "pour", "dans", "que", "qui", "pas", "sur", "avec", "par", "au", "aux", "ce", "cette", "ces", "il",
            "elle", "ne", "se", "son", "sa", "ses", "leur", "leurs", "ou", "mais", "si", "en", "doit", "peut", "plus",
            "tout", "tous", "chaque", "lorsque", "quand", "puis", "sans", "selon", "entre", "nous", "vous");
    private static final Set<String> ENGLISH = Set.of("the", "and", "of", "to", "is", "are", "for", "in", "that",
            "with", "not", "be", "this", "it", "by", "from", "as", "at", "an", "or", "which", "will", "can", "has",
            "have", "should", "must", "its", "their", "they", "each", "into", "only", "than", "there", "after",
            "before", "his", "her", "was", "were", "been", "who", "what", "does", "do");

    private static final Pattern FENCED = Pattern.compile("```.*?```", Pattern.DOTALL);
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`]*`");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern WORD = Pattern.compile("\\p{L}+");
    private static final Pattern ACCENT = Pattern.compile("[éèêëàâîïôûùüçœ]");

    private LanguageDetector() {}

    public static Language detect(String text) {
        if (text == null || text.isBlank()) {
            return Language.FR;
        }
        String cleaned = URL.matcher(INLINE_CODE.matcher(FENCED.matcher(text).replaceAll(" ")).replaceAll(" "))
                .replaceAll(" ").toLowerCase(Locale.ROOT);
        int fr = 0;
        int en = 0;
        Matcher words = WORD.matcher(cleaned);
        while (words.find()) {
            String w = words.group();
            if (FRENCH.contains(w)) fr++;
            else if (ENGLISH.contains(w)) en++;
        }
        int accents = 0;
        for (Matcher m = ACCENT.matcher(cleaned); m.find(); ) accents++;
        double frScore = fr + accents / ACCENTS_PER_WORD;
        if (en >= MIN_WORDS && en >= MIN_RATIO * frScore) {
            return Language.EN;
        }
        return Language.FR; // francais gagnant, signal insuffisant ou indecis : francais
    }
}
