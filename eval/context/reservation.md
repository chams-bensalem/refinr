<!-- SPDX-FileCopyrightText: 2026 Chams Bensalem -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Contexte « intranet » (mesure B : reservation-salles)

Seul le bloc ci-dessous est envoyé comme contexte projet.

```
Contexte projet : intranet du siège. Stack : Java / Spring Boot, Angular, PostgreSQL.
- Notifications : toute notification de l'intranet est un message Teams envoyé par le bot de l'intranet, déjà en service ; aucun e-mail.
- Agenda : l'intégration Outlook passe par Microsoft Graph, avec l'application « Intranet Siège » déjà enregistrée (permission Calendars.ReadWrite accordée, identifiants dans la configuration) ; une réservation crée un événement dans l'agenda du salarié, avec la salle comme lieu.
- Check-in : le salarié confirme sa présence avec le bouton « Je suis là » de sa réservation dans l'intranet, à partir de l'heure de début.
- Module Parking : réservation à la journée des 40 places de parking du siège par les salariés qui ont déclaré un véhicule.
```
