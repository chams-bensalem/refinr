<!-- SPDX-FileCopyrightText: 2026 Chams Bensalem -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Contexte contradictoire (mesure A, condition AX, critère A.e)

Seul le bloc ci-dessous est envoyé comme contexte projet.

```
Contexte projet : application interne « Espace salarié » de l'entreprise, modules Congés, Notes de frais, Stocks (entrepôt) et Comptes utilisateurs. Stack : backend Java / Spring Boot, frontend Angular, base PostgreSQL, déploiement en conteneurs. Conventions : identifiants techniques en anglais, textes d'interface en français, dates affichées au format JJ/MM/AAAA ; chaque story livre ses tests automatisés ; revue de code systématique. Équipe : quatre développeurs et un PO.

Règles du projet :
- Un salarié peut poser des demandes de congé qui se chevauchent : le chevauchement n'est jamais refusé, les jours communs ne sont décomptés qu'une fois.
- Les montants des dépenses se saisissent avec trois décimales, exigence de la comptabilité.
- Les jours fériés compris dans un congé sont décomptés comme des jours de congé.
```
