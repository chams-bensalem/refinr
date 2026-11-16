# Mesure du verdict — anthropic (claude-haiku-5-5)

- Début : 2026-11-16T15:31:25.491497Z ; cible `anthropic` ; `describe()` : `anthropic (claude-haiku-5-5)` ; paramètres optionnels envoyés : aucun
- Prompt `verdict-v3`, lots de 10 stories (production), 2 réparation(s) au plus, `max-retries=0`, ordre mélangé
  par exécution (graine 20261116 + numéro d'exécution) ; délai PT1M30S, 4096 tokens de sortie au plus
- **Référence étiquetée par une IA** (Claude, `labeled_by` = [claude-po]), pas par un humain.
- **Circularité** : modèle Claude évalué contre des étiquettes posées par Claude ; résultats indicatifs.

- Appels : 35 (0 en échec) ; tokens facturés : 122705 en entrée, 82347 en sortie ; coût : 0.0534 USD

- Étiquettes : `labels.csv` corrigé par `labels-adjudication.csv` (v004 ready/sure → ready/hesitant, v020 ready/sure → not-ready/sure, v033 ready/sure → ready/hesitant).
  **Arbitrage fait après avoir vu les réponses des modèles** : il biaise la mesure en leur faveur.

# Jeu d'origine (50 stories, étiquettes arbitrées)

## Comparaison sur le jeu d'origine (mêmes étiquettes arbitrées, même seuil)

| | `2026-11-16-anthropic-claude-haiku-5-5` | `2026-11-16-anthropic-claude-haiku-5-5-verdict-v3` |
|---|---|---|
| exécutions | 5 | 5 |
| **seuil bloquant** | atteint | atteint |
| faux `ai-ready`, `not-ready` / `sure` (majorité) | 0 sur 21 | 0 sur 21 |
| `not-ready` / `sure` jugées prêtes > 1 fois sur 5 | aucune | aucune |
| faux `validation-necessaire`, `ready` / `sure` (≤ 25 %) | 4 sur 18 (22 %) | 6 sur 18 (33 %) **ÉCHEC** |
| faux `validation-necessaire`, `ready` / `hesitant` | 7 sur 7 | 7 sur 7 |
| faux `ai-ready`, `not-ready` / `hesitant` | 0 sur 4 | 0 sur 4 |
| accord `sure` / toutes | 90 % / 78 % | 85 % / 74 % |
| stabilité (≥ 90 %) | 70 % **ÉCHEC** | 76 % **ÉCHEC** |
| sans verdict (repli) | 0 | 0 |
| non mesurées | 0 | 0 |

## Seuil d'acceptation (5 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | **ÉCHEC** (6 sur 18, 33 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 7 sur 7 (100 %) |
| stabilité ≥ 90 % (toutes étiquettes) | **ÉCHEC** (76 %) |

**Seuil bloquant : atteint**

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 85 % | 21 | 0 (0 %) | 18 | 6 (33 %) | 0 | 0 | 77 % |
| `hesitant` (indicatif) | 36 % | 4 | 0 (0 %) | 7 | 7 (100 %) | 0 | 0 | 73 % |
| toutes | 74 % | 25 | 0 (0 %) | 25 | 13 (52 %) | 0 | 0 | 76 % |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 12 | 6 | 0 |
| not-ready | 0 | 21 | 0 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 0 | 7 | 0 |
| not-ready | 0 | 4 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 12 | 13 | 0 |
| not-ready | 0 | 25 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v002 | ready | sure | 1 / 4 / 0 | validation-necessaire ✗ | journal horodaté avec identifiant utilisateur, critère vérifiable ; détails de stockage laissés à l'implémentation | Quelle est la règle RG-03 référencée mais non fournie, que signifie « horodatage strict » (précision, fuseau horaire), et l'historique doit-il être non modifiable ? |
| v004 | ready | hesitant | 2 / 3 / 0 | validation-necessaire ✗ | schéma de la réponse 200 non défini | Quelles informations exactement la réponse 200 doit-elle contenir (quantité seule ou aussi unité, entrepôt, date de mise à jour) et que signifie « stock à jour » (temps réel ou dernière synchronisation) ? |
| v009 | ready | sure | 3 / 2 / 0 | ai-ready (instable) | rôle, comportement UI et 403 côté endpoint précisés |  |
| v013 | ready | hesitant | 0 / 5 / 0 | validation-necessaire ✗ | délais 7 et 14 jours et comportements précisés ; contenu de l'e-mail et jours calendaires à supposer | Que se passe-t-il si le manager n'a pas de N+1 (sommet de hiérarchie), la relance est-elle envoyée une seule fois ou quotidiennement, et que compte comme « action » du manager ? |
| v014 | ready | sure | 2 / 3 / 0 | validation-necessaire ✗ | conditions d'annulation, statut, recrédit et message d'erreur précisés | Une demande dont le premier jour est aujourd'hui ou en cours de journée peut-elle être annulée, et les demandes en attente de validation sont-elles concernées par l'annulation ? |
| v015 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | service existant, libellé exact et cas négatif précisés |  |
| v016 | ready | sure | 3 / 2 / 0 | ai-ready (instable) | contenu de la ligne d'audit et lecture seule précisés |  |
| v019 | ready | hesitant | 0 / 5 / 0 | validation-necessaire ✗ | alerte visuelle sur la référence, seuil et déclenchement testables | Où et à qui l'alerte visuelle est-elle affichée, et quand disparaît-elle (acquittement, remontée du stock au-dessus du seuil) ? |
| v023 | ready | sure | 2 / 3 / 0 | validation-necessaire ✗ | seuil, durée, remise à zéro et message neutre précisés | Une tentative échouée pendant le verrouillage prolonge-t-elle le blocage, et après la fin des 15 minutes le compteur repart-il à zéro ou un seul nouvel échec suffit-il à re-verrouiller le compte ? |
| v024 | ready | sure | 2 / 3 / 0 | validation-necessaire ✗ | endpoint, codes 200/404 et donnée renvoyée précisés | Le format de réponse n'est pas défini (champs exacts, nom et type de la quantité) et il n'est pas précisé si le stock est global ou par entrepôt. Question au PO : quels champs la réponse doit-elle contenir et quel périmètre de stock faut-il retourner ? |
| v028 | ready | hesitant | 0 / 5 / 0 | validation-necessaire ✗ | barème fiscal officiel et catégorie précisés ; source du véhicule à supposer existante | Quel barème fiscal appliquer (année, type de véhicule, puissance fiscale, tranches de distance) et où ce barème est-il fourni ? |
| v031 | ready | sure | 0 / 5 / 0 | validation-necessaire ✗ | formule, arrondi, période et cas chiffrés précisés | Les jours « posés » (y compris en attente) sont-ils déduits du solde, ou seulement les congés validés, et comment est calculé le solde pour un salarié arrivé après le 1er juin ? |
| v033 | ready | hesitant | 2 / 3 / 0 | validation-necessaire ✗ | comportement au-delà de 10 000 articles non défini | Que doit faire l'export lorsque le nombre d'articles dépasse 10 000 : refus, export partiel ou export complet ? Le descriptif ne le précise pas. |
| v038 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | jours fériés légaux connus, exemple chiffré vérifiable |  |
| v039 | ready | hesitant | 1 / 4 / 0 | validation-necessaire ✗ | affichage du motif, resoumission et changement de statut précisés ; canal de notification à supposer existant | Quelles informations du motif de refus doivent être affichées, quels champs de la dépense sont modifiables après refus, et par quel canal le manager est-il notifié ? |
| v041 | ready | hesitant | 0 / 5 / 0 | validation-necessaire ✗ | entrée, refus RG-01 et journalisation RG-03 précisés ; scan supposé en saisie clavier | Quelles sont les règles RG-01 et RG-03 (non décrites dans la story) et qu'entend-on exactement par « signature » du mouvement (identifiant, hash, signature électronique) ? |
| v043 | ready | sure | 1 / 4 / 0 | validation-necessaire ✗ | case, valeur par défaut et effet sur le job existant précisés | Quel est l'état initial de la case pour les comptes existants, qui n'ont pas été créés comme « nouveaux comptes » ? Ce point n'est pas défini. |

## Avant arbitrage (`labels.csv` seul, même règle de seuil)

## Seuil d'acceptation (5 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | **ÉCHEC** (9 sur 21, 43 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 5 sur 5 (100 %) |
| stabilité ≥ 90 % (toutes étiquettes) | **ÉCHEC** (76 %) |

**Seuil bloquant : atteint**

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |
|---|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 78 % | 20 | 0 (0 %) | 21 | 9 (43 %) | 0 | 0 | 73 % |
| `hesitant` (indicatif) | 44 % | 4 | 0 (0 %) | 5 | 5 (100 %) | 0 | 0 | 89 % |
| toutes | 72 % | 24 | 0 (0 %) | 26 | 14 (54 %) | 0 | 0 | 76 % |

# Jeu de contrôle (15 stories, `holdout-labels.csv`, jamais arbitré)

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
| `sure` (bloquant) | 100 % | 5 | 0 (0 %) | 8 | 0 (0 %) | 0 | 0 | 100 % |
| `hesitant` (indicatif) | 50 % | 1 | 0 (0 %) | 1 | 1 (100 %) | 0 | 0 | 0 % |
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
| v054 | not-ready | hesitant | 2 / 3 / 0 | validation-necessaire (instable) | « signé » non défini : comment le système sait-il qu'un bon de livraison est signé (signature papier, électronique, statut existant) ? |  |
| v055 | ready | hesitant | 1 / 4 / 0 | validation-necessaire ✗ | délai de 2 ans, fréquence et visibilité définis ; accès RH supposé passer par la recherche existante | Comment et où les RH consultent-ils les demandes archivées (écran, moteur de recherche, droits d'accès) et l'archivage est-il réversible ? |
