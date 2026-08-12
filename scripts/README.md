# Scripts — tokens, API helpers, and catalog seed

Shell utilities to obtain and use a **delegated Microsoft Entra access token** for a human user against the Platform Management API, plus a **Python seed script** for catalog data.

| Script | Purpose |
|--------|---------|
| [`get-token-human.sh`](get-token-human.sh) | Get a human (delegated) access token |
| [`print-jwt-claims.sh`](print-jwt-claims.sh) | Decode JWT payload (claims) |
| [`with-human-token.sh`](with-human-token.sh) | Fetch token, then run a command with `TOKEN` set |
| [`curl-api.sh`](curl-api.sh) | Convenience curl against the local API with Bearer token |
| [`seed-datasource.py`](seed-datasource.py) | Seed catalog from JSON → **Azure Table** or **REST API** |
| [`fixtures/datasource.json`](fixtures/datasource.json) | Sample catalog document |
| [`requirements-seed.txt`](requirements-seed.txt) | Python deps for table-mode seed |
| [`lib/token-common.sh`](lib/token-common.sh) | Shared helpers |

> Technical / Managed Identity tokens remain under [`backend/scripts/get-token-mi.sh`](../backend/scripts/get-token-mi.sh).

The backend **does not** load `datasource.json` on startup. Use `seed-datasource.py` (or your own tooling) to populate the store, then refresh the check cache if needed.

---

## Catalog seed (`seed-datasource.py`)

Writes **services → participants → callers → entitlements** from a JSON file matching `fixtures/datasource.json`.

### Install (Azure Table mode)

```bash
python3 -m venv .venv-seed
source .venv-seed/bin/activate
pip install -r scripts/requirements-seed.txt
```

### Azure Table (production multi-node)

Table names and property names match the backend (`app.azure-table.table-prefix`, default `pms`).

```bash
# Connection string (Azurite / account key)
export AZURE_STORAGE_CONNECTION_STRING='DefaultEndpointsProtocol=...'
./scripts/seed-datasource.py --mode table --file scripts/fixtures/datasource.json

# Or account endpoint + DefaultAzureCredential (az login / MI env)
export APP_AZURE_TABLE_ENDPOINT=https://<account>.table.core.windows.net
./scripts/seed-datasource.py --mode table --file scripts/fixtures/datasource.json

# Optional: match app.azure-table.table-prefix
./scripts/seed-datasource.py --mode table --table-prefix pms --file scripts/fixtures/datasource.json

# Dry-run
./scripts/seed-datasource.py --mode table --file scripts/fixtures/datasource.json --dry-run
```

Idempotent by default (`--skip-existing`): existing partition/row keys are left unchanged.  
Use `--no-skip-existing` to upsert (merge) entities.

After table seed, either wait for the **datasource-loading** connector hourly tick or:

```bash
eval "$(./scripts/get-token-human.sh --export)"
./scripts/curl-api.sh POST /api/v1/entitlements/cache/refresh
```

### REST API (local backend / in-memory store)

Requires a running API and a **System.Maintainer** token. Also triggers cache refresh at the end.

```bash
eval "$(./scripts/get-token-human.sh --export)"
./scripts/seed-datasource.py --mode api \
  --file scripts/fixtures/datasource.json \
  --base-url http://localhost:8080 \
  --token "$TOKEN"
```

| Flag | Description |
|------|-------------|
| `--file`, `-f` | JSON path (default: `scripts/fixtures/datasource.json`) |
| `--mode table\|api` | Azure Table or REST API |
| `--skip-existing` / `--no-skip-existing` | Skip existing keys (default: skip) |
| `--dry-run` | Parse and print plan only |
| `--connection-string` | Table mode connection string |
| `--endpoint` | Table mode account URL |
| `--table-prefix` | Same as `APP_AZURE_TABLE_PREFIX` (default `pms`) |
| `--base-url` | API mode base (default `http://localhost:8080`) |
| `--token` | API mode Bearer token (or `TOKEN`) |

---

## Prerequisites

| Requirement | Notes |
|-------------|--------|
| **Azure CLI** | Default method (`az-cli`) |
| **curl + jq** | Required for `--method device-code` |
| Entra **API** app registration | `APP_API_CLIENT_ID` |
| Entra **tenant** | `APP_AZURE_TENANT_ID` |
| Human user in a **security group** assigned an API **app role** | e.g. `System.Maintainer` |
| Public/SPA client (device-code only) | `AZURE_HUMAN_CLIENT_ID` with delegated scope `access_as_user` |

```bash
export APP_AZURE_TENANT_ID=<tenant-guid>
export APP_API_CLIENT_ID=<api-app-client-id>
# optional override:
# export API_SCOPE=api://${APP_API_CLIENT_ID}/access_as_user
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
`api://{APP_API_CLIENT_ID}/access_as_user`.

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
