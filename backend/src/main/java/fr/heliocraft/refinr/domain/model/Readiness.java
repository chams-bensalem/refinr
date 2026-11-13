// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.model;

/** Verdict de preparation d'une story pour un agent de code. */
public record Readiness(boolean aiReady, String aiReadyReason) {}
