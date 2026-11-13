// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

/** Gestionnaire de tickets actif (un seul par instance, choisi par configuration) : nom et format des projets. */
public interface DescribeTracker {

    /** Nom affichable du gestionnaire de tickets actif. */
    String displayName();

    /** Vrai si {@code projectId} a le format d'un projet du gestionnaire actif (null ou vide : faux). */
    boolean acceptsProjectId(String projectId);
}
