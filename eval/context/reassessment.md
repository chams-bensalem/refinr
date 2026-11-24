<!-- SPDX-FileCopyrightText: 2026 Chams Bensalem -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Contexte de la réévaluation (critère A.f)

Seul le bloc ci-dessous est envoyé comme contexte projet.

```
Contexte projet : application interne « Espace salarié ».
- Statuts des demandes (congés, notes de frais) : brouillon, soumise, validée, refusée, annulée. Une demande est « en attente » quand son statut est « soumise ». La validation est le passage au statut « validée » ; sa date est celle enregistrée dans l'historique de la demande.
- Dates de référence : une période (« sur les 30 derniers jours ») porte sur la date de l'événement compté (la date de validation pour une demande validée) ; les listes de « dernières demandes » sont triées par date de soumission, de la plus récente à la plus ancienne.
- Délai de validation : de la soumission à la validation ; les demandes refusées ou annulées sont exclues. L'équipe d'un salarié est celle de son manager direct.
- Catégories de dépenses (liste fixe) : transport, hébergement, repas, carburant, fournitures, autre. Un justificatif est obligatoire pour toute dépense.
- Commandes fournisseurs : statuts commandée, expédiée, reçue, annulée. La réception partielle n'est pas un statut : une commande reste « expédiée » tant que toutes ses lignes ne sont pas reçues, et passe à « reçue » à la dernière.
```
