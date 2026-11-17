#!/usr/bin/env bash
# W5-08 end-to-end verification gate:
# - G-01: Preflight & backend health
# - G-02: Focused chat integration/security IT (ChatIntegrationAndSecurityIT, ChatRateLimitIT, ChatGroundedReplyIT)
# - G-03: Full backend (spotlessCheck test bootJar jacocoTestReport + chat line coverage >= 80%) & frontend static gate
# - G-04: Isolated HTTP smoke (Map/Alert read + Chat session/messages/history/429 rate-limit/RFC 7807)
# - G-05: UI contract static check + browser E2E (map -> alert -> chat) when PLAYWRIGHT_MODULE & FRONTEND_BASE_URL are active
# W5_FORCE_FAILURE_GATE=G02 is reserved for controlled failure/notification drills.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://localhost:8080}"
FRONTEND_BASE_URL="${FRONTEND_BASE_URL:-http://localhost:5173}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-120}"
ARTIFACT_DIR="${ARTIFACT_DIR:-$ROOT/artifacts/w5-08}"
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
CHAT_COVERAGE_PCT="0.00"
HTTP_CHAT_SMOKE=not-run
UI_STATIC_CHECK=not-run
UI_BROWSER_SMOKE=not-run

finish() {
  local code=$?
  trap - EXIT
  rm -rf "$TMP_DIR"
  if (( code == 0 )) && { [[ "$GATE" != G-05 ]] || (( FOCUSED_TESTS == 0 || FULL_TESTS == 0 )) || [[ "$HTTP_CHAT_SMOKE" != PASS ]] || [[ "$UI_STATIC_CHECK" != PASS ]]; }; then
    code=1
    DETAIL=incomplete-gate
  fi
  if (( code == 0 )); then RESULT=PASS; DETAIL=all-required-gates-passed; fi
  jq -n --arg started "$STARTED_AT" --arg finished "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg sha "$SHA" --arg result "$RESULT" --arg gate "$GATE" --arg detail "$DETAIL" \
    --arg chat_coverage_pct "$CHAT_COVERAGE_PCT" \
    --arg http_chat_smoke "$HTTP_CHAT_SMOKE" \
    --arg ui_static_check "$UI_STATIC_CHECK" \
    --arg ui_browser_smoke "$UI_BROWSER_SMOKE" \
    --argjson exit_code "$code" \
    --argjson focused_tests "$FOCUSED_TESTS" \
    --argjson full_tests "$FULL_TESTS" \
    --argjson full_skipped "$FULL_SKIPPED" \
    '{started_utc:$started,finished_utc:$finished,sha:$sha,result:$result,gate:$gate,detail:$detail,exit_code:$exit_code,focused_tests:$focused_tests,full_tests:$full_tests,full_skipped:$full_skipped,chat_line_coverage_pct:$chat_coverage_pct,http_chat_smoke:$http_chat_smoke,ui_static_check:$ui_static_check,ui_browser_smoke:$ui_browser_smoke}' \
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
check_chat_coverage() {
  python3 - "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
missed = covered = 0
for pkg in root.findall('package'):
    if pkg.attrib.get('name', '').startswith('vn/weathergis/chat'):
        for c in pkg.findall('counter'):
            if c.attrib.get('type') == 'LINE':
                missed += int(c.attrib.get('missed', 0))
                covered += int(c.attrib.get('covered', 0))
total = missed + covered
if total == 0:
    raise SystemExit('no chat coverage counter')
pct = covered * 100.0 / total
print(f'{pct:.2f}')
if pct < 80.0:
    raise SystemExit(1)
PY
}

: > "$ARTIFACT_DIR/verify.log"
log "START sha=$SHA mode=week5-e2e-verification"
[[ "$BACKEND_BASE_URL" =~ ^https?://[^/]+$ ]] || fail invalid-BACKEND_BASE_URL
[[ "$WAIT_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] && (( WAIT_TIMEOUT_SECONDS > 0 && WAIT_TIMEOUT_SECONDS <= 600 )) || fail invalid-WAIT_TIMEOUT_SECONDS
for tool in bash curl jq java node npm docker python3 git; do need "$tool"; done
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
log 'focused-IT:ChatIntegrationAndSecurityIT,ChatRateLimitIT,ChatGroundedReplyIT'
cd "$ROOT/VNstormgis-Backend"
if ! ./gradlew test --no-daemon --rerun-tasks \
  --tests 'vn.weathergis.chat.ChatIntegrationAndSecurityIT' \
  --tests 'vn.weathergis.chat.ratelimit.ChatRateLimitIT' \
  --tests 'vn.weathergis.chat.ai.ChatGroundedReplyIT' \
  > "$ARTIFACT_DIR/focused-gradle.log" 2>&1; then fail focused-gradle-failed; fi
FOCUSED_TESTS=0
for xml_name in \
  'TEST-vn.weathergis.chat.ChatIntegrationAndSecurityIT.xml' \
  'TEST-vn.weathergis.chat.ratelimit.ChatRateLimitIT.xml' \
  'TEST-vn.weathergis.chat.ai.ChatGroundedReplyIT.xml'; do
  if ! counts="$(count_xml "build/test-results/test/$xml_name" focused)"; then fail "focused-xml-invalid:$xml_name"; fi
  read -r c_total _ <<< "$counts"
  FOCUSED_TESTS=$((FOCUSED_TESTS + c_total))
  cp "build/test-results/test/$xml_name" "$ARTIFACT_DIR/"
done
[[ "${W5_FORCE_FAILURE_GATE:-}" != G02 ]] || fail controlled-focused-gate-failure
log "PASS tests=$FOCUSED_TESTS failures=0 errors=0 skips=0"

GATE=G-03
log 'full-backend-and-frontend-quality-gate'
if ! ./gradlew spotlessCheck test bootJar jacocoTestReport --no-daemon > "$ARTIFACT_DIR/full-gradle.log" 2>&1; then fail full-gradle-failed; fi
if ! counts="$(count_xml 'build/test-results/test/TEST-*.xml' full)"; then fail full-xml-invalid; fi
read -r FULL_TESTS _ _ FULL_SKIPPED <<< "$counts"
if ! CHAT_COVERAGE_PCT="$(check_chat_coverage 'build/reports/jacoco/test/jacocoTestReport.xml')"; then fail chat-coverage-below-80pct; fi
cd "$ROOT/VNstormgis-Frontend"
if ! (npm run type-check && npm run lint && npm run build) > "$ARTIFACT_DIR/frontend-gate.log" 2>&1; then fail frontend-static-gate-failed; fi
log "PASS backend_tests=$FULL_TESTS skips=$FULL_SKIPPED chat_line_coverage=${CHAT_COVERAGE_PCT}% frontend=PASS"

GATE=G-04
log 'isolated-http-smoke:map-alert-chat-ratelimit'
for ep in \
  '/api/v1/locations?format=geojson' \
  '/api/v1/weather/current' \
  '/api/v1/storms?status=active' \
  '/api/v1/alerts/active'; do
  code="$(get "$BACKEND_BASE_URL$ep" "$TMP_DIR/geojson.json")"
  expect "$code" 200
  json '.type == "FeatureCollection" and (.features | type == "array")' "$TMP_DIR/geojson.json" "$ep"
done

random_uuid='00000000-0000-4000-8000-000000000099'
code="$(curl --silent --show-error -o "$TMP_DIR/no-token.json" -w '%{http_code}' \
  -X POST "$BACKEND_BASE_URL/api/v1/chat/sessions/$random_uuid/messages" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Khu vực nào mưa to nhất?"}' || printf 000)"
expect "$code" 401
json '.status == 401 and .errorCode == "CHT_401_SESSION_TOKEN_MISSING"' "$TMP_DIR/no-token.json" missing-token

code="$(curl --silent --show-error -o "$TMP_DIR/err-session.json" -w '%{http_code}' \
  -X POST "$BACKEND_BASE_URL/api/v1/chat/sessions" || printf 000)"
expect "$code" 201
json '.success == true and (.data.session_id | type == "string" and length == 36) and (.data.session_token | type == "string" and length == 43)' "$TMP_DIR/err-session.json" create-err-session
err_session_id="$(jq -r '.data.session_id' "$TMP_DIR/err-session.json")"
err_session_token="$(jq -r '.data.session_token' "$TMP_DIR/err-session.json")"

code="$(curl --silent --show-error -o "$TMP_DIR/cross.json" -w '%{http_code}' \
  -X POST "$BACKEND_BASE_URL/api/v1/chat/sessions/$random_uuid/messages" \
  -H "X-Session-Token: $err_session_token" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Khu vực nào mưa to nhất?"}' || printf 000)"
expect "$code" 404
json '.status == 404 and .errorCode == "CHT_404_SESSION_NOT_FOUND"' "$TMP_DIR/cross.json" cross-session

code="$(curl --silent --show-error -o "$TMP_DIR/val400.json" -w '%{http_code}' \
  -X POST "$BACKEND_BASE_URL/api/v1/chat/sessions/$err_session_id/messages" \
  -H "X-Session-Token: $err_session_token" \
  -H 'Content-Type: application/json' \
  -d '{"message":"   "}' || printf 000)"
expect "$code" 400
json '.status == 400 and .errorCode == "CHT_400_VALIDATION" and (.invalidParams | type == "array" and length > 0)' "$TMP_DIR/val400.json" blank-message

code="$(curl --silent --show-error -o "$TMP_DIR/bad-alert.json" -w '%{http_code}' \
  -X POST "$BACKEND_BASE_URL/api/v1/chat/sessions/$err_session_id/messages" \
  -H "X-Session-Token: $err_session_token" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Giải thích cảnh báo?","referenced_alert_id":"bad-uuid"}' || printf 000)"
expect "$code" 400
json '.status == 400 and .errorCode == "CHT_400_INVALID_ALERT_ID"' "$TMP_DIR/bad-alert.json" bad-alert-id

code="$(curl --silent --show-error -o "$TMP_DIR/missing-alert.json" -w '%{http_code}' \
  -X POST "$BACKEND_BASE_URL/api/v1/chat/sessions/$err_session_id/messages" \
  -H "X-Session-Token: $err_session_token" \
  -H 'Content-Type: application/json' \
  -d "{\"message\":\"Giải thích cảnh báo?\",\"referenced_alert_id\":\"$random_uuid\"}" || printf 000)"
expect "$code" 404
json '.status == 404 and .errorCode == "CHT_404_ALERT_NOT_FOUND"' "$TMP_DIR/missing-alert.json" missing-alert-id

code="$(curl --silent --show-error -o "$TMP_DIR/bad-bbox.json" -w '%{http_code}' \
  -X POST "$BACKEND_BASE_URL/api/v1/chat/sessions/$err_session_id/messages" \
  -H "X-Session-Token: $err_session_token" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Mưa ở đâu?","spatial_filter":{"bbox":[109,17,107,15]}}' || printf 000)"
expect "$code" 400
json '.status == 400 and .errorCode == "CHT_400_INVALID_SPATIAL_FILTER"' "$TMP_DIR/bad-bbox.json" bad-bbox

code="$(curl --silent --show-error -o "$TMP_DIR/session.json" -w '%{http_code}' \
  -X POST "$BACKEND_BASE_URL/api/v1/chat/sessions" || printf 000)"
expect "$code" 201
json '.success == true and (.data.session_id | type == "string" and length == 36) and (.data.session_token | type == "string" and length == 43)' "$TMP_DIR/session.json" create-session
session_id="$(jq -r '.data.session_id' "$TMP_DIR/session.json")"
session_token="$(jq -r '.data.session_token' "$TMP_DIR/session.json")"

for i in {1..10}; do
  code="$(curl --silent --show-error -o "$TMP_DIR/msg-$i.json" -w '%{http_code}' \
    -X POST "$BACKEND_BASE_URL/api/v1/chat/sessions/$session_id/messages" \
    -H "X-Session-Token: $session_token" \
    -H 'Content-Type: application/json' \
    -d "{\"message\":\"Khu vực nào đang có mưa to nhất lần $i?\"}" || printf 000)"
  expect "$code" 200
  json '.success == true and (.data.message_id | type == "string" and length == 36) and (.data.reply | type == "string" and length > 0) and (.data.is_fallback | type == "boolean") and (.data.referenced_entities | type == "object")' "$TMP_DIR/msg-$i.json" "msg-$i"
done

code="$(curl --silent --show-error -D "$TMP_DIR/rl-headers.txt" -o "$TMP_DIR/rl-body.json" -w '%{http_code}' \
  -X POST "$BACKEND_BASE_URL/api/v1/chat/sessions/$session_id/messages" \
  -H "X-Session-Token: $session_token" \
  -H 'Content-Type: application/json' \
  -d '{"message":"Câu hỏi thứ 11 vượt hạn mức?"}' || printf 000)"
expect "$code" 429
retry_header="$(awk 'tolower($1)=="retry-after:" {gsub(/\r/,"",$2); print $2}' "$TMP_DIR/rl-headers.txt" | tail -n 1)"
[[ "$retry_header" =~ ^[0-9]+$ ]] && (( retry_header >= 1 && retry_header <= 60 )) || fail "invalid-retry-after-header:$retry_header"
jq -e --argjson retry "$retry_header" \
  '.status == 429 and .errorCode == "CHT_429_RATE_LIMITED" and .retry_after_seconds == $retry' \
  "$TMP_DIR/rl-body.json" >/dev/null || fail invalid-contract:rate-limit-429

code="$(curl --silent --show-error -o "$TMP_DIR/hist-1.json" -w '%{http_code}' \
  "$BACKEND_BASE_URL/api/v1/chat/sessions/$session_id/messages?limit=10" \
  -H "X-Session-Token: $session_token" || printf 000)"
expect "$code" 200
json '(.data | type == "array" and length == 10) and (tostring | (contains("session_token") or contains("token_hash") or contains("tokens_used")) | not)' "$TMP_DIR/hist-1.json" history-page-1
cursor_id="$(jq -r '.data[9].id' "$TMP_DIR/hist-1.json")"

code="$(curl --silent --show-error -o "$TMP_DIR/hist-2.json" -w '%{http_code}' \
  "$BACKEND_BASE_URL/api/v1/chat/sessions/$session_id/messages?limit=15&after=$cursor_id" \
  -H "X-Session-Token: $session_token" || printf 000)"
expect "$code" 200
json '.data | type == "array" and length == 10' "$TMP_DIR/hist-2.json" history-page-2
HTTP_CHAT_SMOKE=PASS
cp "$TMP_DIR/rl-body.json" "$ARTIFACT_DIR/chat-429-sample.json"
cp "$TMP_DIR/msg-1.json" "$ARTIFACT_DIR/chat-200-sample.json"
pass

GATE=G-05
log 'ui-contract-and-map-alert-chat-e2e'
cd "$ROOT"
grep -q 'vnstormgis.chat.session' VNstormgis-Frontend/src/hooks/useChatSession.ts || fail missing-storage-key
grep -q 'X-Session-Token' VNstormgis-Frontend/src/services/chatService.ts || fail missing-session-token-header
grep -q 'referenced_alert_id' VNstormgis-Frontend/src/services/chatService.ts || fail missing-referenced-alert-id
grep -q 'data-testid="alert-ask-ai"' VNstormgis-Frontend/src/components/dashboard/AlertDetail.tsx || fail missing-alert-ask-ai-testid
for tid in 'ai-chat-drawer' 'ai-chat-history' 'ai-chat-message' 'ai-chat-fallback-badge' 'ai-chat-countdown' 'ai-chat-alert-ref' 'ai-chat-input' 'ai-chat-send'; do
  grep -q "data-testid=\"$tid\"" VNstormgis-Frontend/src/components/dashboard/AiChatDrawer.tsx || fail "missing-testid:$tid"
done
if grep -Eq '85\.4|165 km/h|215 mm|KỸ SƯ VẬN HÀNH|dangerouslySetInnerHTML' VNstormgis-Frontend/src/components/dashboard/AiChatDrawer.tsx; then
  fail mock-or-unsafe-html-in-chat-drawer
fi
UI_STATIC_CHECK=PASS

if [[ -n "${PLAYWRIGHT_MODULE:-}" && -f "${PLAYWRIGHT_MODULE}" ]]; then
  fe_code="$(curl --silent -o /dev/null -w '%{http_code}' "$FRONTEND_BASE_URL" 2>/dev/null || printf 000)"
  if [[ "$fe_code" == 200 ]]; then
    if ! PLAYWRIGHT_MODULE="$PLAYWRIGHT_MODULE" FRONTEND_BASE_URL="$FRONTEND_BASE_URL" \
      node "$ROOT/VNstormgis-Frontend/tests/test-w5-06-ui.mjs" > "$ARTIFACT_DIR/ui-w5-06-playwright.log" 2>&1; then
      fail ui-w5-06-playwright-failed
    fi
    if ! PLAYWRIGHT_MODULE="$PLAYWRIGHT_MODULE" FRONTEND_BASE_URL="$FRONTEND_BASE_URL" \
      node "$ROOT/scripts/test-w4-06-ui.mjs" > "$ARTIFACT_DIR/ui-w4-06-playwright.log" 2>&1; then
      fail ui-w4-06-playwright-failed
    fi
    UI_BROWSER_SMOKE=PASS
  else
    UI_BROWSER_SMOKE="SKIP-frontend-unreachable:$fe_code"
  fi
else
  UI_BROWSER_SMOKE=SKIP-no-playwright-module
fi
log "PASS ui_static=$UI_STATIC_CHECK ui_browser=$UI_BROWSER_SMOKE"
