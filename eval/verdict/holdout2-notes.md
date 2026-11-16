<!-- SPDX-FileCopyrightText: 2026 Chams Bensalem -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Second jeu de contrôle (`holdout2.jsonl`) : intention de l'auteur

**À lire après l'étiquetage** de `holdout2-labels.csv`, pour ne pas l'orienter. Ces notes ne servent pas à la mesure.

15 stories synthétiques (v066 à v080), écrites le 2026-11-16 **après** la mesure v3 et le bloc READINESS v3.1, et
avant toute mesure v3.1 : elles visent les règles ajoutées en v3.1 (cas limites, termes métier ambigus, noms de champs).
Rédigées par Claude, auteur du prompt v3.1 : même biais possible que les étiquettes (référence IA).

Intention : 8 prêtes, 7 pas prêtes ; 7 cas limites (v066, v067, v068, v073, v074, v077, v080).

| id | intention | catégorie visée |
|---|---|---|
| v066 | ready | **cas limite non couvert, sans point sensible** : ordre de deux comptes rendus à la même date |
| v067 | not-ready | **cas limite qui touche un montant** : remboursement d'un article d'une commande avec code promo (répartition de la remise non définie) |
| v068 | not-ready | **cas limite qui touche les droits d'accès** : salarié qui change d'équipe, accès de l'ancien et du nouveau manager à ses anciennes notes |
| v069 | ready | **nom de champ absent, information définie** : contenu de l'export JSON listé, noms des champs non donnés |
| v070 | not-ready | **terme métier ambigu** : « client actif » (compte ouvert, connexion récente, abonné ?) |
| v071 | not-ready | **terme métier ambigu** : dossier « complet » (pièces requises non listées) |
| v072 | ready | courte et complète (limite, affichage, blocage) |
| v073 | ready | second **cas limite non couvert, sans point sensible** : traitement des accents dans la recherche |
| v074 | not-ready | cas limite qui touche les **données personnelles** : sort des données du salarié parti non défini |
| v075 | ready | dépendance externe définie : API publique nommée, sans clé, règle de score et cas d'échec |
| v076 | not-ready | format d'échange imposé mais non fourni (contrat du logisticien), serveur non décrit |
| v077 | ready | montant entièrement défini, cas limite (50,00 €) couvert par un critère |
| v078 | ready | terme qui ressemble à un terme ambigu (« validée ») mais défini dans la story |
| v079 | ready | règle complète (durée, seuil d'affichage, message, blocage) ; le singulier de « 1 jours » est une convention |
| v080 | not-ready | cas limite qui touche les **données personnelles** et un choix produit : quelles données (adresse, consentements) garder en cas de conflit |
