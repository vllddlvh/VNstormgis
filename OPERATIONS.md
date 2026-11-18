# SỔ TAY VẬN HÀNH & TRIỂN KHAI PRODUCTION — VNSTORMGIS (`v1.0.0`)

Tài liệu này hướng dẫn chi tiết quy trình triển khai Production, cấu hình dịch vụ Email thông báo cảnh báo (SMTP thật & Mailpit local), quản trị bộ ngưỡng thiên tai, sao lưu/phục hồi thảm họa PostGIS (Disaster Recovery) và quy trình Rollback 3 cấp độ cho hệ thống **VNstormgis**.

---

## 1. Kiến trúc Triển khai Production

Hệ thống vận hành theo mô hình **Modular Monolith Containerized Stack** gồm 4 thành phần chính trên mạng nội bộ cô lập `weathergis-prod-net`:

1. **`weathergis-web-prod` (Nginx 1.26 Alpine — Cổng `:80` / `:443`):** Phục vụ React 18 SPA tĩnh đã chia 4 gói chunk nén Gzip, gắn bộ 5 HTTP Security Headers chuẩn OWASP (`CSP`, `nosniff`, `DENY`, `Referrer-Policy`, `Permissions-Policy: geolocation=(self)`) và chuyển tiếp `/api/*` sang Backend.
2. **`weathergis-backend-prod` (Spring Boot 3.3.4 / JRE 21 LTS Virtual Threads — Non-root `uid=100`):** Thực thi toàn bộ REST API, phân tích không gian PostGIS, AI Grounding Gemini (`SYS-RULE-004`), giới hạn tần suất Bucket4j và gửi thông báo Email cảnh báo bất đồng bộ (`AFTER_COMMIT`).
3. **`weathergis-postgres-prod` (PostgreSQL 16 + PostGIS 3.4):** Quản lý 12 bảng dữ liệu không gian & nghiệp vụ qua Flyway `V1`–`V10` (không mở cổng ra Internet).
4. **`weathergis-redis-prod` (Redis 7.2 Alpine — `256mb` `volatile-lru`):** Bộ đệm GeoJSON 3 vùng TTL và kho lưu trạng thái Token Bucket Rate Limiter.

---

## 2. Quy trình Triển khai Production Lần Đầu

### Bước 1: Khởi tạo tệp cấu hình bí mật `.env.prod`

```bash
cp VNstormgis-Deployment/env/.env.prod.example VNstormgis-Deployment/env/.env.prod
chmod 600 VNstormgis-Deployment/env/.env.prod
```

Cập nhật các khóa bắt buộc trong `VNstormgis-Deployment/env/.env.prod`:
- `DB_PASSWORD`: mật khẩu mạnh cho PostgreSQL.
- `INTERNAL_SYNC_SECRET` & `ADMIN_API_KEY`: sinh bằng `openssl rand -hex 32`.
- `OPENWEATHERMAP_API_KEY` & `GEMINI_API_KEY`: khóa API thực tế.
- `PUBLIC_WEB_URL` & `PUBLIC_API_URL`: tên miền HTTPS chính thức (ví dụ `https://weathergis.vn`).

### Bước 2: Cấu hình Dịch vụ Gửi Email Cảnh báo (`MAIL_*`)

- **Phương án Production (Gmail SMTP / Google Workspace / AWS SES / Resend):**
  ```dotenv
  MAIL_ENABLED=true
  MAIL_HOST=smtp.gmail.com
  MAIL_PORT=587
  MAIL_USERNAME=canhbao.vnstormgis@gmail.com
  MAIL_PASSWORD=<16-ky-tu-app-password>
  MAIL_SMTP_AUTH=true
  MAIL_SMTP_STARTTLS=true
  MAIL_FROM=no-reply@weathergis.vn
  ```
- **Phương án Demo / Kiểm thử Local (Mailpit tự động trong `docker-compose.yml`):**
  - Không cần tài khoản Gmail. Khi chạy `docker compose up -d`, container `weathergis-mailpit` lắng nghe SMTP tại `localhost:1025` và mở giao diện Hộp thư đến trực quan tại **`http://localhost:8025`** để nhận ngay mã OTP 6 chữ số, liên kết xác thực 1-click và email cảnh báo bão lũ.

### Bước 3: Khởi chạy Stack Production & Kiểm tra Sức khỏe

```bash
docker compose --env-file VNstormgis-Deployment/env/.env.prod \
  -f VNstormgis-Deployment/docker-compose.prod.yml up -d --build

CHECK_NGINX=1 DB_CONTAINER=weathergis-postgres-prod REDIS_CONTAINER=weathergis-redis-prod \
  BACKEND_URL=http://127.0.0.1/ NGX_URL=http://127.0.0.1 \
  bash VNstormgis-Deployment/scripts/health-check.sh
```

---

## 3. Quản trị Bộ Ngưỡng Cảnh báo Thiên tai (`ROLE-ADMIN`)

Các thao tác thêm/sửa/bật-tắt ngưỡng cảnh báo yêu cầu header `X-Admin-Api-Key: <ADMIN_API_KEY>`:

```bash
# Xem danh sách ngưỡng hiện hành (Public)
curl -s http://localhost:8080/api/v1/alert-thresholds | jq .

# Tạo ngưỡng cảnh báo mới
curl -s -X POST http://localhost:8080/api/v1/alert-thresholds \
  -H "Content-Type: application/json" \
  -H "X-Admin-Api-Key: ${ADMIN_API_KEY}" \
  -d '{
    "name": "Mưa đặc biệt lớn miền Trung",
    "parameter": "rain_1h",
    "operator": ">=",
    "thresholdValue": 80.0,
    "severityLevel": "CRITICAL",
    "description": "Cảnh báo ngập lụt đô thị và lũ quét khi mưa 1 giờ đạt từ 80mm trở lên"
  }' | jq .

# Bật / Tắt nhanh một ngưỡng theo ID
curl -s -X PATCH http://localhost:8080/api/v1/alert-thresholds/1/toggle \
  -H "X-Admin-Api-Key: ${ADMIN_API_KEY}" | jq .
```

---

## 4. Sao lưu Tự động & Diễn tập Phục hồi Thảm họa (Backup & DR)

### 4.1. Sao lưu nén chuẩn PostGIS (`RPO <= 24h`)

```bash
bash VNstormgis-Deployment/scripts/backup-db.sh
```
- Sinh tệp `.dump` định dạng nén tùy chỉnh (`pg_dump -Fc --no-owner --no-acl`) kèm chữ ký `.sha256` tại `artifacts/backups/` (hoặc thư mục chỉ định qua `BACKUP_DIR`).
- Có thể đặt lịch `crontab` chạy lúc `02:00 UTC` hằng ngày và đồng bộ lên AWS S3.

### 4.2. Phục hồi CSDL & Diễn tập DR (`RTO < 5 phút`)

```bash
# Diễn tập phục hồi an toàn sang CSDL kiểm thử độc lập (không chạm CSDL đang chạy)
bash VNstormgis-Deployment/scripts/restore-db.sh artifacts/backups/<file>.dump weather_gis_dr_drill

# Phục hồi trực tiếp vào CSDL chính khi xảy ra sự cố
bash VNstormgis-Deployment/scripts/restore-db.sh artifacts/backups/<file>.dump weather_gis_db
```

---

## 5. Quy trình Rollback Khẩn cấp 3 Cấp độ

1. **Cấp độ 1 — Rollback Ứng dụng (Container Image Rollback `< 60 giây`):**
   Cập nhật `BACKEND_IMAGE` / `FRONTEND_IMAGE` trong `.env.prod` về thẻ `:sha-<previous>` hoặc `:v<previous>` ổn định trước đó, sau đó chạy:
   ```bash
   docker compose --env-file VNstormgis-Deployment/env/.env.prod \
     -f VNstormgis-Deployment/docker-compose.prod.yml up -d backend web
   ```
2. **Cấp độ 2 — Tắt khẩn cấp tính năng phụ trợ (Feature Degradation không cần redeploy):**
   - Tắt AI Gemini (chuyển 100% sang mẫu tất định an toàn): đặt `GEMINI_ENABLED=false` rồi `docker compose ... up -d backend`.
   - Tắt gửi SMTP ngoài: đặt `MAIL_ENABLED=false` (vẫn ghi nhận đăng ký vào Outbox log).
   - Bỏ qua bộ đệm Redis khi bảo trì: đặt `REDIS_CACHE_ENABLED=false`.
3. **Cấp độ 3 — Khôi phục Dữ liệu Không gian (Database Point-in-Time / Dump Restore `< 5 phút`):**
   Dừng tạm thời container `backend`, chạy `restore-db.sh` với bản `.dump` gần nhất đã kiểm tra SHA-256, sau đó khởi động lại `backend` và xác nhận bằng `health-check.sh`.

---

## 6. Cổng Kiểm định Tổng thể Trước Phát hành (`verify-week7-golive.sh`)

Chạy một lệnh duy nhất từ thư mục gốc để kiểm định toàn diện mã nguồn, độ phủ test, bundle frontend, bảo mật OWASP, cấu hình Production và diễn tập DR:

```bash
bash .github/scripts/verify-week7-golive.sh
```
