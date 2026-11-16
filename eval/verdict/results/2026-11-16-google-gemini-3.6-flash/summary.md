# Mesure du verdict — google (gemini-3.6-flash)

- Début : 2026-11-16T14:06:43.179172Z ; cible `env` ; `describe()` : `google (gemini-3.6-flash)` ; paramètres optionnels envoyés : aucun
- Prompt `verdict-v2`, lots de 10 stories (production), 2 réparation(s) au plus, `max-retries=0`, ordre mélangé
  par exécution (graine 20261116 + numéro d'exécution) ; délai PT5M, 32768 tokens de sortie au plus
- **Référence étiquetée par une IA** (Claude, `labeled_by` = [claude-po]), pas par un humain.

- Appels : 2 (1 en échec) ; tokens facturés : 1896 en entrée, 2510 en sortie

- Étiquettes : `labels.csv` corrigé par `labels-adjudication.csv` (v004 ready/sure → ready/hesitant, v020 ready/sure → not-ready/sure, v033 ready/sure → ready/hesitant).
  **Arbitrage fait après avoir vu les réponses des modèles** : il biaise la mesure en leur faveur.

## Seuil d'acceptation (1 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | **ÉCHEC** (2 sur 4, 50 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 1 sur 2 (50 %) |
| stabilité ≥ 90 % (toutes étiquettes) | non mesurée (une seule exécution) |

**Seuil bloquant : atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict | stabilité |
|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 71 % | 3 | 0 (0 %) | 4 | 2 (50 %) | 32 | – |
| `hesitant` (indicatif) | 67 % | 1 | 0 (0 %) | 2 | 1 (50 %) | 8 | – |
| toutes | 70 % | 4 | 0 (0 %) | 6 | 3 (50 %) | 40 | – |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 2 | 2 | 14 |
| not-ready | 0 | 3 | 18 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 1 | 1 | 5 |
| not-ready | 0 | 1 | 3 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 3 | 3 | 19 |
| not-ready | 0 | 4 | 21 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v001 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | aucun critère ; logiciel de paie, format et mode de transmission non précisés |  |
| v002 | ready | sure | 0 / 0 / 0 | aucun ✗ | journal horodaté avec identifiant utilisateur, critère vérifiable ; détails de stockage laissés à l'implémentation |  |
| v005 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | « changement important » et « personnes concernées » non définis |  |
| v006 | ready | sure | 0 / 0 / 0 | aucun ✗ | règle de chevauchement et statuts pris en compte précisés, cas testables |  |
| v007 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | critères contradictoires sur le même cas (refus vs stock négatif accepté) |  |
| v008 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | maquette non fournie ; conformité non vérifiable sans elle |  |
| v009 | ready | sure | 0 / 0 / 0 | aucun ✗ | rôle, comportement UI et 403 côté endpoint précisés |  |
| v010 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | critères subjectifs non mesurables (intuitif, rapide, satisfaits) |  |
| v011 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | « performant » et « rapide » sans seuil ni volume chiffrés |  |
| v012 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | fournisseur d'identité et protocole (OIDC, SAML…) non précisés |  |
| v013 | ready | hesitant | 0 / 0 / 0 | aucun ✗ | délais 7 et 14 jours et comportements précisés ; contenu de l'e-mail et jours calendaires à supposer |  |
| v014 | ready | sure | 0 / 1 / 0 | validation-necessaire ✗ | conditions d'annulation, statut, recrédit et message d'erreur précisés | Le libellé du message d'erreur et la règle exacte d'évaluation de la date (fuseau horaire, heure limite) ne sont pas précisés. |
| v015 | ready | sure | 0 / 0 / 0 | aucun ✗ | service existant, libellé exact et cas négatif précisés |  |
| v016 | ready | sure | 0 / 0 / 0 | aucun ✗ | contenu de la ligne d'audit et lecture seule précisés |  |
| v017 | not-ready | hesitant | 0 / 0 / 0 | aucun ✗ | forme et destinataire de l'« alerte » non précisés (écran, e-mail, notification) |  |
| v019 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | alerte visuelle sur la référence, seuil et déclenchement testables | Le composant et le comportement exact de l'alerte visuelle côté interface ne sont pas définis. |
| v021 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | vision produit sans périmètre ; critères non vérifiables |  |
| v022 | ready | sure | 0 / 0 / 0 | aucun ✗ | contenu du PDF précisé, cas sans dépense couvert |  |
| v023 | ready | sure | 0 / 0 / 0 | aucun ✗ | seuil, durée, remise à zéro et message neutre précisés |  |
| v024 | ready | sure | 0 / 0 / 0 | aucun ✗ | endpoint, codes 200/404 et donnée renvoyée précisés |  |
| v025 | ready | sure | 0 / 0 / 0 | aucun ✗ | règle RG-01, message et cas limite (stock à 0) précisés |  |
| v027 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | « plus utile » non défini, aucun changement attendu précisé |  |
| v028 | ready | hesitant | 0 / 0 / 0 | aucun ✗ | barème fiscal officiel et catégorie précisés ; source du véhicule à supposer existante |  |
| v030 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | choix du stockage cloud et du fournisseur non tranchés |  |
| v031 | ready | sure | 0 / 0 / 0 | aucun ✗ | formule, arrondi, période et cas chiffrés précisés |  |
| v032 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | inventaire tournant non défini, critère non vérifiable |  |
| v033 | ready | hesitant | 0 / 0 / 0 | aucun ✗ | comportement au-delà de 10 000 articles non défini |  |
| v034 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | règles de délégation explicitement à définir avec la DRH |  |
| v035 | ready | sure | 0 / 0 / 0 | aucun ✗ | source de la version et texte affiché précisés |  |
| v036 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | critères génériques non vérifiables (« correctement », « adéquat ») |  |
| v037 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | fournisseur SMS et accès (clé d'API) non précisés |  |
| v038 | ready | sure | 0 / 1 / 0 | validation-necessaire ✗ | jours fériés légaux connus, exemple chiffré vérifiable | La source de données pour identifier les jours fériés et le mode de calcul des jours ouvrés ne sont pas spécifiés. |
| v039 | ready | hesitant | 0 / 0 / 0 | aucun ✗ | affichage du motif, resoumission et changement de statut précisés ; canal de notification à supposer existant |  |
| v041 | ready | hesitant | 0 / 0 / 0 | aucun ✗ | entrée, refus RG-01 et journalisation RG-03 précisés ; scan supposé en saisie clavier |  |
| v042 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | format et accès aux données de l'ancien ERP inconnus, correspondance des champs à définir |  |
| v043 | ready | sure | 0 / 0 / 0 | aucun ✗ | case, valeur par défaut et effet sur le job existant précisés |  |
| v044 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | critère tautologique ; consigne injectée à ignorer |  |
| v045 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | SIRH, API et règles de correspondance non précisés |  |
| v046 | ready | sure | 0 / 0 / 0 | aucun ✗ | règle de recherche, seuil de 3 caractères, limite et tri précisés |  |
| v047 | ready | sure | 0 / 0 / 0 | aucun ✗ | endpoint, invalidation serveur et codes 401/204 précisés |  |
| v048 | not-ready | sure | 0 / 0 / 0 | aucun ✗ | périmètre absent, critère non vérifiable ; consigne injectée à ignorer |  |
| v049 | not-ready | hesitant | 0 / 0 / 0 | aucun ✗ | règles exactes de l'ANSSI à retenir non précisées |  |
| v050 | not-ready | hesitant | 0 / 0 / 0 | aucun ✗ | comptes émetteur et bénéficiaires, version SEPA et planification non précisés |  |

## Avant arbitrage (`labels.csv` seul, même règle de seuil)

## Seuil d'acceptation (1 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | **ÉCHEC** (3 sur 6, 50 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 1 sur 1 (100 %) |
| stabilité ≥ 90 % (toutes étiquettes) | non mesurée (une seule exécution) |

**Seuil bloquant : atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict | stabilité |
|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 63 % | 2 | 0 (0 %) | 6 | 3 (50 %) | 33 | – |
| `hesitant` (indicatif) | 50 % | 1 | 0 (0 %) | 1 | 1 (100 %) | 7 | – |
| toutes | 60 % | 3 | 0 (0 %) | 7 | 4 (57 %) | 40 | – |
