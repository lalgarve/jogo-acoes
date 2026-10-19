#!/usr/bin/env bash
# Stops the environment scripts/cloud-up.sh starts in a Claude Code cloud session (spec 05-052):
#
#   1. config-server, email-service and app -- only the processes cloud-up.sh recorded, by PID
#   2. the LocalStack container and the Lambda containers it started
#   3. docker-compose.yml's containers and volumes (db, db-email-service, dns)
#
# Idempotent: with nothing running it does nothing and still exits 0. Logs are kept in the state
# directory for a look after the fact; cloud-up.sh clears them on its next run.
#
# Usage: ./scripts/cloud-down.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="${JOGO_ACOES_CLOUD_DIR:-/tmp/jogo-acoes-cloud}"
PID_DIR="$STATE_DIR/pids"

LOCALSTACK_CONTAINER="jogo-acoes-localstack"

# By recorded PID, never by name: `pkill -f <jar>` also matches the shell running the command
# that contains the jar name, and kills it.
stop_java() {
  local pid_file name pid
  for pid_file in "$PID_DIR"/*.pid; do
    [ -e "$pid_file" ] || continue
    name="$(basename "$pid_file" .pid)"
    pid="$(cat "$pid_file")"
    if kill -0 "$pid" 2>/dev/null; then
      echo "Stopping $name (pid $pid)"
      kill "$pid"
      for _ in $(seq 1 30); do
        kill -0 "$pid" 2>/dev/null || break
        sleep 1
      done
      if kill -0 "$pid" 2>/dev/null; then
        kill -9 "$pid"
      fi
    fi
    rm -f "$pid_file"
  done
}

stop_containers() {
  # Without a Docker daemon there are no containers to stop (a recycled session container loses
  # them along with the daemon).
  if ! docker info >/dev/null 2>&1; then
    return
  fi
  local lambdas
  # LocalStack names the Lambda containers after its own container (MAIN_CONTAINER_NAME in
  # cloud-up.sh), so this only matches the ones it started.
  lambdas="$(docker ps -aq --filter "name=^${LOCALSTACK_CONTAINER}" || true)"
  if [ -n "$lambdas" ]; then
    echo "Removing LocalStack and its Lambda containers"
    # shellcheck disable=SC2086
    docker rm -f $lambdas >/dev/null
  fi
  cd "$REPO_ROOT"
  # -f explicitly, so an exported COMPOSE_FILE (e.g. from scripts/docker-up.sh) doesn't change
  # which files are read.
  docker compose -f docker-compose.yml down -v --remove-orphans
}

stop_java
stop_containers
