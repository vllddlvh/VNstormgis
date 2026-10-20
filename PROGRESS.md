# Tiến độ dự án

## Trạng thái hiện tại

- Commit mới nhất: Task W1-15 hoàn thành — Gắn tag release v0.1.0-alpha, hoàn tất Milestone 1 Tuần 1 (Quality Gate 1 Alpha PASS 100%)
- Đang thực hiện: Task W2-01 (Flyway V4 & V5 + 4 JPA Entities) — code hoàn thành, đang chờ review/commit
- Trạng thái test: Frontend type-check, lint, build (100% pass), Backend spotless & JUnit 5 Testcontainers PostGIS + WireMock (100% pass), E2E Smoke Test 12/12 TC-E2E pass 100%
- Lint: pass (npm run lint:root, npm run frontend:lint, backend spotlessCheck đều pass)

## Đã hoàn thành

- [x] W1-01: Tổ chức Monorepo, Git Workflow & Pre-commit Hooks (Husky, Commitlint, Prettier)
- [x] W1-02: Thiết lập Docker Compose Local Dev Stack (PostgreSQL 16 + PostGIS 3.4, Redis 7.2)
- [x] W1-03: Khởi động & Kiểm định tự động Docker Local Stack (`verify-local-stack.sh`)
- [x] W1-04: Flyway V1 - Kích hoạt phần mở rộng PostGIS và UUID (`V1__init_spatial_extensions.sql`)
- [x] W1-05: Flyway V2 - DDL bảng `locations` & Chỉ mục không gian GiST (`V2__create_location_tables.sql`)
- [x] W1-06: Flyway V3 - Seed 68 trạm quan trắc/tỉnh thành WGS84 Việt Nam (`V3__seed_vietnam_locations.sql`)
- [x] W1-07: Khởi tạo Backend Spring Boot 3.3, Java 21 LTS, Hibernate Spatial 6, Virtual Threads
- [x] W1-08: Hiện thực Bounded Context `location` & Khung xử lý lỗi RFC 7807 Problem Details
- [x] W1-09: Cấu hình `application-dev.yml`, Flyway Auto-migration & SpringDoc OpenAPI (`/swagger-ui.html`)
- [x] W1-10: Khởi tạo Frontend React 18, Vite 5, TypeScript 5, Tailwind CSS 3.4 & Reverse Proxy sang Backend (:8080)
- [x] Tích hợp tiêu chuẩn Frontend Design System & Tokens từ `Frontend - template/` vào toàn bộ tài liệu thiết kế
- [x] W1-11: Tích hợp Leaflet 1.9, Khắc phục triệt để lỗi Marker Icon 404 trong Vite Bundler (RSK-W1-03), Xây dựng bộ tiện ích trắc địa WGS84 (`geoUtils.ts`), Factory Weather Markers phân cấp thiên tai (`markerFactory.ts`) & Component kiểm định `MarkerVerificationMap.tsx`

- [x] W1-12: Xây dựng Bản đồ số cốt lõi `BaseMap.tsx` định vị toàn vẹn lãnh thổ Việt Nam (Tâm 16.047°N, 108.206°E, Zoom 6, ràng buộc MaxBounds) & Hoàn thiện Khung Bố Cục Dashboard Hợp Nhất (Unified Dashboard Layout) chuẩn San Rita Alpine Field Guide
- [x] W1-13: Thiết lập môi trường kiểm thử tích hợp tự động PostGIS với Testcontainers & Khung giả lập dịch vụ ngoài WireMock Sandbox (`LocationRepositoryIT`, `WireMockSandboxTest`, `AbstractPostGisIntegrationTest`)
- [x] W1-14: Thiết lập & Kiểm định luồng kết nối đầu cuối (End-to-End Smoke Test): Truy vấn 68 trạm quan trắc từ CSDL PostGIS qua Spring Boot API đến hiển thị tương tác trên bản đồ Leaflet `BaseMap.tsx` chuẩn thiết kế San Rita (12/12 TC-E2E PASS, script `verify-e2e-smoke.sh`)
- [x] W1-15: Họp Review & Demo Tuần 1, Kiểm toán Definition of Done (DoD) & Nghiệm Thu Chính Thức Cổng Chất Lượng Alpha (Quality Gate 1 Alpha Sign-off — Biên bản nghiệm thu được PO, PM & Tech Lead phê duyệt, gắn tag release `v0.1.0-alpha`, sẵn sàng kích hoạt Tuần 2)

## Đang thực hiện (Sprint 1 Fast-Track — Tuần 2: Ingestion Engine & Leaflet Weather Layers)

> [!IMPORTANT]
> **Kế Hoạch Bù Tiến Độ (Schedule Recovery):** Do dự án bị trễ 1 tuần từ giai đoạn trước, Sprint 1 Tuần 2 mới được thiết lập theo cơ chế Fast-Track gộp toàn bộ phạm vi Tuần 2 cũ (Ingestion Engine) và Tuần 3 cũ (REST API GeoJSON & Leaflet Rainfall/Wind Layers) thành 11 tasks (33 SP / 68h). Kế hoạch chi tiết từng ngày xem tại [`docs/week2/README.md`](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/week2/README.md).

- [x] W2-01 (V4): Flyway V4 - DDL Bảng `weather_records`, `storms`, `storm_tracks` & Chỉ mục GiST (`V4__create_weather_and_storm_tables.sql`)
- [x] W2-01 (V5): Flyway V5 - DDL Bảng `alert_thresholds`, `alerts`, `chat_*`, `sync_logs` (`V5__create_alert_chat_and_log_tables.sql`)
- [x] W2-02: Nâng cấp `LayerControlDrawer.tsx` - Toggle Lớp Mưa (`rainfall` / SR-MAP-002), Telemetry Loading Bar, Huy hiệu Last Sync, ARIA switch 6 lớp & Tái định vị panel góc trên bên phải theo SRS
- [ ] W2-03: Xây dựng OpenWeatherMap Client & Scheduled Ingestion Worker nạp 68 trạm khí tượng (15 phút/lần)
- [ ] W2-04: Xây dựng GDACS Ingestion Client & GeoJSON Parser nạp dữ liệu bão Tây Bắc TBD (30 phút/lần)
- [ ] W2-05: Hiện thực Quy Tắc Nghiệp Vụ `SYS-RULE-001` (Active Storm Classification)
- [ ] W2-06: Tích hợp Redis Cache Layer Tối Ưu Tốc Độ Truy Vấn Thời Tiết (TTL 10 phút)
- [ ] W2-07: Mở Rộng REST API: Endpoints Truy Vấn Thời Tiết GeoJSON Chuẩn RFC 7946 (`/api/v1/weather/current`, `/history`)
- [ ] W2-08: Xây dựng Lớp Bản Đồ Mưa & Vận Tốc Gió Trên Leaflet WebGIS (Color scales, Legend, Popup chi tiết)
- [ ] W2-09: Bộ Kiểm Thử Tự Động Ingestion & API Dựa Trên WireMock Sandbox (Fault injection, Rate limit 429)
- [ ] W2-10: Kịch Bản Kiểm Định Tự Động Toàn Trình (`verify-week2-fasttrack.sh`)
- [ ] W2-11: Họp Demo Cuối Tuần 2 & Nghiệm Thu Cổng Milestone 1 Kép (Milestone 1 + WebGIS Weather Layers)

## Vấn đề đã biết & Rủi ro

- Database PostGIS & Redis container cần duy trì trạng thái healthy (`docker compose -f deployment/docker-compose.yml up -d`) trước khi khởi chạy Backend local.
- Cần cung cấp API key OpenWeatherMap hợp lệ trong `deployment/env/.env.local` (`OWM_API_KEY`) cho Ingestion Worker chạy thực tế.
- Tải công việc Tuần 2 tăng lên 33 SP (68h) đòi hỏi Backend và Frontend triển khai song song dựa trên Mock Contract.

## Các bước tiếp theo

1. **Khởi động Day 6 (Thứ Hai):** GIS/DB triển khai W2-01 (Flyway V4) và W2-02 (Flyway V5); Backend bắt đầu W2-03 (OWM Client).
2. **Triển khai song song Frontend Day 7-8:** Frontend phát triển W2-08 (Lớp bản đồ mưa & gió Leaflet) song song với REST API Backend W2-07.
3. **Nghiệm thu Day 10 (Thứ Sáu):** Chạy `verify-week2-fasttrack.sh` (W2-10) và họp nghiệm thu kép Milestone 1 (W2-11).
