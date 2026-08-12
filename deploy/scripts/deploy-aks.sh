#!/usr/bin/env bash
# Apply Kubernetes manifests to an AKS cluster (via kubeconfig / az aks get-credentials).
#
# Usage:
#   export APP_AZURE_TENANT_ID=...
#   export APP_API_CLIENT_ID=...
#   ./scripts/deploy-aks.sh [acr-login-server] [tag]
#
# Example:
#   ./scripts/deploy-aks.sh mycompanyacr.azurecr.io 1.0.0

set -euo pipefail

ACR_LOGIN_SERVER="${1:-YOUR_ACR_NAME.azurecr.io}"
TAG="${2:-1.0.0}"
NAMESPACE="platform-management"
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

APP_API_CLIENT_ID="${APP_API_CLIENT_ID:-}"
if [[ -z "${APP_AZURE_TENANT_ID:-}" || -z "${APP_API_CLIENT_ID}" ]]; then
  echo "Set APP_AZURE_TENANT_ID and APP_API_CLIENT_ID before deploying." >&2
  exit 1
fi

echo "==> Applying base resources (namespace, SA, config, workload)"
kubectl apply -f "${DEPLOY_DIR}/k8s/namespace.yaml"
kubectl apply -f "${DEPLOY_DIR}/k8s/serviceaccount.yaml"
kubectl apply -f "${DEPLOY_DIR}/k8s/configmap.yaml"

echo "==> Creating / updating secrets"
kubectl create secret generic platform-management-service-secrets \
  --namespace "${NAMESPACE}" \
  --from-literal=APP_AZURE_TENANT_ID="${APP_AZURE_TENANT_ID}" \
  --from-literal=APP_API_CLIENT_ID="${APP_API_CLIENT_ID}" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "==> Deploying application image ${ACR_LOGIN_SERVER}/platform-management-service:${TAG}"
# Patch image via kustomize build in a temp overlay
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

cp -R "${DEPLOY_DIR}/k8s/." "${TMP_DIR}/"
# Remove secret template / optional UI so we don't overwrite live secret with placeholders
rm -f "${TMP_DIR}/secret.yaml" "${TMP_DIR}/ui-deployment.yaml"

cat > "${TMP_DIR}/kustomization.yaml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: ${NAMESPACE}
resources:
  - deployment.yaml
  - service.yaml
  - ingress.yaml
  - hpa.yaml
images:
  - name: platform-management-service
    newName: ${ACR_LOGIN_SERVER}/platform-management-service
    newTag: "${TAG}"
EOF

kubectl apply -k "${TMP_DIR}"

echo "==> Waiting for rollout"
kubectl -n "${NAMESPACE}" rollout status deployment/platform-management-service --timeout=180s

echo "==> Pods"
kubectl -n "${NAMESPACE}" get pods -l app.kubernetes.io/name=platform-management-service

echo "==> Done"
