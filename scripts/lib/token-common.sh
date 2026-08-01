#!/usr/bin/env bash
# Shared helpers for token scripts under scripts/.
# shellcheck shell=bash

token_require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "error: required command not found: ${cmd}" >&2
    exit 1
  fi
}

token_require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "error: environment variable ${name} is required" >&2
    echo "       export ${name}=..." >&2
    exit 1
  fi
}

# Decode JWT payload (middle segment) as JSON on stdout. Best-effort base64url.
token_decode_jwt_payload() {
  local jwt="$1"
  local payload
  payload="$(printf '%s' "${jwt}" | cut -d. -f2)"
  # pad base64url to multiple of 4
  local mod=$(( ${#payload} % 4 ))
  if [[ "${mod}" -eq 2 ]]; then
    payload="${payload}=="
  elif [[ "${mod}" -eq 3 ]]; then
    payload="${payload}="
  fi
  payload="${payload//-/+}"
  payload="${payload//_//}"
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "${payload}" | base64 -d 2>/dev/null | jq .
  else
    printf '%s' "${payload}" | base64 -d 2>/dev/null
    echo
  fi
}

token_print_result() {
  local token="$1"
  local print_claims="${2:-false}"
  local print_export="${3:-false}"

  if [[ -z "${token}" || "${token}" == "null" ]]; then
    echo "error: empty access token" >&2
    exit 1
  fi

  if [[ "${print_claims}" == "true" ]]; then
    echo "==> Token claims (payload)" >&2
    token_decode_jwt_payload "${token}" >&2 || true
    echo >&2
  fi

  if [[ "${print_export}" == "true" ]]; then
    # Safe for: eval "$(./scripts/get-token-human.sh --export)"
    printf "export TOKEN=%q\n" "${token}"
  else
    printf '%s\n' "${token}"
  fi
}

token_usage_common_flags() {
  cat <<'EOF'
  --print-claims   Decode and print JWT payload to stderr
  --export         Print `export TOKEN=...` (for eval)
  -h, --help       Show help
EOF
}
