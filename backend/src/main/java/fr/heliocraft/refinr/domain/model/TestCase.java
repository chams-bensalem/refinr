// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

/**
 * Cas de test d'une story (chaine C2), tire d'un critere d'acceptation.
 *
 * @param criterion critere couvert, {@code AC1}, {@code AC2}… (numerotation de la description)
 * @param kind      {@code nominal}, {@code error} ou {@code edge}
 */
public record TestCase(String title, String criterion, String kind, String given, String when, String then) {}
