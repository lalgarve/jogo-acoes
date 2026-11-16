#!/usr/bin/env bash
# Starts the docker environment in the order it needs (spec 05-050), with the options of a
# configuration file instead of a long command line:
#
#   1. db, db-email-service, localstack and dns
#   2. scripts/test-api-key.sh restore -- the test API keys
#   3. email-service (and config-server, which it depends on)
#   4. scripts/test-api-key.sh restore again -- the test client's sender address, in a table that
#      only exists once email-service has run its migrations
#   5. app
#   6. adminer, ses-viewer and log-viewer, with TOOLS=true
#
# Without the restores in between, email-service answers 401 and app refuses to start: that's why
# a bare `docker compose up` doesn't work (memory/constitution.md, "Verificação manual").
#
# Configuration: docker-up.conf at the repository root (local, not committed), or
# docker-up.conf.example (the committed template, LocalStack Community) when there is no
# docker-up.conf. See docker-up.conf.example for the keys.
#
#   --config FILE  read FILE instead of docker-up.conf
#   --reset        `docker compose down -v` first: empty databases and a fresh LocalStack
#
# Usage: ./scripts/docker-up.sh [--config FILE] [--reset]
# Example: ./scripts/docker-up.sh --reset
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_CONFIG="$REPO_ROOT/docker-up.conf"
TEMPLATE_CONFIG="$REPO_ROOT/docker-up.conf.example"

usage() {
  echo "Usage: $0 [--config FILE] [--reset]" >&2
  exit 1
}

fail() {
  echo "docker-up: $*" >&2
  exit 1
}

CONFIG=""
RESET=false
while [ $# -gt 0 ]; do
  case "$1" in
    --config)
      [ $# -ge 2 ] || usage
      CONFIG="$2"
      shift 2
      ;;
    --reset)
      RESET=true
      shift
      ;;
    *)
      usage
      ;;
  esac
done

if [ -z "$CONFIG" ]; then
  if [ -f "$LOCAL_CONFIG" ]; then
    CONFIG="$LOCAL_CONFIG"
  else
    CONFIG="$TEMPLATE_CONFIG"
    echo "No docker-up.conf, using the template docker-up.conf.example" \
      "(cp docker-up.conf.example docker-up.conf to change it)"
  fi
fi
[ -f "$CONFIG" ] || fail "configuration file not found: $CONFIG"

BLACKBOX=""
LOCALSTACK_PRO=""
CONFIG_TOKEN=""
BUILD=""
TOOLS=""

# KEY=value lines only, parsed instead of sourced: the file is configuration, not a script.
line_number=0
while IFS= read -r line || [ -n "$line" ]; do
  line_number=$((line_number + 1))
  line="${line%$'\r'}"
  case "$line" in
    '' | '#'*) continue ;;
  esac
  [[ "$line" == *=* ]] || fail "$CONFIG:$line_number: expected KEY=value, got: $line"
  key="${line%%=*}"
  value="${line#*=}"
  # Optional surrounding quotes, as in a .env file.
  if [[ "$value" =~ ^\"(.*)\"$ || "$value" =~ ^\'(.*)\'$ ]]; then
    value="${BASH_REMATCH[1]}"
  fi
  case "$key" in
    BLACKBOX) BLACKBOX="$value" ;;
    LOCALSTACK_PRO) LOCALSTACK_PRO="$value" ;;
    LOCALSTACK_AUTH_TOKEN) CONFIG_TOKEN="$value" ;;
    BUILD) BUILD="$value" ;;
    TOOLS) TOOLS="$value" ;;
    *) fail "$CONFIG:$line_number: unknown key $key" ;;
  esac
done < "$CONFIG"

for name in BLACKBOX LOCALSTACK_PRO BUILD TOOLS; do
  case "${!name}" in
    true | false) ;;
    '') fail "$CONFIG: $name is missing (true or false)" ;;
    *) fail "$CONFIG: $name must be true or false, got: ${!name}" ;;
  esac
done

COMPOSE_FILE="docker-compose.yml"
[ "$BLACKBOX" = true ] && COMPOSE_FILE="$COMPOSE_FILE:docker-compose.blackbox.yml"
if [ "$LOCALSTACK_PRO" = true ]; then
  # The file's token wins; an empty one falls back to the exported variable.
  if [ -n "$CONFIG_TOKEN" ]; then
    LOCALSTACK_AUTH_TOKEN="$CONFIG_TOKEN"
  fi
  [ -n "${LOCALSTACK_AUTH_TOKEN:-}" ] ||
    fail "LOCALSTACK_PRO=true needs a token: set LOCALSTACK_AUTH_TOKEN in $CONFIG or export it" \
      "(or set LOCALSTACK_PRO=false to use LocalStack Community)"
  export LOCALSTACK_AUTH_TOKEN
  COMPOSE_FILE="$COMPOSE_FILE:docker-compose.localstack-pro.yml"
fi
export COMPOSE_FILE

BUILD_FLAG=()
[ "$BUILD" = true ] && BUILD_FLAG=(--build)

cd "$REPO_ROOT"

# On failure, LocalStack's own reason (e.g. Pro's "License activation failed", or an init script
# that failed and left it unhealthy, spec 05-055) is in its log, not in Compose's output.
compose_up() {
  if ! docker compose up -d --wait "$@"; then
    local localstack_id localstack_state
    localstack_id="$(docker compose ps -a -q localstack 2>/dev/null || true)"
    if [ -n "$localstack_id" ]; then
      localstack_state="$(docker inspect -f '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{end}}' "$localstack_id")"
      case "$localstack_state" in
        exited*) localstack_state=exited ;;
        *unhealthy) localstack_state=unhealthy ;;
        *) localstack_state="" ;;
      esac
      # Without the healthcheck's own requests, one every 5 seconds, which would push the reason
      # out of the last lines.
      if [ -n "$localstack_state" ]; then
        echo "docker-up: localstack $localstack_state; its last log lines:" >&2
        docker compose logs --no-log-prefix localstack 2>&1 |
          grep -v 'GET /_localstack/init/ready' | tail -n 15 >&2 || true
      fi
    fi
    fail "docker compose up $* failed"
  fi
}

echo "Compose files: $COMPOSE_FILE"
if [ "$LOCALSTACK_PRO" = true ]; then
  echo "LocalStack: Pro"
else
  echo "LocalStack: Community"
fi

if [ "$RESET" = true ]; then
  docker compose down -v
fi

compose_up "${BUILD_FLAG[@]}" db db-email-service localstack dns
./scripts/test-api-key.sh restore
compose_up "${BUILD_FLAG[@]}" email-service
./scripts/test-api-key.sh restore
compose_up "${BUILD_FLAG[@]}" app
if [ "$TOOLS" = true ]; then
  compose_up "${BUILD_FLAG[@]}" adminer ses-viewer log-viewer
fi

echo
echo "Environment up. For other docker compose commands in this shell, use the same files:"
echo "  export COMPOSE_FILE=$COMPOSE_FILE"
if [ "$LOCALSTACK_PRO" = true ]; then
  echo "  export LOCALSTACK_AUTH_TOKEN=...   # the same token; Compose refuses the Pro overlay without it"
fi
