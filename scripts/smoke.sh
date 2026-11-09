#!/usr/bin/env bash
# Read-only smoke; an existing USER token is supplied through the environment.
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
source "$ROOT/scripts/lib.sh"
require curl; require jq
BASE=${API_URL:-http://localhost:8080}; BASE=${BASE%/}
: "${SMOKE_ACCESS_TOKEN:?Set SMOKE_ACCESS_TOKEN}"
curl -fsS --max-time 20 "$BASE/actuator/health/readiness" | jq -e '.status=="UP"' >/dev/null
[[ $(curl -sS --max-time 20 -o /dev/null -w '%{http_code}' "$BASE/api/v1/users/me") == 401 ]] || fail 'Anonymous request was not rejected with 401'
HTTP_TOKEN=$SMOKE_ACCESS_TOKEN
http_get "$BASE/api/v1/users/me" | jq -e '.data.id | strings | length>0' >/dev/null
printf 'PASS: readiness, anonymous rejection, authenticated profile read\n'
