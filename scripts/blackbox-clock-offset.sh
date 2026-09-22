#!/usr/bin/env bash
# Boots the `blackbox` environment (docker-compose.yml + docker-compose.blackbox.yml) with the
# `app` container's clock shifted N days into the past, via libfaketime (LD_PRELOAD) -- no
# change to the host clock, no change to any versioned file. Automates the manual command
# documented in README.md ("Gerando dados de teste com uma data no passado"), so the exact
# invocation doesn't need to be retyped/copy-pasted each time (and risk a typo in the offset).
#
# This exists as a shell script deliberately instead of an injectable Clock bean in app code:
# a Clock has to be threaded through *every* call site that reads the current time, and one
# missed call site (a new feature, a library, a forgotten spot) would silently mix real "now"
# into otherwise time-shifted data -- a subtle bug that's easy to introduce and hard to notice.
# Shifting the clock the whole `app` process sees has no such failure mode: every call to
# LocalDate.now()/Instant.now() sees the same shifted time, with no code change to keep in sync.
#
# Usage: ./scripts/blackbox-clock-offset.sh <days-in-the-past>
# Example: ./scripts/blackbox-clock-offset.sh -30
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ $# -ne 1 ]; then
  echo "Usage: $0 <days-in-the-past>" >&2
  echo "Example: $0 -30" >&2
  exit 1
fi

OFFSET_DAYS="$1"

if ! [[ "$OFFSET_DAYS" =~ ^-[0-9]+$ ]]; then
  echo "Error: <days-in-the-past> must be a negative integer (e.g. -30)." >&2
  exit 1
fi

echo "Booting blackbox with the app clock shifted ${OFFSET_DAYS} days..."

cd "$REPO_ROOT"
exec docker compose -f docker-compose.yml -f docker-compose.blackbox.yml run --rm --service-ports \
  -e FAKETIME_OFFSET="${OFFSET_DAYS} days" \
  --entrypoint "sh -c 'apt-get update -qq && apt-get install -y -qq faketime && faketime \"\$FAKETIME_OFFSET\" java -jar app.jar'" \
  app
