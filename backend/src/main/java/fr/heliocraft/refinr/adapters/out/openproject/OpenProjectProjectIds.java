// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.openproject;

import java.util.regex.Pattern;

/**
 * Format d'un identifiant de projet OpenProject (faits verifies en v1.1 phase 0, cf. DECISIONS.md) : identifiant
 * (slug) de 1 a 100 caracteres parmi minuscules, chiffres, {@code -} et {@code _}, ou id numerique (un identifiant
 * entierement numerique est refuse par OpenProject, il designe donc un id). Les mots reserves (ex. {@code new})
 * restent signales par le 422 d'OpenProject. Classe utilitaire sans bean : l'adaptateur arrive en phase 2.
 */
public final class OpenProjectProjectIds {

    private static final Pattern IDENTIFIER_OR_ID = Pattern.compile("^[a-z0-9_-]{1,100}$");

    private OpenProjectProjectIds() {
    }

    /** Vrai si {@code id} est un identifiant ou un id numerique de projet OpenProject ; null ou vide : faux. */
    public static boolean isValid(String id) {
        return id != null && IDENTIFIER_OR_ID.matcher(id).matches();
    }
}
