# Platform Management Service (monorepo)

Kotlin/Spring Boot **API** + React (**Vite**) **UI** + **deploy** assets for managing participants, service offerings, entitlements, and consumption — secured with Microsoft Entra ID.

**Integrations** are modeled as **connectors** (Entra directory, Blob Capture backfill, Event Hub live ingest, **datasource loading**). Architecture: [`docs/design/connectors-entra-blob-eventhub.md`](docs/design/connectors-entra-blob-eventhub.md). Why Kotlin/Spring Boot: [`docs/design/technology-considerations.md`](docs/design/technology-considerations.md).

```text
platform-management-service/
├── backend/                 # Spring Boot 4 API (Maven)
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── scripts/             # get-token-mi.sh (+ backend-local get-token-human.sh)
│   └── README.md            # API, Entra, persistence, connectors
├── frontend/                # React + TypeScript + MSAL
│   ├── src/
│   ├── Dockerfile
│   ├── server.mjs           # production Node host (/config.js + /api proxy)
│   └── README.md
├── scripts/                 # Human JWT helpers + catalog seed
│   ├── fixtures/datasource.json
│   └── seed-datasource.py
├── deploy/
│   ├── docker-compose.yml   # local API + UI
│   ├── k8s/                 # AKS manifests (API + optional UI)
│   └── scripts/             # build-and-push-acr, ci-deploy, deploy-aks
├── docs/
│   └── design/              # Architecture / design documents
├── Makefile
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
| Catalog store | **In-memory** (`APP_AZURE_TABLE_ENABLED=false`) | **Azure Table Storage** (`APP_AZURE_TABLE_ENABLED=true`) |
| Entitlement check | In-process concurrent maps (hydrated by **datasource-loading** connector) | Same (each pod loads from Table) |
| Catalog seed | External scripts (not in-app) | External scripts → Azure Table |
| Blob + Event Hubs | Optional; MI via `az login` / mocks | **Managed Identity** — no connection strings |
| Auth | Entra JWT always on | Same |

> **Note:** The in-memory store is for single-process local/dev only. Multi-instance deployments must enable Azure Table so every pod shares the same catalog.

There is **no JDBC / H2 / Flyway** stack. Catalog and consumption rows go through repository interfaces backed by Table or process memory.

## Environment variables

Use these names everywhere (API, UI, compose, AKS, token scripts). Root token and deploy scripts still accept the older alias `APP_API_CLIENT_ID` for the API audience.

| Variable | Used by | Purpose |
|----------|---------|---------|
| `APP_AZURE_TENANT_ID` | API + UI | Entra directory (tenant) GUID |
| `APP_API_CLIENT_ID` | API + token scripts | API app registration — JWT audience (`aud` and `api://…`) |
| `APP_CLIENT_ID` | UI | SPA app registration (public client) |
| `APP_API_SCOPE` | UI | Delegated scope, typically `api://$APP_API_CLIENT_ID/access_as_user` |
| `APP_API_BASE_URL` | UI | Backend origin the UI proxies `/api` to (not the UI listen address) |
| `APP_REQUIRED_SCOPE` | API | Optional extra JWT scope gate; leave empty to authorize on app roles only |
| `APP_CORS_ALLOWED_ORIGINS` | API | Comma-separated browser origins (default `http://localhost:3000`) |
| `APP_AZURE_TABLE_ENABLED` | API | `true` for Azure Table; default `false` (in-memory) |
| `APP_AZURE_TABLE_ENDPOINT` | API | `https://<account>.table.core.windows.net` |
| `APP_AZURE_TABLE_CONNECTION_STRING` | API / seed | Local Azurite / account key (optional) |
| `APP_AZURE_TABLE_PREFIX` | API / seed | Table name prefix (default `pms`) |
| `APP_UAMI_CLIENT_ID` / `AZURE_CLIENT_ID` | API Azure data plane | Shared credential for Graph / Blob / Event Hubs / Table |

UI settings are **not** baked into the JS bundle (`VITE_*` is not used). Vite dev, `server.mjs`, and the nginx image all inject `APP_*` at process start via `/config.js`.

## Prerequisites

| Component | Need |
|-----------|------|
| Backend | **JDK 21+** (project targets Java 21), Maven 3.9+, `APP_AZURE_TENANT_ID`, `APP_API_CLIENT_ID` |
| Frontend | Node 22+, Entra **SPA** app registration |
| Seed (Table mode) | Python 3 + `scripts/requirements-seed.txt` |
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
populate Azure Table (or the local store) with external scripts:

```bash
# REST API → running backend (in-memory or Table)
eval "$(./scripts/get-token-human.sh --export)"
./scripts/seed-datasource.py --mode api \
  --file scripts/fixtures/datasource.json \
  --token "$TOKEN"

# Or write Azure Table directly (no running API)
export APP_AZURE_TABLE_ENDPOINT=https://<account>.table.core.windows.net
./scripts/seed-datasource.py --mode table --file scripts/fixtures/datasource.json
```

After a table-only seed, refresh the check cache (`POST /api/v1/entitlements/cache/refresh`)
or wait for the hourly datasource-loading tick.

Token helpers (human JWT — from monorepo root; see [scripts/README.md](scripts/README.md)):

```bash
export APP_AZURE_TENANT_ID=...
export APP_API_CLIENT_ID=...
./scripts/get-token-human.sh --print-claims
# or:
eval "$(./scripts/get-token-human.sh --export --print-claims)"
./scripts/curl-api.sh GET /api/v1/auth/me
```

- API: http://localhost:8080/api/v1
- Health: http://localhost:8080/actuator/health
- OpenAPI / Swagger UI: http://localhost:8080/swagger-ui.html (public to open; **Try it out** still needs a JWT)

### 2. UI

```bash
cd frontend
cp .env.example .env
# Edit .env: APP_AZURE_TENANT_ID, APP_CLIENT_ID, APP_API_SCOPE
npm install
npm run dev
```

Open http://localhost:3000 — Vite proxies `/api` → http://localhost:8080.

### 3. Docker Compose (API + UI)

```bash
export APP_AZURE_TENANT_ID=...
export APP_API_CLIENT_ID=...
export APP_CLIENT_ID=<spa-client-id>
export APP_API_SCOPE=api://$APP_API_CLIENT_ID/access_as_user
export APP_API_BASE_URL=http://localhost:8080

docker compose -f deploy/docker-compose.yml up --build
```

- API: http://localhost:8080
- UI:  http://localhost:3000

## Entra setup (summary)

| Registration | Type | Used by |
|--------------|------|---------|
| **API** (resource) | App roles + scope `access_as_user` | Backend JWT validation (`APP_API_CLIENT_ID`) |
| **SPA** (public client) | Redirect URIs `http://localhost:3000` and `http://localhost:3000/auth-redirect.html` | Frontend MSAL (`APP_CLIENT_ID`) |

Human users: assign API app roles (`System.Maintainer`, `System.Reader`, …) to **security groups**, put users in those groups.

Details: [backend/README.md](backend/README.md) · [frontend/README.md](frontend/README.md).

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

Grant the workload identity **Storage Table Data Contributor** on the account (or the tables used by `APP_AZURE_TABLE_PREFIX`).

## Consumption ingest (connectors)

| Path | Role | Status |
|------|------|--------|
| **POST `/api/v1/consumptions`** | Push / direct registration | **Implemented** (`Consumption.Registrator` or Maintainer) |
| **Blob Capture backfill** | Historical load (`startDate` / `endDate`), Maintainer GET | **Implemented** control plane + runner (default off) |
| **Event Hub continuous** | Live events; **start/stop API** for Maintainer | **Implemented** (default off; MI + Blob checkpoints) |
| **Datasource loading** | Rebuild entitlement check cache from store | **Implemented** (default auto-start; no in-app seed) |
| **Entra directory** | Graph cache for `Platform-System-*` groups | **Implemented** (default auto-start) |

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

Optional UI manifests: `deploy/k8s/ui-deployment.yaml`. Pass `APP_AZURE_TENANT_ID`, `APP_CLIENT_ID`, `APP_API_SCOPE`, and `APP_API_BASE_URL` at **container start** — they are not Docker build-args.

Production expects **Azure Table Storage** for the catalog (`APP_AZURE_TABLE_ENABLED=true`) and **Workload Identity** for Graph / Blob / Event Hubs / Table as connectors are enabled.

### CI

- **GitHub Actions** (`.github/workflows/ci.yml`): JDK 21 Maven `verify` in `backend/`; Node 24 `test:coverage` + `build` in `frontend/`.
- **GitLab CI** (`.gitlab-ci.yml`): Maven / tests in `backend/`; Kaniko builds `backend/Dockerfile`; deploy uses `deploy/scripts/ci-deploy.sh` and `deploy/k8s/`. Setup: [`.gitlab/ci/README.md`](.gitlab/ci/README.md).

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
| [frontend/README.md](frontend/README.md) | SPA setup, MSAL, runtime `APP_*` config, tests |
| [scripts/README.md](scripts/README.md) | Human tokens, `curl-api`, catalog seed |
| [docs/design/connectors-entra-blob-eventhub.md](docs/design/connectors-entra-blob-eventhub.md) | Connector architecture (Entra, Blob, Event Hub, Table) |
| [docs/design/technology-considerations.md](docs/design/technology-considerations.md) | Why Kotlin / Spring Boot (vs Quarkus, Ktor, FastAPI) |
| [`.gitlab/ci/README.md`](.gitlab/ci/README.md) | GitLab pipeline variables and deploy |

## License

This project is licensed under the [Apache License 2.0](LICENSE).
