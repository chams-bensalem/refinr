// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.confluence;

import java.util.regex.Pattern;

/** Formats Confluence : cle d'espace et identifiant de page (listes blanches, avant tout appel ou CQL). */
public final class ConfluenceIds {

    // Cle d'espace Confluence : alphanumerique, ou espace personnel "~" + identifiant de compte (ex. ~557058:uuid)
    private static final Pattern SPACE_KEY = Pattern.compile("^~?[A-Za-z0-9][A-Za-z0-9:_-]{0,254}$");

    // Identifiant de page Confluence : numerique
    private static final Pattern PAGE_ID = Pattern.compile("^[0-9]{1,20}$");

    private ConfluenceIds() {}

    /** Vrai si {@code value} est une cle d'espace Confluence ({@code SPACE}, {@code ~557058:uuid}) ; null : faux. */
    public static boolean isSpaceKey(String value) {
        return value != null && SPACE_KEY.matcher(value).matches();
    }

    /** Vrai si {@code value} est un identifiant de page Confluence (numerique) ; null : faux. */
    public static boolean isPageId(String value) {
        return value != null && PAGE_ID.matcher(value).matches();
    }
}
