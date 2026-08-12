# Platform Management Service — UI

React + TypeScript + Vite SPA using **MSAL** (Microsoft Entra ID).

Monorepo overview and shared env names: [../README.md](../README.md).

## Setup

```bash
cp .env.example .env
# Set APP_AZURE_TENANT_ID, APP_CLIENT_ID, APP_API_SCOPE
# APP_API_SCOPE is typically api://<APP_API_CLIENT_ID>/access_as_user
npm install
npm run dev
```

Dev server: http://localhost:3000  
Proxies `/api` and `/actuator` to `http://localhost:8080`.

Signed-in Maintainers can browse domain entities and the **Connectors** pages (`/connectors`). Readers get list/detail only. Catalog seed is not a UI flow — use [`../scripts/seed-datasource.py`](../scripts/seed-datasource.py).

## Tests

Vitest + React Testing Library (API and MSAL mocked):

```bash
npm test              # single run
npm run test:watch    # watch mode
npm run test:coverage # coverage report + industry gate
```

**Coverage gate** (see `vite.config.ts`, same spirit as backend JaCoCo ≥80% lines):

| Metric | Minimum |
|--------|---------|
| Lines / statements / functions | **80%** |
| Branches | **75%** |

Included: API client, auth/capabilities, authorization UI, layout, and list/detail/form pages.  
Excluded: `main.tsx`, `App.tsx` (wiring), pure types, and test helpers.  
CI runs `npm run test:coverage` so PRs fail if coverage drops below the gate.

## Entra SPA registration

1. App registration → **Single-page application** platform → add **both** redirect URIs:
   - `http://localhost:3000` (app origin; full-page redirect / logout)
   - `http://localhost:3000/auth-redirect.html` (popup / silent token completion — **required** for Sign in with Microsoft popup)
2. API permissions → your API → delegated `access_as_user` → admin consent
3. Users get **app roles** via security groups on the **API** enterprise app (not this SPA)

Popup **login and logout** use `public/auth-redirect.html` (not the full React app).  
With **@azure/msal-browser v5**, that page loads `msal-redirect-bridge.min.js` and calls
`broadcastResponseToMainFrame()` when an auth payload is present, then closes the popup.
After logout, Entra also redirects the popup here — never to `/` — so the SPA does not open
inside the dialog. Vite copies the bridge script from `node_modules` into `public/` automatically.

Register both SPA redirect URIs in Entra (login + logout):

- `http://localhost:3000`
- `http://localhost:3000/auth-redirect.html`

## Production build (portable)

`npm run build` emits `dist/` that can be zipped and deployed as-is. Entra / API
settings are **not** baked into the JS bundle. The included `server.mjs` reads
system environment variables at process start, serves them as `/config.js`,
and reverse-proxies `/api` and `/actuator` to `APP_API_BASE_URL`.
The UI listens on `PORT` (default **3000**), never on the API origin.

```bash
npm run build
cd dist && zip -r ../ui.zip .
az webapp deploy --resource-group <rg> --name <app> --src-path ../ui.zip
```

Set these **application settings** on the Web App (Linux Node 20+). Same names as Vite `.env`, `server.mjs`, the nginx image, and `deploy/docker-compose.yml` — nothing is baked in as `VITE_*` at build time.

| Variable | Purpose |
|---|---|
| `APP_AZURE_TENANT_ID` | Entra tenant GUID |
| `APP_CLIENT_ID` | SPA app registration client ID |
| `APP_API_SCOPE` | Delegated API scope (`api://<APP_API_CLIENT_ID>/access_as_user`) |
| `APP_API_BASE_URL` | Backend origin this process proxies `/api` to (not the UI listen address) |
| `PORT` | UI listen port (Azure sets this; locally defaults to **3000**) |

Startup command (if not detected): `node server.mjs`. Azure supplies `PORT`.

Local check of the same artifact:

```bash
# after npm run build, from frontend/
APP_AZURE_TENANT_ID=... APP_CLIENT_ID=... APP_API_SCOPE=... npm start
```

## Production image

Build once; pass `APP_*` at **container start**:

```bash
docker build -t platform-management-service-ui:local .
docker run --rm -p 3000:80 \
  -e APP_AZURE_TENANT_ID=... \
  -e APP_CLIENT_ID=... \
  -e APP_API_SCOPE=api://.../access_as_user \
  -e APP_API_BASE_URL=https://api.example.com \
  platform-management-service-ui:local
```

Or use `deploy/docker-compose.yml` from the repo root.
