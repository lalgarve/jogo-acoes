#!/usr/bin/env bash
# Sets (or replaces) the sender address of one email-service client (spec 05-031): every e-mail
# the client sends through POST /emails goes out from this address. Operations owns this value,
# not the client, so it is never set through the HTTP API -- same role the api-key CLI plays for
# the keys.
#
# The address is only checked for e-mail shape. Nothing checks it against SES: an address that is
# not a verified SES identity is accepted here and fails later, in email-lambda (plan.md, "Riscos").
#
# Writes the email_service.client_sender row, which the email-service migrations create: the
# service must have started (Flyway) at least once against the database first.
#
# Uses the running db-email-service container (docker compose) when there is one; otherwise the
# local psql against --host/--port, as EMAIL_SERVICE_DB_USER (default email_service_admin) with
# PGPASSWORD (default the docker test password) -- how operations points it at a real database.
#
# Usage: ./scripts/set-email-sender.sh CLIENT ADDRESS [--host HOST] [--port PORT]
# Example: ./scripts/set-email-sender.sh jogo-acoes no-reply@jogo-acoes.example
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

DB_NAME="${EMAIL_SERVICE_DB_NAME:-email_service}"
DB_USER="${EMAIL_SERVICE_DB_USER:-email_service_admin}"
DB_HOST="localhost"
DB_PORT="5433"

usage() {
  echo "Usage: $0 CLIENT ADDRESS [--host HOST] [--port PORT]" >&2
  exit 1
}

[ $# -ge 2 ] || usage
CLIENT="$1"
ADDRESS="$2"
shift 2

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

[ -n "$CLIENT" ] || { echo "CLIENT must not be empty" >&2; exit 1; }

# One @, something before it, a dotted domain after it, no whitespace -- the shape check the spec
# asks for, not full RFC 5322.
if ! [[ "$ADDRESS" =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ ]] || [ ${#ADDRESS} -gt 320 ]; then
  echo "Not an e-mail address: $ADDRESS" >&2
  exit 1
fi

use_container() {
  [ -n "$(docker compose -f "$REPO_ROOT/docker-compose.yml" ps -q db-email-service 2>/dev/null)" ]
}

run_psql() {
  if use_container; then
    docker compose -f "$REPO_ROOT/docker-compose.yml" exec -T db-email-service \
      psql -v ON_ERROR_STOP=1 -q -U "$DB_USER" -d "$DB_NAME" "$@"
  else
    PGPASSWORD="${PGPASSWORD:-email_service_admin}" psql -v ON_ERROR_STOP=1 -q -h "$DB_HOST" \
      -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" "$@"
  fi
}

# psql variables (:'client'), not string interpolation: the values are quoted by psql itself.
run_psql -v client="$CLIENT" -v address="$ADDRESS" <<'SQL'
INSERT INTO email_service.client_sender (client_id, address, created_at, updated_at)
VALUES (:'client', :'address', now() AT TIME ZONE 'UTC', now() AT TIME ZONE 'UTC')
ON CONFLICT (client_id) DO UPDATE
    SET address = EXCLUDED.address, updated_at = EXCLUDED.updated_at;
SQL

echo "Sender address of client '$CLIENT' set to $ADDRESS"
