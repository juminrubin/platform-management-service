# Platform Management Service (monorepo)

Kotlin/Spring Boot **API** + React (**Vite**) **UI** + **deploy** assets for managing participants, service offerings, entitlements, and consumption — secured with Microsoft Entra ID.

**Integrations** are modeled as **connectors** (Entra directory, Blob Capture backfill, Event Hub live ingest, **datasource loading**). Full architecture: [`docs/design/connectors-entra-blob-eventhub.md`](docs/design/connectors-entra-blob-eventhub.md).

```text
platform-management-service/
├── backend/                 # Spring Boot 4 API (Maven)
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── scripts/             # get-token-mi.sh (+ legacy get-token-human.sh)
│   └── README.md            # API, Entra, persistence, connectors
├── frontend/                # React + TypeScript + MSAL
│   ├── src/
│   ├── Dockerfile
│   └── .env.example
├── scripts/                 # Human JWT helpers (get-token-human, curl-api, …)
├── deploy/
│   ├── docker-compose.yml   # local API + UI
│   ├── k8s/                 # AKS manifests (API + optional UI)
│   └── scripts/             # build-and-push-acr, ci-deploy, deploy-aks
├── docs/
│   └── design/              # Architecture / design documents
├── .gitlab-ci.yml
└── .github/workflows/
```

## Architecture (high level)

```text
                    ┌──────────────────────────────────────────┐
  Humans / SPA ──►  │  Platform Management Service (API)       │
  MI / SP ────────► │  Entra JWT resource server               │
                    │                                          │
                    │  Domain CRUD → durable catalog store     │
                    │  Entitlement check → in-memory cache     │
                    │                                          │
                    │  Connectors (feature-flagged):           │
                    │   • Datasource loading (cache only)      │
                    │   • Entra directory (Graph cache)        │
                    │   • Blob Capture Avro (backfill)         │
                    │   • Event Hub (continuous ingest)        │
                    └──────────────────────────────────────────┘
                         │                │              │
            Managed Id   │                │              │
                         ▼                ▼              ▼
              Azure Table Storage   Azure Blob    Azure Event Hubs
              (catalog / durable)   (Capture)     (live + Capture)
```

| Concern | Local / CI | Production (multi-node) |
|---------|------------|-------------------------|
| Catalog store | **In-memory** (`app.azure-table.enabled=false`) | **Azure Table Storage** (`APP_AZURE_TABLE_ENABLED=true`) |
| Entitlement check | In-process concurrent maps (hydrated by **datasource-loading** connector) | Same (each pod loads from Table) |
| Catalog seed | External scripts (not in-app) | External scripts → Azure Table |
| Blob + Event Hubs | Optional; MI via `az login` / mocks | **Managed Identity** — no connection strings |
| Auth | Entra JWT always on | Same |

> **Note:** The in-memory store is for single-process local/dev only. Multi-instance deployments must enable Azure Table so every pod shares the same catalog.

## Prerequisites

| Component | Need |
|-----------|------|
| Backend | **JDK 21+** (project targets Java 21; JDK 25 OK), Maven 3.9+, `APP_AZURE_TENANT_ID`, `APP_API_CLIENT_ID` |
| Frontend | Node 22+, Entra **SPA** app registration |
| Deploy | Docker (compose), optional Azure CLI / kubectl |
| Prod catalog | Azure Storage Account with **Table** service + MI / connection string |

## Quick start (local, two terminals)

### 1. API

```bash
cd backend
export APP_AZURE_TENANT_ID=<tenant-guid>
export APP_API_CLIENT_ID=<api-app-client-id>
# optional: export APP_CORS_ALLOWED_ORIGINS=http://localhost:3000
# Catalog stays in-process memory by default (no Azure Table required)
mvn spring-boot:run
```

On start, the **datasource-loading** connector (auto-start, default on) rebuilds the
entitlement-check cache from the durable store. **Catalog seed is not performed by the app** —
populate Azure Table (or the local store) with external scripts, e.g.
`./scripts/seed-datasource.py` (sample JSON: `scripts/fixtures/datasource.json`).

Token helpers (human JWT — from monorepo root; see [scripts/README.md](scripts/README.md)):

```bash
export APP_AZURE_TENANT_ID=...
export APP_API_CLIENT_ID=...
./scripts/get-token-human.sh --print-claims
# or:
eval "$(./scripts/get-token-human.sh --export --print-claims)"
./scripts/curl-api.sh GET /api/v1/auth/me
```

### 2. UI

```bash
cd frontend
cp .env.example .env
# Edit .env: SPA client id, tenant, API scope
npm install
npm run dev
```

Open http://localhost:3000 — Vite proxies `/api` → http://localhost:8080.

### 3. Docker Compose (API + UI)

```bash
export APP_AZURE_TENANT_ID=...
export APP_API_CLIENT_ID=...
export VITE_AZURE_CLIENT_ID=<spa-client-id>
export VITE_AZURE_API_SCOPE=api://$APP_API_CLIENT_ID/access_as_user

docker compose -f deploy/docker-compose.yml up --build
```

- API: http://localhost:8080  
- UI:  http://localhost:3000  

## Entra setup (summary)

| Registration | Type | Used by |
|--------------|------|---------|
| **API** (resource) | App roles + scope `access_as_user` | Backend JWT validation |
| **SPA** (public client) | Redirect URI `http://localhost:3000` (dev) | Frontend MSAL |

Human users: assign API app roles (`System.Maintainer`, `System.Reader`, …) to **security groups**, put users in those groups.

Details: [backend/README.md](backend/README.md).

## Catalog persistence & entitlement check

```text
External scripts ──seed──► Azure Table (or in-memory)
                                │
           datasource-loading   │  hourly (cache rebuild only)
                                ▼
                    EntitlementCheckCache (Concurrent maps)
                                │
                                ▼
              GET /api/v1/entitlements/check  (µs-scale when warm)
```

| Piece | Role |
|-------|------|
| Durable store | Services, participants, callers, entitlements, consumptions |
| External scripts | Populate catalog (sample: `scripts/fixtures/datasource.json`) |
| **datasource-loading** connector | Rebuild check cache on start / schedule (**no** JSON seed) |
| Check cache | ACTIVE entitlements valid as of “today” UTC only (keeps memory small) |

Operator APIs (System.Maintainer):

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/connectors/datasource-loading` | Connector status |
| `POST` | `/api/v1/connectors/datasource-loading/start\|stop` | Arm / disarm schedule |
| `GET` | `/api/v1/entitlements/cache` | Cache counts / last refresh |
| `POST` | `/api/v1/entitlements/cache/refresh` | Force cache rebuild |

Enable Azure Table in production:

```bash
export APP_AZURE_TABLE_ENABLED=true
export APP_AZURE_TABLE_ENDPOINT=https://<account>.table.core.windows.net
# Auth: same app.azure.credential (UAMI / SP / SAMI) as Graph/Blob/EH
# or local: export APP_AZURE_TABLE_CONNECTION_STRING=...
```

## Consumption ingest (connectors)

| Path | Role | Status |
|------|------|--------|
| **POST `/api/v1/consumptions`** | Push / direct registration | **Implemented** (`Consumption.Registrator` or Maintainer) |
| **Blob Capture backfill** | Historical load (`fromDate` / `untilDate`), async jobs | **Implemented** control plane + runner (default off) |
| **Event Hub continuous** | Live events; **start/stop API** for Maintainer | **Implemented** (default off; MI + Blob checkpoints) |
| **Datasource loading** | Rebuild entitlement check cache from store | **Implemented** (default auto-start; no in-app seed) |

Design: [docs/design/connectors-entra-blob-eventhub.md](docs/design/connectors-entra-blob-eventhub.md).  
Backend notes: [backend/README.md § Connectors](backend/README.md#connectors-integrations).

## Deploy

### AKS (API)

```bash
export APP_AZURE_TENANT_ID=...
export APP_API_CLIENT_ID=...
./deploy/scripts/build-and-push-acr.sh <acr-name> 1.0.0
./deploy/scripts/deploy-aks.sh <acr>.azurecr.io 1.0.0
```

Optional UI manifests: `deploy/k8s/ui-deployment.yaml` (build UI image with `VITE_*` build-args).

Production expects **Azure Table Storage** for the catalog (`APP_AZURE_TABLE_ENABLED=true`) and **Workload Identity** for Graph / Blob / Event Hubs / Table as connectors are enabled.

### GitLab CI

- Maven / tests run in **`backend/`**
- Kaniko builds **`backend/Dockerfile`**
- Deploy uses **`deploy/scripts/ci-deploy.sh`** and **`deploy/k8s/`**

## Why monorepo (not one Maven module for React)

- Independent toolchains (Maven vs npm)
- Separate images / release cadence
- Clear API ↔ SPA boundary (Bearer tokens, CORS)
- One git history, shared Entra docs and compose

Backend stays a pure Spring project; frontend stays a pure Vite app; deploy owns cluster and compose wiring.

## Documentation

| Doc | Description |
|-----|-------------|
| [backend/README.md](backend/README.md) | API endpoints, Entra, domain, persistence, connectors |
| [frontend/README.md](frontend/README.md) | SPA setup (if present) |
| [docs/design/connectors-entra-blob-eventhub.md](docs/design/connectors-entra-blob-eventhub.md) | Connector architecture (Entra, Blob, Event Hub) |

## License

This project is licensed under the [Apache License 2.0](LICENSE).
