// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

/** Resultat d'une recherche de documents source : identifiant (a passer a l'analyse), titre et lien de consultation. */
public record DocumentSummary(String id, String title, String url) {}
