<!-- SPDX-FileCopyrightText: 2026 Chams Bensalem -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Mesure du prompt 3 (réévaluation), phase B3

Première mesure du prompt 3. Jamais en CI : `run` appelle le modèle (`scripts/eval-verdict.sh reassess …`).

## Données

- 20 stories `not-ready / sure` du jeu E0 (`eval/verdict/stories.jsonl`, `labels.csv`).
- `precisions.jsonl` / `precisions.md` : pour chaque story, la question précédente (telle qu'une ligne de verdict la
  porterait), une précision **qui répond** et une précision **hors sujet**. Relues et validées par le mainteneur avant
  la mesure.
- Description envoyée, construite comme Refinr l'écrit dans le ticket (`StoryMarkdown.description` : récit, critères,
  ligne de verdict `validation-necessaire` portant la question précédente).

## Versions comparées

- **Avant** (`verdict-v3.2`) : description complète, ligne de verdict comprise ; pas de champ de question.
- **Après** (`verdict-v3.3`, B3) : description sans ses lignes de verdict (`StoryMarkdown.forModel`) ; dernière
  question ouverte dans son champ (`StoryMarkdown.lastOpenQuestion`).
- Même modèle (Haiku, cible `anthropic`), langue française, 1 appel par story, par précision et par version : 80 appels.

## Critères (fixés avant le premier appel)

Exigences sur les données :
- la précision qui répond couvre tous les points bloquants de la story selon les labels ;
- la précision hors sujet est plausible : un détail non bloquant de la même story (nom, format, libellé).

| # | Critère |
|---|---|
| i | Avec la précision qui répond, au moins 80 % des stories deviennent prêtes (16 sur 20) |
| ii | Avec la précision hors sujet, aucune story ne devient prête |
| iii | « Après » ≥ « avant » moins 1 story sur le critère i ; « après » ne fait pas pire que « avant » sur le critère ii |
| iv | 100 % de réponses valides |
| v | Au moins 90 % des stories qui restent non prêtes ont un `aiReadyReason` terminé par « ? » |

Les critères i, ii et v s'appliquent à la version « après » ; iii compare les deux ; iv aux deux. Si un critère échoue :
STOP, sans retoucher les prompts.
