#!/usr/bin/env bash
# ==============================================================================
# W6-07 End-to-End SIT, 500-CCU Load Testing & Security Verification Gate:
#   - G-01: Preflight, Backend Health & Live OWASP Security Headers Check
#   - G-02: Focused Week 6 SIT & Security Suite (SystemIntegrationAndEdgeCaseIT, SecurityHeadersAndOwaspIT, WeatherSyncServiceTest, StormSyncServiceTest)
#   - G-03: Full Backend + Frontend Quality Gate, JaCoCo Line Coverage >= 80%, & Bundle Size < 1.5MB
#   - G-04: 500-CCU Load & Stress Testing Gate (run-load-test-500ccu.mjs: P95 <= 100ms, Avg < 50ms, 5xx == 0)
#   - G-05: Security Audit & Multi-Stage Container Verification (security-audit-week6.sh)
#   - G-06: UI Contract Static Check + Browser E2E Regression (when PLAYWRIGHT_MODULE is active)
# W6_FORCE_FAILURE_GATE=G02 is reserved for controlled failure drills.
# ==============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://127.0.0.1:8080}"
FRONTEND_BASE_URL="${FRONTEND_BASE_URL:-http://127.0.0.1:5173}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-120}"
ARTIFACT_DIR="${ARTIFACT_DIR:-$ROOT/artifacts/w6-07}"
W6_SKIP_DOCKER_BUILD="${W6_SKIP_DOCKER_BUILD:-0}"
BACKEND_BASE_URL="${BACKEND_BASE_URL%/}"
FRONTEND_BASE_URL="${FRONTEND_BASE_URL%/}"
mkdir -p "$ARTIFACT_DIR"
ARTIFACT_DIR="$(cd "$ARTIFACT_DIR" && pwd)"
TMP_DIR="$(mktemp -d)"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
SHA="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || printf unknown)"
GATE=G-01
RESULT=FAIL
DETAIL=not-started
FOCUSED_TESTS=0
FULL_TESTS=0
FULL_SKIPPED=0
BACKEND_COVERAGE_PCT="0.00"
BUNDLE_SIZE_KB=0
LOAD_TEST_GATE=not-run
SECURITY_AUDIT_GATE=not-run
UI_STATIC_CHECK=not-run
UI_BROWSER_SMOKE=not-run

finish() {
  local code=$?
  trap - EXIT
  rm -rf "$TMP_DIR"
  if (( code == 0 )) && { [[ "$GATE" != G-06 ]] || (( FOCUSED_TESTS == 0 || FULL_TESTS == 0 )) || [[ "$LOAD_TEST_GATE" != PASS ]] || [[ "$SECURITY_AUDIT_GATE" != PASS ]] || [[ "$UI_STATIC_CHECK" != PASS ]]; }; then
    code=1
    DETAIL=incomplete-gate
  fi
  if (( code == 0 )); then RESULT=PASS; DETAIL=all-required-gates-passed; fi
  jq -n --arg started "$STARTED_AT" --arg finished "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg sha "$SHA" --arg result "$RESULT" --arg gate "$GATE" --arg detail "$DETAIL" \
    --arg backend_coverage_pct "$BACKEND_COVERAGE_PCT" \
    --arg load_test_gate "$LOAD_TEST_GATE" \
    --arg security_audit_gate "$SECURITY_AUDIT_GATE" \
    --arg ui_static_check "$UI_STATIC_CHECK" \
    --arg ui_browser_smoke "$UI_BROWSER_SMOKE" \
    --argjson exit_code "$code" \
    --argjson focused_tests "$FOCUSED_TESTS" \
    --argjson full_tests "$FULL_TESTS" \
    --argjson full_skipped "$FULL_SKIPPED" \
    --argjson bundle_size_kb "$BUNDLE_SIZE_KB" \
    '{started_utc:$started,finished_utc:$finished,sha:$sha,result:$result,gate:$gate,detail:$detail,exit_code:$exit_code,focused_tests:$focused_tests,full_tests:$full_tests,full_skipped:$full_skipped,backend_line_coverage_pct:$backend_coverage_pct,frontend_bundle_kb:$bundle_size_kb,load_test_500ccu:$load_test_gate,security_audit:$security_audit_gate,ui_static_check:$ui_static_check,ui_browser_smoke:$ui_browser_smoke}' \
    > "$ARTIFACT_DIR/summary.json" || true
  printf '%s %s %s %s exit=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$GATE" "$RESULT" "$DETAIL" "$code" | tee -a "$ARTIFACT_DIR/verify.log"
  exit "$code"
}
trap finish EXIT
trap 'DETAIL=terminated; exit 143' TERM INT

log() { printf '%s %s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$GATE" "$*" | tee -a "$ARTIFACT_DIR/verify.log"; }
fail() { DETAIL="$*"; log "FAIL $DETAIL"; exit 1; }
pass() { log PASS; }
need() { command -v "$1" >/dev/null 2>&1 || fail "missing-command:$1"; }

count_xml() {
  python3 - "$1" "$2" <<'PY'
import glob, sys, xml.etree.ElementTree as ET
files = glob.glob(sys.argv[1])
if not files:
    raise SystemExit('no JUnit XML found')
total = failures = errors = skipped = 0
for path in files:
    root = ET.parse(path).getroot()
    for suite in ([root] if root.tag == 'testsuite' else root.findall('testsuite')):
        total += int(suite.attrib.get('tests', 0))
        failures += int(suite.attrib.get('failures', 0))
        errors += int(suite.attrib.get('errors', 0))
        skipped += int(suite.attrib.get('skipped', 0))
print(f'{total} {failures} {errors} {skipped}')
if total == 0 or failures or errors or (sys.argv[2] == 'focused' and skipped):
    raise SystemExit(1)
PY
}

check_backend_coverage() {
  python3 - "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
missed = covered = 0
for c in root.findall('counter'):
    if c.attrib.get('type') == 'LINE':
        missed += int(c.attrib.get('missed', 0))
        covered += int(c.attrib.get('covered', 0))
total = missed + covered
if total == 0:
    raise SystemExit('no backend coverage counter')
pct = covered * 100.0 / total
print(f'{pct:.2f}')
if pct < 80.0:
    raise SystemExit(1)
PY
}

: > "$ARTIFACT_DIR/verify.log"
log "START sha=$SHA mode=week6-sit-load-security-verification"

# ------------------------------------------------------------------------------
# G-01: Preflight, Backend Health & Live OWASP Security Headers
# ------------------------------------------------------------------------------
if [[ -z "${JAVA_HOME:-}" ]] && [[ -x /usr/libexec/java_home ]]; then
  detected_java21="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  if [[ -n "$detected_java21" ]]; then
    export JAVA_HOME="$detected_java21"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi
[[ "$BACKEND_BASE_URL" =~ ^https?://[^/]+$ ]] || fail invalid-BACKEND_BASE_URL
for tool in bash curl jq java node npm docker python3 git; do need "$tool"; done
[[ -f "$ROOT/VNstormgis-Backend/gradlew" ]] || fail missing-gradle-wrapper
java -version 2>&1 | head -1 | grep -Eq 'version "21[.\"]' || fail java-21-required
docker info >/dev/null 2>&1 || fail docker-daemon-unavailable

deadline=$((SECONDS + WAIT_TIMEOUT_SECONDS))
while :; do
  code="$(curl --silent --show-error --connect-timeout 3 --max-time 10 -o "$TMP_DIR/health.json" -w '%{http_code}' "$BACKEND_BASE_URL/actuator/health" 2>/dev/null || printf 000)"
  if [[ "$code" == 200 ]] && jq -e '.status == "UP"' "$TMP_DIR/health.json" >/dev/null 2>&1; then break; fi
  (( SECONDS < deadline )) || fail backend-health-timeout
  sleep 2
done

curl -fsSI "$BACKEND_BASE_URL/api/v1/locations" > "$TMP_DIR/headers.txt" || fail locations-headers-fetch-failed
grep -qi '^X-Content-Type-Options: nosniff' "$TMP_DIR/headers.txt" || fail missing-header-nosniff
grep -qi '^X-Frame-Options: DENY' "$TMP_DIR/headers.txt" || fail missing-header-frame-options
grep -qi '^Referrer-Policy: strict-origin-when-cross-origin' "$TMP_DIR/headers.txt" || fail missing-header-referrer-policy
grep -qi '^Permissions-Policy:' "$TMP_DIR/headers.txt" || fail missing-header-permissions-policy
grep -qi '^Content-Security-Policy:' "$TMP_DIR/headers.txt" || fail missing-header-csp
pass

# ------------------------------------------------------------------------------
# G-02: Focused Week 6 SIT & Security Suite
# ------------------------------------------------------------------------------
GATE=G-02
log 'focused-IT:SystemIntegrationAndEdgeCaseIT,SecurityHeadersAndOwaspIT,WeatherSyncServiceTest,StormSyncServiceTest'
if [[ "${W6_FORCE_FAILURE_GATE:-}" == "G02" ]]; then
  fail controlled-focused-gate-failure
fi
cd "$ROOT/VNstormgis-Backend"
if ! ./gradlew test --no-daemon --rerun-tasks \
  --tests 'vn.weathergis.sit.SystemIntegrationAndEdgeCaseIT' \
  --tests 'vn.weathergis.security.SecurityHeadersAndOwaspIT' \
  --tests 'vn.weathergis.automation.service.WeatherSyncServiceTest' \
  --tests 'vn.weathergis.automation.service.StormSyncServiceTest' \
  > "$ARTIFACT_DIR/focused-gradle.log" 2>&1; then fail focused-gradle-failed; fi

FOCUSED_TESTS=0
for xml_name in \
  'TEST-vn.weathergis.sit.SystemIntegrationAndEdgeCaseIT.xml' \
  'TEST-vn.weathergis.security.SecurityHeadersAndOwaspIT.xml' \
  'TEST-vn.weathergis.automation.service.WeatherSyncServiceTest.xml' \
  'TEST-vn.weathergis.automation.service.StormSyncServiceTest.xml'; do
  xml="$ROOT/VNstormgis-Backend/build/test-results/test/$xml_name"
  [[ -f "$xml" ]] || fail "missing-focused-xml:$xml_name"
  read -r count _ _ _ < <(count_xml "$xml" focused) || fail "focused-xml-invalid:$xml_name"
  FOCUSED_TESTS=$((FOCUSED_TESTS + count))
done
mkdir -p "$ARTIFACT_DIR/focused-xml"
cp "$ROOT"/VNstormgis-Backend/build/test-results/test/TEST-*.xml "$ARTIFACT_DIR/focused-xml/"
pass

# ------------------------------------------------------------------------------
# G-03: Full Backend + Frontend Quality Gate & Bundle Size < 1.5MB
# ------------------------------------------------------------------------------
GATE=G-03
log 'full-backend:spotlessCheck,test,bootJar,jacocoTestReport'
if ! ./gradlew spotlessCheck test bootJar jacocoTestReport --no-daemon > "$ARTIFACT_DIR/full-gradle.log" 2>&1; then
  fail full-gradle-failed
fi
read -r FULL_TESTS _ _ FULL_SKIPPED < <(count_xml "$ROOT/VNstormgis-Backend/build/test-results/test/TEST-*.xml" full) || fail full-xml-invalid
JACOCO_XML="$ROOT/VNstormgis-Backend/build/reports/jacoco/test/jacocoTestReport.xml"
[[ -f "$JACOCO_XML" ]] || fail missing-jacoco-xml
BACKEND_COVERAGE_PCT="$(check_backend_coverage "$JACOCO_XML")" || fail "backend-coverage-below-80:$BACKEND_COVERAGE_PCT"
log "backend-line-coverage:${BACKEND_COVERAGE_PCT}%"

log 'frontend:type-check,lint,build,bundle-size'
cd "$ROOT/VNstormgis-Frontend"
if ! (npm run type-check && npm run lint && npm run build) > "$ARTIFACT_DIR/frontend.log" 2>&1; then
  fail frontend-gate-failed
fi
BUNDLE_SIZE_KB="$(du -sk "$ROOT/VNstormgis-Frontend/dist" | awk '{print $1}')"
(( BUNDLE_SIZE_KB > 0 && BUNDLE_SIZE_KB <= 1536 )) || fail "bundle-size-exceeds-1.5mb:${BUNDLE_SIZE_KB}KB"
JS_CHUNK_COUNT="$(ls "$ROOT"/VNstormgis-Frontend/dist/assets/*.js 2>/dev/null | wc -l | tr -d ' ')"
(( JS_CHUNK_COUNT >= 4 )) || fail "missing-code-splitting-chunks:$JS_CHUNK_COUNT"
log "frontend-bundle-size:${BUNDLE_SIZE_KB}KB chunks=${JS_CHUNK_COUNT}"
pass

# ------------------------------------------------------------------------------
# G-04: 500-CCU Load & Stress Test Gate
# ------------------------------------------------------------------------------
GATE=G-04
log "load-test-500ccu:$BACKEND_BASE_URL"
cd "$ROOT"
if ! node "$ROOT/.github/scripts/run-load-test-500ccu.mjs" \
  --base-url "$BACKEND_BASE_URL" \
  --ccu 500 \
  --rounds 8 \
  --think-ms 250 \
  --ramp-up-ms 1000 \
  --chat-users 40 \
  --chat-burst 12 \
  --out "$ARTIFACT_DIR/load-test-summary.json" \
  > "$ARTIFACT_DIR/load-test.log" 2>&1; then
  fail load-test-500ccu-failed
fi
jq -e '.overallResult == "PASS" and .totals.total5xx == 0 and .endpoints.weather_current.p95Ms <= 100 and .mapAggregate.avgMs < 50' \
  "$ARTIFACT_DIR/load-test-summary.json" >/dev/null || fail load-test-contract-failed
LOAD_TEST_GATE=PASS
pass

# ------------------------------------------------------------------------------
# G-05: Security Audit & Multi-Stage Container Verification
# ------------------------------------------------------------------------------
GATE=G-05
log 'security-audit-week6'
if ! SKIP_DOCKER_BUILD="$W6_SKIP_DOCKER_BUILD" bash "$ROOT/.github/scripts/security-audit-week6.sh" \
  --out "$ARTIFACT_DIR/security-audit-summary.json" \
  > "$ARTIFACT_DIR/security-audit.log" 2>&1; then
  fail security-audit-failed
fi
jq -e '.result == "PASS" and .secret_scan.leaked_secrets == 0 and .dependency_audit.frontend_prod_vulnerabilities.critical == 0 and .dependency_audit.frontend_prod_vulnerabilities.high == 0' \
  "$ARTIFACT_DIR/security-audit-summary.json" >/dev/null || fail security-audit-contract-failed
SECURITY_AUDIT_GATE=PASS
pass

# ------------------------------------------------------------------------------
# G-06: UI Contract Static Check + Browser E2E Regression
# ------------------------------------------------------------------------------
GATE=G-06
log 'ui-contract-and-e2e-regression'
CHAT_DRAWER="$ROOT/VNstormgis-Frontend/src/components/dashboard/AiChatDrawer.tsx"
ALERT_DETAIL="$ROOT/VNstormgis-Frontend/src/components/dashboard/AlertDetail.tsx"
LAYOUT="$ROOT/VNstormgis-Frontend/src/components/layout/UnifiedAppLayout.tsx"
for f in "$CHAT_DRAWER" "$ALERT_DETAIL" "$LAYOUT"; do
  [[ -f "$f" ]] || fail "missing-ui-file:$f"
done
for tid in 'ai-chat-drawer' 'ai-chat-history' 'ai-chat-message' 'ai-chat-fallback-badge' 'ai-chat-countdown' 'ai-chat-alert-ref' 'ai-chat-input' 'ai-chat-send'; do
  grep -Fq "data-testid=\"$tid\"" "$CHAT_DRAWER" || fail "missing-testid:$tid"
done
grep -Fq 'data-testid="alert-ask-ai"' "$ALERT_DETAIL" || fail missing-alert-ask-ai-testid
if grep -Fq 'dangerouslySetInnerHTML' "$CHAT_DRAWER" "$ALERT_DETAIL"; then
  fail unsafe-html-rendering-in-ui
fi
UI_STATIC_CHECK=PASS

if [[ -n "${PLAYWRIGHT_MODULE:-}" ]] && [[ -f "${PLAYWRIGHT_MODULE}" ]]; then
  fcode="$(curl --silent --show-error --connect-timeout 3 --max-time 10 -o "$TMP_DIR/frontend.html" -w '%{http_code}' "$FRONTEND_BASE_URL" 2>/dev/null || printf 000)"
  if [[ "$fcode" == 200 ]]; then
    log "running-playwright-browser-e2e:$FRONTEND_BASE_URL"
    if ! PLAYWRIGHT_MODULE="$PLAYWRIGHT_MODULE" APP_URL="$FRONTEND_BASE_URL" OUT_DIR="$ARTIFACT_DIR/ui-w5-06" \
      node "$ROOT/VNstormgis-Frontend/tests/test-w5-06-ui.mjs" > "$ARTIFACT_DIR/playwright-w5-06.log" 2>&1; then
      fail playwright-w5-06-failed
    fi
    if ! PLAYWRIGHT_MODULE="$PLAYWRIGHT_MODULE" APP_URL="$FRONTEND_BASE_URL" OUT_DIR="$ARTIFACT_DIR/ui-w4-06" \
      node "$ROOT/scripts/test-w4-06-ui.mjs" > "$ARTIFACT_DIR/playwright-w4-06.log" 2>&1; then
      fail playwright-w4-06-failed
    fi
    UI_BROWSER_SMOKE=PASS
  else
    UI_BROWSER_SMOKE="SKIPPED(frontend-not-running:$fcode)"
  fi
else
  UI_BROWSER_SMOKE="SKIPPED(no-playwright-module)"
fi
pass
