# Mesure du verdict — anthropic (claude-opus-5-5)

- Début : 2026-11-16T18:31:41.569817Z ; cible `anthropic-check` ; `describe()` : `anthropic (claude-opus-5-5)` ; paramètres optionnels envoyés : aucun
- Prompt `verdict-v3.2`, lots de 10 stories (production), 2 réparation(s) au plus, `max-retries=0`, ordre mélangé
  par exécution (graine 20261116 + numéro d'exécution) ; délai PT1M30S, 4096 tokens de sortie au plus
- **Référence étiquetée par une IA** (Claude, `labeled_by` = [claude-po]), pas par un humain.
- **Circularité** : modèle Claude évalué contre des étiquettes posées par Claude ; résultats indicatifs.
- Langue de sortie : propriété `refinr.llm.output-language` puis détection par story

- Appels : 24 (0 en échec) ; tokens facturés : 91743 en entrée, 32758 en sortie ; coût : 1.0221 USD

- Étiquettes : `labels.csv` corrigé par `labels-adjudication.csv` (v004 ready/sure → ready/hesitant, v020 ready/sure → not-ready/sure, v033 ready/sure → ready/hesitant).
  **Arbitrage fait après avoir vu les réponses des modèles** : il biaise la mesure en leur faveur.

# Jeu d'origine (50 stories, étiquettes arbitrées)

## Comparaison sur le jeu d'origine (mêmes étiquettes arbitrées, même seuil)

| | `2026-11-16-anthropic-claude-opus-5-5-verdict-v3.1` | `2026-11-16-anthropic-claude-opus-5-5-verdict-v3` | `2026-11-16-anthropic-claude-opus-5-5` | `2026-11-16-anthropic-claude-opus-5-5-verdict-v3.2` |
|---|---|---|---|---|
| exécutions | 1 | 1 | 1 | 3 |
| **seuil bloquant** | atteint | atteint | atteint | **NON atteint** |
| faux `ai-ready`, `not-ready` / `sure` (majorité) | 0 sur 21 | 0 sur 21 | 0 sur 21 | 1 sur 21 |
| `not-ready` / `sure` jugées prêtes > 1 fois sur 5 | aucune | aucune | aucune | v020 |
| faux `validation-necessaire`, `ready` / `sure` (≤ 25 %) | 2 sur 18 (11 %) | 1 sur 18 (6 %) | 3 sur 18 (17 %) | 0 sur 18 (0 %) |
| faux `validation-necessaire`, `ready` / `hesitant` | 2 sur 7 | 5 sur 7 | 7 sur 7 | 3 sur 7 |
| faux `ai-ready`, `not-ready` / `hesitant` | 1 sur 4 | 0 sur 4 | 0 sur 4 | 0 sur 4 |
| accord `sure` / toutes | 95 % / 90 % | 97 % / 88 % | 92 % / 80 % | 97 % / 92 % |
| stabilité (≥ 90 %) | non mesurée | non mesurée | non mesurée | 94 % |
| sans verdict (repli) | 0 | 0 | 0 | 0 |
| non mesurées | 0 | 0 | 0 | 0 |

## Seuil d'acceptation (3 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | **ÉCHEC** (1) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | **ÉCHEC** (v020) |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (0 sur 18, 0 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 3 sur 7 (43 %) |
| stabilité ≥ 90 % (toutes étiquettes) | OK (94 %) |

**Seuil bloquant : NON atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 97 % | 21 | 1 (5 %) | 18 | 0 (0 %) | 0 | 0 | 95 % |
| `hesitant` (indicatif) | 73 % | 4 | 0 (0 %) | 7 | 3 (43 %) | 0 | 0 | 91 % |
| toutes | 92 % | 25 | 1 (4 %) | 25 | 3 (12 %) | 0 | 0 | 94 % |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 18 | 0 | 0 |
| not-ready | 1 | 20 | 0 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 4 | 3 | 0 |
| not-ready | 0 | 4 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 22 | 3 | 0 |
| not-ready | 1 | 24 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v009 | ready | sure | 2 / 1 / 0 | ai-ready (instable) | rôle, comportement UI et 403 côté endpoint précisés |  |
| v014 | ready | sure | 2 / 1 / 0 | ai-ready (instable) | conditions d'annulation, statut, recrédit et message d'erreur précisés |  |
| v020 | not-ready | sure | 3 / 0 / 0 | ai-ready ✗ | contradiction entre « temps réel » et « rafraîchit la page » | Les six statuts à afficher sont listés et le critère précise que la mise à jour est visible au rafraîchissement de la page, ce qui est vérifiable sans mécanisme temps réel supplémentaire. |
| v028 | ready | hesitant | 0 / 3 / 0 | validation-necessaire ✗ | barème fiscal officiel et catégorie précisés ; source du véhicule à supposer existante | Quel barème fiscal appliquer (année, types de véhicules et puissance fiscale), et comment traiter les tranches de distance annuelle cumulée qui modifient la formule de calcul ? |
| v039 | ready | hesitant | 1 / 2 / 0 | validation-necessaire ✗ | affichage du motif, resoumission et changement de statut précisés ; canal de notification à supposer existant | Par quel canal et avec quel contenu le manager est-il notifié de la resoumission, et quelles modifications des dépenses le salarié peut-il faire (modifier, ajouter, supprimer) ? |
| v041 | ready | hesitant | 0 / 3 / 0 | validation-necessaire ✗ | entrée, refus RG-01 et journalisation RG-03 précisés ; scan supposé en saisie clavier | Que recouvre exactement la « signature » exigée par RG-03 (simple identifiant utilisateur, signature électronique, hash cryptographique) ? |

## Avant arbitrage (`labels.csv` seul, même règle de seuil)

## Seuil d'acceptation (3 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (0 sur 21, 0 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 3 sur 5 (60 %) |
| stabilité ≥ 90 % (toutes étiquettes) | OK (94 %) |

**Seuil bloquant : atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 100 % | 20 | 0 (0 %) | 21 | 0 (0 %) | 0 | 0 | 95 % |
| `hesitant` (indicatif) | 67 % | 4 | 0 (0 %) | 5 | 3 (60 %) | 0 | 0 | 89 % |
| toutes | 94 % | 24 | 0 (0 %) | 26 | 3 (12 %) | 0 | 0 | 94 % |

# Jeu de contrôle `holdout.jsonl` (15 stories, `holdout-labels.csv`, jamais arbitré)

## Seuil d'acceptation (3 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (0 sur 8, 0 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 0 sur 1 (0 %) |
| stabilité ≥ 90 % (toutes étiquettes) | OK (100 %) |

**Seuil bloquant : atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 100 % | 5 | 0 (0 %) | 8 | 0 (0 %) | 0 | 0 | 100 % |
| `hesitant` (indicatif) | 100 % | 1 | 0 (0 %) | 1 | 0 (0 %) | 0 | 0 | 100 % |
| toutes | 100 % | 6 | 0 (0 %) | 9 | 0 (0 %) | 0 | 0 | 100 % |

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

## Seuil d'acceptation (3 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (0 sur 8, 0 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 0 sur 0 (0 %) |
| stabilité ≥ 90 % (toutes étiquettes) | OK (93 %) |

**Seuil bloquant : atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 100 % | 5 | 0 (0 %) | 8 | 0 (0 %) | 0 | 0 | 92 % |
| `hesitant` (indicatif) | 50 % | 2 | 1 (50 %) | 0 | 0 (0 %) | 0 | 0 | 100 % |
| toutes | 93 % | 7 | 1 (14 %) | 8 | 0 (0 %) | 0 | 0 | 93 % |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 8 | 0 | 0 |
| not-ready | 0 | 5 | 0 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 0 | 0 | 0 |
| not-ready | 1 | 1 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 8 | 0 | 0 |
| not-ready | 1 | 6 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v074 | not-ready | hesitant | 3 / 0 / 0 | ai-ready ✗ | sort des sessions déjà ouvertes à la désactivation non défini (droits d'accès) | Les effets de la désactivation sont précisés et vérifiables : connexion refusée avec un message exact et absence dans l'annuaire. |
| v078 | ready | sure | 2 / 1 / 0 | ai-ready (instable) | « validée » défini, date de mise en paiement et exclusions définies |  |
