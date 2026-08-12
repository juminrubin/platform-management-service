# Platform Management Service — API (backend)

Kotlin + Spring Boot REST API (**Platform Management Service**) for **Participants**, **Service Offerings**, **Entitlements**, **Caller Registrations**, and **Consumptions** — secured with Microsoft Entra ID.

External systems are integrated as **connectors** (datasource loading, directory, historical Blob Capture, live Event Hub). Architecture: [docs/design/connectors-entra-blob-eventhub.md](../docs/design/connectors-entra-blob-eventhub.md).

| Concern | Choice |
|--------|--------|
| Language | Kotlin **2.3.x** |
| Framework | Spring Boot **4.1.0** |
| Build | Maven |
| Runtime / bytecode | **Java 21** (run with JDK 21+) |
| Catalog store (local/CI) | **In-memory** process store (`app.azure-table.enabled=false`) |
| Catalog store (production) | **Azure Table Storage** (`APP_AZURE_TABLE_ENABLED=true`) |
| Entitlement check path | In-process concurrent maps (hydrated by **datasource-loading** connector) |
| Catalog seed | **External scripts only** (not loaded by the app) |
| Auth | Microsoft Entra ID (Azure AD) JWT resource server |
| Azure data plane (Blob / Event Hubs / Graph / Table) | **SP / UAMI / SAMI** from one `client-id` (`AzureCredentialFactory`) |
| Packaging | Docker multi-stage image |
| Deploy | Kubernetes manifests for Azure Kubernetes Service (AKS) |

---

## Project layout

```
src/main/kotlin/org/jrtech/platformmanagement/
  cache/           # EntitlementCheckCache (concurrent maps for /entitlements/check)
  config/          # Security (Microsoft JWT), CORS, Entra directory, Azure credential factory
  config/azure/    # AzureCredentialFactory (SP / UAMI / SAMI from client-id + secret)
  connectors/      # Datasource loading, Blob Capture, Event Hub, Entra directory
  controller/      # REST endpoints (domain + Entra + connector control plane)
  domain/          # Plain domain models & enums (audit set in services)
  dto/             # Request/response models
  entra/           # Entra directory Graph client + models
  exception/       # API errors (RFC 7807 ProblemDetail)
  persistence/     # Azure Table + in-memory repository implementations
  repository/      # Repository interfaces (store-agnostic)
  security/        # Authz, JWT mapping, group → permission scope table
  service/         # Domain business logic (pure; connectors call into services)
src/main/resources/
  application.yml              # App + Entra JWT + Table / connectors
  application-k8s.yml          # Container / AKS runtime extras
Dockerfile
scripts/                       # Entra token scripts (human / MI)
```

Sample catalog JSON for **external** seed scripts: [`../scripts/fixtures/datasource.json`](../scripts/fixtures/datasource.json).

Monorepo deploy/CI assets live under `../deploy/`, `../.gitlab-ci.yml`, and `../docs/design/`.

---

## Quick start

Requires **JDK 21+**, **Maven 3.9+**, and Entra app settings:

```bash
export APP_AZURE_TENANT_ID=<tenant-guid>
export APP_API_CLIENT_ID=<api-app-client-id>
# optional: export APP_AZURE_REQUIRED_SCOPE=access_as_user
# Catalog: in-memory by default (no Azure Table required for local)

cd backend
mvn spring-boot:run
```

On startup the **datasource-loading** connector (default `auto-start=true`) rebuilds the
entitlement-check cache from the durable store. **It does not seed catalog data** — use
external scripts to populate Azure Table (or the local in-memory store).

Security is **always on** (configured in `application.yml`). Every `/api/**` call needs:

```http
Authorization: Bearer <entra-access-token>
```

Obtain a human token from the monorepo root: `../scripts/get-token-human.sh`  
(see [scripts/README.md](../scripts/README.md)). Technical / MI: `./scripts/get-token-mi.sh`.  
See [Microsoft Entra ID authentication](#microsoft-entra-id-authentication--authorization).

Optional verbose logging profile (security unchanged):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

- API base: `http://localhost:8080/api/v1`
- Auth probe: `http://localhost:8080/api/v1/auth/me`
- Health: `http://localhost:8080/actuator/health` (public)
- **OpenAPI / Swagger UI (public — no token to open):** `http://localhost:8080/swagger-ui.html`  
  OpenAPI JSON: `http://localhost:8080/v3/api-docs`  
  **Calling `/api/**` from Try it out still needs a JWT:** click **Authorize**, paste an Entra
  access token from `scripts/get-token-*.sh` (raw JWT, no `Bearer ` prefix).  
  Disable docs with `APP_SWAGGER_ENABLED=false`.

---

## API endpoints

All resources support standard CRUD under `/api/v1`.  
**All of these require a Microsoft Entra access token** (`Authorization: Bearer …`) and an appropriate app role.

### Participants

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/participants` | List (`?status=ACTIVE`) |
| `GET` | `/api/v1/participants/{id}` | Get by id |
| `POST` | `/api/v1/participants` | Create |
| `PUT` | `/api/v1/participants/{id}` | Update |
| `DELETE` | `/api/v1/participants/{id}` | Delete |

```bash
curl -s http://localhost:8080/api/v1/participants | jq
```

```bash
curl -s -X POST http://localhost:8080/api/v1/participants \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "delta-ltd",
    "name": "Delta Ltd",
    "contact": "ops@delta.example",
    "status": "ACTIVE"
  }' | jq
```

### Service offerings

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/service-offerings` | List (`?activeOnly=true&category=LLM`) |
| `GET` | `/api/v1/service-offerings/{id}` | Get by business id (e.g. `gpt-5.1`) |
| `POST` | `/api/v1/service-offerings` | Create |
| `PUT` | `/api/v1/service-offerings/{id}` | Update |
| `DELETE` | `/api/v1/service-offerings/{id}` | Delete |

### Entitlements

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/entitlements` | List (`?participantId=&serviceOfferingId=&status=`) |
| `GET` | `/api/v1/entitlements/{id}` | Get by id |
| `POST` | `/api/v1/entitlements` | Create |
| `PUT` | `/api/v1/entitlements/{id}` | Update |
| `GET` | `/api/v1/entitlements/cache` | Check-cache status (Maintainer) |
| `POST` | `/api/v1/entitlements/cache/refresh` | Force check-cache rebuild (Maintainer) |

```bash
curl -s -X POST http://localhost:8080/api/v1/entitlements \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "participantId": "P001",
    "serviceOfferingId": "gpt-5.1",
    "status": "ACTIVE",
    "validFrom": "2026-01-01",
    "validTo": "2030-01-01",
    "config": "{\"max_tpm\":1000,\"max_rpm\":20}",
    "notes": "Pilot entitlement"
  }' | jq
```

### Caller registrations

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/caller-registrations` | List (`?participantId=&status=`) |
| `GET` | `/api/v1/caller-registrations/{callerId}` | Get by unique caller ID |
| `POST` | `/api/v1/caller-registrations` | Register caller (email / SP client id / UAMI) under a participant |
| `PUT` | `/api/v1/caller-registrations/{callerId}` | Update status |
| `DELETE` | `/api/v1/caller-registrations/{callerId}` | Delete |

### Consumptions

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/consumptions` | List (`?callerId=&serviceOfferingId=`) |
| `GET` | `/api/v1/consumptions/{id}` | Get by id |
| `POST` | `/api/v1/consumptions` | Record usage JSON (`Consumption.Registrator` or Maintainer) |
| `DELETE` | `/api/v1/consumptions/{id}` | Delete |

Create body supports optional `capturedAt` (UTC instant when usage was captured at runtime; defaults to now) and `sourceRefId` (idempotent Source Reference Identification). Response includes both `capturedAt` and `createdAt` (row insert time).

Historical and live **stream** ingest use connectors (below), not this CRUD surface alone.

### Entitlement check (Entitlement.Reader)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/entitlements/check` | Check entitlement by caller ID + service offering |

Query params:

| Param | Required | Default | Description |
|-------|----------|---------|-------------|
| `callerId` | yes | — | Unique principal of the caller registration |
| `serviceOfferingId` | yes | — | Service offering id |
| `fromDate` | no | today (UTC) | Start of evaluation window (inclusive, ISO date) |
| `untilDate` | no | `fromDate` | End of evaluation window (inclusive, ISO date) |

The entitlement must **fully cover** the closed range `[fromDate, untilDate]` (status `ACTIVE`,
`validFrom ≤ fromDate`, and `validTo` null or `≥ untilDate`).

```bash
# Point-in-time check (defaults fromDate=untilDate=today UTC)
# Uses in-memory cache when the datasource-loading connector has loaded it
curl -s 'http://localhost:8080/api/v1/entitlements/check?callerId=sky.walker@company.com&serviceOfferingId=gpt-5.1' \
  -H "Authorization: Bearer $TOKEN" | jq

# Explicit date range
curl -s 'http://localhost:8080/api/v1/entitlements/check?callerId=sky.walker@company.com&serviceOfferingId=gpt-5.1&fromDate=2026-06-01&untilDate=2026-06-30' \
  -H "Authorization: Bearer $TOKEN" | jq
```

Warm-cache latency is typically **sub-millisecond** for the decision itself; end-to-end HTTP is dominated by network + JWT validation.

### Auth (Microsoft Entra principal)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/auth/me` | Current caller from the Bearer JWT (subject, scopes, roles, tenant) |

### Entra directory (`Platform-System-*` groups) + human authorization

Part of the **Entra directory connector** (`entra/` package). Loads security groups whose
**display name starts with** `Platform-System-` (configurable) and their members via
**Microsoft Graph**.

**In-memory store:** a thread-safe `ConcurrentHashMap<groupId, EntraGroupWithMembers>`
(group → members), plus reverse indexes for human identity (email / UPN / object id).
Each successful Graph load rebuilds the maps and swaps them in atomically.
**Refresh cadence:** every **15 minutes** by default (`APP_ENTRA_DIRECTORY_REFRESH_MS=900000`).

#### Human group → permission scope (static table)

Humans (tokens with email / `preferred_username`) are authorized by **membership** in
Entra groups. Group **display names** are mapped to permission scopes via a simple
**static lookup table** in code:

`security/EntraGroupPermissionScopeTable.kt`

| Entra group display name | Permission scope (`ROLE_*`) |
|--------------------------|----------------------------|
| `Platform-System-Maintainer` | `System.Maintainer` |
| `Platform-System-Reader` | `System.Reader` |
| `Platform-System-Entitlement-Reader` | `Entitlement.Reader` |
| `Platform-System-Consumption-Registrator` | `Consumption.Registrator` |

Resolution from JWT claims + Graph membership cache (merged with JWT `roles` if present):

1. Match token email / UPN / `oid` to Graph-loaded group members → display names  
2. Match JWT `groups` claim object ids to cached groups → display names  
3. Look up display names in `EntraGroupPermissionScopeTable` (static map only)

To add a group, edit `BY_GROUP_DISPLAY_NAME` in that Kotlin object.

Technical users (MI / SP) continue to use app roles on the token only.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/entra/groups` | Cached groups + members (**System.Maintainer** only) |
| `GET` | `/api/v1/entra/members` | Unique members across those groups (**System.Maintainer** only) |
| `POST` | `/api/v1/entra/groups/refresh` | Force Graph reload (**System.Maintainer** only) |

**Connector run monitoring** (same loader; status for on-call, not group content):

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/connectors/entra-directory` | Load/run info (last refresh, counts, errors) |
| `POST` | `/api/v1/connectors/entra-directory/start` | Trigger Graph reload (same as `/entra/groups/refresh`) |
| `GET` | `/api/v1/connectors` | Health list includes `entra-directory` |

```bash
export APP_ENTRA_DIRECTORY_ENABLED=true

# Shared Azure credential (Graph + Blob + Event Hub + Table).
# One client-id field: APP_UAMI_CLIENT_ID or AZURE_CLIENT_ID.
#   1. client-id + AZURE_CLIENT_SECRET → service principal (username + password)
#   2. client-id only                  → UAMI
#   3. neither                         → SAMI
#
# UAMI:
# export APP_UAMI_CLIENT_ID=<uami-client-id>
#   # or: export AZURE_CLIENT_ID=<uami-client-id>   (no secret)
#
# Service principal:
export AZURE_TENANT_ID=<tenant>
export AZURE_CLIENT_ID=<app-client-id>
export AZURE_CLIENT_SECRET=<secret>
#
# SAMI: leave client-id empty (host must have a system-assigned identity)

# Graph needs application permissions + admin consent on the credential principal:
#   Group.Read.All, GroupMember.Read.All, User.Read.All
#   (or Directory.Read.All alone)

# Optional:
# export APP_ENTRA_GROUP_NAME_PREFIX=Platform-System-
# export APP_ENTRA_INCLUDE_TRANSITIVE_MEMBERS=true
# export APP_ENTRA_DIRECTORY_REFRESH_MS=900000   # 15 minutes (default)
```

### Azure credential (`app.azure.credential`)

One **`client-id`** is bound from `APP_UAMI_CLIENT_ID` (preferred) or `AZURE_CLIENT_ID`.
Mode is **inferred**:

| Condition | Mode |
|-----------|------|
| `client-id` + `client-secret` both set | **Service principal** (needs `AZURE_TENANT_ID`) |
| `client-id` only | **UAMI** |
| neither | **SAMI** |

---

## Persistence (Azure Table / in-memory)

There is **no JDBC / H2 / Flyway** stack. Catalog and consumption rows are stored via repository interfaces backed by either:

| Mode | When | Multi-node |
|------|------|------------|
| **In-memory** | `app.azure-table.enabled=false` (default local/CI) | **No** — process-local only |
| **Azure Table Storage** | `APP_AZURE_TABLE_ENABLED=true` | **Yes** — shared durable store |

### Configuration

```bash
# Production
export APP_AZURE_TABLE_ENABLED=true
export APP_AZURE_TABLE_ENDPOINT=https://<account>.table.core.windows.net
# Auth: app.azure.credential (same as Graph/Blob/EH)

# Local Azurite / account key (optional)
# export APP_AZURE_TABLE_CONNECTION_STRING="DefaultEndpointsProtocol=http;..."
# export APP_AZURE_TABLE_PREFIX=pms   # table name prefix
```

```yaml
app:
  azure-table:
    enabled: ${APP_AZURE_TABLE_ENABLED:false}
    endpoint: ${APP_AZURE_TABLE_ENDPOINT:}
    connection-string: ${APP_AZURE_TABLE_CONNECTION_STRING:}
    table-prefix: ${APP_AZURE_TABLE_PREFIX:pms}
    create-tables-if-not-exist: true
```

### Table layout (logical)

| Entity | PartitionKey | RowKey |
|--------|--------------|--------|
| Services | `service` | serviceId |
| Participants | `participant` | participantId |
| Callers | `caller` | callerId |
| Entitlements | participantId | serviceOfferingId |
| Consumptions | callerId | consumption UUID |
| Source-ref index | `sourceRef` | sourceRefId → consumption id |

Uniqueness and referential integrity are enforced in **application** code (services / repositories), not by SQL constraints.

### Catalog seed (external only)

The backend **never** loads `datasource.json` or any other seed file. Populate the durable store
with your own scripts/tooling. A sample document shape lives at
[`scripts/fixtures/datasource.json`](../scripts/fixtures/datasource.json).

---

## Domain model

```
Participant 1──* ParticipantServiceEntitlement *──1 ServiceOffering
     │
     └──* ParticipantCallerRegistration 1──* ParticipantCallConsumption *──1 ServiceOffering
           (callerId = unique key)
```

| Entity | Key fields |
|--------|------------|
| **Participant** | `id`, `name`, `contact`, `status`, audit (`createdAt`/`createdBy`, `updatedAt`/`updatedBy`) |
| **ServiceOffering** | `id` (business key), `name`, `category`, `provider` (default `SYSTEM` in service), `config` (JSON), `active`, audit |
| **ParticipantServiceEntitlement** | participant ↔ offering, validity window, `config` (JSON limits), status, audit |
| **ParticipantCallerRegistration** | `callerId` (PK), `participantId`, `status`, audit |
| **ParticipantCallConsumption** | `callerId` ↔ offering, `sourceRefId` (unique when set), `consumptionData` (JSON), `capturedAt` (runtime), `createdAt` (insert) |

Domain types are plain Kotlin models (no JPA). Audit actors (`createdBy` / `updatedBy`) are set in **service** code only (default principal `SYSTEM`).

---

## Connectors (integrations)

Connectors are **in-process** Spring adapters that call domain services (e.g. `ConsumptionService.createFromImport`, `EntitlementCheckCache.refresh`). Domain stays free of Azure SDKs where practical.

Full design (SPI shape, dual-path, security):  
**[docs/design/connectors-entra-blob-eventhub.md](../docs/design/connectors-entra-blob-eventhub.md)**

### Datasource loading (entitlement check cache only)

Connector id: **`datasource-loading`**.

```text
External scripts ──seed──► durable store (Table or memory)
                                │
                    hourly schedule / start / manual refresh
                                ▼
                    EntitlementCheckCache
                      • serviceId → service
                      • callerId → participant + status
                      • (participantId, serviceId) → entitlement
                        (ACTIVE and valid for "today" UTC only)
                                │
                                ▼
              GET /api/v1/entitlements/check  (cache-first)
```

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/connectors/datasource-loading` | Maintainer | Status (counts, last refresh, errors) |
| `POST` | `/api/v1/connectors/datasource-loading/start` | Maintainer | Cache load + arm schedule |
| `POST` | `/api/v1/connectors/datasource-loading/stop` | Maintainer | Disarm schedule |
| `PUT` | `/api/v1/connectors/datasource-loading/config` | Maintainer | e.g. `{ "refreshIntervalMs": 3600000 }` |
| `GET` | `/api/v1/entitlements/cache` | Maintainer | Cache snapshot metadata |
| `POST` | `/api/v1/entitlements/cache/refresh` | Maintainer | One-shot cache rebuild (no schedule change) |

| Setting | Env / property | Default |
|---------|----------------|---------|
| Enable connector | `APP_CONNECTOR_DATASOURCE_ENABLED` | `true` |
| Auto-start on boot | `APP_CONNECTOR_DATASOURCE_AUTO_START` | `true` |
| Refresh interval | `APP_CONNECTOR_DATASOURCE_REFRESH_MS` | `3600000` (1 hour) |

Cache holds only **ACTIVE** entitlements whose validity window covers the UTC calendar day of the refresh (reduces memory vs a full 30×200 matrix of historical rows). After entitlement CRUD, either wait for the next hourly tick or call `POST .../cache/refresh`.

### Dual-path consumption pipeline

```text
Producers → Event Hub ──┬──► Live consumer (Event Hub connector)  ──► domain
                        │
                        └──► Event Hub Capture → Azure Blob (Avro)
                                      └──► Backfill job (Blob connector) ──► domain
```

| Connector | Purpose | Auth (prod) | Control | Implementation status |
|-----------|---------|-------------|---------|------------------------|
| **Datasource loading** | Rebuild entitlement check cache from store | N/A (reads durable store) | `/api/v1/connectors/datasource-loading/**` | **Implemented** (default auto-start; no in-app seed) |
| **Entra directory** | Graph cache for `Platform-System-*` groups → human auth | Graph MI / app secret | `/api/v1/entra/**`, scheduled refresh | **Implemented** (`entra/`) |
| **Blob Capture** | Historical / gap load of Capture Avro by date range | **Storage Blob Data Reader** on MI | Async jobs, **System.Maintainer** only | **Implemented** (control plane + runner; default off) |
| **Event Hub** | Continuous live consumption events | **Event Hubs Data Receiver** on MI; checkpoint container **Contributor** | **Start/stop Web API** (Maintainer) | **Implemented** (control plane + Azure runtime; default disabled) |

**Also available today:** `POST /api/v1/consumptions` for direct/push registration (`Consumption.Registrator`).

### Why dual-path

- **Live path** optimizes for lag (Event Hub).  
- **Historical path** reuses **Event Hub Capture** in Blob (no re-emit of history).  
- Overlap is safe when producers supply a stable **`source_ref_id`** (unique in DB) and import is race-safe.

Do **not** run a continuous Capture **poller** together with the Event Hub consumer by default; use **operator-triggered backfill** for history.

### Blob hierarchical Avro import (implemented)

Reads Avro files from a hierarchical-namespace blob container under **one or more** root prefixes:

```text
{blob-prefix}/yyyy/MM/dd/HH_mm_ss.avro
```

Connector API is unified under `/api/v1/connectors/{id}` where
`id=consumption-storage` for this connector.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/connectors` | System.Maintainer | List connector health |
| `GET` | `/api/v1/connectors/consumption-storage` | System.Maintainer | Retrieve/import Avro for `startDate`..`endDate` |

```http
GET /api/v1/connectors/consumption-storage?startDate=2024-07-01&endDate=2024-07-03&dryRun=false&blobPrefixes=eh-capture&blobPrefixes=manual/import
Authorization: Bearer <token with System.Maintainer>
```

| Query param | Required | Description |
|-------------|----------|-------------|
| `startDate` | yes | Inclusive start (`YYYY-MM-DD`) |
| `endDate` | yes | Inclusive end (`YYYY-MM-DD`) |
| `dryRun` | no | Default `false`; when `true`, parse without DB writes |
| `blobPrefixes` | no | Subset of configured prefixes; omit for all |

```bash
# Connector health (all connectors; ids match path segments)
curl -s "http://localhost:8080/api/v1/connectors" \
  -H "Authorization: Bearer $TOKEN" | jq

# Retrieve Avro for a day range (dry-run parse only)
curl -s "http://localhost:8080/api/v1/connectors/consumption-storage?startDate=2024-07-01&endDate=2024-07-01&dryRun=true" \
  -H "Authorization: Bearer $TOKEN" | jq
```

| Concern | Behaviour |
|---------|-----------|
| Authz | **System.Maintainer only** |
| Layout | Per root prefix: day folders `yyyy/MM/dd`; terminal files `HH_mm_ss.avro` |
| Prefixes | Multiple roots supported; import walks **prefix × day**; blob paths de-duplicated |
| Decode | **Two-layer**: Event Hub Capture Avro `Body` JSON **or** flat business Avro fields → `createFromImport` |
| Idempotency | Domain: existing `source_ref_id` / optional event id (race-safe) |
| Guards | `max-range-days`, `max-blobs-per-job`; `dryRun` parses without DB writes |
| Auth to Azure | Managed Identity + `storage-account-url`, or local `connection-string` |

| Setting | Env / property | Default |
|---------|----------------|---------|
| Enable connector | `APP_CONNECTOR_BLOB_ENABLED` / `app.connectors.consumption-blob.enabled` | `false` |
| Storage account URL | `APP_CONNECTOR_BLOB_ACCOUNT_URL` | _(required for MI)_ |
| Connection string | `APP_CONNECTOR_BLOB_CONNECTION_STRING` | _(local only)_ |
| Container | `APP_CONNECTOR_BLOB_CONTAINER` | |
| Blob prefixes (list) | `app.connectors.consumption-blob.blob-prefixes` / `APP_CONNECTOR_BLOB_PREFIXES_0`… | `[]` |
| Blob prefix (singular / CSV) | `APP_CONNECTOR_BLOB_PREFIX` | _(merged with list; empty = container root)_ |
| Max range days | `APP_CONNECTOR_BLOB_MAX_RANGE_DAYS` | `31` |
| Max blobs per request | `APP_CONNECTOR_BLOB_MAX_BLOBS` | `500` |
| Require `source_ref_id` | `APP_CONNECTOR_BLOB_REQUIRE_SOURCE_REF_ID` | `true` |

```yaml
app:
  connectors:
    consumption-blob:
      enabled: true
      storage-account-url: https://acct.blob.core.windows.net
      container: consumption-capture
      blob-prefixes:
        - eh-capture
        - manual/import
      # or: blob-prefix: eh-capture,manual/import
```

### Event Hub continuous (implemented control plane)

Lifecycle is **operator-controlled** (not auto-started by default). Connector id:
`consumption-eventhub`.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/connectors` | System.Maintainer | List connector health |
| `GET` | `/api/v1/connectors/{id}` | System.Maintainer | Status (`id=consumption-eventhub`) |
| `POST` | `/api/v1/connectors/{id}/start` | System.Maintainer | Start processor |
| `POST` | `/api/v1/connectors/{id}/stop` | System.Maintainer | Stop processor |

```bash
# Status
curl -s http://localhost:8080/api/v1/connectors/consumption-eventhub \
  -H "Authorization: Bearer $TOKEN" | jq

# Start / stop (requires app.connectors.consumption-eventhub.enabled=true)
curl -s -X POST http://localhost:8080/api/v1/connectors/consumption-eventhub/start \
  -H "Authorization: Bearer $TOKEN" | jq
curl -s -X POST http://localhost:8080/api/v1/connectors/consumption-eventhub/stop \
  -H "Authorization: Bearer $TOKEN" | jq
```

| Setting | Env / property | Default |
|---------|----------------|---------|
| Enable connector | `APP_CONNECTOR_EH_ENABLED` / `app.connectors.consumption-eventhub.enabled` | `false` |
| Auto-start on boot | `APP_CONNECTOR_EH_AUTO_START` / `…auto-start` | `false` (prefer API start) |
| Namespace | `APP_CONNECTOR_EH_NAMESPACE` | _(required for Azure runtime)_ |
| Hub name | `APP_CONNECTOR_EH_NAME` | |
| Consumer group | `APP_CONNECTOR_EH_CONSUMER_GROUP` | `$Default` |
| Checkpoint account URL | `APP_CONNECTOR_EH_CHECKPOINT_ACCOUNT_URL` | |
| Checkpoint container | `APP_CONNECTOR_EH_CHECKPOINT_CONTAINER` | `eh-checkpoints` |
| Require `source_ref_id` | `APP_CONNECTOR_EH_REQUIRE_SOURCE_REF_ID` | `true` |

| Runtime behaviour | |
|-------------------|--|
| Auth | **SP / UAMI / SAMI** via `app.azure.credential` (one `client-id`) — no EH connection string |
| Checkpoint store | **Separate** Blob container (not Capture) |
| Delivery | At-least-once; domain dedup via race-safe `createFromImport` + `source_ref_id` |
| Trust | Hub **senders** ≈ registrator trust boundary; this service’s MI is **receiver only** |
| Incomplete Azure config | In-memory runtime (start/stop still work for control-plane tests) |

### Managed Identity (production)

Prefer **no connection strings** for Blob or Event Hubs in production.

| Azure resource | Role on workload MI |
|----------------|---------------------|
| Capture storage container | **Storage Blob Data Reader** |
| EH checkpoint container (separate) | **Storage Blob Data Contributor** |
| Event Hub | **Azure Event Hubs Data Receiver** |
| Event Hub (producers) | **Azure Event Hubs Data Sender** on **producer** identities only |

Local: set `AZURE_CLIENT_ID` + `AZURE_CLIENT_SECRET` (+ `AZURE_TENANT_ID`) for a service principal, or use test mocks.

### Config direction

Legacy keys under `app.consumption-import.*` may remain as a temporary bridge. Target shape:

```yaml
app:
  azure:
    credential:
      # APP_UAMI_CLIENT_ID (UAMI) or AZURE_CLIENT_ID (SP / WI UAMI)
      client-id: ${APP_UAMI_CLIENT_ID:${AZURE_CLIENT_ID:}}
      client-secret: ${AZURE_CLIENT_SECRET:}   # with client-id → service principal
      tenant-id: ${AZURE_TENANT_ID:}
  azure-table:
    enabled: true
    endpoint: https://<account>.table.core.windows.net
  connectors:
    datasource-loading:
      enabled: true
      auto-start: true
      refresh-interval-ms: 3600000
    consumption-blob:
      enabled: false          # default off
      runner-enabled: false   # default off (fail-closed)
      storage-account-url: https://<account>.blob.core.windows.net
      container: consumption-capture
    consumption-eventhub:
      enabled: false
      fully-qualified-namespace: <ns>.servicebus.windows.net
      event-hub-name: consumption
      consumer-group: platform-management
      checkpoint-storage-account-url: https://<account>.blob.core.windows.net
      checkpoint-container: eh-checkpoints
```

See `application.yml` and the design doc for the full property matrix.

### Producer contract (required for dual-path)

Events (live body and Capture body) should include a stable **`source_ref_id`** (e.g. request UUID).  
Without it, dual delivery can insert multiple rows with null refs. Connector ingest should require it when both paths are enabled.

---

## Microsoft Entra ID authentication & authorization

The API is an **OAuth2 resource server**. Spring Security validates **Microsoft Entra ID** access tokens (issuer + signature + audience) and authorizes callers from the JWT **`roles`** claim (Entra **app roles**).

| Mode | Profile | Behaviour |
|------|---------|-----------|
| Local / default | _(none)_ | Entra JWT + app roles required (`application.yml`) |
| Local verbose logs | `local` | Same security, DEBUG logging |
| AKS / Docker | `k8s` | Same security + container probes |

```http
Authorization: Bearer <access_token>
```

Implementation touchpoints:

| Piece | Location |
|-------|----------|
| Security filter chain + Entra claim → authority mapping | `config/SecurityConfig.kt` |
| JWT scopes / roles / groups → authorities | `security/JwtAuthorityMapper.kt` |
| Role name constants | `security/AppRoles.kt` |
| SpEL helper (`@authz.hasAnyRole(...)`) | `security/Authz.kt` |
| Method security on controllers | `@PreAuthorize` on controller methods |
| Permit-all / optional scope gate / CORS / group maps | `config/AppSecurityProperties.kt` + `app.security.*` |
| Issuer + audiences | `application.yml` (`APP_AZURE_TENANT_ID`, `APP_API_CLIENT_ID`) |
| Authenticated principal probe | `GET /api/v1/auth/me` |
| K8s env (`SPRING_PROFILES_ACTIVE=k8s`) | `k8s/configmap.yaml` + secrets |

### Group object IDs and OAuth scopes from Application Registration ID

Prefer assigning **app roles to security groups in Entra** so the token already has `roles`.
When that is not available (token has `groups` only), the API can map group object IDs to
app roles — globally or scoped to an **Application Registration (client) ID**:

```yaml
app:
  security:
    # Global: any token with this group object id gets System.Reader
    group-role-mappings:
      "11111111-2222-3333-4444-555555555555":
        - System.Reader

    # Per Application Registration (matched against token azp / appid / aud)
    application-registrations:
      - client-id: ${APP_API_CLIENT_ID}
        oauth-scopes:
          - access_as_user
          - api://${APP_API_CLIENT_ID}/access_as_user
        group-role-mappings:
          "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee":
            - System.Maintainer
```

Effects:

| Claim / config | Mapped authorities / fields |
|----------------|-----------------------------|
| `scp` / `scope` | `SCOPE_<name>` (+ short name for full URI scopes) |
| `roles` | `ROLE_<appRole>` |
| `groups` | `GROUP_<objectId>` + optional `ROLE_*` from mappings |
| `application-registrations[].oauth-scopes` | Shown on `/api/v1/auth/me` as `expectedScopes` |

`GET /api/v1/auth/me` returns `groups`, `roles` (claim + mapped), `scopes`, `expectedScopes`,
and `matchedApplicationRegistrationIds` for diagnostics.

---

### Recommended authorization model (human + technical)

Use **one mechanism for both humans and technical accounts: Entra app roles** emitted in the access token as:

```json
"roles": ["System.Maintainer"]
```

| Caller type | How the role is granted in Entra | How the token is obtained | What the API checks |
|-------------|----------------------------------|---------------------------|---------------------|
| **Human user** | Assign app role to an **Entra security group**; put users in that group | Delegated flow (interactive login) with scope `api://{API_CLIENT_ID}/access_as_user` | `roles` → `@PreAuthorize` |
| **Technical user** (Managed Identity / SP) | Assign app role directly to the **managed identity / service principal** | Client credentials / IMDS with scope `api://{API_CLIENT_ID}/.default` | Same `roles` → same `@PreAuthorize` |

**Why app roles (not raw group IDs) work best for both:**

1. **Same claim for both principal types.** Humans (including group-assigned roles) and applications get application permissions in `roles`. The API does not need separate code paths.
2. **Groups stay in Entra, not in the API.** You manage membership in groups (`Participant-API-Maintainers`, …). Entra **resolves** group → app role into the token. Controllers only know `System.Maintainer`, not group GUIDs.
3. **No group overage problem.** Large `groups` claims can be truncated (`_claim_names` / Graph lookup). App roles for this API stay small and complete in the token.
4. **Managed Identity has no user groups.** MI is an application identity; it only receives **app roles** assigned to its service principal. A group-only model cannot authorize MI.

**Do not authorize on:**

| Claim / approach | Problem |
|------------------|---------|
| `groups` only | Works poorly for MI; overage; couples API to directory structure |
| Different rules for user vs app tokens | Duplicated policy, harder to test |
| Client id allow-lists only | No least privilege, no group-based admin model |

**Optional outer gate:** `APP_REQUIRED_SCOPE=access_as_user` requires a delegated scope (or matching app role name) on every `/api/**` call. Prefer **app roles for authorization** and keep `required-scope` empty unless you need an extra tenant-wide gate.

```
┌─────────────────────────────────────────────────────────────────────┐
│ Microsoft Entra ID                                                  │
│                                                                     │
│  Security group "Participant-API-Maintainers"                       │
│       │  (members = human users)                                    │
│       ▼                                                             │
│  Enterprise app (API) ── App role assignment                        │
│       System.Maintainer  ──► group                                  │
│       System.Reader      ──► group                                  │
│       Entitlement.Reader ──► group and/or application               │
│       Consumption.Registrator ──► Managed Identity (application)    │
└─────────────────────────────────────────────────────────────────────┘
        │ human: auth code / device code / az login
        │ tech:  IMDS / DefaultAzureCredential / client credentials
        ▼
  Access token (aud = API client id, roles = [...])
        │
        ▼
  platform-management-service  (@PreAuthorize on roles)
```

---

### App roles and endpoint permissions

| Entra app role value | Typical assignees | Permissions |
|----------------------|-------------------|-------------|
| `System.Maintainer` | Human admins via **security group** | Full CRUD on all resources |
| `System.Reader` | Humans via **security group** (read-only ops) | All **GET** list/get endpoints (+ entitlement check) |
| `Entitlement.Reader` | Humans via group **or** system apps | `GET /api/v1/entitlements/check` only |
| `Consumption.Registrator` | **Managed Identity** / service principal | `POST /api/v1/consumptions` |
| _(any authenticated)_ | Any valid token with any role | `GET /api/v1/auth/me` |

**Controller vs service layer:** role checks live on the **controller** (`@PreAuthorize` / `@authz.canRead()` etc.). That maps HTTP routes to roles clearly and keeps services free of security so they can call each other without nested role re-checks. Use the **service** layer for *business* rules (validity dates, active status) — e.g. `EntitlementService.checkByCallerAndService`.

| Endpoint | System.Maintainer | System.Reader | Entitlement.Reader | Consumption.Registrator |
|----------|:-----------------:|:-------------:|:-------------------:|:-----------------------:|
| Participants GET | ✓ | ✓ | | |
| Participants write | ✓ | | | |
| Service offerings GET | ✓ | ✓ | | |
| Service offerings write | ✓ | | | |
| Caller registrations GET | ✓ | ✓ | | |
| Caller registrations write | ✓ | | | |
| Entitlements GET list/id | ✓ | ✓ | | |
| Entitlements write | ✓ | | | |
| `GET /entitlements/check` | ✓ | ✓ | ✓ | |
| Consumptions GET | ✓ | ✓ | | |
| Consumptions DELETE | ✓ | | | |
| `POST /consumptions` | ✓ | | | ✓ |
| Entra directory GET/refresh | ✓ | | | |
| Connectors (list/start/stop/config) | ✓ | | | |
| Entitlement cache GET/refresh | ✓ | | | |
| `GET /auth/me` | ✓ | ✓ | ✓ | ✓ |

---

### 1. Register the API (resource) in Entra

1. Azure Portal → **Microsoft Entra ID** → **App registrations** → **New registration**  
   Name e.g. `platform-management-service`. Account type: single tenant (or as required).
2. Note:
   - **Application (client) ID** → `APP_API_CLIENT_ID`
   - **Directory (tenant) ID** → `APP_AZURE_TENANT_ID`
3. **Expose an API**
   - Application ID URI: `api://<APP_API_CLIENT_ID>` (or a verified custom URI)
   - Add a **delegated** scope for human clients, e.g.:
     - Scope name: `access_as_user`
     - Who can consent: Admins only (or admins + users)
     - Full scope: `api://<APP_API_CLIENT_ID>/access_as_user`
4. **App roles** → *Create app role* (or edit Manifest). Values must match the table below **exactly**:

   | Display name | Value | Allowed member types | Description |
   |--------------|-------|----------------------|-------------|
   | System Maintainer | `System.Maintainer` | **Users/Groups** | Full API admin |
   | System Reader | `System.Reader` | **Users/Groups** | Read-only on all GET endpoints |
   | Entitlement Reader | `Entitlement.Reader` | **Users/Groups** and **Applications** | Entitlement check only |
   | Consumption Registrator | `Consumption.Registrator` | **Applications** | Register consumption (MI / SP) |

5. **Token configuration** (optional): add optional claims `preferred_username`, `email` for human tokens (shown on `/api/v1/auth/me`).

---

### 2. Assign roles to human users via security groups

This is the recommended model for humans: **role → group → users**.

1. **Microsoft Entra ID** → **Groups** → **New group**
   - Type: **Security**
   - Examples:
     - `Participant-API-Maintainers` → will get `System.Maintainer`
     - `Participant-API-Readers` → will get `System.Reader`
     - `Participant-API-Entitlement-Readers` → will get `Entitlement.Reader`
2. Add human users (or nested groups) as **members**.
3. Open the API’s **Enterprise application** (same name as the app registration):
   - **Microsoft Entra ID** → **Enterprise applications** → select the API app  
   - **Users and groups** → **Add user/group**
   - Select the **security group**
   - Select the **app role** (`System.Maintainer`, `System.Reader`, or `Entitlement.Reader`)
   - Assign

When a member of that group signs in and requests a token **for this API**, Entra includes the app role in the access token:

```json
{
  "aud": "<APP_API_CLIENT_ID>",
  "iss": "https://login.microsoftonline.com/<tenant>/v2.0",
  "oid": "<user-object-id>",
  "preferred_username": "alice@contoso.com",
  "scp": "access_as_user",
  "roles": ["System.Maintainer"],
  "tid": "<tenant-id>"
}
```

> You do **not** need to put group object IDs in the API config. Group membership is an Entra concern; the API only sees `roles`.

---

### 3. Register a public/confidential client for human login (optional but typical)

For interactive human access (SPA, desktop, or “login with Azure CLI” style tools):

1. **App registrations** → **New registration** (e.g. `platform-management-cli` or your SPA).
2. **Authentication**
   - Public client / native: enable public client flows if using device code.
   - SPA: add redirect URI (e.g. `http://localhost:3000`).
3. **API permissions** → **Add a permission** → **My APIs** → select the API app → **Delegated** → `access_as_user` → **Grant admin consent**.
4. Note the client’s **Application (client) ID** → `AZURE_HUMAN_CLIENT_ID` below.

Humans still receive **app roles** from group assignments on the **API** enterprise app (step 2), not from this client app.

---

### 4. Assign roles to Managed Identity (technical user)

1. Create or use a **user-assigned** or **system-assigned** managed identity (AKS workload identity, App Service, VM, Function, …).
2. Note the identity’s **Object (principal) ID**.
3. Assign the app role on the **API** resource (portal or CLI).

**Azure CLI (recommended):**

```bash
# IDs
API_APP_ID="<APP_API_CLIENT_ID>"                 # API app registration client id
API_SP_OBJECT_ID=$(az ad sp show --id "$API_APP_ID" --query id -o tsv)

MI_PRINCIPAL_ID="<managed-identity-principal-object-id>"

# App role id for Consumption.Registrator from the API app's appRoles
ROLE_ID=$(az ad sp show --id "$API_APP_ID" --query "appRoles[?value=='Consumption.Registrator'].id | [0]" -o tsv)

az rest --method POST \
  --uri "https://graph.microsoft.com/v1.0/servicePrincipals/$API_SP_OBJECT_ID/appRoleAssignedTo" \
  --headers "Content-Type=application/json" \
  --body "{
    \"principalId\": \"$MI_PRINCIPAL_ID\",
    \"resourceId\": \"$API_SP_OBJECT_ID\",
    \"appRoleId\": \"$ROLE_ID\"
  }"
```

Portal alternative: **Enterprise applications** → API app → **Users and groups** → **Add** → pick the managed identity → role `Consumption.Registrator`.

Application tokens look like:

```json
{
  "aud": "<APP_API_CLIENT_ID>",
  "iss": "https://login.microsoftonline.com/<tenant>/v2.0",
  "oid": "<mi-principal-object-id>",
  "appid": "<mi-client-id>",
  "idtyp": "app",
  "roles": ["Consumption.Registrator"],
  "tid": "<tenant-id>"
}
```

(No `scp` claim on pure application tokens; authorization uses **`roles`** only.)

---

### 5. Generate an access token — human user

Prerequisites: user is in a group that has an app role on the API (step 2); human client has delegated permission `access_as_user` (step 3).

#### Scripts (recommended)

```bash
chmod +x scripts/*.sh scripts/lib/*.sh

export APP_AZURE_TENANT_ID="<tenant-guid>"
export APP_API_CLIENT_ID="<api-app-client-id>"

# Azure CLI interactive login (default) — user must be in the Entra group
TOKEN=$(./scripts/get-token-human.sh)
# or: eval "$(./scripts/get-token-human.sh --export --print-claims)"

# Device code (no browser on this machine; needs a public client id)
export AZURE_HUMAN_CLIENT_ID="<public-or-spa-client-id>"
TOKEN=$(./scripts/get-token-human.sh --method device-code --print-claims)
```

| Script | Purpose |
|--------|---------|
| `scripts/get-token-human.sh` | Delegated token for a human (`az-cli` or `device-code`) |
| `scripts/get-token-mi.sh` | Application token for MI / SP (`imds`, `az-identity`, `client-credentials`) |
| `scripts/lib/token-common.sh` | Shared helpers (JWT decode, env checks) |

Common flags: `--print-claims` (JWT payload on stderr), `--export` (prints `export TOKEN=...` for `eval`).

#### Manual alternatives

**Azure CLI**

```bash
az login --tenant "$APP_AZURE_TENANT_ID"
TOKEN=$(az account get-access-token \
  --resource "api://${APP_API_CLIENT_ID}" \
  --query accessToken -o tsv)
# Expect roles: System.Maintainer or Entitlement.Reader; often scp: access_as_user
```

**Authorization code + PKCE (SPA)** — use **MSAL** with scope  
`api://${APP_API_CLIENT_ID}/access_as_user`. Do **not** use ROPC in production.

#### Call the API as a human

```bash
curl -s http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN" | jq

curl -s http://localhost:8080/api/v1/participants \
  -H "Authorization: Bearer $TOKEN" | jq

curl -s "http://localhost:8080/api/v1/entitlements/check?callerId=sky.walker@company.com&serviceOfferingId=gpt-5.1" \
  -H "Authorization: Bearer $TOKEN" | jq
```


---

### 6. Generate an access token — technical user (Managed Identity)

Prerequisites: MI has app role assignment on the API (step 4). Scope for application permissions is always **`/.default`**.

#### Scripts (recommended)

```bash
export APP_API_CLIENT_ID="<api-app-client-id>"

# On Azure compute with system-assigned MI (auto-detects IMDS when available)
TOKEN=$(./scripts/get-token-mi.sh --print-claims)

# User-assigned managed identity
export AZURE_MI_CLIENT_ID="<user-assigned-mi-client-id>"
TOKEN=$(./scripts/get-token-mi.sh --method imds)

# After: az login --identity
TOKEN=$(./scripts/get-token-mi.sh --method az-identity)

# Local SP stand-in (same app role as MI; not for production secrets in git)
export APP_AZURE_TENANT_ID="<tenant-guid>"
export AZURE_TECH_CLIENT_ID="<sp-client-id>"
export AZURE_TECH_CLIENT_SECRET="<sp-secret>"
TOKEN=$(./scripts/get-token-mi.sh --method client-credentials --print-claims)
```

If `--method` is omitted, the script chooses: **imds** (if reachable) → **client-credentials** (if SP env set) → **az-identity**.

#### Manual / in-app alternatives

**IMDS (system-assigned MI)**

```bash
MI_RESOURCE="api://${APP_API_CLIENT_ID}"
TOKEN=$(curl -s "http://169.254.169.254/metadata/identity/oauth2/token?api-version=2019-08-01&resource=${MI_RESOURCE}" \
  -H "Metadata: true" | jq -r .access_token)
```

**DefaultAzureCredential** (AKS workload identity / App Service / etc.)

```csharp
var credential = new DefaultAzureCredential();
var token = await credential.GetTokenAsync(
    new TokenRequestContext(new[] { $"api://{apiClientId}/.default" }));
```

```python
from azure.identity import DefaultAzureCredential
token = DefaultAzureCredential().get_token(f"api://{api_client_id}/.default")
```

#### Call the API as a technical user

```bash
curl -s http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN" | jq

curl -s -X POST http://localhost:8080/api/v1/consumptions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "callerId": "sky.walker@company.com",
    "serviceOfferingId": "gpt-5.1",
    "sourceRefId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "consumptionData": "{\"input_token\":120,\"output_token\":40}",
    "capturedAt": "2026-07-01T12:00:00Z"
  }' | jq
```

For **historical Capture** or **continuous Event Hub** ingest, see [Connectors](#connectors-integrations) and the [design doc](../docs/design/connectors-entra-blob-eventhub.md).

---

### 7. Run the API

```bash
export APP_AZURE_TENANT_ID=<tenant-guid>
export APP_API_CLIENT_ID=<api-app-client-id>
# Leave empty: authorize only via app roles (recommended for human + MI)
# export APP_AZURE_REQUIRED_SCOPE=access_as_user

mvn spring-boot:run
```

Configuration is in **`application.yml`** (no separate secure profile):

- `issuer-uri`: `https://login.microsoftonline.com/{tenant}/v2.0`
- `audiences`: bare client id **and** `api://{client-id}`
- `app.security.permit-all: false` (always)
- JWT → authorities:
  - `scp` / `scope` → `SCOPE_*` (delegated; informational / optional gate)
  - `roles` → `ROLE_*` (**authorization**)

Verify token mapping:

```bash
curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN" | jq
# "roles": ["System.Maintainer"]  or  ["Consumption.Registrator"]
# "authorities": ["ROLE_System.Maintainer", ...]
```

Without a token:

```bash
curl -i http://localhost:8080/api/v1/participants
# HTTP/1.1 401 Unauthorized
# WWW-Authenticate: Bearer
```

Wrong / missing app role:

```bash
curl -i http://localhost:8080/api/v1/participants -H "Authorization: Bearer $TOKEN"
# HTTP/1.1 403 Forbidden  (authenticated but not System.Maintainer)
```

Docker and AKS use profile `k8s` plus the same Entra env vars from secrets.

### Troubleshooting token / roles

| Symptom | Likely cause |
|---------|----------------|
| 401 always | Wrong issuer/audience; token not for this API; missing `Authorization` header |
| App fails to start | `APP_AZURE_TENANT_ID` / `APP_API_CLIENT_ID` not set |
| 403 with valid login | App role missing on token — check group→role assignment (human) or MI app role assignment (tech) |
| Token has `groups` but no `roles` | You assigned the group as a member without selecting an **app role**, or token was requested for the wrong resource (e.g. Graph / ARM) |
| MI token has no `roles` | App role not assigned to MI principal, or scope was not `api://…/.default` |
| Human token has `scp` but empty `roles` | User not in a group that has an app role on the **API** enterprise application |
| `aud` mismatch | Request token with resource/scope for **this** API (`api://{APP_API_CLIENT_ID}/…`) |

---

## Build & test

```bash
mvn clean verify
mvn package

export APP_AZURE_TENANT_ID=<tenant-guid>
export APP_API_CLIENT_ID=<api-app-client-id>
java -jar target/platform-management-service-1.0.0-SNAPSHOT.jar
```

---

## Container image

Multi-stage `Dockerfile`:

1. **Build** — `maven:3.9.9-eclipse-temurin-17` packages the fat JAR  
2. **Runtime** — `eclipse-temurin:17-jre-jammy`, non-root UID `1001`, port `8080`  
3. Default profile: `SPRING_PROFILES_ACTIVE=k8s` (Entra JWT always on via `application.yml`)

### Local build & run

```bash
docker build -t platform-management-service:1.0.0 .

docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=k8s \
  -e APP_AZURE_TENANT_ID=<tenant-guid> \
  -e APP_API_CLIENT_ID=<api-app-client-id> \
  platform-management-service:1.0.0
```

### Push to Azure Container Registry (ACR)

```bash
# One-time: attach ACR pull rights to AKS (recommended)
# az aks update -n <aks-name> -g <rg> --attach-acr <acr-name>

chmod +x scripts/*.sh
./scripts/build-and-push-acr.sh <acr-name> 1.0.0
```

Or use the GitHub Actions workflow in `.github/workflows/build-push-acr.yml` (OIDC + ACR).

---

## GitLab CI/CD

Full pipeline in [`.gitlab-ci.yml`](.gitlab-ci.yml). Setup details: [`.gitlab/ci/README.md`](.gitlab/ci/README.md).

```
validate → test → package (Kaniko) → security (Trivy) → deploy staging / production
```

| Stage | Behaviour |
|-------|-----------|
| **validate / test** | Maven compile + `verify`, JUnit reports in MR |
| **package** | Kaniko builds the Dockerfile; pushes to **GitLab Container Registry** (and optional **ACR**) |
| **security** | Trivy CRITICAL/HIGH scan |
| **deploy:staging** | Auto on default branch → AKS |
| **deploy:production** | Manual on tags `v1.2.3` → AKS |

Configure at least these CI/CD variables: `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` (deploy SP login), plus `APP_AZURE_TENANT_ID`, `APP_API_CLIENT_ID` (injected into the app), `AKS_RESOURCE_GROUP`, `AKS_CLUSTER_NAME`. Set `ACR_NAME` to also publish to Azure Container Registry.

---

## Deploy to Azure Kubernetes Service (AKS)

Manifests live under `k8s/` and are managed with **Kustomize**.

| File | Purpose |
|------|---------|
| `namespace.yaml` | `platform-management` namespace |
| `serviceaccount.yaml` | Workload identity-ready SA |
| `configmap.yaml` | Non-secret env (`SPRING_PROFILES_ACTIVE`, CORS, …) |
| `secret.yaml` | Template for `APP_AZURE_TENANT_ID` / `APP_API_CLIENT_ID` |
| `deployment.yaml` | 2 replicas, probes, resources, read-only root FS |
| `service.yaml` | ClusterIP → port 80 → container 8080 |
| `ingress.yaml` | Host-based Ingress (NGINX / AGIC annotations) |
| `hpa.yaml` | CPU/memory HPA (2–8 pods) |
| `networkpolicy.yaml` | Optional ingress/egress restrictions |
| `kustomization.yaml` | Image name/tag + resource list |

### 1. Point Kustomize at your ACR image

Edit `k8s/kustomization.yaml`:

```yaml
images:
  - name: platform-management-service
    newName: mycompanyacr.azurecr.io/platform-management-service
    newTag: "1.0.0"
```

### 2. Create Entra ID secret (do not commit real values)

```bash
kubectl apply -f k8s/namespace.yaml

kubectl create secret generic platform-management-service-secrets \
  --namespace platform-management \
  --from-literal=APP_AZURE_TENANT_ID='<tenant-guid>' \
  --from-literal=APP_API_CLIENT_ID='<api-app-client-id>'
```

### 3. Apply the stack

```bash
# Preview
kubectl kustomize k8s/

# Apply (skip secret.yaml if you created the secret above — remove it from
# kustomization resources, or use the helper script)
kubectl apply -k k8s/
```

Helper script (creates secret from env and rolls out):

```bash
export APP_AZURE_TENANT_ID=<tenant-guid>
export APP_API_CLIENT_ID=<api-app-client-id>
./scripts/deploy-aks.sh mycompanyacr.azurecr.io 1.0.0
```

### 4. Verify

```bash
kubectl -n platform-management get pods,svc,ingress,hpa
kubectl -n platform-management logs -l app.kubernetes.io/name=platform-management-service -f

# Port-forward for a quick API check
kubectl -n platform-management port-forward svc/platform-management-service 8080:80
curl -s http://localhost:8080/actuator/health
```

### Probes & security defaults

- **Startup / liveness / readiness** use Spring Boot actuator probes  
  (`/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`) — permitted without JWT  
- Non-root user `1001`, `readOnlyRootFilesystem`, dropped capabilities  
- Graceful shutdown (`server.shutdown=graceful`, 45s termination grace)

### Important notes for AKS

1. **Catalog store** — set `APP_AZURE_TABLE_ENABLED=true` and a Table endpoint (or connection string). The default in-memory store is **not** shared across replicas and is lost on restart.  
2. **Microsoft Entra ID** — pods must reach `login.microsoftonline.com` (HTTPS) to validate JWTs (JWKS / OIDC metadata).  
3. **Ingress** — set `spec.rules[].host` and TLS; use NGINX Ingress or Azure Application Gateway (AGIC).  
4. **Image pull** — prefer `az aks update --attach-acr` over long-lived `imagePullSecrets`.  
5. **Secrets** — prefer Azure Key Vault Provider for Secrets Store CSI or External Secrets Operator over plain `Secret` manifests in git.  
6. **Workload Identity** — grant the pod MI access to Table Storage, Graph, Blob, and Event Hubs as connectors are enabled.

---

## Notes

- Local/CI uses a **process-local in-memory** catalog; production should use **Azure Table Storage**.
- CORS origins are configurable via `APP_CORS_ALLOWED_ORIGINS` (comma-separated).
- Errors use Spring’s `ProblemDetail` (RFC 7807).
- Entitlement **check** is cache-first after the datasource-loading connector has run; list/CRUD read the durable store.
