#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
source "$ROOT/scripts/lib.sh"
[[ $# -eq 1 ]] || fail 'Usage: local.sh start|keycloak|configure|migrate|api|stop|status'
umask 077
STATE="$ROOT/.local"; mkdir -p "$STATE"
iam() { bash "$ROOT/../mpl-iam/scripts/native.sh" --env-file "$ROOT/.env" "$1"; }
migrate() { bash "$ROOT/../mpl-db-migrations/scripts/migrate.sh" --env-file "$ROOT/.env" "$1"; }
start_api() {
  load_env "$ROOT/.env"; select_java
  if managed_pid "$STATE/api.pid" "$ROOT/target/" >/dev/null; then printf 'API already running\n'; return; fi
  local jars=("$ROOT"/target/mpl-be-*.jar) key
  [[ ${#jars[@]} -eq 1 && -f ${jars[0]} ]] || fail 'Build the API first: ./mvnw package'
  require lsof
  if lsof -nP -t -iTCP:"${APP_PORT:-8080}" -sTCP:LISTEN >/dev/null 2>&1; then fail 'API port is occupied; inspect its process'; fi
  migrate verify
  local runtime=()
  for key in PATH HOME JAVA_HOME LANG TMPDIR DB_URL DB_USERNAME DB_PASSWORD DB_POOL_SIZE APP_PORT KEYCLOAK_ADMIN_URL KEYCLOAK_ISSUER_URL KEYCLOAK_JWKS_URL KEYCLOAK_REALM KEYCLOAK_CLIENT_ID KEYCLOAK_CLIENT_SECRET JWT_AUDIENCE CORS_ORIGINS LOG_LEVEL APP_LOG_LEVEL; do
    if declare -p "$key" >/dev/null 2>&1; then runtime+=("$key=${!key}"); fi
  done
  # env replaces itself with Java; privileged job variables are excluded from the JVM environment.
  nohup env -i "${runtime[@]}" SPRING_PROFILES_ACTIVE=local SERVER_ADDRESS=127.0.0.1 SPRING_CONFIG_IMPORT=optional:classpath:/native-no-secrets.properties \
    "$JAVA_BIN" -Xms64m -Xmx384m -jar "${jars[0]}" >> "$STATE/api.log" 2>&1 < /dev/null &
  printf '%s\n' "$!" > "$STATE/api.pid"
  sleep 1
  wait_ready "http://127.0.0.1:${APP_PORT:-8080}/actuator/health/readiness" "$STATE/api.pid" "$ROOT/target/"
  printf 'API ready; restricted log: %s\n' "$STATE/api.log"
}
case "$1" in
  start) iam start; migrate update; start_api;;
  keycloak) iam start;;
  configure) iam configure;;
  migrate) migrate update;;
  api) start_api;;
  stop) stop_managed "$STATE/api.pid" "$ROOT/target/"; iam stop;;
  status) iam status; if pid=$(managed_pid "$STATE/api.pid" "$ROOT/target/"); then printf 'api: running (PID %s)\n' "$pid"; else printf 'api: stopped\n'; fi;;
  *) fail 'Unsupported local action';;
esac
