#!/usr/bin/env bash
# Apply Kubernetes manifests to an AKS cluster (via kubeconfig / az aks get-credentials).
#
# Usage:
#   export AZURE_TENANT_ID=...
#   export AZURE_API_CLIENT_ID=...
#   ./scripts/deploy-aks.sh [acr-login-server] [tag]
#
# Example:
#   ./scripts/deploy-aks.sh mycompanyacr.azurecr.io 1.0.0

set -euo pipefail

ACR_LOGIN_SERVER="${1:-YOUR_ACR_NAME.azurecr.io}"
TAG="${2:-1.0.0}"
NAMESPACE="participant-api"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "${AZURE_TENANT_ID:-}" || -z "${AZURE_API_CLIENT_ID:-}" ]]; then
  echo "Set AZURE_TENANT_ID and AZURE_API_CLIENT_ID before deploying." >&2
  exit 1
fi

echo "==> Applying base resources (namespace, SA, config, workload)"
kubectl apply -f "${ROOT_DIR}/k8s/namespace.yaml"
kubectl apply -f "${ROOT_DIR}/k8s/serviceaccount.yaml"
kubectl apply -f "${ROOT_DIR}/k8s/configmap.yaml"

echo "==> Creating / updating secrets"
kubectl create secret generic participant-service-api-secrets \
  --namespace "${NAMESPACE}" \
  --from-literal=AZURE_TENANT_ID="${AZURE_TENANT_ID}" \
  --from-literal=AZURE_API_CLIENT_ID="${AZURE_API_CLIENT_ID}" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "==> Deploying application image ${ACR_LOGIN_SERVER}/participant-service-api:${TAG}"
# Patch image via kustomize build in a temp overlay
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

cp -R "${ROOT_DIR}/k8s/." "${TMP_DIR}/"
# Remove secret template so we don't overwrite live secret with placeholders
rm -f "${TMP_DIR}/secret.yaml"

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
  - name: participant-service-api
    newName: ${ACR_LOGIN_SERVER}/participant-service-api
    newTag: "${TAG}"
EOF

kubectl apply -k "${TMP_DIR}"

echo "==> Waiting for rollout"
kubectl -n "${NAMESPACE}" rollout status deployment/participant-service-api --timeout=180s

echo "==> Pods"
kubectl -n "${NAMESPACE}" get pods -l app.kubernetes.io/name=participant-service-api

echo "==> Done"
