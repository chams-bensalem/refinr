# Changelog

Toutes les évolutions notables de Refinr sont consignées ici.
Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) · versionnage : [SemVer](https://semver.org/lang/fr/).

## [Unreleased]

### Changed — contrat REST
- `GET /api/v1/mvp/confluence/search` renvoie `{"success": true, "results": [{"id", "title", "url"}]}`
  au lieu de la réponse brute de Confluence (plus de `body.storage`, `_links`, `start`, `limit`…).

### Changed
- `ai.provider` vaut `ollama` par défaut partout (code, configuration d'exemple, README) ; le code utilisait `gemini`.
- Modèle Gemini configurable (`gemini.model`, défaut `gemini-2.5-flash`), utilisé par l'analyse et le health check ;
  `ai.verdict.model` vaut aussi `gemini-2.5-flash` par défaut (au lieu de `gemini-3.6-flash`).

### Removed
- Endpoint de debug `POST /api/v1/mvp/test-issue-types`.
