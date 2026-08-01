# Scripts — human JWT helpers

Shell utilities to obtain and use a **delegated Microsoft Entra access token** for a human user against the Platform Management API.

| Script | Purpose |
|--------|---------|
| [`get-token-human.sh`](get-token-human.sh) | Get a human (delegated) access token |
| [`print-jwt-claims.sh`](print-jwt-claims.sh) | Decode JWT payload (claims) |
| [`with-human-token.sh`](with-human-token.sh) | Fetch token, then run a command with `TOKEN` set |
| [`curl-api.sh`](curl-api.sh) | Convenience curl against the local API with Bearer token |
| [`lib/token-common.sh`](lib/token-common.sh) | Shared helpers |

> Technical / Managed Identity tokens remain under [`backend/scripts/get-token-mi.sh`](../backend/scripts/get-token-mi.sh).

## Prerequisites

| Requirement | Notes |
|-------------|--------|
| **Azure CLI** | Default method (`az-cli`) |
| **curl + jq** | Required for `--method device-code` |
| Entra **API** app registration | `APP_AZURE_API_CLIENT_ID` |
| Entra **tenant** | `APP_AZURE_TENANT_ID` |
| Human user in a **security group** assigned an API **app role** | e.g. `System.Maintainer` |
| Public/SPA client (device-code only) | `AZURE_HUMAN_CLIENT_ID` with delegated scope `access_as_user` |

```bash
export APP_AZURE_TENANT_ID=<tenant-guid>
export APP_AZURE_API_CLIENT_ID=<api-app-client-id>
# optional override:
# export API_SCOPE=api://${APP_AZURE_API_CLIENT_ID}/access_as_user
```

## Quick start

```bash
# From monorepo root
chmod +x scripts/*.sh scripts/lib/*.sh   # once

# Get raw token (stdout)
TOKEN=$(./scripts/get-token-human.sh)

# Export into current shell + inspect roles
eval "$(./scripts/get-token-human.sh --export --print-claims)"

# Probe the API
curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN" | jq
# or:
./scripts/curl-api.sh GET /api/v1/auth/me
./scripts/curl-api.sh GET /api/v1/participants
```

## Methods

### `az-cli` (default)

Uses `az login` (interactive if needed) and `az account get-access-token` for scope  
`api://{APP_AZURE_API_CLIENT_ID}/access_as_user`.

```bash
./scripts/get-token-human.sh --method az-cli --print-claims
```

### `device-code`

OAuth2 device code flow when this machine has no browser (or you prefer not to use Azure CLI).

```bash
export AZURE_HUMAN_CLIENT_ID=<public-or-spa-client-id>
./scripts/get-token-human.sh --method device-code --print-claims
```

## Flags (get-token-human.sh)

| Flag | Description |
|------|-------------|
| `--method az-cli\|device-code` | Token acquisition method (default: `az-cli`) |
| `--print-claims` | Pretty-print JWT payload to **stderr** |
| `--export` | Print `export TOKEN=...` for `eval` |
| `-h`, `--help` | Help |

## Helpers

```bash
# Decode any JWT
./scripts/print-jwt-claims.sh "$TOKEN"

# Run a command with TOKEN already set
./scripts/with-human-token.sh --print-claims -- \
  curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN" | jq

# curl wrapper (auto-fetches TOKEN if missing)
API_BASE=http://localhost:8080 ./scripts/curl-api.sh GET /api/v1/service-offerings
./scripts/curl-api.sh POST /api/v1/participants -d '{"id":"demo","name":"Demo","status":"ACTIVE"}'
```

## Notes

- Tokens are **short-lived**; re-run the script when you get `401`.
- Authorization depends on app **roles** (and optional group mapping), not only on a valid signature.
- Equivalent script also exists at `backend/scripts/get-token-human.sh` for backend-only workflows; prefer **`./scripts/`** from the monorepo root.
