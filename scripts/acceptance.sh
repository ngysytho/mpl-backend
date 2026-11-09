#!/usr/bin/env bash
# Creates only a unique disposable Compose project. Never loads an existing dotenv.
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
source "$ROOT/scripts/lib.sh"
require docker; require jq; require openssl; select_java
cd "$ROOT"
umask 077
PROJECT="mpl-acceptance-$(openssl rand -hex 6)"
ARTIFACT="$ROOT/.local/$PROJECT"; mkdir -p "$ARTIFACT"
ENV_FILE="$ARTIFACT/test.env"; LOG="$ARTIFACT/acceptance.log"; STATUS=FAIL
COMPOSE=(docker compose --project-name "$PROJECT" --env-file "$ENV_FILE" -f "$ROOT/docker-compose.yml")
cleanup() {
  local code=$? volume label cleanup_failed=false
  trap - EXIT
  set +e
  if [[ -f $ENV_FILE ]]; then
    "${COMPOSE[@]}" logs --no-color >> "$LOG" 2>&1
    "${COMPOSE[@]}" down --remove-orphans >> "$LOG" 2>&1 || cleanup_failed=true
    docker volume ls -q --filter "label=com.docker.compose.project=$PROJECT" > "$ARTIFACT/volumes.txt" || cleanup_failed=true
    while IFS= read -r volume; do
      [[ -n $volume ]] || continue
      label=$(docker volume inspect --format '{{index .Labels "com.docker.compose.project"}}' "$volume")
      if [[ $label == "$PROJECT" ]]; then docker volume rm "$volume" >> "$LOG" 2>&1 || cleanup_failed=true; else cleanup_failed=true; fi
    done < "$ARTIFACT/volumes.txt"
    rm -f "$ENV_FILE"
  fi
  if [[ $cleanup_failed == true ]]; then STATUS=FAIL; code=1; fi
  jq -n --arg project "$PROJECT" --arg status "$STATUS" --arg cleanup "$cleanup_failed" \
    --slurpfile metrics "$ARTIFACT/metrics.json" --slurpfile images "$ARTIFACT/images.json" \
    '{project:$project,status:$status,cleanupFailed:($cleanup=="true"),metrics:$metrics[0],images:$images[0],checks:["migration validate/preview/update/history", "IAM apply/reapply and read-only contract", "registration replay", "PKCE issuer/audience/USER", "anonymous 401 and USER 403", "real Keycloak lifecycle", "ADMIN mapping and protected metrics", "empty journals"]}' > "$ARTIFACT/result.json"
  printf 'Acceptance result: %s; evidence: %s\n' "$STATUS" "$ARTIFACT/result.json"
  exit "$code"
}
trap cleanup EXIT
printf '{}\n' > "$ARTIFACT/metrics.json"; printf '{}\n' > "$ARTIFACT/images.json"
# Compose gives process variables priority over --env-file: clear every interpolated key first.
while IFS= read -r key; do unset "$key"; done < <(sed -nE 's/.*\$\{([A-Z_]+).*/\1/p' "$ROOT/docker-compose.yml" | sort -u)
for key in APP_DB_ADMIN_PASSWORD DB_PASSWORD DB_MIGRATION_PASSWORD KEYCLOAK_DB_ADMIN_PASSWORD KEYCLOAK_DB_PASSWORD KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD KEYCLOAK_CLIENT_SECRET KEYCLOAK_STATE_ENCRYPTION_KEY; do
  export "$key=$(openssl rand -hex 16)"
done
read -r APP_PORT KEYCLOAK_PORT APP_DB_PORT < <("$JAVA_BIN" "$ROOT/scripts/FreePorts.java")
export APP_PORT KEYCLOAK_PORT APP_DB_PORT
export KEYCLOAK_PUBLIC_URL="http://localhost:$KEYCLOAK_PORT" SWAGGER_PUBLIC_URL="http://localhost:$APP_PORT"
export FRONTEND_URL=http://localhost:3000 CORS_ORIGINS=http://localhost:3000 MPL_IAM_APPLY_APPROVED=true
export KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME=acceptance-admin
for key in APP_DB_ADMIN_PASSWORD DB_PASSWORD DB_MIGRATION_PASSWORD KEYCLOAK_DB_ADMIN_PASSWORD KEYCLOAK_DB_PASSWORD KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD KEYCLOAK_CLIENT_SECRET KEYCLOAK_STATE_ENCRYPTION_KEY APP_PORT KEYCLOAK_PORT APP_DB_PORT KEYCLOAK_PUBLIC_URL SWAGGER_PUBLIC_URL FRONTEND_URL CORS_ORIGINS MPL_IAM_APPLY_APPROVED KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME; do
  printf '%s=%s\n' "$key" "${!key}" >> "$ENV_FILE"
done
# Force exactly the manifest artifacts, even when caller environment selects other image tags.
export BACKEND_IMAGE=$(jq -r '.components.backend.image' deployment/release-manifest.yaml)
export MIGRATION_IMAGE=$(jq -r '.components.migrations.image' deployment/release-manifest.yaml)
export IAM_IMAGE=$(jq -r '.components.iam.image' deployment/release-manifest.yaml)
for component in backend migrations iam keycloak postgres; do
  ref=$(jq -r --arg c "$component" '.components[$c].image' deployment/release-manifest.yaml)
  id=$(docker image inspect --format '{{.Id}}' "$ref")
  jq --arg c "$component" --arg ref "$ref" --arg id "$id" '.[$c]={image:$ref,id:$id}' "$ARTIFACT/images.json" > "$ARTIFACT/images.tmp"
  mv "$ARTIFACT/images.tmp" "$ARTIFACT/images.json"
done
compose() { printf 'Acceptance stage: %s\n' "$*"; "${COMPOSE[@]}" "$@" >> "$LOG" 2>&1 || fail 'Compose stage failed; inspect restricted acceptance log'; }
printf 'Acceptance stage: compile Java tests\n'
./mvnw -B -ntp -DskipTests test-compile >> "$LOG" 2>&1
compose config --quiet
compose up -d --wait keycloak
compose run --rm migration validate
compose run --rm migration updateSQL
compose run --rm keycloak-config
compose run --rm keycloak-config
compose up -d --wait api
compose run --rm migration verify-history
compose run --rm migration verify
compose run --rm iam-contract
export MPL_REAL_KEYCLOAK_TESTS=true MPL_COMPOSE_TESTS=true MPL_DISPOSABLE_KEYCLOAK=true
export KEYCLOAK_ADMIN_URL="$KEYCLOAK_PUBLIC_URL" KEYCLOAK_CLIENT_ID=user-service-admin API_URL="$SWAGGER_PUBLIC_URL"
export MPL_ACCEPTANCE_METRICS="$ARTIFACT/metrics.json"
printf 'Acceptance stage: Java PKCE/API smoke and real Keycloak lifecycle\n'
./mvnw -B -ntp -Dit.test=RealKeycloakIT,ComposeSmokeIT failsafe:integration-test failsafe:verify >> "$LOG" 2>&1
compose exec -T app-db psql -U postgres -d user_service_db -v ON_ERROR_STOP=1 -c \
  'DO $$ BEGIN IF (SELECT count(*) FROM user_identity_sync)<>0 OR (SELECT count(*) FROM user_provisioning_requests WHERE user_id IS NULL)<>0 THEN RAISE EXCEPTION '\''Unreconciled state'\''; END IF; END $$;'
STATUS=PASS
