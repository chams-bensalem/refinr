# Contributing to Refinr

Thanks for your interest! Bug reports, provider feedback, documentation fixes and pull requests are all welcome.
By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Before you start

- **Security issue?** Do not open a public issue: follow [SECURITY.md](SECURITY.md).
- **Bug or idea?** Search the existing issues first, then open one with the steps to reproduce, the expected and
  actual behavior, your LLM provider and model, and the `correlationId` of the failing call (logs) — **never** paste
  a token, an API key, or the content of a confidential Confluence page.
- **Tried an LLM provider marked "not tested against the real API"** in the README? An issue telling us whether
  analysis and verdict worked (provider, model, any configuration you needed) is very valuable.
- **Larger change** (new provider, new issue tracker, API change)? Open an issue to discuss it before coding.

## Contributor License Agreement

Your first pull request requires signing the [CLA](CLA.md): the CLA Assistant bot comments on the pull request with
a link, and the `license/cla` check must be green before a merge. You keep the copyright of your work; the CLA
lets the maintainer keep distributing it, including under other license terms (section 3 of the CLA).

## Development setup

Requirements: JDK 25, Maven 3.9+, Node.js 22.12+ (reference version in `.nvmrc`), [gitleaks](https://github.com/gitleaks/gitleaks),
and optionally Docker and Ollama. See the [README](README.md#-requirements).

```bash
git clone https://github.com/<your-fork>/refinr.git && cd refinr
git config core.hooksPath .githooks       # pre-commit (gitleaks, SPDX headers) and commit-msg hooks
cp .env.example .env                      # local secrets, git-ignored
cp backend/src/main/resources/application-example.properties backend/src/main/resources/application.properties
```

| Task | Command |
|---|---|
| Backend tests | `cd backend && mvn test` (run a single test while iterating: `mvn test -Dtest=MarkdownAdfTest`) |
| Frontend lint + build | `cd frontend && npm ci && npm run lint && npm run build` |
| License headers | `scripts/check-spdx.sh` |
| Secret scan | `gitleaks detect --source . --no-banner` |
| Full stack | `docker compose up -d --build`, then `curl localhost:8080/api/v1/health` |

The test suite needs no network access or token: every external HTTP call is simulated with MockWebServer.

## Rules for a pull request

1. **One topic per pull request**, branched from `main`. CI must be green: backend tests, frontend lint + build,
   SPDX headers, gitleaks, image builds and the Compose smoke test.
2. **Tests**: every bug fix or behavior change comes with a test. Keep the hexagonal architecture rules
   (`HexagonalArchitectureTest`): `domain` and `application` stay plain Java (no Spring, WebClient or Jackson), and
   `IssueTrackerPort` stays tracker-agnostic (no ADF, `customfield_*` or Jira keys in the core).
3. **License header**: every new source file starts with
   ```
   // SPDX-FileCopyrightText: 2026 Chams Bensalem
   // SPDX-License-Identifier: AGPL-3.0-only
   ```
   in the file's comment syntax (`#`, `/* */`, `<!-- -->`), after a shebang, a Dockerfile `# syntax=` directive,
   `<?xml …?>` or `<!doctype html>`. `scripts/check-spdx.sh` and CI reject a file without it.
4. **No secrets, ever** — not in code, tests, examples, logs or screenshots. Use dummy values such as
   `your-site.atlassian.net`, `PROJ` and `you@example.com`. Real values live only in your local `.env`.
5. **REST contract**: a change to a request, a response or a status code must be called out in the pull request and
   recorded in [CHANGELOG.md](CHANGELOG.md) (`Unreleased`).
6. **Dependencies**: discuss any new runtime dependency in an issue first; structural technical choices are recorded
   in [DECISIONS.md](DECISIONS.md).
7. **Documentation**: keep `README.md` and `README.fr.md` in sync. The property table is checked by
   `ConfigurationDefaultsTest`: a default must be the same in the code, `application-example.properties`,
   `.env.example` and both READMEs.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/): `feat(backend): …`, `fix(frontend): …`, `docs: …`,
`ci: …`, `build: …`, `test: …`, `refactor: …`, `chore: …`; `!` after the scope for a breaking change.

Commit messages must not carry AI tool attribution trailers (`Co-Authored-By: Claude`, `Generated with Claude Code`,
`Claude-Session:`): the `commit-msg` hook and the `commit-messages` CI job reject them.
