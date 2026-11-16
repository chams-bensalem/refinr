# Mesure du verdict — anthropic (claude-haiku-5-5)

- Début : 2026-11-16T16:34:52.000873Z ; cible `anthropic` ; `describe()` : `anthropic (claude-haiku-5-5)` ; paramètres optionnels envoyés : aucun
- Prompt `verdict-v3.1`, lots de 10 stories (production), 2 réparation(s) au plus, `max-retries=0`, ordre mélangé
  par exécution (graine 20261116 + numéro d'exécution) ; délai PT1M30S, 4096 tokens de sortie au plus
- **Référence étiquetée par une IA** (Claude, `labeled_by` = [claude-po]), pas par un humain.
- **Circularité** : modèle Claude évalué contre des étiquettes posées par Claude ; résultats indicatifs.

- Appels : 40 (6 en échec) ; tokens facturés : 145407 en entrée, 105081 en sortie ; coût : 0.0720 USD

- Étiquettes : `labels.csv` corrigé par `labels-adjudication.csv` (v004 ready/sure → ready/hesitant, v020 ready/sure → not-ready/sure, v033 ready/sure → ready/hesitant).
  **Arbitrage fait après avoir vu les réponses des modèles** : il biaise la mesure en leur faveur.

# Jeu d'origine (50 stories, étiquettes arbitrées)

## Comparaison sur le jeu d'origine (mêmes étiquettes arbitrées, même seuil)

| | `2026-11-16-anthropic-claude-haiku-5-5-verdict-v3` | `2026-11-16-anthropic-claude-haiku-5-5` | `2026-11-16-anthropic-claude-haiku-5-5-verdict-v3.1` |
|---|---|---|---|
| exécutions | 5 | 5 | 5 |
| **seuil bloquant** | atteint | atteint | atteint |
| faux `ai-ready`, `not-ready` / `sure` (majorité) | 0 sur 21 | 0 sur 21 | 0 sur 21 |
| `not-ready` / `sure` jugées prêtes > 1 fois sur 5 | aucune | aucune | aucune |
| faux `validation-necessaire`, `ready` / `sure` (≤ 25 %) | 6 sur 18 (33 %) **ÉCHEC** | 4 sur 18 (22 %) | 2 sur 18 (11 %) |
| faux `validation-necessaire`, `ready` / `hesitant` | 7 sur 7 | 7 sur 7 | 5 sur 7 |
| faux `ai-ready`, `not-ready` / `hesitant` | 0 sur 4 | 0 sur 4 | 1 sur 4 |
| accord `sure` / toutes | 85 % / 74 % | 90 % / 78 % | 95 % / 84 % |
| stabilité (≥ 90 %) | 76 % **ÉCHEC** | 70 % **ÉCHEC** | 80 % **ÉCHEC** |
| sans verdict (repli) | 0 | 0 | 0 |
| non mesurées | 0 | 0 | 0 |

## Seuil d'acceptation (5 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (2 sur 18, 11 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 5 sur 7 (71 %) |
| stabilité ≥ 90 % (toutes étiquettes) | **ÉCHEC** (80 %) |

**Seuil bloquant : atteint**

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 95 % | 21 | 0 (0 %) | 18 | 2 (11 %) | 0 | 0 | 85 % |
| `hesitant` (indicatif) | 45 % | 4 | 1 (25 %) | 7 | 5 (71 %) | 0 | 0 | 64 % |
| toutes | 84 % | 25 | 1 (4 %) | 25 | 7 (28 %) | 0 | 0 | 80 % |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 16 | 2 | 0 |
| not-ready | 0 | 21 | 0 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 2 | 5 | 0 |
| not-ready | 1 | 3 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 18 | 7 | 0 |
| not-ready | 1 | 24 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v002 | ready | sure | 1 / 3 / 1 | validation-necessaire ✗ | journal horodaté avec identifiant utilisateur, critère vérifiable ; détails de stockage laissés à l'implémentation | La description demande de journaliser chaque transaction alors que le critère ne vise que les mouvements validés, et « horodatage strict » n'est pas défini, quels mouvements exacts faut-il journaliser et avec quel fuseau horaire ? |
| v004 | ready | hesitant | 3 / 2 / 0 | ai-ready (instable) | schéma de la réponse 200 non défini |  |
| v013 | ready | hesitant | 0 / 5 / 0 | validation-necessaire ✗ | délais 7 et 14 jours et comportements précisés ; contenu de l'e-mail et jours calendaires à supposer | Ce qui constitue une « action » du manager qui arrête le délai, et le comportement lorsque le manager n'a pas de N+1, ne sont pas définis ; quelles actions arrêtent le délai et que doit-il se passer en l'absence de N+1 ? |
| v016 | ready | sure | 2 / 1 / 2 | ai-ready (instable) | contenu de la ligne d'audit et lecture seule précisés |  |
| v017 | not-ready | hesitant | 1 / 1 / 3 | ai-ready ✗ | forme et destinataire de l'« alerte » non précisés (écran, e-mail, notification) | La règle de seuil, l'exemple chiffré (11 moins 2 donne 9, inférieur au seuil de 10) et la réinitialisation de l'alerte sont définis, donc le comportement est implémentable. |
| v019 | ready | hesitant | 1 / 2 / 2 | validation-necessaire ✗ | alerte visuelle sur la référence, seuil et déclenchement testables | La forme, l'emplacement et les destinataires de l'alerte visuelle ainsi que la source du seuil par référence ne sont pas définis, qui doit voir l'alerte et où doit-elle s'afficher ? |
| v024 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | endpoint, codes 200/404 et donnée renvoyée précisés |  |
| v028 | ready | hesitant | 0 / 5 / 0 | validation-necessaire ✗ | barème fiscal officiel et catégorie précisés ; source du véhicule à supposer existante | Quel barème kilométrique fiscal (année, tranches de distance, puissance fiscale par véhicule) faut-il appliquer, et quelle est la liste des véhicules déclarables ? |
| v030 | not-ready | sure | 1 / 4 / 0 | validation-necessaire (instable) | choix du stockage cloud et du fournisseur non tranchés |  |
| v031 | ready | sure | 1 / 1 / 3 | ai-ready (instable) | formule, arrondi, période et cas chiffrés précisés |  |
| v039 | ready | hesitant | 1 / 2 / 2 | validation-necessaire ✗ | affichage du motif, resoumission et changement de statut précisés ; canal de notification à supposer existant | Par quel canal (email ou in-app) le manager doit-il être notifié lorsque le salarié resoumet une note refusée ? |
| v041 | ready | hesitant | 0 / 4 / 1 | validation-necessaire ✗ | entrée, refus RG-01 et journalisation RG-03 précisés ; scan supposé en saisie clavier | Que signifie « signé » pour la RG-03 : une signature électronique cryptographique ou simplement l'identifiant de l'utilisateur horodaté ? |
| v043 | ready | sure | 1 / 2 / 2 | validation-necessaire ✗ | case, valeur par défaut et effet sur le job existant précisés | La case est définie pour les nouveaux comptes, mais l'état initial des comptes existants n'est pas précisé alors qu'il détermine qui reçoit le récapitulatif : les comptes existants doivent-ils être cochés ou décochés par défaut ? |

## Avant arbitrage (`labels.csv` seul, même règle de seuil)

## Seuil d'acceptation (5 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (3 sur 21, 14 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 5 sur 5 (100 %) |
| stabilité ≥ 90 % (toutes étiquettes) | **ÉCHEC** (80 %) |

**Seuil bloquant : atteint**

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 93 % | 20 | 0 (0 %) | 21 | 3 (14 %) | 0 | 0 | 83 % |
| `hesitant` (indicatif) | 33 % | 4 | 1 (25 %) | 5 | 5 (100 %) | 0 | 0 | 67 % |
| toutes | 82 % | 24 | 1 (4 %) | 26 | 8 (31 %) | 0 | 0 | 80 % |

# Jeu de contrôle `holdout.jsonl` (15 stories, `holdout-labels.csv`, jamais arbitré)

## Seuil d'acceptation (5 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (1 sur 8, 13 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 0 sur 1 (0 %) |
| stabilité ≥ 90 % (toutes étiquettes) | OK (93 %) |

**Seuil bloquant : atteint**

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 92 % | 5 | 0 (0 %) | 8 | 1 (13 %) | 0 | 0 | 92 % |
| `hesitant` (indicatif) | 100 % | 1 | 0 (0 %) | 1 | 0 (0 %) | 0 | 0 | 100 % |
| toutes | 93 % | 6 | 0 (0 %) | 9 | 1 (11 %) | 0 | 0 | 93 % |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 7 | 1 | 0 |
| not-ready | 0 | 5 | 0 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 1 | 0 | 0 |
| not-ready | 0 | 1 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 8 | 1 | 0 |
| not-ready | 0 | 6 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v053 | ready | sure | 1 / 3 / 1 | validation-necessaire ✗ | contenu de la réponse, unités et droits définis ; noms des champs = convention | Le cas d'un salarié non manager qui demande l'identifiant d'un identifiant inexistant (403 ou 404 ?) n'est pas tranché alors qu'il touche aux droits d'accès ; quelle réponse doit être renvoyée dans ce cas ? |

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
| v074 | not-ready | hesitant | 4 / 0 / 1 | ai-ready ✗ | sort des sessions déjà ouvertes à la désactivation non défini (droits d'accès) | Le blocage de connexion avec le message « Compte désactivé » et le retrait de l'annuaire sont explicitement définis pour un compte désactivé. |
| v078 | ready | sure | 1 / 2 / 2 | validation-necessaire ✗ | « validée » défini, date de mise en paiement et exclusions définies | Que signifie concrètement la « mise en paiement » (changement de statut, export vers un système de paie, format attendu) ? |
