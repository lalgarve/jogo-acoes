#!/usr/bin/env bash
# Saves and restores email-service's test API key (spec 05-030) -- the `api_key` schema of the
# email_service database, as written by the api-key CLI (lalgarve/api-key, release v1.0.0).
#
# The plaintext key is printed only once by the CLI and can't be recovered from the database, so
# the generated row is kept in docker/postgres-email-service/test-data/api-key-test-data.sql:
# restoring it into a fresh database (new docker volume, sandbox Postgres) makes the same test
# key valid again, without generating a new one and updating every place that uses it. Test
# values only (docker/sandbox) -- never used in staging/production. See
# docker/postgres-email-service/test-data/README.md for the key and pepper this dump matches.
#
#   restore  drops and recreates the `api_key` schema from the saved dump (the `public` schema,
#            email-service's own tables, is never touched)
#   dump     overwrites the saved dump with the current `api_key` schema -- only after
#            deliberately generating a new test key
#
# Uses the running db-email-service container (docker compose) when there is one; otherwise the
# local psql/pg_dump against --host/--port (sandbox: native Postgres, same database/credentials).
#
# Usage: ./scripts/test-api-key.sh restore|dump [--host HOST] [--port PORT]
# Example: ./scripts/test-api-key.sh restore
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DUMP_FILE="$REPO_ROOT/docker/postgres-email-service/test-data/api-key-test-data.sql"

DB_NAME="email_service"
DB_USER="email_service_admin"
DB_PASSWORD="email_service_admin"
DB_HOST="localhost"
DB_PORT="5433"

usage() {
  echo "Usage: $0 restore|dump [--host HOST] [--port PORT]" >&2
  exit 1
}

[ $# -ge 1 ] || usage
COMMAND="$1"
shift

while [ $# -gt 0 ]; do
  case "$1" in
    --host)
      DB_HOST="$2"
      shift 2
      ;;
    --port)
      DB_PORT="$2"
      shift 2
      ;;
    *)
      usage
      ;;
  esac
done

use_container() {
  [ -n "$(docker compose -f "$REPO_ROOT/docker-compose.yml" ps -q db-email-service 2>/dev/null)" ]
}

run_psql() {
  if use_container; then
    docker compose -f "$REPO_ROOT/docker-compose.yml" exec -T db-email-service \
      psql -v ON_ERROR_STOP=1 -q -U "$DB_USER" -d "$DB_NAME"
  else
    PGPASSWORD="$DB_PASSWORD" psql -v ON_ERROR_STOP=1 -q -h "$DB_HOST" -p "$DB_PORT" \
      -U "$DB_USER" -d "$DB_NAME"
  fi
}

run_pg_dump() {
  local options=(--schema=api_key --clean --if-exists --no-owner --no-privileges)
  if use_container; then
    docker compose -f "$REPO_ROOT/docker-compose.yml" exec -T db-email-service \
      pg_dump -U "$DB_USER" -d "$DB_NAME" "${options[@]}"
  else
    PGPASSWORD="$DB_PASSWORD" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
      "${options[@]}"
  fi
}

case "$COMMAND" in
  restore)
    run_psql < "$DUMP_FILE"
    echo "Restored the api_key schema from $DUMP_FILE"
    ;;
  dump)
    # pg_dump >= 16.10 wraps the script in \restrict/\unrestrict, which older psql clients
    # reject as unknown commands -- dropped so the dump restores with any psql 16. They only
    # guard against restoring an untrusted dump; this one is versioned in the repository.
    run_pg_dump | sed -e '/^\\restrict /d' -e '/^\\unrestrict /d' > "$DUMP_FILE"
    echo "Saved the api_key schema to $DUMP_FILE"
    ;;
  *)
    usage
    ;;
esac
