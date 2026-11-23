<!-- SPDX-FileCopyrightText: 2026 Chams Bensalem -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Contexte « boutique » (mesure B : order-export, customer-data-export)

Seul le bloc ci-dessous est envoyé comme contexte projet.

```
Project context: online shop "Maison Verte" (customer accounts, orders, support back office). Stack: Java / Spring Boot backend, React frontend, PostgreSQL.
- Glossary: a paid order is an order whose payment is captured and which is not fully refunded. A cancelled order is never paid.
- File exports: every CSV export of the application is UTF-8 without BOM, semicolon-delimited, with a header row; dates as YYYY-MM-DD, amounts with a dot and two decimals. The date in an export file name is the export date (Europe/Paris).
- Returns module (RMA): a customer requests a return within 30 days of delivery; support approves or rejects it; an approved return triggers a refund.
```
