<!-- SPDX-FileCopyrightText: 2026 Chams Bensalem -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Contexte qui répond (mesure A, condition AR)

Seul le bloc ci-dessous est envoyé comme contexte projet.

```
Contexte projet : application interne « Espace salarié » de l'entreprise, modules Congés, Notes de frais, Stocks (entrepôt) et Comptes utilisateurs. Stack : backend Java / Spring Boot, frontend Angular, base PostgreSQL, déploiement en conteneurs. Conventions : identifiants techniques en anglais, textes d'interface en français, dates affichées au format JJ/MM/AAAA ; chaque story livre ses tests automatisés ; revue de code systématique. Équipe : quatre développeurs et un PO.

Règles et choix du projet :
1. Connexion d'entreprise : Microsoft Entra ID en OpenID Connect ; l'application est déjà enregistrée, identifiant client et secret fournis dans la configuration.
2. SMS : envoyés par Brevo (API transactionnelle), clé d'API fournie dans la configuration ; expéditeur « RH-Conges » ; texte du rappel de congé : « Rappel : votre congé commence demain (JJ/MM/AAAA). »
3. Notifications : une notification est un e-mail envoyé par le service d'envoi existant et une entrée dans le centre de notifications de l'application ; son texte est celui que donne la story, sinon un libellé court de l'événement avec un lien vers l'objet concerné. Le manager référent d'un salarié est son manager direct (champ « manager » du compte).
4. Alertes de stock : adressées, sous forme de notification, aux utilisateurs ayant le rôle « gestionnaire de stock ».
5. Glossaire — demande de congé urgente : demande dont le premier jour tombe moins de 5 jours ouvrés après sa soumission ; le système la marque automatiquement, le salarié ne la choisit pas.
6. Archivage : les justificatifs sont archivés dans Amazon S3 Glacier Deep Archive, dans le compte AWS de l'entreprise (région eu-west-3), avec S3 Object Lock en mode compliance et une rétention de 10 ans ; le bucket et le rôle d'accès sont fournis dans la configuration.
7. Mots de passe des comptes locaux : pour appliquer les recommandations de l'ANSSI, le projet retient : au moins 12 caractères, avec une majuscule, une minuscule, un chiffre et un caractère spécial ; refus des 10 000 mots de passe les plus courants (liste versionnée dans le dépôt).
```
