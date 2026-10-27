#!/usr/bin/env bash
# Dumps the database into backups/ and prunes old archives.
#
# Usage: ./deploy/scripts/backup.sh [retention_days]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

RETENTION_DAYS="${1:-14}"
BACKUP_DIR="${ROOT}/backups"
STAMP="$(date '+%Y%m%d-%H%M%S')"

# shellcheck disable=SC1091
set -a; . ./.env; set +a

mkdir -p "$BACKUP_DIR"
TARGET="${BACKUP_DIR}/genisource-erp-${STAMP}.sql.gz"

echo "==> Dumping ${DB_NAME} to ${TARGET}"
docker compose exec -T db pg_dump \
  --username "${DB_USER}" \
  --dbname "${DB_NAME}" \
  --no-owner \
  --no-privileges \
  | gzip -9 > "$TARGET"

SIZE="$(du -h "$TARGET" | cut -f1)"
echo "==> Wrote ${TARGET} (${SIZE})"

echo "==> Pruning archives older than ${RETENTION_DAYS} days"
find "$BACKUP_DIR" -name 'genisource-erp-*.sql.gz' -type f -mtime "+${RETENTION_DAYS}" -print -delete || true

echo "==> Done. Restore with: ./deploy/scripts/restore.sh ${TARGET}"
