# Mesure du verdict — anthropic (claude-opus-5-5)

- Début : 2026-11-16T14:03:32.140332Z ; cible `anthropic-check` ; `describe()` : `anthropic (claude-opus-5-5)` ; paramètres optionnels envoyés : aucun
- Prompt `verdict-v2`, lots de 10 stories (production), 2 réparation(s) au plus, `max-retries=0`, ordre mélangé
  par exécution (graine 20261116 + numéro d'exécution) ; délai PT1M30S, 4096 tokens de sortie au plus
- **Référence étiquetée par une IA** (Claude, `labeled_by` = [claude-po]), pas par un humain.
- **Circularité** : modèle Claude évalué contre des étiquettes posées par Claude ; résultats indicatifs.

- Appels : 5 (0 en échec) ; tokens facturés : 15349 en entrée, 6738 en sortie ; coût : 0.1962 USD

- Étiquettes : `labels.csv` corrigé par `labels-adjudication.csv` (v004 ready/sure → ready/hesitant, v020 ready/sure → not-ready/sure, v033 ready/sure → ready/hesitant).
  **Arbitrage fait après avoir vu les réponses des modèles** : il biaise la mesure en leur faveur.

## Seuil d'acceptation (1 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | OK (3 sur 18, 17 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 7 sur 7 (100 %) |
| stabilité ≥ 90 % (toutes étiquettes) | non mesurée (une seule exécution) |

**Seuil bloquant : atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict | stabilité |
|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 92 % | 21 | 0 (0 %) | 18 | 3 (17 %) | 0 | – |
| `hesitant` (indicatif) | 36 % | 4 | 0 (0 %) | 7 | 7 (100 %) | 0 | – |
| toutes | 80 % | 25 | 0 (0 %) | 25 | 10 (40 %) | 0 | – |

## Matrices de confusion (verdict majoritaire)

Étiquettes `sure`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 15 | 3 | 0 |
| not-ready | 0 | 21 | 0 |

Étiquettes `hesitant`

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 0 | 7 | 0 |
| not-ready | 0 | 4 | 0 |

Toutes les étiquettes

| étiquette \ verdict | ai-ready | validation-necessaire | sans verdict |
|---|---|---|---|
| ready | 15 | 10 | 0 |
| not-ready | 0 | 25 | 0 |

## Désaccords et instabilités

| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |
|---|---|---|---|---|---|---|
| v004 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | schéma de la réponse 200 non défini | Le schéma de réponse (nom et format des champs), le contrat OpenAPI de référence et les exigences d'authentification ne sont pas fournis. |
| v009 | ready | sure | 0 / 1 / 0 | validation-necessaire ✗ | rôle, comportement UI et 403 côté endpoint précisés | Ambiguïté pour un non-Gestionnaire : le critère semble lui refuser tout l'écran Inventaire (403) alors que la description ne vise que l'export, et le traitement du bouton (masqué ou désactivé) n'est pas précisé. |
| v013 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | délais 7 et 14 jours et comportements précisés ; contenu de l'e-mail et jours calendaires à supposer | Plusieurs points restent à préciser : le contenu et le modèle de l'email de relance, la source de l'arbre hiérarchique, le cas d'un manager sans N+1 et le choix entre jours calendaires et ouvrés. |
| v014 | ready | sure | 0 / 1 / 0 | validation-necessaire ✗ | conditions d'annulation, statut, recrédit et message d'erreur précisés | Il reste à trancher si l'annulation est possible le jour même du premier jour de congé, quels statuts sont annulables (en attente, refusée) et quel est le texte du message d'erreur. |
| v019 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | alerte visuelle sur la référence, seuil et déclenchement testables | La forme et l'emplacement de l'alerte, sa persistance ou son acquittement, le cas d'égalité au seuil et le mode de définition du seuil ne sont pas précisés. |
| v024 | ready | sure | 0 / 1 / 0 | validation-necessaire ✗ | endpoint, codes 200/404 et donnée renvoyée précisés | Le format de réponse (schéma JSON, nom du champ quantité), l'authentification et la source de données du stock courant ne sont pas précisés. |
| v028 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | barème fiscal officiel et catégorie précisés ; source du véhicule à supposer existante | Le barème fiscal n'est pas précisé : il manque l'année de référence, les tranches de distance annuelle, les catégories de puissance fiscale et le mode de déclaration du véhicule, et les critères ne donnent aucun exemple chiffré vérifiable. |
| v033 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | comportement au-delà de 10 000 articles non défini | Le comportement au-delà de 10 000 articles n'est pas défini, ni le format de date ou l'emplacement exact de l'option d'export. |
| v039 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | affichage du motif, resoumission et changement de statut précisés ; canal de notification à supposer existant | Le canal de notification du manager n'est pas précisé, pas plus que les éléments modifiables de la note ou la conservation de l'historique du refus ; ce sont des choix produit qui restent à trancher. |
| v041 | ready | hesitant | 0 / 1 / 0 | validation-necessaire ✗ | entrée, refus RG-01 et journalisation RG-03 précisés ; scan supposé en saisie clavier | La notion de « signature » du mouvement (RG-03) n'est pas définie (simple identifiant ou signature électronique), et le format des codes-barres ainsi que le matériel ou la méthode de scan ne sont pas précisés. |

## Avant arbitrage (`labels.csv` seul, même règle de seuil)

## Seuil d'acceptation (1 exécution(s))

| critère | résultat |
|---|---|
| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | OK (0) |
| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | OK |
| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | **ÉCHEC** (6 sur 21, 29 %) |
| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 5 sur 5 (100 %) |
| stabilité ≥ 90 % (toutes étiquettes) | non mesurée (une seule exécution) |

**Seuil bloquant : atteint** — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %).

## Taux

| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict | stabilité |
|---|---|---|---|---|---|---|---|
| `sure` (bloquant) | 85 % | 20 | 0 (0 %) | 21 | 6 (29 %) | 0 | – |
| `hesitant` (indicatif) | 44 % | 4 | 0 (0 %) | 5 | 5 (100 %) | 0 | – |
| toutes | 78 % | 24 | 0 (0 %) | 26 | 11 (42 %) | 0 | – |
