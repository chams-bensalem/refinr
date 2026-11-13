<div align="center">

# 📄 ➜ 🎯 Refinr

**Refinr transforme une spec Confluence en backlog Jira (Epic → Story → Sous-tâche) et indique, story par story, si un agent de code peut l'implémenter sans décision humaine.**

[English](README.md) · **Français**

Epics · User Stories · Sous-tâches · Critères d'acceptation · Story points · Verdict « prêt pour un agent de code »

![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)
![Ollama](https://img.shields.io/badge/Ollama-local-000000?logo=ollama&logoColor=white)
![Gemini](https://img.shields.io/badge/Gemini-2.5%20Flash-4285F4?logo=googlegemini&logoColor=white)
![Jira](https://img.shields.io/badge/Jira-Cloud-0052CC?logo=jira&logoColor=white)
![Confluence](https://img.shields.io/badge/Confluence-Cloud-172B4D?logo=confluence&logoColor=white)
[![Licence : AGPL-3.0-only](https://img.shields.io/badge/licence-AGPL--3.0--only-blue)](LICENSE)

</div>

<p align="center">
  <img src="docs/demo.gif" alt="Démonstration de Refinr : un ID de page Confluence est analysé, l'aperçu du backlog indique pour chaque story ai-ready ou validation-necessaire avec sa raison, puis le backlog est créé dans Jira" width="900">
</p>

---

## 🤔 Pourquoi

Les agents de code savent implémenter une story… quand elle est claire. Le vrai goulot d'étranglement est en amont :
savoir **quelles** stories peuvent leur être confiées sans aller-retour avec un humain.

Refinr ne se contente pas de découper une spec en tickets : il juge chaque story et pose un label **`ai-ready`**
(périmètre clair, critères testables, aucune dépendance externe non précisée) ou **`validation-necessaire`**
(il manque une décision, avec la raison). Le verdict est **recalculé sur le contenu final** au moment de la création,
et un doute vaut toujours `validation-necessaire`. Résultat : une file prête pour vos agents, en une requête JQL
`labels = "ai-ready"`, et une liste précise de questions pour le Product Owner sur le reste.

## ⚡ Démarrage rapide (Docker)

```bash
git clone https://github.com/chams-bensalem/refinr.git && cd refinr
cp .env.example .env    # renseignez Confluence, Jira et le fournisseur IA
docker compose up -d --build   # UI http://localhost:3000 · API http://localhost:8080
```

Ollama tourne sur votre machine ? Dans `.env`, mettez `LLM_ANALYSIS_BASE_URL=http://host.docker.internal:11434/v1`
(dans un conteneur, `localhost` désigne le conteneur lui-même). Puis `curl localhost:8080/api/v1/health`.
Ollama inclus en option : `docker compose --profile ollama up -d --build`
(avec `LLM_ANALYSIS_BASE_URL=http://ollama:11434/v1`). Détails : [Avec Docker Compose](#avec-docker-compose).

## 🔌 Fournisseurs IA

| Fournisseur | `provider` | Statut |
|---|---|---|
| Ollama (local, défaut : `qwen3:8b`) | `openai-compatible` | ✅ testé en réel |
| Google Gemini (`gemini-2.5-flash`) | `google` | ✅ testé en réel |
| Anthropic Claude | `anthropic` | ⚠️ non testé en réel (serveur HTTP simulé uniquement) |
| OpenAI | `openai-compatible` | ⚠️ non testé en réel (serveur HTTP simulé uniquement) |
| DeepSeek, Mistral, Groq, OpenRouter, LiteLLM, vLLM | `openai-compatible` | ⚠️ non testé en réel (même client que OpenAI / Ollama) |

« Testé en réel » = analyse et verdict exécutés contre la véritable API. Chaque client est par ailleurs couvert par
des tests sur serveur HTTP simulé (succès, `401`, `429` + nouvel essai, JSON invalide + relance, délai dépassé).
Un retour sur un fournisseur non testé est bienvenu : voir [CONTRIBUTING.md](CONTRIBUTING.md).

---

## 📚 Sommaire

- [Pourquoi](#-pourquoi)
- [Démarrage rapide (Docker)](#-démarrage-rapide-docker)
- [Fournisseurs IA](#-fournisseurs-ia)
- [Présentation](#-présentation)
- [Fonctionnalités](#-fonctionnalités)
- [Mode de fonctionnement](#️-mode-de-fonctionnement)
- [Architecture du projet](#-architecture-du-projet)
- [Prérequis](#-prérequis)
- [Installation](#️-installation)
- [Configuration & connexion aux services](#-configuration--connexion-aux-services)
- [Lancer l'application](#️-lancer-lapplication)
- [Utilisation](#-utilisation)
- [Référence de l'API](#-référence-de-lapi)
- [Modèle de données](#️-modèle-de-données)
- [Tests](#-tests)
- [Dépannage](#-dépannage)
- [Limites connues & feuille de route](#️-limites-connues--feuille-de-route)
- [Contribuer](#-contribuer)
- [Licence](#-licence)

---

## ✨ Présentation

**Refinr** lit une page Confluence (cahier des charges, spécification, besoin rapide…), la fait analyser par un LLM jouant le rôle de **Scrum Master / Product Owner**, puis crée automatiquement toute la hiérarchie correspondante dans un projet Jira :

```
Epic  ──►  User Story  ──►  Sous-tâche
```

Chaque story reçoit en plus un **verdict de maturité** : `ai-ready` (un agent de code peut l'implémenter sans décision humaine) ou `validation-necessaire` (il manque une information). Ce verdict est conçu pour alimenter un agent de code via JQL `labels = "ai-ready"`.

Le projet est un monorepo :

| Dossier | Rôle | Stack |
|---|---|---|
| [`backend/`](backend) | API backend | Java 25 · Spring Boot 4.1 · WebClient · Spring Resilience (`@Retryable`) |
| [`frontend/`](frontend) | Interface web | React 18 · TypeScript · Vite · Tailwind · TanStack Query |

---

## 🚀 Fonctionnalités

### Analyse IA du cahier des charges
- 🔌 **Fournisseurs au choix**, configurés séparément pour l'analyse et le verdict : `anthropic` (Claude — ⚠️ *non testé en conditions réelles* : seuls des tests sur serveur HTTP simulé existent, aucun appel à l'API Anthropic n'a encore été fait), `google` (Gemini) ou `openai-compatible` (OpenAI — ⚠️ *non testé en conditions réelles*, comme `anthropic` —, DeepSeek, Mistral, Groq, OpenRouter, Ollama, LiteLLM, vLLM…).
- 📐 **Sortie validée par JSON Schema** (mode JSON natif du fournisseur quand il existe) ; sortie invalide ⇒ jusqu'à 2 relances avec la liste des erreurs, puis erreur `502` propre. Sortie tronquée par la limite de tokens ⇒ erreur explicite, sans relance. Fournisseur indisponible (après les nouveaux essais) ⇒ `502`.
- 🚫 **Document vide refusé** : une page sans texte utile (vide, balises vides, images seules) donne un `400` explicite, sans appel à l'IA.
- 📖 **Lecture Confluence** : récupération du contenu d'une page via son ID (format *storage*) et recherche de pages par espace + titre (CQL).
- 🧠 **Découpage Scrum proportionné** : 1 Epic pour un besoin court, un Epic par module pour un cahier des charges complet — sans jamais inventer de fonctionnalités absentes du document.
- 📝 **User stories normalisées** : format *As a / I want / So that*, titre, description.
- ✅ **Critères d'acceptation testables** au format *Given / When / Then* (2 à 5 par story).
- 🔢 **Estimation en story points** (suite de Fibonacci : 1, 2, 3, 5, 8, 13) et heures estimées par tâche.
- 🏷️ **Priorités et labels techniques** (`backend`, `frontend`, `api`, `security`, `devops`…), nettoyés et normalisés automatiquement.

### Verdict « prêt pour un agent de code »
- 🤖 Chaque story est évaluée : `aiReady = true` **uniquement** si le périmètre est clair, les critères testables et sans dépendance externe non précisée.
- 🔀 Le verdict peut être confié à un **fournisseur IA distinct** de l'analyse (ex. structure par Ollama local, verdict par Gemini, Claude ou un proxy LiteLLM).
- 🛡️ **Sécurité par défaut** : verdict absent, ambigu ou en erreur ⇒ `validation-necessaire`.
- ♻️ **Verdict recalculé à la création** (`create-jira`) : le contenu a pu être modifié depuis l'aperçu, le serveur rejuge donc chaque story sur son contenu final (par lots de `refinr.llm.verdict.batch-size` stories, 10 par défaut ; les stories inchangées depuis l'aperçu reprennent leur verdict en cache) ; les champs `aiReady` / `aiReadyReason` envoyés par le client sont **ignorés**.
- 🔁 **Réévaluation** : une story en attente peut être réévaluée avec une précision de l'utilisateur ; la description Jira d'origine est conservée (images, tableaux, liens) et complétée, le label est mis à jour.

### Création Jira
- 🏗️ Création complète Epic → Story (liée via `parent`) → Subtask.
- 📝 Descriptions écrites en Markdown et converties en **ADF** (titres, gras, italique, listes, code en ligne et blocs de code) : rendu natif dans Jira, critères d'acceptation en liste.
- 🎯 Projet cible **configurable par requête** (`projectKey`) ou projet par défaut.
- ⚙️ Compatible projets **team-managed** (sans champ *priority*) via `jira.priority-enabled=false`.
- 🔢 **Story points** envoyés dans le champ configuré par `jira.story-points-field` (désactivé si vide). L'identifiant dépend de l'instance : `GET /rest/api/3/field`, champ nommé *Story point estimate* (team-managed) ou *Story Points* (company-managed), souvent `customfield_10016`. Le champ doit figurer sur l'écran de création des stories.
- 👀 **Mode prévisualisation** : générer la structure sans rien créer, l'ajuster, puis l'envoyer à Jira.

### Fiabilité & observabilité
- 🔄 **Nouveaux essais ciblés** (lectures et appels IA uniquement) ; la création Jira n'est **jamais** rejouée, pour ne pas créer de doublons :

  | Appel | Nouvel essai | Détail |
  |---|---|---|
  | Lecture d'une page Confluence | ✅ sur HTTP `429` et `500` | 2 nouveaux essais, après 1 s puis 2 s (`503` et autres erreurs : pas de nouvel essai) |
  | Appels IA (analyse, verdict, réévaluation), tous fournisseurs | ✅ sur HTTP `429`, `5xx` (dont `503` de Gemini et `529` d'Anthropic) et fournisseur injoignable | 2 nouveaux essais, `Retry-After` prioritaire (plafonné à 30 s), sinon 2 s puis 8 s à ± 20 % près (configurable : `refinr.llm.*.max-retries`, `retry-base-delay`, `retry-multiplier`, `retry-jitter`, `retry-max-delay`) ; jamais après un délai dépassé (`timeout` : 300 s analyse, 90 s verdict) ni sur une autre erreur `4xx` (`401` : clé refusée) |
  | Sortie IA invalide (JSON illisible ou non conforme au schéma) | ✅ relance **avec la liste des erreurs** | `refinr.llm.max-repair-attempts` (2) relances, puis `502` (`create-jira` : `validation-necessaire`) |
  | Sortie IA tronquée (limite de tokens) | ❌ | erreur explicite `502` : augmenter `max-output-tokens` |
  | Recherche Confluence | ❌ | |
  | **Création Jira** (Epic, Story, Subtask) | ❌ | POST non idempotent : un échec est listé dans la réponse partielle (`created` / `failed`, statut `207` ou `502`), les autres éléments sont créés ; relancez seulement ce qui a échoué |
  | Lecture / mise à jour Jira (`reassess-story`), health check | ❌ | |
- 🩺 **Health check par port** : état de Confluence, Jira et de chaque fournisseur IA (clé et modèle vérifiés par une lecture gratuite, présence du modèle Ollama incluse), mis en cache 45 s, sans aucune clé ni URL dans la réponse.
- 📊 **Logs JSON structurés** (Logstash) avec `correlationId` par requête.

### Interface web
- 🏠 Page d'accueil, page **Analyze** (saisie de l'ID de page Confluence, aperçu Epics > Stories > Sous-tâches avec le verdict de chaque story et sa raison, puis création sur confirmation), page **Results** (compteurs Epics / Stories / Subtasks, tickets créés et, en cas de création partielle, éléments non créés avec leur raison).
- 🟢 Indicateurs d'état des services dans l'en-tête.

---

## ⚙️ Mode de fonctionnement

```mermaid
sequenceDiagram
    autonumber
    actor U as Utilisateur
    participant F as Frontend (React)
    participant B as Backend Refinr
    participant C as Confluence Cloud
    participant A as LLM d'analyse<br/>(anthropic / google / openai-compatible)
    participant V as LLM de verdict<br/>(optionnel)
    participant J as Jira Cloud

    U->>F: Saisit l'ID de la page Confluence
    F->>B: POST /api/v1/analyze-and-create
    B->>C: GET /wiki/rest/api/content/{id}?expand=body.storage
    C-->>B: Contenu HTML de la page
    B->>A: Prompt « Scrum Master » + document
    A-->>B: JSON {epics: [Epic → Stories → Tasks]} validé par JSON Schema
    opt refinr.llm.verdict.provider renseigné
        loop pour chaque lot de 10 stories
            B->>V: Prompt de verdict (lot)
            V-->>B: {verdicts: [{id, aiReady, aiReadyReason}]}
        end
    end
    loop pour chaque Epic / Story / Task
        B->>J: POST /rest/api/3/issue
        J-->>B: Clé Jira (ex. PROJ-42)
    end
    B-->>F: {success, message, epics}
    F-->>U: Résultats
```

**En résumé :**

1. **Lecture** — le backend récupère la page Confluence avec un token API Atlassian (Basic Auth).
2. **Analyse** — le contenu est injecté dans un prompt structuré ; le LLM répond en JSON (mode JSON natif du fournisseur), validé par un JSON Schema ; une sortie invalide est relancée avec la liste des erreurs.
3. **Verdict** — soit inclus dans l'analyse, soit calculé par un fournisseur dédié, par lots de stories (un appel par lot, verdicts en cache par contenu de story).
4. **Création** — chaque Epic, Story puis Sous-tâche est créée dans Jira ; la description est convertie en ADF (Atlassian Document Format) et la story reçoit le label `ai-ready` ou `validation-necessaire`.
5. **Réévaluation** (cycle de vie) — `POST /reassess-story` relit une story Jira en `validation-necessaire`, la réévalue avec la précision fournie et met à jour description + label.

---

## 🧱 Architecture du projet

Architecture **hexagonale** (ports & adaptateurs) : le domaine et les cas d'usage sont en Java pur, sans Spring ni
client HTTP (règles vérifiées par ArchUnit) ; chaque service externe est derrière un port.

```mermaid
flowchart LR
    UI["Frontend React<br/>(nginx)"] -->|REST /api/v1| REST
    subgraph backend["Backend Spring Boot"]
        REST["adapters/in/rest<br/>contrôleurs, DTO, erreurs"]
        subgraph core["Cœur : Java pur"]
            UC["application<br/>cas d'usage : AnalyzeDocument, PreviewBacklog,<br/>CreateBacklog, ReassessStory, SearchDocuments, CheckHealth"]
            DOM["domain<br/>Epic, Story, Task, Readiness<br/>labels réservés, verdict par défaut"]
            UC --> DOM
        end
        REST --> UC
        UC --> P1(["DocumentSourcePort"])
        UC --> P2(["BacklogGeneratorPort"])
        UC --> P3(["ReadinessAssessorPort"])
        UC --> P4(["IssueTrackerPort"])
        P1 -.- CONF["adapters/out/confluence"]
        P2 -.- LLM["adapters/out/llm<br/>anthropic · google · openai-compatible"]
        P3 -.- LLM
        P4 -.- JIRA["adapters/out/jira<br/>ADF, labels, story points"]
    end
    CONF --> C[("Confluence Cloud")]
    LLM --> L[("Fournisseur LLM")]
    JIRA --> J[("Jira Cloud")]
```

```
refinr/
├── .env.example                             # Modèle des secrets locaux (à copier en .env)
└── backend/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/fr/heliocraft/refinr/
        │   │   ├── RefinrApplication.java           # Point d'entrée Spring Boot
        │   │   ├── domain/
        │   │   │   ├── model/                       # Epic, Story, Task, Readiness, TrackedStory (Java pur)
        │   │   │   └── service/                     # Règles : labels réservés, verdict par défaut, normalisation
        │   │   ├── application/
        │   │   │   ├── port/in/                     # AnalyzeDocument, PreviewBacklog, CreateBacklog, ReassessStory, SearchDocuments, CheckHealth
        │   │   │   ├── port/out/                    # DocumentSourcePort, IssueTrackerPort, BacklogGeneratorPort, ReadinessAssessorPort
        │   │   │   └── service/                     # Implémentation des cas d'usage
        │   │   ├── adapters/
        │   │   │   ├── in/rest/                     # BacklogController (/api/v1/*), HealthCheckController, DTO JSON
        │   │   │   └── out/
        │   │   │       ├── confluence/              # ConfluenceDocumentSource
        │   │   │       ├── jira/                    # JiraIssueTracker : labels, priorité, ADF, réévaluation
        │   │   │       └── llm/                     # Clients anthropic / google / openai-compatible, analyse, verdict par lots, cache, JSON Schema
        │   │   └── config/                          # Câblage des cas d'usage et des fournisseurs LLM, Retry, LoggingFilter (correlationId)
        │   └── resources/
        │       ├── application-example.properties   # Modèle de configuration (à copier)
        │       ├── llm/schemas/                     # JSON Schema des sorties IA (backlog, verdict, verdicts par lot)
        │       ├── application.properties           # Copie locale du modèle — ignorée par Git
        │       └── logback-spring.xml               # Logs JSON
        └── test/                                    # Tests unitaires + intégration (MockWebServer)
```

---

## 📋 Prérequis

### Logiciels

| Outil | Version | Usage |
|---|---|---|
| **JDK** | 25 | Backend |
| **Maven** | 3.9+ | Build backend |
| **Node.js** + npm | 22.12+ (référence : `.nvmrc`) | Frontend |
| **Docker** + Compose v2 | récent | Optionnel : lancer toute la stack sans JDK ni Node (`docker compose up`) |
| **Ollama** | récent | Analyse IA locale (fournisseur par défaut, via son API compatible OpenAI `/v1`) |
| Autre fournisseur IA | optionnel | Clé Anthropic, Google AI Studio ou d'une API compatible OpenAI (OpenAI, DeepSeek, Mistral, Groq, OpenRouter, LiteLLM, vLLM…) |

### Comptes & accès

- Un site **Atlassian Cloud** (`https://<votre-site>.atlassian.net`) avec Confluence et Jira.
- Un **token API Atlassian** : <https://id.atlassian.com/manage-profile/security/api-tokens>.
- Les droits de **lecture** sur l'espace Confluence et de **création de tickets** dans le projet Jira.
- Le projet Jira doit proposer les types de tickets **`Epic`**, **`Story`** et **`Subtask`**.
- *(Fournisseur `google`)* une clé **Google AI Studio** : <https://aistudio.google.com/apikey> ; *(fournisseur `anthropic`, non testé en conditions réelles)* une clé API Anthropic.

### Matériel (mode Ollama)

Le modèle par défaut `qwen3:8b` nécessite environ **8 Go de RAM/VRAM libres**. Sur une machine plus modeste, choisissez un modèle plus léger ou un fournisseur en ligne (`google`, `anthropic`, `openai-compatible`).

---

## 🛠️ Installation

### 1. Cloner le dépôt

```bash
git clone https://github.com/chams-bensalem/refinr.git
cd refinr
git config core.hooksPath .githooks   # active les hooks pre-commit (gitleaks) et commit-msg
```

### 2. Préparer l'IA locale (Ollama)

```bash
# macOS
brew install ollama
ollama serve            # démarre le serveur sur http://localhost:11434
ollama pull qwen3:8b    # télécharge le modèle par défaut
```

Refinr appelle Ollama par son API compatible OpenAI (`http://localhost:11434/v1`). Gardez
`LLM_ANALYSIS_REASONING_EFFORT=none` (valeur de `.env.example`) : sans cela, `qwen3` « réfléchit » avant de répondre et
l'analyse peut dépasser le délai. Sur une machine modeste (quelques tokens/s), augmentez `LLM_ANALYSIS_TIMEOUT`
(ex. `30m`).

### 3. Installer le frontend

```bash
cd frontend
npm install
```

---

## 🔐 Configuration & connexion aux services

### Backend

Les secrets vivent **uniquement** dans le fichier `.env` à la racine du dépôt (ignoré par Git).
Copiez les deux modèles puis renseignez vos valeurs dans `.env` :

```bash
cp .env.example .env
cp backend/src/main/resources/application-example.properties backend/src/main/resources/application.properties
```

`application.properties` ne contient que des références `${VAR:défaut}` et importe `.env`
(`spring.config.import=optional:file:../.env[.properties]`). Une vraie variable d'environnement est prioritaire sur `.env`.

> ⚠️ `application.properties` est listé dans `.gitignore` : **ne le commitez jamais**. Préférez les variables d'environnement pour les secrets (`${JIRA_TOKEN:}`, etc.).

```properties
server.port=8080

# --- Confluence -------------------------------------------------
confluence.base-url=${CONFLUENCE_BASE_URL:https://your-site.atlassian.net}
confluence.username=${CONFLUENCE_USERNAME:you@example.com}
confluence.token=${CONFLUENCE_TOKEN:}
confluence.space-key=SPACE

# --- Jira -------------------------------------------------------
jira.base-url=${JIRA_BASE_URL:https://your-site.atlassian.net}
jira.username=${JIRA_USERNAME:you@example.com}
jira.token=${JIRA_TOKEN:}
jira.project-key=PROJ            # projet par défaut
jira.priority-enabled=true       # false pour un projet team-managed
jira.story-points-field=         # ex. customfield_10016 ; vide = story points non envoyés

# --- IA : analyse (structure Epic/Story/Task) -------------------
# anthropic | google | openai-compatible (OpenAI, DeepSeek, Mistral, Groq, OpenRouter, Ollama, LiteLLM, vLLM)
# provider vide : Ollama local (http://localhost:11434/v1, qwen3:8b) ; provider explicite : base-url et model à fournir
# (base-url vide = URL officielle pour anthropic / google, obligatoire pour openai-compatible)
refinr.llm.analysis.provider=${LLM_ANALYSIS_PROVIDER:}
refinr.llm.analysis.base-url=${LLM_ANALYSIS_BASE_URL:}
refinr.llm.analysis.model=${LLM_ANALYSIS_MODEL:}
refinr.llm.analysis.api-key=${LLM_ANALYSIS_API_KEY:}
refinr.llm.analysis.timeout=300s
refinr.llm.analysis.max-output-tokens=32768

# --- IA : verdict ai-ready (optionnel) --------------------------
# provider vide = le verdict est produit par l'appel d'analyse
refinr.llm.verdict.provider=${LLM_VERDICT_PROVIDER:}
refinr.llm.verdict.base-url=${LLM_VERDICT_BASE_URL:}
refinr.llm.verdict.model=${LLM_VERDICT_MODEL:}
refinr.llm.verdict.api-key=${LLM_VERDICT_API_KEY:}
refinr.llm.verdict.timeout=90s
refinr.llm.verdict.max-output-tokens=4096
```

#### Tableau des propriétés

| Propriété | Défaut | Description |
|---|---|---|
| `tracker.provider` | `jira` | Gestionnaire de tickets de l'instance (un seul par instance) : `jira` ou `openproject`. Valeur inconnue ou propriété absente du gestionnaire choisi : arrêt au démarrage avec un message explicite |
| `confluence.base-url` / `jira.base-url` | — | URL du site Atlassian Cloud (sans `/wiki`) |
| `confluence.username` / `jira.username` | — | E-mail du compte Atlassian |
| `confluence.token` / `jira.token` | — | Token API Atlassian |
| `jira.project-key` | — | Clé du projet utilisé si `projectKey` n'est pas fourni |
| `jira.priority-enabled` | `true` | Envoie le champ `priority` (désactiver en team-managed) |
| `jira.story-points-field` | *(vide)* | Champ personnalisé des story points (ex. `customfield_10016`, cf. ci-dessous) ; vide = non envoyés |
| `openproject.base-url` | — | URL d'OpenProject (avec `tracker.provider=openproject`) |
| `openproject.api-key` | — | Clé API du compte Refinr (*Mon compte → Jetons d'accès → API*) |
| `openproject.project` | — | Projet par défaut (identifiant ou id numérique) si `projectKey` n'est pas fourni |
| `openproject.types.epic` / `.story` / `.subtask` | `Epic` / `User story` / `Task` | Types de work package utilisés pour chaque niveau |
| `openproject.readiness-field` | `Agent readiness` | Champ personnalisé de type liste qui porte le verdict (options `ai-ready` / `validation-necessaire`) |
| `openproject.story-points-enabled` | `true` | Envoie `storyPoints` sur les User stories uniquement (module Backlogs) |
| `openproject.priority-enabled` | `true` | Envoie la priorité (par nom, `Medium` → `Normal`) |
| `refinr.llm.analysis.provider` | `openai-compatible` | Fournisseur de l'analyse : `anthropic`, `google` ou `openai-compatible`. Non renseigné : Ollama local, soit `openai-compatible` + `http://localhost:11434/v1` + `qwen3:8b` |
| `refinr.llm.analysis.base-url` | *(vide)* | URL de l'API (avec `/v1` pour `openai-compatible`). Vide = URL officielle pour `anthropic` / `google` ; **obligatoire** pour `openai-compatible` choisi explicitement (sinon échec au démarrage) |
| `refinr.llm.analysis.model` | *(vide)* | Modèle de l'analyse (et du health check), obligatoire dès qu'un fournisseur est choisi |
| `refinr.llm.analysis.api-key` | *(vide)* | Clé API (obligatoire pour `anthropic` et `google`) |
| `refinr.llm.analysis.timeout` | `300s` | Délai maximal par appel d'analyse |
| `refinr.llm.analysis.temperature` | `0.2` | Température (verdict : `0.1`). Vide = non envoyée, défaut du fournisseur (obligatoire pour les modèles OpenAI à raisonnement) ; jamais envoyée à `anthropic` |
| `refinr.llm.analysis.max-tokens-param` | `max_tokens` | `openai-compatible` : nom du paramètre de limite de sortie, `max_tokens`, `max_completion_tokens` (modèles OpenAI à raisonnement) ou `none` (idem `refinr.llm.verdict.*`) |
| `refinr.llm.analysis.extra-body.*` | *(vide)* | `openai-compatible` : propriétés ajoutées au corps de la requête ; `reasoning_effort` vient de `LLM_ANALYSIS_REASONING_EFFORT` (`none` pour Ollama) |
| `refinr.llm.analysis.max-output-tokens` | `32768` | Limite de tokens de sortie (sortie tronquée ⇒ erreur explicite) |
| `refinr.llm.analysis.max-retries` | `2` | Nouveaux essais sur `429`, `5xx` et fournisseur injoignable (idem `refinr.llm.verdict.*`) |
| `refinr.llm.analysis.retry-base-delay` | `2s` | Délai avant le 1ᵉʳ nouvel essai |
| `refinr.llm.analysis.retry-multiplier` | `4` | Facteur entre deux délais (défauts : 2 s puis 8 s) |
| `refinr.llm.analysis.retry-jitter` | `0.2` | Part aléatoire du délai (± 20 %), pour ne pas relancer tous en même temps |
| `refinr.llm.analysis.retry-max-delay` | `30s` | Plafond d'un délai, y compris un `Retry-After` du fournisseur (prioritaire sur le backoff) |
| `refinr.llm.max-repair-attempts` | `2` | Relances avec la liste des erreurs quand la sortie ne respecte pas le schema JSON, puis `502` |
| `refinr.llm.verdict.provider` | *(vide)* | Fournisseur dédié au verdict `ai-ready` ; vide = celui de l'analyse |
| `refinr.llm.verdict.timeout` | `90s` | Délai maximal par appel de verdict |
| `refinr.llm.verdict.max-output-tokens` | `4096` | Limite de tokens de sortie du verdict |
| `refinr.llm.verdict.batch-size` | `10` | Stories jugées par appel de verdict (`1` = un appel par story) |
| `refinr.llm.verdict.cache.ttl` | `24h` | Durée de vie du cache des verdicts réussis (`0s` = pas de cache) |
| `refinr.llm.verdict.cache.max-entries` | `1000` | Taille maximale du cache des verdicts (LRU) |
| `refinr.health.cache-ttl` | `45s` | Durée de mise en cache du health check (`0s` = pas de cache) |
| `refinr.security.allowed-hosts` | `localhost,127.0.0.1,[::1]` | Hôtes acceptés dans l'en-tête `Host` (protection contre le DNS rebinding), séparés par des virgules, port ignoré ; sinon `403`, y compris sur les health checks ; `*` et liste vide refusés au démarrage (`REFINR_ALLOWED_HOSTS`, même variable pour le frontend) |
| `refinr.cors.allowed-origins` | `http://localhost:3000` | Origines autorisées à appeler l'API depuis un navigateur (CORS), séparées par des virgules ; vide = même origine uniquement ; `*` refusé au démarrage (`CORS_ALLOWED_ORIGINS`) |
| `refinr.security.api-key` | *(vide)* | Clé d'API optionnelle : si renseignée, toute requête `/api/**` doit envoyer l'en-tête `X-API-Key` (sauf `/api/v1/health` et `/api/v1/health/live`), sinon `401` ; vide = désactivée (`REFINR_API_KEY`) |

#### Variables d'environnement (recommandé)

Voir `.env.example` pour la liste complète (`CONFLUENCE_TOKEN`, `JIRA_TOKEN`, `LLM_ANALYSIS_API_KEY`,
`LLM_VERDICT_API_KEY`…). Les mêmes noms peuvent être fournis comme variables d'environnement.

#### Migration depuis l'ancienne configuration IA

Les anciennes clés sont **refusées au démarrage** (message listant la correspondance, jamais les valeurs) :

| Ancienne clé (`.env` / propriété) | Nouvelle clé |
|---|---|
| `AI_PROVIDER=ollama` / `ai.provider` | `LLM_ANALYSIS_PROVIDER=openai-compatible` |
| `AI_PROVIDER=gemini` | `LLM_ANALYSIS_PROVIDER=google` |
| `OLLAMA_BASE_URL` / `ollama.base-url` | `LLM_ANALYSIS_BASE_URL` **avec `/v1`** (ex. `http://localhost:11434/v1`) |
| `OLLAMA_MODEL`, `GEMINI_MODEL` | `LLM_ANALYSIS_MODEL` |
| `GEMINI_API_KEY` / `gemini.api-key` | `LLM_ANALYSIS_API_KEY` (ou `LLM_VERDICT_API_KEY`) |
| `AI_VERDICT_PROVIDER=litellm` | `LLM_VERDICT_PROVIDER=openai-compatible` |
| `AI_VERDICT_BASE_URL` | `LLM_VERDICT_BASE_URL` **avec `/v1`** (ex. `http://localhost:4000/v1`) |
| `AI_VERDICT_MODEL`, `AI_VERDICT_API_KEY` | `LLM_VERDICT_MODEL`, `LLM_VERDICT_API_KEY` |
| `ai.verdict.timeout-seconds=90` | `refinr.llm.verdict.timeout=90s` |

Les variables d'environnement **du système** ne sont pas contrôlées (une `GEMINI_API_KEY` peut servir à d'autres outils) : seuls les fichiers de configuration et le `.env` le sont.

### Frontend

Aucune variable n'est nécessaire : le client appelle `/api/v1`. L'URL de l'API est lue **au chargement de la page**
dans `/config.js` (`window.__REFINR_CONFIG__.apiBaseUrl`, défaut `/api/v1`) : l'image Docker la génère depuis
`REFINR_API_BASE_URL`, sans rebuild.

En développement, Vite **proxifie `/api` vers `http://localhost:8080`** (voir `vite.config.ts`) : aucune configuration CORS supplémentaire n'est nécessaire.

### Vérifier la connexion

Une fois le backend démarré :

```bash
curl -s http://localhost:8080/api/v1/health | jq
```

```json
{
  "status": "UP",
  "services": {
    "confluence": { "status": "UP" },
    "jira":       { "status": "UP" },
    "ai":         { "status": "UP", "provider": "openai-compatible", "model": "qwen3:8b" }
  }
}
```

`DEGRADED` signifie qu'au moins un service est `DOWN` ; le champ `error` donne une cause courte et sans donnée sensible : `HTTP 401` (token invalide), `HTTP 404` (projet introuvable), `unreachable` (service injoignable), `timeout`, `model qwen3:8b not found` (modèle Ollama non téléchargé)… `aiVerdict` apparaît quand un fournisseur de verdict distinct est configuré. Le résultat est gardé en cache `refinr.health.cache-ttl` (45 s). Le détail est dans les logs du backend.

En cas d'erreur, les endpoints du backlog (`/api/v1/*`, hors health checks) renvoient `{ "success": false, "error": "<message générique>", "correlationId": "…" }` : jamais le message brut de l'exception ; le `correlationId` permet de retrouver le détail dans les logs.

---

## ▶️ Lancer l'application

```bash
# 1. IA locale
ollama serve

# 2. Backend  →  http://localhost:8080
cd backend
mvn spring-boot:run

# 3. Frontend →  http://localhost:3000
cd frontend
npm run dev
```

### Avec Docker Compose

Toute la stack (backend + frontend) sans JDK ni Node, configurée par le même `.env` :

```bash
cp .env.example .env              # renseignez Confluence, Jira et le fournisseur IA
docker compose up -d --build      # UI http://localhost:3000 · API http://localhost:8080
curl localhost:8080/api/v1/health
```

| Service | Image | Port (hôte) | Détails |
|---|---|---|---|
| `backend` | `refinr-backend` — JRE 25 Alpine, utilisateur non-root `10001` | `127.0.0.1:8080` | Configuration par variables d'environnement uniquement (`.env`), sonde `GET /api/v1/health/live` |
| `frontend` | `refinr-frontend` — nginx non-root (`101`), fichiers statiques | `127.0.0.1:3000` | Proxy `/api` → `REFINR_BACKEND_URL` (`http://backend:8080`) ; ajoute `X-API-Key` côté serveur si `REFINR_API_KEY` est défini |
| `ollama` *(profil `ollama`)* | `ollama/ollama` | — (réseau interne) | `docker compose --profile ollama up -d` ; télécharge `LLM_ANALYSIS_MODEL` au premier démarrage |

- Dans un conteneur, `localhost` désigne le conteneur lui-même : un Ollama lancé sur votre machine est joignable à
  `LLM_ANALYSIS_BASE_URL=http://host.docker.internal:11434/v1`, celui du profil `ollama` à `http://ollama:11434/v1`.
- Les ports ne sont publiés que sur `127.0.0.1` ; conteneurs en système de fichiers en lecture seule, sans capacité Linux,
  `no-new-privileges`. Images multi-architecture (`linux/amd64`, `linux/arm64`).

#### Sécurité

- **`X-API-Key` protège l'accès direct à l'API** (`:8080`, scripts, outils appelants), **pas l'interface web** :
  le proxy nginx du frontend ajoute lui-même la clé à chaque appel `/api`, donc quiconque atteint le frontend
  utilise l'API avec cette clé.
- **N'exposez pas le frontend sur Internet sans authentification placée devant** (reverse proxy avec SSO / OAuth2,
  VPN, réseau privé). Par défaut, les ports ne sont publiés que sur `127.0.0.1`.
- **Hôtes autorisés (`REFINR_ALLOWED_HOSTS`, défaut `localhost,127.0.0.1,[::1]`)** : protection contre le
  *DNS rebinding*, où une page malveillante fait pointer son propre nom de domaine vers `127.0.0.1` pour appeler
  l'API locale depuis votre navigateur, avec la clé ajoutée par le proxy. Le navigateur envoie alors ce nom dans
  `Host` : nginx ferme la connexion (`444`) et le backend répond `403` à tout `Host` non listé. Le contrôle est
  toujours actif, même sans `REFINR_API_KEY`, y compris sur `/api/v1/health/live`. Les ports sont ignorés
  (`localhost:3000` et `localhost` sont équivalents).
- **Déploiement réseau** : ajoutez à la liste le nom DNS ou l'IP par lesquels Refinr est joint, par exemple
  `REFINR_ALLOWED_HOSTS=localhost,127.0.0.1,[::1],refinr.intranet.example,10.0.0.12` dans `.env` (Compose transmet
  la même liste au backend et au frontend). Derrière un reverse proxy, c'est le `Host` transmis par ce proxy qui
  compte : gardez l'en-tête d'origine (`proxy_set_header Host $host` pour nginx) ou listez le nom qu'il envoie.
  Les noms exacts seulement : pas de joker (`*`, `*.example.com`). Si l'API est appelée depuis une autre origine,
  ajoutez aussi cette origine à `CORS_ALLOWED_ORIGINS`.
- `GET /api/v1/health` (détaillé : état de Confluence, Jira, fournisseur et modèle IA, sans clé ni URL) n'exige pas
  de clé, comme la sonde `/api/v1/health/live` (mais tous deux exigent un `Host` autorisé).

Build de production sans Docker :

```bash
(cd backend && mvn clean package && java -jar target/refinr-1.0.1.jar)
(cd frontend && npm run build)   # génère frontend/dist/
```

---

## 🧭 Utilisation

### Trouver l'ID d'une page Confluence

Ouvrez la page dans Confluence : l'ID est le nombre dans l'URL.

```
https://votre-site.atlassian.net/wiki/spaces/SPACE/pages/123456789/Mon+cahier+des+charges
                                                         ^^^^^^^^^
```

Ou recherchez-la via l'API :

```bash
curl "http://localhost:8080/api/v1/confluence/search?spaceKey=SPACE&title=cahier"
```

```json
{ "success": true, "results": [ { "id": "123456789", "title": "Mon cahier des charges", "url": "https://your-site.atlassian.net/wiki/spaces/SPACE/pages/123456789/Mon+cahier+des+charges" } ] }
```

### Via l'interface web

1. Ouvrez <http://localhost:3000> et vérifiez que les indicateurs de santé de l'en-tête sont **verts**.
2. Ouvrez **Analyze a spec**.
3. Collez l'**ID de la page Confluence** et cliquez sur **Preview** : rien n'est encore créé.
4. Relisez l'aperçu Epics > Stories > Sous-tâches et, pour chaque story, le badge `ai-ready` / `validation-necessaire`
   et sa raison.
5. Cliquez sur **Create in Jira** (le verdict est recalculé par le serveur sur ce contenu), puis consultez le
   récapitulatif sur la page **Results** et ouvrez votre projet Jira.

### Via l'API (workflow recommandé : prévisualiser puis créer)

```bash
# 1. Prévisualiser la structure sans rien créer
curl -s -X POST http://localhost:8080/api/v1/analyze-preview \
  -H "Content-Type: application/json" \
  -d '{"confluencePageId": "123456789"}' > preview.json

# 2. (optionnel) Relire / modifier les epics dans preview.json
#    (le verdict ai-ready sera recalculé à la création : inutile de modifier aiReady / aiReadyReason)

# 3. Créer dans Jira à partir de la prévisualisation (projectKey optionnel)
jq '.epics' preview.json | curl -s -X POST "http://localhost:8080/api/v1/create-jira?projectKey=PROJ" \
  -H "Content-Type: application/json" -d @-
```

Ou en une seule étape, éventuellement dans un autre projet :

```bash
curl -s -X POST http://localhost:8080/api/v1/analyze-and-create \
  -H "Content-Type: application/json" \
  -d '{"confluencePageId": "123456789", "projectKey": "DEMO"}'
```

### Débloquer une story `validation-necessaire`

```bash
curl -s -X POST http://localhost:8080/api/v1/reassess-story \
  -H "Content-Type: application/json" \
  -d '{"issueKey": "PROJ-42", "precision": "L’authentification se fait via OAuth2 GitLab, la maquette est validée."}'
```

```json
{ "success": true, "issueKey": "PROJ-42", "aiReady": true, "aiReadyReason": "Périmètre et critères désormais complets." }
```

### 💡 Conseils pour un bon résultat

- Structurez la page Confluence par **modules / fonctionnalités** (titres clairs) : chaque module devient un Epic.
- Précisez les **rôles utilisateurs**, les **règles métier** et les **contraintes techniques** (stack, API, sécurité).
- Mentionnez explicitement les dépendances (accès, clés d'API, maquettes) : sinon les stories seront marquées `validation-necessaire`.
- Utilisez `analyze-preview` sur un nouveau document avant toute création massive.

---

## 📡 Référence de l'API

Base URL : `http://localhost:8080/api/v1`

| Méthode | Endpoint | Corps / paramètres | Description |
|---|---|---|---|
| `POST` | `/analyze-and-create` | `{ "confluencePageId": "…", "projectKey": "PROJ" }` *(projectKey optionnel : projet du gestionnaire de tickets actif — clé Jira `^[A-Z][A-Z0-9]{1,9}$`, ou identifiant OpenProject `^[a-z0-9_-]{1,100}$` / id numérique)* | Lit, analyse et crée toute la hiérarchie dans Jira |
| `POST` | `/analyze-preview` | `{ "confluencePageId": "…" }` | Analyse sans créer de tickets |
| `POST` | `/create-jira` | `Epic[]` (JSON) · `?projectKey=PROJ` *(optionnel, même format)* | Crée une structure déjà générée, dans `projectKey` ou le projet par défaut ; verdict `ai-ready` recalculé par le serveur (`aiReady` / `aiReadyReason` du client ignorés) |
| `POST` | `/reassess-story` | `{ "issueKey": "PROJ-42", "precision": "…" }` | Réévalue une story `validation-necessaire` (`409` si elle ne l'est pas) |
| `GET` | `/confluence/search` | `?spaceKey=SPACE&title=…` | Recherche de pages Confluence (CQL) : `{ success, results: [{ id, title, url }] }` |
| `GET` | `/health` | — | Santé détaillée Confluence / Jira / IA (analyse, et verdict s'il est distinct), en cache 45 s |
| `GET` | `/health/live` | — | Sonde de vivacité `{ "status": "UP" }`, sans appel aux services externes (conteneurs, orchestrateurs) |

**Clé d'API** : si `refinr.security.api-key` (`REFINR_API_KEY`) est définie, chaque appel doit envoyer
`X-API-Key: <clé>` (`/health` et `/health/live` exceptés) ; sinon `401` `{ "success": false, "error": "Missing or invalid API key", "correlationId": "…" }`.
**CORS** : seules les origines de `refinr.cors.allowed-origins` sont acceptées (plus de `*`).
**Hôte** : toute requête dont l'en-tête `Host` n'est pas dans `refinr.security.allowed-hosts` reçoit `403`
`{ "success": false, "error": "Host not allowed", "correlationId": "…" }`, health checks compris.

**Réponse de création (`analyze-and-create` et `create-jira`) :**

| Statut | Cas | `success` |
|---|---|---|
| `200` | Tous les tickets ont été créés | `true` |
| `207` | Création partielle : certains tickets créés, d'autres non | `false` |
| `400` | `projectKey` invalide, ou document vide / sans texte à analyser (`analyze-and-create` ; `analyze-preview` aussi, sans appel à l'IA) | `false` |
| `502` | Aucun ticket n'a pu être créé, sortie IA inexploitable (invalide après relances, ou tronquée), ou fournisseur IA indisponible | `false` |

Un échec n'interrompt pas la création : l'élément est listé dans `failed` (avec une raison : statut HTTP et
erreurs de validation de Jira), ses enfants aussi (`Not created: parent … failed`), et les éléments suivants sont créés.

```json
{
  "success": false,
  "message": "Partially created: 2 issues created, 2 not created",
  "created": [
    { "type": "epic",  "title": "Epic: Authentification", "key": "PROJ-1" },
    { "type": "story", "title": "Connexion OAuth2",       "key": "PROJ-2" }
  ],
  "failed": [
    { "type": "story",   "title": "Export PDF",  "reason": "Jira rejected the story (HTTP 400): priority: Field 'priority' cannot be set." },
    { "type": "subtask", "title": "Générer le PDF", "reason": "Not created: parent story Export PDF failed" }
  ],
  "epics": [ { "name": "Epic: Authentification", "jiraKey": "PROJ-1", "stories": [ … ] } ]
}
```

En cas d'erreur : HTTP `400` / `409` / `500`, ou `502` (fournisseur IA indisponible, sortie IA inexploitable), avec `{ "success": false, "error": "…" }`.

---

## 🗂️ Modèle de données

```jsonc
// Epic
{
  "name": "Epic: Gestion des utilisateurs",
  "description": "…",
  "priority": "High",                 // High | Medium | Low
  "labels": ["backend", "security"],
  "jiraKey": "PROJ-1",                // renseigné après création
  "stories": [
    // Story
    {
      "title": "Connexion OAuth2",
      "asA": "Utilisateur", "iWant": "me connecter avec GitLab", "soThat": "accéder à mes dépôts",
      "description": "…",
      "acceptanceCriteria": ["Given …, When …, Then …"],
      "estimatedStoryPoints": 5,
      "priority": "High",
      "labels": ["backend", "api"],
      "aiReady": false,
      "aiReadyReason": "Le fournisseur OAuth et les scopes ne sont pas précisés.",
      "tasks": [
        // Task (créée en Subtask)
        { "title": "Créer le service OAuth", "description": "…", "estimatedHours": 4, "labels": ["backend"] }
      ]
    }
  ]
}
```

**Labels de verdict** posés sur chaque story Jira :

| Label | Signification |
|---|---|
| `ai-ready` | Implémentable par un agent de code sans décision humaine |
| `validation-necessaire` | Information manquante : validation / précision humaine requise |

Ces deux labels sont **réservés** : s'ils apparaissent dans la sortie de l'IA (sur un Epic, une story ou une tâche), ils sont supprimés — seul le verdict explicite les pose.

---

## 🧪 Tests

```bash
cd backend && mvn test
```

Compilation et tests s'exécutent toujours sur un **JDK 25** (`maven-toolchains-plugin`, goal `select-jdk-toolchain`),
même si Maven lui-même tourne sur un autre JDK : le JDK 25 est découvert automatiquement (`JAVA_HOME`, variables
`JAVA25_HOME`…, `~/.m2/toolchains.xml`, dossiers d'installation usuels). Sans JDK 25 installé, le build échoue
explicitement.

La CI GitHub Actions (`.github/workflows/ci.yml`) exécute à chaque PR : tests backend, lint + build frontend,
contrôle des en-têtes de licence (`scripts/check-spdx.sh`), scan gitleaks de tout l'historique, build des deux images
(`amd64` + `arm64`) et `docker compose up` + health check.
Dependabot propose chaque semaine les mises à jour Maven, npm, Docker et GitHub Actions.

| Suite | Couverture |
|---|---|
| `AnthropicLlmClientTest`, `GoogleLlmClientTest`, `OpenAiCompatibleLlmClientTest` | Par fournisseur, sur serveur HTTP simulé : succès (format de requête, schéma), `401` sans nouvel essai, `429` + `Retry-After`, `5xx`, JSON invalide + relance, timeout, troncature, health check, aucun secret dans les erreurs |
| `LlmBacklogGeneratorTest`, `LlmReadinessAssessorTest`, `CachingReadinessAssessorTest` | Prompts, verdicts par lots (identifiants manquants ou en double), cache des seuls verdicts réussis |
| `LlmConfigurationTest` | Défauts par fournisseur, erreurs de configuration au démarrage, refus des anciennes clés |
| `JiraIssueTrackerTest`, `MarkdownAdfTest` | Création Epic/Story/Subtask, labels, priorité, story points, conversion Markdown → ADF, erreurs Jira, réévaluation, projet cible |
| `ConfluenceDocumentSourceTest` | Lecture de page, erreurs 404/401, contenu vide, volumineux ou à caractères spéciaux, recherche typée et échappement CQL |
| `BacklogControllerTest`, `HealthCheckControllerTest`, `CheckHealthServiceTest` | Endpoints, validation `projectKey`, réponse partielle (207/502), sortie IA inexploitable et fournisseur indisponible (502, y compris `reassess-story`), document vide (400 sans appel IA), verdict recalculé à la création, réévaluation (409), erreurs sans message brut, health check en cache |
| `RestJsonContractTest`, `LlmResponseContractTest` | Contrat JSON figé de l'API REST et du parsing de la réponse LLM |
| `CreateBacklogServiceTest`, `CreateEditedBacklogServiceTest`, `ReassessStoryServiceTest`, `domain.service.*Test` | Cas d'usage et règles métier, sans framework |
| `ApplicationWiringTest` | Démarrage du contexte Spring, câblage des cas d'usage, comportement de retry (tel que décrit plus haut) |
| `ConfigurationDefaultsTest` | Valeurs par défaut identiques dans le code, la configuration d'exemple, `.env.example` et les deux README (`README.md`, `README.fr.md`) |
| `HttpSecurityConfigurationTest`, `AllowedHostFilterTest` | Hôtes autorisés (`localhost`, `127.0.0.1`, `[::1]` avec ou sans port acceptés ; `evil.example` refusé en `403`, sonde `/health/live` comprise, sans clé d'API ; `*` ou liste vide refusés), CORS (origine autorisée renvoyée, autre origine refusée, `*` refusé), clé d'API (absente, fausse, valide, health et préflight exemptés, `401` lisible avec CORS, clé jamais renvoyée) |
| `HexagonalArchitectureTest` | Règles d'architecture (ArchUnit) : domaine/application sans framework, sens des dépendances |
| `FullFlowIntegrationTest` | Parcours complet Confluence → IA → Jira et pannes de chaque service |

Les appels HTTP externes sont simulés avec **OkHttp MockWebServer** : aucun accès réseau ni token n'est requis.

---

## 🩹 Dépannage

| Symptôme | Cause probable | Solution |
|---|---|---|
| `ai: DOWN — model qwen3:8b not found` | Modèle non téléchargé | `ollama pull qwen3:8b` |
| `ai: DOWN — unreachable` | Serveur Ollama arrêté (ou `base-url` erronée) | `ollama serve` ; vérifier `LLM_ANALYSIS_BASE_URL` (avec `/v1`) |
| `ai: DOWN — HTTP 401` | Clé du fournisseur refusée | Vérifier `LLM_ANALYSIS_API_KEY` / `LLM_VERDICT_API_KEY` |
| Démarrage refusé : *Obsolete LLM configuration* | Anciennes clés (`ai.provider`, `GEMINI_API_KEY`…) | Renommer selon la correspondance affichée (voir « Migration depuis l'ancienne configuration IA ») |
| `confluence: DOWN` / `401` | Token ou e-mail Atlassian invalide | Régénérer le token API, vérifier `*.username` |
| `jira: DOWN` / `404` | `jira.project-key` inexistant ou inaccessible | Vérifier la clé et les droits du compte |
| `Failed to create Epic … priority` | Projet team-managed sans champ priorité | `jira.priority-enabled=false` |
| `issuetype … invalid` | Types `Epic` / `Story` / `Subtask` absents | Vérifier la configuration du projet Jira |
| `502` *did not produce a valid result* | Sortie IA non conforme au schéma, même après relances | Utiliser un modèle plus capable ; `json-mode=json_schema` si le fournisseur le permet |
| `502` *output was truncated* | Limite de tokens atteinte | Augmenter `refinr.llm.analysis.max-output-tokens` |
| `502` *The AI provider is unavailable* | Fournisseur injoignable, clé refusée, quota épuisé ou délai dépassé (après les nouveaux essais) | Voir `GET /api/v1/health` et les logs (`correlationId`) |
| `400` *The document is empty or contains no text to analyze* | Page Confluence vide, non publiée ou sans texte (images seules) | Publier le contenu de la page, puis relancer |
| Toutes les stories en `validation-necessaire` | Fournisseur de verdict injoignable | Vérifier `LLM_VERDICT_BASE_URL` / `LLM_VERDICT_API_KEY` ou vider `LLM_VERDICT_PROVIDER` |

Les logs sont émis en **JSON** ; chaque requête porte un `correlationId` pour suivre tout son parcours.

---

## 🗺️ Limites connues & feuille de route

**Limites de la v1.0**

- La création n'est pas transactionnelle : un échec en cours de route laisse les tickets déjà créés
  (réponse partielle `created` / `failed`, à relancer pour les seuls éléments en échec).
- Confluence Cloud et Jira Cloud uniquement ; types de tickets `Epic`, `Story` et `Subtask` requis.
- L'aperçu de l'interface web est en lecture seule et la réévaluation n'y figure pas encore (modification de l'aperçu et réévaluation disponibles par l'API).
- Pas d'authentification des utilisateurs : clé d'API partagée, ajoutée par le proxy (cf. [Sécurité](#sécurité)).
- Cache des verdicts en mémoire, local à chaque instance.

**Feuille de route**

- [x] **v1.0** : Confluence → Jira, verdict `ai-ready`, fournisseurs `anthropic` / `google` / `openai-compatible`,
  images Docker multi-arch, CI.
- [ ] **v1.1** : **OpenProject** comme gestionnaire de tickets (nouvel adaptateur de `IssueTrackerPort`).
- [ ] **v1.2** : **sources documentaires au-delà de Confluence** : envoi de fichier (Markdown, Word, PDF) et texte collé,
  via de nouveaux adaptateurs de `DocumentSourcePort`.
- [ ] **v1.3** : **démo hébergée** avec **BYOK** (*bring your own key* : chacun fournit sa propre clé de fournisseur IA),
  vraie authentification des utilisateurs de l'interface et de l'API, `/api/v1/health` détaillé protégé
  (seule la sonde `/health/live` restera publique).
- [ ] Modification de l'aperçu et réévaluation dans l'interface web (aperçu en lecture seule depuis la 1.0.1).

---

## 🤝 Contribuer

Les contributions sont les bienvenues : lisez [CONTRIBUTING.md](CONTRIBUTING.md) (mise en place, hooks git, conventions)
et le [Code de conduite](CODE_OF_CONDUCT.md). Une première contribution demande la signature du
[CLA](CLA.md), en un clic sur la pull request. Vulnérabilité : ne pas ouvrir d'issue publique, voir [SECURITY.md](SECURITY.md).

## 📜 Licence

Copyright © 2026 Chams Bensalem. Refinr est distribué sous licence
**[GNU Affero General Public License v3.0 only](LICENSE)** (`AGPL-3.0-only`) : chaque fichier source porte l'en-tête
`SPDX-License-Identifier: AGPL-3.0-only`. Si vous proposez une version modifiée de Refinr à des utilisateurs
via un réseau, vous devez leur donner accès à son code source (article 13 de l'AGPL).

---

<div align="center">

**Refinr** — AGPL-3.0-only

</div>
