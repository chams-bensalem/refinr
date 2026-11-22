# Mesure du verdict — google (gemini-3.6-flash)

- Début : 2026-11-22T17:27:44.671045Z ; cible `env` ; `describe()` : `google (gemini-3.6-flash)` ; paramètres optionnels envoyés : aucun
- Prompt `verdict-v3.2`, lots de 10 stories (production), 2 réparation(s) au plus, `max-retries=0`, ordre mélangé
  par exécution (graine 20261116 + numéro d'exécution) ; délai PT5M, 32768 tokens de sortie au plus
- **Référence étiquetée par une IA** (Claude, `labeled_by` = [claude-po]), pas par un humain.
- Langue de sortie : propriété `refinr.llm.output-language` puis détection par story

- Appels : 24 (1 en échec) ; tokens facturés : 52108 en entrée, 77639 en sortie

- Étiquettes : `labels.csv` corrigé par `labels-adjudication.csv` (v004 ready/sure → ready/hesitant, v020 ready/sure → not-ready/sure, v033 ready/sure → ready/hesitant).
  **Arbitrage fait après avoir vu les réponses des modèles** : il biaise la mesure en leur faveur.

# Jeu d'origine (50 stories, étiquettes arbitrées)

## Comparaison sur le jeu d'origine (mêmes étiquettes arbitrées, même seuil)

| | `2026-11-16-google-gemini-3.6-flash` | `2026-11-22-google-gemini-3.6-flash-verdict-v3.2` |
|---|---|---|
| exécutions | 1 | 3 |
| **seuil bloquant** | atteint | **NON atteint** |
| faux `ai-ready`, `not-ready` / `sure` (majorité) | 0 sur 3 | 1 sur 21 |
| `not-ready` / `sure` jugées prêtes > 1 fois sur 5 | aucune | v020, v030 |
| faux `validation-necessaire`, `ready` / `sure` (≤ 25 %) | 2 sur 4 (50 %) **ÉCHEC** | 0 sur 18 (0 %) |
| faux `validation-necessaire`, `ready` / `hesitant` | 1 sur 2 | 2 sur 7 |
| faux `ai-ready`, `not-ready` / `hesitant` | 0 sur 1 | 1 sur 4 |
| accord `sure` / toutes | 71 % / 70 % | 97 % / 92 % |
| stabilité (≥ 90 %) | non mesurée | 90 % |
| sans verdict (repli) | 0 | 0 |
| non mesurées | 40 (v001, v002, v005, v006, v007, v008, v009, v010, v011, v012, v013, v015, v016, v017, v021, v022, v023, v024, v025, v027, v028, v030, v031, v032, v033, v034, v035, v036, v037, v039, v041, v042, v043, v044, v045, v046, v047, v048, v049, v050) | 0 |

## Seuil d'acceptation (3 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | **ÉCHEC** (1) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | **ÉCHEC** (v020, v030) |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (0 sur 18, 0 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 2 sur 7 (29 %) |
| stabilité ≥ 90 % (toutes étiquettes) | OK (90 %) |

**Seuil bloquant : NON atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 97 % | 21 | 1 (5 %) | 18 | 0 (0 %) | 0 | 0 | 97 % |
| `hesitant` (indicatif) | 73 % | 4 | 1 (25 %) | 7 | 2 (29 %) | 0 | 0 | 64 % |
| toutes | 92 % | 25 | 2 (8 %) | 25 | 2 (8 %) | 0 | 0 | 90 % |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 18 | 0 | 0 |
| not-ready | 1 | 20 | 0 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 5 | 2 | 0 |
| not-ready | 1 | 3 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 23 | 2 | 0 |
| not-ready | 2 | 23 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v013 | ready | hesitant | 1 / 1 / 0 | ai-ready (instable) | délais 7 et 14 jours et comportements précisés ; contenu de l'e-mail et jours calendaires à supposer |  |
| v017 | not-ready | hesitant | 1 / 1 / 0 | ai-ready ✗ | forme et destinataire de l'« alerte » non précisés (écran, e-mail, notification) | Les conditions de déclenchement et de réinitialisation automatique de l'alerte selon le seuil minimal sont clairement spécifiées. |
| v019 | ready | hesitant | 2 / 1 / 0 | ai-ready (instable) | alerte visuelle sur la référence, seuil et déclenchement testables |  |
| v020 | not-ready | sure | 3 / 0 / 0 | ai-ready ✗ | contradiction entre « temps réel » et « rafraîchit la page » | La liste des statuts des notes de frais et les modalités d'affichage de l'historique sont clairement définies. |
| v028 | ready | hesitant | 0 / 2 / 0 | validation-necessaire ✗ | barème fiscal officiel et catégorie précisés ; source du véhicule à supposer existante | Quel est le barème fiscal exact à utiliser et quelles données du véhicule doivent être saisies ? |
| v030 | not-ready | sure | 1 / 2 / 0 | validation-necessaire (instable) | choix du stockage cloud et du fournisseur non tranchés |  |
| v039 | ready | hesitant | 2 / 1 / 0 | ai-ready (instable) | affichage du motif, resoumission et changement de statut précisés ; canal de notification à supposer existant |  |
| v041 | ready | hesitant | 0 / 3 / 0 | validation-necessaire ✗ | entrée, refus RG-01 et journalisation RG-03 précisés ; scan supposé en saisie clavier | Que désigne exactement la signature requise par la règle RG-03 dans le cadre d'un mouvement de stock ? |

## Avant arbitrage (`labels.csv` seul, même règle de seuil)

## Seuil d'acceptation (3 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | **ÉCHEC** (v030) |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (0 sur 21, 0 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 2 sur 5 (40 %) |
| stabilité ≥ 90 % (toutes étiquettes) | OK (90 %) |

**Seuil bloquant : NON atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 100 % | 20 | 0 (0 %) | 21 | 0 (0 %) | 0 | 0 | 98 % |
| `hesitant` (indicatif) | 67 % | 4 | 1 (25 %) | 5 | 2 (40 %) | 0 | 0 | 56 % |
| toutes | 94 % | 24 | 1 (4 %) | 26 | 2 (8 %) | 0 | 0 | 90 % |

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
| stabilité ≥ 90 % (toutes étiquettes) | OK (100 %) |

**Seuil bloquant : atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 100 % | 5 | 0 (0 %) | 8 | 0 (0 %) | 0 | 0 | 100 % |
| `hesitant` (indicatif) | 0 % | 2 | 2 (100 %) | 0 | 0 (0 %) | 0 | 0 | 100 % |
| toutes | 87 % | 7 | 2 (29 %) | 8 | 0 (0 %) | 0 | 0 | 100 % |

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
| not-ready | 2 | 0 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 8 | 0 | 0 |
| not-ready | 2 | 5 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v070 | not-ready | hesitant | 3 / 0 / 0 | ai-ready ✗ | « client actif » non défini | La fréquence du traitement, le critère d'inactivité de 6 mois et le modèle d'e-mail de relance à utiliser sont clairement définis. |
| v074 | not-ready | hesitant | 3 / 0 / 0 | ai-ready ✗ | sort des sessions déjà ouvertes à la désactivation non défini (droits d'accès) | Les règles d'accès, le message d'erreur et le comportement dans l'annuaire sont clairement définis. |
