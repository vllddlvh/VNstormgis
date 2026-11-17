# W6-07 Runbook — System Integration Testing (SIT), 500-CCU Load Gate & Security Audit

Tài liệu vận hành cho cổng kiểm định tự động Tuần 6 (`.github/scripts/verify-week6-pipeline.sh`) và workflow GitHub Actions chạy định kỳ hàng đêm (`.github/workflows/week6-sit-nightly.yml`).

---

## 1. Cấu trúc 6 Cổng Kiểm định (`G-01` → `G-06`)

| Gate | Tên cổng | Nội dung kiểm chứng | Điều kiện PASS |
| :--- | :--- | :--- | :--- |
| **G-01** | Preflight & Security Headers | Kiểm tra công cụ (`java` 21, `node`, `npm`, `docker`, `jq`, `curl`), chờ `/actuator/health` trả `UP`, và kiểm tra trực tiếp 5 OWASP Security Headers (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, `Content-Security-Policy`) trên `/api/v1/locations`. | HTTP 200 `.status=="UP"` và đủ 5 security headers |
| **G-02** | Focused Week 6 SIT & Security IT | Thực thi `SystemIntegrationAndEdgeCaseIT`, `SecurityHeadersAndOwaspIT`, `WeatherSyncServiceTest`, `StormSyncServiceTest` (`--rerun-tasks`). | 34 tests PASS, 0 fail/error/skip |
| **G-03** | Full Quality Gate, Coverage & Bundle Size | Chạy `./gradlew spotlessCheck test bootJar jacocoTestReport --no-daemon` (876 tests, line coverage toàn backend `>= 80%`) và `npm run type-check && npm run lint && npm run build`. | Exit 0, coverage `>= 80%`, `VNstormgis-Frontend/dist` `<= 1536 KB` (`< 1.5MB`) với `>= 4` JS chunks |
| **G-04** | Load & Stress Testing `500 CCU` | Chạy `.github/scripts/run-load-test-500ccu.mjs` với `500 CCU` (4.000 request bản đồ/cảnh báo + 40 phiên chat × 12 tin nhắn). | `overallResult=="PASS"`, `weather_current.p95Ms <= 100`, `mapAggregate.avgMs < 50`, `total5xx == 0` |
| **G-05** | Security Audit & Multi-Stage Packaging | Chạy `.github/scripts/security-audit-week6.sh` quét toàn bộ lịch sử Git, `npm audit --omit=dev`, kiểm tra BOM Backend và cấu hình Dockerfile/Nginx. | `result=="PASS"`, `leaked_secrets == 0`, `critical == 0`, `high == 0` |
| **G-06** | UI Contract & Browser E2E Regression | Kiểm tĩnh hợp đồng UI và chạy Playwright E2E (`test-w5-06-ui.mjs` + `test-w4-06-ui.mjs`) khi `PLAYWRIGHT_MODULE` được cung cấp. | Static check PASS + Browser E2E PASS (khi có Playwright) |

---

## 2. Lệnh Chạy Cục bộ

```bash
# Chạy đầy đủ từ thư mục gốc repository:
BACKEND_BASE_URL=http://127.0.0.1:8080 \
ARTIFACT_DIR=/tmp/w6-07-verify \
bash scripts/verify-week6-pipeline.sh

# Diễn tập lỗi có kiểm soát tại G-02:
W6_FORCE_FAILURE_GATE=G02 \
BACKEND_BASE_URL=http://127.0.0.1:8080 \
ARTIFACT_DIR=/tmp/w6-07-drill \
bash scripts/verify-week6-pipeline.sh
```

---

## 3. Lịch Chạy Nightly & Xử lý Sự cố

- **Lịch chạy:** `03:00 UTC` (`10:00` sáng giờ Việt Nam, lệch 20 phút sau W5 nightly `02:40 UTC` và 40 phút sau W4 nightly `02:20 UTC`).
- **Lưu trữ bằng chứng:** Artifact `w6-07-<run_id>` lưu giữ `30 ngày` (`summary.json`, `verify.log`, `load-test-summary.json`, `security-audit-summary.json`, JUnit XML và báo cáo JaCoCo).
- **Cảnh báo:** Khi job thất bại, workflow gửi thông báo JSON tới secret `W6_FAILURE_WEBHOOK_URL` (yêu cầu HTTPS).
