# Platform Management Service — API (backend)

Kotlin + Spring Boot REST API (**Platform Management Service**) for **Participants**, **Service Offerings**, and **Participant Service Entitlements**.

| Concern | Choice |
|--------|--------|
| Language | Kotlin **2.3.21** |
| Framework | Spring Boot **4.1.0** |
| Build | Maven |
| Runtime | **Java 17** |
| Database | H2 (in-memory) |
| Schema | Flyway |
| Auth | Microsoft Entra ID (Azure AD) JWT resource server |
| Packaging | Docker multi-stage image |
| Deploy | Kubernetes manifests for Azure Kubernetes Service (AKS) |

---

## Project layout

```
src/main/kotlin/com/example/platformmanagement/
  config/          # Security (Microsoft JWT), CORS, app properties
  controller/      # REST endpoints
  domain/          # JPA entities & enums
  dto/             # Request/response models
  exception/       # API errors (RFC 7807 ProblemDetail)
  repository/      # Spring Data JPA
  service/         # Business logic
src/main/resources/
  application.yml              # App + Microsoft Entra ID JWT (always on)
  application-k8s.yml          # Container / AKS runtime extras
  db/migration/
    V1__init_schema.sql
    V2__seed_data.sql
Dockerfile
.dockerignore
k8s/                           # AKS manifests (Kustomize)
scripts/                       # ACR/AKS helpers + Entra token scripts (human / MI)
.github/workflows/             # Optional GitHub Actions → ACR
.gitlab-ci.yml                 # GitLab CI/CD (Maven → image → AKS)
.gitlab/ci/README.md           # GitLab variable & Azure setup guide
```

---

## Quick start

Requires **JDK 17+**, **Maven 3.9+**, and Entra app settings:

```bash
export APP_AZURE_TENANT_ID=<tenant-guid>
export APP_AZURE_API_CLIENT_ID=<api-app-client-id>
# optional: export APP_AZURE_REQUIRED_SCOPE=access_as_user

cd platform-management-service
mvn spring-boot:run
```

Security is **always on** (configured in `application.yml`). Every `/api/**` call needs:

```http
Authorization: Bearer <entra-access-token>
```

Obtain a token with `./scripts/get-token-human.sh` or `./scripts/get-token-mi.sh`.  
See [Microsoft Entra ID authentication](#microsoft-entra-id-authentication--authorization).

Optional verbose logging profile (security unchanged):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

- API base: `http://localhost:8080/api/v1`
- Auth probe: `http://localhost:8080/api/v1/auth/me`
- Health: `http://localhost:8080/actuator/health` (public)
- H2 console: `http://localhost:8080/h2-console` (**localhost only** — remote clients get 403)  
  JDBC URL: `jdbc:h2:mem:platformmanagementdb` · User: `sa` · Password: _(empty)_

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
| `DELETE` | `/api/v1/entitlements/{id}` | Delete |

```bash
curl -s -X POST http://localhost:8080/api/v1/entitlements \
  -H 'Content-Type: application/json' \
  -d '{
    "participantId": "acme-corp",
    "serviceOfferingId": "gpt-5.1-mini",
    "status": "ACTIVE",
    "validFrom": "2025-01-01",
    "validTo": "2025-12-31",
    "config": "{\"max_tpm\":1000,\"max_rpm\":20}",
    "notes": "Pilot entitlement"
  }' | jq
```

### Caller identities

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/caller-identities` | List (`?participantId=&status=`) |
| `GET` | `/api/v1/caller-identities/{id}` | Get by id |
| `POST` | `/api/v1/caller-identities` | Register identity (email / SP client id / UAMI) |
| `PUT` | `/api/v1/caller-identities/{id}` | Update status |
| `DELETE` | `/api/v1/caller-identities/{id}` | Delete |

### Consumptions

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/consumptions` | List (`?participantCallerIdentityId=&serviceOfferingId=`) |
| `GET` | `/api/v1/consumptions/{id}` | Get by id |
| `POST` | `/api/v1/consumptions` | Record usage JSON |
| `DELETE` | `/api/v1/consumptions/{id}` | Delete |

### Entitlement check (Entitlement.Reader)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/entitlements/check` | Check entitlement by caller identity + service offering |

```bash
# By principal string (email / Entra client id / managed identity id)
curl -s 'http://localhost:8080/api/v1/entitlements/check?callerIdentity=alice@acme.example&serviceOfferingId=gpt-5.1' \
  -H "Authorization: Bearer $TOKEN" | jq

# Or by internal caller-identity row id
curl -s 'http://localhost:8080/api/v1/entitlements/check?participantCallerIdentityId=c1111111-1111-1111-1111-111111111111&serviceOfferingId=gpt-5.1' \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Auth (Microsoft Entra principal)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/auth/me` | Current caller from the Bearer JWT (subject, scopes, roles, tenant) |

Seed data (from Flyway `V2__seed_data.sql`) is loaded on startup so list endpoints return sample rows immediately.

---

## Domain model

```
Participant 1──* ParticipantServiceEntitlement *──1 ServiceOffering
     │
     └──* ParticipantCallerIdentity 1──* ParticipantCallConsumption *──1 ServiceOffering
```

| Entity | Key fields |
|--------|------------|
| **Participant** | `id` (VARCHAR), `name`, `contact`, `status` |
| **ServiceOffering** | `id` (business key, e.g. `gpt-5.1`), `name`, `category`, `config` (JSON), `active` |
| **ParticipantServiceEntitlement** | participant ↔ offering, validity, `config` (JSON limits), status |
| **ParticipantCallerIdentity** | participant ↔ `callerIdentity` (email / Entra client id) |
| **ParticipantCallConsumption** | caller identity ↔ offering, `consumptionData` (JSON tokens) |

Schema is owned by **Flyway** (`ddl-auto: validate`). Hibernate never mutates the schema.

---

## Microsoft Entra ID authentication & authorization

The API is an **OAuth2 resource server**. Spring Security validates **Microsoft Entra ID** access tokens (issuer + signature + audience) and authorizes callers from the JWT **`roles`** claim (Entra **app roles**).

| Mode | Profile | Behaviour |
|------|---------|-----------|
| Local / default | _(none)_ | Entra JWT + app roles required (`application.yml`) |
| Local verbose logs | `local` | Same security, DEBUG logging |
| AKS / Docker | `k8s` | Same security + container probes / no H2 console |

```http
Authorization: Bearer <access_token>
```

Implementation touchpoints:

| Piece | Location |
|-------|----------|
| Security filter chain + Entra claim → authority mapping | `config/SecurityConfig.kt` |
| Role name constants | `security/AppRoles.kt` |
| SpEL helper (`@authz.hasAnyRole(...)`) | `security/Authz.kt` |
| Method security on controllers | `@PreAuthorize` on controller methods |
| Permit-all / optional scope gate / CORS | `config/AppSecurityProperties.kt` + `app.security.*` |
| Issuer + audiences | `application.yml` (`APP_AZURE_TENANT_ID`, `APP_AZURE_API_CLIENT_ID`) |
| Authenticated principal probe | `GET /api/v1/auth/me` |
| K8s env (`SPRING_PROFILES_ACTIVE=k8s`) | `k8s/configmap.yaml` + secrets |

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

**Optional outer gate:** `APP_AZURE_REQUIRED_SCOPE=access_as_user` requires a delegated scope (or matching app role name) on every `/api/**` call. Prefer **app roles for authorization** and keep `required-scope` empty unless you need an extra tenant-wide gate.

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
| Caller identities GET | ✓ | ✓ | | |
| Caller identities write | ✓ | | | |
| Entitlements GET list/id | ✓ | ✓ | | |
| Entitlements write | ✓ | | | |
| `GET /entitlements/check` | ✓ | ✓ | ✓ | |
| Consumptions GET | ✓ | ✓ | | |
| Consumptions DELETE | ✓ | | | |
| `POST /consumptions` | ✓ | | | ✓ |
| `GET /auth/me` | ✓ | ✓ | ✓ | ✓ |

---

### 1. Register the API (resource) in Entra

1. Azure Portal → **Microsoft Entra ID** → **App registrations** → **New registration**  
   Name e.g. `platform-management-service`. Account type: single tenant (or as required).
2. Note:
   - **Application (client) ID** → `APP_AZURE_API_CLIENT_ID`
   - **Directory (tenant) ID** → `APP_AZURE_TENANT_ID`
3. **Expose an API**
   - Application ID URI: `api://<APP_AZURE_API_CLIENT_ID>` (or a verified custom URI)
   - Add a **delegated** scope for human clients, e.g.:
     - Scope name: `access_as_user`
     - Who can consent: Admins only (or admins + users)
     - Full scope: `api://<APP_AZURE_API_CLIENT_ID>/access_as_user`
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
  "aud": "<APP_AZURE_API_CLIENT_ID>",
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
   - SPA: add redirect URI (e.g. `http://localhost:5173`).
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
API_APP_ID="<APP_AZURE_API_CLIENT_ID>"                 # API app registration client id
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
  "aud": "<APP_AZURE_API_CLIENT_ID>",
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
export APP_AZURE_API_CLIENT_ID="<api-app-client-id>"

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
  --resource "api://${APP_AZURE_API_CLIENT_ID}" \
  --query accessToken -o tsv)
# Expect roles: System.Maintainer or Entitlement.Reader; often scp: access_as_user
```

**Authorization code + PKCE (SPA)** — use **MSAL** with scope  
`api://${APP_AZURE_API_CLIENT_ID}/access_as_user`. Do **not** use ROPC in production.

#### Call the API as a human

```bash
curl -s http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN" | jq

curl -s http://localhost:8080/api/v1/participants \
  -H "Authorization: Bearer $TOKEN" | jq

curl -s "http://localhost:8080/api/v1/entitlements/check?callerIdentity=alice@acme.example&serviceOfferingId=gpt-5.1" \
  -H "Authorization: Bearer $TOKEN" | jq
```

---

### 6. Generate an access token — technical user (Managed Identity)

Prerequisites: MI has app role assignment on the API (step 4). Scope for application permissions is always **`/.default`**.

#### Scripts (recommended)

```bash
export APP_AZURE_API_CLIENT_ID="<api-app-client-id>"

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
MI_RESOURCE="api://${APP_AZURE_API_CLIENT_ID}"
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
    "participantCallerIdentityId": "c1111111-1111-1111-1111-111111111111",
    "serviceOfferingId": "gpt-5.1",
    "consumptionData": "{\"input_token\":120,\"output_token\":40}",
    "consumedAt": "2024-07-01T12:00:00Z"
  }' | jq
```

---

### 7. Run the API

```bash
export APP_AZURE_TENANT_ID=<tenant-guid>
export APP_AZURE_API_CLIENT_ID=<api-app-client-id>
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
| App fails to start | `APP_AZURE_TENANT_ID` / `APP_AZURE_API_CLIENT_ID` not set |
| 403 with valid login | App role missing on token — check group→role assignment (human) or MI app role assignment (tech) |
| Token has `groups` but no `roles` | You assigned the group as a member without selecting an **app role**, or token was requested for the wrong resource (e.g. Graph / ARM) |
| MI token has no `roles` | App role not assigned to MI principal, or scope was not `api://…/.default` |
| Human token has `scp` but empty `roles` | User not in a group that has an app role on the **API** enterprise application |
| `aud` mismatch | Request token with resource/scope for **this** API (`api://{APP_AZURE_API_CLIENT_ID}/…`) |

---

## Build & test

```bash
mvn clean verify
mvn package

export APP_AZURE_TENANT_ID=<tenant-guid>
export APP_AZURE_API_CLIENT_ID=<api-app-client-id>
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
  -e APP_AZURE_API_CLIENT_ID=<api-app-client-id> \
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

Configure at least these CI/CD variables: `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` (deploy SP login), plus `APP_AZURE_TENANT_ID`, `APP_AZURE_API_CLIENT_ID` (injected into the app), `AKS_RESOURCE_GROUP`, `AKS_CLUSTER_NAME`. Set `ACR_NAME` to also publish to Azure Container Registry.

---

## Deploy to Azure Kubernetes Service (AKS)

Manifests live under `k8s/` and are managed with **Kustomize**.

| File | Purpose |
|------|---------|
| `namespace.yaml` | `platform-management` namespace |
| `serviceaccount.yaml` | Workload identity-ready SA |
| `configmap.yaml` | Non-secret env (`SPRING_PROFILES_ACTIVE`, CORS, …) |
| `secret.yaml` | Template for `APP_AZURE_TENANT_ID` / `APP_AZURE_API_CLIENT_ID` |
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
  --from-literal=APP_AZURE_API_CLIENT_ID='<api-app-client-id>'
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
export APP_AZURE_API_CLIENT_ID=<api-app-client-id>
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
- H2 console disabled under profile `k8s`

### Important notes for AKS

1. **H2 is in-memory** — data is lost on pod restart and is **not shared across replicas**. This sample is fine for demos; for production replace H2 with Azure Database for PostgreSQL (or similar) and a single shared schema.  
2. **Microsoft Entra ID** — pods must reach `login.microsoftonline.com` (HTTPS) to validate JWTs (JWKS / OIDC metadata).  
3. **Ingress** — set `spec.rules[].host` and TLS; use NGINX Ingress or Azure Application Gateway (AGIC).  
4. **Image pull** — prefer `az aks update --attach-acr` over long-lived `imagePullSecrets`.  
5. **Secrets** — prefer Azure Key Vault Provider for Secrets Store CSI or External Secrets Operator over plain `Secret` manifests in git.

---

## Notes

- H2 runs in **PostgreSQL compatibility mode** for portable SQL in Flyway scripts.
- CORS origins are configurable via `APP_CORS_ALLOWED_ORIGINS` (comma-separated).
- Errors use Spring’s `ProblemDetail` (RFC 7807).
