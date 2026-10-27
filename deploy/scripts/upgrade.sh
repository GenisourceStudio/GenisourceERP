#!/usr/bin/env bash
# Rebuilds the images from the current working tree and restarts the stack.
#
# Flyway runs on boot, so schema migrations apply automatically. A database backup is
# taken first: migrations are forward-only and a rollback means restoring that dump.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

SKIP_BACKUP="false"
if [ "${1:-}" = "--skip-backup" ]; then
  SKIP_BACKUP="true"
fi

if [ "$SKIP_BACKUP" != "true" ]; then
  echo "==> Taking a pre-upgrade backup"
  ./deploy/scripts/backup.sh
fi

echo "==> Building images"
docker compose build

echo "==> Restarting services"
docker compose up -d

echo "==> Waiting for the API to become healthy"
WEB_PORT_VALUE="$(grep -E '^WEB_PORT=' .env | cut -d= -f2)"
WEB_PORT_VALUE="${WEB_PORT_VALUE:-8080}"

for attempt in $(seq 1 60); do
  if curl -fsS --noproxy '*' "http://127.0.0.1:${WEB_PORT_VALUE}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
    echo "==> Upgrade finished, API is healthy"
    exit 0
  fi
  if [ "$attempt" -eq 60 ]; then
    echo "The API did not become healthy. Check: docker compose logs --tail=200 app" >&2
    echo "Roll back with: ./deploy/scripts/restore.sh <pre-upgrade dump>" >&2
    exit 1
  fi
  sleep 5
done
