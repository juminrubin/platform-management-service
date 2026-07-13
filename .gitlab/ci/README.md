# GitLab CI/CD — setup guide

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

Configure under **Settings → CI/CD → Variables**.  
Mark secrets as **Masked** + **Protected** (and limit protected branches/tags).

### Required for deploy

| Variable | Description |
|----------|-------------|
| `AZURE_CLIENT_ID` | Service principal used by the pipeline |
| `AZURE_CLIENT_SECRET` | SP secret |
| `AZURE_TENANT_ID` | Entra ID tenant |
| `AZURE_SUBSCRIPTION_ID` | Subscription for AKS |
| `AKS_RESOURCE_GROUP` | AKS resource group |
| `AKS_CLUSTER_NAME` | AKS cluster name |
| `APP_AZURE_TENANT_ID` | Tenant ID injected into the app Secret |
| `APP_AZURE_API_CLIENT_ID` | API app client ID (JWT `aud`) |

### Optional

| Variable | Description |
|----------|-------------|
| `ACR_NAME` | Also push image to this Azure Container Registry |
| `ACR_LOGIN_SERVER` | Override (default: `{ACR_NAME}.azurecr.io`) |
| `KUBE_NAMESPACE` | Default `participant-api` |
| `DEPLOY_HOST` | Ingress host (set per environment if needed) |
| `SKIP_TRIVY` | `true` to skip image scan |
| `CI_DEPLOY_USER` / `CI_DEPLOY_PASSWORD` | Deploy token if AKS pulls from GitLab Registry |

## Azure prerequisites

1. **Service principal** with:
   - `Azure Kubernetes Service Cluster User Role` (or broader) on the AKS cluster
   - If using ACR push: `AcrPush` on the registry
2. **AKS → ACR** attachment (recommended over pull secrets):

   ```bash
   az aks update -n <aks> -g <rg> --attach-acr <acr-name>
   ```

3. **RBAC**: SP can `get-credentials` and apply workloads in the target namespace.

## Recommended flow

1. Open MR → compile + test (+ optional Trivy).  
2. Merge to default branch → image build, scan, **auto deploy staging**.  
3. Tag release: `git tag v1.0.0 && git push origin v1.0.0` → package + **manual** production deploy.

## Image tags

| Tag | Meaning |
|-----|---------|
| `$CI_COMMIT_SHORT_SHA` | Immutable (used for deploy) |
| `$CI_COMMIT_REF_SLUG` | Branch / tag slug |
| `latest` | Default branch only |
| `1.0.0` / `v1.0.0` | Git tags |

## Local dry-run of deploy script

```bash
export DEPLOY_IMAGE=myacr.azurecr.io/participant-service-api
export DEPLOY_IMAGE_TAG=abc1234
export DEPLOY_ENV=staging
export APP_AZURE_TENANT_ID=...
export APP_AZURE_API_CLIENT_ID=...
export KUBE_NAMESPACE=participant-api
# kubectl already pointed at cluster
./scripts/ci-deploy.sh
```

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Kaniko auth failure | Ensure GitLab runner can reach `$CI_REGISTRY`; project Container Registry enabled |
| ACR push fails | SP needs `AcrPush`; check `ACR_NAME` / client secret |
| `az aks get-credentials` fails | SP needs cluster user role; correct RG/name/subscription |
| ImagePullBackOff | Attach ACR to AKS, or configure `CI_DEPLOY_*` pull secret |
| Pod CrashLoop (JWT) | Verify `APP_AZURE_*` secrets; pod must reach `login.microsoftonline.com` |
| Trivy fails pipeline | Fix CRITICAL CVEs or set job `allow_failure` / `SKIP_TRIVY` |
