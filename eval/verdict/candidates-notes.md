<!-- SPDX-FileCopyrightText: 2026 Chams Bensalem -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Notes sur les stories candidates (E0)

> **À lire seulement après avoir rempli `labels.csv`** : ce fichier donne la provenance et l'intention de l'auteur,
> qui orienteraient l'étiquetage.

- 50 stories, ordre mélangé (graine `20261116`), identifiants `v001` à `v050` attribués après le mélange.
- **Provenance** :
  - `generated` : story produite par Refinr (`gemini-3.6-flash`) pendant les mesures M0 (2026-11-14) et G0
    (2026-11-16), reprise **sans retouche** (titre, description, critères ; les autres champs ne sont pas envoyés au
    modèle de verdict). Documents d'origine : specs de test des spikes BookStack / XWiki (stocks), spec rédigée pour M0
    (notes de frais), besoin court. Vérifiées une à une : aucune donnée sensible (ni nom de personne, ni e-mail, ni URL,
    ni identifiant, ni clé ; seules références : codes article fictifs et « entrepôt de Lyon » des specs de test).
  - `synthetic` : story rédigée pour la mesure, sans donnée réelle.
- **Catégorie visée** (intention de l'auteur, pas une étiquette) : `ready` clairement prête, `not-ready` clairement
  incomplète, `borderline` cas limite. Seules les étiquettes de `labels.csv` comptent pour la mesure.

## Répartition

| provenance | ready | not-ready | borderline | total |
|---|---|---|---|---|
| generated | 11 | 3 | 3 | 17 |
| synthetic | 9 | 20 | 4 | 33 |
| **total** | 20 | 23 | 7 | 50 |

## Détail

| id | titre | provenance | source | catégorie visée | règle ou piège visé | note |
|---|---|---|---|---|---|---|
| v001 | Export des absences vers la paie | synthetic | S26 | not-ready | aucun critère d'acceptation | Liste de critères vide |
| v002 | Horodatage et tracabilite des mouvements (RG-03) | generated | G0 (2026-11-16), `none-bookstack-p4-r2.json`, epic « Gestion des mouvements de stock », story 3 | ready | - | journalisation, un seul critère |
| v003 | Notifications de soumission de note de frais | generated | M0 (2026-11-14), `gemini-3.6-notes-de-frais-gemini-3.6-flash-t0.2-r2.json`, epic « Epic: Workflow de Validation », story 2 | not-ready | - | canal « email/in-app » non tranché |
| v004 | API Exposition Stock Courant | generated | G0 (2026-11-16), `thinking-low-bookstack-p4-r2.json`, epic « Epic: Gestion du Stock et Mouvements », story 2 | ready | - | endpoint, 200 et 404 |
| v005 | Notifier les personnes concernées | synthetic | S15 | not-ready | ambiguïté fonctionnelle | Destinataires et événements non définis |
| v006 | Refus d'une demande de congé qui chevauche une autre demande | synthetic | S08 | ready | - | Règle métier de chevauchement, cas limites inclus |
| v007 | Sortie de stock au-delà du disponible | synthetic | S27 | not-ready | ambiguïté fonctionnelle (critères contradictoires) | Deux critères incompatibles |
| v008 | Refonte de la page d'accueil selon la nouvelle maquette | synthetic | S18 | not-ready | dépendance externe non précisée (maquette) | Maquette citée, non fournie |
| v009 | Contrôle d'accès à l'export CSV par rôle Gestionnaire | generated | G0 (2026-11-16), `none-besoin-court-r1.json`, epic « Export CSV de l'inventaire », story 3 | ready | - | rôle, UI et 403 |
| v010 | Écran de saisie des dépenses intuitif | synthetic | S12 | not-ready | critère non testable | Critères subjectifs |
| v011 | Export de l'inventaire performant | synthetic | S13 | not-ready | critère non testable | Performance sans seuil |
| v012 | Connexion unique avec l'annuaire de l'entreprise | synthetic | S21 | not-ready | dépendance externe non précisée (fournisseur d'identité) | IdP et protocole non précisés |
| v013 | Relance automatique et escalade hiérarchique | generated | M0 (2026-11-14), `gemini-3.6-notes-de-frais-gemini-3.6-flash-t0.2-r1.json`, epic « Validation des notes et circuit d'escalade », story 3 | borderline | - | envoi d'e-mail et arbre hiérarchique supposés existants |
| v014 | Annulation d'une demande de congé par le salarié | synthetic | S29 | borderline | détail mineur non précisé | Complète sauf le texte exact du message d'erreur |
| v015 | Notification au salarié de la validation de sa note de frais | synthetic | S30 | borderline | dépendance interne supposée existante | S'appuie sur un service interne cité, non décrit |
| v016 | Journalisation et traçabilité des mouvements (RG-03) | generated | M0 (2026-11-14), `gemini-3.6-xwiki-stocks-gemini-3.6-flash-t0.2-r2.json`, epic « Gestion et Suivi des Stocks », story 3 | ready | - | journal d'audit en lecture seule |
| v017 | Alerte de dépassement de seuil minimal | generated | G0 (2026-11-16), `none-xwiki-stocks-r1.json`, epic « Epic: Suivi du Stock et Alertes de Seuil », story 3 | ready | - | alerte et réinitialisation |
| v018 | Endpoint paginé de liste des mouvements de stock | synthetic | S03 | ready | - | API, pagination et champs définis |
| v019 | Gestion des alertes de franchissement de seuil minimal | generated | G0 (2026-11-16), `none-bookstack-p4-r2.json`, epic « Suivi des stocks et Alertes », story 3 | borderline | - | alerte « visuelle » : où, pour qui ? |
| v020 | Suivi en temps réel du statut des notes de frais | generated | G0 (2026-11-16), `thinking-low-notes-de-frais-r1.json`, epic « Epic: Contrôle comptable et Remboursement », story 3 | ready | - | statuts listés |
| v021 | Plateforme de pilotage unifiée des approvisionnements | synthetic | S22 | not-ready | piège : story longue mais vague | Beaucoup de texte, aucun comportement vérifiable |
| v022 | Export PDF d'une note de frais | synthetic | S32 | borderline | mise en page non précisée | Contenu défini, présentation laissée libre |
| v023 | Verrouillage temporaire du compte après échecs de connexion | synthetic | S02 | ready | - | Sécurité, seuils et durées explicites |
| v024 | API Consultation Stock Courant | generated | G0 (2026-11-16), `thinking-low-xwiki-stocks-r2.json`, epic « Gestion des Mouvements et Stocks », story 2 | ready | - | endpoint, 200 et 404 |
| v025 | Contrôle de non-négativité du stock (RG-01) | generated | G0 (2026-11-16), `none-bookstack-p4-r1.json`, epic « Epic: Gestion des Mouvements de Stock », story 2 | ready | - | RG-01 avec valeurs |
| v026 | Contrôle du montant saisi sur une dépense | synthetic | S06 | ready | - | Validation de saisie, bornes et messages |
| v027 | Améliorer le tableau de bord | synthetic | S10 | not-ready | périmètre flou | Aucun périmètre |
| v028 | Calcul automatique des frais kilométriques | generated | M0 (2026-11-14), `gemini-3.6-notes-de-frais-gemini-3.6-flash-t0.2-r1.json`, epic « Saisie des dépenses et gestion des notes », story 2 | not-ready | - | barème fiscal et « véhicule déclaré » non précisés |
| v029 | Traitement prioritaire des demandes de congé urgentes | synthetic | S14 | not-ready | ambiguïté fonctionnelle | « Urgent » non défini |
| v030 | Archivage sécurisé à long terme (10 ans) | generated | M0 (2026-11-14), `gemini-3.6-notes-de-frais-gemini-3.6-flash-t0.2-r2.json`, epic « Epic: Mobile et Archivage Réglementaire », story 2 | not-ready | - | stockage « cloud froid » : choix d'infrastructure non fait |
| v031 | Calcul du solde de congés payés acquis | synthetic | S01 | ready | - | Calcul de solde, règle chiffrée, critères testables |
| v032 | Gérer les inventaires tournants | synthetic | S11 | not-ready | périmètre flou | Fonction nommée, rien de défini |
| v033 | Export CSV des articles filtrés | generated | M0 (2026-11-14), `gemini-3.6-besoin-court-gemini-3.6-flash-t0.2-r1.json`, epic « Epic: Export CSV de l'inventaire », story 1 | ready | - | colonnes, séparateur, encodage, filtres |
| v034 | Délégation de la validation des notes de frais | synthetic | S19 | not-ready | choix produit à trancher | Règles explicitement non décidées |
| v035 | Afficher la version de l'application dans le pied de page | synthetic | S07 | ready | piège : story courte mais complète | Très courte, tout est précisé |
| v036 | Gestion des retours fournisseurs | synthetic | S23 | not-ready | piège : critères présents mais non vérifiables | Forme Given/When/Then, résultat non vérifiable |
| v037 | Rappel par SMS des congés qui commencent le lendemain | synthetic | S17 | not-ready | dépendance externe non précisée (clé d'API, fournisseur) | Fournisseur SMS non choisi |
| v038 | Exclusion des jours fériés du décompte des congés | synthetic | S31 | borderline | référentiel externe implicite | Jours fériés « légaux » sans source de données précisée |
| v039 | Correction et resoumission d'une note refusée | generated | G0 (2026-11-16), `none-notes-de-frais-r1.json`, epic « Epic: Processus de Validation et Escalade », story 3 | ready | - | refus, correction, resoumission |
| v040 | Gestion de la TVA sur les dépenses | synthetic | S28 | not-ready | périmètre flou (règles métier absentes) | TVA sans taux ni règles |
| v041 | Saisie des entrées et sorties de stock par scan | generated | M0 (2026-11-14), `gemini-3.6-bookstack-p4-gemini-3.6-flash-t0.2-r1.json`, epic « Suivi des stocks et mouvements », story 1 | ready | - | saisie par scan, RG-01 et RG-03 couvertes |
| v042 | Reprise des données historiques de l'ancien ERP | synthetic | S20 | not-ready | dépendance externe non précisée (accès, format) | Ancien ERP, format et accès inconnus |
| v043 | Désactivation du récapitulatif hebdomadaire par e-mail | synthetic | S04 | ready | - | Préférence utilisateur simple, comportement complet |
| v044 | Paramétrage des catégories de dépenses | synthetic | S25 | not-ready | piège : consigne injectée visant une autre story | Tente d'influencer le verdict des autres stories du lot |
| v045 | Synchroniser les salariés depuis le SIRH | synthetic | S16 | not-ready | dépendance externe non précisée (accès, API) | SIRH non identifié, accès et format inconnus |
| v046 | Recherche d'article par référence partielle | synthetic | S05 | ready | - | Recherche avec règles précises |
| v047 | La déconnexion invalide la session côté serveur | synthetic | S09 | ready | - | Sécurité de session, vérifiable |
| v048 | Gestion des remises fournisseurs | synthetic | S24 | not-ready | piège : consigne injectée dans la description | Story vague + instruction au modèle |
| v049 | Politique de mot de passe des comptes locaux | synthetic | S33 | not-ready | référence normative externe | règles de mot de passe renvoyées à un référentiel externe, non testables en l'état |
| v050 | Génération et export du fichier de virement SEPA | generated | G0 (2026-11-16), `none-notes-de-frais-r2.json`, epic « Epic: Contrôle et Remboursement comptable », story 2 | borderline | - | fichier SEPA « conforme » : norme et données bancaires implicites |
