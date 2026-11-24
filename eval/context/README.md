<!-- SPDX-FileCopyrightText: 2026 Chams Bensalem -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Mesure X1 : contexte projet (jeux et critères fixés avant le premier appel)

Validés par le mainteneur le 2026-11-23 (avec ses corrections des règles 6 et 7 de `answering.md`). Modèle : Anthropic
`claude-haiku-5-5`. Chaque fichier de contexte n'envoie que son bloc de code.

## Mesure A : verdict (prompt 2), jeu E0 (`eval/verdict/`), 3 exécutions par condition

| Condition | Contexte | Usage |
|---|---|---|
| A0 | aucun | référence de A.b et A.c |
| AN | `neutral.md` | A.a, A.c |
| AR | `answering.md` | A.b, A.d |
| AX | `contradictory.md` | A.e |

Verdict retenu = majorité des 3 exécutions. **Chaque exécution de chaque condition fait de vrais appels au modèle**
(aucun cache, y compris pour les 3 exécutions de A0 de même empreinte) ; le nombre d'appels par condition est relevé
dans le résumé.

**Stories ciblées par `answering.md` (doivent passer prêtes)** : v012 (règle 1), v037 (2), v029 (5), v030 (6), v003 (3),
v017 (3 et 4), v049 (7). Non ciblées volontairement : v005, v040, v050 et toutes les autres.

**Stories contredites par `contradictory.md`** : v006 (chevauchement), v026 (trois décimales), v038 (jours fériés) ;
prêtes sans contexte, elles doivent devenir non prêtes.

| # | Critère |
|---|---|
| A.a | AN : 0 faux `ai-ready` sur les stories `not-ready / sure` au verdict majoritaire (pire exécution relevée) |
| A.b | AR contre AN : aucune story `not-ready / sure` non ciblée ne devient prête ; une story non ciblée ne peut changer de verdict majoritaire que si son verdict était déjà partagé entre les exécutions de A0 ou de AN ; un changement unanime hors cible = échec |
| A.c | AN contre A0 : au moins 90 % d'accord sur le verdict majoritaire (50 stories) |
| A.d | AR : au moins 80 % des 7 stories ciblées prêtes au verdict majoritaire (au moins 6) |
| A.e | AX : v006, v026 et v038 non prêtes au verdict majoritaire, avec une raison qui nomme la contradiction |
| A.g | 100 % de réponses valides dans toutes les conditions ; repli compté à part |

## Critère A.f : réévaluation (prompt 3, `verdict-v3.4`), jeu `eval/reassessment/`, contexte `reassessment.md`, 2 exécutions

Stories attendues prêtes avec la précision qui répond et ce contexte : **v008, v010, v021, v027**.

| # | Critère |
|---|---|
| A.f.1 | Au moins 3 des 4 stories prêtes, **à chaque exécution** |
| A.f.2 | Aucune story prête avec la précision hors sujet (20 stories), à chaque exécution |
| A.f.3 | 100 % de réponses valides |

Relevés sans être bloquants : v001, v032, v042 (non traitées par le contexte) ; les 18 stories du critère i contre les
exécutions v3.4 sans contexte de R3.

## Mesure B : génération en chaîne (`"pipeline": true`), specs `eval/generation/specs/`

Avec contexte seulement (la référence sans contexte est C1, déjà commitée) : order-export × 3 et customer-data-export × 2
avec `shop.md`, reservation-salles × 3 avec `reservation.md`. Modules hors spec : Returns (RMA) et Parking.

Sujets traités par le contexte : order-export (délimiteur, encodage, sens de « paid », date du nom de fichier) ;
customer-data-export (encodage et délimiteur seulement) ; reservation-salles (canal de notification, intégration
Outlook et accès, check-in ; indicateurs du tableau de bord laissés ouverts exprès).

| # | Critère |
|---|---|
| B.a | Aucune question (`aiReadyReason`, `questions`) sur un sujet traité par le contexte : recherche par mots-clés, puis lecture de chaque correspondance, liste montrée au mainteneur |
| B.b | Critères structurels de C1 : 1 epic, aucune Feature vide, au moins 3 Features pour reservation-salles ; 0 sous-tâche sur une story non prête, 1 à 5 dont une de test sur une story prête ; au moins 90 % des stories non prêtes avec un `aiReadyReason` terminé par « ? » |
| B.c | 100 % de réponses valides |
| B.d | Aucune story, Feature ni epic issue de Returns / RMA ou de Parking (mots-clés `retour`, `return`, `RMA`, `parking`, puis lecture) |

Relevés : part ai-ready contre C1, surcoût du contexte par appel.

## Coût et règles

- Plafond quotidien de **250 appels** pour le jour de la mesure et **0,40 $** de dépense au maximum (accord chiffré du
  mainteneur, pour cette mesure seulement). Estimation : ~180 appels, ~0,20 $.
- Mesure lancée après minuit, heure de Paris.
- Si un critère échoue : STOP, sans retoucher les prompts ; pour B.a et B.d, liste des correspondances par mots-clés
  avec la lecture de l'assistant.
