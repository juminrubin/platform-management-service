#!/usr/bin/env bash
# Obtain a human delegated token and run a command with TOKEN / Authorization set.
#
# Usage:
#   ./scripts/with-human-token.sh [--method az-cli|device-code] [--print-claims] -- <command> [args...]
#
# Examples:
#   ./scripts/with-human-token.sh -- curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer \$TOKEN"
#   ./scripts/with-human-token.sh --print-claims -- ./scripts/curl-api.sh GET /api/v1/participants
#
# The child process inherits:
#   TOKEN                 raw JWT
#   AUTHORIZATION         "Bearer <jwt>"

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

METHOD_ARGS=()
PRINT_CLAIMS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --)
      shift
      break
      ;;
    --method)
      METHOD_ARGS+=(--method "${2:-}")
      shift 2
      ;;
    --print-claims)
      PRINT_CLAIMS=(--print-claims)
      shift
      ;;
    -h|--help)
      cat <<EOF
Usage: $(basename "$0") [token options] -- <command> [args...]

Token options:
  --method az-cli|device-code   Passed to get-token-human.sh
  --print-claims                Show claims on stderr before running the command

Environment (same as get-token-human.sh):
  APP_AZURE_TENANT_ID, APP_API_CLIENT_ID, optional AZURE_HUMAN_CLIENT_ID / API_SCOPE

EOF
      exit 0
      ;;
    *)
      echo "error: unknown option: $1 (use -- before the command)" >&2
      exit 1
      ;;
  esac
done

if [[ $# -eq 0 ]]; then
  echo "error: no command specified after --" >&2
  exit 1
fi

TOKEN="$("${SCRIPT_DIR}/get-token-human.sh" "${METHOD_ARGS[@]}" "${PRINT_CLAIMS[@]}")"
export TOKEN
export AUTHORIZATION="Bearer ${TOKEN}"

exec "$@"
