#!/usr/bin/env bash
set -Eeuo pipefail

: "${APP_DB_PASSWORD:?Set APP_DB_PASSWORD in infra/local/postgres.env}"
: "${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in infra/local/postgres.env}"
: "${POSTGRES_DB:?POSTGRES_DB must be set by Compose}"
: "${POSTGRES_USER:?POSTGRES_USER must be set by Compose}"

if [[ "${APP_DB_PASSWORD}" == "${POSTGRES_PASSWORD}" ]]; then
  printf '%s\n' 'APP_DB_PASSWORD must differ from POSTGRES_PASSWORD' >&2
  exit 1
fi

psql \
  --no-psqlrc \
  --set=ON_ERROR_STOP=1 \
  --set=app_password="${APP_DB_PASSWORD}" \
  --set=database_name="${POSTGRES_DB}" \
  --username "${POSTGRES_USER}" \
  --dbname "${POSTGRES_DB}" <<'SQL'
CREATE ROLE investment_app LOGIN PASSWORD :'app_password';
GRANT CONNECT ON DATABASE :"database_name" TO investment_app;
GRANT USAGE ON SCHEMA public TO investment_app;
SQL
