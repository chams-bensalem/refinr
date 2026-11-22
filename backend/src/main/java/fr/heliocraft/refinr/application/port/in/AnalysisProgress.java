// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

/**
 * Suivi des etapes d'une analyse : appele par les cas d'usage au debut de chaque etape. Les appels synchrones passent
 * {@link #NONE}. Une implementation peut lever une exception pour arreter l'analyse avant l'etape suivante (job
 * interrompu ou expire), jamais au milieu d'une etape.
 */
@FunctionalInterface
public interface AnalysisProgress {

    AnalysisProgress NONE = step -> { };

    void step(JobView.Step step);
}
