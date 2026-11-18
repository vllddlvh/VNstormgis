# Hướng dẫn chạy VNstormgis End-to-End trên máy cá nhân

Tài liệu này hướng dẫn chi tiết từng bước để khởi chạy toàn bộ hệ thống **VNstormgis** (Hệ thống bản đồ theo dõi thời tiết & cảnh báo bão lũ Việt Nam) trên máy cá nhân của bạn.

---

## 1. Tổng quan luồng hoạt động

Khi chạy đầy đủ (End-to-End), hệ thống gồm 4 thành phần kết nối với nhau theo luồng:

1. **Cơ sở dữ liệu, Bộ nhớ đệm & Hộp thư cục bộ (Docker):**
   - **PostgreSQL 16 + PostGIS 3.4** (cổng `5432`): Lưu trữ tọa độ 39 điểm quan trắc đang hoạt động, lịch sử thời tiết, quỹ đạo bão, cảnh báo, đăng ký email và lịch sử hội thoại.
   - **Redis 7.2** (cổng `6379`): Lưu bộ nhớ đệm (cache) cho bản đồ và kiểm soát giới hạn tần suất hỏi đáp AI (Rate Limit).
   - **Mailpit** (SMTP cổng `1025`, giao diện Web cổng `8025`): Hộp thư thử nghiệm cục bộ giúp bạn nhận mã xác thực OTP 6 chữ số, liên kết 1-click và email cảnh báo bão lũ ngay tại `http://localhost:8025` mà không cần cấu hình Gmail thật.
2. **Máy chủ Backend (Spring Boot 3.3 / Java 21 — cổng `8080`):**
   - Tự động tạo bảng, nạp sẵn 39 điểm quan trắc (qua Flyway), đồng bộ dữ liệu thời tiết từ **OpenWeatherMap** và dữ liệu bão quốc tế từ **GDACS**, phát sinh cảnh báo và cung cấp REST API / GeoJSON.
3. **Giao diện Frontend (React 18 + Vite 5 — cổng `5173`):**
   - Hiển thị bản đồ số tương tác (Leaflet), các lớp mưa/gió/bão, bảng cảnh báo và Trợ lý AI thời tiết. Mọi yêu cầu `/api` và `/actuator` từ cổng `5173` được Vite tự động chuyển tiếp sang Backend ở cổng `8080`.

> **Lưu ý:** Tất cả các lệnh dưới đây đều được chạy từ **thư mục gốc của dự án (`VNstormgis/`)**, trừ những bước có ghi rõ lệnh `cd`.

---

## 2. Chuẩn bị công cụ cần thiết

Trước khi bắt đầu, hãy đảm bảo máy của bạn đã cài đặt:

- **Docker Desktop**: Đang bật và hoạt động (`docker info` và `docker compose version` chạy không báo lỗi).
- **Java JDK 21**: Kiểm tra bằng lệnh `java -version`.
  - *Mẹo trên macOS:* Nếu máy cài nhiều phiên bản Java, bạn có thể chuyển nhanh sang JDK 21 trong cửa sổ Terminal bằng lệnh:
    ```bash
    export JAVA_HOME=$(/usr/libexec/java_home -v 21)
    ```
- **Node.js (phiên bản 20 hoặc 22) & npm**: Kiểm tra bằng `node -v` và `npm -v`.
- **`curl` và `jq`**: Dùng để kiểm tra nhanh phản hồi API trên Terminal (`brew install jq` trên macOS hoặc `sudo apt install jq` trên Ubuntu).
- **API Key của OpenWeatherMap** *(khuyên dùng)*: Đăng ký miễn phí tại [openweathermap.org](https://openweathermap.org/api) để lấy dữ liệu thời tiết thực tế của 39 điểm quan trắc. Nếu chưa có khóa này, hệ thống vẫn khởi động được và hiển thị danh mục điểm quan trắc trên bản đồ.

---

## 3. Tạo file cấu hình môi trường (`.env.local`)

Ở lần chạy đầu tiên, hãy tạo file cấu hình cục bộ từ file mẫu:

```bash
cp VNstormgis-Deployment/env/.env.example VNstormgis-Deployment/env/.env.local
```

Mở file `VNstormgis-Deployment/env/.env.local` bằng trình soạn thảo bất kỳ và kiểm tra/cập nhật các thông số sau:

| Biến môi trường | Ý nghĩa & Cách điền |
| :--- | :--- |
| `DB_HOST` / `REDIS_HOST` | Giữ nguyên là `localhost` khi chạy Backend trực tiếp trên máy. |
| `DB_PASSWORD` | Mật khẩu cơ sở dữ liệu local (mặc định trong file mẫu đã có sẵn). |
| `INTERNAL_SYNC_SECRET` | Đặt một chuỗi bí mật bất kỳ (ví dụ: `my-local-sync-secret-2026`) để gọi lệnh đồng bộ dữ liệu thủ công. |
| `OPENWEATHERMAP_API_KEY` | Điền API Key OpenWeatherMap của bạn để tải số liệu mưa, gió, nhiệt độ thực tế. |
| `ADMIN_API_KEY` | Chuỗi khóa quản trị để thêm/sửa/xóa ngưỡng cảnh báo qua API (có thể đặt chuỗi bất kỳ). |
| `GEMINI_ENABLED` | Mặc định là `false`. Khi để `false`, cả tính năng **Cảnh báo** và **Trợ lý AI** vẫn hoạt động đầy đủ bằng cơ chế tự động tổng hợp tiếng Việt trực tiếp từ dữ liệu trạm đo trong PostGIS. Nếu muốn dùng mô hình Gemini thật, đổi thành `true` và điền thêm `GEMINI_API_KEY` cùng `GEMINI_MODEL` (ví dụ `gemini-2.5-flash`). |

> **Bảo mật:** File `.env.local` đã được cấu hình trong `.gitignore`, tuyệt đối không commit file chứa khóa thật lên Git.

---

## 4. Chạy hệ thống End-to-End (3 cửa sổ Terminal)

Đây là cách chạy thuận tiện nhất trên máy cá nhân để vừa theo dõi log trực tiếp vừa thao tác nhanh.

### Bước 1 — Khởi động PostGIS và Redis (Terminal 1)

Chạy lệnh sau từ thư mục gốc của dự án:

```bash
docker compose --env-file VNstormgis-Deployment/env/.env.local -f VNstormgis-Deployment/docker-compose.yml up -d
```

Kiểm tra trạng thái của 2 container:

```bash
docker inspect --format '{{.Name}}: {{.State.Health.Status}}' weathergis-postgres weathergis-redis
```

Đợi vài giây cho đến khi cả hai đều hiển thị là **`healthy`**.

---

### Bước 2 — Khởi động máy chủ Backend (Terminal 2)

Mở cửa sổ **Terminal thứ 2**, chuyển vào thư mục `VNstormgis-Backend` và chạy ứng dụng với cấu hình `dev`:

```bash
cd VNstormgis-Backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21) # Chỉ cần trên macOS nếu mặc định chưa phải JDK 21
./gradlew bootRun --args='--spring.profiles.active=dev'
```

- Lần chạy đầu tiên Gradle sẽ tải các thư viện cần thiết (mất khoảng 1–2 phút).
- Khi khởi động, Flyway sẽ tự động tạo toàn bộ bảng dữ liệu và thiết lập **39 điểm quan trắc đang hoạt động** gồm 34 điểm đại diện đơn vị cấp tỉnh và 5 điểm hải đảo, bao gồm Hoàng Sa và Trường Sa. Migration V11 giữ điểm cũ ở trạng thái ngừng theo dõi để tra cứu lịch sử.
- Khi thấy dòng log **`Started WeatherGisApplication`**, máy chủ đã sẵn sàng tại `http://localhost:8080`. Giữ nguyên cửa sổ Terminal 2 này đang chạy.

Bạn có thể quay lại **Terminal 1** để kiểm tra nhanh xem Backend đã phản hồi chưa:

```bash
curl -fsS http://localhost:8080/actuator/health | jq -r '.status'
curl -fsS 'http://localhost:8080/api/v1/locations?format=json' | jq '.data | length'
```

Kết quả `UP` và `39` xác nhận backend sẵn sàng và API trả đủ danh mục điểm quan trắc đang hoạt động.
- Tài liệu Swagger UI trực quan có thể xem tại: `http://localhost:8080/swagger-ui.html`.

---

### Bước 3 — Đồng bộ dữ liệu thời tiết & bão thực tế (Terminal 1)

Sau khi khởi động, Backend có bộ lập lịch tự động tải dữ liệu thời tiết (sau ~20 giây) và dữ liệu bão (sau ~60 giây). Tuy nhiên, bạn có thể kích hoạt đồng bộ ngay lập tức tại **Terminal 1** bằng các lệnh sau:

```bash
# Nạp biến INTERNAL_SYNC_SECRET từ file .env.local vào Terminal hiện tại
set -a
source VNstormgis-Deployment/env/.env.local
set +a

# 1. Kích hoạt đồng bộ thời tiết từ OpenWeatherMap cho 39 điểm quan trắc
curl -fsS -X POST -H "X-Internal-Secret: ${INTERNAL_SYNC_SECRET}" \
  http://localhost:8080/api/v1/internal/sync/weather | jq '.data | {status, recordsProcessed, totalStations, errorMessage}'

# 2. Kích hoạt đồng bộ thông tin bão từ hệ thống cảnh báo quốc tế GDACS
curl -fsS -X POST -H "X-Internal-Secret: ${INTERNAL_SYNC_SECRET}" \
  http://localhost:8080/api/v1/internal/sync/storms | jq '.data | {status, stormsProcessed, stormsOnFeed, errorMessage}'
```

> **Lưu ý:**
> - Nếu hệ thống báo mã lỗi `409`, nghĩa là tiến trình tự động của Backend đang chạy đồng bộ rồi; bạn chỉ cần đợi khoảng 15–20 giây là xong.
> - Nếu hiện tại trên thế giới/Biển Đông không có cơn bão nhiệt đới nào đang hoạt động trên nguồn GDACS, `stormsProcessed: 0` là hoàn toàn bình thường.

Kiểm tra dữ liệu GeoJSON mà bản đồ sẽ hiển thị:

```bash
curl -fsS http://localhost:8080/api/v1/weather/current | jq '{type, count: (.features | length)}'
curl -fsS http://localhost:8080/api/v1/storms | jq '{type, count: (.features | length)}'
curl -fsS http://localhost:8080/api/v1/alerts/active | jq '{type, count: (.features | length)}'
```

---

### Bước 4 — Khởi động giao diện Frontend (Terminal 3)

Mở cửa sổ **Terminal thứ 3**, chuyển vào thư mục `VNstormgis-Frontend` và chạy lệnh:

```bash
cd VNstormgis-Frontend
npm ci        # Chỉ cần chạy ở lần đầu tiên hoặc khi cập nhật thư viện
npm run dev
```

Mở trình duyệt web và truy cập địa chỉ hiển thị trên Terminal (thường là **`http://localhost:5173`**).

---

## 5. Trải nghiệm và kiểm tra các tính năng trên trình duyệt

Khi mở `http://localhost:5173`, bạn có thể kiểm tra toàn bộ luồng tính năng:

1. **Thanh trạng thái góc trên bên phải:**
   - Hiển thị **`MÁY CHỦ HOẠT ĐỘNG | 39 TRẠM ĐO`**.
   - Nếu thấy thông báo mất kết nối máy chủ, hãy nhấn nút **`LÀM MỚI KẾT NỐI`** sau khi Backend đã khởi động xong.
2. **Bảng `LỚP HIỂN THỊ BẢN ĐỒ` (góc trên bên phải):**
   - **Kiểu nền bản đồ:** Chuyển đổi giữa *Tiêu chuẩn*, *Bản dự phòng* và *Nền tối* (giúp nhìn rõ vùng mưa và mũi tên gió).
   - **Ranh giới lãnh thổ:** Bật/tắt đường biên giới đất liền, vùng biển Việt Nam và vùng theo dõi hai quần đảo Hoàng Sa, Trường Sa.
   - **Các lớp dữ liệu thời tiết:** Bật/tắt *Lượng mưa tại trạm*, *Trạm đo thời tiết*, *Bão & Áp thấp nhiệt đới*, *Vùng mưa lớn (Radar)*, *Hướng gió & Sức gió*, *Lớp mây vệ tinh*.
   - **Lọc trạm theo vùng:** Lọc nhanh các trạm thuộc *Bắc Bộ*, *Trung Bộ*, *Tây Nguyên*, *Nam Bộ* hoặc *Biển Đảo*.
3. **Xem chi tiết từng trạm đo & Biểu đồ lịch sử 24h:**
   - Nhấp vào bất kỳ biểu tượng trạm nào trên bản đồ (ví dụ `HAN`, `DNG`, `SGN`, `HSA`, `TSA`) để xem nhiệt độ, lượng mưa 1 giờ/3 giờ, tốc độ gió, độ ẩm, áp suất.
   - Nhấn **`🔍 PHÓNG TO`** để đưa bản đồ lại gần trạm, **`🤖 HỎI AI`** để hỏi trợ lý về trạm đó, **`📈 LỊCH SỬ 24H`** để mở biểu đồ diễn biến mưa/gió/nhiệt độ 24h–7 ngày, hoặc **`✉️ BÁO EMAIL`** để đăng ký nhận cảnh báo cho khu vực này.
4. **Tìm kiếm nhanh Tỉnh/Trạm (`Ctrl+K` / nút `TÌM TRẠM`) & `VỊ TRÍ CỦA TÔI`:**
   - Nhấn **`Ctrl+K`** (hoặc `Cmd+K` trên macOS / nút **`TÌM TRẠM`** trên thanh đầu trang) để tìm nhanh tỉnh/thành có dấu hoặc không dấu (`da nang`, `truong sa`, `HAN`).
   - Nhấn **`VỊ TRÍ CỦA TÔI`** để định vị GPS trình duyệt (hoặc nhập tọa độ tùy chỉnh), xem phân tích không gian PostGIS trực tiếp (trạm gần nhất, thời tiết hiện tại, khoảng cách tới bão active và cảnh báo trong bán kính 100 km).
5. **Đăng ký nhận thông báo cảnh báo qua Email (`NHẬN EMAIL`):**
   - Chọn giám sát theo **Tỉnh / Trạm đo** hoặc **Tọa độ GPS**, cấu hình bán kính (25–200 km) và mức độ tối thiểu (`INFO` → `CRITICAL`).
   - Mở **`http://localhost:8025`** (hộp thư Mailpit local) để lấy mã OTP 6 chữ số (hoặc bấm liên kết xác thực 1-click trong email), sau đó có thể nhấn **`GỬI EMAIL KIỂM TRA NGAY`** để nhận bản tin đối chứng số liệu thực tế PostGIS.
6. **Bảng `CẢNH BÁO` (nút góc trên bên trái bản đồ) & Thanh tin chạy bên dưới:**
   - Xem danh sách các cảnh báo đang hoạt động khi có chỉ số thời tiết vượt ngưỡng.
   - Nhấp vào một cảnh báo để xem chi tiết số liệu đo thực tế lúc phát hiện, nhấn **`XEM VỊ TRÍ`** để bay tới trạm đó, hoặc **`HỎI AI VỀ CẢNH BÁO NÀY`**.
7. **Ngăn `TRỢ LÝ AI` (nút góc dưới bên phải bản đồ):**
   - Chọn một câu hỏi gợi ý sẵn (ví dụ: *"Khu vực nào đang mưa lớn nhất?"*, *"Trạm nào đang có gió mạnh nhất?"*) hoặc nhập câu hỏi bất kỳ bằng tiếng Việt.
   - Hệ thống tự động tạo phiên hội thoại ẩn danh (lưu trong `localStorage`) và trả lời dựa trên số liệu thực tế từ cơ sở dữ liệu.
8. **Tab `TRẠNG THÁI HỆ THỐNG` (thanh điều hướng trên cùng):**
   - Kiểm tra tình trạng kết nối API, xem 5 trạm đo mẫu trả về từ máy chủ, và trải nghiệm **Bản đồ mẫu** minh họa đầy đủ 6 cấp độ cảnh báo trạm đo cùng đường đi lịch sử/dự báo của bão.

---

## 6. Tuỳ chọn: Chạy trọn bộ bằng 1 lệnh Docker Compose (`fullstack`)

Nếu bạn muốn kiểm tra chế độ đóng gói sản phẩm hoàn chỉnh (gồm 4 container: `db`, `redis`, `backend`, và `web` Nginx chạy ở cổng `80`), hãy tắt `bootRun` và `npm run dev` rồi chạy:

```bash
docker compose --profile fullstack --env-file VNstormgis-Deployment/env/.env.local -f VNstormgis-Deployment/docker-compose.yml up -d --build
```

Sau khi build xong, mở trình duyệt tại địa chỉ **`http://localhost`** (cổng 80).

Để tắt chế độ `fullstack`:

```bash
docker compose --profile fullstack --env-file VNstormgis-Deployment/env/.env.local -f VNstormgis-Deployment/docker-compose.yml down
```

---

## 7. Chạy kiểm thử chất lượng mã nguồn (Quality Gate)

Để kiểm tra toàn bộ định dạng mã nguồn, Unit/Integration Test (Backend cần Docker đang chạy để khởi tạo Testcontainers) và kiểm tra kiểu dữ liệu/build của Frontend, chạy lệnh sau từ thư mục gốc:

```bash
(cd VNstormgis-Backend && ./gradlew spotlessCheck test) && \
  (cd VNstormgis-Frontend && npm run type-check && npm run lint && npm run build)
```

Ngoài ra, dự án có sẵn các kịch bản kiểm định tự động theo từng phần (chạy khi Backend đang bật ở cổng `8080`):

- Kiểm định luồng đồng bộ thời tiết & PostGIS: `bash scripts/verify-week2-pipeline.sh`
- Kiểm định luồng bão, Redis Cache & khả năng chịu lỗi: `bash scripts/verify-week3-pipeline.sh`
- Kiểm định luồng cảnh báo tự động: `bash scripts/verify-week4-pipeline.sh`
- Kiểm định luồng Trợ lý AI & giới hạn tần suất (Rate Limit): `bash .github/scripts/verify-week5-pipeline.sh`
- Kiểm định tổng hợp, chịu tải 500 người dùng đồng thời (500 CCU) & bảo mật: `bash .github/scripts/verify-week6-pipeline.sh`
- Kiểm định tổng thể Go-Live (Tuần 7, bao gồm sao lưu/phục hồi DR & Email Subscription): `bash .github/scripts/verify-week7-golive.sh`
- Xem hướng dẫn triển khai Production & vận hành chi tiết tại [OPERATIONS.md](OPERATIONS.md).

---

## 8. Dừng ứng dụng và Xử lý sự cố thường gặp

### Cách dừng ứng dụng
1. Nhấn `Ctrl + C` tại cửa sổ Terminal đang chạy **Frontend** (Terminal 3) và **Backend** (Terminal 2).
2. Dừng các container cơ sở dữ liệu (vẫn giữ nguyên dữ liệu đã lưu cho lần chạy sau):
   ```bash
   docker compose --env-file VNstormgis-Deployment/env/.env.local -f VNstormgis-Deployment/docker-compose.yml stop
   ```

### Xử lý sự cố thường gặp (Troubleshooting)

- **Lỗi `UnsupportedClassVersionError` hoặc báo sai phiên bản Java khi chạy `./gradlew`:**
  - Nguyên nhân: Terminal đang dùng Java cũ (ví dụ Java 8, 11 hoặc 17) thay vì JDK 21.
  - Cách sửa: Chạy `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` trước khi gọi `./gradlew`.
- **Backend không kết nối được PostgreSQL do sai mật khẩu (`password authentication failed`):**
  - Nguyên nhân: Docker volume từ lần chạy cũ đã được khởi tạo với một mật khẩu khác với `DB_PASSWORD` hiện tại trong `.env.local`.
  - Cách sửa: Chỉnh lại `DB_PASSWORD` trong `.env.local` cho khớp, hoặc xóa volume cũ để tạo mới từ đầu bằng lệnh:
    ```bash
    docker compose --env-file VNstormgis-Deployment/env/.env.local -f VNstormgis-Deployment/docker-compose.yml down -v
    ```
- **Gọi lệnh đồng bộ (`/api/v1/internal/sync/...`) bị báo lỗi `401 Unauthorized`:**
  - Nguyên nhân: Giá trị `INTERNAL_SYNC_SECRET` trong cửa sổ Terminal gọi `curl` chưa khớp với giá trị mà Backend đã nạp lúc khởi động.
  - Cách sửa: Nếu bạn vừa sửa `.env.local`, hãy khởi động lại Backend ở Terminal 2 và chạy lại `set -a && source VNstormgis-Deployment/env/.env.local && set +a` ở Terminal 1.
- **Bản đồ hiển thị thông báo `MẤT KẾT NỐI MÁY CHỦ`:**
  - Kiểm tra xem `http://localhost:8080/actuator/health` đã trả về `{"status":"UP"}` chưa, sau đó bấm nút **`LÀM MỚI KẾT NỐI`** trên giao diện web.
- **Số liệu thời tiết trên bản đồ hiển thị `Chưa có số liệu thời tiết mới`:**
  - Kiểm tra xem bạn đã điền `OPENWEATHERMAP_API_KEY` hợp lệ vào `VNstormgis-Deployment/env/.env.local` chưa, sau đó chạy lại lệnh đồng bộ thời tiết ở **Bước 3**.
