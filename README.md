# Habesh Logistics MVP

Community-based logistics platform connecting senders in the US with travelers heading
to Ethiopia. Built as a production-quality MVP replacing scattered spreadsheets,
Telegram messages, and phone calls with a single tracking + operations system.

**Stack**

- Backend: Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Flyway, PostgreSQL
- Frontend: React 18, TypeScript, Vite, Tailwind CSS, React Router, TanStack Query
- Auth: JWT (roles: `ADMIN`, `AGENT`, `TRAVELER`, `SENDER`)
- QR codes: ZXing
- Docker Compose for one-command local run

---

## 1. Quick start (Docker)

```bash
cd habesh-logistics
docker compose up --build
```

Services:
- Backend API: http://localhost:8080  (Swagger UI: http://localhost:8080/swagger-ui.html)
- Frontend: http://localhost:3000
- PostgreSQL: localhost:5432 (user/password `habesh`/`habesh`)

Flyway migrations run on startup. A `DataSeeder` creates demo users, trip, luggage
and shipments the first time the DB is empty.

### Demo accounts

| Role      | Email                       | Password      |
|-----------|-----------------------------|---------------|
| Admin     | `admin@habeshlist.com`      | `Admin@123`   |
| Traveler  | `traveler@example.com`      | `Traveler@123`|
| Agent     | `agent@example.com`         | `Agent@123`   |
| Sender 1  | `sender@example.com`        | `Sender@123`  |
| Sender 2  | `sender2@example.com`       | `Sender@123`  |

Seeded data: `TRIP-<year>-0045` (IAD → ADD) with two luggage units `LUG-<year>-0001`
and `LUG-<year>-0002` already carrying `HL-000101` … `HL-000105`.

Agent referral code: **BETI2026** (10% commission).

---

## 2. Local development (without Docker)

### Backend
Requires Java 21 and Maven 3.9+.

```bash
cd backend
# Start a local Postgres (any way you like) and set env vars:
export DB_URL=jdbc:postgresql://localhost:5432/habesh
export DB_USER=habesh
export DB_PASSWORD=habesh
export JWT_SECRET="this-is-a-dev-secret-change-me-at-least-32-bytes"
mvn spring-boot:run
```

### Frontend
Requires Node 20+.

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

Then open http://localhost:5173.

---

## 3. Environment variables

### Backend
| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/habesh` | JDBC URL |
| `DB_USER` | `habesh` |
| `DB_PASSWORD` | `habesh` |
| `JWT_SECRET` | *(dev default)* | **MUST** be ≥32 bytes in prod |
| `JWT_EXPIRATION_MS` | `86400000` | 24h |
| `CORS_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Comma-separated |
| `DEFAULT_RATE_PER_KG` | `10.00` | USD |
| `DEFAULT_PICKUP_FEE` | `15.00` | USD |
| `DEFAULT_COMMISSION_RATE` | `0.10` | 10% |
| `ALEXANDRIA_ADDRESS` | `1234 King St, Alexandria, VA 22314` | |
| `ALEXANDRIA_HOURS` | `Mon-Sat 9AM-7PM` | |

### Frontend
| Variable | Default | Notes |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080` | Backend URL |

---

## 4. REST API overview

All non-public endpoints require `Authorization: Bearer <jwt>`.

**Auth**
- `POST /api/auth/register` – create a sender account (public)
- `POST /api/auth/login` – returns `{ token, expiresInMs, user }`
- `GET  /api/me` – current user

**Shipments**
- `POST /api/shipments` – sender creates
- `GET  /api/shipments` – role-scoped list
- `GET  /api/shipments/{id}` – role-scoped detail
- `GET  /api/shipments/tracking/{trackingNumber}` – **public, limited info**
- `GET  /api/shipments/{id}/tracking` – events
- `PUT  /api/shipments/{id}/status` – admin/agent/traveler

**Trips**
- `POST /api/trips` – admin
- `GET  /api/trips` – role-scoped
- `GET  /api/trips/{id}`
- `PUT  /api/trips/{id}/status`

**Luggage**
- `POST /api/luggage` – admin
- `GET  /api/luggage/{id}`
- `POST /api/luggage/{id}/shipments` – assign
- `DELETE /api/luggage/{id}/shipments/{shipmentId}` – unassign
- `POST /api/luggage/trips/{tripId}/auto-assign` – greedy packer
- `PUT  /api/luggage/{id}/status`

**Pickups**
- `POST /api/pickups` – sender
- `GET  /api/pickups` – admin
- `PUT  /api/pickups/{id}/status` – admin

**Agents / Referrals**
- `POST /api/agents` – admin
- `GET  /api/agents` – admin
- `GET  /api/agents/me/referrals` – agent dashboard stats
- `GET  /api/agents/me/referred-shipments`
- `GET  /api/agents/{id}/referrals`

**QR**
- `GET /api/qr/shipment/{id}` – PNG
- `GET /api/qr/luggage/{id}` – PNG
- `POST /api/scan` – looks up by HL/LUG code and audit-logs

**Admin**
- `GET /api/admin/dashboard`
- `GET /api/admin/users?role=TRAVELER`

**Public**
- `GET /api/public/info` – brand/pricing/Alexandria info

Full interactive docs: **http://localhost:8080/swagger-ui.html**

---

## 5. Business model (critical)

```
Sender → Shipment → Luggage → Trip → Traveler
```

- A **sender** owns a **shipment** (never the luggage).
- A **traveler** owns a **trip**.
- A **trip** contains one or more **luggage** units.
- A **luggage** unit contains multiple **shipments** from different senders.

Capacity is enforced server-side when assigning shipments to a luggage; a shipment
can only belong to **one** luggage at a time.

---

## 6. Security & privacy

- JWT-signed tokens with role claim.
- Method-level authorization via `@PreAuthorize` and runtime checks in controllers.
- Public tracking endpoint (`/api/shipments/tracking/{HL-...}`) returns a limited
  payload — only the status, destination city/country, weight, and timeline. It
  never exposes other senders, luggage numbers, traveler info, or receiver PII.
- Each shipment also has a secure random `trackingToken` for use as an opaque
  share token (not a sequential numeric ID).
- Every status change writes a `tracking_events` row.
- Every QR scan writes a `scan_audit_log` row.

---

## 7. End-to-end demo scenario

The scenario below is pre-seeded for easy demonstration:

1. Sender creates 5 kg shipment via `/send` → `HL-00010X` tracking number issued.
2. Admin receives at Alexandria (status `RECEIVED_AT_ALEXANDRIA`).
3. Admin assigns shipment to `LUG-<year>-0001` (status `ASSIGNED_TO_LUGGAGE`).
4. `LUG-<year>-0001` belongs to `TRIP-<year>-0045`.
5. Traveler Ahmed sees the trip + its two luggage units at `/traveler`.
6. Traveler marks trip **DEPARTED** → every contained shipment moves to
   `DEPARTED_US` with a tracking event.
7. Ethiopia agent (login as `agent@example.com`) sees the full manifest, marks
   luggage **RECEIVED_BY_AGENT**, then each shipment **DELIVERED**.
8. Sender tracking page shows the complete timeline — no information about other
   senders or luggage contents is leaked.

---

## 8. Tests

```bash
cd backend
mvn test
```

Covers:
- Admin can log in and see dashboard.
- Sender list is scoped to the current sender.
- A sender cannot read another sender’s shipment by ID (403).
- Public tracking never leaks sender/recipient/luggage information.
- Luggage capacity is enforced on assignment (either “already assigned” or
  “capacity exceeded” — both return `400`).
- Referral commission math is correct for the seeded shipment
  (5 kg × $10 × 10% = $5).

---

## 9. Project layout

```
habesh-logistics/
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/habeshlist/
│       ├── HabeshLogisticsApplication.java
│       ├── config/        (SecurityConfig, JpaConfig)
│       ├── security/      (JwtService, JwtAuthFilter, AuthPrincipal)
│       ├── entity/        (User, Shipment, Trip, Luggage, …)
│       ├── repository/
│       ├── service/
│       ├── controller/
│       ├── dto/
│       └── exception/
│   └── src/main/resources/
│       ├── application.yml / application-test.yml
│       └── db/migration/V1__init.sql
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── main.tsx, App.tsx
│       ├── api/           (axios client + typed endpoints)
│       ├── auth/          (AuthContext, ProtectedRoute)
│       ├── components/    (Layout, Timeline, StatusBadge)
│       ├── pages/
│       │   ├── Home / Login / Register / Track / SendPackage
│       │   ├── sender/Dashboard
│       │   ├── traveler/Dashboard
│       │   ├── agent/Dashboard
│       │   └── admin/Dashboard + AdminTrip
│       └── types/
├── docker-compose.yml
└── README.md
```

---

## 10. Not in this MVP (future phases)

- AI route optimisation / traveler-sender matching
- Real payment gateway (payment status is tracked, not collected)
- WhatsApp / SMS integration
- Native mobile apps
- International carrier APIs

The current goal is **reliable tracking and operational visibility** — everything
else can come later.
