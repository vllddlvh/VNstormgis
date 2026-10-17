# Tiến độ dự án

## Trạng thái hiện tại

- Commit mới nhất: Task W1-15 hoàn thành — Gắn tag release v0.1.0-alpha, hoàn tất Milestone 1 Tuần 1 (Quality Gate 1 Alpha PASS 100%)
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

## Đang thực hiện

- [ ] Chuyển giao sang Sprint 1 — Tuần 2: Xây dựng Ingestion Engine nạp dữ liệu khí tượng OWM & dữ liệu bão GDACS (Khởi đầu với Task W2-01: Flyway V4 DDL)

## Vấn đề đã biết

- Database PostGIS & Redis container cần duy trì trạng thái healthy (`docker compose -f deployment/docker-compose.yml up -d`) trước khi khởi chạy Backend local.

## Các bước tiếp theo

1. Khởi động Sprint 1 — Tuần 2: Hiện thực Task W2-01 (Flyway V4: DDL Bảng `weather_records`, `storms`, `storm_tracks`).
2. Kích hoạt Ingestion Worker OpenWeatherMap (15 phút/lần) và GDACS (30 phút/lần) theo kế hoạch bàn giao tại Task W1-15.
