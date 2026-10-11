# AGENTS.md

## Khi bắt đầu phiên (vào ca)

1. Đọc PROGRESS.md để nắm trạng thái hiện tại
2. Đọc DECISIONS.md để nắm các quyết định quan trọng
3. Chạy make check để xác nhận repo đang ở trạng thái nhất quán
4. Tiếp tục từ mục "Các bước tiếp theo" trong PROGRESS.md

## Trước khi kết thúc phiên (tan ca)

1. Cập nhật PROGRESS.md
2. Chạy make check để xác nhận trạng thái nhất quán
3. Commit tất cả phần việc đã hoàn thành

## Quy tắc làm việc

- Làm một tính năng tại một thời điểm
- Chỉ bắt đầu tính năng tiếp theo sau khi tính năng hiện tại pass xác minh end-to-end
- Đừng "tiện thể tái cấu trúc" tính năng B trong khi triển khai tính năng A

## Tổng quan dự án

Hệ thống WebGIS giám sát thời tiết, lượng mưa, gió bão thời gian thực tại Việt Nam tích hợp Grounded AI Warning Agent (`VNstormgis`).

- **Kiến trúc:** Modular Monolith gói gọn trong Spring Boot 3.3 (Java 21 LTS, Virtual Threads, Hibernate Spatial 6).
- **Frontend:** React 18.3, Vite 5, TypeScript 5, Tailwind CSS 3.4, Leaflet 1.9 (`/frontend`).
- **Cơ sở dữ liệu:** PostgreSQL 16 + PostGIS 3.4 (WGS84 EPSG:4326, 10 bảng không gian), Flyway Migration.
- **Bộ nhớ đệm:** Redis 7.2 (GeoJSON Map Cache, Rate Limiting, Chat Session).
- **AI Warning Engine:** Google Gemini Flash API tích hợp cơ chế Factual Grounding chống ảo giác.

## Khởi động nhanh

- **Hạ tầng Docker (PostGIS 16 + Redis 7.2):**
  ```bash
  docker compose -f deployment/docker-compose.yml up -d
  npm run docker:verify
  ```
- **Backend Spring Boot (:8080):**
  ```bash
  cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
  ```
- **Frontend React Vite (:5173, Reverse Proxy -> :8080):**
  ```bash
  cd frontend && npm run dev
  ```
- **Kiểm thử tự động:**
  - Backend (JUnit 5 + Testcontainers PostGIS): `cd backend && ./gradlew test`
  - Frontend (Type-check & ESLint): `cd frontend && npm run type-check && npm run lint`
- **Xác minh đầy đủ (Quality Gate):**
  ```bash
  npm run lint:root && npm run backend:format && npm run frontend:lint && cd backend && ./gradlew spotlessCheck test && cd frontend && npm run build
  ```

## Ràng buộc cứng

1. **Quy ước thứ tự tọa độ địa lý (WGS84 / EPSG:4326):**
   - PostGIS SQL, JTS (Java) & GeoJSON (RFC 7946): Bắt buộc `[Kinh độ (X/Lng), Vĩ độ (Y/Lat)]` (Dải Việt Nam: Lng $102^\circ - 114^\circ E$, Lat $8^\circ - 24^\circ N$).
   - Leaflet Map (Frontend): Bắt buộc `[Vĩ độ (Lat), Kinh độ (Lng)]` (Ví dụ: `map.setView([16.047, 108.206])`).
   - Mọi cột `geometry` bắt buộc đánh chỉ mục GiST (`CREATE INDEX ... USING GIST (geom)`).
2. **Cơ chế Grounding chống ảo giác AI (SYS-RULE-004):**
   - Mọi prompt gửi Gemini API bắt buộc bọc khối số liệu trích xuất thực tế `[FACTUAL_SYSTEM_DATA]` từ PostGIS/trạm quan trắc; nghiêm cấm tự suy diễn hoặc bịa đặt số liệu.
   - Bắt buộc kiểm tra đối soát đầu ra (Output Verification); kích hoạt Fallback Template xác định nếu Gemini lỗi hoặc số liệu không khớp.
3. **Chuẩn hóa lỗi RFC 7807 (Problem Details):**
   - Mọi phản hồi lỗi API từ Backend phải thông qua `GlobalExceptionHandler` trả về cấu trúc `ProblemDetail` thống nhất (`status`, `title`, `detail`, `instance`, `code`, `timestamp`).
4. **Chuẩn định dạng mã nguồn & Frontend Design System:**
   - Backend: Google Java Format (2 spaces), kiểm soát tự động bằng Spotless (`./gradlew spotlessApply`).
   - Frontend: Prettier + ESLint (`--max-warnings 0`), strict TypeScript không cho phép biến/tham số thừa.
   - **Quy chuẩn Giao diện & Tokens:** Toàn bộ giao diện bắt buộc tuân thủ và phát triển bám sát bộ mẫu thiết kế trong `docs/The plans of project/Frontend - template/` (`DESIGN.md`, `tokens.json`, `theme.css`, `variables.css`): phong cách _alpine field guide_, bảng màu tương phản (Highlighter Mint, Sage Stone, Carbon Ink, Topo Gray), typography utilitarian mono + display, border radius `0px` phẳng góc cạnh không đổ bóng.
   - Commit Message: Tuân thủ Conventional Commits `<type>(<scope>): <desc> [W1-xx]` (< 120 ký tự), bắt buộc pass Husky hook.
5. **Bảo mật & Quyền riêng tư (Zero PII):**
   - Tuyệt đối không commit file `.env*`, bí mật hoặc API keys; chỉ đọc từ biến môi trường.
   - Người dùng công cộng truy cập ẩn danh qua `session_token`; endpoint quản trị bảo vệ bằng `X-Admin-Api-Key`.

## Tài liệu chủ đề

- **Kế hoạch & Tiến độ Sprint** (`PROGRESS.md`, `docs/week1/README.md`) — Tra cứu tiến độ, ma trận RACI và tiêu chí nghiệm thu từng Task.
- **Mẫu thiết kế Frontend & Design Tokens** (`docs/The plans of project/Frontend - template/`) — Đọc bắt buộc khi tạo/sửa giao diện, CSS và components UI (`DESIGN.md`, `tokens.json`, `theme.css`, `variables.css`).
- **Lược đồ CSDL 10 bảng PostGIS** (`docs/The plans of project/1. ERD.md`) — Đọc bắt buộc khi tạo Entity, DDL hoặc viết migration Flyway.
- **Đặc tả RESTful API & GeoJSON RFC 7946** (`docs/The plans of project/2. API docs.md`) — Đọc bắt buộc khi thêm hoặc sửa đổi API endpoint.
- **Yêu cầu nghiệp vụ & Ngưỡng rủi ro thiên tai** (`docs/The plans of project/3. BRD.md`) — Bắt buộc khi tính toán cảnh báo thời tiết (mưa, bão, gió giật).
- **Đặc tả yêu cầu phần mềm SRS** (`docs/The plans of project/4. SRS.md`) — Tham khảo ma trận ca kiểm thử và yêu cầu chức năng/phi chức năng.
- **Kiến trúc hệ thống Modular Monolith** (`docs/The plans of project/5. System Architecture.md`) — Tham khảo cấu trúc phân gói, luồng dữ liệu thời gian thực và Ingestion pipeline.
- **Quyết định công nghệ** (`docs/The plans of project/6. Technical Stack.md`) — Tham khảo phiên bản thư viện (Hibernate Spatial 6, JTS, Leaflet, Gemini).
- **Hạ tầng triển khai & Docker** (`docs/The plans of project/7. Deployment Infrastructure.md`) — Tham khảo cấu hình môi trường, Nginx và Docker multi-stage.
