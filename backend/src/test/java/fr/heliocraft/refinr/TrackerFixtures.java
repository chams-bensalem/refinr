// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr;

/** Textes reels relus dans les gestionnaires, partages par les tests (domaine, adaptateurs, services). */
public final class TrackerFixtures {

    private TrackerFixtures() {}

    /** Texte brut reel d'OpenProject apres enregistrement par son editeur (essai reel C2, work package #352). */
    public static final String OPENPROJECT_EDITED = "**As a** gestionnaire  \n**I want** consulter un tableau de bord sur l&#x27;occupation des salles  \n**So that** suivre l&#x27;usage des salles\n\n### Description\n\nLe gestionnaire dispose d&#x27;un tableau de bord sur l&#x27;occupation des salles. La spécification ne définit ni les indicateurs affichés, ni la période couverte, ni ce que signifie « intuitif ».\n\n### Acceptance Criteria\n\n*   **AC1:** Given un gestionnaire connecté, When il ouvre le tableau de bord, Then l&#x27;occupation des salles est affichée.\n    \n*   **AC2:** Given une salle sans réservation sur la période affichée, When le gestionnaire consulte le tableau de bord, Then cette salle apparaît comme inoccupée.\n    \n\nAI readiness (validation-necessaire): Quels indicateurs précis le tableau de bord d&#x27;occupation doit-il afficher et sur quelle période ?\n\n**Labels:** frontend, backend, api\n\nNote du PO : affichage par semaine en priorité.";
}
