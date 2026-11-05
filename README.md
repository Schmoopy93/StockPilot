# StockPilot

StockPilot is a full-stack inventory and replenishment starter for small and
medium-sized businesses. It focuses on answering what to reorder, when, and
why—not just displaying stock counts.

## Architecture

- `backend/`: Java 21, Spring Boot REST API, Spring Data JPA, Flyway migrations
- `frontend/`: Next.js App Router, TypeScript, responsive dashboard
- `compose.yaml`: PostgreSQL, API, and web app for local development

The backend owns inventory calculations and persists stock as an append-only
movement ledger. The dashboard reads from the API; it does not duplicate
business rules.

## First vertical slice

- Create products with a unique SKU, minimum stock, lead time, and unit cost.
- Record inbound, outbound, and signed adjustment movements.
- Reject outbound movements and adjustments that would make stock negative;
  concurrent movement writes lock the product row.
- Review current balances, recent movements, and replenishment suggestions.
- Suggestions include demand, lead time, safety buffer, confidence indicator,
  and estimated purchase cost.

The initial REST endpoints are `GET/POST /api/products`,
`POST /api/stock-movements`, `GET /api/replenishment-recommendations`, and
`GET /api/dashboard`. Validation and calculation logic stays in the backend.
Every inventory query is scoped to the server-selected organization. The
development-only `demo` profile selects the seeded demo organization; production
has no organization provider configured and therefore fails closed until an
authenticated organization context is implemented.
Flyway migration `V2` creates the organization, user, and membership tables,
adds tenant ownership to inventory records, and migrates the existing sample
inventory into the dedicated demo organization.

## Run locally

1. Copy `.env.example` to `.env` and replace the development password.
2. Run `docker compose up --build`.
3. Open `http://localhost:3000`. The API is available at
   `http://localhost:8081/api`.

The frontend can also be run separately with Node.js 20.9 or newer:

```powershell
cd frontend
npm install
npm run dev
```

In that case run the API with `SPRING_PROFILES_ACTIVE=demo` and set the API URL
in `frontend/.env.local` to `http://localhost:8081/api` when using Compose, or
`http://localhost:8080/api` when running the API directly.

## Replenishment calculation (v1)

For each active product, outbound stock movements over the last 30 days are
used to calculate average daily demand. The target stock covers supplier lead
time plus a configurable safety buffer. The suggestion is the positive
difference between target stock and on-hand stock, never less than the
product's minimum-stock threshold. Products without sales history fall back
to the minimum-stock threshold and are marked as low-confidence.

This initial calculation is intentionally deterministic. Open purchase orders,
seasonality, reservations, supplier pack sizes, and multiple warehouses are
not yet included and must be considered before using suggestions to place
real orders.

## Commercialization status

This repository is an initial development foundation, **not production-ready
commercial software**. The demo uses sample data and has no authentication,
authorization, or production secrets management. Tenant scoping is in place,
but the `demo` profile intentionally provides only one fixed demo organization;
it is not a substitute for user authentication. Compose binds application ports
to localhost. Before external deployment, implement and test user authentication,
organization membership/roles, audit controls, backups, monitoring,
privacy/retention policies, and production-grade configuration. Do not enable
the `demo` profile in a public deployment.

Do not use the seeded sample inventory as real business data.
