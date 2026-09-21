#!/usr/bin/env bash
# Runs one instance of blackbox-proxy/ (spec 05-020) -- a reverse proxy that sits in front of
# `app` and always controls Sec-CH-UA*/User-Agent on every forwarded request, letting a browser
# (Swagger UI included) test device-label resolution (spec 05-009) it otherwise can't reach: the
# Fetch Standard forbids page scripts from setting any Sec-* header themselves.
#
# All three options are optional, with defaults matching a plain `docker compose up`/local
# `app` run. Running this script again with different values (most usefully --proxy-port) starts
# a second, independent instance -- each one has its own in-memory device configuration
# (POST/GET /blackbox/proxy/headers), so more than one simulated device at a time means more
# than one instance, not a feature of a single one.
#
# Usage: ./scripts/blackbox-proxy.sh [--target-url URL] [--target-port PORT] [--proxy-port PORT]
# Example: ./scripts/blackbox-proxy.sh --proxy-port 8091
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

TARGET_URL="http://localhost"
TARGET_PORT="8080"
PROXY_PORT="8090"

while [ $# -gt 0 ]; do
  case "$1" in
    --target-url)
      TARGET_URL="$2"
      shift 2
      ;;
    --target-port)
      TARGET_PORT="$2"
      shift 2
      ;;
    --proxy-port)
      PROXY_PORT="$2"
      shift 2
      ;;
    *)
      echo "Usage: $0 [--target-url URL] [--target-port PORT] [--proxy-port PORT]" >&2
      exit 1
      ;;
  esac
done

echo "Starting blackbox-proxy on port ${PROXY_PORT}, forwarding to ${TARGET_URL}:${TARGET_PORT}..."

cd "$REPO_ROOT"
SERVER_PORT="$PROXY_PORT" BLACKBOX_PROXY_TARGET_BASE_URL="${TARGET_URL}:${TARGET_PORT}" \
  exec mvn -pl blackbox-proxy -am spring-boot:run
