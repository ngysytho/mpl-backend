#!/usr/bin/env bash
set -euo pipefail
psql --username "$POSTGRES_USER" --dbname user_service_db --set=ON_ERROR_STOP=1 \
  --set=migration_password="$APP_MIGRATION_PASSWORD" --set=runtime_password="$APP_RUNTIME_PASSWORD" <<'SQL'
CREATE ROLE app_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'migration_password';
CREATE ROLE app_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'runtime_password';
REVOKE ALL ON DATABASE user_service_db FROM PUBLIC;
GRANT CONNECT ON DATABASE user_service_db TO app_migrator, app_runtime;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO app_migrator;
GRANT USAGE ON SCHEMA public TO app_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE app_migrator IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE app_migrator IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO app_runtime;
SQL
