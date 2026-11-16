# Mesure du verdict — anthropic (claude-opus-5-5)

- Début : 2026-11-16T17:08:09.020311Z ; cible `anthropic-check` ; `describe()` : `anthropic (claude-opus-5-5)` ; paramètres optionnels envoyés : aucun
- Prompt `verdict-v3.1`, lots de 10 stories (production), 2 réparation(s) au plus, `max-retries=0`, ordre mélangé
  par exécution (graine 20261116 + numéro d'exécution) ; délai PT1M30S, 4096 tokens de sortie au plus
- **Référence étiquetée par une IA** (Claude, `labeled_by` = [claude-po]), pas par un humain.
- **Circularité** : modèle Claude évalué contre des étiquettes posées par Claude ; résultats indicatifs.

- Appels : 8 (0 en échec) ; tokens facturés : 30629 en entrée, 10653 en sortie ; coût : 0.3356 USD

- Étiquettes : `labels.csv` corrigé par `labels-adjudication.csv` (v004 ready/sure → ready/hesitant, v020 ready/sure → not-ready/sure, v033 ready/sure → ready/hesitant).
  **Arbitrage fait après avoir vu les réponses des modèles** : il biaise la mesure en leur faveur.

# Jeu d'origine (50 stories, étiquettes arbitrées)

## Comparaison sur le jeu d'origine (mêmes étiquettes arbitrées, même seuil)

| | `2026-11-16-anthropic-claude-opus-5-5-verdict-v3` | `2026-11-16-anthropic-claude-opus-5-5` | `2026-11-16-anthropic-claude-opus-5-5-verdict-v3.1` |
|---|---|---|---|
| exécutions | 1 | 1 | 1 |
| **seuil bloquant** | atteint | atteint | atteint |
| faux `ai-ready`, `not-ready` / `sure` (majorité) | 0 sur 21 | 0 sur 21 | 0 sur 21 |
| `not-ready` / `sure` jugées prêtes > 1 fois sur 5 | aucune | aucune | aucune |
| faux `validation-necessaire`, `ready` / `sure` (≤ 25 %) | 1 sur 18 (6 %) | 3 sur 18 (17 %) | 2 sur 18 (11 %) |
| faux `validation-necessaire`, `ready` / `hesitant` | 5 sur 7 | 7 sur 7 | 2 sur 7 |
| faux `ai-ready`, `not-ready` / `hesitant` | 0 sur 4 | 0 sur 4 | 1 sur 4 |
| accord `sure` / toutes | 97 % / 88 % | 92 % / 80 % | 95 % / 90 % |
| stabilité (≥ 90 %) | non mesurée | non mesurée | non mesurée |
| sans verdict (repli) | 0 | 0 | 0 |
| non mesurées | 0 | 0 | 0 |

## Seuil d'acceptation (1 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (2 sur 18, 11 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 2 sur 7 (29 %) |
| stabilité ≥ 90 % (toutes étiquettes) | non mesurée (une seule exécution) |

**Seuil bloquant : atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 95 % | 21 | 0 (0 %) | 18 | 2 (11 %) | 0 | 0 | – |
| `hesitant` (indicatif) | 73 % | 4 | 1 (25 %) | 7 | 2 (29 %) | 0 | 0 | – |
| toutes | 90 % | 25 | 1 (4 %) | 25 | 4 (16 %) | 0 | 0 | – |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 16 | 2 | 0 |
| not-ready | 0 | 21 | 0 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 5 | 2 | 0 |
| not-ready | 1 | 3 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 21 | 4 | 0 |
| not-ready | 1 | 24 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v009 | ready | sure | 0 / 1 / 0 | validation-necessaire ✗ | rôle, comportement UI et 403 côté endpoint précisés | Un utilisateur sans le rôle Gestionnaire doit-il se voir refuser l'accès à tout l'écran Inventaire (HTTP 403), comme l'indique le critère, ou seulement au bouton et à l'endpoint d'export, comme l'indique la description ? |
| v014 | ready | sure | 0 / 1 / 0 | validation-necessaire ✗ | conditions d'annulation, statut, recrédit et message d'erreur précisés | Le salarié peut-il encore annuler sa demande le jour même du premier jour de congé, et l'annulation concerne-t-elle aussi les demandes en attente (avec recrédit du solde) ou seulement les demandes validées ? |
| v017 | not-ready | hesitant | 1 / 0 / 0 | ai-ready ✗ | forme et destinataire de l'« alerte » non précisés (écran, e-mail, notification) | Le déclenchement (passage sous le seuil configuré) et la réinitialisation automatique (retour au-dessus du seuil) sont définis, avec un exemple chiffré vérifiable. |
| v028 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | barème fiscal officiel et catégorie précisés ; source du véhicule à supposer existante | Quel barème fiscal appliquer (année, puissances fiscales, types de véhicules) et comment traiter les tranches qui dépendent de la distance annuelle cumulée lors du calcul de chaque déplacement ? |
| v041 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | entrée, refus RG-01 et journalisation RG-03 précisés ; scan supposé en saisie clavier | En quoi consiste concrètement la « signature » d'un mouvement exigée par RG-03 (signature électronique, identifiant utilisateur, empreinte cryptographique) ? |

## Avant arbitrage (`labels.csv` seul, même règle de seuil)

## Seuil d'acceptation (1 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (3 sur 21, 14 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 2 sur 5 (40 %) |
| stabilité ≥ 90 % (toutes étiquettes) | non mesurée (une seule exécution) |

**Seuil bloquant : atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 93 % | 20 | 0 (0 %) | 21 | 3 (14 %) | 0 | 0 | – |
| `hesitant` (indicatif) | 67 % | 4 | 1 (25 %) | 5 | 2 (40 %) | 0 | 0 | – |
| toutes | 88 % | 24 | 1 (4 %) | 26 | 5 (19 %) | 0 | 0 | – |

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
| `hesitant` (indicatif) | 100 % | 1 | 0 (0 %) | 1 | 0 (0 %) | 0 | 0 | – |
| toutes | 100 % | 6 | 0 (0 %) | 9 | 0 (0 %) | 0 | 0 | – |

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
| not-ready | 0 | 1 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 9 | 0 | 0 |
| not-ready | 0 | 6 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| – | | | | | | |

# Jeu de contrôle `holdout2.jsonl` (15 stories, `holdout2-labels.csv`, jamais arbitré)

## Seuil d'acceptation (1 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (1 sur 8, 13 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 0 sur 0 (0 %) |
| stabilité ≥ 90 % (toutes étiquettes) | non mesurée (une seule exécution) |

**Seuil bloquant : atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 92 % | 5 | 0 (0 %) | 8 | 1 (13 %) | 0 | 0 | – |
| `hesitant` (indicatif) | 50 % | 2 | 1 (50 %) | 0 | 0 (0 %) | 0 | 0 | – |
| toutes | 87 % | 7 | 1 (14 %) | 8 | 1 (13 %) | 0 | 0 | – |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 7 | 1 | 0 |
| not-ready | 0 | 5 | 0 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 0 | 0 | 0 |
| not-ready | 1 | 1 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 7 | 1 | 0 |
| not-ready | 1 | 6 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v073 | ready | sure | 0 / 1 / 0 | validation-necessaire ✗ | champ, casse, seuil, limite et tri définis ; accents = cas limite non sensible | La recherche doit-elle retourner les produits dont le libellé contient le texte saisi ou seulement ceux dont le libellé commence par ce texte ? |
| v074 | not-ready | hesitant | 1 / 0 / 0 | ai-ready ✗ | sort des sessions déjà ouvertes à la désactivation non défini (droits d'accès) | L'acteur, l'action et les effets de la désactivation (connexion refusée avec un message précis, absence dans l'annuaire) sont définis et testables. |
