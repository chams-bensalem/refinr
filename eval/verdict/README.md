<!-- SPDX-FileCopyrightText: 2026 Chams Bensalem -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Jeu de référence du verdict `ai-ready` (E0)

Mesure de la justesse et de la stabilité du verdict `ai-ready` / `validation-necessaire` de Refinr contre des
étiquettes de référence. Rejouée avant chaque release et après chaque changement de prompt ; **jamais dans `mvn test`
ni dans la CI** (`run` appelle le modèle : quota et argent).

> **Référence étiquetée par une IA, pas par un humain.** Les 50 étiquettes de `labels.csv` ont été posées par Claude
> (rôle de PM/PO, `labeled_by` = `claude-po`, 2026-11-16), sans accès à `candidates-notes.md`. Elles sont la seule
> référence de la mesure. Les 9 étiquettes `hesitant` seront relues par le mainteneur.
>
> **Circularité** : mesurer un modèle Claude contre des étiquettes posées par Claude mesure en partie l'accord de Claude
> avec lui-même. Elle vaut pour **les deux cibles Anthropic** : `anthropic` (modèle économique, ex. Haiku) **et**
> `anthropic-check` (modèle haut de gamme, ex. Opus : c'est aussi Claude). Leurs résultats sont **indicatifs** ; ceux
> des autres fournisseurs se lisent comme un accord avec une référence IA.

## Fichiers

| fichier | rôle |
|---|---|
| `stories.jsonl` | 50 stories, une par ligne : `id`, `title`, `description`, `acceptanceCriteria` (exactement les champs envoyés au modèle de verdict) |
| `labels.csv` | étiquettes de référence v1 (`id,label,reason,confidence,labeled_by,labeled_on`), jamais modifiées |
| `labels-adjudication.csv` | arbitrage : étiquettes corrigées (`id,label_v1,confidence_v1,label,confidence,reason,decided_by,decided_on`), appliquées par `check` et `summarize` |
| `holdout.jsonl`, `holdout-labels.csv` | jeu de contrôle : 15 stories écrites après la mesure v2 et les prompts v3, étiquetées en aveugle ; mesuré avec le jeu d'origine, rapporté à part, **jamais arbitré** |
| `holdout-notes.md` | intention de l'auteur du jeu de contrôle, à lire après l'étiquetage |
| `holdout.en.jsonl` | contrôle 1 traduit fidèlement en anglais (mêmes ids v051 à v065), étiquettes de `holdout-labels.csv` reprises telles quelles ; mesuré à part avec `--set en` |
| `candidates-notes.md` | provenance (`generated` / `synthetic`) et intention de l'auteur des stories ; ne sert pas à la mesure |
| `prices.csv` | tarifs par million de tokens, datés, avec la source ; une ligne par tranche (`max_input_tokens` : tokens d'entrée de la requête au plus, vide = sans limite) ; revérifiés avant chaque mesure payante |
| `results/<date>-<fournisseur>-<modèle>/` | `meta.md`, `runs.csv` (un verdict par story et par exécution), `calls.csv` (tokens, coût), `summary.md` |
| `.state/` | compteurs d'appels par jour et registre de dépense par phase ; local, ignoré par git |

Aucune donnée confidentielle : stories synthétiques, ou générées par Refinr à partir des documents de test des spikes et
reprises sans retouche (vérifiées une à une). Les résultats ne contiennent ni prompt ni réponse brute : verdicts,
raisons, tokens et coût.

## Étiquettes (`labels.csv`)

| colonne | valeurs |
|---|---|
| `label` | `ready` (un agent de code peut implémenter la story sans décision humaine) ou `not-ready` |
| `reason` | une phrase, entre guillemets si elle contient une virgule |
| `confidence` | `sure` ou `hesitant` |
| `labeled_by` | auteur de l'étiquette (`claude-po` : Claude, IA) |
| `labeled_on` | date `AAAA-MM-JJ` |

Règle : celle du produit (`READINESS_RULES`). Une story ajoutée plus tard reçoit un nouvel `id`, jamais réutilisé.

**Arbitrage** : une étiquette corrigée va dans `labels-adjudication.csv`, jamais dans `labels.csv` ; `label_v1` et
`confidence_v1` doivent reprendre l'étiquette d'origine (vérifié). `summarize` rapporte aussi le résultat avant
arbitrage. Les trois corrections du 2026-11-16 (v004, v020, v033) ont été faites **après avoir vu les réponses des
modèles** : elles biaisent la mesure en leur faveur.

## Métriques et seuil (validés)

- Verdict retenu par story = majorité sur les exécutions ; égalité = `ai-ready` (le cas dangereux) ; le repli
  (`verdict indisponible`) est compté à part, jamais comme un verdict.
- **Seuil bloquant, sur les étiquettes `sure` seulement** : 0 faux `ai-ready` au verdict majoritaire, et aucune story
  `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 (part > 20 %).
- Étiquettes `hesitant` : rapportées à part, non bloquantes.
- Faux `validation-necessaire` ≤ 25 %, sur les étiquettes `ready` / `sure` (les `ready` / `hesitant` sont rapportées à
  part ; règle fixée après la première mesure) ; stabilité (même verdict à chaque exécution) ≥ 90 %.
- Rapportés aussi : matrices de confusion, liste des désaccords et des instabilités.

## Lancer

Depuis la racine du dépôt (JDK 25, Maven) ; réglages : `backend/src/main/resources/application-example.properties`
(défauts de production) + `.env` à la racine.

```bash
scripts/eval-verdict.sh check                                  # valide stories.jsonl et labels.csv
scripts/eval-verdict.sh describe env                           # réglages effectifs, aucun appel
scripts/eval-verdict.sh estimate env 5                         # appels et tokens estimés, aucun appel
scripts/eval-verdict.sh estimate anthropic 5 --assume-model claude-haiku-5-5   # coût estimé, aucun appel
scripts/eval-verdict.sh run env 5                              # APPELLE LE MODÈLE
scripts/eval-verdict.sh summarize eval/verdict/results/<dossier> [--baseline eval/verdict/results/<autre>]   # jeu d'origine (+ comparaison) et jeu de contrôle à part
scripts/eval-verdict.sh run anthropic 5 --set en               # APPELLE LE MODÈLE : jeu anglais seul (holdout.en.jsonl)
scripts/eval-verdict.sh summarize eval/verdict/results/<dossier>-en --set en
scripts/eval-verdict.sh generate anthropic eval/generation/specs/<spec>.md --out eval/generation/results/<dossier>   # APPELLE LE MODÈLE : génération du backlog
```

Cibles :
- `env` : le verdict de production configuré dans `.env` (`LLM_VERDICT_*`, sinon le rôle analyse `LLM_ANALYSIS_*`).
- `anthropic` / `anthropic-check` : rôle verdict remplacé par `EVAL_ANTHROPIC_API_KEY` et `EVAL_ANTHROPIC_MODEL`
  (modèle économique, volume) ou `EVAL_ANTHROPIC_CHECK_MODEL` (haut de gamme, vérification), sans URL, température ni
  paramètre optionnel du verdict de `.env`. Variables à mettre dans `.env` ; les clés ne sont jamais lues ni affichées
  par le programme (seule leur présence est indiquée).

Garde-fous de `run` :
- refus en CI (variables `CI`, `GITHUB_ACTIONS`, `GITLAB_CI`…) et sous Surefire : `VerdictEval` n'est pas un test
  (aucune annotation JUnit, nom hors des motifs de Surefire) et `VerdictEvalGuardTest` le vérifie dans `mvn test` ;
- `max-retries=0` (une requête par appel ; réparations et nouvelle demande des verdicts manquants comprises) ;
  pause de 15 s entre deux lots (`--pause`) ;
- plafond de requêtes par jour calendaire et par fournisseur : 100 pour Anthropic, 40 pour Gemini et les autres
  (`--cap`, compteur daté dans `.state/`) ; une story pas encore mesurée est rapportée « non mesurée », pas comme un repli ;
- budget de 20 USD par phase (`--budget`, `--phase`) pour un modèle tarifé : avant chaque requête, refus si dépense
  cumulée + coût maximal de la requête (entrée estimée + limite de sortie) dépasse le budget ; coût réel calculé avec
  l'usage renvoyé par le fournisseur, tranche par tranche **et requête par requête** (Haiku 5.5 : 0,10 / 0,50 USD
  jusqu'à 100 000 tokens d'entrée, 0,50 / 2,50 au-delà) ; un modèle absent de `prices.csv` est refusé ;
- lot de verdicts coupé par la limite de tokens de sortie : Refinr le coupe en deux et redemande chaque moitié, jusqu'à
  la story seule (comme en production) ; chaque appel supplémentaire passe par le plafond et le budget, et le pire cas
  de `estimate` en tient compte ;
- `caffeinate -i` (macOS, quand il est disponible) empêche la mise en veille pendant la mesure ; un appel dont la durée
  murale dépasse le délai configuré de plus de 30 s est marqué `suspended` dans `calls.csv` (machine en veille) et
  n'est jamais compté comme un délai du fournisseur ;
- arrêt sur HTTP 400 / 401 / 403 / 404 / 429 et après 2 échecs consécutifs 503 / 529 ou délai dépassé : fichier
  `STOP-<raison>` dans le dossier de résultats (à supprimer pour reprendre) ;
- reprise : relancer la même commande avec `--out <dossier>` ; les lots déjà mesurés sont sautés (ordre mélangé
  reproductible : graine 20261116 + numéro d'exécution).

## Prompts et langue

Les prompts mesurés sont ceux de production : `backend/src/main/resources/llm/prompts/prompts.fr.md` et
`prompts.en.md` (version `VERDICT_PROMPT_VERSION`, relevée dans chaque `meta.md`). La langue de sortie de chaque story
suit la règle de production sans langue demandée : propriété `refinr.llm.output-language` de `.env`, puis détection
français / anglais sur le titre, la description et les critères ; les lots sont homogènes par langue.
