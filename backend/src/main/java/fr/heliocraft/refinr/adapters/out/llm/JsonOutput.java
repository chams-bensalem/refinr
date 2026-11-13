// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import java.util.regex.Pattern;

/** Extrait l'objet JSON du texte d'un modele : bloc de reflexion, balises markdown et texte autour ignores. */
final class JsonOutput {

    private static final Pattern THINK = Pattern.compile("(?s)<think>.*?</think>");

    private JsonOutput() {}

    /** Texte de l'objet JSON (du premier '{' au dernier '}'), ou le texte nettoye s'il n'en contient pas. */
    static String extract(String text) {
        String cleaned = THINK.matcher(text == null ? "" : text).replaceAll("").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        return start >= 0 && end > start ? cleaned.substring(start, end + 1) : cleaned;
    }
}
