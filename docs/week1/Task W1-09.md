# ĐẶC TẢ KỸ THUẬT VÀ HƯỚNG DẪN THỰC THI CHI TIẾT

## TASK W1-09: CẤU HÌNH PROFILE APPLICATION-DEV.YML, TỰ ĐỘNG HÓA FLYWAY MIGRATION & TÍCH HỢP SPRINGDOC OPENAPI 3 (SWAGGER UI)

---

## Thông Tin Nhiệm Vụ (Task Metadata)

| Thuộc Tính (Attribute)         | Giá Trị Chi Tiết (Specification)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| :----------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Mã Nhiệm Vụ (Task ID)**      | **W1-09**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **Tên Nhiệm Vụ (Task Name)**   | Thiết Lập Profile Cấu Hình `application-dev.yml`, Kích Hoạt Tự Động Hóa Flyway Migration Trên Local PostGIS, Tích Hợp SpringDoc OpenAPI 3.0 (Swagger UI `/swagger-ui.html`), Khai Báo OpenAPI Metadata Cho Bounded Context `location`, Cấu Hình Logging Cấu Trúc SQL & Tinh Chỉnh HikariCP Connection Pool                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| **Epic / Giai Đoạn**           | Sprint 1 (Tuần 1 — Tuần 2) — **Giai đoạn: Foundation & Baseline Infrastructure**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| **Lịch Trình Thực Hiện**       | **Ngày 3 (Thứ Tư)** — Khung giờ cuối chiều: 16:30 — 18:30 (hoặc đầu Ngày 4; Thời lượng: 4 giờ công kỹ sư tập trung; ước tính hoàn thiện kiểm định: 4 — 6 giờ công kỹ sư)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| **Điểm Nỗ Lực (Story Points)** | **2 SP** (Standard Integration & Documentation Effort)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| **Mức Độ Ưu Tiên (Priority)**  | **P0 — Blocker Môi Trường Chạy Thực Tế & Hợp Đồng Giao Tiếp API** (Bắt buộc hoàn tất 100% để: 1. Cung cấp Swagger UI làm tài liệu "Single Source of Truth" cho Frontend React Leaflet triển khai Tasks W1-10..W1-14; 2. Tự động hóa việc nạp lược đồ và dữ liệu trắc địa vào CSDL dev; 3. Làm tiền đề cho Integration Test Testcontainers tại Task W1-13)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **Người Chịu Trách Nhiệm (R)** | **Backend Engineer**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **Người Hỗ Trợ Kỹ Thuật (C)**  | Technical Lead, DevOps Engineer, GIS / Database Specialist, Frontend Engineer                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| **Người Phê Duyệt (A)**        | **Project Manager / Technical Lead / Software Architect**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **Bên Được Thông Báo (I)**     | Toàn thể đội ngũ phát triển dự án (Frontend, QA / Automation Tester, Product Owner)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Tài Liệu Căn Cứ Tham Chiếu** | 1. [2. API docs.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/2.%20API%20docs.md)<br>2. [5. System Architecture.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/5.%20System%20Architecture.md#L214)<br>3. [6. Technical Stack.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/6.%20Technical%20Stack.md#L380)<br>4. [7. Deployment Infrastructure.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/7.%20Deployment%20Infrastructure.md#L502)<br>5. [Task W1-07.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/week1/Task%20W1-07.md)<br>6. [Task W1-08.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/week1/Task%20W1-08.md)<br>7. [PROGRESS.md](file:///Users/dllv/Documents/GitHub/VNstormgis/PROGRESS.md) |

---

## 1. Bối Cảnh & Mục Tiêu Kỹ Thuật (Context & Objectives)

### 1.1. Bối Cảnh Nghiệp Vụ & Nhu Cầu Tích Hợp Runtime

Hệ thống **WebGIS Giám Sát Mưa Gió Bão Tích Hợp AI Agent Cảnh Báo (`VNstormgis`)** đã trải qua hai mốc phát triển cốt lõi của tầng Backend trong Sprint 1:

1. **Khởi tạo Framework Spring Boot 3.3 & Spatial Persistence (Task W1-07):**
   - Đã biên dịch thành công với toolchain Java 21 LTS, Gradle 8.8.
   - Đã kích hoạt Virtual Threads (Project Loom) cho máy chủ Tomcat.
   - Đã tích hợp Hibernate Spatial 6.5.x, JTS Core 1.19.0 và Jackson JTS Module.
   - _Lưu ý thiết kế:_ Ở tệp cấu hình mặc định `application.yml`, cơ chế tự động nạp Flyway đã được cố ý thiết lập `spring.flyway.enabled: false` để các bài unit test cơ bản không bị ràng buộc vào CSDL PostgreSQL bên ngoài.
2. **Hiện thực hóa Domain `location` & Global Exception RFC 7807 (Task W1-08):**
   - Đã ánh xạ thành công bảng `locations` sang `LocationEntity` với trường hình học `Point (EPSG:4326)`.
   - Đã xây dựng `LocationRepository` với native query không gian `ST_DistanceSphere` tính khoảng cách thực tế.
   - Đã hoàn thiện `LocationController` cung cấp chế độ phản hồi kép: JSON phong bao chuẩn (`ApiResponse<List<LocationResponse>>`) và GeoJSON FeatureCollection chuẩn RFC 7946 cho Leaflet.
   - Đã hoàn thiện `GlobalExceptionHandler` trả về chuẩn công nghiệp `ProblemDetail` (RFC 7807).

Tuy nhiên, **ứng dụng vẫn chưa thể vận hành trọn vẹn ở môi trường phát triển (Local Runtime)** do còn thiếu các mắt xích sống còn:

- **Chưa có cấu hình kết nối CSDL thực tế cho môi trường dev:** Khi khởi động ứng dụng độc lập, Spring Boot chưa biết cách kết nối tới container PostGIS `weather_gis_db` tại cổng `localhost:5432`.
- **Flyway chưa tự động chạy kiểm tra và đồng bộ lược đồ:** Nếu một kỹ sư mới kéo mã nguồn về và bật backend, CSDL chưa được tự động kiểm tra và áp dụng 3 tệp migration `V1`, `V2`, `V3`.
- **Thiếu tài liệu hóa API tương tác trực quan (Swagger UI / OpenAPI 3.0):** Các kỹ sư Frontend (sắp bắt đầu Tasks W1-10..W1-14) và QA Engineer không có công cụ tra cứu schema, thử nghiệm trực tiếp API trên trình duyệt web, dễ dẫn đến sai lệch hợp đồng giao tiếp (API contract drift).
- **Thiếu cấu hình Logging có cấu trúc:** Khi cần gỡ lỗi (debug) truy vấn SQL hay kiểm tra tham số bind, console hiện tại chưa hiển thị định dạng SQL rõ ràng.
- **Thiếu giám sát kiểm tra trạng thái sức khỏe CSDL và Flyway:** Chưa mở các endpoint Actuator cần thiết (`/actuator/flyway`, `/actuator/health`) để kiểm tra trạng thái sống còn của ứng dụng.

**Task W1-09** chính là **bước hoàn thiện cuối cùng để đưa Backend Spring Boot 3.3 vào trạng thái hoạt động thực tế (Production-Ready Dev Runtime)**, biến mã nguồn trừu tượng thành một dịch vụ có thể chạy, tương tác, kiểm thử trực quan và bàn giao hợp đồng API chuẩn xác 100% cho đội ngũ Frontend.

---

### 1.2. Mục Tiêu Kỹ Thuật Định Lượng (Quantitative Technical Objectives)

Task W1-09 đặt ra 7 mục tiêu kỹ thuật định lượng bắt buộc phải đạt được:

1. **Thiết lập hoàn chỉnh profile cấu hình `application-dev.yml`:**
   - Kích hoạt kích hoạt profile bằng `spring.config.activate.on-profile: dev`.
   - Thiết lập DataSource kết nối an toàn tới `jdbc:postgresql://localhost:5432/weather_gis_db` với tài khoản `weather_gis_user` và cơ chế fallback biến môi trường (`${DB_HOST:localhost}`).
   - Cấu hình Connection Pool HikariCP với kích thước hợp lý cho môi trường máy trạm cá nhân: `maximum-pool-size: 10`, `minimum-idle: 5`, `connection-timeout: 20000ms`.
2. **Kích hoạt tự động hóa vòng đời Flyway Migration:**
   - Bật `spring.flyway.enabled: true` trong profile dev.
   - Thiết lập `baseline-on-migrate: true`, `validate-on-migrate: true`, quét đúng thư mục `classpath:db/migration`.
   - Bảo đảm khi ứng dụng khởi động, toàn bộ 3 phiên bản migration (`V1__init_spatial_extensions.sql`, `V2__create_location_tables.sql`, `V3__seed_vietnam_locations.sql`) được kiểm tra checksum và áp dụng trơn tru.
3. **Hiện thực hóa Bean cấu hình toàn cục `OpenApiConfig.java`:**
   - Khởi tạo bean `OpenAPI` chứa siêu dữ liệu chuẩn mực của hệ thống: Tên dự án, phiên bản (`v1.0.0`), mô tả kiến trúc, thông tin liên hệ, giấy phép bản quyền.
   - Đăng ký danh sách Server URL: Cục bộ (`http://localhost:8080`) và Staging.
   - Cấu hình phân nhóm API (`GroupedOpenApi`): Nhóm toàn bộ (`v1-all`), nhóm Bounded Context (`v1-locations`), và nhóm hệ thống (`system-actuator`).
   - Xử lý tương thích schema kiểu dữ liệu hình học JTS (`org.locationtech.jts.geom.Geometry`, `Point`), ngăn chặn hiện tượng đệ quy vô hạn hoặc vỡ giao diện Swagger UI.
4. **Làm giàu siêu dữ liệu OpenAPI cho REST Controller & DTOs:**
   - Bổ sung annotations `@Tag`, `@Operation`, `@ApiResponses`, `@ApiResponse`, `@Parameter` trên toàn bộ 4 endpoint của `LocationController`.
   - Xử lý định nghĩa schema tường minh cho endpoint hỗ trợ kép `GET /api/v1/locations` (trả về `ApiResponse<List<LocationResponse>>` khi `format=json` và `GeoJsonFeatureCollection` khi `format=geojson`).
   - Bổ sung `@Schema` vào các trường DTO (`LocationResponse`, `LocationNearbyResponse`, `ApiResponse`, `GeoJsonFeatureCollection`, `GeoJsonFeature`) để tài liệu hiển thị ví dụ mẫu (examples) sinh động.
5. **Cấu hình trực quan hóa Swagger UI (`/swagger-ui.html`):**
   - Cho phép truy cập tài liệu Swagger tại đường dẫn chuẩn `http://localhost:8080/swagger-ui.html` (tự động điều hướng sang `/swagger-ui/index.html`).
   - Cung cấp schema JSON thô tại `http://localhost:8080/v3/api-docs`.
   - Bật tính năng thử nghiệm trực tiếp "Try it out", sắp xếp API tags theo bảng chữ cái (`alpha`), sắp xếp methods theo thứ tự chuẩn.
6. **Cấu hình Logging gỡ lỗi SQL & Actuator Health:**
   - Thiết lập mức log `DEBUG` cho `vn.weathergis` và Hibernate SQL; hiển thị SQL có thụt lề format rõ ràng.
   - Mở các endpoint Actuator: `/actuator/health` (hiển thị chi tiết trạng thái kết nối PostGIS và đĩa cứng), `/actuator/flyway` (hiển thị bảng lịch sử di trú).
7. **Bảo đảm 100% Tiêu Chuẩn Chất Lượng (Quality Gates):**
   - Không có lỗi biên dịch; lệnh `./gradlew bootRun --args='--spring.profiles.active=dev'` khởi chạy hoàn tất trong $< 5$ giây.
   - Định dạng mã nguồn tuân thủ 100% Spotless Google Java Format; toàn bộ test cases hiện hữu của W1-07 và W1-08 tiếp tục PASS.

---

## 2. Kiến Trúc Kỹ Thuật & Các Quyết Định Thiết Kế (Architectural Decisions & Deep Dive)

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│             KIẾN TRÚC RUNTIME PROFILE DEV & SPRINGDOC OPENAPI DOCUMENTATION             │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                        │
│   [ Trình Duyệt Web / Frontend Dev / QA ]                                              │
│         │                                                                              │
│         ├───── (HTTP GET /swagger-ui.html) ──────────────────────────────────┐         │
│         ├───── (HTTP GET /v3/api-docs) ────────────────────────────────────┐ │         │
│         └───── (HTTP GET /api/v1/locations?format=geojson) ─────────────┐  │ │         │
│                                                                         │  │ │         │
│                                                                         ▼  ▼ ▼         │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │                      Tomcat Server (Java 21 Virtual Threads)                   │   │
│   │                     CORS Filter (Allow Origin :5173, :3000)                    │   │
│   └──────────────────────────────────────┬─────────────────────────────────────────┘   │
│                                          │                                             │
│                 ┌────────────────────────┴────────────────────────┐                    │
│                 ▼                                                 ▼                    │
│   ┌───────────────────────────┐                     ┌──────────────────────────────┐   │
│   │ SpringDoc OpenAPI Starter │                     │ LocationController           │   │
│   │  - OpenApiConfig.java     │                     │  - @Tag, @Operation          │   │
│   │  - Swagger UI WebJars     │ ◄───(Metadata)───── │  - @ApiResponse 200/400/404  │   │
│   │  - Schema Model Converter │                     │  - Dual-Mode Response Docs   │   │
│   └─────────────┬─────────────┘                     └──────────────┬───────────────┘   │
│                 │                                                  │                   │
│                 ▼ (Swagger UI HTML & OpenAPI 3.0 JSON)             ▼                   │
│   ┌───────────────────────────┐                     ┌──────────────────────────────┐   │
│   │  http://localhost:8080    │                     │ LocationService & Repository │   │
│   │     /swagger-ui.html      │                     │  - PostGIS Spatial Queries   │   │
│   └───────────────────────────┘                     └──────────────┬───────────────┘   │
│                                                                    │                   │
│   ┌────────────────────────────────────────────────────────────────┴───────────────┐   │
│   │                     Spring Boot Lifecycle Startup (Profile: dev)               │   │
│   │                                                                                │   │
│   │   1. Đọc application-dev.yml                                                   │   │
│   │   2. Khởi tạo HikariCP DataSource (10 conns, target: localhost:5432)            │   │
│   │   3. Kích hoạt Flyway Auto-Migration:                                          │   │
│   │      - Quét classpath:db/migration                                             │   │
│   │      - Kiểm tra checksum V1, V2, V3 trong flyway_schema_history                │   │
│   │      - Tự động thực thi nếu chưa nạp (Idempotent Migration)                     │   │
│   │   4. Hibernate Spatial ddl-auto=validate kiểm tra khớp nối Entity               │   │
│   │   5. Actuator kích hoạt /actuator/health & /actuator/flyway                    │   │
│   └────────────────────────────────┬───────────────────────────────────────────────┘   │
│                                    │ (JDBC Connection)                                 │
│                                    ▼                                                   │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │                  Docker PostGIS Container (weathergis-postgres)                │   │
│   │         - Port 5432, Database: weather_gis_db                                  │   │
│   │         - PostGIS 3.4 + UUID-OSSP (V1)                                         │   │
│   │         - Table: locations, GIST Index (V2)                                    │   │
│   │         - Seed data: 68 điểm trắc địa quốc gia WGS84 (V3)                      │   │
│   └────────────────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 2.1. Chiến Lược Phân Tách Profile Môi Trường (Profile Segregation Strategy)

Trong kiến trúc Spring Boot chuyên nghiệp, việc pha trộn cấu hình giữa môi trường phát triển cục bộ (local dev), kiểm thử tự động (automated test) và môi trường sản xuất (production) là nguyên nhân hàng đầu dẫn đến các lỗi rò rỉ dữ liệu hoặc đứt gãy quy trình CI/CD.

Dự án áp dụng nguyên tắc **Tách bạch cấu hình theo Profile**:

1. **Profile Mặc Định (`application.yml`):**
   - Chứa các thông số bất biến (baseline configurations) chung cho toàn hệ thống: Virtual Threads, Jackson naming strategy, JPA batch size, Hibernate naming strategy (`CamelCaseToUnderscoresNamingStrategy`).
   - Thiết lập `spring.flyway.enabled: false` và `spring.jpa.hibernate.ddl-auto: validate` để đảm bảo khi chạy các bài Unit Test độc lập (không cần database) hoặc khi đóng gói Docker container, ứng dụng không cố gắng kết nối database ngay lập tức.
2. **Profile Phát Triển Cục Bộ (`application-dev.yml`):**
   - Kích hoạt khi truyền cờ `--spring.profiles.active=dev` hoặc thiết lập biến môi trường `SPRING_PROFILES_ACTIVE=dev`.
   - Trỏ trực tiếp tới Docker container PostGIS chạy tại máy trạm lập trình viên (`localhost:5432`).
   - Kích hoạt **Flyway tự động chạy kiểm tra và đồng bộ (`spring.flyway.enabled: true`)**.
   - Mở rộng logging: Bật log SQL, hiển thị tham số bind, bật log phân tích câu lệnh không gian để lập trình viên quan sát hiệu năng truy vấn `ST_DistanceSphere`.
   - Bật đầy đủ giao diện Swagger UI phục vụ kiểm thử tương tác.
3. **Profile Kiểm Thử Tự Động (`application-test.yml` — Phục vụ Task W1-13):**
   - Sẽ được Testcontainers PostGIS ghi đè động các thông số `jdbc:tc:postgresql:...` để thực thi các bài kiểm thử cô lập hoàn toàn.
4. **Profile Sản Xuất (`application-prod.yml` — Phục vụ Giai đoạn Triển khai):**
   - Nhận thông số từ Kubernetes Secret / Vault, tắt Swagger UI công khai hoặc yêu cầu xác thực bảo mật, tắt SQL debug logging để bảo vệ an toàn thông tin.

---

### 2.2. Cơ Chế Tự Động Hóa Flyway Migration & Idempotency Trên Profile Dev

Tại sao phải tự động hóa Flyway ở tầng ứng dụng khi lập trình viên khởi động Profile `dev`?

- **Vấn đề thực tế:** Khi làm việc nhóm, một lập trình viên GIS có thể bổ sung tệp migration `V4__create_weather_metrics_table.sql`. Nếu Backend không tự động chạy Flyway khi khởi động, các lập trình viên Frontend hoặc Backend khác kéo mã nguồn về sẽ gặp lỗi `TableNotFoundException` hoặc Hibernate `SchemaManagementException: Schema-validation: missing table`.
- **Giải pháp thiết kế:**
  ```yaml
  spring:
    flyway:
      enabled: true
      baseline-on-migrate: true
      baseline-version: 0
      locations: classpath:db/migration
      validate-on-migrate: true
      table: flyway_schema_history
  ```
- **Nguyên lý vận hành:**
  1. Khi Spring Boot khởi động, bean `FlywayMigrationInitializer` được kích hoạt trước khi JPA `EntityManagerFactory` được tạo.
  2. Flyway mở kết nối tới `weather_gis_db`, kiểm tra bảng `flyway_schema_history`.
  3. Flyway đối soát mã băm (checksum) của các tệp migration `V1`, `V2`, `V3` trên classpath với lịch sử CSDL.
  4. Nếu có tệp mới chưa nạp, Flyway thực thi tuần tự trong transaction.
  5. Nếu toàn bộ đã nạp và checksum trùng khớp, quá trình kết thúc trong vài phần trăm giây mà không làm gián đoạn hệ thống.
  6. Sau khi Flyway xác nhận CSDL hợp lệ, Hibernate `ddl-auto: validate` mới bắt đầu đối soát cấu trúc thực thể `LocationEntity` với cấu trúc bảng thực tế.

---

### 2.3. Thiết Kế Tài Liệu Hóa Chuẩn Mực Với SpringDoc OpenAPI 3.0

Hệ thống sử dụng thư viện `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0` tương thích hoàn hảo với Spring Boot 3.3.x và Java 21.

#### Thách thức 1: Xử lý Endpoint Phản Hồi Kép (Dual-Mode Response Documentation)

Endpoint `GET /api/v1/locations` được thiết kế có thể trả về:

- Kiểu JSON phong bao: `ApiResponse<List<LocationResponse>>` (khi `format=json`).
- Kiểu GeoJSON chuẩn: `GeoJsonFeatureCollection` (khi `format=geojson`).

Trong mã nguồn Java, kiểu trả về là `ResponseEntity<?>` (wildcard). Nếu không có cấu hình OpenAPI tường minh, Swagger UI sẽ hiển thị kiểu trả về là chuỗi rỗng hoặc `object`, làm mất đi giá trị của tài liệu hóa.
_Giải pháp thiết kế:_ Sử dụng `@Operation` kết hợp đa thẻ `@ApiResponse` chỉ định rõ `mediaType = "application/json"` ứng với `ApiResponse<List<LocationResponse>>` và `mediaType = "application/geo+json"` ứng với `GeoJsonFeatureCollection`.

#### Thách thức 2: Nguy cơ Đệ Quy Vô Hạn và Vỡ Schema do Thư Viện JTS Geometry

Đối tượng `org.locationtech.jts.geom.Geometry` (hoặc `Point`) chứa nhiều thuộc tính nội tại của thuật toán trắc địa (như `precisionModel`, `coordinateSequence`, `factory`, `envelopeInternal`). Nếu SpringDoc quét trực tiếp các trường này bằng reflection, nó sẽ sinh ra một schema JSON khổng lồ với hàng trăm trường không cần thiết, thậm chí gây lỗi vòng lặp tham chiếu đệ quy (StackOverflowError).
_Giải pháp thiết kế:_ Trong `OpenApiConfig.java`, cấu hình thay thế kiểu (Type Replacement) thông qua `SpringDocUtils`:

```java
SpringDocUtils.getConfig().replaceWithSchema(
    org.locationtech.jts.geom.Geometry.class,
    new io.swagger.v3.oas.models.media.Schema<>()
        .type("object")
        .description("Đối tượng hình học GeoJSON RFC 7946 (Point, Polygon, MultiPolygon)")
        .example(Map.of("type", "Point", "coordinates", List.of(105.8542, 21.0285)))
);
```

#### Thách thức 3: Phân Nhóm Tài Liệu API (GroupedOpenApi)

Khi hệ thống phát triển sang Tuần 2 (dữ liệu khí tượng OWM/GDACS) và Tuần 3 (AI Agent Cảnh Báo), số lượng endpoint sẽ tăng lên nhanh chóng. Việc dồn tất cả vào một trang duy nhất sẽ gây khó khăn cho việc tra cứu.
_Giải pháp thiết kế:_ Cấu hình 3 nhóm API chuyên biệt thông qua bean `GroupedOpenApi`:

1. `public-api`: Nhóm toàn bộ các endpoint công khai thuộc tiền tố `/api/v1/**`.
2. `locations-api`: Nhóm riêng biệt cho Bounded Context trạm quan trắc trắc địa `/api/v1/locations/**`.
3. `system-monitoring`: Nhóm giám sát sức khỏe hệ thống `/actuator/**`.

---

### 2.4. Cấu Trúc HikariCP Connection Pool Cho Môi Trường Phát Triển

Để bảo đảm máy chủ phát triển hoạt động ổn định, không chiếm dụng kiệt quệ tài nguyên của PostgreSQL:

| Thuộc Tính Cấu Hình HikariCP | Giá Trị Dev   | Rationale Kỹ Thuật (Lý Do Thiết Kế)                                                                                                               |
| :--------------------------- | :------------ | :------------------------------------------------------------------------------------------------------------------------------------------------ |
| `maximum-pool-size`          | `10`          | Vừa đủ cho 1 lập trình viên test đồng thời nhiều tab trên browser/Swagger, tránh chiếm vượt ngưỡng `max_connections` (mặc định 100) của Postgres. |
| `minimum-idle`               | `5`           | Duy trì sẵn 5 kết nối nhàn rỗi để các truy vấn từ Swagger UI phản hồi tức thì mà không phải chịu chi phí bắt tay TCP mới.                         |
| `idle-timeout`               | `300000ms`    | 5 phút: Tự động thu hồi các kết nối nhàn rỗi vượt quá `minimum-idle` để giải phóng RAM cho máy trạm.                                              |
| `connection-timeout`         | `20000ms`     | 20 giây: Thời gian chờ tối đa khi lấy kết nối từ pool trước khi ném ra `SQLTransientConnectionException`.                                         |
| `max-lifetime`               | `1200000ms`   | 20 phút: Định kỳ đóng và mở mới kết nối để tránh lỗi stale socket hoặc rò rỉ bộ nhớ ở tầng driver JDBC.                                           |
| `pool-name`                  | Chuỗi rõ ràng | Đặt tên `WeatherGisDevHikariPool` để dễ dàng nhận diện và giám sát trong log hệ thống cũng như Java Flight Recorder.                              |

---

## 3. Đặc Tả Chi Tiết Các Tệp Mã Nguồn & Cấu Hình (Code & Config Specifications)

Dưới đây là đặc tả chi tiết 100% các tệp mã nguồn và cấu hình kỹ thuật mà kỹ sư Backend cần tạo mới hoặc cập nhật trong Task W1-09.

```
backend/
├── src/
│   ├── main/
│   │   ├── java/vn/weathergis/
│   │   │   ├── common/
│   │   │   │   ├── api/
│   │   │   │   │   ├── ApiResponse.java                   # [CẬP NHẬT] Bổ sung @Schema OpenAPI
│   │   │   │   │   ├── GeoJsonFeature.java                # [CẬP NHẬT] Bổ sung @Schema OpenAPI
│   │   │   │   │   └── GeoJsonFeatureCollection.java      # [CẬP NHẬT] Bổ sung @Schema OpenAPI
│   │   │   │   └── config/
│   │   │   │       ├── CorsConfig.java                    # Đã có từ W1-08
│   │   │   │       ├── JacksonConfig.java                 # Đã có từ W1-07
│   │   │   │       └── OpenApiConfig.java                 # [TẠO MỚI] Cấu hình SpringDoc OpenAPI 3.0
│   │   │   └── location/
│   │   │       ├── controller/
│   │   │       │   └── LocationController.java            # [CẬP NHẬT] Bổ sung @Tag, @Operation, @ApiResponses
│   │   │       └── dto/
│   │   │           ├── LocationNearbyResponse.java        # [CẬP NHẬT] Bổ sung @Schema OpenAPI
│   │   │           └── LocationResponse.java              # [CẬP NHẬT] Bổ sung @Schema OpenAPI
│   │   └── resources/
│   │       ├── application.yml                            # Đã có từ W1-07
│   │       └── application-dev.yml                        # [TẠO MỚI] Cấu hình chuyên biệt môi trường DEV
```

---

### 3.1. Tệp Cấu Hình Profile Phát Triển (`backend/src/main/resources/application-dev.yml`)

Tệp này kích hoạt khi chạy với `--spring.profiles.active=dev`. Thiết lập toàn bộ kết nối PostgreSQL PostGIS, tự động hóa Flyway, chi tiết log SQL, cấu hình Swagger UI và giám sát Actuator.

```yaml
# ==============================================================================
# HỆ THỐNG WEBGIS GIÁM SÁT MƯA GIÓ BÃO TÍCH HỢP AI AGENT (VNSTORMGIS)
# CẤU HÌNH MÔI TRƯỜNG PHÁT TRIỂN CỤC BỘ (LOCAL DEVELOPMENT PROFILE)
# ==============================================================================

spring:
  config:
    activate:
      on-profile: dev

  # ----------------------------------------------------------------------------
  # 1. CẤU HÌNH KẾT NỐI CƠ SỞ DỮ LIỆU POSTGRESQL + POSTGIS 16
  # ----------------------------------------------------------------------------
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:weather_gis_db}
    username: ${DB_USER:weather_gis_user}
    password: ${DB_PASSWORD:weather_gis_password_secure_2024}
    driver-class-name: org.postgresql.Driver
    hikari:
      pool-name: WeatherGisDevHikariPool
      maximum-pool-size: 10
      minimum-idle: 5
      idle-timeout: 300000 # 5 phút nhàn rỗi thì giải phóng kết nối thừa
      connection-timeout: 20000 # 20 giây chờ lấy kết nối tối đa
      max-lifetime: 1200000 # 20 phút vòng đời kết nối tối đa
      auto-commit: true
      connection-test-query: SELECT 1

  # ----------------------------------------------------------------------------
  # 2. CẤU HÌNH QUẢN TRỊ DI TRÚ CSDL FLYWAY (TỰ ĐỘNG KÍCH HOẠT TRÊN DEV)
  # ----------------------------------------------------------------------------
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    locations: classpath:db/migration
    schemas: public
    table: flyway_schema_history
    validate-on-migrate: true
    out-of-order: false
    clean-disabled: true # Tuyệt đối không cho phép lệnh clean phá hủy CSDL dev

  # ----------------------------------------------------------------------------
  # 3. CẤU HÌNH HIBERNATE SPATIAL 6 & JPA LOGGING
  # ----------------------------------------------------------------------------
  jpa:
    open-in-view: false
    show-sql: false # Tắt show-sql mặc định để dùng logger có định dạng bên dưới
    hibernate:
      ddl-auto: validate # Đối soát chặt chẽ Entity với DDL Flyway; không tự tạo sửa bảng
    properties:
      hibernate:
        format_sql: true # Tự động thụt lề định dạng câu lệnh SQL cho dễ quan sát
        highlight_sql: true # Làm nổi bật từ khóa SQL trên console có màu ANSI
        use_sql_comments: true # Thêm comment chú thích nguồn gốc câu lệnh trong Hibernate
        jdbc:
          batch_size: 50 # Tối ưu chèn hàng loạt dữ liệu khí tượng trong tương lai
        order_inserts: true
        order_updates: true

# ------------------------------------------------------------------------------
# 4. CẤU HÌNH TÀI LIỆU HÓA SPRINGDOC OPENAPI 3 & SWAGGER UI
# ------------------------------------------------------------------------------
springdoc:
  api-docs:
    path: /v3/api-docs
    enabled: true
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
    tags-sorter: alpha # Sắp xếp các nhóm API theo bảng chữ cái A-Z
    operations-sorter: method # Sắp xếp các endpoint theo phương thức HTTP (GET, POST, ...)
    display-request-duration: true # Hiển thị thời gian thực thi của từng request trên giao diện
    doc-expansion: list # Mở rộng danh sách endpoint ở mức tổng quan gọn gàng
    try-it-out-enabled: true # Bật sẵn chế độ thử nghiệm API tương tác trực tiếp
    filter: true # Bật ô tìm kiếm lọc nhanh endpoint trên giao diện Swagger
  show-actuator: true # Hiển thị các endpoint Actuator trong tài liệu API
  packages-to-scan: vn.weathergis
  paths-to-match: /api/**, /actuator/**

# ------------------------------------------------------------------------------
# 5. CẤU HÌNH GHI NHẬT KÝ (LOGGING) MÔI TRƯỜNG DEV
# ------------------------------------------------------------------------------
logging:
  level:
    root: INFO
    vn.weathergis: DEBUG # Ghi log chi tiết toàn bộ nghiệp vụ ứng dụng WeatherGIS
    org.hibernate.SQL: DEBUG # Hiển thị các câu truy vấn SQL thực thi
    org.hibernate.orm.jdbc.bind: TRACE # Hiển thị chi tiết giá trị tham số bind vào câu lệnh SQL
    org.flywaydb: INFO # Ghi log chi tiết quá trình quét và di trú Flyway
    org.springdoc: INFO # Ghi log quá trình quét khởi tạo tài liệu OpenAPI
    com.zaxxer.hikari: INFO # Giám sát trạng thái cấp phát kết nối của HikariCP
  pattern:
    console: "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr(%5p) %clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %m%n%wEx"

# ------------------------------------------------------------------------------
# 6. CẤU HÌNH GIÁM SÁT VẬN HÀNH SPRING BOOT ACTUATOR
# ------------------------------------------------------------------------------
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,flyway # Mở endpoint xem sức khỏe và lịch sử di trú Flyway
  endpoint:
    health:
      show-details: always # Hiển thị chi tiết trạng thái kết nối CSDL và đĩa cứng
      probes:
        enabled: true # Bật liveness và readiness probes chuẩn Kubernetes
  info:
    env:
      enabled: true
```

---

### 3.2. Lớp Cấu Hình Toàn Cục SpringDoc OpenAPI (`OpenApiConfig.java`)

Tệp này được đặt tại thư mục `backend/src/main/java/vn/weathergis/common/config/OpenApiConfig.java`. Nó chịu trách nhiệm khai báo siêu dữ liệu hệ thống, xử lý tương thích schema đối tượng hình học JTS `Geometry` (hóa giải hoàn toàn rủi ro vỡ schema Swagger), và định nghĩa các nhóm phân loại API.

```java
package vn.weathergis.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình tập trung tài liệu hóa OpenAPI 3.0 và Swagger UI cho hệ thống VNstormgis.
 * Định nghĩa siêu dữ liệu kỹ thuật, danh sách máy chủ, phân nhóm Bounded Context
 * và chuẩn hóa lược đồ dữ liệu hình học OGC JTS Geometry.
 */
@Configuration
public class OpenApiConfig {

    static {
        // [RSK-W1-12 HÓA GIẢI]: Thay thế toàn bộ đối tượng JTS Geometry phức tạp bằng schema GeoJSON phẳng.
        // Ngăn chặn reflection quét sâu vào các trường nội bộ của JTS gây tràn bộ nhớ hoặc vỡ UI Swagger.
        SpringDocUtils.getConfig()
                .replaceWithSchema(
                        Geometry.class,
                        new Schema<>()
                                .type("object")
                                .description("Đối tượng hình học OGC GeoJSON RFC 7946 (Point, Polygon)")
                                .example(
                                        Map.of(
                                                "type", "Point",
                                                "coordinates", List.of(105.8542, 21.0285))))
                .replaceWithSchema(
                        Point.class,
                        new Schema<>()
                                .type("object")
                                .description("Điểm trắc địa WGS84 GeoJSON [longitude, latitude]")
                                .example(
                                        Map.of(
                                                "type", "Point",
                                                "coordinates", List.of(105.8542, 21.0285))));
    }

    @Bean
    public OpenAPI customOpenAPI(
            @Value("${server.port:8080}") int serverPort,
            @Value("${spring.application.name:weather-gis-backend}") String appName) {

        Contact contact = new Contact()
                .name("Đội Ngũ Kỹ Thuật Vietnam WeatherGIS")
                .email("engineering@weathergis.vn")
                .url("https://github.com/dllv/VNstormgis");

        License license = new License()
                .name("Apache 2.0 / Bản Quyền Hệ Thống Dự Án Mở")
                .url("https://www.apache.org/licenses/LICENSE-2.0.html");

        Info info = new Info()
                .title("Vietnam WeatherGIS - RESTful & Spatial GeoJSON APIs")
                .version("1.0.0-SNAPSHOT")
                .description(
                        """
                        ### Hệ Thống WebGIS Giám Sát Mưa Gió Bão Tích Hợp AI Agent Cảnh Báo Thiên Tai

                        Bộ tài liệu API đặc tả các điểm cuối truy vấn dữ liệu không gian, trắc địa khí tượng,
                        dữ liệu dự báo thời tiết và kênh tương tác với trợ lý thông minh.

                        #### Quy Chuẩn Phản Hồi Dữ Liệu:
                        - **Chuẩn Không Gian:** Hệ quy chiếu trắc địa toàn cầu WGS84 (EPSG:4326), tọa độ `[kinh độ (lng), vĩ độ (lat)]`.
                        - **Chuẩn Bản Đồ:** GeoJSON FeatureCollection theo đặc tả RFC 7946 (sẵn sàng cho Leaflet `L.geoJSON()`).
                        - **Chuẩn Phong Bao REST:** Phong bao hợp nhất `ApiResponse<T>` chứa `success`, `message`, `data`, `timestamp`.
                        - **Chuẩn Báo Lỗi Toàn Cục:** Chuẩn công nghiệp RFC 7807 Problem Details (`ProblemDetail`).
                        """)
                .contact(contact)
                .license(license);

        Server localServer = new Server()
                .url("http://localhost:" + serverPort)
                .description("Máy chủ phát triển cục bộ (Local Development Server)");

        Server stagingServer = new Server()
                .url("https://staging.weathergis.vn")
                .description("Môi trường kiểm thử tích hợp (Staging Integration Server)");

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer, stagingServer));
    }

    /** Nhóm toàn bộ các API nghiệp vụ công khai của hệ thống (/api/v1/**). */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("1. Toàn Bộ API Hệ Thống (v1-all)")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    /** Nhóm chuyên biệt cho Bounded Context quản lý điểm trắc địa và trạm quan trắc. */
    @Bean
    public GroupedOpenApi locationApi() {
        return GroupedOpenApi.builder()
                .group("2. Trạm Quan Trắc & Không Gian (v1-locations)")
                .pathsToMatch("/api/v1/locations/**")
                .build();
    }

    /** Nhóm giám sát vận hành hệ thống thông qua Spring Boot Actuator. */
    @Bean
    public GroupedOpenApi actuatorApi() {
        return GroupedOpenApi.builder()
                .group("3. Giám Sát Sức Khỏe Vận Hành (actuator)")
                .pathsToMatch("/actuator/**")
                .build();
    }
}
```

---

### 3.3. Cập Nhật Bổ Sung Siêu Dữ Liệu OpenAPI Cho `LocationController.java`

Cập nhật `backend/src/main/java/vn/weathergis/location/controller/LocationController.java` với đầy đủ các annotations OpenAPI (`@Tag`, `@Operation`, `@ApiResponses`, `@ApiResponse`, `@Parameter`) để Swagger UI hiển thị thông tin đầy đủ, rõ ràng và có thể thử nghiệm trực tiếp.

```java
package vn.weathergis.location.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.location.dto.LocationNearbyResponse;
import vn.weathergis.location.dto.LocationResponse;
import vn.weathergis.location.service.LocationService;

/** Bộ điều khiển RESTful API quản lý trạm quan trắc khí tượng và địa không gian. */
@Tag(
        name = "1. Trạm Quan Trắc Trắc Địa (Locations)",
        description =
                "Truy vấn mạng lưới 68 điểm trắc địa quốc gia (63 tỉnh/thành phố và 5 trạm hải đảo) "
                        + "hỗ trợ định dạng kép JSON / GeoJSON RFC 7946 và truy vấn không gian PostGIS ST_DistanceSphere.")
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * Lấy danh sách điểm quan trắc hỗ trợ kép định dạng JSON hoặc GeoJSON.
     */
    @Operation(
            summary = "Lấy danh sách trạm quan trắc (Hỗ trợ kép JSON / GeoJSON)",
            description =
                    "Truy vấn danh bạ các trạm quan trắc khí tượng trên toàn lãnh thổ Việt Nam. "
                            + "Khi truyền `format=geojson`, trả về cấu trúc FeatureCollection chuẩn RFC 7946 nạp trực tiếp vào Leaflet `L.geoJSON()`.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Truy vấn danh sách trạm thành công",
                content = {
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = vn.weathergis.common.api.ApiResponse.class)),
                    @Content(
                            mediaType = "application/geo+json",
                            schema = @Schema(implementation = GeoJsonFeatureCollection.class))
                }),
        @ApiResponse(
                responseCode = "400",
                description = "Tham số lọc không hợp lệ (RFC 7807)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public ResponseEntity<?> getLocations(
            @Parameter(
                            description = "Định dạng kết quả trả về: 'json' (mặc định) hoặc 'geojson' chuẩn Leaflet",
                            example = "json",
                            schema = @Schema(allowableValues = {"json", "geojson"}, defaultValue = "json"))
                    @RequestParam(name = "format", defaultValue = "json")
                    String format,
            @Parameter(description = "Lọc theo vùng địa lý (Bắc Bộ, Trung Bộ, Nam Bộ)", example = "Bắc Bộ")
                    @RequestParam(name = "region", required = false)
                    String region,
            @Parameter(description = "Lọc theo trạng thái hoạt động (mặc định true)", example = "true")
                    @RequestParam(name = "is_active", defaultValue = "true")
                    Boolean isActive,
            @Parameter(description = "Từ khóa tìm kiếm theo tên tỉnh/thành hoặc mã trạm", example = "Hà Nội")
                    @RequestParam(name = "search", required = false)
                    String search) {

        if ("geojson".equalsIgnoreCase(format)) {
            GeoJsonFeatureCollection geoJson =
                    locationService.getAllLocationsAsGeoJson(region, isActive, search);
            return ResponseEntity.ok(geoJson);
        }

        List<LocationResponse> locations =
                locationService.getAllLocations(region, isActive, search);
        return ResponseEntity.ok(
                vn.weathergis.common.api.ApiResponse.ok(locations, "Lấy danh sách trạm quan trắc thành công"));
    }

    /** Lấy chi tiết trạm quan trắc theo khóa định danh UUID. */
    @Operation(
            summary = "Lấy chi tiết trạm theo UUID",
            description = "Tra cứu thông tin chi tiết một trạm trắc địa thông qua khóa chính UUID duy nhất.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tìm thấy trạm quan trắc",
                content = @Content(schema = @Schema(implementation = vn.weathergis.common.api.ApiResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Không tìm thấy trạm với UUID cung cấp (RFC 7807)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<LocationResponse>> getLocationById(
            @Parameter(description = "Định danh UUID của trạm", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
                    @PathVariable("id")
                    UUID id) {
        LocationResponse response = locationService.getLocationById(id);
        return ResponseEntity.ok(vn.weathergis.common.api.ApiResponse.ok(response));
    }

    /** Lấy chi tiết trạm quan trắc theo mã tự nhiên (ví dụ: HAN, DNG, SGN). */
    @Operation(
            summary = "Lấy chi tiết trạm theo mã trắc địa (Code)",
            description = "Tra cứu nhanh thông tin trạm theo mã ký hiệu viết tắt 3 ký tự (ví dụ: HAN - Hà Nội, DNG - Đà Nẵng, SGN - TP.HCM).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tìm thấy trạm quan trắc",
                content = @Content(schema = @Schema(implementation = vn.weathergis.common.api.ApiResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Không tìm thấy mã trạm (RFC 7807)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/code/{code}")
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<LocationResponse>> getLocationByCode(
            @Parameter(description = "Mã viết tắt trạm trắc địa (3 ký tự in hoa)", example = "HAN")
                    @PathVariable("code")
                    String code) {
        LocationResponse response = locationService.getLocationByCode(code);
        return ResponseEntity.ok(vn.weathergis.common.api.ApiResponse.ok(response));
    }

    /** Tìm kiếm các trạm quan trắc gần nhất quanh một tọa độ trắc địa. */
    @Operation(
            summary = "Tìm kiếm trạm lân cận quanh tọa độ (ST_DistanceSphere)",
            description =
                    "Thực thi truy vấn không gian PostGIS sử dụng hàm cầu `ST_DistanceSphere` để tính "
                            + "khoảng cách thực tế theo đường chim bay từ một điểm trắc địa WGS84 tâm tới các trạm trong bán kính cho trước.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Truy vấn không gian thành công",
                content = @Content(schema = @Schema(implementation = vn.weathergis.common.api.ApiResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Tọa độ hoặc bán kính không hợp lệ (RFC 7807)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/nearby")
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<List<LocationNearbyResponse>>> getNearbyLocations(
            @Parameter(description = "Kinh độ tâm WGS84 [-180, 180]", example = "105.8542")
                    @RequestParam("lng")
                    double longitude,
            @Parameter(description = "Vĩ độ tâm WGS84 [-90, 90]", example = "21.0285")
                    @RequestParam("lat")
                    double latitude,
            @Parameter(description = "Bán kính quét tìm kiếm tính bằng Kilometers (mặc định 100km)", example = "50.0")
                    @RequestParam(name = "radiusKm", defaultValue = "100.0")
                    double radiusKm) {

        List<LocationNearbyResponse> nearby =
                locationService.getNearbyLocations(longitude, latitude, radiusKm);
        return ResponseEntity.ok(
                vn.weathergis.common.api.ApiResponse.ok(nearby, "Tìm thấy " + nearby.size() + " trạm lân cận"));
    }
}
```

---

### 3.4. Bổ Sung `@Schema` Vào Các Lớp DTO

Để tài liệu Swagger UI hiển thị thông tin trực quan, dễ hiểu đối với lập trình viên Frontend, các DTO cần được bổ sung chú thích `@Schema`:

#### 1. Cập nhật `backend/src/main/java/vn/weathergis/location/dto/LocationResponse.java`:

```java
package vn.weathergis.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import vn.weathergis.location.domain.LocationEntity;

/** DTO phẳng hóa thông tin trạm quan trắc phục vụ các client REST tiêu chuẩn. */
@Schema(description = "Thông tin chi tiết điểm trắc địa / trạm quan trắc khí tượng")
public record LocationResponse(
        @Schema(description = "Khóa chính UUID duy nhất", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        UUID id,
        @Schema(description = "Mã ký hiệu trắc địa 3 ký tự", example = "HAN")
        String code,
        @Schema(description = "Tên địa danh trạm quan trắc", example = "Hà Nội")
        String name,
        @Schema(description = "Vùng địa lý hành chính", example = "Bắc Bộ")
        String region,
        @Schema(description = "Kinh độ trắc địa WGS84 (longitude)", example = "105.8542")
        double longitude,
        @Schema(description = "Vĩ độ trắc địa WGS84 (latitude)", example = "21.0285")
        double latitude,
        @Schema(description = "Trạng thái hoạt động giám sát", example = "true")
        boolean isActive,
        @Schema(description = "Dấu thời gian cập nhật gần nhất theo chuẩn ISO 8601", example = "2026-10-08T18:00:00+07:00")
        OffsetDateTime updatedAt) {

    public static LocationResponse fromEntity(LocationEntity entity) {
        double lng = entity.getGeom() != null ? entity.getGeom().getX() : 0.0;
        double lat = entity.getGeom() != null ? entity.getGeom().getY() : 0.0;
        return new LocationResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getRegion(),
                lng,
                lat,
                Boolean.TRUE.equals(entity.getIsActive()),
                entity.getUpdatedAt());
    }
}
```

#### 2. Cập nhật `backend/src/main/java/vn/weathergis/location/dto/LocationNearbyResponse.java`:

```java
package vn.weathergis.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** DTO kết quả tìm kiếm trạm lân cận kèm khoảng cách trắc địa đường chim bay. */
@Schema(description = "Kết quả trạm quan trắc lân cận kèm khoảng cách tính toán bởi ST_DistanceSphere")
public record LocationNearbyResponse(
        @Schema(description = "Khóa chính UUID của trạm", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        UUID id,
        @Schema(description = "Mã trạm trắc địa", example = "HAN")
        String code,
        @Schema(description = "Tên trạm quan trắc", example = "Hà Nội")
        String name,
        @Schema(description = "Vùng địa lý", example = "Bắc Bộ")
        String region,
        @Schema(description = "Kinh độ WGS84", example = "105.8542")
        double longitude,
        @Schema(description = "Vĩ độ WGS84", example = "21.0285")
        double latitude,
        @Schema(description = "Khoảng cách đường chim bay tính bằng Kilometers", example = "12.45")
        double distanceKm) {}
```

#### 3. Cập nhật `backend/src/main/java/vn/weathergis/common/api/ApiResponse.java`:

```java
package vn.weathergis.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Phong bao chuẩn hóa phản hồi RESTful API cho toàn bộ hệ thống WeatherGIS.
 *
 * @param <T> Kiểu dữ liệu thực thể payload
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Phong bao phản hồi chuẩn RESTful Envelope cho mọi API")
public class ApiResponse<T> {

    @Schema(description = "Chỉ thị trạng thái xử lý nghiệp vụ thành công hay thất bại", example = "true")
    private boolean success;

    @Schema(description = "Thông điệp mô tả kết quả xử lý dành cho giao diện người dùng", example = "Thao tác thành công")
    private String message;

    @Schema(description = "Dữ liệu payload thực tế trả về")
    private T data;

    @Schema(description = "Thời điểm phản hồi theo chuẩn ISO 8601", example = "2026-10-08T18:00:00+07:00")
    private OffsetDateTime timestamp;

    public ApiResponse() {
        this.timestamp = OffsetDateTime.now();
    }

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = OffsetDateTime.now();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "Thành công", data);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
```

---

## 4. Ma Trận Kịch Bản Kiểm Thử & Nghiệm Thu Định Lượng (Verification Matrix)

Để nghiệm thu Task W1-09 đạt tiêu chuẩn Definition of Done (DoD), kỹ sư Backend cần thực thi đầy đủ **10 kịch bản kiểm định (TC-01..TC-10)** và ghi nhận kết quả xác minh:

| Mã Kịch Bản | Mục Tiêu Kiểm Định                              | Lệnh / Thao Tác Thực Thi                                                       | Kết Quả Kỳ Vọng (Expected Outcome)                                                                                                     | Tiêu Chuẩn Nghiệm Thu                                |
| :---------- | :---------------------------------------------- | :----------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------- | :--------------------------------------------------- |
| **TC-01**   | Kiểm tra cú pháp YAML của `application-dev.yml` | `cat backend/src/main/resources/application-dev.yml`                           | Không có lỗi thụt dòng (indentation), biến môi trường fallback đúng chuẩn `${VAR:default}`.                                            | Cú pháp YAML hợp lệ 100%.                            |
| **TC-02**   | Khởi động ứng dụng với Profile `dev`            | `./gradlew bootRun --args='--spring.profiles.active=dev'`                      | Log hiển thị: `The following 1 profile is active: "dev"`. Khởi động thành công cổng 8080.                                              | Ứng dụng khởi động $< 6$ giây.                       |
| **TC-03**   | Xác minh HikariCP kết nối PostGIS Local         | Quan sát console log khi khởi động                                             | Log hiển thị: `HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection` trỏ vào `weather_gis_db`.                             | Kết nối DB thông suốt, không timeout.                |
| **TC-04**   | Xác minh Flyway Auto-Migration kích hoạt        | Quan sát console log Flyway                                                    | Log hiển thị: `Current version of schema "public": 3` và `Schema "public" is up to date. No migration necessary.`                      | Flyway validate thành công V1, V2, V3.               |
| **TC-05**   | Kiểm tra Actuator Flyway Endpoint               | `curl -s http://localhost:8080/actuator/flyway`                                | Trả về JSON chứa lịch sử 3 script `V1`, `V2`, `V3` với trạng thái `SUCCESS`.                                                           | HTTP 200 OK, hiển thị đầy đủ 3 migration.            |
| **TC-06**   | Kiểm tra Actuator Health Check                  | `curl -s http://localhost:8080/actuator/health`                                | Trả về `{"status":"UP","components":{"db":{"status":"UP",...}}}`.                                                                      | Trạng thái hệ thống và CSDL PostGIS `UP`.            |
| **TC-07**   | Truy cập Swagger UI trên trình duyệt            | Mở `http://localhost:8080/swagger-ui.html`                                     | Tự động chuyển hướng sang `/swagger-ui/index.html`, hiển thị giao diện xanh đen của Swagger UI kèm tiêu đề dự án "Vietnam WeatherGIS". | HTTP 200/302, render giao diện không lỗi JavaScript. |
| **TC-08**   | Xác minh OpenAPI JSON Spec                      | `curl -s http://localhost:8080/v3/api-docs`                                    | Trả về chuỗi JSON OpenAPI 3.0 hợp lệ chứa đầy đủ info, servers, paths `/api/v1/locations`.                                             | JSON parse hợp lệ, không chứa đệ quy vòng lặp.       |
| **TC-09**   | Thử nghiệm tương tác Swagger "Try it out"       | Click "Try it out" trên Swagger UI endpoint `/api/v1/locations?format=geojson` | Nhận kết quả trực tiếp trên trình duyệt là FeatureCollection chứa 68 features điểm trắc địa.                                           | HTTP 200 OK, phản hồi GeoJSON đúng định dạng.        |
| **TC-10**   | Kiểm tra tuân thủ Spotless & Test Hồi Quy       | `./gradlew spotlessCheck test`                                                 | Spotless không báo vi phạm; 100% các bài test của Task W1-07 và W1-08 đều `BUILD SUCCESSFUL`.                                          | Không phá vỡ bất kỳ bài kiểm thử nào.                |

---

## 5. Tương Thích Kiến Trúc Tầng Ứng Dụng & Kế Hoạch Chuyển Giao (Integration & Handoff)

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        MẮT XÍCH CHUYỂN GIAO TIẾN ĐỘ SPRINT 1                           │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                        │
│   [ Task W1-07 & W1-08 ]                                                               │
│   - Nền tảng Spring Boot 3.3, Hibernate Spatial                                        │
│   - Bounded Context location, JTS Entity, Repository, Controller kép, RFC 7807         │
│                                  │                                                     │
│                                  ▼                                                     │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │ TASK W1-09: RUNTIME DEV & OPENAPI DOCUMENTATION (TRỌNG TÂM HIỆN TẠI)           │   │
│   │  1. application-dev.yml kết nối PostGIS 16 localhost                           │   │
│   │  2. Flyway tự động kiểm tra và đồng bộ lược đồ V1..V3                          │   │
│   │  3. Swagger UI tại /swagger-ui.html làm Single Source of Truth                 │   │
│   │  4. Actuator Health & Flyway endpoints giám sát trực tiếp                      │   │
│   └──────────────────────────────┬─────────────────────────────────────────────────┘   │
│                                  │                                                     │
│         ┌────────────────────────┴────────────────────────┐                            │
│         ▼                                                 ▼                            │
│   [ BÀN GIAO CHO FRONTEND ]                         [ BÀN GIAO CHO QA / DEVOPS ]       │
│   Tasks W1-10, W1-11, W1-12, W1-14                  Task W1-13                         │
│   - Frontend dùng Swagger UI để tra cứu             - QA dùng Flyway config chuẩn      │
│     schema DTO và GeoJSON.                            để xây dựng Testcontainers       │
│   - Frontend gọi trực tiếp API Backend                PostGIS integration test.        │
│     thông qua Vite Reverse Proxy :8080.             - Xác thực tính độc lập của CI/CD. │
│                                                                                        │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### 5.1. Bàn Giao Hợp Đồng Giao Tiếp API Cho Đội Ngũ Frontend (Tasks W1-10..W1-14)

- **Điểm kết nối:** Toàn bộ kỹ sư Frontend khi bắt đầu khởi tạo dự án React 18 / Vite (Task W1-10) và tích hợp bản đồ Leaflet (Task W1-11, W1-12) sẽ truy cập trực tiếp vào `http://localhost:8080/swagger-ui.html`.
- **Hợp đồng GeoJSON chuẩn xác:** Frontend tra cứu schema `GeoJsonFeatureCollection`, nắm rõ cấu trúc thuộc tính `properties: { id, code, name, region }` và mảng hình học `geometry: { type: "Point", coordinates: [lng, lat] }` để cấu hình hàm `L.geoJSON()` vẽ marker trên bản đồ.
- **Hợp đồng Xử lý Lỗi RFC 7807:** Frontend nắm rõ cấu trúc `ProblemDetail` (thuộc tính `type`, `title`, `status`, `detail`, `instance`, `invalidParams`) để hiển thị thông báo toast lỗi cho người dùng một cách chuyên nghiệp.

### 5.2. Tiền Đề Cho Kiểm Thử Tự Động Testcontainers PostGIS (Task W1-13)

- Cơ chế Flyway được kích hoạt và kiểm định sạch sẽ ở W1-09 giúp đảm bảo rằng khi bước sang Task W1-13, bài kiểm thử tích hợp `LocationRepositoryTest` với Testcontainers PostGIS chỉ cần kế thừa đúng logic nạp migration này vào container tạm thời mà không gặp bất kỳ lỗi cú pháp SQL nào.

---

## 6. Sổ Đăng Ký Quản Trị Rủi Ro Kỹ Thuật (Risk Registry & Contingency Plans)

Dưới đây là 5 rủi ro kỹ thuật trọng yếu được nhận diện cho Task W1-09 kèm biện pháp ứng phó cụ thể:

| Mã Rủi Ro     | Mô Tả Nguy Cơ Kỹ Thuật                                                                                                                                                    |      Mức Độ      | Kế Hoạch Ứng Phó & Giải Pháp Hóa Giải (Mitigation Strategy)                                                                                                                                                                                          |   Trạng Thái    |
| :------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | :--------------: | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :-------------: |
| **RSK-W1-10** | **Flyway Checksum Mismatch:** Khi khởi động lại trên container Docker cũ, Flyway báo lỗi `Migration checksum mismatch for migration version X`.                           |     **Cao**      | Kích hoạt `spring.flyway.baseline-on-migrate: true`. Nếu lập trình viên lỡ sửa đổi tệp migration cũ trên máy local, cung cấp lệnh cứu hộ: `./gradlew flywayRepair` hoặc reset container nhanh bằng `docker compose down -v && docker compose up -d`. | 🟢 Đã Kiểm Soát |
| **RSK-W1-11** | **Xung Đột Phiên Bản SpringDoc & Spring Boot 3.3:** Swagger UI không nạp được tài nguyên CSS/JS hoặc báo lỗi 404 khi truy cập `/swagger-ui.html`.                         |  **Trung Bình**  | Dự án đã khóa cứng phiên bản tương thích chính thức `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0` (tương thích Spring Boot 3.3.x). Đường dẫn cấu hình tường minh `springdoc.swagger-ui.path=/swagger-ui.html`.                           | 🟢 Đã Kiểm Soát |
| **RSK-W1-12** | **Vỡ Schema do Reflection Quét JTS Geometry:** Swagger UI quét trường `org.locationtech.jts.geom.Geometry` gây tràn bộ đệm hoặc loop schema vô hạn.                       | **Nghiêm Trọng** | Đã hóa giải tận gốc tại `OpenApiConfig.java` bằng khối `static { SpringDocUtils.getConfig().replaceWithSchema(Geometry.class, ...); }`, thay thế toàn bộ bằng schema GeoJSON phẳng trực quan.                                                        | 🟢 Đã Hóa Giải  |
| **RSK-W1-13** | **Tràn Bộ Đệm Log Console (Log Flooding):** Cấu hình `org.hibernate.orm.jdbc.bind: TRACE` ghi ra quá nhiều dòng log khi tải hàng nghìn bản ghi, làm chậm tốc độ terminal. |     **Thấp**     | Thiết lập giới hạn số dòng hiển thị của console hoặc lập trình viên có thể điều chỉnh linh hoạt về `DEBUG` trong `application-dev.yml` khi cần đo đạc hiệu năng cao.                                                                                 | 🟢 Đã Kiểm Soát |
| **RSK-W1-14** | **Lỗi Chặn CORS Khi Gọi Thử Nghiệm Từ Client Ngoài:** Khi lập trình viên mở Swagger từ Postman hoặc trình duyệt khác bị CORS chặn.                                        |  **Trung Bình**  | Lớp `CorsConfig.java` (đã hoàn thiện tại Task W1-08) đã cho phép đầy đủ HTTP methods (GET, POST, PUT, DELETE, OPTIONS) và headers cho các origin dev (`:5173`, `:3000`).                                                                             | 🟢 Đã Kiểm Soát |

---

## 7. Sổ Tay Hướng Dẫn Kỹ Sư Thực Thi Từng Bước (Developer Step-by-Step Runbook)

Kỹ sư Backend thực hiện lần lượt các bước dưới đây để hoàn thiện Task W1-09:

### Bước 1: Kiểm Tra Trạng Thái Hạ Tầng PostGIS Docker Local

Đảm bảo container `weathergis-postgres` đang chạy và sẵn sàng nhận kết nối tại cổng 5432:

```bash
# Kiểm tra container đang chạy
docker ps --filter "name=weathergis-postgres"

# Kiểm tra phản hồi cổng CSDL
nc -zv localhost 5432 || pg_isready -h localhost -p 5432 -U weather_gis_user
```

### Bước 2: Tạo Tệp Cấu Hình `application-dev.yml`

Tạo tệp `backend/src/main/resources/application-dev.yml` với nội dung chi tiết như đã đặc tả tại [Mục 3.1](#31-tệp-cấu-hình-profile-phát-triển-backendsrcmainresourcesapplication-devyml).

### Bước 3: Tạo Lớp Cấu Hình Toàn Cục `OpenApiConfig.java`

Tạo tệp `backend/src/main/java/vn/weathergis/common/config/OpenApiConfig.java` với nội dung như đã đặc tả tại [Mục 3.2](#32-lớp-cấu-hình-toàn-cục-springdoc-openapi-openapiconfigjava).

### Bước 4: Bổ Sung Siêu Dữ Liệu OpenAPI Vào Controller & DTOs

1. Mở `backend/src/main/java/vn/weathergis/location/controller/LocationController.java` và bổ sung các annotations `@Tag`, `@Operation`, `@ApiResponses`, `@Parameter` như tại [Mục 3.3](#33-cập-nhật-bổ-sung-siêu-dữ-liệu-openapi-cho-locationcontrollerjava).
2. Mở `LocationResponse.java`, `LocationNearbyResponse.java`, `ApiResponse.java` và bổ sung các annotations `@Schema` như tại [Mục 3.4](#34-bổ-sung-schema-vào-các-lớp-dto).

### Bước 5: Định Dạng Mã Nguồn Bằng Spotless

Chạy định dạng tự động để mã nguồn tuân thủ 100% chuẩn Google Java Format:

```bash
cd backend
./gradlew spotlessApply
```

### Bước 6: Khởi Động Ứng Dụng Với Profile Dev & Xác Thực Kiểm Định

Khởi chạy máy chủ Backend trên terminal:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Mở một cửa sổ terminal khác và thực hiện chuỗi lệnh kiểm tra tự động:

```bash
# 1. Kiểm tra Actuator Health (Phải trả về status: UP)
curl -s http://localhost:8080/actuator/health | jq .

# 2. Kiểm tra Actuator Flyway (Phải hiển thị 3 migration V1, V2, V3)
curl -s http://localhost:8080/actuator/flyway | jq .

# 3. Kiểm tra OpenAPI JSON Specification
curl -s http://localhost:8080/v3/api-docs | jq .info

# 4. Kiểm tra API Locations định dạng JSON Envelope chuẩn
curl -s "http://localhost:8080/api/v1/locations?search=H%C3%A0%20N%E1%BB%99i" | jq .

# 5. Kiểm tra API Locations định dạng GeoJSON chuẩn RFC 7946 cho Leaflet
curl -s "http://localhost:8080/api/v1/locations?format=geojson" | jq .type
```

### Bước 7: Mở Trình Duyệt Kiểm Tra Swagger UI Trực Quan

Truy cập đường dẫn:

```
http://localhost:8080/swagger-ui.html
```

- Xác nhận trang chuyển hướng mượt mà sang `http://localhost:8080/swagger-ui/index.html`.
- Xác nhận các nhóm API hiển thị trong dropdown chọn nhóm (v1-all, v1-locations, actuator).
- Thử nghiệm trực tiếp nút **Try it out** trên endpoint `GET /api/v1/locations/nearby` với tham số `lng=105.8542`, `lat=21.0285`, `radiusKm=50`.

---

## 8. Danh Mục Sản Phẩm Bàn Giao & Checklist Nghiệm Thu (Acceptance Checklist)

### 8.1. Danh Mục Sản Phẩm Bàn Giao (Deliverables)

| STT | Tên Tệp Sản Phẩm                                                                  | Trạng Thái | Mô Tả Trách Nhiệm Kỹ Thuật                                                             |
| :-: | :-------------------------------------------------------------------------------- | :--------: | :------------------------------------------------------------------------------------- |
|  1  | `backend/src/main/resources/application-dev.yml`                                  |  Tạo mới   | Cấu hình profile dev trỏ vào PostGIS local, bật Flyway, HikariCP, log SQL và Actuator. |
|  2  | `backend/src/main/java/vn/weathergis/common/config/OpenApiConfig.java`            |  Tạo mới   | Cấu hình OpenAPI 3.0, thông tin dự án, servers, nhóm API và xử lý thay thế schema JTS. |
|  3  | `backend/src/main/java/vn/weathergis/location/controller/LocationController.java` |  Cập nhật  | Bổ sung đầy đủ annotations `@Tag`, `@Operation`, `@ApiResponses`, `@Parameter`.        |
|  4  | `backend/src/main/java/vn/weathergis/location/dto/LocationResponse.java`          |  Cập nhật  | Bổ sung annotations `@Schema` cho từng thuộc tính hiển thị ví dụ mẫu.                  |
|  5  | `backend/src/main/java/vn/weathergis/location/dto/LocationNearbyResponse.java`    |  Cập nhật  | Bổ sung annotations `@Schema` cho trường khoảng cách và tọa độ.                        |
|  6  | `backend/src/main/java/vn/weathergis/common/api/ApiResponse.java`                 |  Cập nhật  | Bổ sung annotations `@Schema` cho phong bao REST Envelope.                             |
|  7  | `docs/week1/Task W1-09.md`                                                        |  Tạo mới   | Toàn văn tài liệu đặc tả kỹ thuật và cẩm nang vận hành chi tiết Task W1-09.            |

---

### 8.2. Checklist Tiêu Chí Chấp Nhận (Definition of Done - DoD)

- [ ] **Tệp cấu hình dev:** Tệp `application-dev.yml` hiện diện và được kích hoạt chính xác khi chạy profile `dev`.
- [ ] **Kết nối CSDL:** HikariCP kết nối thành công tới PostgreSQL 16 PostGIS local trên cổng 5432 mà không phát sinh lỗi timeout hay authentication error.
- [ ] **Đồng bộ hóa Flyway:** Flyway tự động quét classpath và đối soát thành công 3 tệp migration `V1`, `V2`, `V3`; lịch sử di trú được phản ánh chính xác tại endpoint `/actuator/flyway`.
- [ ] **Giao diện Swagger UI:** Truy cập thành công `http://localhost:8080/swagger-ui.html`, hiển thị đầy đủ thông tin hệ thống, nhóm API và mô tả chi tiết các tham số.
- [ ] **Thử nghiệm tương tác:** Chức năng "Try it out" trên Swagger UI gọi thành công các endpoint `/api/v1/locations` (cả định dạng JSON và GeoJSON) và `/api/v1/locations/nearby`.
- [ ] **Không vỡ schema JTS:** Không xuất hiện lỗi đệ quy vòng lặp hoặc lỗi parse schema đối với kiểu dữ liệu hình học trắc địa OGC JTS.
- [ ] **Logging chất lượng:** Console hiển thị các câu truy vấn SQL được định dạng thụt lề rõ ràng kèm tham số bind khi gọi API từ Swagger.
- [ ] **Chất lượng mã nguồn:** Toàn bộ mã nguồn Java tuân thủ 100% Spotless Google Java Format (`./gradlew spotlessCheck` trả về `BUILD SUCCESSFUL`).
- [ ] **Kiểm thử hồi quy:** Toàn bộ các bài kiểm thử tự động của Task W1-07 và Task W1-08 tiếp tục PASS 100%.
- [ ] **Chuyển giao:** Sẵn sàng bàn giao đường dẫn Swagger UI cho đội ngũ Frontend bắt đầu triển khai **Task W1-10: Khởi tạo React 18, Vite, TypeScript, Tailwind CSS** và **Task W1-11: Tích hợp Leaflet 1.9**.
