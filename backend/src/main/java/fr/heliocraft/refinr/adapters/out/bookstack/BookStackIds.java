// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.bookstack;

import java.util.regex.Pattern;

/**
 * Formats BookStack (controles locaux, sans appel) : id de page numerique, et livre designe par son id numerique ou
 * son slug (minuscules, chiffres, {@code -} et {@code _}, tel que BookStack les produit).
 */
public final class BookStackIds {

    private static final Pattern NUMERIC_ID = Pattern.compile("^[0-9]{1,18}$");
    private static final Pattern BOOK_SLUG = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,254}$");

    private BookStackIds() {}

    /** Vrai si {@code value} est un id de page BookStack (numerique) ; null : faux. */
    public static boolean isPageId(String value) {
        return value != null && NUMERIC_ID.matcher(value).matches();
    }

    /** Vrai si {@code value} designe un livre : id numerique ou slug ; null : faux. */
    public static boolean isBook(String value) {
        return value != null && (NUMERIC_ID.matcher(value).matches() || BOOK_SLUG.matcher(value).matches());
    }

    /** Vrai si {@code value} est un id de livre numerique (sinon, un slug a resoudre). */
    static boolean isNumericId(String value) {
        return value != null && NUMERIC_ID.matcher(value).matches();
    }
}
