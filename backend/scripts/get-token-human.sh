#!/usr/bin/env bash
# Obtain a Microsoft Entra access token for a **human user** (delegated).
#
# The user must belong to an Entra security group that is assigned an app role
# on this API (e.g. System.Maintainer, Entitlement.Reader). The token's `roles`
# claim is what the API authorizes on.
#
# Prerequisites:
#   - APP_AZURE_TENANT_ID
#   - APP_AZURE_API_CLIENT_ID   (API app registration client id)
#   - For device-code: AZURE_HUMAN_CLIENT_ID (public client with access_as_user)
#   - Azure CLI (method az-cli) or curl + jq (method device-code)
#
# Usage:
#   export APP_AZURE_TENANT_ID=...
#   export APP_AZURE_API_CLIENT_ID=...
#   ./scripts/get-token-human.sh [--method az-cli|device-code] [--print-claims] [--export]
#
# Examples:
#   TOKEN=$(./scripts/get-token-human.sh)
#   eval "$(./scripts/get-token-human.sh --export --print-claims)"
#   ./scripts/get-token-human.sh --method device-code
#
# Call the API:
#   curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN" | jq

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/token-common.sh
source "${SCRIPT_DIR}/lib/token-common.sh"

METHOD="az-cli"
PRINT_CLAIMS="false"
PRINT_EXPORT="false"

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Obtain a delegated access token for a human user against the participant API.

Environment:
  APP_AZURE_TENANT_ID          Entra tenant GUID (required)
  APP_AZURE_API_CLIENT_ID      API app registration client ID (required)
  AZURE_HUMAN_CLIENT_ID    Public/SPA client ID (required for --method device-code)
  API_SCOPE                Override scope (default: api://\$APP_AZURE_API_CLIENT_ID/access_as_user)

Options:
  --method az-cli          Use Azure CLI (default). Runs az login if needed.
  --method device-code     OAuth2 device code flow (browser on another device)
$(token_usage_common_flags)

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

token_require_env APP_AZURE_TENANT_ID
token_require_env APP_AZURE_API_CLIENT_ID

API_SCOPE="${API_SCOPE:-api://${APP_AZURE_API_CLIENT_ID}/access_as_user}"
API_RESOURCE="api://${APP_AZURE_API_CLIENT_ID}"

get_token_az_cli() {
  token_require_cmd az

  if ! az account show >/dev/null 2>&1; then
    echo "==> Not logged in. Starting interactive az login (tenant ${APP_AZURE_TENANT_ID})" >&2
    az login --tenant "${APP_AZURE_TENANT_ID}" --allow-no-subscriptions >/dev/null
  fi

  echo "==> Requesting delegated token for resource ${API_RESOURCE}" >&2
  echo "    (sign-in user must be in a group assigned an API app role)" >&2

  # Prefer --scope when supported; fall back to --resource for older Azure CLI.
  local token=""
  if token="$(az account get-access-token --scope "${API_SCOPE}" --query accessToken -o tsv 2>/dev/null)" \
    && [[ -n "${token}" && "${token}" != "null" ]]; then
    printf '%s' "${token}"
    return 0
  fi

  token="$(az account get-access-token --resource "${API_RESOURCE}" --query accessToken -o tsv)"
  printf '%s' "${token}"
}

get_token_device_code() {
  token_require_cmd curl
  token_require_cmd jq
  token_require_env AZURE_HUMAN_CLIENT_ID

  local tenant="${APP_AZURE_TENANT_ID}"
  local client_id="${AZURE_HUMAN_CLIENT_ID}"
  local scope="${API_SCOPE} offline_access openid profile"
  local device_json device_code user_code verify_uri interval expires_in
  local token_json token error

  echo "==> Starting device code flow" >&2
  echo "    client_id=${client_id}" >&2
  echo "    scope=${API_SCOPE}" >&2

  device_json="$(curl -sS -X POST \
    "https://login.microsoftonline.com/${tenant}/oauth2/v2.0/devicecode" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "client_id=${client_id}" \
    --data-urlencode "scope=${scope}")"

  device_code="$(echo "${device_json}" | jq -r .device_code)"
  user_code="$(echo "${device_json}" | jq -r .user_code)"
  verify_uri="$(echo "${device_json}" | jq -r .verification_uri)"
  interval="$(echo "${device_json}" | jq -r '.interval // 5')"
  expires_in="$(echo "${device_json}" | jq -r '.expires_in // 900')"

  if [[ -z "${device_code}" || "${device_code}" == "null" ]]; then
    echo "error: device code request failed:" >&2
    echo "${device_json}" | jq . >&2 || echo "${device_json}" >&2
    exit 1
  fi

  echo >&2
  echo "================================================================" >&2
  echo " Open:  ${verify_uri}" >&2
  echo " Code:  ${user_code}" >&2
  echo " Sign in as the human user (group must have API app role)." >&2
  echo " Waiting up to ${expires_in}s ..." >&2
  echo "================================================================" >&2
  echo >&2

  local elapsed=0
  while (( elapsed < expires_in )); do
    sleep "${interval}"
    elapsed=$((elapsed + interval))

    token_json="$(curl -sS -X POST \
      "https://login.microsoftonline.com/${tenant}/oauth2/v2.0/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      --data-urlencode "grant_type=urn:ietf:params:oauth:grant-type:device_code" \
      --data-urlencode "client_id=${client_id}" \
      --data-urlencode "device_code=${device_code}")"

    token="$(echo "${token_json}" | jq -r .access_token)"
    if [[ -n "${token}" && "${token}" != "null" ]]; then
      printf '%s' "${token}"
      return 0
    fi

    error="$(echo "${token_json}" | jq -r .error)"
    case "${error}" in
      authorization_pending|slow_down)
        echo "    … still waiting (${elapsed}s)" >&2
        if [[ "${error}" == "slow_down" ]]; then
          sleep "${interval}"
          elapsed=$((elapsed + interval))
        fi
        ;;
      authorization_declined|expired_token|bad_verification_code)
        echo "error: device login failed: ${error}" >&2
        echo "${token_json}" | jq . >&2
        exit 1
        ;;
      *)
        echo "error: token endpoint response:" >&2
        echo "${token_json}" | jq . >&2 || echo "${token_json}" >&2
        exit 1
        ;;
    esac
  done

  echo "error: device code expired before login completed" >&2
  exit 1
}

TOKEN=""
case "${METHOD}" in
  az-cli)
    TOKEN="$(get_token_az_cli)"
    ;;
  device-code)
    TOKEN="$(get_token_device_code)"
    ;;
  *)
    echo "error: unknown method: ${METHOD} (use az-cli or device-code)" >&2
    exit 1
    ;;
esac

token_print_result "${TOKEN}" "${PRINT_CLAIMS}" "${PRINT_EXPORT}"
