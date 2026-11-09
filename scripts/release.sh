#!/usr/bin/env bash
# Validate by default. DB/IAM recovery is always separate, never automatic.
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
source "$ROOT/scripts/lib.sh"
require jq
ACTION=validate; MANIFEST="$ROOT/deployment/release-manifest.yaml"; ENV_FILE="$ROOT/.env"; PROJECT=mpl-be
EXECUTE=false; REVIEWED=false; APPLY_IAM=false; BACKUP=; RECOVERY=; API_IMAGE=
if [[ $# -gt 0 && $1 != --* ]]; then ACTION=$1; shift; fi
while [[ $# -gt 0 ]]; do
  case "$1" in
    --manifest) MANIFEST=${2:?}; shift 2;; --env-file) ENV_FILE=${2:?}; shift 2;; --project) PROJECT=${2:?}; shift 2;;
    --execute) EXECUTE=true; shift;; --sql-reviewed) REVIEWED=true; shift;; --apply-iam) APPLY_IAM=true; shift;;
    --backup-reference) BACKUP=${2:?}; shift 2;; --recovery-reference) RECOVERY=${2:?}; shift 2;; --api-image) API_IMAGE=${2:?}; shift 2;;
    *) fail "Unsupported argument: $1";;
  esac
done
jq -e -f "$ROOT/scripts/release.jq" "$MANIFEST" >/dev/null || fail 'Release version/schema/IAM compatibility validation failed'
case "$ACTION" in
  validate) printf 'PASS: release versions and declared compatibility constraints\n'; exit 0;;
  preview) ;;
  deploy)
    [[ $EXECUTE == true && $REVIEWED == true && -n $BACKUP && -n $RECOVERY ]] || fail 'Deploy requires --execute --sql-reviewed --backup-reference and --recovery-reference'
    jq -e '.verification.status=="PASS_LOCAL"' "$MANIFEST" >/dev/null || fail 'Release acceptance evidence missing'
    : "${SMOKE_ACCESS_TOKEN:?Existing protected smoke-test token required}";;
  rollback-api)
    [[ $EXECUTE == true ]] || fail 'API rollback requires --execute'
    jq -e --arg image "$API_IMAGE" '.schema.verifiedApiRollbackImages | index($image)!=null' "$MANIFEST" >/dev/null || fail 'No verification evidence for this API/schema/IAM rollback combination'
    : "${SMOKE_ACCESS_TOKEN:?Existing protected smoke-test token required}";;
  *) fail 'Usage: release.sh validate|preview|deploy|rollback-api [options]';;
esac
require docker
export BACKEND_IMAGE=$(jq -r '.components.backend.image' "$MANIFEST")
export MIGRATION_IMAGE=$(jq -r '.components.migrations.image' "$MANIFEST")
export IAM_IMAGE=$(jq -r '.components.iam.image' "$MANIFEST")
COMPOSE=(docker compose --project-name "$PROJECT" --env-file "$ENV_FILE" -f "$ROOT/docker-compose.yml")
umask 077
mkdir -p "$ROOT/.local/releases"
EVIDENCE=$(mktemp -d "$ROOT/.local/releases/$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX")
jq -n --arg release "$(jq -r .release "$MANIFEST")" --arg action "$ACTION" --arg backup "$BACKUP" --arg recovery "$RECOVERY" --arg hash "$(sha256_file "$MANIFEST")" \
 '{release:$release,action:$action,status:"FAIL",stages:[],backup:$backup,recovery:$recovery,manifestSha256:$hash}' > "$EVIDENCE/result.json"
trap 'rm -f "$EVIDENCE/compose.json"; printf "Release evidence: %s\n" "$EVIDENCE/result.json"' EXIT
record_stage() { jq --arg stage "$1" '.stages += [$stage]' "$EVIDENCE/result.json" > "$EVIDENCE/result.tmp"; mv "$EVIDENCE/result.tmp" "$EVIDENCE/result.json"; }
run() { local stage=$1; shift; "${COMPOSE[@]}" "$@" > "$EVIDENCE/$stage.log" 2>&1 || fail "Release stage failed: $stage; inspect restricted evidence"; record_stage "$stage"; }
"${COMPOSE[@]}" --profile iam config --format json > "$EVIDENCE/compose.json"
for pair in api:backend migration:migrations keycloak-config:iam iam-contract:iam keycloak:keycloak app-db:postgres keycloak-db:postgres; do
  service=${pair%:*}; component=${pair#*:}
  [[ $(jq -r --arg s "$service" '.services[$s].image' "$EVIDENCE/compose.json") == $(jq -r --arg c "$component" '.components[$c].image' "$MANIFEST") ]] || fail "Compose image mismatch: $service"
done
rm "$EVIDENCE/compose.json"
if [[ $ACTION == rollback-api ]]; then
  export BACKEND_IMAGE=$API_IMAGE
  run rollback-schema-check run --rm --no-deps migration verify
  run rollback-iam-check run --rm --no-deps iam-contract
  run api-rollback up -d --no-deps --wait api
else
  for command in verify-history validate status updateSQL; do run "$command" run --rm --no-deps migration "$command"; done
  if [[ $ACTION == deploy ]]; then
    for component in backend migrations iam keycloak postgres; do
      image=$(jq -r --arg c "$component" '.components[$c].image' "$MANIFEST")
      expected=$(jq -er --arg c "$component" '.components[$c].testedImageId' "$MANIFEST")
      [[ $(docker image inspect --format '{{.Id}}' "$image") == "$expected" ]] || fail "Artifact differs from tested image: $component"
    done
    for pair in app-db:postgres keycloak-db:postgres keycloak:keycloak; do
      service=${pair%:*}; component=${pair#*:}; container=$("${COMPOSE[@]}" ps -q "$service")
      [[ -n $container ]] || fail "Required infrastructure is not running: $service"
      expected=$(jq -r --arg c "$component" '.components[$c].testedImageId' "$MANIFEST")
      [[ $(docker inspect --format '{{.Image}}' "$container") == "$expected" ]] || fail "Running infrastructure differs from tested release: $service"
    done
    run migration run --rm --no-deps migration update
    run migration-complete run --rm --no-deps migration verify
    if [[ $APPLY_IAM == true ]]; then export MPL_IAM_APPLY_APPROVED=true; run iam-apply run --rm --no-deps keycloak-config; fi
    run iam-contract run --rm --no-deps iam-contract
    run api up -d --no-deps --wait api
  fi
fi
if [[ $ACTION != preview ]]; then
  bash "$ROOT/scripts/smoke.sh" > "$EVIDENCE/smoke.log" 2>&1 || fail 'Protected smoke failed; inspect restricted evidence'
  record_stage smoke
  run sync-state exec -T app-db psql -U postgres -d user_service_db -v ON_ERROR_STOP=1 -c \
    'SELECT (SELECT count(*) FROM user_identity_sync) AS pending, (SELECT count(*) FROM user_provisioning_requests WHERE user_id IS NULL) AS incomplete, (SELECT COALESCE(sum(attempts),0) FROM user_identity_sync) AS retries;'
fi
jq '.status="PASS"' "$EVIDENCE/result.json" > "$EVIDENCE/result.tmp"; mv "$EVIDENCE/result.tmp" "$EVIDENCE/result.json"
