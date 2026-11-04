#!/usr/bin/env bash
# =============================================================================
# verify-week3-pipeline.sh — Kiểm định tích hợp đầu cuối Tuần 3 (Task W3-09)
#
# Chuỗi kiểm chứng: PostGIS/GDACS -> Storm API GeoJSON RFC 7946
#                   -> Redis Cache (HIT < 5ms, TTL đúng 3 vùng, fallback = 0)
#                   -> Hợp đồng UI tĩnh W3-07 (11 định danh)
#                   -> Graceful Degradation (Redis sập vẫn HTTP 200, hồi phục)
#
# Điểm vào nghiệm thu: DoD README Tuần 3 Mục 7 mục 1/5/6/7 — cổng Milestone 2.
# Log của script này là phụ lục Biên bản Milestone 2 (W3-10).
#
# Tiền điều kiện (script KHÔNG tự khởi động — chỉ fail-fast kèm chỉ dẫn):
#   1. docker compose -f VNstormgis-Deployment/docker-compose.yml up -d
#   2. Backend chạy:  cd VNstormgis-Backend && ./gradlew bootRun \
#                        --args='--spring.profiles.active=dev'
#   (KHÔNG cần INTERNAL_SYNC_SECRET — W3-09 phạm vi ĐỌC, đối chiếu #10)
#
# Tùy biến qua biến môi trường (mặc định trong ngoặc):
#   BACKEND_BASE_URL (http://localhost:8080) · REDIS_CONTAINER (weathergis-redis)
#   DB_CONTAINER (weathergis-postgres) · DB_USER (gis_user) · DB_NAME (weather_gis_db)
#   FRONTEND_DIR (VNstormgis-Frontend) · EXPECTED_STATIONS (68)
#   LATENCY_BUDGET_MS (5) · HIT_SAMPLES (5) · OUTAGE_BUDGET_MS (2000)
#   WEATHER_TTL_MIN (840) · WEATHER_TTL_MAX (900)
#   STORM_TTL_MIN (1740) · STORM_TTL_MAX (1800) · SKIP_DEGRADATION (0)
#
# Exit code: 0 <=> Pass 100% (SKIP được phép, phải giải trình trong báo cáo)
#            1 <=> có FAIL hoặc preflight hỏng
# Tái chạy an toàn: chỉ ĐỌC API/PostGIS. Ghi duy nhất: DEL 2 key cache tự tái
#                   sinh + stop/start container Redis (có trap khôi phục).
# =============================================================================
set -euo pipefail

# ----------------------------- Cấu hình tùy biến -----------------------------
BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://localhost:8080}"
REDIS_CONTAINER="${REDIS_CONTAINER:-weathergis-redis}"
DB_CONTAINER="${DB_CONTAINER:-weathergis-postgres}"
DB_USER="${DB_USER:-gis_user}"
DB_NAME="${DB_NAME:-weather_gis_db}"
FRONTEND_DIR="${FRONTEND_DIR:-VNstormgis-Frontend}"
EXPECTED_STATIONS="${EXPECTED_STATIONS:-68}"
LATENCY_BUDGET_MS="${LATENCY_BUDGET_MS:-5}"
HIT_SAMPLES="${HIT_SAMPLES:-5}"
OUTAGE_BUDGET_MS="${OUTAGE_BUDGET_MS:-2000}"
WEATHER_TTL_MIN="${WEATHER_TTL_MIN:-840}"
WEATHER_TTL_MAX="${WEATHER_TTL_MAX:-900}"
STORM_TTL_MIN="${STORM_TTL_MIN:-1740}"
STORM_TTL_MAX="${STORM_TTL_MAX:-1800}"
SKIP_DEGRADATION="${SKIP_DEGRADATION:-0}"
# STORM_WATCH_BBOX (đối chiếu #1): lng 100..128 · lat 0..28 — chỉ phân loại, không lọc
# Envelop ASSERT CỨNG (bắt hoán đổi [lat,lng]): lng 60..160 · lat -10..60

# ----------------------------- Bộ đếm & tiện ích -----------------------------
TOTAL=0
PASSED=0
FAILED=0
SKIPPED=0
FAILED_LIST=()
SKIP_LIST=()
TMP_DIR="$(mktemp -d)"
REDIS_STOPPED=0

if [ -t 1 ]; then
    BOLD=$'\033[1m'
    RED=$'\033[31m'
    GREEN=$'\033[32m'
    YELLOW=$'\033[33m'
    RESET=$'\033[0m'
else
    BOLD='' RED='' GREEN='' YELLOW='' RESET=''
fi

log()  { printf '\n%s== %s%s\n' "$BOLD" "$*" "$RESET"; }
pass() { TOTAL=$((TOTAL + 1)); PASSED=$((PASSED + 1)); printf '  [PASS] %s\n' "$*"; }
fail() {
    TOTAL=$((TOTAL + 1))
    FAILED=$((FAILED + 1))
    FAILED_LIST+=("$*")
    printf '  %s[FAIL]%s %s\n' "$RED" "$RESET" "$*"
}
skip() {
    SKIPPED=$((SKIPPED + 1))
    SKIP_LIST+=("$*")
    printf '  %s[SKIP]%s %s\n' "$YELLOW" "$RESET" "$*"
}
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
le() { awk -v a="$1" -v b="$2" 'BEGIN{exit (a<=b)?0:1}'; }
http_and_jq() { [ "$1" = "$2" ] && jq -e "$3" "$4" >/dev/null 2>&1; }
# jq có truyền số lượng kỳ vọng từ biến (EXPECTED_STATIONS)
jqn() { jq -e --argjson n "$EXPECTED_STATIONS" "$1" "$2" >/dev/null 2>&1; }

# GlobalExceptionHandler đặt mã lỗi trường "errorCode" (AGENTS.md ghi "code") — đối chiếu #6.
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

# GET <url> <outfile> [timeout] -> in mã HTTP ra stdout ('000' nếu lỗi)
get() {
    local code
    code=$(curl -s -o "$2" -w '%{http_code}' -m "${3:-15}" "$1" || true)
    printf '%s' "${code:-000}"
}

# redis-cli qua container (KHÔNG cần redis-cli cục bộ — đối chiếu #3)
rcli() { docker exec "$REDIS_CONTAINER" redis-cli "$@" 2>/dev/null | tr -d '\r' || true; }

# Giá trị hiện tại của một Micrometer counter ('0' khi thiếu)
metric_float() {
    local v
    v=$(curl -s -m 5 "$BACKEND_BASE_URL/actuator/metrics/$1" 2>/dev/null \
        | jq -r '(.measurements[0].value // 0)' 2>/dev/null || true)
    case "${v:-}" in
        ''|null) v=0 ;;
    esac
    printf '%s' "$v"
}

# TTL phải là số dương nằm trong (lo, hi]; -1 = không exp, -2 = không tồn tại -> fail
ttl_ok() {
    local t="$1" lo="$2" hi="$3"
    case "$t" in
        ''|*-*) return 1 ;;
    esac
    awk -v t="$t" -v lo="$lo" -v hi="$hi" 'BEGIN{exit (t>lo && t<=hi)?0:1}'
}

# sample_median <url> <n> -> "median|min|max|các mẫu" (xếp theo thời điểm lấy mẫu)
sample_median() {
    local url="$1" n="$2" i t ms
    local -a seq=()
    for ((i = 1; i <= n; i++)); do
        t=$(curl -s -o /dev/null -w '%{time_total}' -m 15 "$url" || true)
        [ -z "$t" ] && return 1
        ms=$(awk -v x="$t" 'BEGIN{printf "%.1f", x * 1000}')
        seq+=("$ms")
    done
    printf '%s\n' "${seq[@]}" | sort -n | awk -v vals="${seq[*]}" '
        { a[NR] = $1 }
        END {
            med = (NR % 2) ? a[(NR + 1) / 2] : (a[NR / 2] + a[NR / 2 + 1]) / 2
            printf "%.1f|%.1f|%.1f|%s", med, a[1], a[NR], vals
        }'
}

# Khôi phục Redis trên MỌI đường exit (đối chiếu #8)
ensure_redis_up() {
    docker start "$REDIS_CONTAINER" >/dev/null 2>&1 || true
}
cleanup() {
    local code=$?
    [ -n "${TMP_DIR:-}" ] && rm -rf "$TMP_DIR"
    if [ "$REDIS_STOPPED" = "1" ]; then
        ensure_redis_up
        printf '  %s[RECOVERY]%s %s được khôi phục sau kiểm định degradation\n' \
            "$YELLOW" "$RESET" "$REDIS_CONTAINER"
    fi
    exit "$code"
}
trap cleanup EXIT

# =============================================================================
# BƯỚC 0 — PREFLIGHT: dependencies, hạ tầng, backend (fail-fast)
# =============================================================================
log "BƯỚC 0/7 — PREFLIGHT MÔI TRƯỜNG"

for c in curl jq docker; do
    command -v "$c" >/dev/null 2>&1 || die "Thiếu lệnh '$c'. Cài đặt rồi chạy lại."
done

for ctr in "$DB_CONTAINER" "$REDIS_CONTAINER"; do
    H="$(docker inspect --format '{{.State.Health.Status}}' "$ctr" 2>/dev/null || true)"
    [ "$H" = "healthy" ] || die \
        "Container '$ctr' chưa healthy (trạng thái: '${H:-không tìm thấy}'). Chạy: docker compose -f VNstormgis-Deployment/docker-compose.yml up -d"
done

BACKEND_UP=0
for _ in {1..30}; do
    if curl -sf -m 5 "$BACKEND_BASE_URL/actuator/health" 2>/dev/null \
        | jq -e '.status == "UP"' >/dev/null 2>&1; then
        BACKEND_UP=1
        break
    fi
    sleep 2
done
[ "$BACKEND_UP" = "1" ] || die \
    "Backend chưa UP tại $BACKEND_BASE_URL/actuator/health (đã thử 60s). Khởi động: cd VNstormgis-Backend && ./gradlew bootRun --args='--spring.profiles.active=dev'"

ACTUATOR_METRICS=0
if curl -sf -m 5 "$BACKEND_BASE_URL/actuator/metrics" >/dev/null 2>&1; then
    ACTUATOR_METRICS=1
fi

pass "Preflight: curl + jq + docker · postgres + redis healthy · backend UP · actuator metrics=$ACTUATOR_METRICS"

# =============================================================================
# BƯỚC 1 — STORM COLLECTION: GeoJSON RFC 7946 + TRẬT TỰ TỌA ĐỘ + HỢP ĐỒNG 11 KHÓA
# =============================================================================
log "BƯỚC 1/7 — STORM API: GET /api/v1/storms?status=active"

SC="$TMP_DIR/storms.geojson"
CODE=$(get "$BACKEND_BASE_URL/api/v1/storms?status=active" "$SC")
check "HTTP 200" eq "$CODE" "200"
check "type=FeatureCollection và features là mảng" \
    http_and_jq "$CODE" "200" \
    '.type == "FeatureCollection" and (.features | type == "array")' "$SC"

TOTAL_STORMS="$(jq -r '.features | length' "$SC" 2>/dev/null || echo 0)"
printf '  — Bão active trả về: %s feature —\n' "$TOTAL_STORMS"

check "Mọi feature: type=Feature, geometry=Point, coordinates đúng 2 phần tử" \
    jq -e '[.features[] | (.type == "Feature" and .geometry.type == "Point"
        and (.geometry.coordinates | length) == 2)] | all' "$SC"

# ASSERT CỨNG (đối chiếu #1): vẫn bắt hoán đổi [lat,lng] vì sau swap
#   CHAMPI-26 -> lat ~ 127,4 (> 60) và lng ~ 31,2; TWO-26 -> lat ~ 81,8 (> 60).
check "ASSERT CỨNG — trật tự [lng, lat]: mọi điểm lng ∈ [60,160] và lat ∈ [-10,60]" \
    jq -e '[.features[] | .geometry.coordinates
        | (.[0] >= 60 and .[0] <= 160 and .[1] >= -10 and .[1] <= 60)] | all' "$SC"

check "ASSERT CỨNG — ràng buộc trắc địa tuyệt đối: lat ∈ [-90,90], lng ∈ [-180,180]" \
    jq -e '[.features[] | .geometry.coordinates
        | (.[1] >= -90 and .[1] <= 90 and .[0] >= -180 and .[0] <= 180)] | all' "$SC"

check "Mọi properties.status == \"active\" (lọc đúng chỉ số)" \
    jq -e '[.features[].properties.status] | all(. == "active")' "$SC"

check "Hợp đồng 11 khóa StormServiceImpl#properties() — đủ khóa, kể cả min_pressure/end_date null" \
    jq -e '[.features[] | .properties | (has("id") and has("name") and has("year")
        and has("category") and has("max_wind_speed") and has("max_wind_speed_kmh")
        and has("min_pressure") and has("status") and has("start_date")
        and has("end_date") and has("updated_at"))] | all' "$SC"

check "properties có ĐÚNG 11 khóa (không thừa — hợp đồng đóng băng W3-01 #1)" \
    jq -e '(.features | length == 0)
        or ((.features[0].properties | keys | length) == 11)' "$SC"

# ASSERT MỀM (đối chiếu #1): phân loại trong/ngoài STORM_WATCH_BBOX, KHÔNG lọc.
INSIDE="$(jq -r '[.features[] | .geometry.coordinates
    | (.[0] >= 100 and .[0] <= 128 and .[1] >= 0 and .[1] <= 28)]
    | map(select(.)) | length' "$SC" 2>/dev/null || echo 0)"
OUTSIDE=$((TOTAL_STORMS - INSIDE))
check "STORM_WATCH_BBOX: inside($INSIDE) + outside($OUTSIDE) == total($TOTAL_STORMS)" \
    eq "$((INSIDE + OUTSIDE))" "$TOTAL_STORMS"

if [ "$TOTAL_STORMS" -gt 0 ]; then
    if [ "$OUTSIDE" -gt 0 ]; then
        pass "Ngoài hộp theo dõi = $OUTSIDE — PASS theo W3-05 #4 (vẫn render + phù hiệu 'NGOÀI HỘP THEO DÕI', KHÔNG ẩn)"
        jq -r '.features[]
            | select((.geometry.coordinates[0] < 100) or (.geometry.coordinates[0] > 128)
                  or (.geometry.coordinates[1] < 0)  or (.geometry.coordinates[1] > 28))
            | "     · " + ((.properties.name // .properties.id) | tostring)
              + "  [lng=" + (.geometry.coordinates[0] | tostring)
              + ", lat=" + (.geometry.coordinates[1] | tostring) + "]"' "$SC"
    else
        pass "Toàn bộ $TOTAL_STORMS bão nằm trong STORM_WATCH_BBOX"
    fi
else
    skip "Biển Đông hiện không có bão active — nhóm phân loại bbox vacuous (HTTP 200 + features:[] vẫn PASS ở assert trên)"
fi

STORM_ID="$(jq -r 'if (.features | length) > 0
    then (.features[0].properties.id // "" | tostring) else "" end' "$SC" 2>/dev/null || echo "")"
case "$STORM_ID" in
    ''|null) STORM_ID="" ;;
esac

# =============================================================================
# BƯỚC 2 — STORM TRACK: LineString history >= 2 đỉnh + Point 9 khóa + nhánh SKIP
# =============================================================================
log "BƯỚC 2/7 — STORM TRACK: GET /api/v1/storms/{id}/track"

if [ -z "$STORM_ID" ]; then
    skip "Không có bão active — bỏ qua /track (README cho phép nhánh SKIP minh bạch)"
else
    TR="$TMP_DIR/track.geojson"
    CODE=$(get "$BACKEND_BASE_URL/api/v1/storms/$STORM_ID/track" "$TR")
    check "track $STORM_ID → HTTP 200" eq "$CODE" "200"
    check "track là FeatureCollection" \
        http_and_jq "$CODE" "200" \
        '.type == "FeatureCollection" and (.features | type == "array")' "$TR"

    TR_N="$(jq -r '.features | length' "$TR" 2>/dev/null || echo 0)"
    printf '  — Track %s: %s feature (history+forecast+Point+impact) —\n' "$STORM_ID" "$TR_N"

    if [ "$TR_N" -gt 0 ]; then
        # Vị trí tọa độ ở 3 bậc khác nhau theo kind: Point = coordinates,
        # LineString = coordinates[], Polygon = coordinates[][][] -> dùng recursive
        # descent `..` + chọn mảng 2 số, đúng cho cả 3 loại feature.
        # Biên đông 180 thay vì 160 (sai khác W3-09 #4): mốc lịch sử THẬT của CHAMPI-26
        # có 161,2°E; hoán đổi [lat,lng] vẫn bị bắt vì 'lat' sẽ thành ~161,2 > 60.
        check "Mọi đỉnh track là cặp [lng,lat] độ dài 2 + ASSERT CỨNG envelop lng ∈ [60,180] lat ∈ [-10,60]" \
            jq -e '[.. | select(type == "array" and length == 2
                    and (.[0] | type == "number") and (.[1] | type == "number"))
                | (.[0] >= 60 and .[0] <= 180 and .[1] >= -10 and .[1] <= 60)] | all' "$TR"

        HIST_N="$(jq -r '[.features[] | select(.properties.kind == "history"
            and .geometry.type == "LineString")] | length' "$TR" 2>/dev/null || echo 0)"
        if [ "$HIST_N" -eq 0 ]; then
            pass "$TR_N feature track nhưng chưa có LineString history — hợp đồng W3-02 cho phép"
            skip "LineString ≥ 2 đỉnh — bão $STORM_ID chưa có mốc lịch sử (không hạ ngưỡng, chỉ SKIP)"
        else
            check "LineString history có ≥ 2 đỉnh (hợp đồng bàn giao W3-02)" \
                jq -e '[.features[] | select(.properties.kind == "history"
                        and .geometry.type == "LineString")
                    | (.geometry.coordinates | length)]
                    | (length >= 1) and (min >= 2)' "$TR"
        fi

        check "kind ∈ {history, forecast, impact} hoặc null (Point không kind — W3-06 #6)" \
            jq -e '[.features[] | (.properties.kind // "NULL")]
                | all(. == "history" or . == "forecast" or . == "impact" or . == "NULL")' "$TR"

        check "Mọi Point mốc có đủ 9 khóa hợp đồng track" \
            jq -e '[.features[] | select(.geometry.type == "Point") | .properties
                | (has("track_id") and has("storm_id") and has("track_type")
                   and has("forecast_time") and has("wind_speed") and has("wind_gust")
                   and has("pressure") and has("category") and has("impact_radius_km"))] | all' "$TR"

        IMPACT_N="$(jq -r '[.features[] | select(.properties.kind == "impact")] | length' "$TR")"
        POLY_N="$(jq -r '[.features[] | select(.geometry.type == "Polygon")] | length' "$TR")"
        printf '  — Polygon impact live: %s · feature Polygon: %s (W3-06 #5: live 0 impact là BÌNH THƯỜNG, cấm bịa vòng) —\n' \
            "$IMPACT_N" "$POLY_N"
        check "Nếu có Polygon impact thì mỗi vòng ≥ 4 đỉnh đóng kín (không suy từ impact_radius_km)" \
            jq -e 'if ([.features[] | select(.geometry.type == "Polygon")] | length) == 0
                then true
                else ([.features[] | select(.geometry.type == "Polygon")
                      | (.geometry.coordinates[0] | length) >= 4] | all)
                end' "$TR"
    else
        pass "Track rỗng hợp lệ: HTTP 200 + features: [] (hợp đồng bàn giao W3-02 cho bão chưa có mốc)"
        skip "LineString ≥ 2 đỉnh — bão $STORM_ID chưa có mốc lịch sử"
    fi
fi

# =============================================================================
# BƯỚC 3 — NHÁNH ÂM TÍNH RFC 7807 (STM_*)
# =============================================================================
log "BƯỚC 3/7 — HỢP ĐỒNG LỖI RFC 7807 (STM_404_NOT_FOUND / STM_400_INVALID_STATUS)"

# id âm tính: UUID ngẫu nhiên. id là String (StormController dòng 169/214)
# -> chắc chắn 404, không cần seed DB (đối chiếu #12).
BAD_ID="$( (uuidgen 2>/dev/null || printf 'w309-%s-%s' "$RANDOM$RANDOM" "$(date +%s)") \
    | tr 'A-Z' 'a-z')"

CODE=$(get "$BACKEND_BASE_URL/api/v1/storms/$BAD_ID" "$TMP_DIR/s404.json")
check "GET /storms/{id-rác} → HTTP 404" eq "$CODE" "404"
check "404 errorCode = STM_404_NOT_FOUND + status = 404" \
    http_and_jq "$CODE" "404" \
    '.status == 404 and .errorCode == "STM_404_NOT_FOUND"' "$TMP_DIR/s404.json"
check "404 đủ trường RFC 7807 (type/status/title/detail/instance/timestamp/errorCode)" \
    is_problem "$TMP_DIR/s404.json"

CODE=$(get "$BACKEND_BASE_URL/api/v1/storms/$BAD_ID/track" "$TMP_DIR/t404.json")
check "GET /storms/{id-rác}/track → HTTP 404 STM_404_NOT_FOUND" \
    http_and_jq "$CODE" "404" \
    '.status == 404 and .errorCode == "STM_404_NOT_FOUND"' "$TMP_DIR/t404.json"
check "404 /track đủ trường RFC 7807" is_problem "$TMP_DIR/t404.json"

CODE=$(get "$BACKEND_BASE_URL/api/v1/storms?status=invalid" "$TMP_DIR/s400.json")
check "GET /storms?status=invalid → HTTP 400" eq "$CODE" "400"
check "400 errorCode = STM_400_INVALID_STATUS + status = 400" \
    http_and_jq "$CODE" "400" \
    '.status == 400 and .errorCode == "STM_400_INVALID_STATUS"' "$TMP_DIR/s400.json"
check "400 đủ trường RFC 7807" is_problem "$TMP_DIR/s400.json"

CODE=$(get "$BACKEND_BASE_URL/api/v1/storms?status=all&format=json" "$TMP_DIR/s200.json")
check "GET /storms?status=all&format=json → HTTP 200 (ngoài 3 vùng cache, thẳng PostGIS)" \
    eq "$CODE" "200"

# =============================================================================
# BƯỚC 4 — REDIS CACHE: HIT < 5ms · TTL ĐÚNG 3 VÙNG · FALLBACK Δ = 0
# =============================================================================
log "BƯỚC 4/7 — REDIS CACHE (W3-08 số chuẩn: HIT 2,029 / 1,169 ms)"

W_URL="$BACKEND_BASE_URL/api/v1/weather/current"
S_URL="$BACKEND_BASE_URL/api/v1/storms?status=active"
W_KEY="weathergis:cache:weather_current::geojson"
S_KEY="weathergis:cache:active_storms::geojson:active"

# Prime: lượt đầu CÓ THỂ là MISS (Redis bàn giao dbsize=0 từ W3-08).
curl -s -o /dev/null -m 30 "$W_URL" || true
curl -s -o /dev/null -m 30 "$S_URL" || true

check "key $W_KEY TỒN TẠI trong Redis" eq "$(rcli EXISTS "$W_KEY")" "1"
check "key $S_KEY TỒN TẠI trong Redis" eq "$(rcli EXISTS "$S_KEY")" "1"

W_TTL="$(rcli TTL "$W_KEY")"
S_TTL="$(rcli TTL "$S_KEY")"
printf '  — TTL đo được: weather_current=%ss · active_storms=%ss (Redis DBSIZE: %s) —\n' \
    "${W_TTL:-?}" "${S_TTL:-?}" "$(rcli DBSIZE)"
check "TTL weather_current ${W_TTL}s ∈ (${WEATHER_TTL_MIN}, ${WEATHER_TTL_MAX}] — vùng 15 phút" \
    ttl_ok "$W_TTL" "$WEATHER_TTL_MIN" "$WEATHER_TTL_MAX"
check "TTL active_storms ${S_TTL}s ∈ (${STORM_TTL_MIN}, ${STORM_TTL_MAX}] — vùng 30 phút" \
    ttl_ok "$S_TTL" "$STORM_TTL_MIN" "$STORM_TTL_MAX"

# Payload vẫn đúng sau khi qua cache (không lạc shape miss<->hit) — đối chiếu #4 W3-08:
# byte-identical đã được W3-08 chứng minh bằng sha256; ở đây kiểm cấu trúc + đủ 68 trạm.
GEO_W="$TMP_DIR/cached-weather.geojson"
CODE=$(get "$W_URL" "$GEO_W")
check "/weather/current qua cache → HTTP 200" eq "$CODE" "200"
check "/weather/current qua cache vẫn đủ $EXPECTED_STATIONS trạm, mỗi trạm [lng,lat] ∈ bbox Việt Nam (102–116 / 8–24)" \
    jqn '.type == "FeatureCollection" and (.features | length) == $n
        and ([.features[] | (.geometry.type == "Point"
              and (.geometry.coordinates | length) == 2
              and (.geometry.coordinates[0] >= 102) and (.geometry.coordinates[0] <= 116)
              and (.geometry.coordinates[1] >= 8)   and (.geometry.coordinates[1] <= 24))]
            | all)' "$GEO_W"

# Đo HIT: median 5 mẫu tuần tự (đối chiếu #7) — KHÔNG dùng c=10 của W3-08.
FB_BEFORE="$(metric_float weathergis.cache.fallback)"
for spec in "weather_current|$W_URL" "active_storms|$S_URL"; do
    NAME="${spec%%|*}"
    URL="${spec#*|}"
    if RES="$(sample_median "$URL" "$HIT_SAMPLES")"; then
        MED="${RES%%|*}"; R1="${RES#*|}"
        MIN="${R1%%|*}"; R2="${R1#*|}"
        MAX="${R2%%|*}"; SAMPLES="${R2#*|}"
        printf '  %s: median=%sms min=%sms max=%sms · mẫu: %s\n' \
            "$NAME" "$MED" "$MIN" "$MAX" "$SAMPLES"
        check "HIT $NAME: median ${MED}ms < ${LATENCY_BUDGET_MS}ms (chuẩn W3-08)" \
            lt "$MED" "$LATENCY_BUDGET_MS"
    else
        fail "HIT $NAME: không lấy được mẫu curl"
    fi
done
FB_AFTER="$(metric_float weathergis.cache.fallback)"

if [ "$ACTUATOR_METRICS" = "1" ]; then
    check "Δ counter weathergis.cache.fallback = 0 trong cửa sổ HIT ($FB_BEFORE → $FB_AFTER)" \
        eq "$FB_BEFORE" "$FB_AFTER"
else
    skip "Actuator /metrics không đọc được — bỏ qua assert fallback Δ và acquire Δ"
fi

# Corroboration tùy chọn (W3-08 #3): Timer acquire chỉ có dữ liệu khi thực sự phải chờ
# connection; thiếu dữ liệu => thông tin, không phải FAIL (đối chiếu #7).
ACQ="$(metric_float hikaricp.connections.acquire)"
printf '  — hikaricp.connections.acquire (tham khảo, không phải cổng): %s —\n' "${ACQ:-n/a}"
if [ "$ACTUATOR_METRICS" = "1" ] && [ "${ACQ:-0}" != "0" ]; then
    skip "acquire > 0 trong cửa sổ HIT ($ACQ) — có thể do eviction/nhiễu dev :8080; W3-08 đo Δacquire 200→0"
fi

# =============================================================================
# BƯỚC 5 — HỢP ĐỒNG UI TĨNH (11 ĐỊNH DANH W3-07) — grep, 0 dependency (đối chiếu #2)
# =============================================================================
log "BƯỚC 5/7 — HỢP ĐỒNG UI TĨNH: 11 định danh điều khiển lớp (W3-07)"

LCD="$FRONTEND_DIR/src/components/dashboard/LayerControlDrawer.tsx"
UAL="$FRONTEND_DIR/src/components/layout/UnifiedAppLayout.tsx"
MF="$FRONTEND_DIR/src/components/map/markerFactory.ts"

for f in "$LCD" "$UAL" "$MF"; do
    [ -f "$f" ] || die "Không thấy $f — đặt FRONTEND_DIR cho đúng (repo dùng VNstormgis-Frontend/)."
done

for id in "legend-risk-" "legend-track-history" "legend-track-forecast" "legend-track-impact" \
          "legend-outside-box" "last-sync-badge" "last-sync-storm" "storm-layer-count" \
          "data-storm-state" "data-storm-last-sync"; do
    check "LayerControlDrawer.tsx khai báo $id" grep -q -- "$id" "$LCD"
done
check "UnifiedAppLayout.tsx khai báo data-storm-ops-mode" \
    grep -q -- 'data-storm-ops-mode' "$UAL"

# legend-risk là template literal `legend-risk-${level}` -> chuỗi tĩnh chỉ chứng minh
# TIỀN TỐ; 4 cấp phải được chứng minh ở nguồn hằng số STORM_RISK_CONFIG.
for lvl in RED ORANGE GREEN UNCLASSIFIED; do
    check "STORM_RISK_CONFIG khai báo cấp $lvl (nguồn của legend-risk-\$level)" \
        grep -qE "^[[:space:]]+${lvl}[[:space:]]*:[[:space:]]*\{[[:space:]]*label:" "$MF"
done
check "StormRiskLevel union khai đủ 4 bậc (hợp đồng màu duy nhất — 0 hex thứ hai)" \
    grep -qE 'StormRiskLevel = "RED" \| "ORANGE" \| "GREEN" \| "UNCLASSIFIED"' "$MF"

# =============================================================================
# BƯỚC 6 — GRACEFUL DEGRADATION (ĐẶT CUỐI CÙNG: phá trạng thái cache — đối chiếu #8)
# =============================================================================
log "BƯỚC 6/7 — GRACEFUL DEGRADATION: Redis sập → vẫn HTTP 200 → hồi phục cache"

if [ "$SKIP_DEGRADATION" = "1" ]; then
    skip "SKIP_DEGRADATION=1 — runner không có quyền docker (README cho phép tách nhánh này)"
else
    printf '  Blast radius: dùng chung container Redis của backend dev :8080 — đối xử tiền lệ W3-03 D-5\n'
    if ! docker stop -t 2 "$REDIS_CONTAINER" >/dev/null 2>&1; then
        skip "Không dừng được $REDIS_CONTAINER (thiếu quyền docker) — chạy lại với SKIP_DEGRADATION=1"
    else
        REDIS_STOPPED=1
        FB_OFF_A="$(metric_float weathergis.cache.fallback)"

        NBAD=0
        NREQ=0
        OFF_MAX=0
        for i in 1 2 3 4 5; do
            for spec in "weather|$W_URL" "storms|$S_URL"; do
                NAME="${spec%%|*}"
                URL="${spec#*|}"
                probe="$TMP_DIR/off-$i-$NAME.json"
                # Lấy cả mã HTTP lẫn thời gian trong CÙNG curl (không xin 2 request)
                CT=$(curl -s -o "$probe" -w '%{http_code} %{time_total}' -m 10 "$URL" 2>/dev/null \
                    || printf '000 0')
                C="${CT%% *}"
                T="${CT##* }"
                NREQ=$((NREQ + 1))
                if [ "$C" = "200" ] && [ "${T:-0}" != "0" ]; then
                    MS=$(awk -v x="$T" 'BEGIN{printf "%.1f", x * 1000}')
                    OFF_MAX=$(awk -v a="$OFF_MAX" -v m="$MS" 'BEGIN{printf "%.1f", (m > a) ? m : a}')
                else
                    NBAD=$((NBAD + 1))
                    MS="n/a"
                fi
                printf '     · mẫu %s %-8s → HTTP %s · %sms\n' "$i" "$NAME" "$C" "$MS"
            done
        done
        check "Redis SẬP: $NREQ request (2 endpoint × 5 lượt) đều HTTP 200 — số request lỗi = $NBAD" \
            eq "$NBAD" "0"
        check "Redis SẬP: lượt fallback vẫn bị chặn bởi command timeout (max ${OFF_MAX}ms < ${OUTAGE_BUDGET_MS}ms)" \
            le "$OFF_MAX" "$OUTAGE_BUDGET_MS"

        FB_OFF_B="$(metric_float weathergis.cache.fallback)"
        check "Redis SẬP: counter weathergis.cache.fallback TĂNG ($FB_OFF_A → $FB_OFF_B) — bằng chứng fail-open thật" \
            awk -v a="$FB_OFF_A" -v b="$FB_OFF_B" 'BEGIN{exit (b>a)?0:1}'

        if ! docker start "$REDIS_CONTAINER" >/dev/null 2>&1; then
            fail "Không tự khởi động lại được $REDIS_CONTAINER — chạy: docker start $REDIS_CONTAINER"
        else
            REDIS_STOPPED=0
            REDIS_OK=0
            for _ in {1..15}; do
                [ "$(docker inspect --format '{{.State.Health.Status}}' "$REDIS_CONTAINER" 2>/dev/null || true)" = "healthy" ] \
                    && { REDIS_OK=1; break; }
                sleep 2
            done
            check "$REDIS_CONTAINER trở lại healthy" eq "$REDIS_OK" "1"

            # Đối chiếu #5: AOF + volume => key SỐNG SÓT qua stop/start, nên phải DEL
            # chủ động rồi re-prime thì phép đo hồi phục mới phân biệt được HIT/MISS.
            rcli DEL "$W_KEY" "$S_KEY" >/dev/null
            curl -s -o /dev/null -m 30 "$W_URL" || true
            curl -s -o /dev/null -m 30 "$S_URL" || true
            check "Sau hồi phục: key weather_current được nạp lại" eq "$(rcli EXISTS "$W_KEY")" "1"
            check "Sau hồi phục: key active_storms được nạp lại" eq "$(rcli EXISTS "$S_KEY")" "1"

            FB_REC_A="$(metric_float weathergis.cache.fallback)"
            if RES="$(sample_median "$W_URL" 3)"; then
                MED="${RES%%|*}"
                check "Sau hồi phục: /weather/current HIT median ${MED}ms < ${LATENCY_BUDGET_MS}ms (cache tự hoạt động lại)" \
                    lt "$MED" "$LATENCY_BUDGET_MS"
            else
                fail "Sau hồi phục: không đo được HIT /weather/current"
            fi
            FB_REC_B="$(metric_float weathergis.cache.fallback)"
            check "Sau hồi phục: Δ fallback = 0 ($FB_REC_A → $FB_REC_B)" eq "$FB_REC_A" "$FB_REC_B"
        fi
    fi
fi

# =============================================================================
# BƯỚC 7 — BÁO CÁO TỔNG KẾT (README Mục 3.5: Pass 100% + exit code chuẩn CI)
# =============================================================================
log "BƯỚC 7/7 — BÁO CÁO TỔNG KẾT"

printf '\n──────────────────────────────────────────────────────\n'
printf ' KẾT QUẢ KIỂM ĐỊNH PIPELINE TUẦN 3 (verify-week3-pipeline.sh)\n'
printf '   Pass : %s/%s\n' "$PASSED" "$TOTAL"
printf '   Fail : %s\n' "$FAILED"
printf '   Skip : %s (phải giải trình trong báo cáo — đối chiếu #15)\n' "$SKIPPED"
if [ "$SKIPPED" -gt 0 ]; then
    printf '   Danh sách SKIP:\n'
    for s in "${SKIP_LIST[@]}"; do
        printf '     - %s\n' "$s"
    done
fi
if [ "$FAILED" -gt 0 ] || [ "$TOTAL" -eq 0 ]; then
    if [ "$FAILED" -gt 0 ]; then
        printf '   Các hạng mục FAIL:\n'
        for f in "${FAILED_LIST[@]}"; do
            printf '     - %s\n' "$f"
        done
    else
        printf '   (TOTAL = 0 — script không chạy assert nào, coi như KHÔNG ĐẠT)\n'
    fi
    printf ' TỶ LỆ PASS: %s%% — KHÔNG ĐẠT cổng nghiệm thu Milestone 2\n' \
        "$((TOTAL > 0 ? PASSED * 100 / TOTAL : 0))"
    printf '──────────────────────────────────────────────────────\n'
    exit 1
fi
printf ' TỶ LỆ PASS: 100%% — ĐẠT DoD Mục 7.7 (README Tuần 3)\n'
printf '──────────────────────────────────────────────────────\n'
exit 0
