#!/usr/bin/env bash
# curl helper for the Platform Management API using a human (or pre-set) Bearer token.
#
# If TOKEN is not set, obtains one via get-token-human.sh.
#
# Usage:
#   ./scripts/curl-api.sh GET /api/v1/auth/me
#   ./scripts/curl-api.sh GET /api/v1/participants
#   ./scripts/curl-api.sh POST /api/v1/participants -d '{"id":"x","name":"X","status":"ACTIVE"}'
#   API_BASE=http://localhost:8080 ./scripts/curl-api.sh GET /api/v1/auth/me
#
# Environment:
#   TOKEN                    Optional pre-fetched JWT
#   API_BASE                 Default http://localhost:8080
#   APP_AZURE_TENANT_ID      Used when TOKEN is missing
#   APP_API_CLIENT_ID        Used when TOKEN is missing

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_BASE="${API_BASE:-http://localhost:8080}"

usage() {
  cat <<EOF
Usage: $(basename "$0") <METHOD> <PATH> [curl args...]

Examples:
  $(basename "$0") GET /api/v1/auth/me
  $(basename "$0") GET '/api/v1/entitlements/check?callerId=alice@acme.example&serviceOfferingId=gpt-5.1'
  $(basename "$0") POST /api/v1/participants -d '{"id":"demo","name":"Demo","status":"ACTIVE"}'

If TOKEN is unset, runs ./scripts/get-token-human.sh (requires Azure CLI + Entra env vars).

EOF
}

if [[ $# -lt 2 ]]; then
  usage >&2
  exit 1
fi

METHOD="$(printf '%s' "$1" | tr '[:lower:]' '[:upper:]')"
PATH_PART="$2"
shift 2

case "${PATH_PART}" in
  http://*|https://*)
    URL="${PATH_PART}"
    ;;
  /*)
    URL="${API_BASE}${PATH_PART}"
    ;;
  *)
    URL="${API_BASE}/${PATH_PART}"
    ;;
esac

if [[ -z "${TOKEN:-}" ]]; then
  echo "==> TOKEN not set; obtaining human token via get-token-human.sh" >&2
  TOKEN="$("${SCRIPT_DIR}/get-token-human.sh")"
  export TOKEN
fi

# Default Accept; -d implies JSON content-type if not already provided in "$@".
CURL_ARGS=(
  -sS
  -X "${METHOD}"
  -H "Authorization: Bearer ${TOKEN}"
  -H "Accept: application/json"
)

HAS_CONTENT_TYPE=false
HAS_DATA=false
for arg in "$@"; do
  case "${arg}" in
    -H|--header)
      ;;
    Content-Type:*|content-type:*)
      HAS_CONTENT_TYPE=true
      ;;
    -d|--data|--data-raw|--data-binary|-d*)
      HAS_DATA=true
      ;;
  esac
done

# If user passed -d and no Content-Type, add application/json.
if [[ "${HAS_DATA}" == "true" && "${HAS_CONTENT_TYPE}" == "false" ]]; then
  # Heuristic: scan full argv for Content-Type in -H values
  for ((i = 1; i <= $#; i++)); do
    if [[ "${!i}" == "-H" || "${!i}" == "--header" ]]; then
      next=$((i + 1))
      if [[ ${next} -le $# && "${!next}" == [Cc]ontent-[Tt]ype:* ]]; then
        HAS_CONTENT_TYPE=true
        break
      fi
    fi
  done
fi
if [[ "${HAS_DATA}" == "true" && "${HAS_CONTENT_TYPE}" == "false" ]]; then
  CURL_ARGS+=(-H "Content-Type: application/json")
fi

if command -v jq >/dev/null 2>&1; then
  curl "${CURL_ARGS[@]}" "$@" "${URL}" | jq .
else
  curl "${CURL_ARGS[@]}" "$@" "${URL}"
  echo
fi
