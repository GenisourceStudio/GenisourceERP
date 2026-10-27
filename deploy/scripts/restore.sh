#!/usr/bin/env bash
# Restores a dump produced by backup.sh.
#
# Usage: ./deploy/scripts/restore.sh backups/genisource-erp-20260101-030000.sql.gz
#
# This overwrites the current database. The script asks for confirmation unless
# FORCE=1 is set, which is what an unattended restore drill would use.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

if [ $# -ne 1 ]; then
  echo "Usage: $0 <backup-file.sql.gz>" >&2
  exit 2
fi

ARCHIVE="$1"
if [ ! -f "$ARCHIVE" ]; then
  echo "Backup file not found: $ARCHIVE" >&2
  exit 1
fi

# shellcheck disable=SC1091
set -a; . ./.env; set +a

if [ "${FORCE:-0}" != "1" ]; then
  echo "This will REPLACE the current contents of database '${DB_NAME}'."
  read -r -p "Type the database name to confirm: " CONFIRM
  if [ "$CONFIRM" != "${DB_NAME}" ]; then
    echo "Aborted."
    exit 1
  fi
fi

echo "==> Stopping the application to release connections"
docker compose stop app

echo "==> Restoring ${ARCHIVE}"
gunzip -c "$ARCHIVE" | docker compose exec -T db psql \
  --username "${DB_USER}" \
  --dbname "${DB_NAME}" \
  --quiet --no-psqlrc

echo "==> Starting the application"
docker compose start app

echo "==> Restore finished"
