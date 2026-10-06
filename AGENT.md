# AGENT OPERATIONAL HARNESS & CONTEXT SPECIFICATION

## Vietnam Weather & Storm Monitoring WebGIS with Grounded AI Warning Agent (`VNstormgis`)

---

## 1. Định Danh Hệ Thống & Sứ Mệnh (System Identity & Mission)

### 1.1. Mục Tiêu Dự Án

**VNstormgis** là nền tảng WebGIS giám sát thời tiết, lượng mưa, gió bão và thiên tai tại Việt Nam theo thời gian thực, tích hợp **Grounded AI Warning Agent** nhằm tự động phát hiện nguy cơ và hỗ trợ hỏi-đáp ngôn ngữ tự nhiên.

Hệ thống hoạt động với hai chế độ AI Agent cốt lõi:

- **Chủ động (Proactive Agent):** Định kỳ giám sát số liệu trạm quan trắc và bão, tự động so khớp ngưỡng nguy hiểm, tổng hợp dữ liệu thực tế (Factual Grounding) và kích hoạt Gemini API sinh bản tin cảnh báo tự nhiên bằng tiếng Việt.
- **Bị động (Passive / Interactive Agent):** Tiếp nhận câu hỏi tự nhiên từ người dùng qua Web Chatbox (ví dụ: _"khu vực nào đang mưa lớn nhất?"_, _"tại sao có cảnh báo bão ở Đà Nẵng?"_), truy vấn số liệu không gian trong PostGIS và sinh câu trả lời chính xác, giải thích ngữ cảnh thực tế.

### 1.2. Ranh Giới Hệ Thống (Scope Boundaries)

- **In-Scope:**
  - Bản đồ số WebGIS tương tác đa lớp (trạm mưa, vector gió, tâm bão, quỹ đạo bão, đa giác cảnh báo).
  - Tích hợp dữ liệu thời tiết thời gian thực từ **OpenWeatherMap API**.
  - Tích hợp dữ liệu bão nhiệt đới đang hoạt động từ **GDACS API**.
  - Bộ quy tắc ngưỡng cảnh báo rủi ro thiên tai (mưa lớn 1h/3h, gió giật, bão tiếp cận).
  - Trợ lý AI hội thoại (Google Gemini API) tuân thủ cơ chế **Factual Grounding** chống ảo giác.
  - Giao diện người dùng Web Responsive trên một màn hình (SPA Dashboard).
- **Out-of-Scope:**
  - Tuyệt đối không tự huấn luyện mô hình Machine Learning dự báo quỹ đạo bão.
  - Tuyệt đối không hỗ trợ gửi tin cảnh báo qua SMS, Email hoặc Mobile Push Notification (Web-only).
  - Không thu thập thông tin định danh cá nhân (Zero PII - người dùng truy cập ẩn danh).

---

## 2. Mô Hình Tư Duy Kiến Trúc (Architecture Mental Model)

### 2.1. Phong Cách Kiến Trúc: Modular Monolith

Hệ thống tuân thủ triệt để nguyên tắc **"Simplicity First"** và **"Avoid Premature Scaling"**:

- Toàn bộ backend gói gọn trong **một Spring Boot 3.3 Application** duy nhất, không dùng Microservices, Service Mesh hay Kafka/RabbitMQ.
- Tác vụ định kỳ (Ingestion và Quét ngưỡng) được điều phối thông qua **Spring In-Process Task Scheduler (`@Scheduled`)** với **Virtual Threads (Java 21)**.
- Dữ liệu địa lý và quan trắc tập trung tại **PostgreSQL 16 + PostGIS 3.4**.
- Bộ nhớ đệm phân tán tập trung **Redis 7.2** chịu trách nhiệm lưu tạm GeoJSON bản đồ, quản lý phiên chat và Token Bucket Rate Limiting.

```
                              ┌─────────────────────────────────────────┐
                              │            REACT 18 WEBGIS              │
                              │     (Leaflet 1.9 + Tailwind CSS)        │
                              └────────────────────┬────────────────────┘
                                                   │ HTTPS / RESTful API (RFC 7946 GeoJSON)
                                                   ▼
                              ┌─────────────────────────────────────────┐
                              │       SPRING BOOT 3.3 BACKEND CORE      │
                              │ ┌─────────────────────────────────────┐ │
                              │ │ Modular Monolith Domains:           │ │
                              │ │ • location   • weather   • storm    │ │
                              │ │ • alert      • chat      • sync     │ │
                              │ └─────────────────────────────────────┘ │
                              │ │ In-Process Schedulers (@Scheduled)  │ │
                              │ └──────────────────┬──────────────────┘ │
                              └────────┬───────────┴───────────┬────────┘
                                       │                       │
           ┌───────────────────────────┼───────────────────────┼───────────────────────────┐
           ▼                           ▼                       ▼                           ▼
┌────────────────────┐    ┌────────────────────┐    ┌────────────────────┐    ┌────────────────────┐
│   POSTGRESQL 16    │    │     REDIS 7.2      │    │  OPENWEATHERMAP &  │    │   GOOGLE GEMINI    │
│    + POSTGIS 3.4   │    │  (GeoJSON Cache,   │    │     GDACS APIS     │    │     FLASH API      │
│  (Spatial DB WGS84)│    │ Rate Limit, Chat)  │    │  (Weather & Storm) │    │(Grounded AI Engine)│
└────────────────────┘    └────────────────────┘    └────────────────────┘    └────────────────────┘
```

### 2.2. Bố Trí Cấu Trúc Monorepo (Repository Layout)

```text
VNstormgis/
├── .github/workflows/         # CI/CD pipelines (backend-ci.yml, frontend-ci.yml)
├── .husky/                    # Git Hooks tự động (pre-commit, commit-msg)
├── backend/                   # Backend API Service (Spring Boot 3.3.x, Java 21 LTS)
│   ├── src/main/java/vn/weathergis/
│   │   ├── common/            # Configs, Security, RFC 7807 Exception handling
│   │   ├── location/          # Domain Trạm quan trắc & Địa lý (63 trạm WGS84)
│   │   ├── weather/           # Domain Bản ghi thời tiết & Ingestion OWM
│   │   ├── storm/             # Domain Bão, Tâm bão, Quỹ đạo & Ingestion GDACS
│   │   ├── alert/             # Domain Ngưỡng rủi ro & Engine so khớp cảnh báo
│   │   ├── chat/              # Domain AI Chatbot & Grounding Prompting
│   │   └── automation/        # In-process Scheduled Tasks & Sync Logs
│   ├── src/main/resources/db/migration/ # Flyway Migration Scripts (V1, V2, V3...)
│   └── src/test/              # JUnit 5, Mockito & Testcontainers PostGIS
├── frontend/                  # WebGIS Client (React 18, Vite, TypeScript 5, Leaflet)
│   ├── src/components/        # BaseMap, LayerControl, AlertPanel, ChatDrawer
│   ├── src/services/          # API Client (Axios) kết nối /api/v1
│   └── src/types/             # GeoJSON DTO & Domain Type Definitions
├── deployment/                # Cấu hình hạ tầng môi trường
│   ├── docker-compose.yml     # PostGIS 16 + Redis 7.2 Local Dev Stack
│   ├── env/                   # .env.example, .env.local
│   ├── docker/                # Multi-stage Dockerfiles (Backend, Frontend)
│   └── nginx/                 # Nginx Reverse Proxy & Static Asset Server
├── docs/                      # Nguồn chân lý (SRS, BRD, ERD, System Arch, Week 1 Plans)
├── AGENT.md                   # Tài liệu Harness điều phối Agent (File này)
├── commitlint.config.js       # Quy chuẩn Conventional Commits v1.0.0
├── .lintstagedrc.json         # Tự động hóa linter trước khi commit
└── package.json               # Quản trị Monorepo root
```

### 2.3. Lược Đồ Dữ Liệu Địa Không Gian (10 Bảng PostGIS)

1. `locations`: Điểm trạm quan trắc 63 tỉnh thành (`geom Point EPSG:4326`, `boundary MultiPolygon`, `is_active`).
2. `weather_records`: Dữ liệu đo đạc thời tiết theo giờ (`location_id`, `recorded_at`, `temperature`, `rain_1h`, `rain_3h`, `wind_speed`, `wind_gust`, `raw_data`).
3. `storms`: Thông tin bão nhiệt đới đang hoạt động từ GDACS (`id`, `name`, `category`, `max_wind_speed`, `status`, `geom_current`).
4. `storm_tracks`: Quỹ đạo lịch sử và dự báo của bão (`storm_id`, `track_type`, `forecast_time`, `geom Point`, `impact_radius_km`, `impact_polygon`).
5. `alert_thresholds`: Cấu hình bộ tham số cảnh báo (`parameter`, `operator`, `threshold_value`, `severity_level`, `is_enabled`).
6. `alerts`: Cảnh báo rủi ro được sinh ra (`location_id`, `storm_id`, `title`, `severity_level`, `trigger_reason`, `ai_explanation`, `affected_geom`, `status`).
7. `chat_sessions`: Quản lý phiên truy cập ẩn danh (`session_token`, `title`).
8. `chat_messages`: Lịch sử hỏi đáp AI (`session_id`, `sender_type`, `content`, `referenced_alert_id`, `spatial_filter`, `tokens_used`).
9. `data_sync_logs`: Nhật ký đồng bộ dữ liệu ngoại vi (`source`, `status`, `records_synced`, `error_message`).
10. `flyway_schema_history`: Bảng theo dõi phiên bản migration cơ sở dữ liệu.

---

## 3. Các Nguyên Tắc Bất Biến & Rào Chắn An Toàn (Guardrails & Invariants)

Khi đọc hoặc sinh mã nguồn trong repository này, AI Agent **BẮT BUỘC** tuân thủ tuyệt đối 5 rào chắn kỹ thuật sau:

### ⚠️ Rào Chắn 1: Quy Ước Thứ Tự Tọa Độ Địa Lý (Spatial Coordinate Invariant)

Sự nhầm lẫn giữa Kinh độ (Longitude/X) và Vĩ độ (Latitude/Y) sẽ phá hỏng toàn bộ hệ thống bản đồ:

- **Trong PostGIS SQL & JTS Topology Suite (Java):**
  - Hàm `ST_MakePoint(X, Y)` nhận **`X = Kinh độ (Longitude)`**, **`Y = Vĩ độ (Latitude)`**.
  - Khởi tạo JTS: `geometryFactory.createPoint(new Coordinate(longitude, latitude))`.
  - Tọa độ Việt Nam chuẩn: Longitude nằm trong dải $\approx 102.0^\circ - 114.0^\circ E$; Latitude nằm trong dải $\approx 8.0^\circ - 24.0^\circ N$.
- **Trong Chuẩn GeoJSON (RFC 7946) & REST API Backend:**
  - Thứ tự mảng tọa độ bắt buộc: **`[Kinh độ (Longitude), Vĩ độ (Latitude)]`** (Ví dụ Hà Nội: `[105.8542, 21.0285]`).
- **Trong Thư Viện Bản Đồ Leaflet (Frontend):**
  - Hàm `L.latLng(lat, lng)` hoặc mảng Leaflet nhận **`[Vĩ độ (Latitude), Kinh độ (Longitude)]`**.
  - Khi nạp dữ liệu từ GeoJSON sang Leaflet, hàm `L.geoJSON()` tự động xử lý hoán vị; nhưng khi gọi thủ công `map.setView([lat, lng])` hoặc tạo marker thủ công, phải truyền đúng **Vĩ độ trước, Kinh độ sau**.
- **Hệ Quy Chiếu Tọa Độ (SRID):** Toàn hệ thống sử dụng duy nhất **EPSG:4326 (WGS84)**. Mọi cột `geometry` bắt buộc đánh chỉ mục không gian `USING GIST`.

### ⚠️ Rào Chắn 2: Cơ Chế Grounding Chống Ảo Giác AI (Anti-Hallucination Guardrail)

Quy tắc nghiệp vụ cốt lõi `SYS-RULE-004` / `BR-RULE-004`:

- **Nguyên tắc vàng:** AI Agent chỉ được phép diễn giải số liệu có thực trong hệ thống; nghiêm cấm tự suy diễn, phỏng đoán hoặc bịa đặt chỉ số thời tiết.
- **Cấu trúc Prompt:** Mọi prompt gửi sang Gemini API (cả chủ động và bị động) bắt buộc chứa khối ngữ cảnh dữ liệu thực:
  ```text
  [FACTUAL_SYSTEM_DATA]
  {
    "location": "Hà Nội",
    "rain_1h_mm": 65.5,
    "wind_speed_kmh": 72.0,
    "threshold_violated": "Mưa rất to (>= 50mm/1h)"
  }
  [/FACTUAL_SYSTEM_DATA]
  Chỉ thị: Giải thích tình hình dựa DUY NHẤT vào số liệu trên. Tuyệt đối không thay đổi con số.
  ```
- **Bộ Lọc Đối Soát Đầu Ra (Output Verification Filter):**
  - Trích xuất toàn bộ con số trong văn bản do Gemini sinh ra và so sánh đối chiếu với bối cảnh đầu vào.
  - Nếu phát hiện số liệu bịa đặt hoặc gọi Gemini thất bại: Kích hoạt cơ chế Fallback sử dụng bản mẫu định dạng sẵn (Deterministic Template), ghi nhận log cảnh báo và không làm gián đoạn hệ thống.

### ⚠️ Rào Chắn 3: An Toàn & Bảo Mật Xác Thực (Security & Privacy Invariant)

- **Zero PII & Ẩn danh:** Người dùng công cộng (`ROLE-PUBLIC`) không cần tài khoản, hệ thống định danh qua `session_token` tự sinh.
- **Endpoint Bảo vệ:**
  - Quản trị cấu hình ngưỡng: Header `X-Admin-Api-Key: <admin_key>` hoặc Bearer Token.
  - Worker đồng bộ nội bộ: Header `X-Internal-Secret: <worker_secret>`.
- **Bảo Vệ Bí Mật (No Secrets in Git):** Tuyệt đối không commit tệp `.env`, `.env.local` hoặc hardcode API keys (Gemini, OpenWeatherMap, mật khẩu CSDL) vào kho mã nguồn. Luôn đọc từ biến môi trường.

### ⚠️ Rào Chắn 4: Xử Lý Lỗi Chuẩn RFC 7807 (Problem Details)

Mọi phản hồi lỗi từ Backend API phải tuân thủ định dạng Problem Details RFC 7807 thống nhất (`status`, `title`, `detail`, `instance`, `code`, `timestamp`), đảm bảo Frontend bắt lỗi dễ dàng và rõ nghĩa.

### ⚠️ Rào Chắn 5: Chuẩn Định Dạng Mã Nguồn (Code Style Invariant)

- **Backend (Java):** Google Java Format (2-space indent), được cưỡng chế tự động thông qua **Spotless Gradle Plugin**.
- **Frontend (TS/React):** Prettier (2-space indent, single quote, trailing comma) và ESLint.
- **Quy cách Git Commit:** Conventional Commits v1.0.0 kèm mã Task ID (ví dụ: `feat(location): add spatial gist index [W1-05]`).

---

## 4. Bảng Lệnh Vận Hành Thực Thi (Deterministic Operational Harness)

Dưới đây là các lệnh thao tác chuẩn mà Agent hoặc Developer cần sử dụng:

### 4.1. Lệnh Quản Trị Cấp Root Monorepo

| Lệnh Thực Thi             | Mục Đích                                                          |
| :------------------------ | :---------------------------------------------------------------- |
| `npm run lint:root`       | Kiểm tra định dạng toàn bộ JSON, YAML, Markdown                   |
| `npm run format:root`     | Tự động format toàn bộ JSON, YAML, Markdown bằng Prettier         |
| `npm run backend:format`  | Tự động format mã nguồn Java bằng Google Java Format qua Spotless |
| `npm run frontend:lint`   | Chạy ESLint kiểm tra lỗi mã nguồn React TypeScript                |
| `npm run frontend:format` | Tự động format mã nguồn Frontend bằng Prettier                    |

### 4.2. Lệnh Quản Trị Hạ Tầng (Docker Compose)

| Lệnh Thực Thi                                                | Mục Đích                                          |
| :----------------------------------------------------------- | :------------------------------------------------ |
| `docker compose -f deployment/docker-compose.yml up -d`      | Khởi động PostGIS 16 và Redis 7.2 ngầm            |
| `docker compose -f deployment/docker-compose.yml ps`         | Kiểm tra trạng thái healthcheck của các container |
| `docker compose -f deployment/docker-compose.yml logs -f db` | Xem trực tiếp log khởi động PostGIS               |
| `docker compose -f deployment/docker-compose.yml down`       | Dừng các container (vẫn giữ dữ liệu trong volume) |
| `docker compose -f deployment/docker-compose.yml down -v`    | Xóa sạch container kèm toàn bộ volume dữ liệu     |

### 4.3. Lệnh Phát Triển Backend (Spring Boot / Gradle)

| Lệnh Thực Thi                           | Mục Đích                                              |
| :-------------------------------------- | :---------------------------------------------------- |
| `cd backend && ./gradlew bootRun`       | Chạy ứng dụng Backend cục bộ (Cổng `:8080`)           |
| `cd backend && ./gradlew test`          | Chạy toàn bộ Unit & Integration Test (Testcontainers) |
| `cd backend && ./gradlew spotlessCheck` | Kiểm tra vi phạm định dạng mã nguồn Java              |
| `cd backend && ./gradlew spotlessApply` | Tự động sửa định dạng toàn bộ file Java               |
| `cd backend && ./gradlew build -x test` | Build artifact JAR không chạy test                    |

### 4.4. Lệnh Phát Triển Frontend (React / Vite)

| Lệnh Thực Thi                  | Mục Đích                                       |
| :----------------------------- | :--------------------------------------------- |
| `cd frontend && npm install`   | Cài đặt các gói phụ thuộc Frontend             |
| `cd frontend && npm run dev`   | Khởi động máy chủ dev Vite (Cổng `:5173`)      |
| `cd frontend && npm run build` | Biên dịch TypeScript và đóng gói bản phát hành |
| `cd frontend && npm run lint`  | Chạy kiểm tra tĩnh ESLint                      |

---

## 5. Quy Trình Làm Việc & Kiểm Thử Của Agent (Agent Workflow & Quality Gates)

Mỗi khi AI Agent nhận nhiệm vụ lập trình hoặc chỉnh sửa mã nguồn, quy trình bắt buộc phải là:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ 1. ĐỌC TÀI LIỆU │ ──> │  2. TẠO NHÁNH   │ ──> │ 3. THỰC THI MÃ │ ──> │ 4. CHẠY QUALITY │ ──> 5. COMMIT CHUẨN
│  SRS / ERD / W1 │     │  FEATURE TASK   │     │  VÀ TEST TỰ ĐỘNG│     │  GATES LOCAL    │
└─────────────────┘     └─────────────────┘     └─────────────────┘     └─────────────────┘
```

### Bước 1: Tra Cứu Tài Liệu Nguồn Căn Cứ

- Không đoán mò kiến trúc. Tra cứu tệp tài liệu tương ứng tại `docs/The plans of project/` hoặc `docs/week1/`.
- Xác định rõ mã nghiệp vụ (`SR-xxx`), bảng CSDL liên quan trong `1. ERD.md`, và giao ước API trong `2. API docs.md`.

### Bước 2: Tạo Nhánh Tính Năng (Git Branching)

- Nhánh làm việc phân rẽ từ `staging`:
  ```bash
  git checkout staging
  git pull origin staging
  git checkout -b feature/<mã-task>-<tên-ngắn-gọn>
  ```

### Bước 3: Triển Khai Mã Nguồn & Viết Kiểm Thử

- Backend: Tạo entity JTS, repository kế thừa Spring Data JPA, service interface và controller. Viết test với MockMvc hoặc Testcontainers PostGIS.
- Frontend: Tạo component chuẩn TypeScript, định nghĩa rõ ràng kiểu dữ liệu GeoJSON trong `types/`, xử lý state rõ ràng.

### Bước 4: Kiểm Soát Chất Lượng Cục Bộ (Shift-Left Quality Gate)

Trước khi bàn giao kết quả hoặc hoàn tất phản hồi, Agent phải đảm bảo:

1. Java code đã chạy format: `npm run backend:format` (hoặc `cd backend && ./gradlew spotlessApply`).
2. Frontend code không có lỗi lint: `npm run frontend:lint`.
3. Kiểm thử tự động chạy qua: `cd backend && ./gradlew test`.
4. Không có file rác, file nhạy cảm `.env*` bị sửa đổi hoặc đưa vào git.

### Bước 5: Cam Kết Mã Nguồn (Conventional Commit)

Tuân thủ cú pháp Husky / Commitlint:

```text
<type>(<scope>): <mô tả ngắn bằng tiếng Việt hoặc Anh> [<Mã-Task>]
```

- Các kiểu hợp lệ: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `ci`.
- _Ví dụ:_ `feat(location): implement Spatial JTS Entity and GeoJSON DTO [W1-08]`

---

## 6. Trạng Thái Hiện Tại & Lộ Trình Phát Triển (Roadmap & Status)

### 6.1. Tiến Độ Hiện Tại (Sprint 1 - Tuần 1: Foundation & Baseline)

- [x] **W1-01:** Khởi tạo Monorepo, cấu hình `.editorconfig`, `.gitignore`, Husky pre-commit, Commitlint, Prettier.
- [x] **W1-02:** Thiết lập Docker Compose Local Dev Stack (`deployment/docker-compose.yml`, PostGIS 16 + Redis 7.2).
- [ ] **W1-03:** Khởi động & Kiểm định Docker Local Stack trên máy phát triển.
- [ ] **W1-04:** Flyway V1: Khởi tạo phần mở rộng PostGIS & UUID (`V1__init_spatial_extensions.sql`).
- [ ] **W1-05:** Flyway V2: DDL bảng `locations` & Chỉ mục không gian GiST (`V2__create_location_tables.sql`).
- [ ] **W1-06:** Flyway V3: Seed 63 Tỉnh Thành & Trạm Khí Tượng WGS84 (`V3__seed_vietnam_locations.sql`).
- [ ] **W1-07:** Khởi tạo Spring Boot 3.3 Gradle, Java 21 LTS, Hibernate Spatial 6, Virtual Threads.
- [ ] **W1-08:** Hiện thực Domain `location` & Khung xử lý lỗi toàn cục RFC 7807 Problem Details.
- [ ] **W1-09:** Cấu hình `application-dev.yml` & Tích hợp SpringDoc OpenAPI (`/swagger-ui.html`).
- [ ] **W1-10:** Khởi tạo React 18, Vite, TypeScript 5, Tailwind CSS, cấu hình proxy `:8080`.
- [ ] **W1-11:** Tích hợp Leaflet 1.9 & Xử lý Leaflet Icon Marker Bug trong Vite bundler.
- [ ] **W1-12:** Dựng Component `BaseMap` Lãnh thổ Việt Nam ($16^\circ N, 108^\circ E$, zoom 6) & App Layout.
- [ ] **W1-13:** Viết Integration Test với Testcontainers PostGIS & WireMock.
- [ ] **W1-14:** Kết nối Smoke Test E2E (Hiển thị 63 trạm quan trắc từ API lên Leaflet).
- [ ] **W1-15:** Nghiệm thu Cổng Alpha Tuần 1 (Quality Gate 1 Alpha).

### 6.2. Lộ Trình Tổng Thể 8 Tuần (Master Roadmap)

- **Sprint 1 (Tuần 1 - 2): Foundation & Ingestion Pipeline** (Hạ tầng, PostGIS, Ingestion OpenWeatherMap & GDACS, đồng bộ tự động).
- **Sprint 2 (Tuần 3 - 4): Spatial Analysis & Visualization** (Bản đồ số Leaflet đa lớp, vector gió, trực quan hóa bão & quỹ đạo, API thống kê).
- **Sprint 3 (Tuần 5 - 6): Grounded AI Warning Engine** (Bộ quy tắc ngưỡng, In-process Scheduler so khớp, Gemini API Factual Grounding, Chatbox).
- **Sprint 4 (Tuần 7 - 8): Hardening, Performance & Production** (Redis Caching, Rate Limiting Bucket4j, Docker multi-stage, Nginx, CI/CD).

---

## 7. Mục Lục Tra Cứu Tài Liệu Nguồn (Documentation Sitemap)

Khi cần đào sâu bất kỳ khía cạnh kỹ thuật nào, AI Agent hãy mở trực tiếp các tệp tài liệu tương ứng:

| Khía Cạnh Cần Tra Cứu                | Đường Dẫn Tệp Tài Liệu                                                                                                                                                                                                               | Nội Dung Chính                                                     |
| :----------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------- |
| **Tổng quan phạm vi & Nghiệp vụ**    | [0. Introduction.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/0.%20Introduction.md)<br>[3. BRD.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/3.%20BRD.md) | In-scope/Out-of-scope, 8 quy tắc nghiệp vụ rủi ro thiên tai        |
| **Mô hình CSDL & Flyway DDL**        | [1. ERD.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/1.%20ERD.md)                                                                                                                              | Chi tiết 10 bảng, kiểu dữ liệu không gian, GiST index, Flyway DDL  |
| **Đặc tả RESTful API & GeoJSON**     | [2. API docs.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/2.%20API%20docs.md)                                                                                                                  | Endpoints, Schema Request/Response, RFC 7946, mã lỗi HTTP          |
| **Đặc tả Yêu cầu Kỹ thuật Phần mềm** | [4. SRS.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/4.%20SRS.md)                                                                                                                              | Yêu cầu chức năng (`SR-xxx`), phi chức năng (`NFR-xxx`), Use cases |
| **Bản thiết kế Kiến trúc Hệ thống**  | [5. System Architecture.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/5.%20System%20Architecture.md)                                                                                            | C4 Model, Modular Monolith layout, luồng dữ liệu thời gian thực    |
| **Quyết định Công nghệ Kỹ thuật**    | [6. Technical Stack.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/6.%20Technical%20Stack.md)                                                                                                    | Java 21, Spring Boot 3.3, Leaflet, Gemini Flash, Bucket4j          |
| **Hạ tầng Triển khai & DevOps**      | [7. Deployment Infrastructure.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/7.%20Deployment%20Infrastructure.md)                                                                                | Docker Compose, Nginx, CI/CD GitHub Actions, cấu hình môi trường   |
| **Kế hoạch Chi Tiết Tuần 1**         | [week1/README.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/week1/README.md)                                                                                                                                               | Toàn bộ 15 task chi tiết, ma trận phân công, rủi ro kỹ thuật       |
| **Đặc tả Task W1-01**                | [week1/Task W1-01.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/week1/Task%20W1-01.md)                                                                                                                                     | Chi tiết thiết lập Monorepo, Husky và Git hooks                    |
| **Đặc tả Task W1-02**                | [week1/Task W1-02.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/week1/Task%20W1-02.md)                                                                                                                                     | Chi tiết Docker Compose Local Stack PostGIS & Redis                |

---

_Tài liệu này là Agent Harness chính thức của dự án. Mọi phiên làm việc tiếp theo của AI Agent đều lấy tài liệu này làm kim chỉ nam thực thi._
