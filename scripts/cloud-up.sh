#!/usr/bin/env bash
# Starts the whole docker environment inside a Claude Code cloud session (spec 05-052), where
# `docker compose up` and scripts/docker-up.sh don't work: the session only reaches the internet
# through a proxy on 127.0.0.1 with its own CA, which containers on the Compose network can't
# reach, so the Dockerfile builds can't download dependencies and LocalStack Pro can't activate
# its license. Instead:
#
#   1. build on the host (Maven already trusts the proxy there)
#   2. db, db-email-service and dns from docker-compose.yml (no internet needed)
#   3. LocalStack Pro on the host network, with the proxy and its CA, running
#      docker/localstack/init/ and deploying email-lambda's function.zip
#   4. config-server, then test-api-key.sh restore, email-service, restore again and app (profiles
#      docker,blackbox), all as `java -jar` on the host, configured like docker-compose.yml and
#      docker-compose.blackbox.yml with service names replaced by localhost
#
# Always starts from scratch (runs scripts/cloud-down.sh first): email-service only pushes changed
# templates to SES, so a new LocalStack next to an old email-service database would have no
# templates at all. Logs and PIDs go to the state directory ($JOGO_ACOES_CLOUD_DIR, default
# /tmp/jogo-acoes-cloud), outside the repository.
#
# Needs LOCALSTACK_AUTH_TOKEN (set in the cloud environment's variables); never printed. Outside a
# cloud session, use scripts/docker-up.sh.
#
#   --no-build  reuse the artifacts already in */target instead of running Maven
#
# Usage: ./scripts/cloud-up.sh [--no-build]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="${JOGO_ACOES_CLOUD_DIR:-/tmp/jogo-acoes-cloud}"
LOG_DIR="$STATE_DIR/logs"
PID_DIR="$STATE_DIR/pids"
LAMBDA_DIR="$STATE_DIR/lambda"

PROXY_CA="/root/.ccr/ca-bundle.crt"
LOCALSTACK_CONTAINER="jogo-acoes-localstack"
LOCALSTACK_IMAGE="localstack/localstack-pro:4"
LOCALSTACK_URL="http://localhost:4566"
SERVICE_TIMEOUT_SECONDS=120
LOCALSTACK_TIMEOUT_SECONDS=180

# Same test values as docker-compose.yml, versioned on purpose there -- none is a real secret.
# When a variable changes in docker-compose.yml or docker-compose.blackbox.yml, change it here too.
CONFIG_SERVER_USERNAME="config"
CONFIG_SERVER_PASSWORD="config-docker-not-a-secret"
EMAIL_SENDER_ADDRESS="no-reply@jogo-acoes.example"
EMAIL_SERVICE_API_KEY="dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU"
API_KEY_HMAC_PEPPER="jogo-acoes-test-pepper-not-a-secret"
ADMIN_EMAIL="success+admin@simulator.amazonses.com"

usage() {
  echo "Usage: $0 [--no-build]" >&2
  exit 1
}

fail() {
  echo "cloud-up: $*" >&2
  exit 1
}

BUILD=true
while [ $# -gt 0 ]; do
  case "$1" in
    --no-build)
      BUILD=false
      shift
      ;;
    *)
      usage
      ;;
  esac
done

# --- Checks: nothing is started or stopped before these pass -------------------------------------

if [ -z "${HTTPS_PROXY:-}" ] || [ ! -f "$PROXY_CA" ]; then
  fail "this script only works in a Claude Code cloud session (HTTPS_PROXY and $PROXY_CA);" \
    "elsewhere, use scripts/docker-up.sh"
fi
# No fallback to LocalStack Community: a run on Community proves nothing about Pro (spec 05-052).
if [ -z "${LOCALSTACK_AUTH_TOKEN:-}" ]; then
  fail "LOCALSTACK_AUTH_TOKEN is not set: add it to the cloud environment's variables"
fi

cd "$REPO_ROOT"

# One artifact per module; a missing one with --no-build is an error, not a silent rebuild.
artifact() {
  local pattern="$1" found
  found="$(compgen -G "$pattern" | head -n 1 || true)"
  [ -n "$found" ] || fail "$pattern not found (run without --no-build)"
  echo "$found"
}

# --- Docker daemon ---------------------------------------------------------------------------------

mkdir -p "$LOG_DIR" "$PID_DIR" "$LAMBDA_DIR"

if ! docker info >/dev/null 2>&1; then
  echo "Starting the Docker daemon"
  # Inherits HTTPS_PROXY from this shell, which is what lets it pull images.
  setsid nohup dockerd >"$STATE_DIR/dockerd.log" 2>&1 </dev/null &
  for _ in $(seq 1 30); do
    docker info >/dev/null 2>&1 && break
    sleep 1
  done
  docker info >/dev/null 2>&1 || fail "the Docker daemon did not start; see $STATE_DIR/dockerd.log"
fi

# --- From scratch ------------------------------------------------------------------------------------

./scripts/cloud-down.sh
rm -f "$LOG_DIR"/*.log

# From here on a failure leaves part of the environment running.
fail() {
  echo "cloud-up: $*" >&2
  echo "cloud-up: what already started is still running; ./scripts/cloud-down.sh stops it" >&2
  exit 1
}

# --- Build -------------------------------------------------------------------------------------------

if [ "$BUILD" = true ]; then
  echo "Building (log: $LOG_DIR/build.log)"
  if ! { ./scripts/install-api-key-lib.sh && mvn -B package -DskipTests; } >"$LOG_DIR/build.log" 2>&1; then
    tail -n 30 "$LOG_DIR/build.log" >&2
    fail "build failed; see $LOG_DIR/build.log"
  fi
fi
CONFIG_SERVER_JAR="$(artifact 'config-server/target/config-server-*.jar')"
EMAIL_SERVICE_JAR="$(artifact 'email-service/target/email-service-*.jar')"
APP_JAR="$(artifact 'app/target/jogo-acoes-*.jar')"
FUNCTION_ZIP="$(artifact 'email-lambda/target/function.zip')"
cp "$FUNCTION_ZIP" "$LAMBDA_DIR/function.zip"

# --- Postgres and the test DNS ---------------------------------------------------------------------

echo "Starting db, db-email-service and dns"
docker compose -f docker-compose.yml up -d --wait db db-email-service dns

# --- Java services -----------------------------------------------------------------------------------

# setsid + nohup: the service outlives this script and the shell that ran it.
start_java() {
  local name="$1" jar="$2"
  shift 2
  echo "Starting $name (log: $LOG_DIR/$name.log)"
  setsid nohup env "$@" java -jar "$jar" >"$LOG_DIR/$name.log" 2>&1 </dev/null &
  echo $! >"$PID_DIR/$name.pid"
}

# Ready on Spring Boot's "Started ...Application" line; the log, not an open port, so a failure
# comes with its reason.
wait_java() {
  local name="$1" log="$LOG_DIR/$1.log" pid
  pid="$(cat "$PID_DIR/$name.pid")"
  for _ in $(seq 1 "$SERVICE_TIMEOUT_SECONDS"); do
    if grep -q 'Started .*Application' "$log"; then
      return
    fi
    if ! kill -0 "$pid" 2>/dev/null || grep -q 'APPLICATION FAILED TO START' "$log"; then
      break
    fi
    sleep 1
  done
  echo "cloud-up: $name did not start; its last log lines:" >&2
  tail -n 30 "$log" >&2
  fail "$name did not start; see $log"
}

start_java config-server "$CONFIG_SERVER_JAR" \
  CONFIG_REPO_LOCATION="file:$REPO_ROOT/config-repo/" \
  CONFIG_SERVER_USERNAME="$CONFIG_SERVER_USERNAME" \
  CONFIG_SERVER_PASSWORD="$CONFIG_SERVER_PASSWORD"

# --- LocalStack Pro ------------------------------------------------------------------------------------

echo "Starting LocalStack Pro (log: docker logs $LOCALSTACK_CONTAINER)"
# --network host: the only way for LocalStack to reach the session's proxy and activate the
# license. The Lambda containers stay on the bridge network (LocalStack can't find a Lambda
# container's address on the host network) and reach LocalStack through the host gateway.
docker run -d --name "$LOCALSTACK_CONTAINER" --network host \
  --add-host localhost.localstack.cloud:127.0.0.1 \
  --add-host sqs.us-east-1.localhost.localstack.cloud:127.0.0.1 \
  -e LOCALSTACK_AUTH_TOKEN \
  -e MAIN_CONTAINER_NAME="$LOCALSTACK_CONTAINER" \
  -e SERVICES=sqs,ses,s3,lambda \
  -e EMAIL_SENDER_ADDRESS="$EMAIL_SENDER_ADDRESS" \
  -e LAMBDA_DOCKER_NETWORK=bridge \
  -e LAMBDA_DOCKER_FLAGS="--add-host localhost.localstack.cloud:host-gateway" \
  -e HTTPS_PROXY \
  -e NO_PROXY="localhost,127.0.0.1,localhost.localstack.cloud,.localhost.localstack.cloud" \
  -e REQUESTS_CA_BUNDLE=/etc/proxy-ca.crt \
  -e SSL_CERT_FILE=/etc/proxy-ca.crt \
  -e CURL_CA_BUNDLE=/etc/proxy-ca.crt \
  -v "$PROXY_CA:/etc/proxy-ca.crt:ro" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$REPO_ROOT/docker/localstack/init:/etc/localstack/init/ready.d:ro" \
  -v "$LAMBDA_DIR:/tmp/email-lambda-target:ro" \
  "$LOCALSTACK_IMAGE" >/dev/null

localstack_failed() {
  echo "cloud-up: $1; LocalStack's last log lines:" >&2
  docker logs --tail 15 "$LOCALSTACK_CONTAINER" >&2 2>&1 || true
  fail "$1"
}

# The container is healthy before the init scripts have created the queue, verified the sender and
# deployed email-lambda: wait for /_localstack/init/ready instead.
init_state=""
for _ in $(seq 1 "$LOCALSTACK_TIMEOUT_SECONDS"); do
  if [ "$(docker inspect -f '{{.State.Status}}' "$LOCALSTACK_CONTAINER")" = exited ]; then
    localstack_failed "LocalStack exited"
  fi
  init_state="$(curl -s "$LOCALSTACK_URL/_localstack/init/ready" | python3 -c '
import json, sys
try:
    ready = json.load(sys.stdin)
except ValueError:
    print("waiting")
    sys.exit()
states = {script["name"]: script["state"] for script in ready.get("scripts", [])}
if any(state == "ERROR" for state in states.values()):
    print("error " + " ".join(f"{name}={state}" for name, state in sorted(states.items())))
elif ready.get("completed") and states and all(state == "SUCCESSFUL" for state in states.values()):
    print("done")
else:
    print("waiting")
' 2>/dev/null || echo waiting)"
  case "$init_state" in
    done) break ;;
    error*) localstack_failed "a LocalStack init script failed (${init_state#error })" ;;
  esac
  sleep 1
done
[ "$init_state" = done ] || localstack_failed "LocalStack init scripts did not finish in ${LOCALSTACK_TIMEOUT_SECONDS}s"

# --- email-service and app -------------------------------------------------------------------------

wait_java config-server

./scripts/test-api-key.sh restore

start_java email-service "$EMAIL_SERVICE_JAR" \
  SPRING_PROFILES_ACTIVE=docker \
  SERVER_PORT=8082 \
  CONFIG_SERVER_URL=http://localhost:8888 \
  CONFIG_SERVER_USERNAME="$CONFIG_SERVER_USERNAME" \
  CONFIG_SERVER_PASSWORD="$CONFIG_SERVER_PASSWORD" \
  SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/email_service \
  SPRING_DATASOURCE_USERNAME=email_service_admin \
  SPRING_DATASOURCE_PASSWORD=email_service_admin \
  SPRING_CLOUD_AWS_SES_ENDPOINT="$LOCALSTACK_URL" \
  SPRING_CLOUD_AWS_SQS_ENDPOINT="$LOCALSTACK_URL" \
  EMAIL_QUEUE_NAME=jogo-acoes-email-commands \
  EMAIL_DOMAIN_CHECK_DNS_SERVERS=127.0.0.1:1053 \
  API_KEY_HMAC_PEPPER="$API_KEY_HMAC_PEPPER"
wait_java email-service

./scripts/test-api-key.sh restore

start_java app "$APP_JAR" \
  SPRING_PROFILES_ACTIVE=docker,blackbox \
  ADMIN_EMAIL="$ADMIN_EMAIL" \
  CONFIG_SERVER_URL=http://localhost:8888 \
  CONFIG_SERVER_USERNAME="$CONFIG_SERVER_USERNAME" \
  CONFIG_SERVER_PASSWORD="$CONFIG_SERVER_PASSWORD" \
  SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/jogo_acoes \
  SPRING_DATASOURCE_USERNAME=jogo_acoes_admin \
  SPRING_DATASOURCE_PASSWORD=jogo_acoes_admin \
  EMAIL_SERVICE_BASE_URL=http://localhost:8082/api \
  EMAIL_SENDER=email-service \
  EMAIL_SERVICE_API_KEY="$EMAIL_SERVICE_API_KEY"
wait_java app

echo
echo "Environment up (LocalStack Pro):"
echo "  app            http://localhost:8080/api"
echo "  email-service  http://localhost:8082/api"
echo "  LocalStack     $LOCALSTACK_URL"
echo "Logs and PIDs: $STATE_DIR"
echo "Blackbox suite: ./scripts/cloud-blackbox-tests.sh   Stop: ./scripts/cloud-down.sh"
