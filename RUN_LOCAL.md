# Chạy VNstormgis end to end trên máy cá nhân

Hướng dẫn này chạy ứng dụng theo luồng **PostGIS/Redis → Spring Boot API → Vite/React → trình duyệt**. Thực hiện các lệnh từ thư mục gốc của repo, trừ nơi ghi rõ `cd`. Cần ba cửa sổ terminal để giữ backend và frontend chạy đồng thời.

## 1. Chuẩn bị

- Docker Desktop đang chạy, có `docker compose`.
- JDK 21 (`java -version`), Node.js 20 hoặc 22 và npm (`node -v`, `npm -v`). Nếu máy có nhiều JDK, chọn JDK 21 qua `JAVA_HOME` trước khi chạy Gradle.
- `curl` để kiểm tra API; `jq` để đọc JSON và chạy các script kiểm định của repo.
- Kết nối mạng ở lần chạy đầu để tải Docker images và dependencies của Gradle/npm.
- Khóa OpenWeatherMap hợp lệ nếu muốn xem **dữ liệu thời tiết thực**. Backend vẫn chạy và 68 trạm vẫn được seed khi chưa có khóa.

Nếu chưa có `.env.local`, tạo cấu hình local một lần:

```bash
cp VNstormgis-Deployment/env/.env.example VNstormgis-Deployment/env/.env.local
```

Mở `VNstormgis-Deployment/env/.env.local` và thay `INTERNAL_SYNC_SECRET` bằng một chuỗi riêng. Điền `OPENWEATHERMAP_API_KEY` hợp lệ để đồng bộ thời tiết; thay `ADMIN_API_KEY` nếu sẽ dùng API quản trị ngưỡng. Giữ `DB_HOST=localhost`, `REDIS_HOST=localhost` khi backend chạy trên máy. Không commit `.env.local` hoặc đưa khóa thật vào mã nguồn.

Profile `dev` của backend tự nạp file này khi lệnh Gradle được chạy **từ `VNstormgis-Backend/`**. Docker Compose không tự tìm file ở đường dẫn đó, nên các lệnh Compose bên dưới luôn có `--env-file`. Nếu một volume PostgreSQL cũ dùng mật khẩu khác, hãy đặt `DB_PASSWORD` khớp với mật khẩu của volume đó; đổi biến môi trường không đổi mật khẩu đã lưu trong database.

## 2. Khởi động PostGIS và Redis (terminal 1)

```bash
docker compose --env-file VNstormgis-Deployment/env/.env.local -f VNstormgis-Deployment/docker-compose.yml up -d
docker inspect --format '{{.State.Health.Status}}' weathergis-postgres weathergis-redis
```

Chờ cả hai dòng đều là `healthy`. Compose chỉ chạy database và cache; backend và frontend chạy ở hai terminal tiếp theo.

## 3. Khởi động backend (terminal 2)

```bash
cd VNstormgis-Backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Lần chạy đầu Gradle có thể tải dependencies. Flyway tự tạo schema, chạy migrations và seed 68 trạm. Chờ log `Started WeatherGisApplication`; giữ terminal này mở. API dùng `http://localhost:8080`, Swagger UI ở `http://localhost:8080/swagger-ui.html`.

Trên macOS, nếu `java -version` đang hiện bản khác nhưng JDK 21 đã cài, chạy `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` trong terminal 2 trước lệnh Gradle.

Từ một terminal khác, kiểm tra API:

```bash
curl -fsS http://localhost:8080/actuator/health | jq -r '.status'
curl -fsS 'http://localhost:8080/api/v1/locations?format=json' | jq '.data | length'
```

Kết quả mong đợi là `UP` và `68` trên database mới. Nếu database đã có dữ liệu riêng, số trạm có thể khác.

## 4. Đồng bộ dữ liệu thực (terminal 1)

Backend tự đồng bộ theo lịch (thời tiết sau khoảng 20 giây, bão sau khoảng 60 giây từ lúc khởi động). Để chạy ngay và xem kết quả, nạp secret vào **terminal gọi `curl`** rồi gọi endpoint nội bộ:

```bash
set -a
source VNstormgis-Deployment/env/.env.local
set +a
curl -fsS -X POST -H "X-Internal-Secret: ${INTERNAL_SYNC_SECRET}" http://localhost:8080/api/v1/internal/sync/weather | jq '.data | {status, recordsProcessed, totalStations, errorMessage}'
curl -fsS -X POST -H "X-Internal-Secret: ${INTERNAL_SYNC_SECRET}" http://localhost:8080/api/v1/internal/sync/storms | jq '.data | {status, stormsProcessed, stormsOnFeed, errorMessage}'
```

Nếu lịch đồng bộ đang chạy, endpoint có thể trả `409`; chờ lượt đó xong rồi thử lại. Đồng bộ thời tiết cần khóa OpenWeatherMap hợp lệ và truy cập được API bên ngoài. Nguồn GDACS có thể không có bão đang hoạt động, nên `stormsProcessed=0` không tự nó là lỗi.

Kiểm tra dữ liệu API mà bản đồ đọc:

```bash
curl -fsS http://localhost:8080/api/v1/weather/current | jq '{type, count: (.features | length)}'
curl -fsS http://localhost:8080/api/v1/storms | jq '{type, count: (.features | length)}'
curl -fsS http://localhost:8080/api/v1/alerts/active | jq '{type, count: (.features | length)}'
```

Thời tiết `count > 0` xác nhận đã có bản ghi thực. Danh sách bão hoặc cảnh báo rỗng là hợp lệ nếu hiện không có bão hay ngưỡng bị vượt. Cảnh báo được tạo từ dữ liệu vi phạm ngưỡng; khi Gemini chưa bật, backend có thể dùng nội dung fallback.

## 5. Khởi động frontend và kiểm tra trên trình duyệt (terminal 3)

```bash
cd VNstormgis-Frontend
npm ci
npm run dev
```

Chỉ cần `npm ci` ở lần cài đầu hoặc khi lockfile thay đổi.

Mở URL Vite in trên terminal (thường là `http://localhost:5173`). Vite chuyển `/api` và `/actuator` đến backend cổng 8080.

1. Mở **KIỂM ĐỊNH (DIAGNOSTICS)**: phải thấy `SPRING BOOT 3.3 ACTIVE` và số trạm PostGIS.
2. Mở **TÁC CHIẾN (GIS OPS)**: xem các lớp trạm và thời tiết; bấm **CẢNH BÁO** để xem danh sách cảnh báo thực hoặc trạng thái rỗng.
3. Nếu có banner lỗi backend, bấm **LÀM MỚI KẾT NỐI** sau khi sửa lỗi. Chỉ nhìn thấy marker chưa chứng minh kết nối thành công, vì UI có dữ liệu trạm mẫu khi tải API thất bại.

Ngăn chat AI trên frontend hiện là giao diện mô phỏng, chưa gọi Gemini. `GEMINI_ENABLED=true`, `GEMINI_API_KEY` và `GEMINI_MODEL` chỉ dùng cho luồng tạo nội dung cảnh báo của backend khi được cấu hình.

## 6. Kiểm định và dừng ứng dụng

Kiểm tra mã nguồn, từ thư mục gốc (backend tests cần Docker/Testcontainers):

```bash
(cd VNstormgis-Backend && ./gradlew spotlessCheck test)
(cd VNstormgis-Frontend && npm run type-check && npm run lint && npm run build)
```

Sau khi đã có dữ liệu thời tiết, có thể chạy `bash scripts/verify-week3-pipeline.sh` để kiểm định tích hợp nghiêm ngặt. Script này kỳ vọng 68 trạm và tạm dừng/khởi động lại Redis để kiểm tra phục hồi; không dùng làm smoke test đầu tiên trên database trống.
`bash scripts/verify-week2-pipeline.sh` cũng gọi các API đồng bộ thực và cần `INTERNAL_SYNC_SECRET` đã export cùng khóa OpenWeatherMap hợp lệ.

Để dừng: nhấn `Ctrl+C` ở terminal frontend và backend, rồi từ thư mục gốc chạy:

```bash
docker compose --env-file VNstormgis-Deployment/env/.env.local -f VNstormgis-Deployment/docker-compose.yml stop
```

`stop` giữ lại dữ liệu trong Docker volumes để lần sau tiếp tục. Nếu cổng `5432`, `6379` hoặc `8080` đã bị chiếm, giải phóng cổng hoặc chỉnh cấu hình tương ứng trước khi chạy. Backend và Vite proxy hiện cùng dùng cổng 8080; biến `SERVER_PORT` và `VITE_API_BASE_URL` trong file mẫu chưa đổi hai cấu hình này.

## 7. Nếu chưa thấy dữ liệu

- Container chưa `healthy`: kiểm tra `docker compose --env-file VNstormgis-Deployment/env/.env.local -f VNstormgis-Deployment/docker-compose.yml ps` và log của `db`/`redis`. Nếu PostgreSQL báo sai mật khẩu trên volume cũ, đối chiếu lại `DB_PASSWORD` với mật khẩu đã dùng khi tạo volume.
- Đồng bộ trả `401`: `INTERNAL_SYNC_SECRET` trong shell gọi `curl` phải giống file backend đã nạp. Nếu vừa sửa file, khởi động lại backend rồi `source` lại file ở terminal 1.
- `weather/current` có `count=0`: xem `status` và `errorMessage` từ lệnh đồng bộ; kiểm tra khóa OpenWeatherMap và kết nối mạng.
- UI báo `BACKEND OFFLINE`: xác nhận backend còn chạy tại `http://localhost:8080/actuator/health`, rồi bấm **LÀM MỚI KẾT NỐI**. Dùng đúng URL mà Vite in ra nếu cổng 5173 đang bị chiếm.

## Week 4 alert verification and recovery

Dùng Java 21, PostGIS/Redis như trên. `GET /api/v1/alert-thresholds`, `GET /api/v1/alerts/active`, `GET /api/v1/alerts/{id}` là public; POST/PUT/PATCH/DELETE threshold cần `X-Admin-Api-Key` từ `ADMIN_API_KEY`. `GEMINI_ENABLED=false` (mặc định) cho phép fallback offline; chỉ bật live khi đã cấu hình `GEMINI_API_KEY` và `GEMINI_MODEL` qua môi trường. Model mặc định rỗng; lần smoke `gemini-3.1-flash-lite` ngày 2026-11-07 không phải default. Swagger UI: `/swagger-ui/index.html`; OpenAPI: `/v3/api-docs`. Không dùng backend cũ còn chạy để đại diện cho jar vừa build.

Local gate: `bash scripts/verify-week4-pipeline.sh` với `BACKEND_BASE_URL` (mặc định `http://localhost:8080`), `ARTIFACT_DIR` và `WAIT_TIMEOUT_SECONDS` tùy chọn. Xem `summary.json` trước, rồi Gradle log/JUnit XML. Script chỉ đọc live API; generation/fallback được chứng minh bằng fixture Testcontainers/WireMock, không bởi active collection rỗng. Xem `scripts/W4-08-RUNBOOK.md` cho lịch, retention, webhook và dispatch.

Khi job chậm, kiểm `alert_generation_jobs` theo `status`, `last_error`, `lease_until`, `quarantine_until`, `next_attempt_at` trên môi trường kiểm thử cô lập và kiểm worker/retry/DB. PENDING dùng lease/token fencing; READY dùng publication retry/backoff. Chỉ phục hồi hạ tầng rồi để scheduler chạy hoặc chứng minh trên fixture cô lập; không xóa key chống trùng, sửa snapshot hay sửa job thủ công. Quy trình xử lý dữ kiện bất hợp lệ vĩnh viễn và giới hạn 4000 ký tự còn chờ AI/Backend + Tech Lead quyết định.
