#!/usr/bin/env bash
# Build the container image and push it to Azure Container Registry (ACR).
#
# Prerequisites:
#   - Docker (or compatible buildx)
#   - Azure CLI logged in: az login
#   - ACR name and resource group
#
# Usage:
#   ./scripts/build-and-push-acr.sh <acr-name> [tag]
#
# Example:
#   ./scripts/build-and-push-acr.sh mycompanyacr 1.0.0

set -euo pipefail

ACR_NAME="${1:-}"
TAG="${2:-1.0.0}"
IMAGE_NAME="platform-management-service"

if [[ -z "${ACR_NAME}" ]]; then
  echo "Usage: $0 <acr-name> [tag]" >&2
  exit 1
fi

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "${DEPLOY_DIR}/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"

LOGIN_SERVER="$(az acr show --name "${ACR_NAME}" --query loginServer -o tsv)"
FULL_IMAGE="${LOGIN_SERVER}/${IMAGE_NAME}:${TAG}"

echo "==> Logging in to ACR: ${ACR_NAME}"
az acr login --name "${ACR_NAME}"

echo "==> Building API image ${FULL_IMAGE} (context: backend/)"
docker build \
  --platform linux/amd64 \
  -f "${BACKEND_DIR}/Dockerfile" \
  -t "${FULL_IMAGE}" \
  -t "${LOGIN_SERVER}/${IMAGE_NAME}:latest" \
  "${BACKEND_DIR}"

echo "==> Pushing ${FULL_IMAGE}"
docker push "${FULL_IMAGE}"
docker push "${LOGIN_SERVER}/${IMAGE_NAME}:latest"

echo "==> Done"
echo "    Image: ${FULL_IMAGE}"
echo "    Update k8s/kustomization.yaml images.newName / newTag, then:"
echo "      kubectl apply -k k8s/"
