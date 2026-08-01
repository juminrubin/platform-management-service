#!/usr/bin/env bash
# Decode and pretty-print a JWT access token payload (claims).
#
# Usage:
#   ./scripts/print-jwt-claims.sh "$TOKEN"
#   echo "$TOKEN" | ./scripts/print-jwt-claims.sh
#   ./scripts/print-jwt-claims.sh --file /path/to/token.txt
#
# Requires: base64; jq is recommended for pretty JSON.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/token-common.sh
source "${SCRIPT_DIR}/lib/token-common.sh"

usage() {
  cat <<EOF
Usage: $(basename "$0") [TOKEN]
       $(basename "$0") --file PATH
       echo "\$TOKEN" | $(basename "$0")

Decode the JWT payload (middle segment) and print claims as JSON.

EOF
}

JWT=""
case "${1:-}" in
  -h|--help)
    usage
    exit 0
    ;;
  --file)
    if [[ -z "${2:-}" || ! -f "$2" ]]; then
      echo "error: --file requires an existing path" >&2
      exit 1
    fi
    JWT="$(tr -d '[:space:]' <"$2")"
    ;;
  "")
    if [[ -t 0 ]]; then
      echo "error: pass a token argument, --file PATH, or pipe the token on stdin" >&2
      usage >&2
      exit 1
    fi
    JWT="$(tr -d '[:space:]')"
    ;;
  *)
    JWT="$(printf '%s' "$1" | tr -d '[:space:]')"
    ;;
esac

if [[ -z "${JWT}" || "${JWT}" != *.* ]]; then
  echo "error: value does not look like a JWT (expected header.payload.signature)" >&2
  exit 1
fi

token_decode_jwt_payload "${JWT}"
