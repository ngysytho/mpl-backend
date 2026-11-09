#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
source "$ROOT/scripts/lib.sh"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
bash "$ROOT/scripts/release.sh" validate
for mutation in '.components.backend.image="mpl-be:latest"' '.components.backend.image="mpl-be:9.0.0"' '.components.iam.configCli="wrong"' '.schema.database="keycloak_db"' '.schema.requiredChangesets[-1]="wrong"' '.schema.verifiedApiRollbackImages=["mpl-be:0.9.0"]'; do
  jq "$mutation" "$ROOT/deployment/release-manifest.yaml" > "$TMP/invalid.json"
  if bash "$ROOT/scripts/release.sh" validate --manifest "$TMP/invalid.json" > /dev/null 2>&1; then fail "Invalid release accepted: $mutation"; fi
done
if bash "$ROOT/scripts/release.sh" deploy > /dev/null 2>&1; then fail 'Ungated deploy accepted'; fi
if bash "$ROOT/scripts/release.sh" rollback-api --execute --api-image mpl-be:0.9.0 > /dev/null 2>&1; then fail 'Unverified rollback accepted'; fi
cat > "$TMP/test.env" <<'ENV'
# Literal dotenv, no shell evaluation
MPL_LOADER_VALUE='$(touch /tmp/mpl-loader-must-not-run)'
MPL_LOADER_EXISTING=file
MPL_LOADER_EMPTY=
ENV
export MPL_LOADER_EXISTING=caller
load_env "$TMP/test.env"
[[ $MPL_LOADER_VALUE == '$(touch /tmp/mpl-loader-must-not-run)' && $MPL_LOADER_EXISTING == caller && $MPL_LOADER_EMPTY == '' ]] || fail 'Dotenv literal/preserve/empty semantics failed'
[[ ! -e /tmp/mpl-loader-must-not-run ]] || fail 'Dotenv executed code'
printf 'PASS: six invalid release mutations, deploy/rollback gates and literal dotenv loading\n'
