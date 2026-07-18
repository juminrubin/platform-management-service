#!/usr/bin/env bash
# Obtain a Microsoft Entra access token for a **technical user**:
# Managed Identity (IMDS / az identity) or a local service principal (client credentials).
#
# The identity must have an API app role assigned (e.g. Consumption.Registrator).
# Application tokens use scope api://{APP_AZURE_API_CLIENT_ID}/.default and carry `roles`.
#
# Prerequisites:
#   - APP_AZURE_API_CLIENT_ID
#   - APP_AZURE_TENANT_ID (client-credentials method)
#   - On Azure: managed identity enabled (imds / az-identity)
#   - Local stand-in: AZURE_TECH_CLIENT_ID + AZURE_TECH_CLIENT_SECRET
#
# Usage:
#   export APP_AZURE_API_CLIENT_ID=...
#   ./scripts/get-token-mi.sh [--method imds|az-identity|client-credentials] [options]
#
# Examples:
#   # On Azure VM / App Service / Container with system-assigned MI
#   TOKEN=$(./scripts/get-token-mi.sh --method imds)
#
#   # User-assigned MI
#   AZURE_MI_CLIENT_ID=<mi-client-id> ./scripts/get-token-mi.sh --method imds
#
#   # az login --identity already done
#   TOKEN=$(./scripts/get-token-mi.sh --method az-identity)
#
#   # Local SP stand-in for MI
#   export APP_AZURE_TENANT_ID=... AZURE_TECH_CLIENT_ID=... AZURE_TECH_CLIENT_SECRET=...
#   TOKEN=$(./scripts/get-token-mi.sh --method client-credentials --print-claims)
#
# Call the API:
#   curl -s -X POST http://localhost:8080/api/v1/consumptions \
#     -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
#     -d '{"participantCallerIdentityId":"...","serviceOfferingId":"gpt-5.1","consumptionData":"{}"}'

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/token-common.sh
source "${SCRIPT_DIR}/lib/token-common.sh"

METHOD=""
PRINT_CLAIMS="false"
PRINT_EXPORT="false"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Obtain an application access token (Managed Identity or service principal).

Environment:
  APP_AZURE_API_CLIENT_ID        API app registration client ID (required)
  APP_AZURE_TENANT_ID            Tenant GUID (required for client-credentials)
  AZURE_MI_CLIENT_ID         User-assigned MI client id (optional; imds / az-identity)
  AZURE_TECH_CLIENT_ID       Service principal client id (client-credentials)
  AZURE_TECH_CLIENT_SECRET   Service principal secret (client-credentials)
  MI_RESOURCE                Override resource (default: api://\$APP_AZURE_API_CLIENT_ID)
  MI_SCOPE                   Override scope (default: api://\$APP_AZURE_API_CLIENT_ID/.default)

Options:
  --method imds              Azure Instance Metadata Service (default on Azure hosts)
  --method az-identity       Azure CLI: az account get-access-token (after az login --identity)
  --method client-credentials  Local SP with client id/secret (MI stand-in)
$(token_usage_common_flags)

If --method is omitted, the script picks:
  1) imds if IMDS is reachable
  2) else client-credentials if AZURE_TECH_CLIENT_ID/SECRET are set
  3) else az-identity

EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --method)
      METHOD="${2:-}"
      shift 2
      ;;
    --print-claims)
      PRINT_CLAIMS="true"
      shift
      ;;
    --export)
      PRINT_EXPORT="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "error: unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

token_require_env APP_AZURE_API_CLIENT_ID

MI_RESOURCE="${MI_RESOURCE:-api://${APP_AZURE_API_CLIENT_ID}}"
MI_SCOPE="${MI_SCOPE:-api://${APP_AZURE_API_CLIENT_ID}/.default}"

imds_reachable() {
  curl -sS -m 1 -o /dev/null -w '' \
    -H "Metadata: true" \
    "http://169.254.169.254/metadata/instance?api-version=2021-02-01" 2>/dev/null
}

detect_method() {
  if imds_reachable; then
    echo "imds"
  elif [[ -n "${AZURE_TECH_CLIENT_ID:-}" && -n "${AZURE_TECH_CLIENT_SECRET:-}" ]]; then
    echo "client-credentials"
  else
    echo "az-identity"
  fi
}

if [[ -z "${METHOD}" ]]; then
  METHOD="$(detect_method)"
  echo "==> Auto-selected method: ${METHOD}" >&2
fi

get_token_imds() {
  token_require_cmd curl
  token_require_cmd jq

  local url="http://169.254.169.254/metadata/identity/oauth2/token?api-version=2019-08-01"
  url+="&resource=$(printf '%s' "${MI_RESOURCE}" | jq -sRr @uri)"

  if [[ -n "${AZURE_MI_CLIENT_ID:-}" ]]; then
    url+="&client_id=$(printf '%s' "${AZURE_MI_CLIENT_ID}" | jq -sRr @uri)"
    echo "==> IMDS token for user-assigned MI client_id=${AZURE_MI_CLIENT_ID}" >&2
  else
    echo "==> IMDS token for system-assigned managed identity" >&2
  fi
  echo "    resource=${MI_RESOURCE}" >&2

  local body token
  body="$(curl -sS -H "Metadata: true" "${url}")"
  token="$(echo "${body}" | jq -r .access_token)"
  if [[ -z "${token}" || "${token}" == "null" ]]; then
    echo "error: IMDS token request failed:" >&2
    echo "${body}" | jq . >&2 || echo "${body}" >&2
    exit 1
  fi
  printf '%s' "${token}"
}

get_token_az_identity() {
  token_require_cmd az

  if ! az account show >/dev/null 2>&1; then
    echo "==> Not logged in. Trying az login --identity" >&2
    if [[ -n "${AZURE_MI_CLIENT_ID:-}" ]]; then
      az login --identity -u "${AZURE_MI_CLIENT_ID}" >/dev/null
    else
      az login --identity >/dev/null
    fi
  fi

  echo "==> Requesting application token for resource ${MI_RESOURCE}" >&2

  local token=""
  if token="$(az account get-access-token --scope "${MI_SCOPE}" --query accessToken -o tsv 2>/dev/null)" \
    && [[ -n "${token}" && "${token}" != "null" ]]; then
    printf '%s' "${token}"
    return 0
  fi

  token="$(az account get-access-token --resource "${MI_RESOURCE}" --query accessToken -o tsv)"
  printf '%s' "${token}"
}

get_token_client_credentials() {
  token_require_cmd curl
  token_require_cmd jq
  token_require_env APP_AZURE_TENANT_ID
  token_require_env AZURE_TECH_CLIENT_ID
  token_require_env AZURE_TECH_CLIENT_SECRET

  echo "==> Client credentials token (service principal stand-in for MI)" >&2
  echo "    client_id=${AZURE_TECH_CLIENT_ID}" >&2
  echo "    scope=${MI_SCOPE}" >&2

  local body token
  body="$(curl -sS -X POST \
    "https://login.microsoftonline.com/${APP_AZURE_TENANT_ID}/oauth2/v2.0/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "client_id=${AZURE_TECH_CLIENT_ID}" \
    --data-urlencode "client_secret=${AZURE_TECH_CLIENT_SECRET}" \
    --data-urlencode "scope=${MI_SCOPE}" \
    --data-urlencode "grant_type=client_credentials")"

  token="$(echo "${body}" | jq -r .access_token)"
  if [[ -z "${token}" || "${token}" == "null" ]]; then
    echo "error: client_credentials token request failed:" >&2
    echo "${body}" | jq . >&2 || echo "${body}" >&2
    exit 1
  fi
  printf '%s' "${token}"
}

TOKEN=""
case "${METHOD}" in
  imds)
    TOKEN="$(get_token_imds)"
    ;;
  az-identity)
    TOKEN="$(get_token_az_identity)"
    ;;
  client-credentials)
    TOKEN="$(get_token_client_credentials)"
    ;;
  *)
    echo "error: unknown method: ${METHOD} (use imds, az-identity, or client-credentials)" >&2
    exit 1
    ;;
esac

token_print_result "${TOKEN}" "${PRINT_CLAIMS}" "${PRINT_EXPORT}"
