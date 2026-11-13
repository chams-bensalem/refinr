// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

/**
 * Contenu natif d'un ticket (description riche : images, tableaux, liens), opaque pour le domaine. Seul
 * l'adaptateur du gestionnaire de tickets qui l'a lu sait l'interpreter et le reecrire tel quel.
 */
public interface TrackerContent {
}
