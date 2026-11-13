// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.jira;

import fr.heliocraft.refinr.domain.model.TrackerContent;

import java.util.Map;

/** Description d'origine d'une issue Jira au format ADF (Atlassian Document Format), reecrite telle quelle. */
record JiraAdfContent(Map<String, Object> document) implements TrackerContent {
}
