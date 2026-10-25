#!/usr/bin/env bash
# =============================================================================
# verify-week2-pipeline.sh — Kiểm định tích hợp đầu cuối Tuần 2 (Task W2-10)
#
# Chuỗi kiểm chứng: OpenWeatherMap/GDACS -> Ingestion Engine -> PostGIS
#                   -> REST API GeoJSON RFC 7946 (WeatherController)
#
# Điểm vào nghiệm thu: DoD README Tuần 2 Mục 7 mục 6 — "script
# verify-week2-pipeline.sh pass toàn diện" trước cổng Milestone 1 kép (W2-11).
#
# Tiền điều kiện (script KHÔNG tự khởi động — chỉ fail-fast kèm chỉ dẫn):
#   1. docker compose -f VNstormgis-Deployment/docker-compose.yml up -d
#   2. Backend chạy: cd VNstormgis-Backend && ./gradlew bootRun \
#                        --args='--spring.profiles.active=dev'
#      với INTERNAL_SYNC_SECRET + OPENWEATHERMAP_API_KEY đã export
#   3. Biến môi trường khi chạy script: INTERNAL_SYNC_SECRET (bắt buộc)
#
# Tùy biến qua biến môi trường (mặc định trong ngoặc):
#   BACKEND_BASE_URL (http://localhost:8080) · DB_CONTAINER (weathergis-postgres)
#   DB_USER (gis_user) · DB_NAME (weather_gis_db) · EXPECTED_STATIONS (68)
#   LATENCY_BUDGET_MS (50) · FRESHNESS_SECONDS (600) · SKIP_SYNC (0)
#
# Exit code: 0 <=> Pass 100% · 1 <=> có FAIL hoặc preflight hỏng
# Tái chạy an toàn: chỉ ĐỌC CSDL/API + kích hoạt ingestion (append-only).
# =============================================================================
set -euo pipefail

# ----------------------------- Cấu hình tùy biến -----------------------------
BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://localhost:8080}"
DB_CONTAINER="${DB_CONTAINER:-weathergis-postgres}"
DB_USER="${DB_USER:-gis_user}"
DB_NAME="${DB_NAME:-weather_gis_db}"
EXPECTED_STATIONS="${EXPECTED_STATIONS:-68}"
LATENCY_BUDGET_MS="${LATENCY_BUDGET_MS:-50}"
FRESHNESS_SECONDS="${FRESHNESS_SECONDS:-600}"
SYNC_TIMEOUT_S="${SYNC_TIMEOUT_S:-300}"
SKIP_SYNC="${SKIP_SYNC:-0}"
INTERNAL_SYNC_SECRET="${INTERNAL_SYNC_SECRET:-}"

# ----------------------------- Bộ đếm & tiện ích -----------------------------
TOTAL=0
PASSED=0
FAILED=0
FAILED_LIST=()
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

if [ -t 1 ]; then
    BOLD=$'\033[1m'
    RED=$'\033[31m'
    RESET=$'\033[0m'
else
    BOLD=''
    RED=''
    RESET=''
fi

log()  { printf '\n%s== %s%s\n' "$BOLD" "$*" "$RESET"; }
pass() { TOTAL=$((TOTAL + 1)); PASSED=$((PASSED + 1)); printf '  [PASS] %s\n' "$*"; }
fail() {
    TOTAL=$((TOTAL + 1))
    FAILED=$((FAILED + 1))
    FAILED_LIST+=("$*")
    printf '  %s[FAIL]%s %s\n' "$RED" "$RESET" "$*"
}
skip() { printf '  [SKIP] %s\n' "$*"; }
die()  { printf '%s[LỖI PREFLIGHT]%s %s\n' "$RED" "$RESET" "$*" >&2; exit 1; }

check() {
    local name="$1"
    shift
    if "$@" >/dev/null 2>&1; then
        pass "$name"
    else
        fail "$name"
    fi
}

eq() { [ "$1" = "$2" ]; }
lt() { awk -v a="$1" -v b="$2" 'BEGIN{exit (a<b)?0:1}'; }
http_and_jq() { [ "$1" = "$2" ] && jq -e "$3" "$4" >/dev/null 2>&1; }
fresh_enough() { [ -n "$1" ] && lt "$1" "$2"; }

# GlobalExceptionHandler hiện trả mã lỗi ở trường "errorCode" (AGENTS.md ghi "code").
# Script bám hợp đồng thực thi để không đỏ giả; không sửa backend trong phạm vi W2-10.
is_problem() {
    jq -e '
        (.type | (type == "string" and length > 0))
        and (.status | type == "number")
        and (.title | (type == "string" and length > 0))
        and (.detail | (type == "string" and length > 0))
        and (.instance | (type == "string" and length > 0))
        and has("timestamp")
        and (.errorCode | (type == "string" and length > 0))
    ' "$1" >/dev/null 2>&1
}

# Truy vấn SQL qua container (không cần psql client cục bộ)
db() {
    docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c "$1" \
        2>/dev/null || true
}

# =============================================================================
# BƯỚC 0 — PREFLIGHT: dependencies, hạ tầng, bí mật (fail-fast)
# =============================================================================
log "BƯỚC 0/7 — PREFLIGHT MÔI TRƯỜNG"

for c in curl jq docker; do
    command -v "$c" >/dev/null 2>&1 || die "Thiếu lệnh '$c'. Cài đặt rồi chạy lại."
done

[ -n "$INTERNAL_SYNC_SECRET" ] \
    || die "Chưa export INTERNAL_SYNC_SECRET (biến backend dùng để xác thực /internal/sync)."

DB_HEALTH="$(docker inspect --format '{{.State.Health.Status}}' "$DB_CONTAINER" 2>/dev/null || true)"
[ "$DB_HEALTH" = "healthy" ] \
    || die "Container '$DB_CONTAINER' chưa healthy (trạng thái: '${DB_HEALTH:-không tìm thấy}'). Chạy: docker compose -f VNstormgis-Deployment/docker-compose.yml up -d"

# Backend sẵn sàng? (retry 30 lần × 2s — chờ bootRun khởi động xong Flyway)
BACKEND_UP=0
for _ in {1..30}; do
    if curl -sf -m 5 "$BACKEND_BASE_URL/actuator/health" 2>/dev/null \
        | jq -e '.status == "UP"' >/dev/null 2>&1; then
        BACKEND_UP=1
        break
    fi
    sleep 2
done
[ "$BACKEND_UP" = "1" ] \
    || die "Backend chưa UP tại $BACKEND_BASE_URL/actuator/health (đã thử 60s). Khởi động: cd VNstormgis-Backend && ./gradlew bootRun --args='--spring.profiles.active=dev'"

pass "Preflight: curl + jq + docker + postgres healthy + backend UP + secret sẵn sàng"

# =============================================================================
# BƯỚC 1 — KIỂM ĐỊNH BẢO MẬT ÂM TÍNH: fail-closed 401 SYN_401_INVALID_SECRET
# (bổ sung so với README — W2-03 quyết định #9, AGENTS.md mục 6)
# =============================================================================
log "BƯỚC 1/7 — BẢO MẬT ENDPOINT NỘI BỘ (fail-closed)"

CODE=$(curl -s -o "$TMP_DIR/401.json" -w '%{http_code}' -m 15 \
    -X POST "$BACKEND_BASE_URL/api/v1/internal/sync/weather" || true)
CODE="${CODE:-000}"
check "POST sync không kèm secret → HTTP 401" eq "$CODE" "401"
check "401 là Problem Detail có errorCode SYN_401_INVALID_SECRET" \
    http_and_jq "$CODE" "401" '.status == 401 and .errorCode == "SYN_401_INVALID_SECRET"' \
    "$TMP_DIR/401.json"
check "401 đủ trường RFC 7807 (type/status/title/detail/instance/timestamp)" \
    is_problem "$TMP_DIR/401.json"

# =============================================================================
# BƯỚC 2 — KÍCH HOẠT INGESTION OWM + GDACS (README Bước 1; retry 409)
# =============================================================================
log "BƯỚC 2/7 — KÍCH HOẠT INGESTION QUA ENDPOINT NỘI BỘ"

# trigger_sync <path> <body>: đặt SYNC_CODE; trả 0 khi HTTP 200.
# Không để lỗi curl/409 làm set -e giết script giữa chừng.
trigger_sync() {
    local path="$1"
    local body="$2"
    local attempt
    SYNC_CODE=000
    for attempt in 1 2 3; do
        if SYNC_CODE=$(curl -s -o "$body" -w '%{http_code}' -m "$SYNC_TIMEOUT_S" \
            -X POST -H "X-Internal-Secret: $INTERNAL_SYNC_SECRET" \
            "$BACKEND_BASE_URL$path"); then
            :
        else
            SYNC_CODE=000
        fi
        case "$SYNC_CODE" in
            200)
                return 0
                ;;
            409)
                if [ "$attempt" -lt 3 ]; then
                    printf '  … 409 SYN_409_SYNC_IN_PROGRESS (lần %s) — chờ 20s\n' "$attempt"
                    sleep 20
                else
                    return 1
                fi
                ;;
            401)
                printf '  … 401 — kiểm tra INTERNAL_SYNC_SECRET có trùng backend không\n' >&2
                return 1
                ;;
            *)
                return 1
                ;;
        esac
    done
    return 1
}

if [ "$SKIP_SYNC" = "1" ]; then
    skip "SKIP_SYNC=1 — bỏ trigger OWM (dùng dữ liệu đang có, vẫn kiểm chứng DB/API)"
else
    trigger_sync "/api/v1/internal/sync/weather" "$TMP_DIR/wsync.json" || true
    check "POST /internal/sync/weather → HTTP 200" eq "${SYNC_CODE:-000}" "200"
    check "Sync OWM: SUCCESS với ${EXPECTED_STATIONS}/${EXPECTED_STATIONS} trạm" \
        jq -e --argjson n "$EXPECTED_STATIONS" \
        '.success == true and .data.status == "SUCCESS"
         and .data.source == "OPENWEATHERMAP"
         and .data.totalStations == $n and .data.recordsProcessed == $n' \
        "$TMP_DIR/wsync.json"
fi

if [ "$SKIP_SYNC" = "1" ]; then
    skip "SKIP_SYNC=1 — bỏ trigger GDACS"
else
    trigger_sync "/api/v1/internal/sync/storms" "$TMP_DIR/ssync.json" || true
    check "POST /internal/sync/storms → HTTP 200" eq "${SYNC_CODE:-000}" "200"
    check "Sync GDACS: SUCCESS (feed có thể 0 sự kiện TC)" \
        jq -e '.success == true and .data.status == "SUCCESS" and .data.source == "GDACS"' \
        "$TMP_DIR/ssync.json"
fi

# =============================================================================
# BƯỚC 3 — KIỂM CHỨNG DỮ LIỆU TRONG POSTGIS (README Bước 2)
# =============================================================================
log "BƯỚC 3/7 — POSTGIS: weather_records, data_sync_logs, storms"

check "locations: đúng $EXPECTED_STATIONS trạm active" \
    eq "$(db "SELECT count(*) FROM locations WHERE is_active = true")" "$EXPECTED_STATIONS"
check "weather_records phủ đủ $EXPECTED_STATIONS trạm (DISTINCT location_id)" \
    eq "$(db "SELECT count(DISTINCT location_id) FROM weather_records")" "$EXPECTED_STATIONS"

check "data_sync_logs: lượt OWM mới nhất = SUCCESS" \
    eq "$(db "SELECT status FROM data_sync_logs WHERE source='OPENWEATHERMAP' ORDER BY started_at DESC LIMIT 1")" "SUCCESS"
check "data_sync_logs: lượt OWM mới nhất records_processed = $EXPECTED_STATIONS" \
    eq "$(db "SELECT records_processed FROM data_sync_logs WHERE source='OPENWEATHERMAP' ORDER BY started_at DESC LIMIT 1")" "$EXPECTED_STATIONS"
check "data_sync_logs: lượt GDACS mới nhất = SUCCESS" \
    eq "$(db "SELECT status FROM data_sync_logs WHERE source='GDACS' ORDER BY started_at DESC LIMIT 1")" "SUCCESS"

FRESH="$(db "SELECT COALESCE(EXTRACT(EPOCH FROM (now() - (SELECT max(recorded_at) FROM weather_records))), 999999)")"
check "Độ tươi dữ liệu: bản ghi mới nhất < ${FRESHNESS_SECONDS}s" \
    fresh_enough "${FRESH:-}" "$FRESHNESS_SECONDS"

STORM_CNT="$(db "SELECT count(*) FROM storms")"
if [ "${STORM_CNT:-0}" -gt 0 ]; then
    check "storms: mọi geom_current có SRID 4326" \
        eq "$(db "SELECT count(*) FROM storms WHERE ST_SRID(geom_current) <> 4326")" "0"
    check "storms: status chỉ nằm trong {active, dissipated} (SYS-RULE-001)" \
        eq "$(db "SELECT count(*) FROM storms WHERE status NOT IN ('active','dissipated')")" "0"
    TRACK_CNT="$(db "SELECT count(*) FROM storm_tracks")"
    if [ "${TRACK_CNT:-0}" -gt 0 ]; then
        check "storm_tracks: mọi geom có SRID 4326" \
            eq "$(db "SELECT count(*) FROM storm_tracks WHERE ST_SRID(geom) <> 4326")" "0"
    else
        skip "storm_tracks rỗng (bão mới chưa có mốc lịch sử thứ 2)"
    fi
else
    skip "GDACS feed hiện không có bão — bỏ qua kiểm tra bảng storms/storm_tracks (log GDACS vẫn phải SUCCESS ở trên)"
fi

# =============================================================================
# BƯỚC 4 — GEOJSON RFC 7946 + ĐỐI CHIẾU 68 MÃ TRẠM (README Bước 3)
# =============================================================================
log "BƯỚC 4/7 — GET /api/v1/weather/current: SCHEMA GEOJSON RFC 7946"

GEO="$TMP_DIR/current.geojson"
CODE=$(curl -s -o "$GEO" -w '%{http_code}' -m 15 \
    "$BACKEND_BASE_URL/api/v1/weather/current" || true)
CODE="${CODE:-000}"
check "HTTP 200" eq "$CODE" "200"
check "FeatureCollection với đúng $EXPECTED_STATIONS features" \
    jq -e --argjson n "$EXPECTED_STATIONS" \
    '.type == "FeatureCollection" and (.features | length) == $n' "$GEO"
check "Mọi feature: type=Feature, geometry=Point, coordinates 2 phần tử" \
    jq -e '[.features[] | (.type == "Feature" and .geometry.type == "Point"
        and (.geometry.coordinates | length) == 2)] | all' "$GEO"
# Bbox mở rộng tới 116°E để bao phủ trạm đảo tiền tiêu TSA (Quần đảo Trường Sa,
# 114.3667°E) trong seed V3. Vẫn bắt hoán đổi lat/lng vì mọi vĩ độ đều < 24.
check "TRẬT TỰ TỌA ĐỘ [lng, lat]: lng ∈ [102,116], lat ∈ [8,24] (bbox Việt Nam)" \
    jq -e '[.features[] | (.geometry.coordinates[0] >= 102 and .geometry.coordinates[0] <= 116
        and .geometry.coordinates[1] >= 8 and .geometry.coordinates[1] <= 24)] | all' "$GEO"
check "Properties đủ 8 khóa bắt buộc (code/name/region/temperature/wind_speed/wind_direction/rain_1h/recorded_at)" \
    jq -e '[.features[] | .properties | (has("code") and has("name") and has("region")
        and has("temperature") and has("wind_speed") and has("wind_direction")
        and has("rain_1h") and has("recorded_at"))] | all' "$GEO"
check "recorded_at định dạng ISO-8601" \
    jq -e '[.features[] | .properties.recorded_at
        | test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}")] | all' "$GEO"

db "SELECT code FROM locations WHERE is_active = true ORDER BY code" \
    | sed '/^$/d' > "$TMP_DIR/codes.db"
jq -r '.features[].properties.code' "$GEO" 2>/dev/null | sort > "$TMP_DIR/codes.api" || true
check "68 mã trạm khớp tuyệt đối CSDL ↔ GeoJSON payload" \
    diff -q "$TMP_DIR/codes.db" "$TMP_DIR/codes.api"

# =============================================================================
# BƯỚC 5 — HISTORY + HỢP ĐỒNG LỖI RFC 7807 (bổ sung — AGENTS.md mục 3)
# =============================================================================
log "BƯỚC 5/7 — GET /weather/history & HỢP ĐỒNG LỖI RFC 7807"

HIST="$TMP_DIR/hist.json"
CODE=$(curl -s -o "$HIST" -w '%{http_code}' -m 15 \
    "$BACKEND_BASE_URL/api/v1/weather/history?location_code=HAN&hours=24" || true)
CODE="${CODE:-000}"
check "history HAN 24h → HTTP 200 + success + readings không rỗng" \
    http_and_jq "$CODE" "200" '.success == true and (.data.readings | length) >= 1' "$HIST"

CODE=$(curl -s -o "$TMP_DIR/e400.json" -w '%{http_code}' -m 15 \
    "$BACKEND_BASE_URL/api/v1/weather/history?location_code=HAN&hours=999" || true)
CODE="${CODE:-000}"
check "hours=999 → HTTP 400 Problem Detail" \
    http_and_jq "$CODE" "400" '.status == 400' "$TMP_DIR/e400.json"
check "400 đủ trường RFC 7807" is_problem "$TMP_DIR/e400.json"

CODE=$(curl -s -o "$TMP_DIR/e404.json" -w '%{http_code}' -m 15 \
    "$BACKEND_BASE_URL/api/v1/weather/history?location_code=ZZZ999&hours=24" || true)
CODE="${CODE:-000}"
check "mã trạm không tồn tại → HTTP 404 WTH_404_NOT_FOUND" \
    http_and_jq "$CODE" "404" '.status == 404 and .errorCode == "WTH_404_NOT_FOUND"' \
    "$TMP_DIR/e404.json"
check "404 đủ trường RFC 7807" is_problem "$TMP_DIR/e404.json"

# =============================================================================
# BƯỚC 6 — THỜI GIAN PHẢN HỒI HTTP < 50ms (README Bước 4; warm-up + 3 mẫu)
# =============================================================================
log "BƯỚC 6/7 — HIỆU NĂNG: GET /weather/current < ${LATENCY_BUDGET_MS}ms"

URL="$BACKEND_BASE_URL/api/v1/weather/current"
curl -s -o /dev/null -m 15 "$URL" || true   # warm-up (loại noise JIT/HTTP pool lần đầu)

LAT_OK=1
LAT_SUM=0
LAT_MAX=0
for i in 1 2 3; do
    T=$(curl -s -o /dev/null -w '%{time_total}' -m 15 "$URL" || true)
    if [ -z "$T" ]; then
        printf '  mẫu %s: lỗi curl\n' "$i"
        LAT_OK=0
        continue
    fi
    MS=$(awk -v t="$T" 'BEGIN{printf "%.1f", t*1000}')
    LAT_SUM=$(awk -v a="$LAT_SUM" -v m="$MS" 'BEGIN{printf "%.1f", a+m}')
    LAT_MAX=$(awk -v a="$LAT_MAX" -v m="$MS" 'BEGIN{printf "%.1f", (m>a)?m:a}')
    printf '  mẫu %s: %sms\n' "$i" "$MS"
    awk -v m="$MS" -v b="$LATENCY_BUDGET_MS" 'BEGIN{exit (m<b)?0:1}' || LAT_OK=0
done
LAT_AVG=$(awk -v s="$LAT_SUM" 'BEGIN{printf "%.1f", s/3}')
check "Cả 3 mẫu đo đều < ${LATENCY_BUDGET_MS}ms (trung bình: ${LAT_AVG}ms · max: ${LAT_MAX}ms)" \
    eq "$LAT_OK" "1"

# =============================================================================
# BƯỚC 7 — BÁO CÁO TỔNG KẾT (README Bước 5: Pass 100%)
# =============================================================================
log "BƯỚC 7/7 — BÁO CÁO TỔNG KẾT"

printf '\n──────────────────────────────────────────────────────\n'
printf ' KẾT QUẢ KIỂM ĐỊNH PIPELINE TUẦN 2 (verify-week2-pipeline.sh)\n'
printf ' Pass: %s/%s · Fail: %s\n' "$PASSED" "$TOTAL" "$FAILED"
if [ "$FAILED" -gt 0 ] || [ "$TOTAL" -eq 0 ]; then
    if [ "$FAILED" -gt 0 ]; then
        printf ' Các hạng mục FAIL:\n'
        for f in "${FAILED_LIST[@]}"; do
            printf '   - %s\n' "$f"
        done
    fi
    printf ' TỶ LỆ PASS: %s%% — KHÔNG ĐẠT cổng nghiệm thu\n' \
        "$((TOTAL > 0 ? PASSED * 100 / TOTAL : 0))"
    printf '──────────────────────────────────────────────────────\n'
    exit 1
fi
printf ' TỶ LỆ PASS: 100%% — ĐẠT điều kiện DoD Mục 7.6 (README Tuần 2)\n'
printf '──────────────────────────────────────────────────────\n'
exit 0
