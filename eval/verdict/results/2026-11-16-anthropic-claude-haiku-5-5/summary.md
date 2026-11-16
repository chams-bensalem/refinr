# Mesure du verdict — anthropic (claude-haiku-5-5)

- Début : 2026-11-16T13:52:47.719118Z ; cible `anthropic` ; `describe()` : `anthropic (claude-haiku-5-5)` ; paramètres optionnels envoyés : aucun
- Prompt `verdict-v2`, lots de 10 stories (production), 2 réparation(s) au plus, `max-retries=0`, ordre mélangé
  par exécution (graine 20261116 + numéro d'exécution) ; délai PT1M30S, 4096 tokens de sortie au plus
- **Référence étiquetée par une IA** (Claude, `labeled_by` = [claude-po]), pas par un humain.
- **Circularité** : modèle Claude évalué contre des étiquettes posées par Claude ; résultats indicatifs.

- Appels : 25 (0 en échec) ; tokens facturés : 76745 en entrée, 41575 en sortie ; coût : 0.0285 USD

- Étiquettes : `labels.csv` corrigé par `labels-adjudication.csv` (v004 ready/sure → ready/hesitant, v020 ready/sure → not-ready/sure, v033 ready/sure → ready/hesitant).
  **Arbitrage fait après avoir vu les réponses des modèles** : il biaise la mesure en leur faveur.

## Seuil d'acceptation (5 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (4 sur 18, 22 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 7 sur 7 (100 %) |
| stabilité ≥ 90 % (toutes étiquettes) | **ÉCHEC** (70 %) |

**Seuil bloquant : atteint**

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict | stabilité |
|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 90 % | 21 | 0 (0 %) | 18 | 4 (22 %) | 0 | 69 % |
| `hesitant` (indicatif) | 36 % | 4 | 0 (0 %) | 7 | 7 (100 %) | 0 | 73 % |
| toutes | 78 % | 25 | 0 (0 %) | 25 | 11 (44 %) | 0 | 70 % |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 14 | 4 | 0 |
| not-ready | 0 | 21 | 0 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 0 | 7 | 0 |
| not-ready | 0 | 4 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 14 | 11 | 0 |
| not-ready | 0 | 25 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v002 | ready | sure | 3 / 2 / 0 | ai-ready (instable) | journal horodaté avec identifiant utilisateur, critère vérifiable ; détails de stockage laissés à l'implémentation |  |
| v004 | ready | hesitant | 1 / 4 / 0 | validation-necessaire ✗ | schéma de la réponse 200 non défini | Le schéma de réponse 200 (format du corps, nom des champs) n'est pas défini, le chemin d'accès à la spécification OpenAPI et l'emplacement d'implémentation ne sont pas précisés, et la notion de stock à jour n'est pas explicitée. |
| v009 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | rôle, comportement UI et 403 côté endpoint précisés |  |
| v013 | ready | hesitant | 0 / 5 / 0 | validation-necessaire ✗ | délais 7 et 14 jours et comportements précisés ; contenu de l'e-mail et jours calendaires à supposer | Le contenu de l'email de relance, la source de l'arbre hiérarchique (N+1), la définition de « sans action » et le comportement si aucun supérieur n'existe ne sont pas précisés. |
| v014 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | conditions d'annulation, statut, recrédit et message d'erreur précisés |  |
| v016 | ready | sure | 0 / 5 / 0 | validation-necessaire ✗ | contenu de la ligne d'audit et lecture seule précisés | Le mécanisme d'inaltérabilité (lecture seule, append-only) n'est pas défini et le périmètre (entrées/sorties de tous les utilisateurs ou des seuls magasiniers) reste ambigu. |
| v017 | not-ready | hesitant | 2 / 3 / 0 | validation-necessaire (instable) | forme et destinataire de l'« alerte » non précisés (écran, e-mail, notification) |  |
| v019 | ready | hesitant | 0 / 5 / 0 | validation-necessaire ✗ | alerte visuelle sur la référence, seuil et déclenchement testables | Le type d'alerte visuelle (emplacement, composant, canal) n'est pas défini, la description reste vague, et le seuil ainsi que la règle de déclenchement (une seule fois ou à chaque sortie) ne sont pas explicités. |
| v022 | ready | sure | 1 / 4 / 0 | validation-necessaire ✗ | contenu du PDF précisé, cas sans dépense couvert | La « mise en page standard » n'est pas définie et le format exact du PDF ainsi que la bibliothèque de génération restent à trancher. |
| v023 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | seuil, durée, remise à zéro et message neutre précisés |  |
| v024 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | endpoint, codes 200/404 et donnée renvoyée précisés |  |
| v028 | ready | hesitant | 0 / 5 / 0 | validation-necessaire ✗ | barème fiscal officiel et catégorie précisés ; source du véhicule à supposer existante | Le barème fiscal à appliquer n'est pas précisé (barème par puissance fiscale, année, tables de valeurs) et doit être fourni ou tranché avant implémentation. |
| v031 | ready | sure | 3 / 2 / 0 | ai-ready (instable) | formule, arrondi, période et cas chiffrés précisés |  |
| v033 | ready | hesitant | 2 / 3 / 0 | validation-necessaire ✗ | comportement au-delà de 10 000 articles non défini | Le comportement au-delà de 10 000 articles n'est pas spécifié, et le format des dates et l'emplacement de l'option d'export restent implicites. |
| v035 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | source de la version et texte affiché précisés |  |
| v038 | ready | sure | 2 / 3 / 0 | validation-necessaire ✗ | jours fériés légaux connus, exemple chiffré vérifiable | La liste des jours fériés à exclure n'est pas fournie (source ou calcul des fériés mobiles comme Pâques ou Lundi de Pentecôte) et le traitement des demi-journées n'est pas spécifié. |
| v039 | ready | hesitant | 0 / 5 / 0 | validation-necessaire ✗ | affichage du motif, resoumission et changement de statut précisés ; canal de notification à supposer existant | Les champs modifiables de la note, le canal de notification du manager et le comportement de l'interface ne sont pas précisés. |
| v041 | ready | hesitant | 0 / 5 / 0 | validation-necessaire ✗ | entrée, refus RG-01 et journalisation RG-03 précisés ; scan supposé en saisie clavier | La méthode de « signature » du mouvement n'est pas définie, et l'intégration du scanner de code-barres (matériel, format, dépendance externe) n'est pas précisée. |
| v043 | ready | sure | 2 / 3 / 0 | validation-necessaire ✗ | case, valeur par défaut et effet sur le job existant précisés | La valeur de la case et le comportement pour les comptes existants (migration, valeur par défaut) ne sont pas précisés, seuls les nouveaux comptes sont couverts. |
| v046 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | règle de recherche, seuil de 3 caractères, limite et tri précisés |  |
| v047 | ready | sure | 4 / 1 / 0 | ai-ready (instable) | endpoint, invalidation serveur et codes 401/204 précisés |  |

## Avant arbitrage (`labels.csv` seul, même règle de seuil)

## Seuil d'acceptation (5 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | **ÉCHEC** (7 sur 21, 33 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 5 sur 5 (100 %) |
| stabilité ≥ 90 % (toutes étiquettes) | **ÉCHEC** (70 %) |

**Seuil bloquant : atteint**

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict | stabilité |
|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 83 % | 20 | 0 (0 %) | 21 | 7 (33 %) | 0 | 66 % |
| `hesitant` (indicatif) | 44 % | 4 | 0 (0 %) | 5 | 5 (100 %) | 0 | 89 % |
| toutes | 76 % | 24 | 0 (0 %) | 26 | 12 (46 %) | 0 | 70 % |
