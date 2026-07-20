# Platform Management Service (monorepo)

Kotlin/Spring Boot **API** + React (**Vite**) **UI** + **deploy** assets for managing participants, service offerings, entitlements, and consumption — secured with Microsoft Entra ID.

```text
platform-management-service/
├── backend/                 # Spring Boot 4 API (Maven)
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── scripts/             # get-token-human.sh, get-token-mi.sh
│   └── README.md            # API & Entra details
├── frontend/                # React + TypeScript + MSAL
│   ├── src/
│   ├── Dockerfile
│   └── .env.example
├── deploy/
│   ├── docker-compose.yml   # local API + UI
│   ├── k8s/                 # AKS manifests (API + optional UI)
│   └── scripts/             # build-and-push-acr, ci-deploy, deploy-aks
├── .gitlab-ci.yml
└── .github/workflows/
```

## Prerequisites

| Component | Need |
|-----------|------|
| Backend | JDK 17+, Maven 3.9+, `APP_AZURE_TENANT_ID`, `APP_AZURE_API_CLIENT_ID` |
| Frontend | Node 22+, Entra **SPA** app registration |
| Deploy | Docker (compose), optional Azure CLI / kubectl |

## Quick start (local, two terminals)

### 1. API

```bash
cd backend
export APP_AZURE_TENANT_ID=<tenant-guid>
export APP_AZURE_API_CLIENT_ID=<api-app-client-id>
# optional: export APP_CORS_ALLOWED_ORIGINS=http://localhost:3000
mvn spring-boot:run
```

Token helpers (from `backend/`):

```bash
export APP_AZURE_TENANT_ID=...
export APP_AZURE_API_CLIENT_ID=...
./scripts/get-token-human.sh --print-claims
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
export APP_AZURE_API_CLIENT_ID=...
export VITE_AZURE_CLIENT_ID=<spa-client-id>
export VITE_AZURE_API_SCOPE=api://$APP_AZURE_API_CLIENT_ID/access_as_user

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

## Deploy

### AKS (API)

```bash
export APP_AZURE_TENANT_ID=...
export APP_AZURE_API_CLIENT_ID=...
./deploy/scripts/build-and-push-acr.sh <acr-name> 1.0.0
./deploy/scripts/deploy-aks.sh <acr>.azurecr.io 1.0.0
```

Optional UI manifests: `deploy/k8s/ui-deployment.yaml` (build UI image with `VITE_*` build-args).

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

## License

This project is licensed under the [Apache License 2.0](LICENSE).

