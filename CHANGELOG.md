# Changelog

Toutes les évolutions notables de Refinr sont consignées ici.
Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) · versionnage : [SemVer](https://semver.org/lang/fr/).

## [Unreleased]

### Changed — contrat REST
- `create-jira` : le serveur **recalcule** le verdict `ai-ready` de chaque story sur son contenu final (le contenu a pu
  être modifié depuis l'aperçu) ; les champs `aiReady` et `aiReadyReason` envoyés par le client sont **ignorés**.
  Verdict indisponible ⇒ `validation-necessaire`. Coût : un appel au fournisseur de verdict par story.
  La réponse renvoie les `epics` avec le verdict recalculé.
- `analyze-and-create` et `create-jira` : réponse de création avec `created` (`type`, `title`, `key`) et `failed`
  (`type`, `title`, `reason`) ; un échec n'interrompt plus la création des éléments suivants. Statut `200` si tout
  est créé, `207` si la création est partielle, `502` si rien n'a pu être créé (auparavant : `500` au premier échec,
  sans indiquer ce qui avait déjà été créé). `create-jira` renvoie aussi les `epics` avec leurs clés.
- `GET /api/v1/mvp/confluence/search` renvoie `{"success": true, "results": [{"id", "title", "url"}]}`
  au lieu de la réponse brute de Confluence (plus de `body.storage`, `_links`, `start`, `limit`…).

### Added
- `POST /api/v1/mvp/create-jira?projectKey=PROJ` : projet cible optionnel, validé comme pour `analyze-and-create`
  (`^[A-Z][A-Z0-9]{1,9}$`, `400` sinon) ; sans paramètre, le projet par défaut (`jira.project-key`).

- Descriptions Jira : conversion Markdown → ADF (titres, gras, italique, listes à puces et numérotées, code en ligne,
  blocs de code) au lieu d'un unique paragraphe de texte brut contenant du balisage wiki (`h3.`, `*…*`).

- Story points envoyés à Jira dans le champ `jira.story-points-field` (`JIRA_STORY_POINTS_FIELD`, ex. `customfield_10016`) ;
  désactivé si vide (défaut).

### Changed
- `ai.provider` vaut `ollama` par défaut partout (code, configuration d'exemple, README) ; le code utilisait `gemini`.
- Modèle Gemini configurable (`gemini.model`, défaut `gemini-2.5-flash`), utilisé par l'analyse et le health check ;
  `ai.verdict.model` vaut aussi `gemini-2.5-flash` par défaut (au lieu de `gemini-3.6-flash`).

### Security
- Recherche Confluence : la clé d'espace est validée (liste blanche, `400` sinon) et le titre est échappé dans la
  chaîne CQL ; un titre contenant `"` ne peut plus ajouter de clauses à la requête.

- La clé Gemini est envoyée dans l'en-tête `x-goog-api-key` et non plus dans l'URL (`?key=`), qui pouvait
  apparaître dans les logs et les messages d'erreur.

- Les erreurs renvoyées au client ne contiennent plus le message brut des exceptions (URL internes, corps de réponse
  des services) : message générique + `correlationId` pour les endpoints `/mvp/*`, cause courte (`HTTP 401`,
  `unreachable`…) pour `/health`.

### Removed
- Endpoint de debug `POST /api/v1/mvp/test-issue-types`.
