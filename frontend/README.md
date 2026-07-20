# Platform Management Service — UI

React + TypeScript + Vite SPA using **MSAL** (Microsoft Entra ID).

## Setup

```bash
cp .env.example .env
# Set SPA client id, tenant, API scope (api://<API_CLIENT_ID>/access_as_user)
npm install
npm run dev
```

Dev server: http://localhost:3000  
Proxies `/api` and `/actuator` to `http://localhost:8080`.

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

## Production image

```bash
docker build -t platform-management-service-ui:local \
  --build-arg APP_AZURE_TENANT_ID=... \
  --build-arg APP_AZURE_CLIENT_ID=... \
  --build-arg APP_AZURE_API_CLIENT_ID=... \
  --build-arg APP_AZURE_API_SCOPE=api://.../access_as_user \
  --build-arg APP_API_BASE_URL=https://api.example.com \
  .
```

Or use `deploy/docker-compose.yml` from the repo root.
