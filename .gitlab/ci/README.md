# Platform Management Service — GitLab CI/CD setup

Pipeline definition: [`.gitlab-ci.yml`](../../.gitlab-ci.yml)  
Deploy script: [`scripts/ci-deploy.sh`](../../scripts/ci-deploy.sh)

## Pipeline overview

```
validate:compile → test:unit → package:image → security:trivy → deploy:staging
                                                              ↘ deploy:production (manual, tags)
```

| Job | When | What |
|-----|------|------|
| `validate:compile` | MR, branch, tag | `mvn compile` |
| `test:unit` | MR, branch, tag | `mvn verify` + JUnit report |
| `package:image` | default branch, tag, MR | Kaniko build → GitLab Registry (+ optional ACR) |
| `security:trivy` | default branch, tag; manual on MR | CRITICAL/HIGH scan |
| `deploy:staging` | **auto** on default branch | AKS rollout |
| `deploy:production` | **manual** on semver tags `v1.2.3` | AKS rollout |

## CI/CD variables

### Azure deploy login (service principal)

| Variable | Purpose |
|----------|---------|
| `AZURE_CLIENT_ID` | SP client ID for `az login` |
| `AZURE_CLIENT_SECRET` | SP secret (masked, protected) |
| `AZURE_TENANT_ID` | Entra tenant for the deploy SP |
| `AZURE_SUBSCRIPTION_ID` | Azure subscription |
| `AKS_RESOURCE_GROUP` | AKS resource group |
| `AKS_CLUSTER_NAME` | AKS cluster name |

### Spring app secrets (injected into the cluster)

| Variable | Purpose |
|----------|---------|
| `APP_AZURE_TENANT_ID` | Tenant ID used by Platform Management Service JWT validation |
| `APP_AZURE_API_CLIENT_ID` | API app client ID (JWT `aud`) |

### Optional

| Variable | Purpose |
|----------|---------|
| `ACR_NAME` | Also push image to Azure Container Registry |
| `ACR_LOGIN_SERVER` | e.g. `myacr.azurecr.io` |
| `KUBE_NAMESPACE` | default: `platform-management` |
| `DEPLOY_HOST` | Ingress host override |
| `SKIP_TRIVY` | `"true"` to skip vulnerability scan |

## Local deploy smoke test

```bash
export APP_AZURE_TENANT_ID=...
export APP_AZURE_API_CLIENT_ID=...
export DEPLOY_IMAGE=registry.example.com/platform-management-service
export DEPLOY_IMAGE_TAG=dev
./scripts/ci-deploy.sh
```

Or:

```bash
export APP_AZURE_TENANT_ID=...
export APP_AZURE_API_CLIENT_ID=...
./scripts/deploy-aks.sh mycompanyacr.azurecr.io 1.0.0
```
