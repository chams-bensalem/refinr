# Décisions techniques

Une ligne par choix structurant : la décision, puis sa justification.

## Backend

- **Java 25** (LTS) : version imposée par le projet ; `java.version=25`, compilation en `--release 25`.
- **Spring Boot 4.1.2** (Spring Framework 7.0) : dernière version stable ; seule ligne de Boot pleinement supportée sur Java 25 à long terme (3.5.x en fin de support).
- **Starters modulaires de Boot 4** (`spring-boot-starter-webmvc`, `spring-boot-starter-webclient`, `spring-boot-starter-webmvc-test`) : remplacent `starter-web` (déprécié) et `starter-webflux` (seul WebClient est utilisé, pas le serveur réactif).
- **Résilience native de Spring Framework 7** (`org.springframework.resilience.annotation.Retryable` + `@EnableResilientMethods`) au lieu de spring-retry : spring-retry n'est plus géré par Boot 4, est en maintenance et compilé contre Spring 6.2 ; supprime deux dépendances (spring-retry, spring-aspects). Transposition à l'identique (`maxAttempts=3` → `maxRetries=2`).
- **Jackson 3** (`tools.jackson`, géré par Boot 4) : plus aucun `com.fasterxml.jackson.core:jackson-databind` ; seul `jackson-annotations` (partagé par Jackson 3) subsiste. Le mapper local de l'analyseur utilise `JsonMapper.builderWithJackson2Defaults()` pour garder le comportement de parsing de Jackson 2.
- **Sérialisation REST avec les défauts Jackson 3 de Spring Boot 4** : `spring.jackson.use-jackson2-defaults=true`, provisoire pendant la montée de version, est retiré en phase 4 (exemple, configuration de test). Contrat JSON vérifié sans changement : `RestJsonContractTest` (snapshot STRICT de `analyze-preview`, lecture de tous les champs de `create-jira`, propriétés inconnues ignorées) et toute la suite passent sans ajuster ni le mapper ni les tests. Le mapper local de l'adaptateur LLM reste explicitement en `JsonMapper.builderWithJackson2Defaults()` (parsing tolérant des réponses de modèle).
- **logstash-logback-encoder 9.0** : première version basée sur Jackson 3, évite de réintroduire Jackson 2 pour les logs.
- **MockWebServer 5.5.1 (artefact `mockwebserver`, package `okhttp3.mockwebserver`)** : version explicite car Boot 4 ne gère plus OkHttp ; artefact de compatibilité choisi pour ne pas réécrire les tests pendant la montée de version (migration vers `mockwebserver3` possible plus tard).
- **Mockito chargé en `-javaagent`** (surefire + `dependency:properties`) : le chargement dynamique d'agent est déprécié depuis Java 21 ; remplace l'option `-Dnet.bytebuddy.experimental`.
- **Suppression de `DocumentAnalyzer`** : code mort (aucune référence, cf. AUDIT) ; supprimé plutôt que migré vers Jackson 3. Seul effet visible : le log de démarrage « Using Ollama » de ce bean disparaît.
- **ArchUnit 1.4.1 (`archunit-junit5`, scope test)** : tests d'architecture hexagonale exécutés par JUnit 5 (domaine/application sans framework, dépendances vers le centre, adaptateurs indépendants, aucun identifiant `jira` au centre) ; bibliothèque de test seule, sans impact sur l'artefact livré.
- **Cas d'usage câblés par `config/UseCaseConfig` (`@Bean`)** : les services de `application/service` restent du Java pur, sans `@Service`, conformément à la règle « domaine et application sans Spring ».
- **`SearchDocuments` renvoie des `DocumentSummary(id, title, url)`** (type de domaine) : remplace la `Map` brute de Confluence (dette de la phase 3) ; `GET /confluence/search` répond `{success, results:[{id,title,url}]}` et ne demande plus le contenu des pages (`expand=body.storage` retiré). Contrat REST modifié, cf. `CHANGELOG.md`.
- **Retry : la création Jira n'est jamais retentée, réponse partielle à la place** : POST non idempotent (un `500` ou un délai dépassé peut survenir après la création côté Jira → doublon). Un échec est signalé dans la réponse (`created` / `failed`, `207`/`502`) et la création continue pour les autres éléments ; l'utilisateur relance uniquement ce qui a échoué. Lecture Confluence et analyse LLM restent retentées (`@Retryable`, Spring 7 suit les causes), comportement figé par `ApplicationWiringTest`.
- **`IssueTrackerException` dans `application/port/out`** : exception du contrat du port (levée par l'adaptateur, lue par `CreateBacklogService`), au message sûr pour le client ; la règle ArchUnit « ports sortants = interfaces » admet désormais les exceptions de ces ports.
- **`InvalidRequestException` (`application/port/in`) pour les 400** : seul ce type, au message écrit par un cas d'usage, est renvoyé tel quel au client ; une `IllegalArgumentException` (souvent levée par une librairie, avec URL ou détail technique) n'est plus interceptée et devient une erreur `500` au message générique. La validation de la recherche (clé d'espace, titre) est faite par `SearchDocumentsService` ; l'adaptateur Confluence garde l'échappement CQL et une garde défensive.
- **Fournisseurs IA : clients HTTP maison sur WebClient** (`anthropic`, `google`, `openai-compatible`) plutôt que Spring AI 2.0, LangChain4j ou les SDK officiels : deux appels JSON structurés par fournisseur, sans outils ni RAG ; aucune dépendance ajoutée (Spring AI : surface large et autoconfiguration à un modèle par fournisseur ; LangChain4j : starters Boot 4 encore en beta et Jackson 2 par défaut ; SDK officiels `anthropic-java` / `google-genai` : OkHttp, Kotlin et Jackson 2 réintroduits). Coût accepté : suivre les évolutions des trois API.
- **Anthropic sans streaming** : réponse simple, `max_tokens` borné (`refinr.llm.*.max-output-tokens`) et délai de 300 s pour l'analyse ; pas de `temperature` (refusée par les modèles Claude récents).
- **Sorties IA validées par JSON Schema avec networknt `json-schema-validator` 3.0.8** (ligne Jackson 3, `tools.jackson`) : seule dépendance ajoutée (+ `com.ethlo.time:itu`), `jackson-dataformat-yaml` exclu (schémas JSON uniquement). Les schémas (`resources/llm/schemas`) sont aussi envoyés au fournisseur pour son mode JSON natif : une seule source de vérité. Racine objet `{"epics": [...]}` (exigée par les modes JSON natifs).
- **Schéma envoyé à Anthropic sans `minimum`, `maximum`, `multipleOf`, `minLength`, `maxLength`** (refusés en `400` par ses sorties structurées, retirés d'habitude par ses SDK) : la validation locale garde le schéma complet.
- **`maven-enforcer` `bannedDependencies`** : le build échoue si `com.fasterxml.jackson.core:jackson-databind` ou `jackson-core` revient (dépendance directe ou transitive) ; seul `jackson-annotations`, partagé par Jackson 3, est admis.
- **Relances de contenu distinctes des nouveaux essais de transport** : sortie invalide ⇒ la conversation est prolongée par la réponse fautive et la liste des erreurs (2 fois), puis `502` ; sortie tronquée ⇒ erreur explicite sans relance (le même appel serait tronqué de la même façon) ; transport ⇒ `429`/`5xx`/injoignable, `Retry-After` plafonné, jamais après un délai dépassé.
- **Ancienne configuration IA refusée au démarrage** plutôt que traduite : projet pas encore publié, une couche de compatibilité n'aurait servi à personne ; le message liste la correspondance (noms de clés seulement). Les variables d'environnement du système ne sont pas contrôlées (faux positifs possibles avec d'autres outils).
- **Health check par port sortant** (`HealthCheckable`, cas d'usage `CheckHealth`) avec cache de 45 s : le header du frontend l'interroge à chaque affichage ; lectures gratuites uniquement (modèle ou liste des modèles), aucune clé ni URL exposée.
- **Verdict par lots + cache par contenu** : un appel par lot de 10 stories (identifiants `s1…sN` contrôlés, manquants redemandés une fois) ; cache en mémoire (LRU 1000, 24 h) des seuls verdicts réussis, clé = SHA-256 du contenu jugé + fournisseur + URL + modèle + version du prompt. Respecte la règle de la phase 4 (verdict sur le contenu final, rien venant du client) tout en ne rejugeant que les stories modifiées. Cache local à l'instance (suffisant pour la v1). Stories d'un lot encodées en JSON (une story ne peut pas imiter l'en-tête ou l'identifiant d'une autre et fausser son verdict, puis le cache) ; le risque résiduel d'injection par le contenu d'une story porte sur son propre verdict, comme pour un appel unitaire, et le label n'est qu'une aide au tri (`batch-size=1` pour l'éliminer entre stories).
- **Enumérations admises dans `application/port/out`** (règle ArchUnit) : `LlmException.Reason` qualifie l'exception du contrat des ports IA.
- **Un gestionnaire de tickets par instance (`tracker.provider`, défaut `jira`)** : l'adaptateur porte `@ConditionalOnProperty` (un seul `IssueTrackerPort` instancié) ; `TrackerConfiguration` contrôle la valeur au démarrage par un `BeanFactoryPostProcessor` (avant toute création de bean, donc avant les erreurs de placeholder) : valeur inconnue ou vide, ou propriété **absente** du gestionnaire choisi → arrêt avec un message explicite listant les propriétés manquantes. Une propriété présente mais vide (ex. `JIRA_TOKEN=`) reste tolérée, comme avant (le health la signale ; le smoke compose de la CI démarre sans secrets). `openproject` est reconnu mais refusé tant que l'adaptateur n'existe pas (v1.1 phase 2).
- **Format de `projectKey` délégué au gestionnaire actif** : port entrant `DescribeTracker` (nom affichable et `acceptsProjectId`) → `IssueTrackerPort` ; règles dans des classes finales sans bean, `JiraProjectKeys` (`^[A-Z][A-Z0-9]{1,9}$`) et `OpenProjectProjectIds` (`^[a-z0-9_-]{1,100}$`, cf. reconnaissance). Le contrôleur ne dépend d'aucun adaptateur (règle ArchUnit) et garde son contrat (`projectKey`, `400 Invalid projectKey: …`, validation avant toute lecture du document).

## Frontend

- **Mise à jour prudente** : Vite 5 → 8 (et `@vitejs/plugin-react` 6) et mises à jour mineures/correctives (axios, react-router 6.30.x, react-query, postcss…) pour corriger `npm audit` ; **React 18, Tailwind 3 et TypeScript 5.9 conservés** (React Router 6 aussi à l'origine, monté depuis en v7 : voir plus bas) — le frontend n'a aucun test pour sécuriser des montées majeures touchant rendu et routage, et TypeScript 7 (portage natif) est une rupture d'outillage.
- **Node figé** (`.nvmrc` + `engines` dans `package.json`) : Node 26.12.1 (LTS) dans `.nvmrc` à la racine = version de référence pour le développement et la CI ; `engines` assoupli à node `>=22.12.0` / npm `>=10.9.0` (minimum requis par Vite 8, Node 20 étant en fin de vie) pour ne pas bloquer les contributeurs sur une LTS plus ancienne.
- **React Router 6 → 7 (`react-router-dom` 7.18.5, PR Dependabot #13)** : corrige les 2 alertes `npm audit` modérées de la v6 qui étaient acceptées jusque-là (open redirect via backslash dans `<Link>`/`useNavigate`, injection lors de l'hydratation SSR) ; `npm audit` : 0 vulnérabilité. Mode « bibliothèque » (`BrowserRouter`/`Routes`) conservé, aucun changement de code : les API utilisées (`Link`, `Outlet`, `Navigate`, `useNavigate`, `useLocation`) sont inchangées, et les avertissements « Future Flag » de la v6 disparaissent de la console. Faute de tests frontend, vérifié à la main avec une API simulée : Dashboard → Analyze → Preview → Create in Jira → Results, « Start over », retours « Back » après erreur d'aperçu et de création, précédent/suivant du navigateur, redirection de `/results` sans état vers `/analyze`. Les montées majeures suivantes de `react-router-dom` restent manuelles (`dependabot.yml`).
- **ESLint 10 (config « flat » existante) + `typescript-eslint`, `eslint-plugin-react-hooks`, `eslint-plugin-react-refresh`, `globals`, `@eslint/js` en devDependencies** : dépendances que `eslint.config.js` importait déjà sans qu'elles soient déclarées (lint impossible) ; option `--ext` retirée du script (inexistante en config flat).
- **`overrides.minimatch: ^11.0.8`** (`frontend/package.json`) : ESLint 10 et typescript-eslint tirent `minimatch` 10.x, vulnérable (GHSA-g3hv-75wg-m249, DoS par amplification de motifs, corrigé en 11.0.1) ; npm ne proposait qu'un retour à ESLint 9. Outillage de dev uniquement ; lint vérifié fonctionnel avec minimatch 11. **Retrait** : quand `eslint` et `typescript-eslint` dépendront nativement de `minimatch >= 11.0.1` — vérifier en retirant l'override puis `npm install && npm ls minimatch && npm audit` (aucune alerte minimatch) et `npm run lint` vert.

## Packaging, sécurité, CI

- **Toolchains Maven, goal `select-jdk-toolchain` (`maven-toolchains-plugin` 3.2.0, version `[25,26)`)** : compilation et tests (surefire) sur un JDK 25 quel que soit le JDK qui lance Maven ; découverte automatique des JDK installés, sans `toolchains.xml` à écrire (contrairement au goal `toolchain` classique). Vérifié avec Maven sur JDK 27 → surefire en 25.0.2.
- **Image backend : Dockerfile multi-stage, `eclipse-temurin:25-jre-alpine`** plutôt que Buildpacks (`spring-boot:build-image`, image plus lourde, builder lent et peu maîtrisable en multi-arch) ou jlink (liste de modules à maintenir, risque d'erreur à l'exécution sur un module chargé par réflexion) ; ~117 Mo compressés. Jar extrait en couches Spring Boot (`-Djarmode=tools extract --layers --launcher`), utilisateur non-root `10001`, `MaxRAMPercentage=75`.
- **Build Maven et Node sur `$BUILDPLATFORM`** : bytecode et fichiers statiques ne dépendent pas de l'architecture ; seule l'image finale est multi-arch (`linux/amd64`, `linux/arm64`), sans émulation QEMU pendant la compilation.
- **`application-example.properties` devient l'`application.properties` de l'image** : il lit déjà tout depuis l'environnement ; aucun secret dans l'image (`.dockerignore` exclut `.env` et l'`application.properties` local).
- **Frontend : `nginxinc/nginx-unprivileged` (Alpine), configuration par template au démarrage** (`envsubst` limité aux variables `REFINR_*`) : proxy `/api` vers `REFINR_BACKEND_URL` (même origine, pas de CORS en Docker) et `/config.js` généré depuis `REFINR_API_BASE_URL`, lu par le client au chargement : URL de l'API modifiable sans rebuild.
- **`REFINR_API_KEY` ajoutée par le proxy nginx, jamais envoyée au navigateur** : une clé dans un bundle statique serait publique ; contrepartie assumée et documentée : l'interface web n'est pas authentifiée elle-même.
- **CORS et clé d'API par deux filtres servlet, sans Spring Security** : besoin limité (origines explicites, une clé partagée) ; Spring Security ajouterait une dépendance lourde et une configuration par défaut (CSRF, login) à neutraliser. `*` refusé au démarrage ; clé comparée à temps constant ; filtre CORS avant la clé pour que le `401` reste lisible par le navigateur.
- **Liste d'hôtes autorisés (`REFINR_ALLOWED_HOSTS`) des deux côtés contre le DNS rebinding** : le proxy ajoute la clé d'API, donc une page malveillante qui se fait résoudre en `127.0.0.1` l'utiliserait ; elle envoie toutefois son propre nom dans `Host`. nginx : serveur par défaut `return 444` + `server_name` généré depuis la liste ; backend : filtre servlet toujours actif (`403`), qui lit l'en-tête `Host` brut et non `X-Forwarded-Host` (qu'un script peut fixer), noms exacts, port ignoré, `*` refusé. Une seule variable pour les deux, transmise par Compose.
- **Health check et sonde `GET /api/v1/health/live` exemptés de clé** : aucun secret exposé, nécessaires aux sondes de conteneur. La sonde `/live` (ajout au contrat REST) n'appelle aucun service externe : le `HEALTHCHECK` Docker n'interroge pas Confluence, Jira et le LLM toutes les 30 s.
- **Docker Compose durci** : ports publiés sur `127.0.0.1` uniquement, système de fichiers en lecture seule (tmpfs pour `/tmp` et la config nginx générée), `cap_drop: ALL`, `no-new-privileges`. Profil `ollama` optionnel (service + téléchargement du modèle), dépendance `required: false`.
- **CI GitHub Actions** : jobs indépendants (tests backend JDK 25, lint + build frontend avec `.nvmrc`, gitleaks sur tout l'historique, build multi-arch des deux images sans push avec cache GHA, smoke test `docker compose up --wait` + `curl` health). gitleaks installé depuis la release officielle épinglée (même version qu'en local, somme SHA-256 vérifiée) plutôt que `gitleaks-action` (licence requise pour les comptes d'organisation). Actions épinglées par SHA de commit (version en commentaire), y compris `actions/*` : un tag est mutable.
- **Dependabot** (natif GitHub, sans application tierce) plutôt que Renovate : Maven, npm, Docker (Dockerfiles et `docker-compose.yml`, image `ollama/ollama` épinglée en version exacte) et GitHub Actions (SHA), hebdomadaire, mineures et correctifs groupés ; montées majeures de React, React Router, Tailwind et TypeScript ignorées (cf. frontend sans tests).

## Licence, contributions, documentation

- **AGPL-3.0-only + CLA** (décision du mainteneur) : l'AGPL oblige un service réseau dérivé à publier son code ; le CLA (adapté de l'ICLA Apache v2.2, section 3 : droit de licencier sous d'autres termes, engagement de garder chaque version publiée aussi sous une licence open source OSI) préserve une double licence future. Texte de `LICENSE` téléchargé tel quel depuis gnu.org (SHA-256 `0d96a4ff…abcb0`).
- **En-têtes SPDX (`SPDX-FileCopyrightText` + `SPDX-License-Identifier`) contrôlés par un script shell** (`scripts/check-spdx.sh`, hook pre-commit en mode `--staged` sur le contenu indexé + job CI) plutôt que l'outil REUSE (dépendance Python, et exige aussi une licence pour JSON, images et lock files) ou un plugin Maven/ESLint (deux mécanismes pour un seul contrôle). Périmètre : Java, XML, properties, TS/TSX/JS/CSS/HTML, Dockerfiles, configuration nginx, hooks et scripts ; JSON exclu (pas de commentaire possible).
- **`<licenses>` déclaré dans `pom.xml`** : sans lui, le POM hérite de la licence Apache-2.0 de `spring-boot-starter-parent`.
- **CLA via l'application hébergée cla-assistant.io** plutôt que l'action « CLA Assistant Lite » (`contributor-assistant/github-action`), archivée en mars 2026 (plus de correctifs, runtime `node20`) : service maintenu, aucun workflow ni secret dans le dépôt, signatures conservées par le service ; le statut `license/cla` est rendu obligatoire par la protection de `main`.
- **Signalement de vulnérabilités par le « private vulnerability reporting » de GitHub**, sans adresse e-mail publiée ; code de conduite Contributor Covenant 2.1 (texte officiel), contact par message privé au mainteneur.
- **README en anglais (vitrine + référence) et `README.fr.md` (référence détaillée, dont la migration de l'ancienne configuration IA)** ; `ConfigurationDefaultsTest` vérifie le tableau des propriétés des deux fichiers (mêmes défauts que le code).

## OpenProject (v1.1) — reconnaissance de l'API v3

Faits vérifiés le 2026-11-13 par `curl` contre OpenProject **17.9.7** (dernière version stable, image
`openproject/openproject:17.9.7` all-in-one, arm64/amd64), avec un compte non admin `refinr-bot`. Ids et valeurs ci-dessous
sont ceux de l'instance de test (exemples, à résoudre à l'exécution) ; les messages d'erreur suivent la langue du compte.

- **Authentification** : HTTP Basic `apikey:<clé API>` (clé créée par le compte lui-même : *Mon compte → Jetons d'accès → API* ;
  l'admin doit laisser « Activer les jetons d'API » coché). `GET /api/v3/users/me` → `200 {"id":5,"login":"refinr-bot","status":"active"}`.
- **Création en un seul POST par niveau**, hiérarchie comprise : `POST /api/v3/work_packages?notify=false`
  `{"subject":"…","_links":{"project":{"href":"/api/v3/projects/refinr-spike"},"type":{"href":"/api/v3/types/6"},"parent":{"href":"/api/v3/work_packages/38"},"customField1":{"href":"/api/v3/custom_options/2"},"priority":{"href":"/api/v3/priorities/9"}},"storyPoints":5}`
  → `201 {"id":39,"lockVersion":0,…,"_links":{"parent":{"href":"/api/v3/work_packages/38"}}}`. `project` accepte l'identifiant ou l'id numérique.
  `notify=false` évite les notifications. Epic → User story → Task vérifié.
- **Ajouter un enfant modifie le parent** : la création de la story fait passer le `lockVersion` de l'epic de 0 à 1
  (« mis à jour automatiquement en changeant les valeurs dans le lot de travaux enfant ») → ne jamais réutiliser un `lockVersion` mis en cache.
- **Description** : `{"format":"markdown","raw":"## Goal\n\nTrack **stock**…\n- item"}` stockée telle quelle (`raw`) et rendue en HTML
  (titres, gras, listes, code) → aucune conversion côté Refinr.
- **Verdict dans le champ liste « Agent readiness »** : écrit via `_links.customFieldN` → `/api/v3/custom_options/{id}` ; relu via
  `_links.customField1.title` (`"ai-ready"`). `GET /api/v3/custom_options/1` → `{"_type":"CustomOption","id":1,"value":"ai-ready"}`.
- **Résolution par nom (types, champ, options)** : `GET /api/v3/projects/{projet}/types` → types **activés dans le projet** (`[{id:5,name:"Epic"},{id:6,"User story"},{id:1,"Task"},…]`) ;
  `POST /api/v3/projects/{projet}/work_packages/form` `{"_links":{"type":{"href":"/api/v3/types/6"}}}` → `_embedded.schema` donne
  `customField1.name = "Agent readiness"`, `writable: true` et `_embedded.allowedValues` = `[{href:"/api/v3/custom_options/1",value:"ai-ready"},{href:"…/2",value:"validation-necessaire"}]`.
  Le schéma seul (`GET /api/v3/work_packages/schemas/{projet}-{type}`) **n'embarque pas** les `allowedValues` d'un champ liste → passer par `/form`.
  `refinr-bot` voit et écrit le champ (schéma `writable: true`, création et PATCH réussis).
- **Prérequis côté admin pour que le champ apparaisse** (sinon absent du schéma) : champ créé (*Administration → Champs personnalisés → Lots de travaux → Liste*),
  **activé dans le projet** (*Paramètres du projet → Lots de travaux → Champs personnalisés*) **et** ajouté au formulaire de chaque type
  (*Administration → Types → {type} → Formulaire*, glisser le champ dans un groupe). Types Epic et User story à activer dans le projet (seuls Task, Milestone, Summary task le sont par défaut).
- **Story points** : le module Backlogs existe toujours en 17.9.7 et est actif par défaut dans un nouveau projet ; attribut API `storyPoints`
  (schéma : `"Story points"`, `writable: true`) sur Epic, User story et Task ; `"storyPoints":5` accepté et relu. → story points **gardés** pour OpenProject en v1.1
  (optionnels, `openproject.story-points-enabled`), mais **sur les User stories uniquement**, même périmètre que Jira : jamais sur Epic ni Task, bien que l'API les accepte.
- **Priorité** : `_links.priority` → `/api/v3/priorities/{id}` ; valeurs par défaut `Low` / `Normal` / `High` / `Immediate` (`GET /api/v3/priorities`, aussi dans `allowedValues` du schéma) → résolution par nom.
- **Commentaire** : `POST /api/v3/work_packages/{id}/activities?notify=false` `{"comment":{"raw":"Reassessment: **ai-ready** now."}}` → `201 {"_type":"Activity::Comment"}` (markdown).
  Le commentaire **incrémente le `lockVersion`** du work package.
- **`lockVersion` obligatoire sur PATCH** : `PATCH /api/v3/work_packages/{id}` `{"lockVersion":1,"_links":{"customField1":{"href":"/api/v3/custom_options/1"}}}` → `200 {"lockVersion":2}` ;
  version périmée **ou absente** → `409 UpdateConflict`. Séquence de réévaluation : commenter, relire (`GET`) puis PATCH avec le `lockVersion` relu.
- **Forme des erreurs** : `{"_type":"Error","errorIdentifier":"urn:openproject-org:api:v3:errors:<Code>","message":"<localisé>"}` → s'appuyer sur `errorIdentifier`, jamais sur `message`.
  `401 Unauthenticated` (clé invalide ou absente) ; `403 MissingPermission` (création dans un projet où le compte est Reader) ; `404 NotFound` (work package inexistant) ;
  `409 UpdateConflict` ; `422 PropertyConstraintViolation` avec `_embedded.details.attribute` (`subject` vide, `type` non activé dans le projet,
  option `customField1` invalide, projet inconnu → attribut `project`) ; `422 PropertyIsReadOnly` (attribut sans permission d'écriture) ;
  `422 MultipleErrors` avec `_embedded.errors[]` (ex. parent inexistant).
- **Permissions minimales du compte Refinr** (vérifiées une à une avec un rôle dédié, dans le projet) : `view_work_packages` (dépendance imposée par OpenProject),
  `add_work_packages` (sinon `403 MissingPermission` à la création), `manage_subtasks` (sinon `422 PropertyIsReadOnly` sur `parent`),
  `add_work_package_comments` (sinon **`404 NotFound`** sur `/activities`, pas 403), `edit_work_packages` (sinon `422 PropertyIsReadOnly` au PATCH du verdict).
  Le rôle standard « Member » les contient toutes ; compte non admin suffisant.
- **Identifiant de projet** : jusqu'à 100 caractères parmi minuscules, chiffres, `-` et `_` (`a_b-c`, `1abc`, `-abc` acceptés) ;
  refusés : purement numérique (`123` → 422), mot réservé (`new` → 422), majuscules, espaces, plus de 100 caractères.
  L'id numérique est aussi accepté partout (`/api/v3/projects/3`).
  → validation Refinr : `^[a-z0-9_-]{1,100}$` (slug ; s'il est entièrement numérique, il est traité comme un id) ; les mots réservés restent signalés par le 422 d'OpenProject.
- **Lien web d'un work package** : `{base-url}/work_packages/{id}` (redirige vers `/projects/{identifiant}/work_packages/{id}/activity`).

### Adaptateur OpenProject (v1.1 phase 2)

- **`OpenProjectIssueTracker` (clients HTTP maison sur `WebClient`, comme Jira et Confluence)**, actif seulement avec `tracker.provider=openproject` ; propriétés obligatoires `openproject.base-url`, `openproject.api-key`, `openproject.project` (absente → arrêt au démarrage ; vide tolérée, signalée par le health) ; projet par défaut non vide contrôlé au démarrage par `OpenProjectProjectIds`.
- **Résolution des noms en ids au premier usage, mémorisée par projet** : types activés (`GET /projects/{p}/types`), champ du verdict, ses options et les priorités lus dans le formulaire de la User story (`POST /projects/{p}/work_packages/form`) ; type, champ ou option introuvable → message listant les 3 réglages admin. Une résolution en échec n'est pas mémorisée ; un `422 PropertyConstraintViolation` sur `type` ou `customFieldN` invalide le cache du projet (la requête suivante re-résout), **sans relance du POST** (non idempotent).
- **Labels dans la description** : OpenProject n'a pas de labels ; les labels normalisés (sans le verdict, porté par le champ liste) forment une dernière ligne `**Labels:** a, b` de la description de l'epic, de la story et de la tâche.
- **Texte commun extrait dans `domain/service/StoryMarkdown`** (description d'une story, lignes de réévaluation) : utilisé par les deux adaptateurs (qui ne peuvent pas dépendre l'un de l'autre) ; sortie Jira identique au caractère près (test de référence `StoryMarkdownTest`).
- **Priorité** : par nom, insensible à la casse, avec l'alias `Medium` → `Normal` (l'analyse produit High / Medium / Low) ; absente ou inconnue → non envoyée (défaut du projet OpenProject).
- **Réévaluation** : la précision et le nouveau verdict vont dans un **commentaire** (la description d'origine n'est pas réécrite), puis relecture du work package (le commentaire incrémente `lockVersion`) et `PATCH` du champ liste ; sur `409`, une seule nouvelle tentative (relecture + PATCH). Le verdict courant (option du champ) tient lieu de label pour la règle « en attente de validation » : `ReassessStoryService` est inchangé.
- **Délais** : 30 s fixes par appel (5 s pour le health, comme Jira), sans propriété dédiée ; un délai dépassé à la création le signale (« it may have been created »). Lectures (`findStory`) relancées sur 429 et 500 comme Confluence ; POST et PATCH jamais relancés automatiquement (hors la nouvelle tentative sur 409).

### Interface et packaging OpenProject (v1.1 phase 3)

- **`GET /api/v1/tracker` (nom seul) et `url` dans chaque élément de `created`** : l'interface lit le nom du gestionnaire actif pour ses libellés et reçoit l'adresse de chaque ticket calculée par l'adaptateur (`IssueTrackerPort.issueUrl`) ; ajouts rétrocompatibles, sans exposer l'URL de l'instance dans un endpoint dédié ni de clé. `openproject.web-url` (optionnelle, défaut `base-url`) sert uniquement à ces liens, quand Refinr joint OpenProject par une adresse interne (ex. `host.docker.internal` en Docker).
- **Pas d'OpenProject dans le `docker-compose.yml`**, même en profil : image all-in-one lourde (plusieurs Go, ~4 Go de RAM recommandés, première initialisation de plusieurs minutes) et préparation manuelle (types, champ, compte, clé) de toute façon nécessaire ; les variables `OPENPROJECT_*` passent déjà par `env_file: .env`.
- **Health Google inchangé (lecture du modèle) et cas documenté dans le Dépannage**, plutôt qu'un appel de génération : un `generateContent` à chaque health (cache 45 s, interrogé par l'interface) consommerait du quota et basculerait en DOWN sur chaque `503` de forte demande.
- **Modèle Gemini des exemples : `gemini-3.6-flash`** (`gemini-2.5-flash` répond 404 sur `generateContent` tout en restant listé).

## BookStack (v1.2) — reconnaissance de l'API

Faits vérifiés le 2026-11-13 par `curl` contre BookStack **v26.09.3** (image **communautaire** `lscr.io/linuxserver/bookstack:26.09.3`,
amd64/arm64 — le projet BookStack ne publie pas d'image officielle — + `mariadb:11.8.9`), avec un compte non admin `refinr-bot`.
Ids et slugs ci-dessous sont ceux de l'instance de test.

- **Authentification** : en-tête `Authorization: Token <token_id>:<token_secret>` ; jeton rattaché à un utilisateur, créé sur sa fiche
  (*Settings → Users → {compte} → API Tokens → Create Token* : nom + date d'expiration facultative ; un admin peut le créer pour le compte technique),
  secret affiché une seule fois.
  `GET /api/system` → `200 {"version":"v26.09.3","instance_id":"…","app_name":"BookStack","base_url":"http://localhost:8091"}` avec les droits minimaux
  → **sonde de health** (`/api/users/me` n'existe pas : `403`).
- **Toujours envoyer `Accept: application/json`** : sans lui, un refus de permission sur certaines routes (ex. `/export/*`) répond
  `302 Location: {base}` (page d'accueil) au lieu d'un `403` JSON.
- **Identifiant d'une page** : `id` numérique, **stable** (renommage : `slug` `cdc-gestion-des-conges` → `cdc-conges-v2`, `id` 8 inchangé) ;
  séquence partagée entre livres, chapitres et pages (livres 1–2, pages à partir de 3). Le slug n'est pas un identifiant (change au renommage,
  unique seulement dans un livre). Dans l'UI : **sélectionner du texte de la page** → la bulle affiche le permalien `{base}/link/4#bkmrk-…`
  et le tag d'inclusion `{{@4#bkmrk-…}}` (l'id est le nombre) ; il apparaît aussi dans les URL de l'API et les résultats de recherche.
- **Lecture** : `GET /api/pages/4` → `{"id":4,"name":"…","slug":"…","book_id":1,"chapter_id":null,"editor":"markdown"|"wysiwyg","html":"…","raw_html":"…","markdown":"…",…}`.
  `markdown` n'est rempli que pour une page écrite avec l'éditeur Markdown (`""` en WYSIWYG) ; `html` est toujours rempli.
- **Format retenu : `html` de `GET /api/pages/{id}`** (un seul appel, pas de permission d'export, même nature que le HTML de stockage Confluence) :
  - titres, listes, tableaux, liens, code (`<pre><code class="language-yaml">`) et citations préservés ; images en `<img src>` (pas de base64), attributs `id="bkmrk-…"` sans texte ;
  - **les inclusions `{{@3}}` sont développées** dans `html` (le contenu de la page 3 apparaît), pas dans `raw_html` ni dans `markdown` ni dans `/export/markdown` ;
  - pas de titre ajouté : page vide → `<p id="bkmrk-"></p>`, image seule → `<p><img src="…" alt=""></p>`.
- **Exports écartés** (`GET /api/pages/{id}/export/{markdown|html|plaintext}`, `200 application/octet-stream`, **permission `content-export` requise**
  sinon `403 {"error":"You do not have permission to perform the requested action."}` — forme d'erreur différente des autres routes) :
  - `markdown` : **ajoute le nom de la page en `# H1`** (page vide → `# Page vide`) et n'expand pas les inclusions d'une page Markdown.
    Qualité de l'export d'une page **WYSIWYG** : titres, gras/italique, listes, liens, code avec langue, image `![alt](url)` et citation bien convertis ;
    **les tableaux restent en HTML brut** (`<table id="bkmrk-…"><thead>…`) ;
  - `html` : document complet avec `<style>` et images **en base64** (21 Ko pour 2 Ko de contenu) ; `plaintext` : perd listes et tableaux.
- **`DocumentText.hasUsefulText`** sur les contenus réels : `html` → `true` (CDC Markdown, CDC WYSIWYG, page avec inclusion), **`false` (page vide, image seule)** ;
  `/export/markdown` → `true` pour la page vide et l'image seule (titre ajouté, `![](…)`) → faux positifs, autre raison d'écarter l'export.
- **Recherche par titre** :
  - restreinte à un livre : `GET /api/pages?filter[name:like]=%CDC%&filter[book_id]=1` → `{"data":[{"id":4,"name":"CDC - Gestion des stocks","book_id":1,"slug":"…",…}],"total":2}`
    (`like` insensible à la casse ; `%` et `_` saisis par l'utilisateur à échapper) ;
  - globale : `GET /api/search?query={in_name:CDC} {type:page}` → `data[]` avec `type`, `id`, `name`, `book_id`, `url` complète ; **pas de filtre par livre** dans la syntaxe de recherche ;
  - livre par slug : `GET /api/books?filter[slug]=projet-rh` → `{"data":[{"id":2,…}],"total":1}` → le `space` de Refinr peut être l'id ou le slug du livre.
  - Les pages sans droit de lecture sont absentes des deux listes (pas d'erreur).
- **Lien web** : `{base}/link/{id}` redirige vers `{base}/books/{book_slug}/page/{slug}` (connexion requise) et reste valide après renommage ;
  l'ancien slug redirige aussi. `GET /api/pages/{id}` ne donne pas `book_slug` → utiliser le permalien `/link/{id}` (aucun appel en plus).
- **Erreurs** (corps `{"error":{"message":"…","code":N}}`, compte en anglais) :
  `401` sans en-tête (« No authorization token found on the request »), format invalide (« …the format appeared incorrect »),
  token_id inconnu (« No matching API token was found… »), secret faux (« The secret provided for the given used API token is incorrect ») ;
  `403` rôle sans `access-api` (« The owner of the used API token does not have permission to make API calls ») ;
  **`404 Page not found.`** pour une page inexistante, un id non numérique **et une page non visible** (droit `page-view-all` retiré
  ou restriction sur la page) → un manque de droit de lecture est indiscernable d'une page absente.
- **Droits minimaux du compte** (vérifiés en les retirant un à un, rôle dédié « Refinr reader ») : `access-api` (« Access system API ») et
  `page-view-all` (lecture et recherche) ; `book-view-all` seulement pour résoudre un livre par son slug ; `chapter-view-all` et `content-export` inutiles.
  Le compte n'a besoin d'aucun droit d'écriture. Une restriction de page (*Permissions* de la page, rôle sans *View*) la rend invisible au compte.
- **Limite de débit** : en-têtes `X-RateLimit-Limit: 180` et `X-RateLimit-Remaining` sur chaque réponse ; **`429` non observé** (185 appels
  `/api/system` en rafale → 185 × `200`) → relance sur `429` à garder par prudence (documentée par BookStack), non reproduite ici.
- **Création d'utilisateur depuis l'UI** : échoue si l'envoi d'invitation est coché sans SMTP configuré (« Could not create user since invite email failed to send »).

## XWiki (v1.2) — reconnaissance de l'API REST

Faits vérifiés le 2026-11-13 par `curl` contre XWiki **18.4.8 LTS** (image officielle `xwiki:18.4.8-postgres-tomcat`, amd64/arm64,
+ `postgres:17.12`, flavor « XWiki Standard » 18.4.8 installé par l'assistant), avec un compte non admin `refinr_bot`.
Références ci-dessous : celles de l'instance de test. Aucune différence d'API avec la 18.9.0 rencontrée (non testée sur 18.9.0).

- **Authentification** : HTTP Basic, **préemptif** (aucun en-tête `WWW-Authenticate` sur les `401` : un client qui attend le défi n'envoie
  jamais ses identifiants). Seule méthode testée pour un compte technique ; aucun mécanisme de jeton d'API natif n'a été vérifié.
  Chaque réponse porte **`XWiki-User: xwiki:XWiki.refinr_bot`** (utilisateur effectif) et `XWiki-Version: 18.4.8`.
- **Identifiants refusés = invité, pas d'erreur systématique** : `GET /rest/wikis/xwiki` avec un mauvais mot de passe → `200` **sans** `XWiki-User` ;
  sur une page protégée → `401` sans `XWiki-User`. → **Health** : `GET /rest/wikis/xwiki` et vérifier `XWiki-User == xwiki:XWiki.{username}` (absent → identifiants refusés).
- **Nom d'utilisateur** : lettres latines, chiffres et `_` uniquement (« Invalid username provided ») → `refinr_bot`, pas `refinr-bot`.
- **Identifiant d'une page = sa référence** : REST `"id":"xwiki:ProjetStocks.CDC-GestionDesStocks.WebHome"`, `"fullName":"ProjetStocks.CDC-GestionDesStocks.WebHome"`.
  Pages imbriquées : chaque page est un espace, contenu dans `…WebHome`. Les `.` d'un nom sont échappés : page « Spec v1.2 » → `xwiki:ProjetStocks.Spec v1\.2.WebHome`.
  Chemin REST : chaque espace devient un segment `spaces/{nom}` → `/rest/wikis/xwiki/spaces/ProjetStocks/spaces/CDC-GestionDesStocks/pages/WebHome`
  (encodage URL des espaces : `Spec%20v1.2`). Pas d'id numérique. Dans l'UI : onglet **Information** de la page → champ **« Page reference »**
  (bouton copier, bascule locale/globale) ; le HTML de chaque page porte aussi `data-xwiki-reference` et `data-xwiki-rest-url`.
  **Renommage (vérifié en phase 3)** : la référence change ; avec la redirection automatique (défaut de l'UI), l'ancienne référence répond `200` avec une page **vide** portant un objet `XWiki.RedirectClass` (`location` = nouvelle référence) — l'API REST ne suit pas cette redirection.
- **Lecture** : `GET /rest/wikis/xwiki/spaces/…/pages/WebHome` (`Accept: application/json`) → `{"id":"…","title":"CDC - Gestion des stocks","syntax":"xwiki/2.1","content":"= Gestion…","xwikiAbsoluteUrl":"http://localhost:8092/bin/view/ProjetStocks/CDC-GestionDesStocks/","renderedContent":null,"hierarchy":{…},…}`.
- **Rendu HTML dans la même réponse** : `?supportedSyntax=html/5.0` (toute syntaxe différente de celle de la page) → **`renderedContent`** = HTML rendu
  (calculé par `XWikiDocument.displayDocument`, droits du compte appliqués). Paramètre présent dans le WADL (`/rest/application.wadl`).
  `/bin/get/{espaces}/WebHome?outputSyntax=html` donne le même HTML hors REST (`Content-Type` erroné `text/plain`) ; `outputSyntax=markdown/1.2` non supporté (retombe en HTML) ;
  `outputSyntax=plain` perd puces et numéros de listes.
- **Format retenu : `renderedContent` (HTML rendu)** plutôt que la syntaxe XWiki 2.1 brute :
  - **`{{include}}` développé** dans le rendu (le contenu du Glossaire apparaît), **littéral** dans `content` (`{{include reference="ProjetStocks.Glossaire.WebHome"/}}`) ;
  - `{{code language="yaml"}}` rendu en `<div class="code">` coloré (`<span style>`, `<br/>`, `&nbsp;`, pas de `<pre>`), texte du code intact ; dans `content`, bloc lisible tel quel ;
  - titres (`<h1…h3 id="H…">`), listes, tableau (`<th scope="col">`), gras/italique, liens absolus, `{{info}}` (boîte + texte `Information` pour lecteurs d'écran) ;
    images en `<img src="{base}/bin/download/…/schema.png?rev=1.1">` (pas de base64) ;
  - **une macro en échec injecte un message et une pile Java complète** (`<div class="box xwikirenderingerror">` + `<div class="… xwikirenderingerrordescription hidden">`) :
    `{{include}}` d'une page interdite au compte → « Current user [xwiki:XWiki.refinr_bot] doesn't have view rights on document [xwiki:ProjetRH.CDC-Confidentiel.WebHome] »,
    sans fuite du contenu → **l'adaptateur doit retirer ces blocs** avant l'analyse.
- **`DocumentText.hasUsefulText`** : rendu HTML → `true` (CDC), **`false` (page vide : `""`, image seule : `<p><img …/></p>`)** ;
  syntaxe brute → `true` pour l'image seule (`[[image:schema.png]]`) → faux positif, autre raison de retenir le rendu.
- **Recherche par titre** :
  - globale : `GET /rest/wikis/xwiki/search?q=CDC&scope=title` → `{"searchResults":[{"type":"page","id":"xwiki:ProjetStocks.CDC-GestionDesStocks.WebHome","title":"CDC - Gestion des stocks","space":"ProjetStocks.CDC-GestionDesStocks","pageName":"WebHome",…}]}`
    (insensible à la casse, sous-chaîne) ;
  - `/rest/wikis/xwiki/spaces/ProjetStocks/search` **ne descend pas dans les sous-espaces** (0 résultat : les pages imbriquées sont dans `ProjetStocks.CDC-…`) ;
  - restreinte à un sous-arbre : `GET /rest/wikis/xwiki/query?type=solr&q=title:*CDC* AND space_prefix:ProjetStocks` → 1 résultat (même forme) ;
    caractères spéciaux Solr à échapper ; index Solr mis à jour en asynchrone ;
  - `type=xwql`/`hql` refusés au compte (`400 Query type [xwql] is not allowed. Allowed query types are: [solr].`) ;
  - pages sans droit de lecture absentes des résultats.
  → le `space` de Refinr pour XWiki = référence d'espace (`ProjetStocks`, ou `ProjetStocks.Sous-espace`).
- **Lien web** : champ `xwikiAbsoluteUrl` de la page (`http://localhost:8092/bin/view/ProjetStocks/CDC-GestionDesStocks/`) ; dérivable aussi de la référence
  (`{base}/bin/view/{espaces séparés par /}/` pour une page `WebHome`), sans appel.
- **Erreurs** : corps **HTML Tomcat** (`<!doctype html>…HTTP Status 401 – Unauthorized`) même avec `Accept: application/json` → ne s'appuyer que sur le code et `XWiki-User` :
  `401` identifiants refusés, invité **et page interdite au compte authentifié** (avec `XWiki-User: xwiki:XWiki.refinr_bot`) — **pas de `403`** ;
  `404` page absente dans un espace lisible ; page absente dans un espace interdit → `401` ; écriture par le compte → `401`.
- **Droits minimaux** : droit `view` sur les espaces à analyser, et rien d'autre (objet `XWiki.XWikiGlobalRights` sur `{Espace}.WebPreferences`,
  posé ici par REST), hérité par les sous-espaces ; un refus sur une page (`XWiki.XWikiRights`, `allow=0`) la rend invisible.
  **Piège** : tout nouvel utilisateur est ajouté automatiquement à `XWiki.XWikiAllGroup`, qui a `edit` (donc lecture) sur tout le wiki par défaut :
  le compte voyait `Main.WebHome` jusqu'à son retrait du groupe (puis `401`). Wiki rendu privé : `view` accordé explicitement à `XWikiAdminGroup` au niveau wiki
  → invités et autres comptes implicitement refusés.
- **CSRF** : les écritures REST authentifiées (formulaires) exigent l'en-tête `XWiki-Form-Token` (fourni dans les réponses) ; **inutile pour les lectures** de Refinr.
- **Multi-wiki** : wiki principal `xwiki` (préfixe des références et segment `/wikis/xwiki/`) ; sous-wikis non testés → `xwiki.wiki` optionnelle, défaut `xwiki`.

### Cœur neutre vis-à-vis de la source (v1.2 phase 1)

- **`source.provider` contrôlé au démarrage par `SourceConfiguration`**, même mécanique que `tracker.provider` (valeur inconnue, propriétés manquantes listées, vide toléré) ; `ConfluenceDocumentSource` porte `@ConditionalOnProperty(source.provider=confluence, matchIfMissing)` → aucune propriété `confluence.*` lue pour une autre source.
- **Formats délégués à l'adaptateur** (`DocumentSourcePort.acceptsSpace` / `acceptsDocumentId`, comme `acceptsProjectId` côté tracker) : identifiant et espace n'ont pas la même forme d'un outil à l'autre (id numérique, slug, référence XWiki) ; la regex Confluence quitte le cœur (`ConfluenceIds`).
- **`GET /api/v1/source` → `{"name": …}`**, endpoint séparé symétrique de `GET /api/v1/tracker` plutôt qu'un champ ajouté à `/tracker` : chaque endpoint décrit un seul port, sans URL ni clé.
- **Alias dépréciés conservés à l'identique** (`confluencePageId`, `GET /confluence/search` avec `spaceKey` obligatoire et le même message `Invalid spaceKey`) ; dépréciation signalée par les en-têtes `Deprecation` (RFC 9745) et `Link rel="successor-version"` (RFC 8288), sans changer le corps.
- **Recherche Confluence sans espace** : même clause de titre échappée que la recherche par espace, et `limit=25` explicite dans les deux cas (valeur par défaut de l'API Confluence, désormais testée).

### Adaptateur BookStack (v1.2 phase 2)

- **Erreurs de source typées (`DocumentSourceException` du port sortant, traduite par les cas d'usage en `404` / `502`)**, Confluence compris : message sûr (outil, id, statut HTTP, droit en cause), sans URL ni corps brut ; `404` seulement pour une page absente ou invisible (indiscernables dans BookStack), tout le reste (`401`, `403`, `429`, `5xx`, `3xx`, délai, injoignable) en `502`.
- **Relances `@Retryable` à travers l'exception enveloppée** : Spring Framework 7 compare `includes` à toute la chaîne des causes (`ExceptionTypeFilter.match(…, true)`) → un `429` / `5xx` enveloppé est relancé, un `404` non (testé). Ces relances supposent des **proxys de classe** (configuration AOP de Spring Boot) : un proxy JDK par interface ne voit pas l'annotation de la méthode de l'adaptateur → test de relance dans un contexte avec `AopAutoConfiguration`, comme l'application.
- **Recherche par `GET /api/pages?filter[name:like]=…`** (et non `/api/search`, sans filtre par livre) : valeurs passées en variables d'URI, encodées une seule fois (`%25…%25`, testé sur la requête brute) ; jokers `%` `_` et `\` échappés par `\` (vérifié contre BookStack : `%\_%` → 0, `%_%` → tout) ; `count=25` comme Confluence.
- **Livre par id ou slug** ; slug résolu par `GET /api/books?filter[slug]=` ; slug inconnu ou livre invisible (sans `book-view-all`) → résultat vide et `warn` serveur, pas d'erreur (l'id numérique est recommandé).
- **Toute réponse 3xx est une erreur** (`onStatus(is3xxRedirection)`) en plus de `Accept: application/json` : un refus BookStack sans cet en-tête redirige vers l'accueil, qui serait sinon lu comme une réponse vide.
- **Health = `GET /api/system`** (jeton + droit *Access system API*, aucun droit de contenu) ; jeton vide → `DOWN` sans appel.

### Adaptateur XWiki (v1.2 phase 3)

- **Identifiant = référence de document** de l'onglet *Information* (`ProjetStocks.CDC.WebHome`, `xwiki:` facultatif, échappements `\.` `\:` `\\`) ; **sans `.WebHome`** (forme recopiée de l'URL `/bin/view/Espace/Page/`) : page imbriquée `Espace.Page.WebHome` d'abord, puis page terminale `Espace.Page` sur un « introuvable » ; le `404` final cite les deux références. Références avec `/` refusées (segment `%2F` rejeté par Tomcat).
- **Renommage suivi** : contenu vide → lecture de `…/objects/XWiki.RedirectClass/0/properties/location` (`404` = vraie page vide) → lecture de la cible, 3 redirections au plus, jamais vers un autre wiki ; sans cela, une page renommée donnerait le `400` « document vide ».
- **Blocs d'erreur de macro retirés** (`div`/`span` de classe `xwikirenderingerror` / `xwikirenderingerrordescription`, balises imbriquées comptées) avant l'analyse : ni pile Java ni nom de page interdite envoyés au modèle ; nombre retiré journalisé en `warn`, sans le contenu.
- **Erreurs lues sur le code et `XWiki-User`, jamais sur le corps** (HTML Tomcat) : `404` ou `401` *avec* `XWiki-User` → document introuvable ou invisible (`404` Refinr) ; `401` *sans* `XWiki-User` → identifiants refusés (`502`).
- **Recherche** : sans espace, `GET /rest/wikis/{wiki}/search?scope=title` (base, sous-chaîne, insensible à la casse et immédiate) ; avec espace, Solr (`/query?type=solr`), seule recherche qui descend dans les sous-espaces : `title` y est découpé en mots → **une clause `title:*mot*` par mot** (lettres et chiffres seulement, en minuscules : aucun caractère de la syntaxe Solr n'atteint la requête), puis `space_prefix:"…"` avec la référence d'espace échappée (`ProjetStocks.Spec v1\\.2`). Différences assumées : Solr est sensible aux accents (`conges` ≠ « congés ») et indexé en asynchrone. `limit` 25 dans les deux cas.
- **Health = `GET /rest/wikis/{wiki}` + `XWiki-User` se terminant par `:XWiki.{username}`** : des identifiants refusés répondent `200` en invité sur cette ressource.
- **`supportedSyntax=html/5.0` envoyé encodé (`html%2F5.0`)**, valeur passée en variable d'URI : accepté par XWiki (vérifié de bout en bout).
- **Renommé vers une cible absente** : `404` qui le dit (« the page was renamed to …, which is not found… »), sans essai de la page terminale.
- **Vérifié contre l'instance** : la recherche en base n'interprète pas `%` ni `_` saisis (`q=%` → 0 résultat, XWiki les échappe) ; la connexion Basic est sensible à la casse du nom d'utilisateur (`REFINR_BOT` refusé) → la comparaison exacte de `XWiki-User` dans le health ne peut pas donner de faux `DOWN`.
- **Repli sur la page terminale aussi sur un `401` avec `XWiki-User`** (et pas seulement sur un `404`) : XWiki répond `401` pour une page absente d'un espace que le compte ne peut pas lire, et ce `401` est déjà traduit en « introuvable ou invisible » (`404`).
- **Inclusions vérifiées** : repère unique ajouté à la seule page incluse (Glossaire) → absent de la syntaxe brute de la page principale (`{{include reference="ProjetStocks.Glossaire.WebHome"/}}`), présent dans son `renderedContent` lu par le compte technique.

## Entrée « texte » (v1.3)

### Backend (v1.3 phase B1)

- **`content` (+ `title` optionnel) sur `analyze-preview` et `analyze-and-create`**, exclusif de `documentId` / `confluencePageId` : mode texte dès que `content` est présent (même vide → `400` « document vide ») ; combiné à un identifiant non vide → `400` ; `title` sans `content` → `400` ; aucun des deux → `400` existant « documentId is required ». Contrat par identifiant inchangé.
- **Même chemin que les sources** : le texte contourne `DocumentSourcePort` (indépendant de `source.provider`), puis `requireUsefulText` → génération → verdict distinct éventuel → création, par une méthode commune aux deux entrées dans chaque service.
- **Titre = `# {titre}` sur une ligne en tête du texte envoyé au modèle** (espaces et retours à la ligne ramenés à une espace) ; un titre seul ne suffit pas : le texte utile est exigé dans `content`.
- **Pas de `documentUrl`** en mode texte (le texte n'a pas d'adresse dans la source). **Logs** : taille en caractères et présence d'un titre, jamais le contenu ni le titre.
- **`refinr.document.max-content-chars` = 200 000 par défaut** (`DOCUMENT_MAX_CONTENT_CHARS`), caractères Unicode (points de code), titre compris, vérifiée avant tout appel au modèle → **`413`** avec la limite dans le message. Pourquoi 200 000 : (1) nginx — même en JSON tout échappé (`\uXXXX`, 6 octets par caractère, 12 pour un caractère hors BMP compté une fois), 200 000 caractères tiennent sous les 5 Mio de `client_max_body_size 5m` (≤ 2,4 Mo) : un texte trop long reçoit toujours le `413` explicite de Refinr, jamais le `413` muet de nginx ; (2) modèle — ~50 à 70 k jetons de texte FR/EN, dans les fenêtres de 128 k et plus des fournisseurs pris en charge avec la marge du prompt et des 32 768 jetons de sortie ; (3) un cahier des charges réel fait rarement plus de quelques dizaines de milliers de caractères. Valeur ≤ 0 → échec au démarrage. La limite ne s'applique qu'au mode texte (aucun changement pour les sources).
- **Comptage en points de code** (`codePointCount`) : un emoji compte pour un caractère, comme pour un utilisateur ; pas de normalisation Unicode.
- **`hasUsefulText` inchangé** : vérifié sur du Markdown riche (titres, listes, tableau, bloc de code avec `<` / `>`) ; un Markdown sans lettre ni chiffre (`#`, `-`, `|---|`, clôtures de code vides) reste refusé.
- **Plafond du corps HTTP côté backend : `refinr.http.max-body-bytes` = 5 242 880 octets (5 Mio, `HTTP_MAX_BODY_BYTES`)**, aligné sur `client_max_body_size 5m` de nginx, pour un backend joint sans le proxy : filtre servlet (après hôte et CORS, avant la clé d'API : refus sans lire le corps) → `413` JSON si `Content-Length` dépasse ; corps sans `Content-Length` (`chunked`) lu au plus jusqu'à la limite + 1 octet, `413` au-delà, sinon rejoué depuis la mémoire (au plus 5 Mio par requête). Appliqué à tous les chemins (sur-ensemble de `/api/**`, aucun contournement par encodage du chemin). Valeur ≤ 0 → échec au démarrage. Corps jamais journalisé.
