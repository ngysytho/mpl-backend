#!/usr/bin/env bash
set -euo pipefail
psql --username "$POSTGRES_USER" --dbname keycloak_db --set=ON_ERROR_STOP=1 --set=kc_password="$KEYCLOAK_DB_PASSWORD" <<'SQL'
CREATE ROLE keycloak LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'kc_password';
REVOKE ALL ON DATABASE keycloak_db FROM PUBLIC;
GRANT CONNECT, CREATE, TEMPORARY ON DATABASE keycloak_db TO keycloak;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO keycloak;
SQL
