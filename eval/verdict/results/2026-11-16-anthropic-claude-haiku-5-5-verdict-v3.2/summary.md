# Mesure du verdict — anthropic (claude-haiku-5-5)

- Début : 2026-11-16T18:12:18.883311Z ; cible `anthropic` ; `describe()` : `anthropic (claude-haiku-5-5)` ; paramètres optionnels envoyés : aucun
- Prompt `verdict-v3.2`, lots de 10 stories (production), 2 réparation(s) au plus, `max-retries=0`, ordre mélangé
  par exécution (graine 20261116 + numéro d'exécution) ; délai PT1M30S, 4096 tokens de sortie au plus
- **Référence étiquetée par une IA** (Claude, `labeled_by` = [claude-po]), pas par un humain.
- **Circularité** : modèle Claude évalué contre des étiquettes posées par Claude ; résultats indicatifs.
- Langue de sortie : propriété `refinr.llm.output-language` puis détection par story

- Appels : 42 (1 en échec) ; tokens facturés : 158328 en entrée, 110388 en sortie ; coût : 0.0710 USD

- Étiquettes : `labels.csv` corrigé par `labels-adjudication.csv` (v004 ready/sure → ready/hesitant, v020 ready/sure → not-ready/sure, v033 ready/sure → ready/hesitant).
  **Arbitrage fait après avoir vu les réponses des modèles** : il biaise la mesure en leur faveur.

# Jeu d'origine (50 stories, étiquettes arbitrées)

## Comparaison sur le jeu d'origine (mêmes étiquettes arbitrées, même seuil)

| | `2026-11-16-anthropic-claude-haiku-5-5-verdict-v3.1` | `2026-11-16-anthropic-claude-haiku-5-5-verdict-v3` | `2026-11-16-anthropic-claude-haiku-5-5` | `2026-11-16-anthropic-claude-haiku-5-5-verdict-v3.2` |
|---|---|---|---|---|
| exécutions | 5 | 5 | 5 | 5 |
| **seuil bloquant** | atteint | atteint | atteint | atteint |
| faux `ai-ready`, `not-ready` / `sure` (majorité) | 0 sur 21 | 0 sur 21 | 0 sur 21 | 0 sur 21 |
| `not-ready` / `sure` jugées prêtes > 1 fois sur 5 | aucune | aucune | aucune | aucune |
| faux `validation-necessaire`, `ready` / `sure` (≤ 25 %) | 2 sur 18 (11 %) | 6 sur 18 (33 %) **ÉCHEC** | 4 sur 18 (22 %) | 2 sur 18 (11 %) |
| faux `validation-necessaire`, `ready` / `hesitant` | 5 sur 7 | 7 sur 7 | 7 sur 7 | 6 sur 7 |
| faux `ai-ready`, `not-ready` / `hesitant` | 1 sur 4 | 0 sur 4 | 0 sur 4 | 0 sur 4 |
| accord `sure` / toutes | 95 % / 84 % | 85 % / 74 % | 90 % / 78 % | 95 % / 84 % |
| stabilité (≥ 90 %) | 80 % **ÉCHEC** | 76 % **ÉCHEC** | 70 % **ÉCHEC** | 78 % **ÉCHEC** |
| sans verdict (repli) | 0 | 0 | 0 | 0 |
| non mesurées | 0 | 0 | 0 | 0 |

## Seuil d'acceptation (5 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (2 sur 18, 11 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 6 sur 7 (86 %) |
| stabilité ≥ 90 % (toutes étiquettes) | **ÉCHEC** (78 %) |

**Seuil bloquant : atteint**

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 95 % | 21 | 0 (0 %) | 18 | 2 (11 %) | 0 | 0 | 82 % |
| `hesitant` (indicatif) | 45 % | 4 | 0 (0 %) | 7 | 6 (86 %) | 0 | 0 | 64 % |
| toutes | 84 % | 25 | 0 (0 %) | 25 | 8 (32 %) | 0 | 0 | 78 % |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 16 | 2 | 0 |
| not-ready | 0 | 21 | 0 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 1 | 6 | 0 |
| not-ready | 0 | 4 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 17 | 8 | 0 |
| not-ready | 0 | 25 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v002 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | journal horodaté avec identifiant utilisateur, critère vérifiable ; détails de stockage laissés à l'implémentation |  |
| v004 | ready | hesitant | 2 / 3 / 0 | validation-necessaire ✗ | schéma de la réponse 200 non défini | La source de données du stock à jour (système, entrepôt ou base) n'est ni nommée ni décrite, alors qu'elle conditionne la réponse de l'endpoint ; quel système fournit les quantités en stock ? |
| v013 | ready | hesitant | 0 / 5 / 0 | validation-necessaire ✗ | délais 7 et 14 jours et comportements précisés ; contenu de l'e-mail et jours calendaires à supposer | Qu'est-ce qu'une « note » dans ce contexte, et quelles actions du manager mettent fin à l'attente (validation, refus, autre) ? |
| v014 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | conditions d'annulation, statut, recrédit et message d'erreur précisés |  |
| v016 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | contenu de la ligne d'audit et lecture seule précisés |  |
| v017 | not-ready | hesitant | 2 / 3 / 0 | validation-necessaire (instable) | forme et destinataire de l'« alerte » non précisés (écran, e-mail, notification) |  |
| v019 | ready | hesitant | 1 / 4 / 0 | validation-necessaire ✗ | alerte visuelle sur la référence, seuil et déclenchement testables | Où et comment l'alerte visuelle doit-elle s'afficher pour l'utilisateur (écran, emplacement, forme de l'alerte) ? |
| v023 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | seuil, durée, remise à zéro et message neutre précisés |  |
| v024 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | endpoint, codes 200/404 et donnée renvoyée précisés |  |
| v028 | ready | hesitant | 0 / 5 / 0 | validation-necessaire ✗ | barème fiscal officiel et catégorie précisés ; source du véhicule à supposer existante | Quel barème kilométrique précis (année, tranches de distance, puissance fiscale par type de véhicule) doit être appliqué pour calculer le montant ? |
| v031 | ready | sure | 2 / 3 / 0 | validation-necessaire ✗ | formule, arrondi, période et cas chiffrés précisés | La description retient les jours « posés et validés » alors que les critères ne retiennent que les jours « validés » : faut-il aussi déduire les congés posés mais encore en attente de validation ? |
| v039 | ready | hesitant | 2 / 3 / 0 | validation-necessaire ✗ | affichage du motif, resoumission et changement de statut précisés ; canal de notification à supposer existant | Le critère « clairement affiché » n'est pas vérifiable et les règles de modification des dépenses ne sont pas définies : quels éléments de la note le salarié peut-il modifier et comment le motif de refus doit-il être affiché ? |
| v041 | ready | hesitant | 0 / 5 / 0 | validation-necessaire ✗ | entrée, refus RG-01 et journalisation RG-03 précisés ; scan supposé en saisie clavier | Le terme « signé » de RG-03 n'est pas défini (signature électronique, identifiant utilisateur, empreinte ou autre) : que doit contenir précisément la signature d'un mouvement de stock ? |
| v043 | ready | sure | 2 / 3 / 0 | validation-necessaire ✗ | case, valeur par défaut et effet sur le job existant précisés | La description ne précise pas l'état de la case pour les comptes existants (cochée ou non), ce qui détermine qui reçoit le récapitulatif : quel est l'état attendu pour les comptes déjà créés ? |

## Avant arbitrage (`labels.csv` seul, même règle de seuil)

## Seuil d'acceptation (5 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (4 sur 21, 19 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 5 sur 5 (100 %) |
| stabilité ≥ 90 % (toutes étiquettes) | **ÉCHEC** (78 %) |

**Seuil bloquant : atteint**

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 90 % | 20 | 0 (0 %) | 21 | 4 (19 %) | 0 | 0 | 80 % |
| `hesitant` (indicatif) | 44 % | 4 | 0 (0 %) | 5 | 5 (100 %) | 0 | 0 | 67 % |
| toutes | 82 % | 24 | 0 (0 %) | 26 | 9 (35 %) | 0 | 0 | 78 % |

# Jeu de contrôle `holdout.jsonl` (15 stories, `holdout-labels.csv`, jamais arbitré)

## Seuil d'acceptation (5 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (0 sur 8, 0 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 1 sur 1 (100 %) |
| stabilité ≥ 90 % (toutes étiquettes) | **ÉCHEC** (87 %) |

**Seuil bloquant : atteint**

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 100 % | 5 | 0 (0 %) | 8 | 0 (0 %) | 0 | 0 | 92 % |
| `hesitant` (indicatif) | 50 % | 1 | 0 (0 %) | 1 | 1 (100 %) | 0 | 0 | 50 % |
| toutes | 93 % | 6 | 0 (0 %) | 9 | 1 (11 %) | 0 | 0 | 87 % |

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
| v055 | ready | hesitant | 2 / 3 / 0 | validation-necessaire ✗ | délai de 2 ans, fréquence et visibilité définis ; accès RH supposé passer par la recherche existante | La façon dont les RH retrouvent et consultent une demande archivée n'est pas définie (écran, critères de recherche, stockage), alors que le critère de recherche en dépend : comment les RH doivent-ils rechercher et consulter les demandes archivées ? |

# Jeu de contrôle `holdout2.jsonl` (15 stories, `holdout2-labels.csv`, jamais arbitré)

## Seuil d'acceptation (5 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (1 sur 8, 13 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 0 sur 0 (0 %) |
| stabilité ≥ 90 % (toutes étiquettes) | OK (93 %) |

**Seuil bloquant : atteint**

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 92 % | 5 | 0 (0 %) | 8 | 1 (13 %) | 0 | 0 | 92 % |
| `hesitant` (indicatif) | 50 % | 2 | 1 (50 %) | 0 | 0 (0 %) | 0 | 0 | 100 % |
| toutes | 87 % | 7 | 1 (14 %) | 8 | 1 (13 %) | 0 | 0 | 93 % |

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
| v074 | not-ready | hesitant | 5 / 0 / 0 | ai-ready ✗ | sort des sessions déjà ouvertes à la désactivation non défini (droits d'accès) | Le comportement attendu est clair : un compte désactivé ne peut plus se connecter (message « Compte désactivé ») et n'apparaît plus dans l'annuaire, ce qui est vérifiable par les deux critères. |
| v078 | ready | sure | 2 / 3 / 0 | validation-necessaire ✗ | « validée » défini, date de mise en paiement et exclusions définies | Une note validée le 20 mars puis passée au statut « contrôlée » ou « remboursée » avant le 5 avril doit-elle être mise en paiement, sachant que la description exclut les notes « à un autre statut » ? |
