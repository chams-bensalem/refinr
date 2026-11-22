# Mesure du verdict — anthropic (claude-haiku-5-5)

- Début : 2026-11-16T18:35:35.671431Z ; cible `anthropic` ; `describe()` : `anthropic (claude-haiku-5-5)` ; paramètres optionnels envoyés : aucun
- Prompt `verdict-v3.2`, lots de 10 stories (production), 2 réparation(s) au plus, `max-retries=0`, ordre mélangé
  par exécution (graine 20261116 + numéro d'exécution) ; délai PT1M30S, 4096 tokens de sortie au plus
- **Référence étiquetée par une IA** (Claude, `labeled_by` = [claude-po]), pas par un humain.
- **Circularité** : modèle Claude évalué contre des étiquettes posées par Claude ; résultats indicatifs.
- Jeu mesuré : `holdout.en.jsonl` (contrôle 1 traduit en anglais, étiquettes `holdout-labels.csv` reprises telles quelles)
- Langue de sortie : propriété `refinr.llm.output-language` puis détection par story

# Contrôle 1, anglais (`holdout.en.jsonl`, étiquettes `holdout-labels.csv` reprises telles quelles, jamais arbitrées)

## Seuil d'acceptation (5 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (0 sur 8, 0 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 1 sur 1 (100 %) |
| stabilité ≥ 90 % (toutes étiquettes) | **ÉCHEC** (80 %) |

**Seuil bloquant : atteint**

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 100 % | 5 | 0 (0 %) | 8 | 0 (0 %) | 0 | 0 | 85 % |
| `hesitant` (indicatif) | 50 % | 1 | 0 (0 %) | 1 | 1 (100 %) | 0 | 0 | 50 % |
| toutes | 93 % | 6 | 0 (0 %) | 9 | 1 (11 %) | 0 | 0 | 80 % |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 8 | 0 | 0 |
| not-ready | 0 | 5 | 0 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 0 | 1 | 0 |
| not-ready | 0 | 1 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 8 | 1 | 0 |
| not-ready | 0 | 6 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v053 | ready | sure | 3 / 2 / 0 | ai-ready (instable) | contenu de la réponse, unités et droits définis ; noms des champs = convention |  |
| v055 | ready | hesitant | 1 / 4 / 0 | validation-necessaire ✗ | délai de 2 ans, fréquence et visibilité définis ; accès RH supposé passer par la recherche existante | How HR finds and views archived requests is undefined (search criteria, screen or endpoint, and which HR roles have access). Which HR search and access behavior should apply? |
| v056 | ready | sure | 3 / 2 / 0 | ai-ready (instable) | règle d'arrondi et format d'affichage définis avec exemples |  |
