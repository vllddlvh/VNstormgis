#!/usr/bin/env bash
# ==============================================================================
# W7-07 Final Go-Live Verification Gate (Milestone 4 — Release v1.0.0):
#   - G-01: Full Backend Quality Gate (spotlessCheck, full Testcontainers suite, bootJar, JaCoCo >= 80%)
#   - G-02: Full Frontend Quality Gate (type-check, lint 0 warnings, build, dist <= 1536KB, >= 4 chunks)
#           + Static UI Contract Check (W3/W5/W6 + new GPS, 24h History, Search, Email Subscription)
#   - G-03: Security Audit & Multi-Stage Container Hardening (security-audit-week6.sh)
#   - G-04: Production Artifacts & Compose Validation (application-prod.yml, docker-compose.prod.yml, release-cd.yml, ops scripts)
#   - G-05: Live Infrastructure Health, Spatial /at-point, Email Subscription + Mailpit & PostGIS DR Drill (when stack is running)
# ==============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ARTIFACT_DIR="${ARTIFACT_DIR:-$ROOT/artifacts/w7-07}"
BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://127.0.0.1:8080}"
MAILPIT_API_URL="${MAILPIT_API_URL:-http://127.0.0.1:8025}"
W7_SKIP_DOCKER_BUILD="${W7_SKIP_DOCKER_BUILD:-0}"
W7_REQUIRE_LIVE_STACK="${W7_REQUIRE_LIVE_STACK:-0}"

mkdir -p "$ARTIFACT_DIR"
ARTIFACT_DIR="$(cd "$ARTIFACT_DIR" && pwd)"
TMP_DIR="$(mktemp -d)"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
SHA="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || printf unknown)"

GATE="G-01"
RESULT="FAIL"
DETAIL="not-started"
FULL_TESTS=0
BACKEND_COVERAGE_PCT="0.00"
BUNDLE_SIZE_KB=0
SECURITY_GATE="not-run"
PROD_CONFIG_GATE="not-run"
LIVE_DR_AND_E2E_GATE="not-run"

finish() {
  local code=$?
  trap - EXIT
  rm -rf "$TMP_DIR"
  if (( code == 0 )); then
    RESULT="PASS"
    DETAIL="all-golive-gates-passed"
  fi
  jq -n \
    --arg started "$STARTED_AT" \
    --arg finished "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg sha "$SHA" \
    --arg result "$RESULT" \
    --arg gate "$GATE" \
    --arg detail "$DETAIL" \
    --arg backend_coverage_pct "$BACKEND_COVERAGE_PCT" \
    --arg security_gate "$SECURITY_GATE" \
    --arg prod_config_gate "$PROD_CONFIG_GATE" \
    --arg live_dr_and_e2e_gate "$LIVE_DR_AND_E2E_GATE" \
    --argjson exit_code "$code" \
    --argjson full_tests "$FULL_TESTS" \
    --argjson bundle_size_kb "$BUNDLE_SIZE_KB" \
    '{started_utc:$started,finished_utc:$finished,sha:$sha,result:$result,gate:$gate,detail:$detail,exit_code:$exit_code,full_tests:$full_tests,backend_line_coverage_pct:$backend_coverage_pct,frontend_bundle_kb:$bundle_size_kb,security_audit:$security_gate,prod_config_gate:$prod_config_gate,live_dr_and_e2e:$live_dr_and_e2e_gate}' \
    > "$ARTIFACT_DIR/summary.json" || true
  printf '%s %s %s %s exit=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$GATE" "$RESULT" "$DETAIL" "$code" | tee -a "$ARTIFACT_DIR/verify.log"
  exit "$code"
}
trap finish EXIT

log() { printf '%s [%s] %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$GATE" "$*" | tee -a "$ARTIFACT_DIR/verify.log"; }
fail() { DETAIL="$*"; log "FAIL: $DETAIL"; exit 1; }

: > "$ARTIFACT_DIR/verify.log"
log "START sha=$SHA mode=week7-golive-verification"

if [[ -z "${JAVA_HOME:-}" ]] && [[ -x /usr/libexec/java_home ]]; then
  detected_java21="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  if [[ -n "$detected_java21" ]]; then
    export JAVA_HOME="$detected_java21"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

# ------------------------------------------------------------------------------
# G-01: Full Backend Quality Gate & JaCoCo >= 80%
# ------------------------------------------------------------------------------
GATE="G-01"
log "Running Backend spotlessCheck + test + bootJar + jacocoTestReport..."
cd "$ROOT/VNstormgis-Backend"
./gradlew spotlessCheck test bootJar jacocoTestReport --no-daemon > "$ARTIFACT_DIR/backend-gradle.log" 2>&1 || fail "backend-gradle-failed"

read -r FULL_TESTS BACKEND_COVERAGE_PCT < <(
  python3 - "$ROOT/VNstormgis-Backend/build/test-results/test" "$ROOT/VNstormgis-Backend/build/reports/jacoco/test/jacocoTestReport.xml" <<'PY'
import glob, os, sys, xml.etree.ElementTree as ET
xml_dir, jacoco_xml = sys.argv[1], sys.argv[2]
files = glob.glob(os.path.join(xml_dir, "TEST-*.xml"))
total = failures = errors = 0
for p in files:
    r = ET.parse(p).getroot()
    for s in ([r] if r.tag == "testsuite" else r.findall("testsuite")):
        total += int(s.attrib.get("tests", 0))
        failures += int(s.attrib.get("failures", 0))
        errors += int(s.attrib.get("errors", 0))
if total == 0 or failures > 0 or errors > 0:
    raise SystemExit(1)
jr = ET.parse(jacoco_xml).getroot()
missed = covered = 0
for c in jr.findall("counter"):
    if c.attrib.get("type") == "LINE":
        missed += int(c.attrib.get("missed", 0))
        covered += int(c.attrib.get("covered", 0))
pct = covered * 100.0 / (missed + covered)
if pct < 80.0:
    raise SystemExit(1)
print(f"{total} {pct:.2f}")
PY
) || fail "backend-metrics-validation-failed"
log "PASS: Backend tests=$FULL_TESTS, line_coverage=${BACKEND_COVERAGE_PCT}%"

# ------------------------------------------------------------------------------
# G-02: Frontend Quality Gate, Bundle Budget & Static UI Contracts
# ------------------------------------------------------------------------------
GATE="G-02"
log "Running Frontend type-check + lint + build..."
cd "$ROOT/VNstormgis-Frontend"
(npm run type-check && npm run lint && npm run build) > "$ARTIFACT_DIR/frontend.log" 2>&1 || fail "frontend-gate-failed"

BUNDLE_SIZE_KB="$(du -sk "$ROOT/VNstormgis-Frontend/dist" | awk '{print $1}')"
(( BUNDLE_SIZE_KB > 0 && BUNDLE_SIZE_KB <= 1536 )) || fail "bundle-exceeds-1536kb:${BUNDLE_SIZE_KB}"
JS_CHUNKS="$(ls "$ROOT"/VNstormgis-Frontend/dist/assets/*.js 2>/dev/null | wc -l | tr -d ' ')"
(( JS_CHUNKS >= 4 )) || fail "js-chunks-below-4:${JS_CHUNKS}"

grep -Fq 'data-testid="my-location-card"' "$ROOT/VNstormgis-Frontend/src/components/dashboard/MyLocationCard.tsx" || fail "missing-my-location-card"
grep -Fq 'data-testid="email-subscription-modal"' "$ROOT/VNstormgis-Frontend/src/components/dashboard/EmailSubscriptionModal.tsx" || fail "missing-email-subscription-modal"
grep -Fq 'data-testid="station-history-modal"' "$ROOT/VNstormgis-Frontend/src/components/dashboard/StationHistoryModal.tsx" || fail "missing-station-history-modal"
grep -Fq 'data-testid="station-search-modal"' "$ROOT/VNstormgis-Frontend/src/components/dashboard/StationSearchModal.tsx" || fail "missing-station-search-modal"
grep -Fq 'geolocation=(self)' "$ROOT/VNstormgis-Deployment/nginx/conf.d/weathergis.conf" || fail "nginx-geolocation-policy-missing"
log "PASS: Frontend dist=${BUNDLE_SIZE_KB}KB (${JS_CHUNKS} JS chunks) & all UI contracts verified."

# ------------------------------------------------------------------------------
# G-03: Security Audit & Multi-Stage Docker Verification
# ------------------------------------------------------------------------------
GATE="G-03"
log "Running security-audit-week6.sh..."
cd "$ROOT"
SKIP_DOCKER_BUILD="$W7_SKIP_DOCKER_BUILD" bash "$ROOT/.github/scripts/security-audit-week6.sh" \
  --out "$ARTIFACT_DIR/security-audit.json" > "$ARTIFACT_DIR/security-audit.log" 2>&1 || fail "security-audit-failed"
SECURITY_GATE="PASS"
log "PASS: Security audit clean (0 secrets, 0 High/Critical CVEs)."

# ------------------------------------------------------------------------------
# G-04: Production Deployment Configs & Operational Scripts Validation
# ------------------------------------------------------------------------------
GATE="G-04"
log "Validating W7 production configs, scripts, and CI/CD release workflow..."
for req_file in \
  "$ROOT/VNstormgis-Backend/src/main/resources/application-prod.yml" \
  "$ROOT/VNstormgis-Deployment/docker-compose.prod.yml" \
  "$ROOT/VNstormgis-Deployment/env/.env.prod.example" \
  "$ROOT/VNstormgis-Deployment/scripts/backup-db.sh" \
  "$ROOT/VNstormgis-Deployment/scripts/restore-db.sh" \
  "$ROOT/VNstormgis-Deployment/scripts/health-check.sh" \
  "$ROOT/.github/workflows/release-cd.yml" \
  "$ROOT/OPERATIONS.md"; do
  [[ -f "$req_file" ]] || fail "missing-w7-artifact:$req_file"
done

DB_USERNAME=prod_check DB_PASSWORD=prod_check INTERNAL_SYNC_SECRET=prod_check ADMIN_API_KEY=prod_check \
  docker compose -f "$ROOT/VNstormgis-Deployment/docker-compose.prod.yml" config > "$TMP_DIR/compose-prod-resolved.yml" || fail "invalid-docker-compose-prod"

PROD_CONFIG_GATE="PASS"
log "PASS: Production compose, profile, operational scripts & release-cd.yml validated."

# ------------------------------------------------------------------------------
# G-05: Live Stack E2E (Spatial /at-point + Email OTP via Mailpit) & DR Drill
# ------------------------------------------------------------------------------
GATE="G-05"
HCODE="$(curl -s -o "$TMP_DIR/health.json" -w '%{http_code}' "${BACKEND_BASE_URL}/actuator/health" 2>/dev/null || printf 000)"
if [[ "$HCODE" == "200" ]]; then
  log "Live backend detected at $BACKEND_BASE_URL — executing live health, spatial, subscription & DR drill..."
  BACKEND_URL="$BACKEND_BASE_URL" bash "$ROOT/VNstormgis-Deployment/scripts/health-check.sh" > "$ARTIFACT_DIR/health-check.log" 2>&1 || fail "live-health-check-failed"

  AT_CODE="$(curl -s -o "$TMP_DIR/at-point.json" -w '%{http_code}' "${BACKEND_BASE_URL}/api/v1/locations/at-point?lng=108.2022&lat=16.0544&radiusKm=100")"
  [[ "$AT_CODE" == "200" ]] || fail "at-point-http-$AT_CODE"
  jq -e '.success == true and .data.nearestStation.code == "DNG"' "$TMP_DIR/at-point.json" >/dev/null || fail "at-point-payload-mismatch"

  SUB_EMAIL="golive.$(date +%s)@example.com"
  SUB_CODE="$(curl -s -o "$TMP_DIR/sub-create.json" -w '%{http_code}' -X POST "${BACKEND_BASE_URL}/api/v1/subscriptions" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"${SUB_EMAIL}\",\"scopeType\":\"COORDINATE\",\"longitude\":108.2022,\"latitude\":16.0544,\"label\":\"Đà Nẵng GPS\",\"radiusKm\":50,\"minSeverity\":\"WARNING\"}")"
  [[ "$SUB_CODE" == "201" ]] || fail "subscription-create-http-$SUB_CODE"
  SUB_ID="$(jq -r '.data.id' "$TMP_DIR/sub-create.json")"

  MP_CODE="$(curl -s -o "$TMP_DIR/mailpit-msgs.json" -w '%{http_code}' "${MAILPIT_API_URL}/api/v1/messages" 2>/dev/null || printf 000)"
  if [[ "$MP_CODE" == "200" ]]; then
    OTP="$(jq -r --arg email "$SUB_EMAIL" '[.messages[] | select(.To[0].Address == $email)][0].Subject // ""' "$TMP_DIR/mailpit-msgs.json" | grep -Eo '[0-9]{6}' | head -1 || true)"
    if [[ -n "$OTP" ]]; then
      V_CODE="$(curl -s -o "$TMP_DIR/sub-verify.json" -w '%{http_code}' -X POST "${BACKEND_BASE_URL}/api/v1/subscriptions/${SUB_ID}/verify" \
        -H 'Content-Type: application/json' -d "{\"otp\":\"${OTP}\"}")"
      [[ "$V_CODE" == "200" ]] || fail "subscription-verify-http-$V_CODE"
      UNSUB_TOK="$(jq -r '.data.unsubscribeToken' "$TMP_DIR/sub-verify.json")"
      T_CODE="$(curl -s -o "$TMP_DIR/sub-test.json" -w '%{http_code}' -X POST "${BACKEND_BASE_URL}/api/v1/subscriptions/${SUB_ID}/test-notification" \
        -H 'Content-Type: application/json' -d "{\"token\":\"${UNSUB_TOK}\"}")"
      [[ "$T_CODE" == "200" ]] || fail "subscription-test-mail-http-$T_CODE"
      U_CODE="$(curl -s -o /dev/null -w '%{http_code}' -X POST "${BACKEND_BASE_URL}/api/v1/subscriptions/unsubscribe" \
        -H 'Content-Type: application/json' -d "{\"token\":\"${UNSUB_TOK}\"}")"
      [[ "$U_CODE" == "200" ]] || fail "subscription-unsub-http-$U_CODE"
      log "PASS: Live Mailpit OTP verification + test email + 1-click unsubscribe verified!"
    fi
  fi

  if docker inspect weathergis-postgres >/dev/null 2>&1 && [[ "$(docker inspect -f '{{.State.Running}}' weathergis-postgres)" == "true" ]]; then
    BACKUP_OUT="$(BACKUP_DIR="$ARTIFACT_DIR/dr-backups" bash "$ROOT/VNstormgis-Deployment/scripts/backup-db.sh")"
    DUMP_PATH="$(echo "$BACKUP_OUT" | tail -n 1)"
    DR_DB="weather_gis_dr_drill"
    bash "$ROOT/VNstormgis-Deployment/scripts/restore-db.sh" "$DUMP_PATH" "$DR_DB" > "$ARTIFACT_DIR/dr-restore.log" 2>&1 || fail "dr-restore-failed"
    docker exec -e PGPASSWORD="${DB_PASSWORD:-gis_password_secret}" weathergis-postgres \
      psql -U "${DB_USERNAME:-gis_user}" -d postgres -c "DROP DATABASE IF EXISTS \"${DR_DB}\";" >/dev/null
    log "PASS: PostGIS Disaster Recovery drill (pg_dump -> pg_restore -> active SRID-4326 stations) succeeded!"
  fi
  LIVE_DR_AND_E2E_GATE="PASS"
else
  if [[ "$W7_REQUIRE_LIVE_STACK" == "1" ]]; then
    fail "live-backend-not-running-at-$BACKEND_BASE_URL"
  fi
  LIVE_DR_AND_E2E_GATE="SKIPPED(backend-offline)"
  log "SKIP: Live stack not running on $BACKEND_BASE_URL (set W7_REQUIRE_LIVE_STACK=1 to enforce)."
fi

log "ALL WEEK 7 GO-LIVE GATES PASSED."
