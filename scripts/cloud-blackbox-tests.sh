#!/usr/bin/env bash
# Runs the blackbox suite (blackbox-tests/, behave + pytest) against the environment
# scripts/cloud-up.sh started in a Claude Code cloud session (spec 05-052):
#
#   1. a Python venv in the state directory, created once
#   2. the API client regenerated from docs/openapi.yaml on every run (blackbox-tests/README.md),
#      into the state directory, and installed with the suite's own dependencies
#   3. LocalStack's SES message store emptied: a leftover e-mail to the same address (the seeded
#      administrator gets one per login) would make the suite read an old, already invalidated link
#   4. behave, then pytest -- both always run; exits 1 if either fails
#
# Arguments are passed to behave (e.g. a feature file or --tags).
#
# Usage: ./scripts/cloud-blackbox-tests.sh [behave arguments]
# Example: ./scripts/cloud-blackbox-tests.sh features/public_competition_entry.feature
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="${JOGO_ACOES_CLOUD_DIR:-/tmp/jogo-acoes-cloud}"
VENV_DIR="$STATE_DIR/venv"
CLIENT_DIR="$STATE_DIR/generated_client"
TESTS_DIR="$REPO_ROOT/blackbox-tests"

APP_URL="http://localhost:8080/api"
LOCALSTACK_URL="http://localhost:4566"

fail() {
  echo "cloud-blackbox-tests: $*" >&2
  exit 1
}

if [ "$(curl -s -o /dev/null -w '%{http_code}' "$APP_URL/swagger-ui/index.html")" != 200 ]; then
  fail "app is not answering on $APP_URL; start the environment with ./scripts/cloud-up.sh"
fi

if [ ! -x "$VENV_DIR/bin/python" ]; then
  echo "Creating the Python venv ($VENV_DIR)"
  python3 -m venv "$VENV_DIR"
  "$VENV_DIR/bin/pip" install -q openapi-python-client
fi

echo "Generating the API client from docs/openapi.yaml"
"$VENV_DIR/bin/openapi-python-client" generate \
  --path "$REPO_ROOT/docs/openapi.yaml" \
  --output-path "$CLIENT_DIR" \
  --overwrite --meta setup \
  --config "$TESTS_DIR/openapi-python-client-config.yaml" >/dev/null
"$VENV_DIR/bin/pip" install -q -e "$CLIENT_DIR" -e "$TESTS_DIR"

curl -sf -X DELETE "$LOCALSTACK_URL/_aws/ses" >/dev/null || fail "could not empty LocalStack's SES store"

cd "$TESTS_DIR"
status=0
"$VENV_DIR/bin/behave" "$@" || status=1
"$VENV_DIR/bin/pytest" -q || status=1
exit "$status"
