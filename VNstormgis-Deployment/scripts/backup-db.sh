#!/usr/bin/env bash
# ==============================================================================
# W7-01: PostgreSQL 16 + PostGIS 3.4 Production Backup Automation Script
# Creates compressed custom-format dump (.dump), SHA-256 checksum, verifies
# archive TOC via pg_restore --list, and enforces retention cleanup.
# ==============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$ROOT_DIR/artifacts/backups}"
DB_CONTAINER="${DB_CONTAINER:-weathergis-postgres}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-weather_gis_db}"
DB_USERNAME="${DB_USERNAME:-gis_user}"
DB_PASSWORD="${DB_PASSWORD:-gis_password_secret}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DUMP_FILE="$BACKUP_DIR/${DB_NAME}_${TIMESTAMP}.dump"
SHA_FILE="${DUMP_FILE}.sha256"

echo "[BACKUP] Starting PostGIS backup for database '$DB_NAME'..."

if docker inspect "$DB_CONTAINER" >/dev/null 2>&1 && [[ "$(docker inspect -f '{{.State.Running}}' "$DB_CONTAINER")" == "true" ]]; then
  docker exec -e PGPASSWORD="$DB_PASSWORD" "$DB_CONTAINER" \
    pg_dump -Fc --no-owner --no-acl -U "$DB_USERNAME" -d "$DB_NAME" > "$DUMP_FILE"
  TOC_LINES="$(docker exec -i "$DB_CONTAINER" pg_restore --list < "$DUMP_FILE" | wc -l | tr -d ' ')"
else
  PGPASSWORD="$DB_PASSWORD" pg_dump -Fc --no-owner --no-acl \
    -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" -f "$DUMP_FILE"
  TOC_LINES="$(pg_restore --list "$DUMP_FILE" | wc -l | tr -d ' ')"
fi

if [[ ! -s "$DUMP_FILE" ]] || (( TOC_LINES < 10 )); then
  echo "[BACKUP] FAIL: Backup archive is empty or corrupted (TOC lines=$TOC_LINES)" >&2
  exit 1
fi

if command -v shasum >/dev/null 2>&1; then
  (cd "$BACKUP_DIR" && shasum -a 256 "$(basename "$DUMP_FILE")" > "$(basename "$SHA_FILE")")
elif command -v sha256sum >/dev/null 2>&1; then
  (cd "$BACKUP_DIR" && sha256sum "$(basename "$DUMP_FILE")" > "$(basename "$SHA_FILE")")
fi

find "$BACKUP_DIR" -name "${DB_NAME}_*.dump*" -type f -mtime +"$BACKUP_RETENTION_DAYS" -delete 2>/dev/null || true

SIZE_BYTES="$(wc -c < "$DUMP_FILE" | tr -d ' ')"
echo "[BACKUP] PASS: Created $DUMP_FILE (${SIZE_BYTES} bytes, TOC=${TOC_LINES} entries)"
echo "$DUMP_FILE"
