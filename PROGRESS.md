# Tiến độ dự án

## Trạng thái hiện tại

- Commit mới nhất: 770cc25 (docs(design): integrate frontend template standards and design tokens)
- Trạng thái test: 10/10 JUnit tests vượt qua (100% pass), Frontend type-check & build pass, Docker 8/8 healthy
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

## Đang thực hiện

- [ ] Task W1-11: Tích hợp Leaflet 1.9 & Xử lý Leaflet Icon Marker Bug trong Vite bundler (Sẵn sàng triển khai)

## Vấn đề đã biết

- Leaflet marker icon mặc định bị mất hình ảnh (lỗi 404/rỗng) trong môi trường đóng gói Vite do đường dẫn tương đối bị hash (RSK-W1-03) — Cần khắc phục bằng `leafletConfig.ts`.
- Database PostGIS & Redis container cần duy trì trạng thái healthy (`docker compose -f deployment/docker-compose.yml up -d`) trước khi khởi chạy Backend local.

## Các bước tiếp theo

1. Thực hiện Task W1-11: Tạo module `frontend/src/components/map/leafletConfig.ts` giải quyết lỗi Leaflet Icon Bug và chuẩn bị marker assets.
2. Thực hiện Task W1-12: Xây dựng Component `BaseMap.tsx` định vị trung tâm Việt Nam ($16.0^\circ N, 108.0^\circ E$, zoom 6) và hoàn thiện Dashboard Layout bám sát Design Template San Rita (`Frontend - template/`).
3. Thực hiện Task W1-13: Viết Integration Test với Testcontainers PostGIS cho `LocationRepository`.
4. Thực hiện Task W1-14: Kết nối Smoke Test E2E (render 68 trạm quan trắc từ API lên bản đồ Leaflet).
5. Thực hiện Task W1-15: Rà soát tiêu chí chấp nhận DoD và nghiệm thu Cổng Alpha Tuần 1 (Quality Gate 1 Alpha).
