# Mesure du verdict — anthropic (claude-opus-5-5)

- Début : 2026-11-16T16:20:52.019271Z ; cible `anthropic-check` ; `describe()` : `anthropic (claude-opus-5-5)` ; paramètres optionnels envoyés : aucun
- Prompt `verdict-v3`, lots de 10 stories (production), 2 réparation(s) au plus, `max-retries=0`, ordre mélangé
  par exécution (graine 20261116 + numéro d'exécution) ; délai PT1M30S, 4096 tokens de sortie au plus
- **Référence étiquetée par une IA** (Claude, `labeled_by` = [claude-po]), pas par un humain.
- **Circularité** : modèle Claude évalué contre des étiquettes posées par Claude ; résultats indicatifs.

- Appels : 7 (0 en échec) ; tokens facturés : 24541 en entrée, 8695 en sortie ; coût : 0.2721 USD

- Étiquettes : `labels.csv` corrigé par `labels-adjudication.csv` (v004 ready/sure → ready/hesitant, v020 ready/sure → not-ready/sure, v033 ready/sure → ready/hesitant).
  **Arbitrage fait après avoir vu les réponses des modèles** : il biaise la mesure en leur faveur.

# Jeu d'origine (50 stories, étiquettes arbitrées)

## Comparaison sur le jeu d'origine (mêmes étiquettes arbitrées, même seuil)

| | `2026-11-16-anthropic-claude-opus-5-5` | `2026-11-16-anthropic-claude-opus-5-5-verdict-v3` |
|---|---|---|
| exécutions | 1 | 1 |
| **seuil bloquant** | atteint | atteint |
| faux `ai-ready`, `not-ready` / `sure` (majorité) | 0 sur 21 | 0 sur 21 |
| `not-ready` / `sure` jugées prêtes > 1 fois sur 5 | aucune | aucune |
| faux `validation-necessaire`, `ready` / `sure` (≤ 25 %) | 3 sur 18 (17 %) | 1 sur 18 (6 %) |
| faux `validation-necessaire`, `ready` / `hesitant` | 7 sur 7 | 5 sur 7 |
| faux `ai-ready`, `not-ready` / `hesitant` | 0 sur 4 | 0 sur 4 |
| accord `sure` / toutes | 92 % / 80 % | 97 % / 88 % |
| stabilité (≥ 90 %) | non mesurée | non mesurée |
| sans verdict (repli) | 0 | 0 |
| non mesurées | 0 | 0 |

## Seuil d'acceptation (1 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (1 sur 18, 6 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 5 sur 7 (71 %) |
| stabilité ≥ 90 % (toutes étiquettes) | non mesurée (une seule exécution) |

**Seuil bloquant : atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 97 % | 21 | 0 (0 %) | 18 | 1 (6 %) | 0 | 0 | – |
| `hesitant` (indicatif) | 55 % | 4 | 0 (0 %) | 7 | 5 (71 %) | 0 | 0 | – |
| toutes | 88 % | 25 | 0 (0 %) | 25 | 6 (24 %) | 0 | 0 | – |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 17 | 1 | 0 |
| not-ready | 0 | 21 | 0 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 2 | 5 | 0 |
| not-ready | 0 | 4 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 19 | 6 | 0 |
| not-ready | 0 | 25 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v013 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | délais 7 et 14 jours et comportements précisés ; contenu de l'e-mail et jours calendaires à supposer | Les délais de 7 et 14 jours sont-ils en jours calendaires ou ouvrés, quel est le contenu de l'email de relance, et que faire si le manager n'a pas de N+1 ? |
| v014 | ready | sure | 0 / 1 / 0 | validation-necessaire ✗ | conditions d'annulation, statut, recrédit et message d'erreur précisés | L'annulation est-elle encore possible le jour même du premier jour de congé, et quels statuts de demande sont annulables (en attente, refusée), avec quel effet sur le solde ? |
| v019 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | alerte visuelle sur la référence, seuil et déclenchement testables | Où et sous quelle forme l'alerte visuelle doit-elle s'afficher, et quand doit-elle disparaître (retour au-dessus du seuil, acquittement manuel) ? |
| v028 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | barème fiscal officiel et catégorie précisés ; source du véhicule à supposer existante | Quel barème fiscal appliquer (année, critère de puissance fiscale du véhicule) et comment traiter les tranches qui dépendent de la distance annuelle cumulée du salarié ? |
| v033 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | comportement au-delà de 10 000 articles non défini | Quel comportement est attendu lorsque les filtres actifs renvoient plus de 10 000 articles (export refusé, tronqué ou asynchrone), et quel format adopter pour la date du dernier mouvement ? |
| v041 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | entrée, refus RG-01 et journalisation RG-03 précisés ; scan supposé en saisie clavier | Que recouvre concrètement la « signature » exigée par RG-03 (signature électronique, simple identifiant utilisateur, empreinte cryptographique) et selon quelle référence ? |

## Avant arbitrage (`labels.csv` seul, même règle de seuil)

## Seuil d'acceptation (1 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (3 sur 21, 14 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 4 sur 5 (80 %) |
| stabilité ≥ 90 % (toutes étiquettes) | non mesurée (une seule exécution) |

**Seuil bloquant : atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 93 % | 20 | 0 (0 %) | 21 | 3 (14 %) | 0 | 0 | – |
| `hesitant` (indicatif) | 56 % | 4 | 0 (0 %) | 5 | 4 (80 %) | 0 | 0 | – |
| toutes | 86 % | 24 | 0 (0 %) | 26 | 7 (27 %) | 0 | 0 | – |

# Jeu de contrôle `holdout.jsonl` (15 stories, `holdout-labels.csv`, jamais arbitré)

## Seuil d'acceptation (1 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (0 sur 8, 0 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 0 sur 1 (0 %) |
| stabilité ≥ 90 % (toutes étiquettes) | non mesurée (une seule exécution) |

**Seuil bloquant : atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 100 % | 5 | 0 (0 %) | 8 | 0 (0 %) | 0 | 0 | – |
| `hesitant` (indicatif) | 50 % | 1 | 1 (100 %) | 1 | 0 (0 %) | 0 | 0 | – |
| toutes | 93 % | 6 | 1 (17 %) | 9 | 0 (0 %) | 0 | 0 | – |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 8 | 0 | 0 |
| not-ready | 0 | 5 | 0 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 1 | 0 | 0 |
| not-ready | 1 | 0 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 9 | 0 | 0 |
| not-ready | 1 | 5 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v054 | not-ready | hesitant | 1 / 0 / 0 | ai-ready ✗ | « signé » non défini : comment le système sait-il qu'un bon de livraison est signé (signature papier, électronique, statut existant) ? | La règle de blocage sur la signature du bon de livraison, l'effet sur le stock et le message d'erreur sont explicites et vérifiables. |
