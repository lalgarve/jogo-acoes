#!/usr/bin/env bash
# Shows whether LocalStack's init (docker/localstack/init/) left everything the docker environment
# needs: each ready.d script's state, email-lambda's function state and its event source mapping
# from the send queue. Exits 1 if any of them is not as expected (spec 05-055).
#
# localstack's healthcheck in docker-compose.yml already fails on a broken init; this script is
# what puts the result in CI's log (`docker compose up -d` never shows the output of the scripts
# that run inside the container) and gives a manual check the same answer.
#
# Runs everything inside the LocalStack container, which has python3 and awslocal: by default the
# Compose service localstack; LOCALSTACK_CONTAINER names another container instead (e.g.
# jogo-acoes-localstack, started by scripts/cloud-up.sh).
#
# Usage: ./scripts/check-localstack.sh
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

FUNCTION_NAME=EmailLambda
QUEUE_NAME=jogo-acoes-email-commands

in_localstack() {
  if [ -n "${LOCALSTACK_CONTAINER:-}" ]; then
    docker exec "$LOCALSTACK_CONTAINER" "$@"
  else
    docker compose exec -T localstack "$@"
  fi
}

ok=true

echo "Init scripts (/_localstack/init/ready):"
# Prints one "name state" line per script, and "completed false" while the init is still running.
if ! init_states="$(in_localstack python3 -c '
import json, urllib.request
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
ready = json.load(opener.open("http://localhost:4566/_localstack/init/ready"))
for script in ready.get("scripts", []):
    print(script["name"], script["state"])
if not ready.get("completed"):
    print("completed false")
')"; then
  echo "  could not read /_localstack/init/ready"
  ok=false
elif [ -z "$init_states" ]; then
  echo "  no init script ran"
  ok=false
else
  while read -r name state; do
    echo "  $name: $state"
    [ "$state" = SUCCESSFUL ] || ok=false
  done <<<"$init_states"
fi

function_state="$(in_localstack awslocal lambda get-function --function-name "$FUNCTION_NAME" \
  --query 'Configuration.State' --output text 2>/dev/null)" || function_state="missing"
echo "$FUNCTION_NAME: $function_state"
[ "$function_state" = Active ] || ok=false

mapping="$(in_localstack awslocal lambda list-event-source-mappings --function-name "$FUNCTION_NAME" \
  --query 'EventSourceMappings[].[State,EventSourceArn]' --output text 2>/dev/null)" || mapping=""
mapping_state="$(awk -v queue=":$QUEUE_NAME" '$2 ~ queue "$" { print $1 }' <<<"$mapping")"
echo "Event source mapping from $QUEUE_NAME: ${mapping_state:-missing}"
[ "$mapping_state" = Enabled ] || ok=false

if [ "$ok" = true ]; then
  echo "check-localstack: LocalStack is ready"
else
  echo "check-localstack: LocalStack's init is incomplete; see docker compose logs localstack" >&2
  exit 1
fi
