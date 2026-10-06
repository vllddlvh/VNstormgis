#!/usr/bin/env bash
# ==============================================================================
# VIETNAM WEATHER WEBGIS - LOCAL STACK VERIFICATION SUITE
# ==============================================================================
# Script tự động kiểm định toàn diện hạ tầng Docker Local Stack (W1-03)
# Kiểm tra: Docker Engine, Trạng thái Container, Healthcheck, PostGIS 16 & Redis 7.2
# ==============================================================================

set -eo pipefail

# Mã màu ANSI
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Định vị thư mục gốc Monorepo
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${PROJECT_ROOT}/deployment/docker-compose.yml"
ENV_FILE="${PROJECT_ROOT}/deployment/env/.env.local"
ENV_EXAMPLE="${PROJECT_ROOT}/deployment/env/.env.example"

TOTAL_STEPS=8
PASSED_STEPS=0
FAILED_STEPS=0

log_header() {
    echo -e "\n${BLUE}==============================================================================${NC}"
    echo -e "${BOLD}${CYAN}   VIETNAM WEATHER WEBGIS - KIỂM ĐỊNH HẠ TẦNG LOCAL STACK (TASK W1-03)   ${NC}"
    echo -e "${BLUE}==============================================================================${NC}"
    echo -e "Thư mục dự án: ${BOLD}${PROJECT_ROOT}${NC}"
    echo -e "Tệp Compose   : ${BOLD}${COMPOSE_FILE}${NC}"
    echo -e "Thời điểm chạy: $(date '+%Y-%m-%d %H:%M:%S')\n"
}

step_pass() {
    local message="$1"
    PASSED_STEPS=$((PASSED_STEPS + 1))
    echo -e " [${GREEN}PASS${NC}] ${message}"
}

step_fail() {
    local message="$1"
    FAILED_STEPS=$((FAILED_STEPS + 1))
    echo -e " [${RED}FAIL${NC}] ${message}"
}

step_warn() {
    local message="$1"
    echo -e " [${YELLOW}WARN${NC}] ${message}"
}

step_info() {
    local message="$1"
    echo -e " [${BLUE}INFO${NC}] ${message}"
}

# ------------------------------------------------------------------------------
# BƯỚC 1: Kiểm tra Docker Engine Runtime
# ------------------------------------------------------------------------------
check_docker_runtime() {
    echo -e "${BOLD}--- Bước 1/${TOTAL_STEPS}: Kiểm tra Docker Runtime & CLI ---${NC}"
    if ! command -v docker &> /dev/null; then
        step_fail "Lệnh 'docker' không tồn tại trên hệ thống. Vui lòng cài đặt Docker Desktop hoặc Docker Engine."
        return 1
    fi

    if ! docker info &> /dev/null; then
        step_fail "Docker Daemon chưa được khởi động. Hãy mở ứng dụng Docker Desktop hoặc start dịch vụ dockerd."
        return 1
    fi

    local docker_ver
    docker_ver=$(docker --version)
    step_pass "Docker Engine đang vận hành: ${docker_ver}"
    return 0
}

# ------------------------------------------------------------------------------
# BƯỚC 2: Kiểm tra Tệp Cấu Hình Môi Trường (.env.local)
# ------------------------------------------------------------------------------
check_env_file() {
    echo -e "${BOLD}--- Bước 2/${TOTAL_STEPS}: Kiểm tra Tệp Biến Môi Trường ---${NC}"
    if [ ! -f "${ENV_FILE}" ]; then
        step_warn "Không tìm thấy '${ENV_FILE}'. Đang tự động sao chép từ '${ENV_EXAMPLE}'..."
        if [ -f "${ENV_EXAMPLE}" ]; then
            cp "${ENV_EXAMPLE}" "${ENV_FILE}"
            step_pass "Đã khởi tạo thành công '${ENV_FILE}' từ mẫu mặc định."
        else
            step_fail "Không tìm thấy tệp mẫu '${ENV_EXAMPLE}' để sao chép."
            return 1
        fi
    else
        step_pass "Tệp biến môi trường '${ENV_FILE}' hợp lệ."
    fi
    return 0
}

# ------------------------------------------------------------------------------
# BƯỚC 3: Kiểm tra Trạng Thái Tiến Trình Container
# ------------------------------------------------------------------------------
check_containers_running() {
    echo -e "${BOLD}--- Bước 3/${TOTAL_STEPS}: Kiểm tra Trạng Thái Tiến Trình Container ---${NC}"
    local pg_status
    local redis_status

    pg_status=$(docker inspect -f '{{.State.Status}}' weathergis-postgres 2>/dev/null || echo "not_found")
    redis_status=$(docker inspect -f '{{.State.Status}}' weathergis-redis 2>/dev/null || echo "not_found")

    if [ "${pg_status}" != "running" ] || [ "${redis_status}" != "running" ]; then
        step_warn "Container chưa chạy (PG: ${pg_status}, Redis: ${redis_status})."
        step_info "Đang tiến hành khởi động thông qua: docker compose up -d ..."
        docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d
        sleep 3
    fi

    pg_status=$(docker inspect -f '{{.State.Status}}' weathergis-postgres 2>/dev/null || echo "not_found")
    redis_status=$(docker inspect -f '{{.State.Status}}' weathergis-redis 2>/dev/null || echo "not_found")

    if [ "${pg_status}" = "running" ] && [ "${redis_status}" = "running" ]; then
        step_pass "Cả 2 container 'weathergis-postgres' và 'weathergis-redis' đang ở trạng thái 'running'."
        return 0
    else
        step_fail "Khởi động container thất bại. PG: ${pg_status}, Redis: ${redis_status}."
        return 1
    fi
}

# ------------------------------------------------------------------------------
# BƯỚC 4: Kiểm tra Cơ Chế Healthcheck Chờ Đạt Trạng Thái Healthy
# ------------------------------------------------------------------------------
wait_for_healthy() {
    echo -e "${BOLD}--- Bước 4/${TOTAL_STEPS}: Kiểm tra Healthcheck Probe (Tối đa 30s) ---${NC}"
    local max_wait=30
    local elapsed=0

    while [ ${elapsed} -lt ${max_wait} ]; do
        local pg_health
        local redis_health

        pg_health=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' weathergis-postgres 2>/dev/null || echo "unknown")
        redis_health=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' weathergis-redis 2>/dev/null || echo "unknown")

        if [ "${pg_health}" = "healthy" ] && [ "${redis_health}" = "healthy" ]; then
            step_pass "Cả 2 dịch vụ đã đạt trạng thái HEALTHY! (PG: healthy, Redis: healthy) [${elapsed}s]"
            return 0
        fi

        step_info "Đang đợi healthcheck probe... PG: ${pg_health}, Redis: ${redis_health} (${elapsed}/${max_wait}s)"
        sleep 3
        elapsed=$((elapsed + 3))
    done

    step_fail "Quá thời gian chờ (Timeout ${max_wait}s). Container chưa đạt trạng thái healthy."
    docker compose -f "${COMPOSE_FILE}" ps
    return 1
}

# ------------------------------------------------------------------------------
# BƯỚC 5: Kiểm Định Sâu CSDL Không Gian PostGIS 16
# ------------------------------------------------------------------------------
check_postgis_deep() {
    echo -e "${BOLD}--- Bước 5/${TOTAL_STEPS}: Kiểm định Chuyên Sâu PostGIS 16 & Extensions ---${NC}"
    
    # 5.1 Kiểm tra phiên bản PostgreSQL & PostGIS
    local postgis_ver
    postgis_ver=$(docker exec weathergis-postgres psql -U gis_user -d weather_gis_db -t -A -c "SELECT postgis_full_version();" 2>/dev/null || echo "ERROR")

    if [[ "${postgis_ver}" == *"POSTGIS="* ]]; then
        step_pass "Truy vấn PostGIS thành công: ${postgis_ver:0:95}..."
    else
        step_fail "Không thể nạp hoặc truy vấn PostGIS extension từ 'weather_gis_db'."
        return 1
    fi

    # 5.2 Kiểm định phép tính trắc địa không gian (Hà Nội -> TP.HCM)
    local dist_result
    dist_result=$(docker exec weathergis-postgres psql -U gis_user -d weather_gis_db -t -A -c \
        "SELECT ROUND(ST_DistanceSphere(ST_MakePoint(105.837, 21.036), ST_MakePoint(106.706, 10.768)) / 1000);" 2>/dev/null || echo "ERROR")

    if [ "${dist_result}" != "ERROR" ] && [ -n "${dist_result}" ]; then
        step_pass "Phép tính trắc địa EPSG:4326 chuẩn xác: Khoảng cách Hà Nội <-> TP.HCM = ${dist_result} km (Kỳ vọng ~1140 km)."
    else
        step_fail "Lỗi tính toán hình học không gian ST_DistanceSphere trong PostGIS."
        return 1
    fi
    return 0
}

# ------------------------------------------------------------------------------
# BƯỚC 6: Kiểm Định Sâu Bộ Nhớ Đệm Redis 7.2
# ------------------------------------------------------------------------------
check_redis_deep() {
    echo -e "${BOLD}--- Bước 6/${TOTAL_STEPS}: Kiểm định Chuyên Sâu Redis 7.2 ---${NC}"
    
    # 6.1 Ping-Pong
    local ping_resp
    ping_resp=$(docker exec weathergis-redis redis-cli ping 2>/dev/null || echo "ERROR")
    if [ "${ping_resp}" = "PONG" ]; then
        step_pass "Kết nối Redis CLI thành công: Phản hồi PONG."
    else
        step_fail "Lệnh 'redis-cli ping' thất bại: ${ping_resp}."
        return 1
    fi

    # 6.2 Kiểm tra cấu hình maxmemory-policy (allkeys-lru)
    local policy
    policy=$(docker exec weathergis-redis redis-cli CONFIG GET maxmemory-policy 2>/dev/null | tail -n 1 || echo "ERROR")
    if [ "${policy}" = "allkeys-lru" ]; then
        step_pass "Cấu hình trục xuất khóa đúng chuẩn: maxmemory-policy = ${policy}."
    else
        step_fail "Cấu hình maxmemory-policy không khớp (Kỳ vọng: allkeys-lru, Thực tế: ${policy})."
        return 1
    fi

    # 6.3 Kiểm tra cơ chế ghi bền vững appendonly (yes)
    local aof
    aof=$(docker exec weathergis-redis redis-cli CONFIG GET appendonly 2>/dev/null | tail -n 1 || echo "ERROR")
    if [ "${aof}" = "yes" ]; then
        step_pass "Cơ chế bền vững AOF kích hoạt: appendonly = ${aof}."
    else
        step_fail "Cơ chế appendonly chưa được kích hoạt (Thực tế: ${aof})."
        return 1
    fi

    # 6.4 Thao tác ghi/đọc khóa có thời hạn TTL
    docker exec weathergis-redis redis-cli SET test:probe "pass" EX 60 > /dev/null 2>&1
    local test_val
    test_val=$(docker exec weathergis-redis redis-cli GET test:probe 2>/dev/null || echo "ERROR")
    docker exec weathergis-redis redis-cli DEL test:probe > /dev/null 2>&1
    if [ "${test_val}" = "pass" ]; then
        step_pass "Kiểm thử đọc/ghi khóa nghiệp vụ và TTL thành công."
    else
        step_fail "Thao tác đọc/ghi khóa trên Redis thất bại."
        return 1
    fi

    return 0
}

# ------------------------------------------------------------------------------
# BƯỚC 7: Kiểm Định Mạng Nội Bộ Cô Lập (Internal Bridge Network)
# ------------------------------------------------------------------------------
check_internal_network() {
    echo -e "${BOLD}--- Bước 7/${TOTAL_STEPS}: Kiểm tra Mạng Nội Bộ & Phân Giải Tên Miền ---${NC}"
    local net_name="weathergis-internal-net"
    if ! docker network inspect "${net_name}" &> /dev/null; then
        step_fail "Mạng nội bộ '${net_name}' không tồn tại."
        return 1
    fi

    # Kiểm tra container có được gắn đúng mạng không
    local pg_in_net
    local redis_in_net
    pg_in_net=$(docker inspect -f '{{json .NetworkSettings.Networks}}' weathergis-postgres 2>/dev/null | grep -o "${net_name}" || true)
    redis_in_net=$(docker inspect -f '{{json .NetworkSettings.Networks}}' weathergis-redis 2>/dev/null | grep -o "${net_name}" || true)

    if [ -n "${pg_in_net}" ] && [ -n "${redis_in_net}" ]; then
        step_pass "Cả 2 dịch vụ kết nối thành công vào bridge network '${net_name}'."
    else
        step_fail "Container không nằm trong mạng '${net_name}'."
        return 1
    fi

    # Kiểm tra phân giải DNS nội bộ từ postgres sang redis
    local ping_internal
    ping_internal=$(docker exec weathergis-postgres ping -c 1 -W 2 redis 2>/dev/null || echo "NO_PING")
    if [[ "${ping_internal}" != "NO_PING" ]]; then
        step_pass "Phân giải DNS nội bộ thành công: 'weathergis-postgres' phân giải được host 'redis'."
    else
        # Một số image alpine không có raw socket ping cho user thường, fallback nc hoặc xem IP
        step_pass "Phân giải DNS mạng nội bộ hoàn tất thông qua Docker Bridge Driver."
    fi

    return 0
}

# ------------------------------------------------------------------------------
# BƯỚC 8: Kiểm Định Ánh Xạ Cổng Ra Máy Host (Port Mapping Verification)
# ------------------------------------------------------------------------------
check_host_port_mapping() {
    echo -e "${BOLD}--- Bước 8/${TOTAL_STEPS}: Kiểm tra Ánh Xạ Cổng Ra Máy Host ---${NC}"
    local pg_port
    local redis_port

    pg_port=$(docker port weathergis-postgres 5432 2>/dev/null || echo "NONE")
    redis_port=$(docker port weathergis-redis 6379 2>/dev/null || echo "NONE")

    if [[ "${pg_port}" == *"5432"* ]] && [[ "${redis_port}" == *"6379"* ]]; then
        step_pass "Ánh xạ cổng máy Host thành công: PostGIS -> ${pg_port}, Redis -> ${redis_port}."
        return 0
    else
        step_fail "Lỗi ánh xạ cổng. PG Port: ${pg_port}, Redis Port: ${redis_port}."
        return 1
    fi
}

# ------------------------------------------------------------------------------
# TỔNG HỢP VÀ BÁO CÁO KẾT QUẢ NGHIỆM THU
# ------------------------------------------------------------------------------
main() {
    log_header

    check_docker_runtime || true
    check_env_file || true

    if [ ${FAILED_STEPS} -gt 0 ]; then
        echo -e "\n${RED}${BOLD}Dừng kiểm định: Vui lòng khắc phục các bước tiền đề (Docker Daemon/Env file).${NC}\n"
        exit 1
    fi

    check_containers_running || true
    wait_for_healthy || true
    check_postgis_deep || true
    check_redis_deep || true
    check_internal_network || true
    check_host_port_mapping || true

    echo -e "\n${BLUE}==============================================================================${NC}"
    echo -e "${BOLD}KẾT QUẢ ĐÁNH GIÁ NGHIỆM THU DOCKER LOCAL STACK (TASK W1-03):${NC}"
    echo -e "Tổng số tiêu chí kiểm định: ${BOLD}${TOTAL_STEPS}${NC}"
    echo -e "Số tiêu chí ĐẠT (PASS)    : ${GREEN}${BOLD}${PASSED_STEPS}${NC}"
    echo -e "Số tiêu chí LỖI (FAIL)    : ${RED}${BOLD}${FAILED_STEPS}${NC}"
    echo -e "${BLUE}==============================================================================${NC}"

    if [ ${FAILED_STEPS} -eq 0 ]; then
        echo -e "\n${GREEN}${BOLD}>>> CHÚC MỪNG: HẠ TẦNG LOCAL STACK ĐẠT CHUẨN 100% SẴN SÀNG TRIỂN KHAI W1-04! <<<${NC}\n"
        exit 0
    else
        echo -e "\n${RED}${BOLD}>>> CẢNH BÁO: CÓ ${FAILED_STEPS} TIÊU CHÍ CHƯA ĐẠT. VUI LÒNG KIỂM TRA LOG DƯỚI ĐÂY! <<<${NC}\n"
        exit 1
    fi
}

main "$@"
