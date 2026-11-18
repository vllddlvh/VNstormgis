#!/usr/bin/env bash
# ==============================================================================
# W7-01: Full-Stack Operational Health Check Script
# Checks PostGIS, Redis, Backend Actuator Health, Core GeoJSON APIs, and optional
# Nginx Reverse Proxy (/healthz).
# ==============================================================================
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
NGINX_URL="${NGINX_URL:-http://127.0.0.1:80}"
CHECK_NGINX="${CHECK_NGINX:-0}"
DB_CONTAINER="${DB_CONTAINER:-weathergis-postgres}"
REDIS_CONTAINER="${REDIS_CONTAINER:-weathergis-redis}"
DB_USERNAME="${DB_USERNAME:-gis_user}"
DB_NAME="${DB_NAME:-weather_gis_db}"

echo "[HEALTH] Checking VNstormgis infrastructure & application health..."

if docker inspect "$DB_CONTAINER" >/dev/null 2>&1 && [[ "$(docker inspect -f '{{.State.Running}}' "$DB_CONTAINER")" == "true" ]]; then
  docker exec "$DB_CONTAINER" pg_isready -U "$DB_USERNAME" -d "$DB_NAME" >/dev/null
  echo "[HEALTH] PASS: PostGIS container '$DB_CONTAINER' is ready."
fi

if docker inspect "$REDIS_CONTAINER" >/dev/null 2>&1 && [[ "$(docker inspect -f '{{.State.Running}}' "$REDIS_CONTAINER")" == "true" ]]; then
  PONG="$(docker exec "$REDIS_CONTAINER" redis-cli ping | tr -d '[:space:]')"
  [[ "$PONG" == "PONG" ]] || { echo "[HEALTH] FAIL: Redis did not return PONG" >&2; exit 1; }
  echo "[HEALTH] PASS: Redis container '$REDIS_CONTAINER' returned PONG."
fi

HEALTH_JSON="$(curl -fsS "${BACKEND_URL}/actuator/health")"
echo "$HEALTH_JSON" | grep -q '"status":"UP"' || {
  echo "[HEALTH] FAIL: Backend /actuator/health is not UP: $HEALTH_JSON" >&2
  exit 1
}
echo "[HEALTH] PASS: Backend '${BACKEND_URL}/actuator/health' status=UP."

for ep in "/api/v1/locations" "/api/v1/weather/current" "/api/v1/storms" "/api/v1/alerts/active"; do
  CODE="$(curl -s -o /dev/null -w '%{http_code}' "${BACKEND_URL}${ep}")"
  [[ "$CODE" == "200" ]] || {
    echo "[HEALTH] FAIL: Endpoint ${ep} returned HTTP ${CODE}" >&2
    exit 1
  }
done
echo "[HEALTH] PASS: Core REST/GeoJSON endpoints returned HTTP 200."

if [[ "$CHECK_NGINX" == "1" ]]; then
  NGINX_RESP="$(curl -fsS "${NGINX_URL}/healthz" | tr -d '[:space:]')"
  [[ "$NGINX_RESP" == "OK" ]] || {
    echo "[HEALTH] FAIL: Nginx ${NGINX_URL}/healthz returned '${NGINX_RESP}'" >&2
    exit 1
  }
  echo "[HEALTH] PASS: Nginx reverse proxy '${NGINX_URL}/healthz' returned OK."
fi

echo "[HEALTH] ALL CHECKS PASSED."
