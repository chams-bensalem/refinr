# Changelog

Toutes les évolutions notables de Refinr sont consignées ici.
Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) · versionnage : [SemVer](https://semver.org/lang/fr/).

## [Unreleased]

## [1.0.1] - 2026-11-13

Interface web et documentation ; API REST inchangée.

### Changed
- Page **Analyze** : plus de création en un clic. **Preview** (`POST /api/v1/analyze-preview`) affiche
  Epics > Stories > Sous-tâches avec, pour chaque story, le badge `ai-ready` / `validation-necessaire` et sa raison ;
  **Create in Jira** (`POST /api/v1/create-jira` avec le backlog affiché, verdict recalculé par le serveur) mène à la
  page Results. Un `502` où rien n'a pu être créé affiche aussi les raisons sur Results.
- Erreurs de **Preview** et de **Create in Jira** : message renvoyé par l'API, référence (`correlationId`) et bouton
  de retour (« Back to the form » / « Back to the preview ») ; l'échec de création s'affiche à côté des boutons et
  rappelle que des tickets ont pu être créés.
- Aucune relance automatique côté navigateur (`retry: false` pour les requêtes et les mutations TanStack Query) et
  verrou contre le double-clic / double Entrée sur **Preview** et **Create in Jira** : un `POST /create-jira` rejoué
  pourrait créer des tickets en double.
- README : GIF de démonstration (`docs/demo.gif`) à la place de l'emplacement réservé.
- Versions du backend et du frontend : `1.0.1`.
- Libellés : « CDC » remplacé par « spec » / « requirements document », « Doc to Jira » par « Analyze a spec » dans
  l'en-tête, titre d'accueil « Turn a Confluence spec into an agent-ready Jira backlog ».

## [1.0.0] - 2026-11-13

**Initial public release.** Les choix techniques sont consignés dans [DECISIONS.md](DECISIONS.md).

### Added

**Analyse IA d'une spec Confluence**
- Lecture d'une page Confluence Cloud par son ID et recherche de pages par espace + titre
  (`GET /api/v1/confluence/search`, clé d'espace validée, titre échappé dans le CQL).
- Découpage Scrum Epic → Story → Sous-tâche par un LLM : stories *As a / I want / So that*, 2 à 5 critères
  *Given / When / Then*, story points (Fibonacci), heures estimées, priorités et labels techniques normalisés.
- Fournisseurs IA au choix, configurés séparément pour l'analyse (`refinr.llm.analysis.*`) et le verdict
  (`refinr.llm.verdict.*`) : `anthropic`, `google` (Gemini) et `openai-compatible` (OpenAI, DeepSeek, Mistral, Groq,
  OpenRouter, Ollama, LiteLLM, vLLM…). Défaut : Ollama local (`qwen3:8b`). Testés en réel : Ollama et Gemini.
- Sorties validées par JSON Schema (mode JSON natif du fournisseur), jusqu'à 2 relances avec la liste des erreurs,
  puis `502` ; sortie tronquée ⇒ erreur explicite ; document vide ⇒ `400` sans appel à l'IA.
- Nouveaux essais des appels IA sur `429`, `5xx` et fournisseur injoignable (backoff exponentiel avec jitter,
  `Retry-After` prioritaire et plafonné), jamais après un délai dépassé ; lecture Confluence retentée sur `429` / `500`.

**Verdict « prêt pour un agent de code »**
- Label `ai-ready` ou `validation-necessaire` (avec la raison) sur chaque story ; un doute vaut
  `validation-necessaire`. Labels réservés : retirés s'ils viennent de la sortie de l'IA.
- Verdict recalculé par le serveur sur le contenu final à la création (`aiReady` / `aiReadyReason` du client ignorés),
  par lots de 10 stories, avec un cache par contenu (seules les stories modifiées sont rejugées).
- Réévaluation d'une story `validation-necessaire` avec une précision de l'utilisateur (`POST /api/v1/reassess-story`),
  description Jira d'origine conservée et complétée.

**Création Jira**
- `POST /api/v1/analyze-preview` (aperçu sans création), `POST /api/v1/create-jira` (structure éventuellement modifiée)
  et `POST /api/v1/analyze-and-create` (en une étape) ; projet cible optionnel `projectKey`.
- Descriptions converties de Markdown en ADF (titres, gras, italique, listes, code) ; story points dans le champ
  `jira.story-points-field` ; projets team-managed (`jira.priority-enabled=false`).
- Réponse partielle `created` / `failed` (`200`, `207`, `502`) : la création n'est jamais retentée (POST non idempotent)
  et continue après un échec.

**Interface web**
- Pages Dashboard, Analyze (suivi des étapes) et Results (compteurs, tickets créés, éléments non créés et leur raison),
  état des services dans l'en-tête ; URL de l'API lue à l'exécution (`/config.js`).

**Exploitation et sécurité**
- `GET /api/v1/health` (état de Confluence, Jira et de chaque fournisseur IA, en cache 45 s, sans clé ni URL) et
  sonde `GET /api/v1/health/live`.
- Erreurs sans message brut d'exception (message générique + `correlationId`), logs JSON structurés.
- Clé d'API optionnelle (`X-API-Key`), CORS limité à une liste d'origines, liste d'hôtes autorisés contre le DNS
  rebinding (backend `403`, nginx `444`) ; clé Gemini transmise en en-tête, jamais dans l'URL.
- Images Docker multi-arch (`amd64` / `arm64`) non-root, `docker-compose.yml` (profil optionnel `ollama`, configuration
  par `.env`, ports sur `127.0.0.1`, conteneurs en lecture seule).
- CI GitHub Actions (tests sur JDK 25, lint + build, en-têtes SPDX, gitleaks, images, smoke test Compose) et Dependabot.

**Projet**
- Licence AGPL-3.0-only (en-tête SPDX dans chaque fichier source), CLA, README en anglais et en français,
  `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`.

[Unreleased]: https://github.com/chams-bensalem/refinr/compare/v1.0.0...HEAD
[1.0.1]: https://github.com/chams-bensalem/refinr/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/chams-bensalem/refinr/releases/tag/v1.0.0
