# Changelog

Toutes les évolutions notables de Refinr sont consignées ici.
Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) · versionnage : [SemVer](https://semver.org/lang/fr/).

## [Unreleased]

### Added
- `source.provider` (`SOURCE_PROVIDER`, défaut `confluence`) : une source documentaire par instance, `confluence`,
  `bookstack` ou `xwiki`. Valeur inconnue ou propriété absente de la source choisie : arrêt au démarrage avec un message
  listant les propriétés manquantes. Les propriétés `confluence.*` ne sont plus requises si la source n'est pas
  Confluence.
- Source **BookStack** (API REST à jetons, vérifiée avec BookStack v26.09) : contenu lu dans le champ `html` de la page
  (inclusions développées), recherche par titre dans tout BookStack ou dans un livre (id ou slug), liens par permalien
  `/link/{id}`, health par `/api/system`. Configuration `bookstack.*` / `BOOKSTACK_*` (`bookstack.web-url` optionnelle
  pour les liens) ; compte dédié avec les seules permissions *Access system API* et *View* sur les pages.
- Source **XWiki** (API REST, vérifiée avec XWiki 18.4 LTS) : HTML rendu par XWiki (macros `{{include}}`, `{{code}}`…
  exécutées avec les droits du compte, blocs d'erreur de macro et piles Java retirés), identifiant = référence de la
  page (onglet *Information* → *Page reference*, ou `Espace.Page` recopié de l'URL : page imbriquée puis page
  terminale), page renommée suivie jusqu'à sa nouvelle référence, recherche par titre dans tout le wiki ou dans un
  espace et ses sous-espaces, health vérifiant que XWiki reconnaît bien le compte. Configuration `xwiki.*` /
  `XWIKI_*` (`xwiki.web-url`, `xwiki.wiki` optionnelles) ; compte technique en HTTP Basic (à n'utiliser qu'en HTTPS)
  avec le seul droit *View* sur les espaces à analyser.
- API : champ neutre `documentId` pour `analyze-preview` et `analyze-and-create` (`confluencePageId` toujours accepté
  comme alias ; les deux avec des valeurs différentes → `400`).
- API : `GET /api/v1/documents/search?title=…&space=…` (`space` optionnel : sans lui, tous les espaces lisibles par le
  compte, même limite de 25 résultats) et `GET /api/v1/source` → `{ "name": "Confluence" }`.

### Changed
- Identifiant de document absent ou vide : `400 documentId is required` au lieu d'un `500` ; identifiant au mauvais
  format pour la source active (Confluence : numérique) : `400 Invalid documentId`, sans appel à la source (#20).
- Erreurs de la source documentaire (lecture et recherche), Confluence compris : `404` si le document est absent ou non
  visible pour le compte, `502` si la source refuse les identifiants ou les droits, est en erreur ou injoignable — au
  lieu d'un `500` générique. Message sûr et utile (outil, identifiant du document, statut HTTP), sans URL ni corps brut.

### Deprecated
- `GET /api/v1/confluence/search` (`spaceKey` obligatoire) : alias de `GET /api/v1/documents/search`, inchangé, avec
  les en-têtes `Deprecation` et `Link: rel="successor-version"`. `confluencePageId` : préférer `documentId`.

### Migration
- Recopier dans `application.properties` la ligne `source.provider=${SOURCE_PROVIDER:confluence}` (facultatif :
  `confluence` est la valeur par défaut) et, pour BookStack ou XWiki, le bloc `bookstack.*` ou `xwiki.*` de
  `application-example.properties`.

## [1.1.0] - 2026-11-13

**OpenProject** comme gestionnaire de tickets, au choix de Jira (un seul par instance). Contrat REST existant inchangé ;
deux ajouts rétrocompatibles.

### Added
- `tracker.provider` (`TRACKER_PROVIDER`, défaut `jira`) : `jira` ou `openproject`. Valeur inconnue ou propriété absente
  du gestionnaire choisi : arrêt au démarrage avec un message listant les propriétés manquantes.
- Adaptateur **OpenProject** (API v3, vérifié avec OpenProject 17.9) : Epic → User story → Task via le parent des lots
  de travaux, descriptions Markdown, verdict dans le champ liste *Agent readiness*, story points sur les User stories
  uniquement, priorité par nom, labels en dernière ligne de la description, réévaluation en commentaire.
  Configuration `openproject.*` / `OPENPROJECT_*` ; préparation côté OpenProject (types, champ, 5 permissions, clé API)
  décrite dans le README.
- API : `GET /api/v1/tracker` → `{ "name": "Jira" | "OpenProject" }` ; chaque élément de `created` des réponses de
  création porte désormais `url`, l'adresse web du ticket (`{site}/browse/KEY` pour Jira, `{instance}/work_packages/{id}`
  pour OpenProject ; `openproject.web-url` permet une adresse de liens différente de `openproject.base-url`).
- `projectKey` : clé Jira ou identifiant / id OpenProject selon le gestionnaire actif (même champ, même `400`).

### Changed
- Interface web : libellés selon le gestionnaire actif (« Create in OpenProject »…) et liens vers les tickets créés
  sur la page Results.
- Health : la section du gestionnaire de tickets est celle du gestionnaire actif (`jira` ou `openproject`).
- Modèle Gemini des exemples : `gemini-3.6-flash` (`gemini-2.5-flash` est retiré pour `generateContent` : 404, bien que
  toujours listé). Section Dépannage : un health `ai: UP` ne garantit pas que le modèle génère encore.
- Versions du backend et du frontend : `1.1.0`.

### Migration
- **`application.properties` local** (copie de `application-example.properties`) : recopier les nouvelles lignes
  `tracker.provider` et `openproject.*`. Sans elles, `tracker.provider=openproject` s'arrête au démarrage sur
  *Missing configuration for tracker.provider=openproject: …* ; avec Jira, rien ne change.
- **`.env`** : pour OpenProject, ajouter `TRACKER_PROVIDER=openproject` et les variables `OPENPROJECT_*` (cf. `.env.example`).
- **Clés Google AI Studio** : les nouvelles clés sont au format `AQ.…` ; elles s'envoient toujours dans l'en-tête
  `x-goog-api-key`, rien à changer côté Refinr. Si `LLM_ANALYSIS_MODEL=gemini-2.5-flash`, passer à un modèle actuel.

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

[Unreleased]: https://github.com/chams-bensalem/refinr/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/chams-bensalem/refinr/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/chams-bensalem/refinr/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/chams-bensalem/refinr/releases/tag/v1.0.0
