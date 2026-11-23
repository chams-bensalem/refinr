<!-- SPDX-FileCopyrightText: 2026 Chams Bensalem -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Prompt 3, verdict-v3.3 (fr), copie figée pour la mesure R3 (comparaison avec la version de production)

```
MISSION : cette User Story a été jugée non prête. L'utilisateur apporte une précision. Réévalue la story en en tenant compte.

{{READINESS}}

La précision de l'utilisateur fait foi sur ce qu'elle tranche. Ne suppose rien d'autre. Si une autre question reste ouverte, la story reste non prête et aiReadyReason donne cette question.

STORY :
Titre : {{TITLE}}
Description : {{DESCRIPTION}}
{{IF_QUESTION}}Question bloquante précédente : {{PREVIOUS_QUESTION}}

PRÉCISION DE L'UTILISATEUR :
{{PRECISION}}

Rédige aiReadyReason en {{LANGUAGE}}.

Réponds UNIQUEMENT par le JSON : {"aiReady":false,"aiReadyReason":""}
```
