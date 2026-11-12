# Changelog

Toutes les évolutions notables de Refinr sont consignées ici.
Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) · versionnage : [SemVer](https://semver.org/lang/fr/).

## [Unreleased]

### Changed — configuration IA (incompatible)
- Fournisseurs IA configurés par `refinr.llm.analysis.*` et `refinr.llm.verdict.*` (`provider` : `anthropic`,
  `google` ou `openai-compatible`, `base-url`, `model`, `api-key`, `timeout`, `max-output-tokens`…), variables
  `LLM_ANALYSIS_*` / `LLM_VERDICT_*`. Les anciennes clés (`ai.provider`, `ollama.*`, `gemini.*`, `ai.verdict.*`,
  `groq.*`, `anthropic.api-key` et `AI_PROVIDER`, `OLLAMA_*`, `GEMINI_*`, `AI_VERDICT_*` dans `.env`) sont **refusées
  au démarrage**, avec la correspondance à appliquer (cf. README, « Migration depuis l'ancienne configuration IA »).
  Ollama passe par son API compatible OpenAI : `base-url` avec `/v1`.

### Changed — contrat REST
- `analyze-preview`, `analyze-and-create`, `reassess-story` : `502` (message générique) quand la sortie de l'IA est
  inexploitable — non conforme au schéma JSON même après relances, ou tronquée par la limite de tokens (auparavant `500`).
  Un fournisseur indisponible reste un `500`.
- `GET /api/v1/health` : `services.ai` (fournisseur d'analyse) indique `provider` = `anthropic`, `google` ou
  `openai-compatible` ; `services.aiVerdict` apparaît quand un fournisseur de verdict distinct est configuré.
  Réponse mise en cache 45 s (`refinr.health.cache-ttl`).

### Added
- Clients `anthropic` (API Messages, sans streaming), `google` (Gemini) et `openai-compatible` (OpenAI, DeepSeek,
  Mistral, Groq, OpenRouter, Ollama, LiteLLM, vLLM…), avec le mode JSON natif de chaque fournisseur.
- Sorties IA validées par JSON Schema (`backend/src/main/resources/llm/schemas`) ; sortie invalide ⇒ relance avec la
  liste des erreurs (`refinr.llm.max-repair-attempts`, 2), puis `502`. Sortie tronquée (`max_tokens`, `MAX_TOKENS`,
  `length`) ⇒ erreur explicite, sans relance.
- Verdict par lots (`refinr.llm.verdict.batch-size`, 10 stories par appel) et cache des verdicts réussis par contenu de
  story (`refinr.llm.verdict.cache.*`, 24 h) : `create-jira` après un aperçu ne rejuge que les stories modifiées.
- Health check par port sortant (Confluence, Jira, chaque fournisseur IA), via des lectures gratuites.
- `refinr.llm.*.extra-body.*` (`openai-compatible`) : propriétés ajoutées au corps de la requête ;
  `LLM_ANALYSIS_REASONING_EFFORT=none` désactive la réflexion des modèles Ollama (équivalent de l'ancien `think: false`).

### Changed
- Appels IA : nouveaux essais sur `429`, `5xx` (dont `529`) et fournisseur injoignable, `Retry-After` respecté
  (plafonné à 30 s) ; jamais après un délai dépassé. Le verdict (`create-jira`, `reassess-story`) est désormais
  retenté comme l'analyse.
- Le modèle ne peut plus fournir de clés de ticket (`jiraKey`, `epicKey`…) dans sa sortie : refusées par le schéma.
- Les corps d'erreur des fournisseurs IA ne sont plus journalisés (certains recopient une partie de la clé refusée).

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

### Fixed
- Frontend : page Results alignée sur la vraie réponse de l'API (plus de `undefined` / `NaN`, éléments non créés
  affichés avec leur raison, lien inexistant vers le board retiré) ; redirection par `<Navigate>` au lieu d'un
  `navigate()` pendant le rendu ; client API typé sur le contrat réel (message d'erreur et `correlationId` affichés).
- Frontend : `npm run lint` fonctionne (dépendances ESLint déclarées, option `--ext` retirée).
- README : description exacte des nouveaux essais. La création Jira n'a jamais été retentée (le README l'annonçait),
  seuls `429` et `500` le sont pour la lecture Confluence et l'analyse IA (pas tous les `5xx`).

### Removed
- Réglage provisoire `spring.jackson.use-jackson2-defaults=true` : la sérialisation REST utilise les défauts Jackson 3
  de Spring Boot 4, contrat JSON inchangé (vérifié par les tests de contrat).
- Frontend : mention « Powered by Gemini AI » et chiffres marketing inventés (90 % de temps gagné, 2 min, 100 %
  de précision), bouton « View Demo » sans action.
- Endpoint de debug `POST /api/v1/mvp/test-issue-types`.
