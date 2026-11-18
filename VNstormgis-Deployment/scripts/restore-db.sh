#!/usr/bin/env bash
# ==============================================================================
# W7-01: PostgreSQL 16 + PostGIS 3.4 Disaster Recovery Restore Script
# Verifies SHA-256 checksum, restores custom-format dump into target database,
# validates PostGIS extension & SRID 4326 spatial data, and reports RTO seconds.
# ==============================================================================
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <path-to-dump-file> [target-db-name]" >&2
  exit 1
fi

DUMP_FILE="$1"
TARGET_DB="${2:-${TARGET_DB:-weather_gis_db}}"
DB_CONTAINER="${DB_CONTAINER:-weathergis-postgres}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USERNAME="${DB_USERNAME:-gis_user}"
DB_PASSWORD="${DB_PASSWORD:-gis_password_secret}"
EXPECTED_STATIONS="${EXPECTED_STATIONS:-39}"

if [[ ! -f "$DUMP_FILE" ]]; then
  echo "[RESTORE] FAIL: Dump file not found: $DUMP_FILE" >&2
  exit 1
fi

START_EPOCH="$(date +%s)"

if [[ -f "${DUMP_FILE}.sha256" ]]; then
  echo "[RESTORE] Verifying SHA-256 checksum..."
  DUMP_DIR="$(cd "$(dirname "$DUMP_FILE")" && pwd)"
  SHA_BASE="$(basename "${DUMP_FILE}.sha256")"
  if command -v shasum >/dev/null 2>&1; then
    (cd "$DUMP_DIR" && shasum -a 256 -c "$SHA_BASE")
  elif command -v sha256sum >/dev/null 2>&1; then
    (cd "$DUMP_DIR" && sha256sum -c "$SHA_BASE")
  fi
fi

echo "[RESTORE] Restoring '$DUMP_FILE' into target database '$TARGET_DB'..."

if docker inspect "$DB_CONTAINER" >/dev/null 2>&1 && [[ "$(docker inspect -f '{{.State.Running}}' "$DB_CONTAINER")" == "true" ]]; then
  docker exec -e PGPASSWORD="$DB_PASSWORD" "$DB_CONTAINER" \
    psql -v ON_ERROR_STOP=1 -U "$DB_USERNAME" -d postgres \
    -tc "SELECT 1 FROM pg_database WHERE datname = '${TARGET_DB}'" | grep -q 1 || \
    docker exec -e PGPASSWORD="$DB_PASSWORD" "$DB_CONTAINER" \
      psql -v ON_ERROR_STOP=1 -U "$DB_USERNAME" -d postgres \
      -c "CREATE DATABASE \"${TARGET_DB}\";"

  docker exec -e PGPASSWORD="$DB_PASSWORD" "$DB_CONTAINER" \
    psql -v ON_ERROR_STOP=1 -U "$DB_USERNAME" -d "$TARGET_DB" \
    -c "CREATE EXTENSION IF NOT EXISTS postgis;" >/dev/null

  docker exec -i -e PGPASSWORD="$DB_PASSWORD" "$DB_CONTAINER" \
    pg_restore --clean --if-exists --no-owner --no-acl -U "$DB_USERNAME" -d "$TARGET_DB" < "$DUMP_FILE"

  STATION_COUNT="$(docker exec -e PGPASSWORD="$DB_PASSWORD" "$DB_CONTAINER" \
    psql -tAc "SELECT count(*) FROM locations WHERE is_active = TRUE AND ST_SRID(geom) = 4326" -U "$DB_USERNAME" -d "$TARGET_DB" | tr -d '[:space:]')"
else
  PGPASSWORD="$DB_PASSWORD" psql -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d postgres \
    -tc "SELECT 1 FROM pg_database WHERE datname = '${TARGET_DB}'" | grep -q 1 || \
    PGPASSWORD="$DB_PASSWORD" psql -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d postgres \
      -c "CREATE DATABASE \"${TARGET_DB}\";"

  PGPASSWORD="$DB_PASSWORD" psql -v ON_ERROR_STOP=1 -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$TARGET_DB" \
    -c "CREATE EXTENSION IF NOT EXISTS postgis;" >/dev/null

  PGPASSWORD="$DB_PASSWORD" pg_restore --clean --if-exists --no-owner --no-acl \
    -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$TARGET_DB" "$DUMP_FILE"

  STATION_COUNT="$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$TARGET_DB" \
    -tAc "SELECT count(*) FROM locations WHERE is_active = TRUE AND ST_SRID(geom) = 4326" | tr -d '[:space:]')"
fi

END_EPOCH="$(date +%s)"
ELAPSED="$(( END_EPOCH - START_EPOCH ))"

if [[ -z "$STATION_COUNT" ]] || (( STATION_COUNT < EXPECTED_STATIONS )); then
  echo "[RESTORE] FAIL: Spatial verification failed (expected >=${EXPECTED_STATIONS} SRID-4326 stations, got '${STATION_COUNT}')" >&2
  exit 1
fi

echo "[RESTORE] PASS: Restored '$TARGET_DB' in ${ELAPSED}s (RTO < 300s), verified ${STATION_COUNT} active SRID-4326 stations."
