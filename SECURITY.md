# Security policy

## Supported versions

| Version | Supported |
|---|---|
| 1.0.x | ✅ |
| < 1.0 | ❌ |

Security fixes are released on the latest minor version.

## Reporting a vulnerability

**Please do not open a public issue, discussion or pull request for a vulnerability.**

Report it privately through GitHub: **[Report a vulnerability](https://github.com/chams-bensalem/refinr/security/advisories/new)**
(tab *Security* → *Report a vulnerability*). Only the maintainer can read the report.

Please include:

- the affected version or commit, and the deployment mode (Docker Compose, plain JAR, frontend dev server);
- the steps to reproduce, or a proof of concept, and the impact you observed;
- any configuration needed (`REFINR_API_KEY`, `REFINR_ALLOWED_HOSTS`, `CORS_ALLOWED_ORIGINS`, LLM provider…) —
  **with dummy values only, never a real token or key**.

## What to expect

- Acknowledgement within **5 business days**, and a first assessment within **14 days**.
- Coordinated disclosure: a fix and a GitHub Security Advisory (with a CVE when relevant) are published together;
  you are credited unless you prefer otherwise. Please give us up to **90 days** before any public disclosure.

## Scope

In scope: the backend, the frontend, the Docker images and `docker-compose.yml` of this repository — for example a
bypass of the Host allowlist, the CORS policy or `X-API-Key`, a leak of a secret (Atlassian token, LLM key) in a
response, a log or the health check, CQL injection, or a prompt injection that defeats the reserved `ai-ready` /
`validation-necessaire` labels beyond what [DECISIONS.md](DECISIONS.md) documents.

Out of scope:

- Exposing the web UI to the Internet without authentication in front of it: the API key is added by the frontend
  proxy and does not authenticate users. This is a documented limitation of v1.0 (README, *Security*).
- Vulnerabilities in Confluence, Jira or an LLM provider themselves — report them to that vendor.
- Model output quality (a wrong verdict without a security impact) — open a regular issue.
