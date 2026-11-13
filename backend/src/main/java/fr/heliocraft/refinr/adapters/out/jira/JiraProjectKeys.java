// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.jira;

import java.util.regex.Pattern;

/** Format d'une cle de projet Jira (ex. {@code PROJ}) : une majuscule puis 1 a 9 majuscules ou chiffres. */
public final class JiraProjectKeys {

    private static final Pattern KEY = Pattern.compile("^[A-Z][A-Z0-9]{1,9}$");

    private JiraProjectKeys() {
    }

    /** Vrai si {@code key} a le format d'une cle de projet Jira ; null ou vide : faux. */
    public static boolean isValid(String key) {
        return key != null && KEY.matcher(key).matches();
    }
}
