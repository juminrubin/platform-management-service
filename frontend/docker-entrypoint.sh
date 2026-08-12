#!/bin/sh
set -eu

CONFIG_PATH="${CONFIG_PATH:-/usr/share/nginx/html/config.js}"

json_escape() {
  printf '%s' "$1" | awk 'BEGIN { ORS="" } {
    gsub(/\\/, "\\\\")
    gsub(/"/, "\\\"")
    gsub(/\t/, "\\t")
    gsub(/\r/, "\\r")
    gsub(/</, "\\u003c")
    print
  }'
}

tenant=$(json_escape "${APP_AZURE_TENANT_ID:-}")
client=$(json_escape "${APP_CLIENT_ID:-}")
scope=$(json_escape "${APP_API_SCOPE:-}")
api=$(json_escape "${APP_API_BASE_URL:-}")

cat > "${CONFIG_PATH}" <<EOF
window.__APP_CONFIG__={"APP_AZURE_TENANT_ID":"${tenant}","APP_CLIENT_ID":"${client}","APP_API_SCOPE":"${scope}","APP_API_BASE_URL":"${api}"};
EOF

exec nginx -g 'daemon off;'
