# Platform Management Service — UI

React + TypeScript + Vite SPA using **MSAL** (Microsoft Entra ID).

## Setup

```bash
cp .env.example .env
# Set SPA client id, tenant, API scope (api://<API_CLIENT_ID>/access_as_user)
npm install
npm run dev
```

Dev server: http://localhost:5173  
Proxies `/api` and `/actuator` to `http://localhost:8080`.

## Tests

Vitest + React Testing Library (API and MSAL mocked):

```bash
npm test              # single run
npm run test:watch    # watch mode
npm run test:coverage # coverage report
```

Coverage includes list/detail/form pages for all domain modules plus shared UI helpers.

## Entra SPA registration

1. App registration → SPA platform → redirect `http://localhost:5173`
2. API permissions → your API → delegated `access_as_user` → admin consent
3. Users get **app roles** via security groups on the **API** enterprise app (not this SPA)

## Production image

```bash
docker build -t platform-management-service-ui:local \
  --build-arg VITE_AZURE_TENANT_ID=... \
  --build-arg VITE_AZURE_CLIENT_ID=... \
  --build-arg VITE_AZURE_API_CLIENT_ID=... \
  --build-arg VITE_AZURE_API_SCOPE=api://.../access_as_user \
  --build-arg VITE_API_BASE_URL=https://api.example.com \
  .
```

Or use `deploy/docker-compose.yml` from the repo root.
