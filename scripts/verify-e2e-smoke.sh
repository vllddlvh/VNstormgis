#!/usr/bin/env bash
# ==============================================================================
# VIETNAM WEATHER WEBGIS (VNSTORMGIS) - SPRINT 1
# KỊCH BẢN KIỂM ĐỊNH SMOKE TEST ĐẦU CUỐI E2E (TASK W1-14)
# ==============================================================================
# Mục tiêu: Xác thực toàn bộ chuỗi mắt xích từ PostgreSQL 16 + PostGIS 3.4
#          ➔ Spring Boot 3.3 Backend (:8080)
#          ➔ Vite Dev Reverse Proxy (:5173/api)
#          ➔ Dữ liệu 68 trạm quan trắc trắc địa WGS84 lên WebGIS Leaflet.
#
# Ma trận kiểm thử nghiệm thu:
#   TC-E2E-01: Kiểm tra số lượng trạm nạp (đúng 68 trạm WGS84)
#   TC-E2E-02: Kiểm tra độ phân tán địa lý (Cực Bắc Hà Giang ➔ Cực Nam Cà Mau)
#   TC-E2E-03: Kiểm tra chủ quyền hải đảo (Hoàng Sa HSA & Trường Sa TSA)
#   TC-E2E-04: Kiểm tra dữ liệu trạm Thủ đô Hà Nội [HAN]
#   TC-E2E-05: Kiểm tra phân rã bộ lọc vùng miền (Bắc Bộ, Trung Bộ, Nam Bộ, Biển Đảo)
#   TC-E2E-06: Kiểm tra khả năng kháng lỗi RFC 7807 Problem Details
# ==============================================================================

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

report_pass() {
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    PASSED_TESTS=$((PASSED_TESTS + 1))
    echo -e " ${GREEN}[PASS]${NC} $1"
}

report_fail() {
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    FAILED_TESTS=$((FAILED_TESTS + 1))
    echo -e " ${RED}[FAIL]${NC} $1: $2"
}

echo -e "${CYAN}==============================================================================${NC}"
echo -e "${CYAN}   VNSTORMGIS - KIỂM ĐỊNH SMOKE TEST ĐẦU CUỐI E2E (TASK W1-14)                ${NC}"
echo -e "${CYAN}==============================================================================${NC}"
echo "Thời điểm kiểm thử: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# Xác định URL mục tiêu (thông qua Vite Reverse Proxy hoặc trực tiếp Spring Boot)
PROXY_BASE_URL="http://localhost:5173/api"
BACKEND_BASE_URL="http://localhost:8080/api"

# Kiểm tra tính sẵn sàng của Backend và Vite Proxy
echo -e "${BLUE}--- 0. Khởi Động & Kiểm Tra Kết Nối Mạng Loopback ---${NC}"

HEALTH_STATUS=$(curl -s "http://localhost:8080/actuator/health" | jq -r '.status // empty')
if [ "$HEALTH_STATUS" = "UP" ]; then
    report_pass "Backend Spring Boot Actuator báo trạng thái UP (:8080/actuator/health)"
else
    report_fail "Backend Spring Boot Actuator" "Không phản hồi UP (Giá trị: '$HEALTH_STATUS')"
fi

PROXY_CHECK=$(curl -s "$PROXY_BASE_URL/v1/locations" | jq -r '.success // empty')
if [ "$PROXY_CHECK" = "true" ]; then
    report_pass "Vite Dev Reverse Proxy (:5173/api ➔ :8080/api) thông mạch HTTP Loopback"
else
    report_fail "Vite Dev Reverse Proxy" "Không chuyển tiếp thành công tới Backend"
fi

echo ""
echo -e "${BLUE}--- 1. TC-E2E-01: Kiểm Tra Số Lượng Trạm Nạp (68 Trạm PostGIS) ---${NC}"

# Kiểm tra JSON Envelope
JSON_COUNT=$(curl -s "$PROXY_BASE_URL/v1/locations?format=json" | jq -r '.data | length')
if [ "$JSON_COUNT" -eq 68 ]; then
    report_pass "Định dạng JSON Envelope trả về đúng chính xác 68 trạm quan trắc"
else
    report_fail "TC-E2E-01 (JSON Envelope)" "Kỳ vọng 68 trạm nhưng nhận được: $JSON_COUNT"
fi

# Kiểm tra GeoJSON RFC 7946 FeatureCollection
GEOJSON_COUNT=$(curl -s "$PROXY_BASE_URL/v1/locations?format=geojson" | jq -r '.features | length')
GEOJSON_TYPE=$(curl -s "$PROXY_BASE_URL/v1/locations?format=geojson" | jq -r '.type')
if [ "$GEOJSON_COUNT" -eq 68 ] && [ "$GEOJSON_TYPE" = "FeatureCollection" ]; then
    report_pass "Định dạng GeoJSON RFC 7946 trả về FeatureCollection với 68 đối tượng không gian"
else
    report_fail "TC-E2E-01 (GeoJSON)" "Kỳ vọng 68 FeatureCollection nhưng nhận type=$GEOJSON_TYPE, count=$GEOJSON_COUNT"
fi

echo ""
echo -e "${BLUE}--- 2. TC-E2E-02: Kiểm Tra Độ Phân Tán Địa Lý (Bắc ➔ Nam Toàn Vẹn) ---${NC}"

# Cực Bắc: Hà Giang (HGG)
HGG_LAT=$(curl -s "$PROXY_BASE_URL/v1/locations/code/HGG" | jq -r '.data.latitude')
HGG_LNG=$(curl -s "$PROXY_BASE_URL/v1/locations/code/HGG" | jq -r '.data.longitude')
if (( $(echo "$HGG_LAT > 22.5" | bc -l) )); then
    report_pass "Trạm Cực Bắc Hà Giang (HGG): Vĩ độ $HGG_LAT°N, Kinh độ $HGG_LNG°E (> 22.5°N)"
else
    report_fail "TC-E2E-02 (Cực Bắc HGG)" "Vĩ độ không đạt ngưỡng Cực Bắc: $HGG_LAT"
fi

# Cực Nam: Cà Mau (CMU)
CMU_LAT=$(curl -s "$PROXY_BASE_URL/v1/locations/code/CMU" | jq -r '.data.latitude')
CMU_LNG=$(curl -s "$PROXY_BASE_URL/v1/locations/code/CMU" | jq -r '.data.longitude')
if (( $(echo "$CMU_LAT < 9.5" | bc -l) )); then
    report_pass "Trạm Cực Nam Cà Mau (CMU): Vĩ độ $CMU_LAT°N, Kinh độ $CMU_LNG°E (< 9.5°N)"
else
    report_fail "TC-E2E-02 (Cực Nam CMU)" "Vĩ độ không đạt ngưỡng Cực Nam: $CMU_LAT"
fi

echo ""
echo -e "${BLUE}--- 3. TC-E2E-03: Kiểm Tra Chủ Quyền Hải Đảo (Hoàng Sa & Trường Sa) ---${NC}"

# Hoàng Sa (HSA)
HSA_NAME=$(curl -s "$PROXY_BASE_URL/v1/locations/code/HSA" | jq -r '.data.name')
HSA_REGION=$(curl -s "$PROXY_BASE_URL/v1/locations/code/HSA" | jq -r '.data.region')
HSA_LAT=$(curl -s "$PROXY_BASE_URL/v1/locations/code/HSA" | jq -r '.data.latitude')
HSA_LNG=$(curl -s "$PROXY_BASE_URL/v1/locations/code/HSA" | jq -r '.data.longitude')
if [ "$HSA_REGION" = "Biển Đông" ] && (( $(echo "$HSA_LNG > 111.0" | bc -l) )); then
    report_pass "Trạm Hoàng Sa (HSA): '$HSA_NAME' [Vùng: $HSA_REGION, $HSA_LAT°N, $HSA_LNG°E]"
else
    report_fail "TC-E2E-03 (Hoàng Sa)" "Dữ liệu không khớp: region=$HSA_REGION, lng=$HSA_LNG"
fi

# Trường Sa (TSA)
TSA_NAME=$(curl -s "$PROXY_BASE_URL/v1/locations/code/TSA" | jq -r '.data.name')
TSA_REGION=$(curl -s "$PROXY_BASE_URL/v1/locations/code/TSA" | jq -r '.data.region')
TSA_LAT=$(curl -s "$PROXY_BASE_URL/v1/locations/code/TSA" | jq -r '.data.latitude')
TSA_LNG=$(curl -s "$PROXY_BASE_URL/v1/locations/code/TSA" | jq -r '.data.longitude')
if [ "$TSA_REGION" = "Biển Đông" ] && (( $(echo "$TSA_LNG > 114.0" | bc -l) )); then
    report_pass "Trạm Trường Sa (TSA): '$TSA_NAME' [Vùng: $TSA_REGION, $TSA_LAT°N, $TSA_LNG°E]"
else
    report_fail "TC-E2E-03 (Trường Sa)" "Dữ liệu không khớp: region=$TSA_REGION, lng=$TSA_LNG"
fi

echo ""
echo -e "${BLUE}--- 4. TC-E2E-04: Kiểm Tra Trạm Thủ Đô Hà Nội [HAN] ---${NC}"

HAN_RES=$(curl -s "$PROXY_BASE_URL/v1/locations/code/HAN")
HAN_CODE=$(echo "$HAN_RES" | jq -r '.data.code')
HAN_NAME=$(echo "$HAN_RES" | jq -r '.data.name')
HAN_LAT=$(echo "$HAN_RES" | jq -r '.data.latitude')
HAN_LNG=$(echo "$HAN_RES" | jq -r '.data.longitude')

if [ "$HAN_CODE" = "HAN" ] && [ "$HAN_NAME" = "Hà Nội" ] && [ "$HAN_LAT" = "21.0285" ] && [ "$HAN_LNG" = "105.8542" ]; then
    report_pass "Trạm Thủ đô Hà Nội: [HAN // HÀ NỘI] tọa độ chuẩn 21.0285°N, 105.8542°E"
else
    report_fail "TC-E2E-04 (Hà Nội)" "Tọa độ hoặc thông tin trạm sai khác: $HAN_RES"
fi

echo ""
echo -e "${BLUE}--- 5. TC-E2E-05: Kiểm Tra Phân Rã Bộ Lọc Vùng Miền ---${NC}"

ALL_REGIONS=$(curl -s "$PROXY_BASE_URL/v1/locations" | jq -r '.data | map(.region) | unique | .[]')
NORTH_COUNT=$(curl -s "$PROXY_BASE_URL/v1/locations" | jq -r '[.data[] | select(.region == "Bắc Bộ")] | length')
CENTRAL_COUNT=$(curl -s "$PROXY_BASE_URL/v1/locations" | jq -r '[.data[] | select(.region | contains("Trung Bộ"))] | length')
HIGHLANDS_COUNT=$(curl -s "$PROXY_BASE_URL/v1/locations" | jq -r '[.data[] | select(.region == "Tây Nguyên")] | length')
SOUTH_COUNT=$(curl -s "$PROXY_BASE_URL/v1/locations" | jq -r '[.data[] | select(.region == "Nam Bộ")] | length')
ISLANDS_COUNT=$(curl -s "$PROXY_BASE_URL/v1/locations" | jq -r '[.data[] | select(.region | startswith("Biển"))] | length')

SUM_REGIONS=$((NORTH_COUNT + CENTRAL_COUNT + HIGHLANDS_COUNT + SOUTH_COUNT + ISLANDS_COUNT))

if [ "$NORTH_COUNT" -eq 25 ] && [ "$CENTRAL_COUNT" -eq 14 ] && [ "$HIGHLANDS_COUNT" -eq 5 ] && [ "$SOUTH_COUNT" -eq 19 ] && [ "$ISLANDS_COUNT" -eq 5 ] && [ "$SUM_REGIONS" -eq 68 ]; then
    report_pass "Bộ lọc vùng miền phân bổ tuyệt đối chuẩn xác: Bắc Bộ ($NORTH_COUNT), Trung Bộ ($CENTRAL_COUNT), Tây Nguyên ($HIGHLANDS_COUNT), Nam Bộ ($SOUTH_COUNT), Biển Đảo ($ISLANDS_COUNT) ➔ Tổng = $SUM_REGIONS trạm"
else
    report_fail "TC-E2E-05 (Bộ lọc vùng miền)" "Số lượng vùng miền sai lệch: Bắc=$NORTH_COUNT, Trung=$CENTRAL_COUNT, Tây Nguyên=$HIGHLANDS_COUNT, Nam=$SOUTH_COUNT, Đảo=$ISLANDS_COUNT"
fi

echo ""
echo -e "${BLUE}--- 6. TC-E2E-06: Kiểm Tra Kháng Lỗi RFC 7807 Problem Details ---${NC}"

# Kiểm tra lỗi 404 khi mã trạm không tồn tại
ERR_404=$(curl -s -w "\n%{http_code}" "$PROXY_BASE_URL/v1/locations/code/NOT_EXIST_CODE")
HTTP_CODE_404=$(echo "$ERR_404" | tail -n 1)
PROBLEM_404=$(echo "$ERR_404" | sed '$d')
CODE_404=$(echo "$PROBLEM_404" | jq -r '.status // empty')
ERR_TITLE_404=$(echo "$PROBLEM_404" | jq -r '.title // empty')

if [ "$HTTP_CODE_404" = "404" ] && [ "$CODE_404" = "404" ]; then
    report_pass "Xử lý lỗi 404 chuẩn Problem Details: status=404, title='$ERR_TITLE_404'"
else
    report_fail "TC-E2E-06 (Lỗi 404)" "Kỳ vọng HTTP 404 ProblemDetail, nhận http=$HTTP_CODE_404, payload=$PROBLEM_404"
fi

# Kiểm tra lỗi 400 khi tọa độ nearby vượt giới hạn trắc địa
ERR_400=$(curl -s -w "\n%{http_code}" "$PROXY_BASE_URL/v1/locations/nearby?lng=999&lat=999")
HTTP_CODE_400=$(echo "$ERR_400" | tail -n 1)
PROBLEM_400=$(echo "$ERR_400" | sed '$d')
CODE_400=$(echo "$PROBLEM_400" | jq -r '.status // empty')
ERR_TITLE_400=$(echo "$PROBLEM_400" | jq -r '.title // empty')

if [ "$HTTP_CODE_400" = "400" ] && [ "$CODE_400" = "400" ]; then
    report_pass "Xử lý lỗi 400 trắc địa chuẩn Problem Details: status=400, title='$ERR_TITLE_400'"
else
    report_fail "TC-E2E-06 (Lỗi 400)" "Kỳ vọng HTTP 400 ProblemDetail, nhận http=$HTTP_CODE_400, payload=$PROBLEM_400"
fi

echo ""
echo -e "${CYAN}==============================================================================${NC}"
echo -e "${CYAN}KẾT QUẢ ĐÁNH GIÁ NGHIỆM THU E2E SMOKE TEST (TASK W1-14):                      ${NC}"
echo -e "Tổng số kịch bản kiểm thử: ${TOTAL_TESTS}"
echo -e "Số kịch bản ĐẠT (PASS)   : ${GREEN}${PASSED_TESTS}${NC}"
echo -e "Số kịch bản LỖI (FAIL)   : ${RED}${FAILED_TESTS}${NC}"
echo -e "${CYAN}==============================================================================${NC}"

if [ "$FAILED_TESTS" -eq 0 ]; then
    echo -e "${GREEN}>>> CHÚC MỪNG: HỆ THỐNG ĐÃ THÔNG MẠCH E2E ĐẦU CUỐI 100% SẴN SÀNG CHO W1-15! <<<${NC}"
    exit 0
else
    echo -e "${RED}>>> CẢNH BÁO: PHÁT HIỆN LỖI KIỂM THỬ E2E. CẦN KHẮC PHỤC TRƯỚC KHI BÀN GIAO! <<<${NC}"
    exit 1
fi
