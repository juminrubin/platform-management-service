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
IMAGE_NAME="participant-service-api"

if [[ -z "${ACR_NAME}" ]]; then
  echo "Usage: $0 <acr-name> [tag]" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

LOGIN_SERVER="$(az acr show --name "${ACR_NAME}" --query loginServer -o tsv)"
FULL_IMAGE="${LOGIN_SERVER}/${IMAGE_NAME}:${TAG}"

echo "==> Logging in to ACR: ${ACR_NAME}"
az acr login --name "${ACR_NAME}"

echo "==> Building ${FULL_IMAGE}"
docker build \
  --platform linux/amd64 \
  -t "${FULL_IMAGE}" \
  -t "${LOGIN_SERVER}/${IMAGE_NAME}:latest" \
  .

echo "==> Pushing ${FULL_IMAGE}"
docker push "${FULL_IMAGE}"
docker push "${LOGIN_SERVER}/${IMAGE_NAME}:latest"

echo "==> Done"
echo "    Image: ${FULL_IMAGE}"
echo "    Update k8s/kustomization.yaml images.newName / newTag, then:"
echo "      kubectl apply -k k8s/"
