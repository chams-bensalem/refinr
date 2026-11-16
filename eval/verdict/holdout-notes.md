<!-- SPDX-FileCopyrightText: 2026 Chams Bensalem -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Jeu de contrôle (`holdout.jsonl`) : intention de l'auteur

**À lire après l'étiquetage** de `holdout-labels.csv`, pour ne pas l'orienter. Ces notes ne servent pas à la mesure.

15 stories synthétiques (v051 à v065), écrites le 2026-11-16 **après** la mesure E0 v2 et les prompts v3 : elles
n'ont été vues par aucun modèle et servent à vérifier que v3 ne s'est pas seulement ajusté aux 50 stories du jeu de
référence. Rédigées par Claude (auteur des prompts v3) : même biais possible que les étiquettes (référence IA).

Intention : 7 prêtes, 6 pas prêtes, 2 cas limites.

| id | intention | catégorie visée |
|---|---|---|
| v051 | ready | **détail d'implémentation laissé ouvert** (pagination par décalage ou curseur), comportement entièrement défini |
| v052 | not-ready | **décision produit manquante** : taux de remise et critère de fidélité à arbitrer |
| v053 | ready | **nom de champ non donné, contenu défini** : deux soldes, unité, règles d'accès, codes 200 / 403 / 404 |
| v054 | not-ready | **terme ambigu « signé »** : signature manuscrite scannée, électronique, case cochée ? |
| v055 | limite (plutôt not-ready) | **fonctionnalité implicite** : « consultable par les RH » suppose un écran ou une recherche d'archives non décrits |
| v056 | ready | courte mais complète : règle d'arrondi et exemples chiffrés |
| v057 | not-ready | dépendance externe non précisée : « service de référentiel » de la direction financière, ni nommé ni documenté |
| v058 | ready | notification complète : heure, fuseau, destinataire, contenu, exclusions |
| v059 | not-ready | critères non vérifiables (« rapidement », « lisible », « vision claire ») malgré une description correcte |
| v060 | ready | second **détail d'implémentation laissé ouvert** (fréquence de purge), borné par un critère vérifiable |
| v061 | not-ready | contradiction : annulable « tant qu'elle n'a pas été livrée » mais refus au statut « expédiée » |
| v062 | ready | règle de sécurité complète : durée, remise à zéro, message |
| v063 | limite (plutôt ready) | terme vague dans le titre et la description (« récentes »), défini par le critère (5 dernières) |
| v064 | not-ready | maquette citée mais ni fournie ni référencée |
| v065 | ready | intégration externe définie : source publique sans authentification, horaire, données, cas d'indisponibilité |
