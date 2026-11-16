# Test des prompts 2 et 3 (exécutés par Claude, pas par Refinr)

## Prompt 2 : verdict séparé, sur les 13 stories où Haiku ou Opus contredisaient les étiquettes

| id | étiquette (labels.csv) | verdict v3 | raison v3 |
|---|---|---|---|
| v009 | ready / sure | true | Rôle autorisé, comportement de l'écran et refus 403 de l'endpoint définis. |
| v013 | ready / hesitant | true | Délais de 7 et 14 jours et réattribution au supérieur définis ; contenu de l'e-mail = convention. |
| v014 | ready / sure | true | Condition d'annulation, statut, recrédit et refus définis. |
| v016 | ready / sure | true | Contenu de la ligne d'audit et lecture seule définis ; le mécanisme d'inaltérabilité est un choix technique. |
| v017 | not-ready / hesitant | false | Comment l'alerte de stock bas est-elle présentée, et à qui ? |
| v019 | ready / hesitant | true | Seuil, déclenchement et alerte visuelle sur la référence vérifiables. |
| v022 | ready / sure | true | Contenu du PDF et cas sans dépense définis ; « mise en page standard » = convention. |
| v024 | ready / sure | true | Endpoint, codes 200/404 et donnée renvoyée (quantité) définis ; le nom du champ est une convention. |
| v028 | ready / hesitant | true | Barème fiscal officiel et catégorie définis ; véhicule déclaré supposé existant. |
| v038 | ready / sure | true | Jours fériés légaux de métropole connus, exemple chiffré vérifiable. |
| v039 | ready / sure | true | Affichage du motif, resoumission, changement de statut et notification définis. |
| v041 | ready / hesitant | **false** | Que signifie « mouvement signé » en RG-03 : identifiant de l'utilisateur, ou signature électronique ? |
| v043 | ready / sure | true | Case, valeur par défaut et effet sur le job existant définis. |

Accord avec les étiquettes : 12 sur 13. Seul écart : v041 (étiquette hésitante).

## Prompt 3 : réévaluation

1. « Order export », précision : « point-virgule, UTF-8 avec BOM » → **true** : « Le séparateur et l'encodage sont maintenant fixés ; tous les critères sont vérifiables. »
2. « Customer data export », précision : « format JSON, téléchargement dans le navigateur » → **false** : « Quels champs du compte client l'export doit-il contenir, une fois la revue RGPD terminée ? »
   (la précision tranche deux points, il en reste d'autres : la story reste bloquée et donne la question suivante)
