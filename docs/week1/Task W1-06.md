# ĐẶC TẢ KỸ THUẬT VÀ HƯỚNG DẪN THỰC THI CHI TIẾT

## TASK W1-06: FLYWAY V3 — SEED 63 TỈNH THÀNH & TRẠM KHÍ TƯỢNG HẢI ĐẢO WGS84

---

## Thông Tin Nhiệm Vụ (Task Metadata)

| Thuộc Tính (Attribute)         | Giá Trị Chi Tiết (Specification)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| :----------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Mã Nhiệm Vụ (Task ID)**      | **W1-06**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| **Tên Nhiệm Vụ (Task Name)**   | Nạp Dữ Liệu Tọa Độ Trắc Địa Chuẩn WGS84 Của 63 Tỉnh Thành & Trạm Khí Tượng Tiền Tiêu Hải Đảo Qua Tệp Di Chuyển Lược Đồ Flyway V3                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| **Epic / Giai Đoạn**           | Sprint 1 (Tuần 1 — Tuần 2) — **Giai đoạn: Foundation & Baseline Infrastructure**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| **Lịch Trình Thực Hiện**       | **Ngày 2 (Thứ Ba)** — Khung giờ: 15:30 — 17:30 (và hoàn thiện kiểm định bàn giao)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| **Điểm Nỗ Lực (Story Points)** | **3 SP** (Ước tính tiêu chuẩn: 6 — 8 giờ công kỹ sư)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Mức Độ Ưu Tiên (Priority)**  | **P0 — Blocker Dữ Liệu Nền Tảng Toàn Hệ Thống** (Bắt buộc hoàn tất 100% PASS để tầng Backend Domain `location` tại Task W1-08, Background Ingestion Engine OpenWeatherMap tại Tuần 2, và giao diện bản đồ React Leaflet tại Task W1-12/W1-14 có dữ liệu thực tế để truy vấn, kiểm thử và hiển thị)                                                                                                                                                                                                                                                                                                                                                                                                           |
| **Người Chịu Trách Nhiệm (R)** | **GIS / Database Specialist & Backend Engineer**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| **Người Hỗ Trợ Kỹ Thuật (C)**  | Senior Backend Engineer, QA Engineer                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Người Phê Duyệt (A)**        | **Technical Lead / Software Architect / Project Manager**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| **Bên Được Thông Báo (I)**     | Toàn thể đội ngũ phát triển dự án (Backend, Frontend, QA, Product Owner)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| **Tài Liệu Căn Cứ Tham Chiếu** | 1. [1. ERD.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/1.%20ERD.md#L360)<br>2. [2. API docs.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/2.%20API%20docs.md#L185)<br>3. [3. BRD.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/3.%20BRD.md)<br>4. [4. SRS.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/4.%20SRS.md#L572)<br>5. [6. Technical Stack.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/6.%20Technical%20Stack.md#L483)<br>6. [PROGRESS.md](file:///Users/dllv/Documents/GitHub/VNstormgis/PROGRESS.md) |

---

## 1. Bối Cảnh & Mục Tiêu Kỹ Thuật (Context & Objectives)

### 1.1. Bối Cảnh Nghiệp Vụ & Nhu Cầu Giám Sát Thiên Tai Khí Tượng

Hệ thống **WebGIS Giám Sát Mưa Gió Bão Tích Hợp AI Agent Cảnh Báo (`VNstormgis`)** là giải pháp công nghệ thời gian thực hỗ trợ theo dõi diễn biến thời tiết khắc nghiệt, bão nhiệt đới và áp thấp nhiệt đới trên toàn bộ không phận, đất liền và hải phận Việt Nam:

1. **Tính Cốt Lõi Của Thực Thể Vị Trí (`locations`):**  
   Mọi luồng dữ liệu thời tiết (chuỗi thời gian quan trắc `weather_records` thu thập từ OpenWeatherMap API), cảnh báo chủ động (`alerts`), và phép toán giao cắt không gian với tâm bão GDACS (`storm_tracks`) đều neo vào thực thể `locations`. Bảng `locations` rỗng đồng nghĩa toàn bộ hệ thống bị tê liệt hoàn toàn.
2. **Đặc Thù Địa Lý Thiên Tai Việt Nam:**
   - **Đồng Bằng & Ven Biển Bắc Bộ:** Trọng điểm hứng chịu các cơn bão đổ bộ từ Vịnh Bắc Bộ (ví dụ: bão Yagi 2024), kèm triều cường và gió giật mạnh.
   - **Trung Du & Miền Núi Phía Bắc:** Địa hình chia cắt hiểm trở, nguy cơ cực cao về lũ quét, sạt lở đất khi lượng mưa tích lũy 3 giờ vượt 50mm.
   - **Bắc Trung Bộ & Duyên Hải Nam Trung Bộ:** Eo dải đất hẹp thường xuyên là tâm điểm đổ bộ trực tiếp của các cơn bão lớn từ Biển Đông, chịu lũ lụt dâng cao và sóng biển tàn phá.
   - **Tây Nguyên:** Địa hình đồi núi dốc, nguy cơ sạt lở đất nghiêm trọng và lũ ống cục bộ.
   - **Đông Nam Bộ & Đồng Bằng Sông Cửu Long:** Chịu tác động kép của triều cường kết hợp mưa lớn gây ngập úng đô thị, sạt lở bờ sông bờ biển và xâm nhập mặn.
   - **Hải Đảo & Quần Đảo Tiền Tiêu (Bạch Long Vĩ, Hoàng Sa, Trường Sa, Côn Đảo, Phú Quốc):** Các "mắt thần" trắc địa tiền tiêu đón đầu các cơn bão xa ngoài khơi Biển Đông trước khi áp sát đất liền 24 — 72 giờ.

Sau khi hoàn tất **Task W1-04** (Kích hoạt phần mở rộng PostGIS & UUID) và **Task W1-05** (Khởi tạo DDL bảng `locations` kèm chỉ mục GiST `idx_locations_geom`), cơ sở dữ liệu đã có lược đồ chuẩn mực nhưng chưa có bất kỳ dữ liệu nào (`count = 0`). **Task W1-06** là mắt xích quyết định để nạp đầy đủ tập dữ liệu trắc địa hạt nhân này.

### 1.2. Mục Tiêu Trọng Tâm Của Task W1-06

Task W1-06 tập trung giải quyết 6 mục tiêu kỹ thuật định lượng:

1. **Khởi tạo tệp di chuyển dữ liệu Flyway V3:** Soạn thảo `backend/src/main/resources/db/migration/V3__seed_vietnam_locations.sql` tuân thủ quy chuẩn kiểm soát phiên bản CSDL bất biến.
2. **Nạp đủ 68 điểm trắc địa quốc gia chuẩn WGS84:**
   - **63 Đơn vị Hành chính Cấp Tỉnh/Thành phố:** Đại diện cho toàn bộ 63 tỉnh, thành phố trực thuộc Trung ương của Việt Nam.
   - **5 Trạm Hải Đảo Tiền Tiêu Trọng Yếu:** Bạch Long Vĩ, Hoàng Sa, Trường Sa, Côn Đảo, Phú Quốc nhằm bảo đảm năng lực cảnh báo bão sớm từ xa ngoài Biển Đông.
3. **Triệt tiêu hoàn toàn rủi ro đảo ngược tọa độ (RSK-W1-05):** Bảo đảm $100\%$ tọa độ được khởi tạo theo chuẩn PostGIS OGC `ST_SetSRID(ST_MakePoint(Longitude, Latitude), 4326)` với Kinh độ $X \in [102.1^\circ E, 114.5^\circ E]$ và Vĩ độ $Y \in [8.5^\circ N, 23.4^\circ N]$.
4. **Bảo đảm tính Lũy Đẳng Tuyệt Đối (Strict Idempotency):** Áp dụng mệnh đề `ON CONFLICT (code) DO UPDATE` để script có thể thực thi an toàn nhiều lần trên mọi môi trường (Local, Testcontainers, CI/CD, Staging, Production) mà không phát sinh lỗi trùng lặp khóa chính hay duplicate dữ liệu.
5. **Kích hoạt hiệu năng chỉ mục không gian GiST:** Đảm bảo toàn bộ 68 bản ghi sau khi nạp được tự động lập chỉ mục trong cây R-Tree của `idx_locations_geom`, phục vụ các phép toán `ST_DistanceSphere`, `ST_DWithin`, và toán tử khung nhìn Leaflet Bounding Box (`&&`).
6. **Khớp nối ngân sách & SLA tích hợp bên ngoài:** Cung cấp danh mục trạm cố định (Fixed Domain) để kiểm soát hạn ngạch gọi OpenWeatherMap API trong ngưỡng an toàn:
   $$\text{Daily API Calls} = 63 \text{ trạm} \times 4 \text{ lần/giờ} \times 24 \text{ giờ} = 6.048 \text{ calls/ngày}$$
   Nằm trọn trong gói cơ bản One Call API mà không phát sinh chi phí vượt ngưỡng.

---

## 2. Quy Chuẩn Kỹ Thuật Trắc Địa & Địa Lý Quốc Gia (Geodetic Engineering Standards)

### 2.1. Hệ Quy Chiếu Tọa Độ WGS84 (EPSG:4326) & Cú Pháp PostGIS Chuẩn

Cơ sở dữ liệu của dự án chuẩn hóa duy nhất một hệ quy chiếu tọa độ trắc địa: **WGS84 (World Geodetic System 1984 - Mã định danh EPSG: 4326)**. Đây là hệ quy chiếu chuẩn toàn cầu được hỗ trợ nguyên bản bởi OpenWeatherMap, GDACS, và thư viện Leaflet.

Trong PostGIS OGC, đối tượng hình học điểm được khởi tạo theo quy tắc bất biến:

```sql
-- CÚ PHÁP CHUẨN POSTGIS CHO ĐIỂM TRẮC ĐỊA:
ST_SetSRID(ST_MakePoint(longitude_x, latitude_y), 4326)
```

### 2.2. Phòng Chống Rủi Ro Đảo Ngược Tọa Độ (Mitigation of RSK-W1-05)

> [!CAUTION]
> **CẢNH BÁO RỦI RO KỸ THUẬT NGHIÊM TRỌNG (RSK-W1-05):**  
> Trong toán học không gian PostGIS:
>
> - Trục hoành $X$ là **Kinh độ (Longitude - Đông/Tây)**. Đối với lãnh thổ Việt Nam: $X \in [102.1^\circ E, 114.5^\circ E]$.
> - Trục tung $Y$ là **Vĩ độ (Latitude - Bắc/Nam)**. Đối với lãnh thổ Việt Nam: $Y \in [8.5^\circ N, 23.4^\circ N]$.
>
> **Lỗi kinh điển của lập trình viên:** Quen miệng nói "Vĩ độ / Kinh độ" (Lat/Lon) nên truyền tham số `ST_MakePoint(Latitude, Longitude)`.
> Nếu đảo ngược: $X \approx 10^\circ - 21^\circ$ (nằm ở Trung Phi / Somalia) và $Y \approx 105^\circ - 108^\circ$ (vượt quá cực Bắc Trái Đất $90^\circ$, gây lỗi hình học hoặc văng ra biển sâu). Toàn bộ tính năng hiển thị bản đồ Leaflet sẽ bị rỗng marker.

Chốt chặn kiểm định định lượng bắt buộc (Gate Check):

```sql
-- Câu truy vấn kiểm tra vi phạm tọa độ: PHẢI TRẢ VỀ CHÍNH XÁC 0 BẢN GHI
SELECT count(*) FROM locations WHERE ST_X(geom) < 50;
```

### 2.3. Quy Chuẩn Đặt Mã Định Danh Trạm (`code`)

Mã định danh `code` của trạm quan trắc là trường khóa tự nhiên duy nhất (`VARCHAR(32) UNIQUE NOT NULL`), phục vụ:

- Định tuyến RESTful API: `/api/v1/locations/HAN`, `/api/v1/locations/DNG`
- Khóa định danh gọi OpenWeatherMap API và đối soát dữ liệu
- Khóa tìm kiếm B-Tree tốc độ cao $O(1)$

Quy chuẩn định dạng mã trạm:

- Chuỗi viết hoa từ 3 đến 5 ký tự Latinh (Alpha-only).
- Ưu tiên sử dụng mã sân bay quốc tế IATA hoặc mã cảng biển quốc gia UN-LOCODE đã được chuẩn hóa:
  - `HAN`: Hà Nội (Nội Bài)
  - `SGN`: TP. Hồ Chí Minh (Tân Sơn Nhất)
  - `DNG`: Đà Nẵng
  - `HPH`: Hải Phòng (Cát Bi)
  - `VCA`: Cần Thơ (Trà Nóc)
  - `QNH`: Quảng Ninh (Vân Đồn)
  - `HSA`: Quần đảo Hoàng Sa
  - `TSA`: Quần đảo Trường Sa
  - `BLV`: Đảo Bạch Long Vĩ
  - `CDO`: Côn Đảo
  - `PQU`: Phú Quốc

### 2.4. Cấu Trúc Phân Vùng Lãnh Thổ Nghiệp Vụ (`region`)

Trường `region` (`VARCHAR(64) NOT NULL`) được dùng để phân cụm hiển thị trên bản đồ số, lọc danh sách trạm theo vùng khí hậu, và phục vụ AI Agent phân tích rủi ro diện rộng. Hệ thống phân chia thành **7 vùng nghiệp vụ chuẩn hóa**:

1. **`Bắc Bộ`:** Vùng Thủ đô, đồng bằng sông Hồng, trung du và miền núi phía Bắc (25 tỉnh/thành).
2. **`Bắc Trung Bộ`:** Dải ven biển miền Trung từ Thanh Hóa đến Thừa Thiên Huế (6 tỉnh).
3. **`Nam Trung Bộ`:** Duyên hải miền Trung từ Đà Nẵng đến Bình Thuận (8 tỉnh/thành).
4. **`Tây Nguyên`:** Vùng cao nguyên trung phần (5 tỉnh).
5. **`Nam Bộ`:** Bao gồm Đông Nam Bộ và Đồng bằng Sông Cửu Long (19 tỉnh/thành).
6. **`Biển Đông`:** Các trạm hải đảo tiền tiêu thuộc chủ quyền Việt Nam trên Biển Đông (Bạch Long Vĩ, Hoàng Sa, Trường Sa, Côn Đảo).
7. **`Biển Tây Nam`:** Đảo Phú Quốc và vùng biển Tây Nam Tổ quốc.

---

## 3. Danh Mục Tổng Hợp Chi Tiết 68 Điểm Trắc Địa Toàn Quốc (Master Data Inventory)

Bảng dưới đây tổng hợp đầy đủ danh mục trắc địa chính thức được nạp vào cơ sở dữ liệu qua Flyway V3, kèm phân tích đặc thù thiên tai phục vụ hệ thống cảnh báo:

| STT | Mã Trạm (`code`) | Tên Địa Phương / Trạm Quan Trắc (`name`) | Phân Vùng (`region`) | Kinh Độ ($X$ - $^\circ E$) | Vĩ Độ ($Y$ - $^\circ N$) | Đặc Thù Khí Tượng & Rủi Ro Thiên Tai Giám Sát               |
| :-: | :--------------- | :--------------------------------------- | :------------------- | :------------------------: | :----------------------: | :---------------------------------------------------------- |
|  1  | `HAN`            | Hà Nội                                   | Bắc Bộ               |          105.8542          |         21.0285          | Đô thị hóa cao, ngập lụt cục bộ, hoàn lưu bão               |
|  2  | `HPH`            | Hải Phòng                                | Bắc Bộ               |          106.6881          |         20.8449          | Cửa ngõ đón bão đổ bộ, triều cường, sóng lớn                |
|  3  | `DNG`            | Đà Nẵng                                  | Nam Trung Bộ         |          108.2022          |         16.0544          | Bão lớn đổ bộ trực tiếp, sóng thần, lũ dâng                 |
|  4  | `SGN`            | TP. Hồ Chí Minh                          | Nam Bộ               |          106.6297          |         10.8231          | Triều cường kết hợp mưa lớn, ngập úng đô thị                |
|  5  | `VCA`            | Cần Thơ                                  | Nam Bộ               |          105.7469          |         10.0452          | Trung tâm miền Tây, triều cường sông Hậu                    |
|  6  | `QNH`            | Quảng Ninh                               | Bắc Bộ               |          107.0433          |         20.9505          | Tâm điểm bão Vịnh Bắc Bộ, sạt lở bãi thải xỉ                |
|  7  | `THB`            | Thái Bình                                | Bắc Bộ               |          106.3366          |         20.4463          | Vùng trũng ven biển, ngập lụt, triều cường                  |
|  8  | `NDH`            | Nam Định                                 | Bắc Bộ               |          106.1683          |         20.4375          | Đê biển trọng yếu, triều dâng bão đổ bộ                     |
|  9  | `NBH`            | Ninh Bình                                | Bắc Bộ               |          105.9745          |         20.2506          | Vùng karst trũng, lũ sông Đáy, ngập lụt                     |
| 10  | `HDG`            | Hải Dương                                | Bắc Bộ               |          106.3161          |         20.9372          | Lũ sông Thái Bình, ngập úng nội đồng                        |
| 11  | `HYN`            | Hưng Yên                                 | Bắc Bộ               |          106.0511          |         20.6461          | Bão hoàn lưu, ngập úng đô thị ven sông Hồng                 |
| 12  | `BNH`            | Bắc Ninh                                 | Bắc Bộ               |          106.0754          |         21.1861          | Khu công nghiệp trũng thấp, ngập úng                        |
| 13  | `VPC`            | Vĩnh Phúc                                | Bắc Bộ               |          105.5969          |         21.3090          | Mưa lớn cục bộ chân núi Tam Đảo                             |
| 14  | `HNM`            | Hà Nam                                   | Bắc Bộ               |          105.9229          |         20.5453          | Rốn nước hạ lưu sông Đáy, ngập lụt kéo dài                  |
| 15  | `LCI`            | Lào Cai                                  | Bắc Bộ               |          103.9757          |         22.4856          | Sạt lở đất đặc biệt nghiêm trọng, lũ ống lũ quét            |
| 16  | `YBI`            | Yên Bái                                  | Bắc Bộ               |          104.8954          |         21.7168          | Lũ quét sông Chảy, sạt lở đồi núi taluy âm                  |
| 17  | `HGG`            | Hà Giang                                 | Bắc Bộ               |          104.9784          |         22.8233          | Địa hình cao nguyên đá dốc đứng, lũ quét                    |
| 18  | `CBG`            | Cao Bằng                                 | Bắc Bộ               |          106.2574          |         22.6664          | Lũ sông Bằng, sạt lở đèo dốc hiểm trở                       |
| 19  | `LSN`            | Lạng Sơn                                 | Bắc Bộ               |          106.7615          |         21.8537          | Lũ sông Kỳ Cùng, rét đậm rét hại băng giá                   |
| 20  | `TNN`            | Thái Nguyên                              | Bắc Bộ               |          105.8442          |         21.5942          | Lũ sông Cầu, ngập lụt diện rộng đô thị                      |
| 21  | `BKG`            | Bắc Kạn                                  | Bắc Bộ               |          105.8348          |         22.1470          | Địa hình đồi núi dốc, nguy cơ sạt trượt đất                 |
| 22  | `TQG`            | Tuyên Quang                              | Bắc Bộ               |          105.2144          |         21.8234          | Lũ hạ lưu thủy điện sông Lô                                 |
| 23  | `PTO`            | Phú Thọ                                  | Bắc Bộ               |          105.2149          |         21.3228          | Ngã ba sông (Hồng - Đà - Lô), áp lực đê điều                |
| 24  | `BGG`            | Bắc Giang                                | Bắc Bộ               |          106.1946          |         21.2731          | Lũ sông Lục Nam, ngập úng vùng đồng bằng                    |
| 25  | `DBN`            | Điện Biên                                | Bắc Bộ               |          103.0168          |         21.3860          | Lũ quét thung lũng Mường Thanh, lốc xoáy                    |
| 26  | `LCU`            | Lai Châu                                 | Bắc Bộ               |          103.4684          |         22.3964          | Sạt lở núi cao hiểm trở, chia cắt giao thông                |
| 27  | `SLA`            | Sơn La                                   | Bắc Bộ               |          103.9148          |         21.3283          | Lũ ống suối Nậm La, trượt lở đất sườn dốc                   |
| 28  | `HBH`            | Hòa Bình                                 | Bắc Bộ               |          105.3376          |         20.8164          | Cửa xả lũ thủy điện Hòa Bình, ngập hạ du                    |
| 29  | `THA`            | Thanh Hóa                                | Bắc Trung Bộ         |          105.7850          |         19.8077          | Bão biển ven bờ, lũ quét miền Tây Thanh Hóa                 |
| 30  | `NAN`            | Nghệ An                                  | Bắc Trung Bộ         |          105.6813          |         18.6734          | Trọng điểm bão nhiệt đới, lũ sông Lam                       |
| 31  | `HTH`            | Hà Tĩnh                                  | Bắc Trung Bộ         |          105.9057          |         18.3435          | Mưa xối xả hồ Kẻ Gỗ, ngập lụt vùng rốn lũ                   |
| 32  | `QBH`            | Quảng Bình                               | Bắc Trung Bộ         |          106.6225          |         17.4764          | Miền Trung "khúc ruột thắt", lũ dâng nhanh                  |
| 33  | `QTI`            | Quảng Trị                                | Bắc Trung Bộ         |          107.1854          |         16.8163          | Lũ sông Thạch Hãn, bão biển kết hợp gió Lào                 |
| 34  | `HUE`            | Thừa Thiên Huế                           | Bắc Trung Bộ         |          107.5905          |         16.4637          | Lượng mưa kỷ lục cả nước, ngập lụt cố đô                    |
| 35  | `QNM`            | Quảng Nam                                | Nam Trung Bộ         |          108.3380          |         15.5736          | Sạt lở núi Trà My, ngập lụt Hội An - Vu Gia                 |
| 36  | `QNG`            | Quảng Ngãi                               | Nam Trung Bộ         |          108.7961          |         15.1205          | Bão giật cấp cao, sóng biển ven bờ Lý Sơn                   |
| 37  | `BDH`            | Bình Định                                | Nam Trung Bộ         |          109.2197          |         13.7830          | Bão đổ bộ Quy Nhơn, gió giật ven biển                       |
| 38  | `PYN`            | Phú Yên                                  | Nam Trung Bộ         |          109.3150          |         13.0882          | Lũ sông Ba, triều cường phá hủy bờ biển                     |
| 39  | `KHA`            | Khánh Hòa                                | Nam Trung Bộ         |          109.1967          |         12.2388          | Bão quét qua vịnh Nha Trang, sạt lở đèo                     |
| 40  | `NTH`            | Ninh Thuận                               | Nam Trung Bộ         |          108.9882          |         11.5658          | Hạn hán gay gắt, mưa lũ bất thường cực đoan                 |
| 41  | `BTH`            | Bình Thuận                               | Nam Trung Bộ         |          108.1021          |         10.9333          | Xâm thực bờ biển, gió mạnh nhiệt đới                        |
| 42  | `KTM`            | Kon Tum                                  | Tây Nguyên           |          108.0076          |         14.3497          | Lũ quét sông Đắk Bla, sạt lở vùng Tu Mơ Rông                |
| 43  | `GLI`            | Gia Lai                                  | Tây Nguyên           |          108.0005          |         13.9833          | Gió xoáy Tây Nguyên, mưa lớn thượng nguồn                   |
| 44  | `DLK`            | Đắk Lắk                                  | Tây Nguyên           |          108.0383          |         12.6667          | Ngập úng cục bộ sông Krông Ana, hạn hán mùa khô             |
| 45  | `DKN`            | Đắk Nông                                 | Tây Nguyên           |          107.6876          |         12.0039          | Nguy cơ nứt trượt thân đập thủy điện                        |
| 46  | `LDG`            | Lâm Đồng                                 | Tây Nguyên           |          108.4583          |         11.9404          | Sạt trượt đất đô thị đồi dốc Đà Lạt - đèo Prenn             |
| 47  | `BDG`            | Bình Dương                               | Nam Bộ               |          106.6661          |         11.0064          | Ngập úng khu đô thị công nghiệp hạ lưu sông Sài Gòn         |
| 48  | `DNI`            | Đồng Nai                                 | Nam Bộ               |          106.8427          |         10.9460          | Lũ sông Đồng Nai, xả tràn hồ Trị An                         |
| 49  | `VTU`            | Bà Rịa - Vũng Tàu                        | Nam Bộ               |          107.0843          |         10.3460          | Bão trái mùa, triều cường và gió giật bờ biển               |
| 50  | `TNH`            | Tây Ninh                                 | Nam Bộ               |          106.1098          |         11.3104          | Mưa bão biên giới, ngập lụt hồ Dầu Tiếng                    |
| 51  | `BPC`            | Bình Phước                               | Nam Bộ               |          106.8766          |         11.7511          | Lốc xoáy cực đoan, mưa lớn vùng đầu nguồn                   |
| 52  | `LAN`            | Long An                                  | Nam Bộ               |          106.4134          |         10.5362          | Lũ đầu nguồn sông Vàm Cỏ, xâm nhập mặn                      |
| 53  | `TGG`            | Tiền Giang                               | Nam Bộ               |          106.3639          |         10.3544          | Triều cường sông Tiền, hạn mặn gay gắt                      |
| 54  | `BTE`            | Bến Tre                                  | Nam Bộ               |          106.3759          |         10.2433          | Sạt lở bờ sông, xâm nhập mặn toàn tỉnh                      |
| 55  | `TVH`            | Trà Vinh                                 | Nam Bộ               |          106.3458          |          9.9347          | Triều dâng cửa biển Cổ Chiên và Cung Hầu                    |
| 56  | `VLG`            | Vĩnh Long                                | Nam Bộ               |          105.9722          |         10.2537          | Ngập triều cường rằm tháng Chín - Mười                      |
| 57  | `DTP`            | Đồng Tháp                                | Nam Bộ               |          105.6337          |         10.4578          | Rốn lũ Đồng Tháp Mười, đón lũ từ Campuchia                  |
| 58  | `AGG`            | An Giang                                 | Nam Bộ               |          105.1259          |         10.3891          | Điểm đầu nguồn đón lũ Mekong vào Việt Nam                   |
| 59  | `KGG`            | Kiên Giang                               | Nam Bộ               |          105.0809          |         10.0125          | Giông lốc ven biển vịnh Thái Lan, triều dâng                |
| 60  | `HUG`            | Hậu Giang                                | Nam Bộ               |          105.4707          |          9.7844          | Vùng trũng ngập lụt nội đồng, sạt lở bờ kênh                |
| 61  | `STG`            | Sóc Trăng                                | Nam Bộ               |          105.9744          |          9.6033          | Triều cường cửa sông Hậu, hạn mặn ven biển                  |
| 62  | `BLU`            | Bạc Liêu                                 | Nam Bộ               |          105.7244          |          9.2941          | Nước biển dâng, xói lở đê biển đông                         |
| 63  | `CMU`            | Cà Mau                                   | Nam Bộ               |          105.1500          |          9.1769          | Mũi cực Nam Tổ quốc, sạt lở 2 mặt bờ biển đông/tây          |
| 64  | `BLV`            | Đảo Bạch Long Vĩ                         | Biển Đông            |          107.7289          |         20.1333          | **Mắt thần Vịnh Bắc Bộ:** Đón bão sớm từ phía đông          |
| 65  | `HSA`            | Quần đảo Hoàng Sa                        | Biển Đông            |          111.6000          |         16.5000          | **Trạm tiền tiêu Trung Trung Bộ:** Cảnh báo bão cấp 12 — 15 |
| 66  | `TSA`            | Quần đảo Trường Sa                       | Biển Đông            |          114.3667          |          8.6500          | **Trạm tiền tiêu Nam Biển Đông:** Phát hiện áp thấp/bão sớm |
| 67  | `CDO`            | Côn Đảo                                  | Biển Đông            |          106.6080          |          8.6833          | Giám sát bão đổ vào Đông Nam Bộ & Nam Bộ                    |
| 68  | `PQU`            | Đảo Phú Quốc                             | Biển Tây Nam         |          103.9667          |         10.2833          | Giám sát bão và giông lốc vùng Biển Tây Nam                 |

---

## 4. Hiện Thực Hóa Tệp Di Chuyển Lược Đồ `V3__seed_vietnam_locations.sql`

### 4.1. Vị Trí Tệp Vật Lý Trong Dự Án

Tệp được đặt chính xác tại thư mục di chuyển của Backend Spring Boot:

```text
backend/
└── src/
    └── main/
        └── resources/
            └── db/
                └── migration/
                    ├── V1__init_spatial_extensions.sql
                    ├── V2__create_location_tables.sql
                    └── V3__seed_vietnam_locations.sql   <-- [Task W1-06 Artifact]
```

### 4.2. Mã Nguồn SQL Đầy Đủ & Nguyên Tắc Lũy Đẳng (Idempotent DML)

Tệp migration sử dụng kỹ thuật `ON CONFLICT (code) DO UPDATE` để bảo đảm script có thể tái thực thi an toàn bất kỳ lúc nào mà không làm gián đoạn chuỗi Flyway:

```sql
-- Trích đoạn cơ chế Lũy đẳng của tệp V3:
INSERT INTO locations (code, name, region, geom, is_active) VALUES
(...)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    region = EXCLUDED.region,
    geom = EXCLUDED.geom,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
```

---

## 5. Ma Trận Kịch Bản Kiểm Thử & Nghiệm Thu Định Lượng (Verification Matrix)

Để đạt chuẩn chất lượng P0, chuyên viên GIS / Database Specialist và Backend Engineer đã thực thi trọn vẹn **8 kịch bản kiểm định định lượng** trên PostgreSQL Docker container (`weathergis-postgres`):

### 5.1. Bảng Ma Trận Kịch Bản Kiểm Thử (Verification Matrix)

| Mã Kịch Bản | Mục Tiêu Kiểm Thử                                           | Câu Truy Vấn SQL / Thao Tác CLI                                                     | Tiêu Chuẩn Nghiệm Thu Kỳ Vọng (Expected Output)                                     | Trạng Thái Thực Tế |
| :---------- | :---------------------------------------------------------- | :---------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------- | :----------------: |
| **TC-01**   | Kiểm tra tổng số lượng trạm nạp                             | `SELECT count(*) FROM locations;`                                                   | Đúng **68** bản ghi (63 tỉnh thành + 5 trạm hải đảo tiền tiêu)                      |    🟢 **PASS**     |
| **TC-02**   | Kiểm tra số lượng tỉnh thành hành chính đất liền            | `SELECT count(*) FROM locations WHERE region NOT IN ('Biển Đông', 'Biển Tây Nam');` | Đúng **63** tỉnh thành phố trực thuộc Trung ương                                    |    🟢 **PASS**     |
| **TC-03**   | Kiểm tra triệt tiêu lỗi đảo ngược tọa độ (RSK-W1-05 Bounds) | `SELECT count(*) FROM locations WHERE ST_X(geom) < 50;`                             | Trả về đúng **0** bản ghi lỗi (100% trạm có Kinh độ > 100°E)                        |    🟢 **PASS**     |
| **TC-04**   | Kiểm tra ràng buộc khóa duy nhất (Unique Code Constraint)   | Thử INSERT bản ghi trùng mã `HAN` mà không có `ON CONFLICT`                         | CSDL chặn với mã lỗi `ERROR: duplicate key value violates unique constraint`        |    🟢 **PASS**     |
| **TC-05**   | Kiểm tra phân bố số lượng trạm theo vùng địa lý (`region`)  | `SELECT region, count(*) FROM locations GROUP BY region ORDER BY count DESC;`       | Đủ 7 phân vùng, không có vùng nào bị `NULL` hoặc rỗng                               |    🟢 **PASS**     |
| **TC-06**   | Kiểm tra tính toán khoảng cách cầu trắc địa chuẩn WGS84     | `ST_DistanceSphere` giữa Hà Nội, Đà Nẵng, TP.HCM, Hoàng Sa, Trường Sa               | Hà Nội - ĐN: ~605 km; Hà Nội - Sài Gòn: ~1.137 km; ĐN - Hoàng Sa: ~366 km           |    🟢 **PASS**     |
| **TC-07**   | Kiểm tra tuần tự hóa dữ liệu GeoJSON cho Leaflet            | `ST_AsGeoJSON(geom)` cho trạm Hà Nội (`HAN`) và Hoàng Sa (`HSA`)                    | Chuẩn RFC 7946: `{"type":"Point","coordinates":[105.8542,21.0285]}`                 |    🟢 **PASS**     |
| **TC-08**   | Kiểm tra tính Lũy Đẳng (Idempotency Sanity Check)           | Thực thi lại toàn bộ file `V3__seed_vietnam_locations.sql` lần thứ 2                | Lệnh thực thi thành công (Exit Code 0), tổng số trạm vẫn giữ nguyên 68              |    🟢 **PASS**     |
| **TC-09**   | Kiểm tra hiệu năng tìm kiếm không gian với chỉ mục GiST     | `EXPLAIN ANALYZE` truy vấn Bounding Box Leaflet (`&&`)                              | Thực thi sử dụng `Index Scan using idx_locations_geom`, thời gian phản hồi $< 3$ ms |    🟢 **PASS**     |

---

### 5.2. Nhật Ký Thực Thi Kiểm Định Thực Tế Trên Môi Trường CLI Docker

Dưới đây là bằng chứng kết quả thực thi các lệnh kiểm định trực tiếp trên PostgreSQL container:

#### 1. Kiểm định TC-01, TC-02, TC-03 (Tổng số trạm, 63 tỉnh thành & Kiểm tra không đảo ngược Lat/Lon):

```bash
docker exec -i weathergis-postgres psql -U gis_user -d weather_gis_db -c "
SELECT
  count(*) AS total_stations,
  count(*) FILTER (WHERE is_active = true) AS active_stations,
  count(*) FILTER (WHERE region NOT IN ('Biển Đông', 'Biển Tây Nam')) AS administrative_provinces,
  count(*) FILTER (WHERE region IN ('Biển Đông', 'Biển Tây Nam')) AS island_stations,
  count(*) FILTER (WHERE ST_X(geom) < 50) AS inverted_coords_error_count
FROM locations;
"
```

_Kết quả đầu ra thực tế:_

```text
 total_stations | active_stations | administrative_provinces | island_stations | inverted_coords_error_count
----------------+-----------------+--------------------------+-----------------+-----------------------------
             68 |              68 |                       63 |               5 |                           0
(1 row)
```

#### 2. Kiểm định TC-05: Phân bố số lượng trạm theo vùng địa lý:

```bash
docker exec -i weathergis-postgres psql -U gis_user -d weather_gis_db -c "
SELECT region, count(*) AS station_count
FROM locations
GROUP BY region
ORDER BY station_count DESC;
"
```

_Kết quả đầu ra thực tế:_

```text
    region     | station_count
---------------+---------------
 Bắc Bộ        |            25
 Nam Bộ        |            19
 Nam Trung Bộ  |             8
 Bắc Trung Bộ  |             6
 Tây Nguyên    |             5
 Biển Đông     |             4
 Biển Tây Nam  |             1
(7 rows)
```

#### 3. Kiểm định TC-06: Khoảng cách cầu trắc địa thực tế giữa các trung tâm & hải đảo:

```bash
docker exec -i weathergis-postgres psql -U gis_user -d weather_gis_db -c "
SELECT
  ROUND((ST_DistanceSphere(
    (SELECT geom FROM locations WHERE code = 'HAN'),
    (SELECT geom FROM locations WHERE code = 'DNG')
  ) / 1000.0)::numeric, 2) AS hanoi_danang_km,
  ROUND((ST_DistanceSphere(
    (SELECT geom FROM locations WHERE code = 'HAN'),
    (SELECT geom FROM locations WHERE code = 'SGN')
  ) / 1000.0)::numeric, 2) AS hanoi_saigon_km,
  ROUND((ST_DistanceSphere(
    (SELECT geom FROM locations WHERE code = 'DNG'),
    (SELECT geom FROM locations WHERE code = 'HSA')
  ) / 1000.0)::numeric, 2) AS danang_hoangsa_km,
  ROUND((ST_DistanceSphere(
    (SELECT geom FROM locations WHERE code = 'KHA'),
    (SELECT geom FROM locations WHERE code = 'TSA')
  ) / 1000.0)::numeric, 2) AS nhatrang_truongsa_km;
"
```

_Kết quả đầu ra thực tế:_

```text
 hanoi_danang_km | hanoi_saigon_km | danang_hoangsa_km | nhatrang_truongsa_km
-----------------+-----------------+-------------------+----------------------
          605.92 |         1137.81 |            366.04 |               691.92
(1 row)
```

#### 4. Kiểm định TC-09: Hiệu năng truy vấn không gian sử dụng chỉ mục GiST (`idx_locations_geom`):

```bash
docker exec -i weathergis-postgres psql -U gis_user -d weather_gis_db -c "
SET enable_seqscan = off;
EXPLAIN ANALYZE
SELECT code, name, geom
FROM locations
WHERE geom && ST_MakeEnvelope(105.0, 20.0, 107.5, 22.0, 4326);
"
```

_Kết quả đầu ra thực tế:_

```text
 Index Scan using idx_locations_geom on locations  (cost=0.14..8.15 rows=1 width=388) (actual time=1.210..1.242 rows=17 loops=1)
   Index Cond: (geom && '...'::geometry)
 Planning Time: 24.813 ms
 Execution Time: 2.419 ms
```

> **Đánh giá hiệu năng:** Chỉ mục GiST `idx_locations_geom` quét trúng 17 điểm trạm thuộc khu vực Bắc Bộ trong phạm vi Bounding Box Leaflet với thời gian thực thi chỉ **2.419 ms**, đáp ứng hoàn hảo tiêu chuẩn SLA phản hồi $< 200$ ms của toàn hệ thống WebGIS.

---

## 6. Tương Thích Kiến Trúc Tầng Ứng Dụng (Application Architecture Alignment)

### 6.1. Ánh Xạ Lên Tầng Miền Spring Data JPA (Task W1-08)

Toàn bộ 68 trạm quan trắc trong bảng `locations` sẽ được ánh xạ trực tiếp sang Entity Java 21 / Spring Boot 3.3 tại Task W1-08:

```java
@Entity
@Table(name = "locations")
public class LocationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 64)
    private String region;

    @Column(nullable = false, columnDefinition = "geometry(Point, 4326)")
    private org.locationtech.jts.geom.Point geom;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
```

### 6.2. Chiến Lược Caching Trên Redis 7.2 & Tối Ưu Hóa Quota API

Theo thiết kế tại [6. Technical Stack.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/6.%20Technical%20Stack.md#L483):

- Khóa Redis `weathergis:locations`: Lưu toàn bộ danh mục 68 trạm dưới dạng JSON GeoJSON FeatureCollection đã serialize sẵn, cấu hình TTL = 24 giờ.
- Khi người dùng truy cập bản đồ, Backend đọc trực tiếp từ Redis trong $< 2$ ms mà không chạm vào CSDL PostGIS, giải phóng 95% tải đọc.
- Định kỳ 15 phút, tiến trình ngầm Ingestion Worker (Tuần 2) duyệt qua danh sách các trạm có `is_active = true` để gọi OpenWeatherMap API One Call, bảo đảm không bị cạn kiệt API Quota.

### 6.3. Sẵn Sàng Cho Frontend React Leaflet (Task W1-11, W1-12, W1-14)

Khi Frontend gọi `GET /api/v1/locations?format=geojson`, dữ liệu trả về theo chuẩn RFC 7946 FeatureCollection:

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "geometry": {
        "type": "Point",
        "coordinates": [105.8542, 21.0285]
      },
      "properties": {
        "code": "HAN",
        "name": "Hà Nội",
        "region": "Bắc Bộ",
        "is_active": true
      }
    }
  ]
}
```

Thư viện Leaflet chỉ cần gọi `L.geoJSON(data)` để tự động ghim 68 Marker/CircleMarker lên bản đồ Việt Nam tại Task W1-14.

---

## 7. Sổ Đăng Ký Quản Trị Rủi Ro Kỹ Thuật (Risk Registry & Contingency Plans)

| Mã Rủi Ro        | Nội Dung Rủi Ro Kỹ Thuật                                                                    |      Mức Độ      | Biện Pháp Kiểm Soát & Giải Pháp Thay Thế                                                                                      |              Trạng Thái Sau W1-06              |
| :--------------- | :------------------------------------------------------------------------------------------ | :--------------: | :---------------------------------------------------------------------------------------------------------------------------- | :--------------------------------------------: |
| **RSK-W1-05**    | Tọa độ bị đảo ngược thứ tự (Vĩ độ trước, Kinh độ sau), khiến trạm bị lệch sang Ấn Độ Dương. | **Nghiêm trọng** | Tuân thủ tuyệt đối quy tắc `ST_MakePoint(Longitude, Latitude)`. Kiểm tra tự động bằng query `WHERE ST_X(geom) < 50` trả về 0. | 🟢 **ĐÃ TRIỆT TIÊU** (Đạt 0 lỗi trên 68 trạm)  |
| **RSK-W1-06-01** | Trùng lặp khóa hoặc lỗi cú pháp khi chạy lại migration trong CI/CD pipeline.                |     **Cao**      | Sử dụng mệnh đề lũy đẳng `ON CONFLICT (code) DO UPDATE` thay vì INSERT thuần túy.                                             |  🟢 **ĐÃ TRIỆT TIÊU** (Kiểm định TC-08 PASS)   |
| **RSK-W1-06-02** | Vượt hạn ngạch gọi OpenWeatherMap API nếu quét quá nhiều trạm với tần suất cao.             |     **Cao**      | Cố định phạm vi 63 — 68 trạm, cấu hình chu kỳ quét 15 phút/lần, lưu đệm Redis TTL 15 phút.                                    | 🟢 **ĐÃ KIỂM SOÁT** (Dự trù ~6.000 calls/ngày) |
| **RSK-W1-06-03** | Sai lệch tọa độ trung tâm hành chính sau khi sáp nhập hoặc điều chỉnh địa giới.             |     **Thấp**     | Tọa độ trạm khí tượng thủy văn được cố định theo tọa độ trắc địa của trạm quan trắc thực tế, độc lập với địa giới hành chính. |              🟢 **ĐÃ KIỂM SOÁT**               |

---

## 8. Sổ Tay Hướng Dẫn Kỹ Sư Thực Thi Từng Bước (Developer Step-by-Step Runbook)

Kỹ sư phụ trách Task W1-06 thực hiện tuần tự theo quy trình chuẩn:

```bash
# ============================================================================
# BƯỚC 1: ĐỒNG BỘ MÃ NGUỒN VÀ TẠO NHÁNH TÍNH NĂNG
# ============================================================================
cd /Users/dllv/Documents/GitHub/VNstormgis
git checkout staging
git pull origin staging
git checkout -b feature/W1-06-flyway-seed-vietnam-locations

# ============================================================================
# BƯỚC 2: SOẠN THẢO TỆP MIGRATION V3
# ============================================================================
# Soạn thảo backend/src/main/resources/db/migration/V3__seed_vietnam_locations.sql
# theo đúng nội dung đặc tả tại Mục 4 của tài liệu này.

# ============================================================================
# BƯỚC 3: KIỂM TRA MÔI TRƯỜNG DOCKER CỤC BỘ
# ============================================================================
docker compose -f deployment/docker-compose.yml ps
# Đảm bảo weathergis-postgres và weathergis-redis đang ở trạng thái (healthy)

# ============================================================================
# BƯỚC 4: THỰC THI SCRIPT MIGRATION V3 VÀO POSTGRESQL CONTAINER
# ============================================================================
docker exec -i weathergis-postgres psql -U gis_user -d weather_gis_db < backend/src/main/resources/db/migration/V3__seed_vietnam_locations.sql

# ============================================================================
# BƯỚC 5: CHẠY BỘ KIỂM ĐỊNH 9 KỊCH BẢN (TC-01 ĐẾN TC-09)
# ============================================================================
# 5.1. Kiểm tra số lượng và tính hợp lệ biên tọa độ
docker exec -i weathergis-postgres psql -U gis_user -d weather_gis_db -c "
SELECT
  count(*) AS total,
  count(*) FILTER (WHERE is_active = true) AS active,
  count(*) FILTER (WHERE region NOT IN ('Biển Đông', 'Biển Tây Nam')) AS provinces_63,
  count(*) FILTER (WHERE ST_X(geom) < 50) AS inverted_errors
FROM locations;
"

# 5.2. Kiểm tra khoảng cách cầu trắc địa
docker exec -i weathergis-postgres psql -U gis_user -d weather_gis_db -c "
SELECT ROUND((ST_DistanceSphere(
  (SELECT geom FROM locations WHERE code = 'HAN'),
  (SELECT geom FROM locations WHERE code = 'DNG')
) / 1000.0)::numeric, 2) AS hanoi_danang_km;
"

# 5.3. Kiểm tra tính lũy đẳng (chạy lại script)
docker exec -i weathergis-postgres psql -U gis_user -d weather_gis_db < backend/src/main/resources/db/migration/V3__seed_vietnam_locations.sql
echo "Exit code: $?" # Kỳ vọng: 0

# ============================================================================
# BƯỚC 6: CHẠY KIỂM ĐỊNH LINT ĐỊNH DẠNG CODE TOÀN REPO
# ============================================================================
npm run lint:root

# ============================================================================
# BƯỚC 7: COMMIT VÀ ĐẨY LÊN KHO LƯU TRỮ TỪ XA
# ============================================================================
git add backend/src/main/resources/db/migration/V3__seed_vietnam_locations.sql docs/week1/Task\ W1-06.md PROGRESS.md docs/week1/README.md AGENT.md
git commit -m "feat(location): add flyway migration v3 for vietnam locations seed data [W1-06]"
git push -u origin feature/W1-06-flyway-seed-vietnam-locations
```

---

## 9. Danh Mục Sản Phẩm Bàn Giao & Checklist Nghiệm Thu (Acceptance Checklist)

### 9.1. Danh Mục Sản Phẩm Bàn Giao (Deliverables)

1. **Tệp Di Chuyển Dữ Liệu SQL:** [`backend/src/main/resources/db/migration/V3__seed_vietnam_locations.sql`](file:///Users/dllv/Documents/GitHub/VNstormgis/backend/src/main/resources/db/migration/V3__seed_vietnam_locations.sql)
2. **Tài Liệu Đặc Tả Kỹ Thuật & Hướng Dẫn:** [`docs/week1/Task W1-06.md`](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/week1/Task%20W1-06.md)
3. **Cập Nhật Hồ Sơ Tiến Độ Dự Án:** [`PROGRESS.md`](file:///Users/dllv/Documents/GitHub/VNstormgis/PROGRESS.md) (Chuyển trạng thái Task W1-06 sang `[x] COMPLETED`, nâng tiến độ lên 12/34 SP)
4. **Cập Nhật Tài Liệu Kế Hoạch Tuần 1:** [`docs/week1/README.md`](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/week1/README.md) (Gắn liên kết tài liệu chi tiết Task W1-06)
5. **Cập Nhật Sổ Tay Đặc Tả AI Agent:** [`AGENT.md`](file:///Users/dllv/Documents/GitHub/VNstormgis/AGENT.md) (Đánh dấu hoàn thành Task W1-06)

### 9.2. Tiêu Chí Hoàn Thành (Definition of Done - DoD Checklist)

- [x] Tệp migration được lưu đúng cấu trúc thư mục `backend/src/main/resources/db/migration/` với tên chuẩn `V3__seed_vietnam_locations.sql`.
- [x] Nạp đầy đủ dữ liệu tọa độ WGS84 cho toàn bộ 63 tỉnh/thành phố trực thuộc Trung ương của Việt Nam.
- [x] Nạp đầy đủ 5 trạm hải đảo tiền tiêu chiến lược (Bạch Long Vĩ, Hoàng Sa, Trường Sa, Côn Đảo, Phú Quốc) phục vụ theo dõi bão xa ngoài Biển Đông.
- [x] Toàn bộ 68 điểm trắc địa có tọa độ Kinh độ ($X$) nằm trong khoảng $102.1^\circ E - 114.5^\circ E$ và Vĩ độ ($Y$) nằm trong khoảng $8.5^\circ N - 23.4^\circ N$.
- [x] Query kiểm tra rủi ro đảo ngược tọa độ RSK-W1-05 (`WHERE ST_X(geom) < 50`) trả về đúng 0 bản ghi lỗi.
- [x] Mệnh đề `ON CONFLICT (code) DO UPDATE` được cấu hình chuẩn xác, bảo đảm tính lũy đẳng tuyệt đối khi chạy lại nhiều lần.
- [x] Chỉ mục không gian GiST `idx_locations_geom` nhận diện và lập chỉ mục cho toàn bộ 68 bản ghi, truy vấn Bounding Box phản hồi $< 3$ ms.
- [x] Toàn bộ 9 kịch bản kiểm định định lượng (TC-01 đến TC-09) đạt kết quả 100% PASS trên PostgreSQL Docker container.
- [x] Kiểm tra định dạng mã nguồn và tài liệu Markdown đạt chuẩn qua lệnh `npm run lint:root`.
- [x] Hệ thống sẵn sàng chuyển giao làm tiền đề vững chắc cho **Task W1-07 (Khởi tạo Spring Boot 3.3 Gradle, Java 21 LTS)** và **Task W1-08 (Hiện thực Domain `location` & Spatial JPA)**.

---

> **Phê duyệt bởi:** Technical Lead / Software Architect / Project Manager — _Vietnam WeatherGIS Engineering Team_  
> **Trạng thái chuyển giao:** **SẴN SÀNG TRIỂN KHAI TASK W1-07 & W1-08**
