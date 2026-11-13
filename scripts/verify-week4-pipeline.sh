#!/usr/bin/env bash
# W4-08 read-only smoke + isolated Testcontainers gate.
# BACKEND_BASE_URL=http://localhost:8080; WAIT_TIMEOUT_SECONDS=120; ARTIFACT_DIR=artifacts/w4-08.
# W4_FORCE_FAILURE_GATE=G02 is reserved for a controlled workflow notification drill.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://localhost:8080}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-120}"
ARTIFACT_DIR="${ARTIFACT_DIR:-$ROOT/artifacts/w4-08}"
BACKEND_BASE_URL="${BACKEND_BASE_URL%/}"
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
DETAIL_SMOKE=not-run

finish() {
  local code=$?
  trap - EXIT
  rm -rf "$TMP_DIR"
  if (( code == 0 )) && { [[ "$GATE" != G-05 ]] || (( FOCUSED_TESTS == 0 || FULL_TESTS == 0 )) || [[ "$DETAIL_SMOKE" == not-run ]]; }; then
    code=1
    DETAIL=incomplete-gate
  fi
  if (( code == 0 )); then RESULT=PASS; DETAIL=all-required-gates-passed; fi
  jq -n --arg started "$STARTED_AT" --arg finished "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg sha "$SHA" --arg result "$RESULT" --arg gate "$GATE" --arg detail "$DETAIL" \
    --arg detail_smoke "$DETAIL_SMOKE" --argjson exit_code "$code" \
    --argjson focused_tests "$FOCUSED_TESTS" --argjson full_tests "$FULL_TESTS" \
    --argjson full_skipped "$FULL_SKIPPED" \
    '{started_utc:$started,finished_utc:$finished,sha:$sha,result:$result,gate:$gate,detail:$detail,detail_smoke:$detail_smoke,exit_code:$exit_code,focused_tests:$focused_tests,full_tests:$full_tests,full_skipped:$full_skipped}' \
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
expect() { local actual=$1 wanted=$2; [[ "$actual" == "$wanted" ]] || fail "http:$actual:expected:$wanted"; }
json() { jq -e "$1" "$2" >/dev/null || fail "invalid-contract:$3"; }
get() {
  local url=$1 output=$2
  curl --silent --show-error --connect-timeout 3 --max-time 10 -o "$output" -w '%{http_code}' "$url" 2>/dev/null || printf 000
}
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

: > "$ARTIFACT_DIR/verify.log"
log "START sha=$SHA mode=integration-plus-read-only-smoke"
[[ "$BACKEND_BASE_URL" =~ ^https?://[^/]+$ ]] || fail invalid-BACKEND_BASE_URL
[[ "$WAIT_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] && (( WAIT_TIMEOUT_SECONDS > 0 && WAIT_TIMEOUT_SECONDS <= 600 )) || fail invalid-WAIT_TIMEOUT_SECONDS
for tool in bash curl jq java docker python3 git; do need "$tool"; done
[[ -f "$ROOT/VNstormgis-Backend/gradlew" ]] || fail missing-gradle-wrapper
java -version 2>&1 | head -1 | grep -Eq 'version "21[.\"]' || fail java-21-required
docker info >/dev/null 2>&1 || fail docker-daemon-unavailable
deadline=$((SECONDS + WAIT_TIMEOUT_SECONDS))
while :; do
  code="$(get "$BACKEND_BASE_URL/actuator/health" "$TMP_DIR/health.json")"
  if [[ "$code" == 200 ]] && jq -e '.status == "UP"' "$TMP_DIR/health.json" >/dev/null 2>&1; then break; fi
  (( SECONDS < deadline )) || fail backend-health-timeout
  sleep 2
done
pass

GATE=G-02
log 'focused-IT:AlertPipelineIT,AlertGeminiFaultIT'
cd "$ROOT/VNstormgis-Backend"
if ! ./gradlew test --no-daemon --rerun-tasks --tests 'vn.weathergis.alert.AlertPipelineIT' --tests 'vn.weathergis.alert.AlertGeminiFaultIT' > "$ARTIFACT_DIR/focused-gradle.log" 2>&1; then fail focused-gradle-failed; fi
if ! counts="$(count_xml 'build/test-results/test/TEST-vn.weathergis.alert.AlertPipelineIT.xml' focused)"; then fail focused-xml-invalid; fi
read -r pipeline_count _ <<< "$counts"
if ! counts="$(count_xml 'build/test-results/test/TEST-vn.weathergis.alert.AlertGeminiFaultIT.xml' focused)"; then fail focused-xml-invalid; fi
read -r fault_count _ <<< "$counts"
FOCUSED_TESTS=$((pipeline_count + fault_count))
cp build/test-results/test/TEST-vn.weathergis.alert.AlertPipelineIT.xml "$ARTIFACT_DIR/"
cp build/test-results/test/TEST-vn.weathergis.alert.AlertGeminiFaultIT.xml "$ARTIFACT_DIR/"
[[ "${W4_FORCE_FAILURE_GATE:-}" != G02 ]] || fail controlled-focused-gate-failure
log "PASS tests=$FOCUSED_TESTS failures=0 errors=0 skips=0"

GATE=G-03
log full-backend-gate
if ! ./gradlew spotlessCheck test bootJar jacocoTestReport --no-daemon > "$ARTIFACT_DIR/full-gradle.log" 2>&1; then fail full-gradle-failed; fi
if ! counts="$(count_xml 'build/test-results/test/TEST-*.xml' full)"; then fail full-xml-invalid; fi
read -r FULL_TESTS _ _ FULL_SKIPPED <<< "$counts"
log "PASS tests=$FULL_TESTS failures=0 errors=0 skips=$FULL_SKIPPED"

GATE=G-04
log read-only-api-smoke
code="$(get "$BACKEND_BASE_URL/api/v1/alert-thresholds" "$TMP_DIR/thresholds.json")"
expect "$code" 200
json '.data | type == "array"' "$TMP_DIR/thresholds.json" thresholds
code="$(get "$BACKEND_BASE_URL/api/v1/alerts/active" "$TMP_DIR/active.json")"
expect "$code" 200
json '.type == "FeatureCollection" and (.features | type == "array") and all(.features[]; .type == "Feature" and (.id | type == "string" and test("^[0-9a-fA-F-]{36}$")) and .id == .properties.alert_id and (.properties.severity_level | IN("INFO","WARNING","DANGER","CRITICAL")) and (.properties.is_fallback | type == "boolean") and (.geometry == null or (.geometry.type == "Point" and (.geometry.coordinates | length == 2) and (.geometry.coordinates[0] | type == "number" and . >= -180 and . <= 180) and (.geometry.coordinates[1] | type == "number" and . >= -90 and . <= 90))))' "$TMP_DIR/active.json" active
alert_id="$(jq -r '.features[0].id // empty' "$TMP_DIR/active.json")"
if [[ -n "$alert_id" ]]; then
  code="$(get "$BACKEND_BASE_URL/api/v1/alerts/$alert_id" "$TMP_DIR/detail.json")"
  expect "$code" 200
  jq -e --slurpfile active "$TMP_DIR/active.json" --arg id "$alert_id" \
    '.data.id == $id and (.data.title | type == "string" and length > 0) and (.data.ai_explanation | type == "string" and length > 0) and (.data.is_fallback | type == "boolean") and .data.status == "active" and (.data.factual_snapshot == null or (.data.factual_snapshot | type == "object")) and .data.title == $active[0].features[0].properties.title and .data.severity_level == $active[0].features[0].properties.severity_level and .data.is_fallback == $active[0].features[0].properties.is_fallback' "$TMP_DIR/detail.json" >/dev/null || fail invalid-contract:detail
  DETAIL_SMOKE=PASS
else
  DETAIL_SMOKE=SKIP-empty-active-collection
  log "$DETAIL_SMOKE"
fi
for spec in 'bad-id|400|ALR_400_INVALID_ID' '00000000-0000-4000-8000-000000000000|404|ALR_404_NOT_FOUND'; do
  IFS='|' read -r id wanted error_code <<< "$spec"
  code="$(get "$BACKEND_BASE_URL/api/v1/alerts/$id" "$TMP_DIR/problem.json")"
  expect "$code" "$wanted"
  jq -e --argjson status "$wanted" --arg error "$error_code" \
    '.status == $status and .errorCode == $error and (.title | type == "string" and length > 0) and (.detail | type == "string" and length > 0) and (.instance | type == "string" and length > 0) and (.timestamp | type == "string" and length > 0)' "$TMP_DIR/problem.json" >/dev/null || fail "invalid-contract:problem-$wanted"
done
pass

GATE=G-05
DETAIL=grounding-and-fallback-mapped-to-W4-07-IT
log 'PASS evidence=focused-IT-XML; mapping=runbook/W4-07-report'
