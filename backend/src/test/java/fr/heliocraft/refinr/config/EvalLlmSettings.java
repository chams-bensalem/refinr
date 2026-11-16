// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.adapters.out.llm.LlmSettings;

/**
 * Acces, pour le programme de mesure du verdict ({@code fr.heliocraft.refinr.eval.VerdictEval}), aux reglages que
 * {@link LlmConfiguration} calcule au demarrage. Hors des tests automatiques.
 */
public final class EvalLlmSettings {

    private EvalLlmSettings() {}

    /** Reglages du client de verdict, comme en production : role verdict s'il est configure, sinon role analyse. */
    public static LlmSettings productionVerdict(LlmProperties properties) {
        return properties.verdict().isConfigured()
                ? LlmConfiguration.verdictSettings(properties.verdict())
                : LlmConfiguration.analysisSettings(properties.analysis());
    }

    /** Reglages du role verdict seul (cibles qui le remplacent entierement). */
    public static LlmSettings verdictRole(LlmProperties properties) {
        return LlmConfiguration.verdictSettings(properties.verdict());
    }

    /** Reglages du role analyse (generation du backlog). */
    public static LlmSettings analysisRole(LlmProperties properties) {
        return LlmConfiguration.analysisSettings(properties.analysis());
    }

    /** Vrai si un fournisseur de verdict distinct est configure (verdict separe apres la generation). */
    public static boolean separateVerdict(LlmProperties properties) {
        return properties.verdict().isConfigured();
    }

    public static int maxRepairAttempts(LlmProperties properties) {
        Integer repairs = properties.maxRepairAttempts();
        return repairs != null ? repairs : LlmConfiguration.DEFAULT_MAX_REPAIR_ATTEMPTS;
    }
}
