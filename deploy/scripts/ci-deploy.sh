#!/usr/bin/env bash
# Deploy platform-management-service to AKS from GitLab CI (or locally with kubectl configured).
#
# Expected environment:
#   DEPLOY_IMAGE          full image repo without tag (e.g. myacr.azurecr.io/platform-management-service)
#   DEPLOY_IMAGE_TAG      image tag (e.g. commit SHA)
#   KUBE_NAMESPACE        Kubernetes namespace (default: platform-management)
#   DEPLOY_ENV            staging | production | review
#   APP_AZURE_TENANT_ID   Entra tenant for the Spring app
#   APP_AZURE_API_CLIENT_ID  API app client id (JWT audience)
#   DEPLOY_HOST           optional Ingress host override
#   CI_PROJECT_DIR        repo root (GitLab sets this)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="${CI_PROJECT_DIR:-$(cd "${DEPLOY_DIR}/.." && pwd)}"
NAMESPACE="${KUBE_NAMESPACE:-platform-management}"
DEPLOY_ENV="${DEPLOY_ENV:-staging}"
IMAGE_REPO="${DEPLOY_IMAGE:?DEPLOY_IMAGE is required (from package:image dotenv)}"
IMAGE_TAG="${DEPLOY_IMAGE_TAG:?DEPLOY_IMAGE_TAG is required}"
APP_TENANT="${APP_AZURE_TENANT_ID:-}"
APP_CLIENT="${APP_AZURE_API_CLIENT_ID:-}"

if [[ -z "${APP_TENANT}" || -z "${APP_CLIENT}" ]]; then
  echo "ERROR: Set APP_AZURE_TENANT_ID and APP_AZURE_API_CLIENT_ID (Spring app JWT settings)." >&2
  exit 1
fi

echo "==> Deploy env:     ${DEPLOY_ENV}"
echo "==> Namespace:      ${NAMESPACE}"
echo "==> Image:          ${IMAGE_REPO}:${IMAGE_TAG}"
echo "==> Repo root:      ${REPO_ROOT}"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

# Copy API manifests; exclude secret template so CI-managed secret is not overwritten
cp -R "${DEPLOY_DIR}/k8s/." "${TMP_DIR}/"
rm -f "${TMP_DIR}/secret.yaml" "${TMP_DIR}/kustomization.yaml" "${TMP_DIR}/networkpolicy.yaml" "${TMP_DIR}/ui-deployment.yaml"

# Optional Ingress host patch
if [[ -n "${DEPLOY_HOST:-}" ]]; then
  echo "==> Setting Ingress host to ${DEPLOY_HOST}"
  # Portable-ish sed for Alpine/GNU
  sed -i.bak "s/host: .*/host: ${DEPLOY_HOST}/" "${TMP_DIR}/ingress.yaml" || \
    sed -i '' "s/host: .*/host: ${DEPLOY_HOST}/" "${TMP_DIR}/ingress.yaml"
fi

# Environment-specific replica / resource tweaks
case "${DEPLOY_ENV}" in
  production)
    # Ensure at least 2 replicas in production (already default in deployment.yaml)
    ;;
  staging|review)
    # Staging can run leaner
    if command -v yq >/dev/null 2>&1; then
      yq -i '.spec.replicas = 1' "${TMP_DIR}/deployment.yaml"
    else
      sed -i.bak 's/replicas: 2/replicas: 1/' "${TMP_DIR}/deployment.yaml" || \
        sed -i '' 's/replicas: 2/replicas: 1/' "${TMP_DIR}/deployment.yaml"
    fi
    ;;
esac

cat > "${TMP_DIR}/kustomization.yaml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: ${NAMESPACE}
resources:
  - namespace.yaml
  - serviceaccount.yaml
  - configmap.yaml
  - deployment.yaml
  - service.yaml
  - ingress.yaml
  - hpa.yaml
images:
  - name: platform-management-service
    newName: ${IMAGE_REPO}
    newTag: "${IMAGE_TAG}"
labels:
  - pairs:
      app.kubernetes.io/managed-by: gitlab-ci
      app.kubernetes.io/part-of: platform-management
      app.kubernetes.io/environment: ${DEPLOY_ENV}
    includeSelectors: false
EOF

echo "==> Ensuring namespace exists"
kubectl apply -f "${TMP_DIR}/namespace.yaml"

echo "==> Upserting application secrets (Entra ID → APP_* env)"
kubectl create secret generic platform-management-service-secrets \
  --namespace "${NAMESPACE}" \
  --from-literal=APP_AZURE_TENANT_ID="${APP_TENANT}" \
  --from-literal=APP_AZURE_API_CLIENT_ID="${APP_CLIENT}" \
  --dry-run=client -o yaml | kubectl apply -f -

# If the image is on GitLab Registry, ensure a pull secret exists (optional)
if [[ -n "${CI_REGISTRY:-}" && "${IMAGE_REPO}" == "${CI_REGISTRY}"* ]]; then
  if [[ -n "${CI_DEPLOY_USER:-}" && -n "${CI_DEPLOY_PASSWORD:-}" ]]; then
    echo "==> Upserting GitLab registry pull secret"
    kubectl create secret docker-registry gitlab-registry \
      --namespace "${NAMESPACE}" \
      --docker-server="${CI_REGISTRY}" \
      --docker-username="${CI_DEPLOY_USER}" \
      --docker-password="${CI_DEPLOY_PASSWORD}" \
      --dry-run=client -o yaml | kubectl apply -f -

    # Patch service account to use pull secret
    kubectl patch serviceaccount platform-management-service \
      --namespace "${NAMESPACE}" \
      --type merge \
      -p '{"imagePullSecrets":[{"name":"gitlab-registry"}]}' || true
  else
    echo "==> WARNING: Image is on GitLab Registry but CI_DEPLOY_USER/PASSWORD not set."
    echo "    Prefer ACR with AKS attach-acr, or set deploy token variables."
  fi
fi

echo "==> Applying Kustomize manifests"
kubectl apply -k "${TMP_DIR}"

echo "==> Waiting for rollout"
kubectl -n "${NAMESPACE}" rollout status deployment/platform-management-service --timeout=180s

echo "==> Status"
kubectl -n "${NAMESPACE}" get pods,svc,ingress,hpa -l app.kubernetes.io/name=platform-management-service

echo "==> Deploy complete (${DEPLOY_ENV})"
