// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest.dto;

/**
 * Cas de test d'une story (chaine C2) dans le contrat REST : sortie de l'apercu, entree de create-jira (controlee par
 * {@code TestCasePolicy}, 400 sinon).
 */
public record TestCaseDto(String title, String criterion, String kind, String given, String when, String then) {}
