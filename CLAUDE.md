# Refinr

Outil open source (AGPL-3.0-only) : lit une spec Confluence, la fait analyser par un LLM,
crée Epic → Story → Subtask dans Jira avec un verdict `ai-ready` / `validation-necessaire`.

## Stack
- Imposé : **Java 25** et **Spring Boot** (dernière version stable compatible Java 25).
- Tout le reste (librairies, outils de test, build, frontend, CI…) : pour chaque choix structurant,
  le justifier en une ligne dans `DECISIONS.md`.

## Commandes
- Tests backend : `cd backend && mvn test` (préférer un test ciblé pendant l'itération)
- Build frontend : `cd frontend && npm run build`
- Scan secrets : `gitleaks detect --source . --no-banner` (staged : `gitleaks protect --staged`)
- En-têtes de licence : `scripts/check-spdx.sh` (staged : `--staged`) — tout fichier source commence par `SPDX-FileCopyrightText: 2026 Chams Bensalem` + `SPDX-License-Identifier: AGPL-3.0-only`
- Docs : `README.md` (anglais) et `README.fr.md` à garder synchronisés (tableau des propriétés vérifié par `ConfigurationDefaultsTest`)
- Hooks git versionnés (`.githooks/` : pre-commit gitleaks + en-têtes SPDX, commit-msg anti-attribution IA) : `git config core.hooksPath .githooks` une fois par clone
- Config locale : `cp .env.example .env` (secrets) et `cp backend/src/main/resources/application-example.properties backend/src/main/resources/application.properties` (le backend importe `../.env`)
- Stack Docker : `docker compose up -d --build` puis `curl localhost:8080/api/v1/health` (profil `--profile ollama` optionnel)
- JDK : Maven sélectionne un JDK 25 par Toolchains (`select-jdk-toolchain`), quel que soit le JDK qui lance `mvn`
- CI : `.github/workflows/ci.yml` (tests, lint/build, en-têtes SPDX, gitleaks, images multi-arch, smoke compose) ; Dependabot : `.github/dependabot.yml`
- Mettre cette section à jour en cas de changement d'outillage.

## Architecture (hexagonale)
- Package racine : `fr.heliocraft.refinr`, groupId Maven `fr.heliocraft` · nom produit : **Refinr** · nom technique (artefacts, images Docker, services compose) : `refinr`
- `domain` et `application` : aucune dépendance Spring / WebClient / Jackson
- Ports sortants : `DocumentSourcePort`, `IssueTrackerPort`, `BacklogGeneratorPort`, `ReadinessAssessorPort`
- `IssueTrackerPort` reste agnostique (pas d'ADF, `customfield_*`, ni clé Jira) : deux adaptateurs, `adapters/out/jira` et
  `adapters/out/openproject` (depuis la v1.1), un seul actif par instance (`tracker.provider`)

## Règles
- IMPORTANT : aucun secret dans le code, les tests, les exemples, les logs ou sur GitHub. Les secrets vivent uniquement
  dans `.env` à la racine (local, jamais commité) ; `.env.example` ne contient que des valeurs factices.
- Git : une branche par lot de travail, intégration sur `main` uniquement par PR.
  Jamais de push direct sur `main`, jamais de force-push, jamais de changement de visibilité du repo.
- IMPORTANT : aucune attribution IA dans les commits, PR, tags ou releases : pas de `Co-Authored-By: Claude`,
  pas de « Generated with Claude Code », pas de `Claude-Session:`. Messages de commit en Conventional Commits.
- Aucune nouvelle dépendance lourde sans accord du mainteneur.
- Pas de changement de comportement fonctionnel ni de contrat REST sans le signaler.
- Montrer la sortie réelle des tests/builds (pas juste « c'est vert »).
- Si une information manque, demander plutôt que deviner.
