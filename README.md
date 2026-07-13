# Participant Service API

Kotlin + Spring Boot REST API for **Participants**, **Service Offerings**, and **Participant Service Entitlements**.

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
src/main/kotlin/com/example/participantapi/
  config/          # Security (Microsoft JWT), CORS, app properties
  controller/      # REST endpoints
  domain/          # JPA entities & enums
  dto/             # Request/response models
  exception/       # API errors (RFC 7807 ProblemDetail)
  repository/      # Spring Data JPA
  service/         # Business logic
src/main/resources/
  application.yml              # Default: open API + H2
  application-secure.yml       # Microsoft Entra ID JWT
  application-k8s.yml          # Container / AKS runtime
  db/migration/
    V1__init_schema.sql
    V2__seed_data.sql
Dockerfile
.dockerignore
k8s/                           # AKS manifests (Kustomize)
scripts/                       # Build/push to ACR + deploy helpers
.github/workflows/             # Optional GitHub Actions → ACR
.gitlab-ci.yml                 # GitLab CI/CD (Maven → image → AKS)
.gitlab/ci/README.md           # GitLab variable & Azure setup guide
```

---

## Quick start (no auth)

Requires **JDK 17+** and **Maven 3.9+**.

```bash
cd participant-service-api
mvn spring-boot:run
```

Or with an explicit local profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

- API base: `http://localhost:8080/api/v1`
- Health: `http://localhost:8080/actuator/health`
- H2 console: `http://localhost:8080/h2-console`  
  JDBC URL: `jdbc:h2:mem:participantdb` · User: `sa` · Password: _(empty)_

---

## API endpoints

All resources support standard CRUD under `/api/v1`.

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

## Microsoft Entra ID authentication

The app acts as an **OAuth2 resource server**. Clients obtain a token from Microsoft and call the API with:

```http
Authorization: Bearer <access_token>
```

### 1. Azure app registration (API)

1. Azure Portal → **Microsoft Entra ID** → **App registrations** → **New registration**
2. Note **Application (client) ID** and **Directory (tenant) ID**
3. **Expose an API**
   - Set Application ID URI (e.g. `api://<client-id>`)
   - Add a scope, e.g. `access_as_user`
4. Optional: **App roles** for application permissions (client credentials)

### 2. Client application

Register a client (SPA, daemon, or another API) and grant it the API scope / role.

### 3. Run with the secure profile

```bash
export AZURE_TENANT_ID=<tenant-guid>
export AZURE_API_CLIENT_ID=<api-app-client-id>
# optional: require a specific scope/role from the token
export AZURE_REQUIRED_SCOPE=access_as_user

mvn spring-boot:run -Dspring-boot.run.profiles=secure
```

Configuration is in `application-secure.yml`:

- `issuer-uri`: `https://login.microsoftonline.com/{tenant}/v2.0`
- `audiences`: API app client ID (`aud` claim validation)
- JWT authorities:
  - `scp` / `scope` → `SCOPE_*`
  - `roles` → `ROLE_*`

### 4. Call a protected endpoint

```bash
TOKEN=$(curl -s -X POST "https://login.microsoftonline.com/$AZURE_TENANT_ID/oauth2/v2.0/token" \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET" \
  -d "scope=api://$AZURE_API_CLIENT_ID/.default" \
  -d "grant_type=client_credentials" | jq -r .access_token)

curl -s http://localhost:8080/api/v1/participants \
  -H "Authorization: Bearer $TOKEN" | jq
```

> Default / `local` profiles set `app.security.permit-all=true` so you can develop without Azure. **Never deploy with permit-all in production.**

---

## Build & test

```bash
mvn clean verify
mvn package
java -jar target/participant-service-api-1.0.0-SNAPSHOT.jar
java -jar target/participant-service-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=secure
```

---

## Container image

Multi-stage `Dockerfile`:

1. **Build** — `maven:3.9.9-eclipse-temurin-17` packages the fat JAR  
2. **Runtime** — `eclipse-temurin:17-jre-jammy`, non-root UID `1001`, port `8080`  
3. Default profiles: `SPRING_PROFILES_ACTIVE=k8s,secure`

### Local build & run

```bash
docker build -t participant-service-api:1.0.0 .

# Local smoke test without Entra ID (override profiles)
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=k8s,local \
  -e APP_SECURITY_PERMIT_ALL=true \
  participant-service-api:1.0.0
```

For a realistic secure container:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=k8s,secure \
  -e AZURE_TENANT_ID=<tenant-guid> \
  -e AZURE_API_CLIENT_ID=<api-app-client-id> \
  participant-service-api:1.0.0
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

Configure at least these CI/CD variables: `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `AKS_RESOURCE_GROUP`, `AKS_CLUSTER_NAME`, `APP_AZURE_TENANT_ID`, `APP_AZURE_API_CLIENT_ID`. Set `ACR_NAME` to also publish to Azure Container Registry.

---

## Deploy to Azure Kubernetes Service (AKS)

Manifests live under `k8s/` and are managed with **Kustomize**.

| File | Purpose |
|------|---------|
| `namespace.yaml` | `participant-api` namespace |
| `serviceaccount.yaml` | Workload identity-ready SA |
| `configmap.yaml` | Non-secret env (`SPRING_PROFILES_ACTIVE`, CORS, …) |
| `secret.yaml` | Template for `AZURE_TENANT_ID` / `AZURE_API_CLIENT_ID` |
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
  - name: participant-service-api
    newName: mycompanyacr.azurecr.io/participant-service-api
    newTag: "1.0.0"
```

### 2. Create Entra ID secret (do not commit real values)

```bash
kubectl apply -f k8s/namespace.yaml

kubectl create secret generic participant-service-api-secrets \
  --namespace participant-api \
  --from-literal=AZURE_TENANT_ID='<tenant-guid>' \
  --from-literal=AZURE_API_CLIENT_ID='<api-app-client-id>'
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
export AZURE_TENANT_ID=<tenant-guid>
export AZURE_API_CLIENT_ID=<api-app-client-id>
./scripts/deploy-aks.sh mycompanyacr.azurecr.io 1.0.0
```

### 4. Verify

```bash
kubectl -n participant-api get pods,svc,ingress,hpa
kubectl -n participant-api logs -l app.kubernetes.io/name=participant-service-api -f

# Port-forward for a quick API check
kubectl -n participant-api port-forward svc/participant-service-api 8080:80
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
- CORS origins are configurable via `CORS_ALLOWED_ORIGINS` (comma-separated).
- Errors use Spring’s `ProblemDetail` (RFC 7807).
