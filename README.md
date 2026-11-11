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

The inventory REST endpoints are `GET/POST /api/products`,
`POST /api/stock-movements`, `GET /api/replenishment-recommendations`, and
`GET /api/dashboard`. Movement history is available through paginated
`GET /api/stock-movements` with optional product, type, and date filters.
Validation and calculation logic stays in the backend.
Every inventory query is scoped to the server-selected organization. The
development-only `demo` profile selects the seeded demo organization; the
`auth` profile derives the organization from the signed-in user's membership.
Flyway migration `V2` adds tenant ownership to inventory records and migrates
the sample inventory into the dedicated demo organization. Migrations `V3` and
`V4` add authentication tokens and session revocation support. Migration `V8`
adds the organization-scoped supplier product catalog, and `V9` snapshots the
supplier SKU onto purchase order lines.

## Run locally

1. Copy `.env.example` to `.env` and replace the development password.
2. Run `docker compose up --build`.
3. Open `http://localhost:3000`. The API is available at
   `http://localhost:8081/api`. Local verification/reset emails can be read in
   the Mailpit inbox at `http://localhost:8025`.

The default Compose profile is `demo` for dashboard development. To run the API
with authentication instead, set `SPRING_PROFILES_ACTIVE=auth` in the
environment before starting Compose and restart the services. The frontend
provides `/login`, `/register`, `/forgot-password`, `/verify-email`, and
`/reset-password` screens, plus `/products`, `/purchasing`, `/orders`,
`/suppliers`, `/movements`, and `/settings` workspace routes. Login redirects
accept a same-site `next` path. The API
provides matching authentication endpoints under `/api/auth`, including
`POST /api/auth/resend-verification`; mutating requests include a CSRF token
from `GET /api/auth/csrf`. Verification and reset emails link back to the frontend.
The dashboard requires a verified session in the `auth` profile; the default
`demo` profile remains available for anonymous UI development.
Authentication endpoints also use PostgreSQL-backed per-client rate limits:
10 logins per minute, 5 registrations, verification resends, and password-reset requests per hour,
10 email-verification attempts per 15 minutes, and 5 password-reset
submissions per 15 minutes. Exceeded limits return `429` and a `Retry-After`
header. The API stores only HMACs of client addresses and removes inactive
buckets periodically. Set `APP_AUTH_RATE_LIMIT_SECRET` to the same random,
unguessable secret of at least 32 characters on every API instance; rotate it
deliberately because rotation resets existing rate-limit buckets. Deployments
behind a reverse proxy must set `APP_AUTH_RATE_LIMIT_TRUSTED_PROXIES` to
comma-separated proxy IPs or CIDR ranges. The API then resolves the client
from `X-Forwarded-For` only when the direct peer matches this trusted list;
configure the proxy to append the actual peer and never trust arbitrary
client-supplied forwarding headers.

In local development, verification and password-reset emails are captured by
Mailpit rather than delivered to a real mailbox. Open
`http://localhost:8025` to view them. To deliver mail externally, set
`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`,
`MAIL_SMTP_STARTTLS`, and `APP_FROM_EMAIL` in `.env`, then restart the API with
`docker compose up --build -d api`. Keep provider credentials out of source
control. For Gmail, use an app password rather than the account password.

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

This initial calculation is intentionally deterministic. Quantities in draft,
ordered, and partially received purchase orders reduce the recommended
quantity. Seasonality, reservations, supplier pack sizes, and multiple
warehouses are not yet included in the calculation. Recommendations can be
used to prepare a draft purchase order; they do not automatically place orders
with suppliers.

When a product has a primary supplier configured, its supplier-specific price,
lead time, and minimum order quantity are used for the recommendation and
estimated cost. Otherwise, the product's standard price and lead time are used.

## Suppliers and purchase orders

Suppliers and orders are organization-scoped. Authorized users can save
supplier contacts, create a draft order from selected replenishment
recommendations or from the order form, mark it as ordered, and record partial
or complete deliveries. A receipt creates inbound stock movements atomically
with the order progress update. Supplier management and order creation,
dispatch, and cancellation are limited to owners and administrators; staff can
record deliveries. The latest 100 orders are shown in the workspace.

Owners and administrators can maintain a per-supplier product catalog with
supplier SKU, unit price, minimum order quantity, lead time, and one primary
supplier per product. Supplier-specific prices are prefilled when preparing
orders for that supplier, and minimum quantities are validated by the API.

## Prices and exchange rates

Product, supplier-catalog, and purchase-order prices are stored in RSD. The API
fetches the free ExchangeRate-API Open Access feed on startup and once daily.
It requires no API key and supports EUR/RSD. Its update date appears in the
workspace; EUR display uses the latest saved feed rate. The workspace includes
the attribution link required by the provider.

Migration `V10` records currency conversion state and `V11` switches rate
storage to the new provider. On the first successful fetch, existing EUR unit
prices are converted to RSD once, in one database transaction, and rounded to
two decimal places using the feed's actual update date. Until then, saved prices
continue to display as EUR and price entry, supplier price updates, and new
purchase orders remain disabled. If the feed is unavailable, prices are not
relabeled or converted using an estimated rate. Use the workspace's
`Osveži kurs` action to retry; `GET /api/currency` reports the status.

This is an informational daily reference rate, not the official NBS middle
rate, and the provider does not guarantee its accuracy. Its free terms allow
commercial use but prohibit redistributing the data or offering a separate
programmatic exchange-rate service. Verify that your deployment complies
before commercial use. See
https://www.exchangerate-api.com/terms and
https://www.exchangerate-api.com/docs/free.

## Commercialization status

This repository is an initial development foundation, **not production-ready
commercial software**. The default `demo` profile uses sample data and allows
anonymous inventory access for local UI development. Set
`SPRING_PROFILES_ACTIVE=auth` to enable registration, verified-email session
authentication, tenant-scoped inventory API access, CSRF, and password reset.
Compose binds application and Mailpit ports to localhost. Before external deployment,
configure production email delivery, organization membership management, audit
controls, backups, monitoring, privacy/retention policies, production-grade
secrets and HTTPS. Never use the `demo`
profile in a public deployment.

Do not use the seeded sample inventory as real business data.
