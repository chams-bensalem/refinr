// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import java.util.regex.Pattern;

/**
 * Chemins des jobs ({@code /api/v1/jobs/{id}}) : format de l'identifiant et masquage dans les journaux. Sans cle
 * d'API, l'identifiant complet suffit a lire le resultat d'un job : les journaux n'en gardent que les 8 premiers
 * caracteres.
 */
public final class JobPaths {

    static final String BASE = "/api/v1/jobs";
    /** Identifiant d'un job : UUID aleatoire en minuscules. */
    static final Pattern JOB_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern JOB_ID_IN_PATH = Pattern.compile("(?i)(/jobs/)([0-9a-f]{8})[0-9a-f-]+");

    private JobPaths() {}

    static boolean isJobId(String id) {
        return id != null && JOB_ID.matcher(id).matches();
    }

    /** Chemin a journaliser : identifiant de job ramene a ses 8 premiers caracteres ({@code /api/v1/jobs/1a2b3c4d…}). */
    public static String forLogs(String path) {
        return path == null ? null : JOB_ID_IN_PATH.matcher(path).replaceAll("$1$2…");
    }
}
