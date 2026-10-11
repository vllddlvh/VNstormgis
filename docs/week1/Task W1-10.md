# ĐẶC TẢ KỸ THUẬT VÀ HƯỚNG DẪN THỰC THI CHI TIẾT

## TASK W1-10: KHỞI TẠO ỨNG DỤNG FRONTEND REACT 18, VITE 5, TYPESCRIPT 5, TAILWIND CSS & THIẾT LẬP REVERSE PROXY VITE SANG BACKEND SPRING BOOT

---

## Thông Tin Nhiệm Vụ (Task Metadata)

| Thuộc Tính (Attribute)         | Giá Trị Chi Tiết (Specification)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| :----------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Mã Nhiệm Vụ (Task ID)**      | **W1-10**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **Tên Nhiệm Vụ (Task Name)**   | Khởi Tạo Bộ Khung Ứng Dụng Frontend (React 18.3, Vite 5, TypeScript 5, Tailwind CSS 3.4), Cấu Hình Module Aliasing `@/*`, Xây Dựng Reverse Proxy `/api` Sang Backend `:8080` (Hóa Giải CORS RSK-W1-04), Thiết Lập Design System Khí Tượng Thủy Văn & Xây Dựng Bảng Điều Khiển Kiểm Định Kết Nối API                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Epic / Giai Đoạn**           | Sprint 1 (Tuần 1 — Tuần 2) — **Giai đoạn: Foundation & Baseline Infrastructure**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| **Lịch Trình Thực Hiện**       | **Ngày 4 (Thứ Năm)** — Buổi sáng: 08:00 — 12:00 (Thời lượng: 4 giờ công kỹ sư tập trung; ước tính hoàn thiện kiểm định: 4 — 6 giờ công kỹ sư)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| **Điểm Nỗ Lực (Story Points)** | **2 SP** (Standard Frontend Bootstrapping & Proxy Integration)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| **Mức Độ Ưu Tiên (Priority)**  | **P0 — Blocker Nền Tảng Giao Diện WebGIS & Cầu Nối API Client** (Bắt buộc hoàn tất 100% để: 1. Cung cấp nền tảng chạy SPA cho các tasks GIS bản đồ W1-11, W1-12; 2. Giải quyết triệt để rủi ro CORS khi gọi sang Spring Boot Backend; 3. Cung cấp bộ khung thư viện Leaflet, Lucide Icons, Axios, Tailwind CSS cho toàn bộ Sprint 1 & 2)                                                                                                                                                                                                                                                                                                                                                                                    |
| **Người Chịu Trách Nhiệm (R)** | **Frontend Engineer**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Người Hỗ Trợ Kỹ Thuật (C)**  | Technical Lead, GIS Specialist, Backend Engineer, DevOps Engineer                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| **Người Phê Duyệt (A)**        | **Project Manager / Technical Lead / Software Architect**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **Bên Được Thông Báo (I)**     | Toàn thể đội ngũ phát triển dự án (Backend, QA / Automation Tester, Product Owner)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| **Tài Liệu Căn Cứ Tham Chiếu** | 1. [5. System Architecture.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/5.%20System%20Architecture.md#L350)<br>2. [6. Technical Stack.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/6.%20Technical%20Stack.md#L181)<br>3. [2. API docs.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/2.%20API%20docs.md)<br>4. [Task W1-01.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/week1/Task%20W1-01.md)<br>5. [Task W1-08.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/week1/Task%20W1-08.md)<br>6. [Task W1-09.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/week1/Task%20W1-09.md) |

---

## 1. Bối Cảnh & Mục Tiêu Kỹ Thuật (Context & Objectives)

### 1.1. Bối Cảnh Nghiệp Vụ & Nhu Cầu Khởi Tạo Phân Hệ Frontend SPA

Dự án **WebGIS Giám Sát Mưa Gió Bão Tích Hợp AI Agent Cảnh Báo (`VNstormgis`)** đã hoàn tất trọn vẹn toàn bộ các mốc hạ tầng cơ sở dữ liệu và Backend trong 3 ngày đầu tiên của Sprint 1:

1. **Hạ Tầng Không Gian & CSDL (Tasks W1-02..W1-06):** Cụm container Docker PostGIS 16 và Redis 7.2 đã vận hành ổn định. Các bản ghi di trú Flyway V1, V2, V3 đã nạp đầy đủ tiện ích PostGIS, lược đồ bảng `locations` cùng chỉ mục không gian GiST, và hạt giống dữ liệu 63 tỉnh thành & trạm khí tượng thủy văn WGS84 chuẩn xác.
2. **Backend Domain `location` & Profile Dev (Tasks W1-07..W1-09):** Khung dịch vụ Spring Boot 3.3 (Java 21 LTS, Virtual Threads) đã hoàn thiện. Các endpoint `/api/v1/locations` hỗ trợ chế độ phản hồi kép (JSON Envelope và RFC 7946 GeoJSON FeatureCollection). Tài liệu OpenAPI 3.0 / Swagger UI đã sẵn sàng tại `http://localhost:8080/swagger-ui.html`.

Tuy nhiên, tại thư mục phân hệ `frontend/`, dự án mới chỉ có cấu trúc thư mục khung rỗng (được giữ chỗ từ Task W1-01). Để chuẩn bị cho việc tích hợp bản đồ số Leaflet (Task W1-11) và dựng bố cục bảng điều khiển trung tâm (Task W1-12), **Task W1-10** là bước đi tiên quyết nhằm:

- **Khởi tạo bộ khung Single Page Application (SPA):** Sử dụng **React 18.3**, **Vite 5**, **TypeScript 5.x** và **Tailwind CSS 3.4**, tạo tiền đề cho trải nghiệm người dùng siêu tốc với thời gian khởi động Dev Server $< 300\text{ms}$ và cơ chế Hot Module Replacement (HMR) tức thì.
- **Hóa giải triệt để rủi ro CORS (Mã rủi ro RSK-W1-04):** Khi lập trình trên máy trạm (local machine), Frontend chạy tại nguồn gốc `http://localhost:5173` và Backend chạy tại `http://localhost:8080`. Mặc dù Backend đã có cấu hình `CorsConfig`, việc các trình duyệt hiện đại thực hiện kiểm tra Preflight (`OPTIONS`) hoặc chặn cookie/header nội bộ có thể gây cản trở phát triển. Giải pháp tiêu chuẩn công nghiệp là thiết lập **Reverse Proxy cục bộ ngay trong `vite.config.ts`**, chuyển tiếp mọi lời gọi có tiền tố `/api` sang `:8080`. Cơ chế này mô phỏng chuẩn xác 100% kiến trúc Production (nơi Nginx Reverse Gateway đứng trước tiếp nhận toàn bộ lưu lượng).
- **Thiết lập Design System Khí Tượng & Công Cụ Tiện Ích UI:** Cấu hình bảng màu khí tượng nhận diện cấp độ bão, mưa lớn, triều cường; tích hợp thư viện icon hiện đại `lucide-react`, và bộ công cụ kết hợp lớp CSS `clsx` + `tailwind-merge` (`cn()` helper).
- **Chuẩn hóa tầng giao tiếp HTTP (API Client):** Xây dựng singleton instance Axios có cấu hình sẵn bộ chặn (interceptors) xử lý hợp đồng phản hồi `ApiResponse<T>` và ánh xạ lỗi chuẩn RFC 7807 `ProblemDetail` đã được định nghĩa ở Backend Task W1-08.
- **Xây dựng Màn hình Kiểm định Kết nối (Verification Dashboard):** Cung cấp một giao diện trực quan ngay tại `App.tsx` giúp kiểm tra tính sẵn sàng của hệ thống (Vite, Tailwind, Lucide, kết nối API Backend qua Proxy) trước khi bàn giao sang Task W1-11 tích hợp Leaflet.

---

### 1.2. Mục Tiêu Kỹ Thuật Định Lượng (Quantitative Technical Objectives)

Task W1-10 đặt ra 8 mục tiêu kỹ thuật định lượng bắt buộc phải đạt được:

1. **Khởi tạo hoàn chỉnh ứng dụng Vite 5 + React 18 + TypeScript 5:**
   - Cấu trúc tệp tiêu chuẩn bao gồm: `package.json`, `vite.config.ts`, `tsconfig.json`, `tsconfig.node.json`, `index.html`.
   - Biên dịch hoàn tất bằng lệnh `npm run build` tạo gói sản phẩm tĩnh trong thư mục `dist/` mà không phát sinh bất kỳ lỗi TypeScript nào.
2. **Cài đặt đầy đủ danh mục thư viện phụ thuộc (Core Dependencies):**
   - Bộ thư viện hiển thị WebGIS & Icon: `leaflet@^1.9.4`, `react-leaflet@^4.2.1`, `@types/leaflet@^1.9.12`, `lucide-react@^0.395.0`.
   - Bộ thư viện UI & Phong cách: `tailwindcss@^3.4.4`, `postcss@^8.4.38`, `autoprefixer@^10.4.19`, `clsx@^2.1.1`, `tailwind-merge@^2.3.0`.
   - Thư viện HTTP Client: `axios@^1.7.2`.
3. **Cấu hình TypeScript 5 Type-Checking & Path Aliasing:**
   - Kích hoạt chế độ kiểm tra kiểu nghiêm ngặt: `"strict": true`, `"noUnusedLocals": true`, `"noUnusedParameters": true`.
   - Thiết lập Path Alias `@/*` trỏ tới `./src/*` trong cả `tsconfig.json` và `vite.config.ts`, loại bỏ hoàn toàn các đường dẫn tương đối phức tạp như `../../../`.
4. **Hiện thực hóa Design System Khí Tượng trong Tailwind CSS:**
   - Khai báo bảng màu thời tiết chuẩn hóa trong `tailwind.config.js`: `weather-safe` (xanh lục), `weather-advisory` (xanh da trời), `weather-warning` (vàng hổ phách), `weather-severe` (cam đậm), `weather-extreme` (đỏ cờ), `weather-catastrophic` (tím thẫm).
   - Thiết lập giao diện nền WebGIS tối ưu cho hiển thị bản đồ (tông màu Dark slate/zinc hiện đại).
5. **Cấu hình Vite Dev Server & Reverse Proxy (Hóa giải RSK-W1-04):**
   - Thiết lập cổng phát triển mặc định `port: 5173`, cho phép truy cập mạng nội bộ `host: true`.
   - Cấu hình proxy chuyển tiếp toàn bộ request `/api` sang `http://localhost:8080` với `changeOrigin: true` và `secure: false`.
   - Cấu hình proxy chuyển tiếp `/actuator` sang `http://localhost:8080` phục vụ kiểm tra sức khỏe ứng dụng (Health check).
6. **Xây dựng Typed HTTP Client & Location Service:**
   - Thiết lập singleton Axios trong `src/services/apiClient.ts` với timeout $10000\text{ms}$ và bộ xử lý lỗi RFC 7807 `ProblemDetail`.
   - Viết `src/services/locationService.ts` thực thi lời gọi `GET /api/v1/locations` lấy danh sách trạm quan trắc từ Backend.
7. **Xây dựng Giao Diện Kiểm Định Hệ Thống (Verification Dashboard):**
   - Render thành công component tiêu đề "VNstormgis - Hệ Thống Giám Sát WebGIS Bão Lũ Việt Nam".
   - Hiển thị các thẻ chỉ số trạng thái: Tình trạng Vite HMR, Tình trạng Tailwind CSS, và Tình trạng kết nối tới Backend Spring Boot `:8080` qua Proxy.
   - Khi Backend đang chạy, giao diện gọi thành công API và hiển thị số lượng 68 trạm quan trắc trích xuất từ CSDL PostGIS.
8. **Tuân thủ 100% Quality Gates Monorepo:**
   - Lệnh kiểm tra chất lượng mã nguồn `npm run lint` (hoặc lệnh cấp root `npm run frontend:lint`) hoàn tất với 0 lỗi, 0 cảnh báo.
   - Tương thích hoàn toàn với Git Pre-commit Hook (`.lintstagedrc.json` và Husky).

---

## 2. Kiến Trúc Kỹ Thuật & Các Quyết Định Thiết Kế (Architectural Decisions & Deep Dive)

### 2.1. Sơ Đồ Kiến Trúc Runtime Frontend, Vite Dev Server & Reverse Proxy

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│             KIẾN TRÚC RUNTIME FRONTEND VITE DEV SERVER & REVERSE PROXY                 │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                        │
│   [ TRÌNH DUYỆT NGƯỜI DÙNG (Browser Runtime: Chrome / Firefox / Safari / Edge) ]       │
│                                │                                                       │
│                                │ 1. Nạp Single Page Application (HTML, JS, CSS)        │
│                                ▼                                                       │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │                      VITE DEV SERVER (Node.js Process :5173)                   │   │
│   │                                                                                │   │
│   │  - Hot Module Replacement (HMR via WebSocket ws://localhost:5173)             │   │
│   │  - Esbuild On-the-fly TSX/CSS Transpilation (< 50ms)                           │   │
│   │  - Static Asset Serving (index.html, Lucide icons, Leaflet images)             │   │
│   │                                                                                │   │
│   │  ┌──────────────────────────────────────────────────────────────────────────┐  │   │
│   │  │                 VITE LOCAL REVERSE PROXY (server.proxy)                  │  │   │
│   │  │                                                                          │  │   │
│   │  │   Rules:                                                                 │  │   │
│   │  │   • /api/*      ───(Forward via HTTP loopback)───► http://localhost:8080 │  │   │
│   │  │   • /actuator/* ───(Forward via HTTP loopback)───► http://localhost:8080 │  │   │
│   │  └──────────────────────────────────────────────────────────────────────────┘  │   │
│   └────────────────────────────────────┬───────────────────────────────────────────┘   │
│                                        │                                               │
│                                        │ 2. Proxy request không bị vướng CORS          │
│                                        │    (Host Header rewrite to localhost:8080)    │
│                                        ▼                                               │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │                     SPRING BOOT 3.3 TOMCAT SERVER (:8080)                      │   │
│   │                                                                                │   │
│   │  - LocationController (/api/v1/locations)                                      │   │
│   │  - Actuator Health Controller (/actuator/health)                               │   │
│   │  - Virtual Threads Dispatcher                                                  │   │
│   └────────────────────────────────────┬───────────────────────────────────────────┘   │
│                                        │                                               │
│                                        │ 3. Spatial Query (ST_DistanceSphere)          │
│                                        ▼                                               │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │                       POSTGRESQL 16 + POSTGIS 3.4 (:5432)                      │   │
│   │                                                                                │   │
│   │  - Table: locations (68 trạm WGS84 EPSG:4326)                                  │   │
│   └────────────────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 2.2. Phân Tích Cơ Chế Giải Quyết CORS & Lợi Ích Của Vite Reverse Proxy (RSK-W1-04)

Trong quá trình phát triển hệ thống Web phân tách Backend và Frontend, bài toán **Cross-Origin Resource Sharing (CORS)** luôn là nguồn gốc của các lỗi gián đoạn phát triển:

#### 1. Bản chất sự cố CORS:

- Trình duyệt coi `http://localhost:5173` (Frontend) và `http://localhost:8080` (Backend) là hai nguồn gốc khác nhau (Different Origin do khác số cổng - Port).
- Khi mã nguồn JavaScript thực hiện hàm gọi `axios.get('http://localhost:8080/api/v1/locations')`, trình duyệt sẽ phát một yêu cầu Preflight `OPTIONS`. Nếu Backend chưa phản hồi đúng header `Access-Control-Allow-Origin`, hoặc khi có gửi kèm Cookie/Authentication headers, yêu cầu sẽ bị chặn ngay lập tức tại tầng Network của trình duyệt.

#### 2. So sánh giải pháp Server-side vs Vite Reverse Proxy:

| Tiêu Chí Đánh Giá            | Phương Án A: Chỉ dùng CORS ở Backend (`CorsFilter`)                                                | Phương Án B: Dùng Vite Reverse Proxy (`server.proxy`) — **ĐƯỢC CHỌN**                              |
| :--------------------------- | :------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------- |
| **Cơ chế hoạt động**         | Trình duyệt gọi trực tiếp sang cổng `:8080`; Backend trả về header CORS.                           | Trình duyệt gọi `/api/...` (cùng origin `:5173`); Vite proxy ngầm sang `:8080`.                    |
| **Yêu cầu Preflight**        | Trình duyệt bắt buộc gửi request `OPTIONS` trước mỗi request thay đổi trạng thái.                  | **Không có Preflight**: Trình duyệt coi đây là request Same-Origin, phản hồi ngay lập tức.         |
| **Độ phức tạp cấu hình FE**  | Frontend phải duy trì biến môi trường `VITE_API_BASE_URL=http://localhost:8080` dễ sai sót.        | Frontend chỉ cần gọi đường dẫn tương đối `/api/...`, không phụ thuộc IP máy chủ.                   |
| **Tính tương đồng với Prod** | Khác biệt: Ở môi trường Production, Nginx sẽ phục vụ Frontend và proxy Backend chung một tên miền. | **Giống 100% Production**: Nginx ở Production cũng reverse proxy `/api` như Vite làm ở Dev.        |
| **Khả năng khắc phục lỗi**   | Dễ gặp lỗi chặn Cookie hoặc lỗi Header không khớp khi chuyển đổi máy dev hoặc môi trường mạng.     | **Loại bỏ 100% rủi ro CORS**: Tiến trình Node.js của Vite gọi sang Spring Boot qua kết nối nội bộ. |

**Quyết định kiến trúc:** Cấu hình `server.proxy` trong `vite.config.ts` làm cơ chế mặc định cho môi trường phát triển cục bộ, kết hợp với việc Backend duy trì `CorsConfig` (đã hoàn thành tại W1-08) để tạo thành hệ thống bảo vệ kép (Defense in Depth).

---

### 2.3. Hệ Thống Design System & Bảng Màu Khí Tượng Tailwind CSS

Dự án WebGIS chuyên dụng về thiên tai yêu cầu giao diện có độ tương phản cao, khả năng trực quan hóa dữ liệu màu sắc phân cấp chuẩn mực theo thang đo khí tượng quốc gia và quốc tế (GDACS / WMO):

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                     BẢNG MÃ MÀU CHUẨN CẢNH BÁO THỜI TIẾT DỰ ÁN                         │
├──────────────────┬─────────────────┬───────────┬───────────────────────────────────────┤
│ Cấp Độ Rủi Ro    │ Tên Token CSS   │ Mã Hex    │ Ý Nghĩa Khí Tượng Nghiệp Vụ           │
├──────────────────┼─────────────────┼───────────┼───────────────────────────────────────┤
│ Cấp 1: Bình Thường│ weather-safe    │ #10B981   │ Trời quang, gió nhẹ, không mưa        │
│ Cấp 2: Chú Ý     │ weather-advisory│ #0EA5E9   │ Mưa rào rải rác, gió cấp 4 - 5        │
│ Cấp 3: Cảnh Báo  │ weather-warning │ #F59E0B   │ Mưa to 50-100mm, áp thấp nhiệt đới    │
│ Cấp 4: Nguy Hiểm │ weather-severe  │ #F97316   │ Bão nhiệt đới, gió cấp 8 - 11, mưa lớn│
│ Cấp 5: Khẩn Cấp  │ weather-extreme │ #EF4444   │ Siêu bão cấp 12+, lũ quét, ngập lụt   │
│ Thảm Họa         │ weather-catastrophic #A855F7│ Thiên tai nghiêm trọng, triều cường cực đoan │
└──────────────────┴─────────────────┴───────────┴───────────────────────────────────────┘
```

Ngoài ra, toàn bộ nền bản đồ số sử dụng bảng màu **Slate Palette** (`slate-900`, `slate-800`, `slate-700`) để làm nổi bật các lớp dữ liệu thời tiết (Weather Overlays) và vector bão mà không gây mỏi mắt cho người quan sát trong thời gian dài.

---

### 2.4. Chiến Lược Cấu Trúc Mã Nguồn Frontend (Modular Directory Hierarchy)

Mã nguồn tại phân hệ `frontend/` được tổ chức theo cấu trúc module rõ ràng, chuẩn bị đầy đủ không gian phát triển cho các tác vụ tiếp theo (W1-11 đến W1-15 và Sprint 2):

```text
frontend/
├── public/                                      # Tài nguyên tĩnh không qua đóng gói (favicon, static geojson)
│   ├── favicon.ico
│   └── vite.svg
├── src/
│   ├── assets/                                  # Hình ảnh, biểu tượng vector SVG
│   ├── components/
│   │   ├── common/                              # Các UI component tái sử dụng (Button, Badge, Card, Modal)
│   │   │   ├── StatusBadge.tsx                  # Huy hiệu trạng thái kết nối và sức khỏe hệ thống
│   │   │   └── VerificationPanel.tsx            # Bảng điều khiển kiểm tra các thành phần kỹ thuật
│   │   ├── layout/                              # Khung giao diện tổng thể (Header, Sidebar, DashboardLayout)
│   │   └── map/                                 # Các component liên quan đến bản đồ Leaflet (cho W1-11 & W1-12)
│   │       ├── leafletConfig.ts                 # Cấu hình biểu tượng và tiện ích Leaflet (W1-11)
│   │       └── BaseMap.tsx                      # Component bản đồ nền Việt Nam (W1-12)
│   ├── lib/
│   │   └── utils.ts                             # Hàm tiện ích dùng chung (helper gộp class cn())
│   ├── services/                                # Tầng giao tiếp HTTP API Client
│   │   ├── apiClient.ts                         # Singleton Axios instance & Response Interceptors
│   │   └── locationService.ts                   # Dịch vụ gọi API trạm quan trắc (/api/v1/locations)
│   ├── types/                                   # Định nghĩa kiểu dữ liệu TypeScript
│   │   ├── api.ts                               # Kiểu chuẩn ApiResponse<T>, ProblemDetail RFC 7807
│   │   ├── location.ts                          # Kiểu dữ liệu trạm quan trắc & GeoJSON FeatureCollection
│   │   └── vite-env.d.ts                        # Khai báo môi trường client của Vite
│   ├── App.tsx                                  # Component gốc tích hợp màn hình kiểm định W1-10
│   ├── index.css                                # Định nghĩa CSS Variables & Tailwind Directives
│   └── main.tsx                                 # Điểm nhập (Entrypoint) React 18 createRoot
├── .eslintrc.cjs                                # Cấu hình linter ESLint chuẩn cho React TypeScript
├── .gitignore                                   # Loại trừ node_modules, dist, .vite
├── index.html                                   # HTML Template chuẩn SEO và Viewport di động
├── package.json                                 # Danh mục gói phụ thuộc và scripts thực thi
├── postcss.config.js                            # Cấu hình PostCSS nạp Tailwind CSS & Autoprefixer
├── tailwind.config.js                           # Cấu hình Design Tokens, Themes và Content Purging
├── tsconfig.json                                # Cấu hình TypeScript cho ứng dụng web (App tsconfig)
├── tsconfig.node.json                           # Cấu hình TypeScript cho tệp cấu hình Vite (Node tsconfig)
└── vite.config.ts                               # Cấu hình Vite Build Tool, Plugins, Alias & Reverse Proxy
```

---

### 2.5. Cơ Chế Quản Lý Giao Tiếp HTTP Client (Axios Instance & RFC 7807 Error Interceptor)

Tầng giao tiếp dữ liệu của Frontend được bảo vệ bằng lớp trừu tượng `apiClient.ts` sử dụng thư viện **Axios**:

1. **Base URL linh hoạt:** Thiết lập `baseURL: '/api'` để mọi lời gọi API tự động đi qua Vite Reverse Proxy ở môi trường cục bộ và Nginx Gateway ở môi trường máy chủ.
2. **Timeout an toàn:** Thiết lập thời gian chờ tối đa $10000\text{ms}$ ($10\text{s}$), ngăn chặn tình trạng treo giao diện khi kết nối mạng chập chờn.
3. **Response Interceptor chuẩn RFC 7807:**
   - Khi Backend trả về mã lỗi $4xx$ hoặc $5xx$, `GlobalExceptionHandler` của Spring Boot (xây dựng tại W1-08) sẽ phản hồi đối tượng lỗi chuẩn `ProblemDetail` (chứa `title`, `status`, `detail`, `instance`, `timestamp`).
   - Interceptor của Axios tự động bắt đối tượng này, đóng gói thành lỗi có cấu trúc giúp UI hiển thị thông báo lỗi thân thiện cho người dùng, thay vì báo lỗi không xác định (`[object Object]`).

---

## 3. Đặc Tả Chi Tiết Các Tệp Mã Nguồn & Cấu Hình (Code & Config Specifications)

Dưới đây là đặc tả chi tiết 100% nội dung mã nguồn của toàn bộ các tệp cấu hình và mã nguồn cần triển khai trong Task W1-10:

### 3.1. Tệp Cấu Hình Gói Phụ Thuộc (`frontend/package.json`)

Tệp `package.json` khai báo chính xác phiên bản của tất cả các thư viện đã được phê duyệt trong [6. Technical Stack.md](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/6.%20Technical%20Stack.md):

```json
{
  "name": "vnstormgis-frontend",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "lint": "eslint . --ext ts,tsx --report-unused-disable-directives --max-warnings 0",
    "preview": "vite preview",
    "type-check": "tsc --noEmit"
  },
  "dependencies": {
    "axios": "^1.7.2",
    "clsx": "^2.1.1",
    "leaflet": "^1.9.4",
    "lucide-react": "^0.395.0",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-leaflet": "^4.2.1",
    "tailwind-merge": "^2.3.0"
  },
  "devDependencies": {
    "@types/leaflet": "^1.9.12",
    "@types/node": "^20.14.9",
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "@typescript-eslint/eslint-plugin": "^7.13.1",
    "@typescript-eslint/parser": "^7.13.1",
    "@vitejs/plugin-react": "^4.3.1",
    "autoprefixer": "^10.4.19",
    "eslint": "^8.57.0",
    "eslint-plugin-react-hooks": "^4.6.2",
    "eslint-plugin-react-refresh": "^0.4.7",
    "postcss": "^8.4.38",
    "tailwindcss": "^3.4.4",
    "typescript": "^5.4.5",
    "vite": "^5.3.1"
  }
}
```

---

### 3.2. Tệp Cấu Hình Vite Build Tool & Reverse Proxy (`frontend/vite.config.ts`)

Cấu hình máy chủ phát triển Vite với Reverse Proxy chuyển tiếp `/api` và `/actuator` sang Spring Boot `:8080`, giải quyết triệt để rủi ro CORS RSK-W1-04:

```typescript
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 5173,
    host: true, // Cho phép lắng nghe trên tất cả địa chỉ mạng nội bộ
    strictPort: false,
    proxy: {
      // Reverse Proxy chuyển tiếp lời gọi API sang Backend Spring Boot
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
        configure: (proxy) => {
          proxy.on("error", (err) => {
            console.error("[Vite Proxy Error] Lỗi kết nối tới Backend Spring Boot (:8080):", err.message);
          });
        },
      },
      // Reverse Proxy chuyển tiếp lời gọi Actuator Health sang Backend
      "/actuator": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
      },
    },
  },
  build: {
    outDir: "dist",
    sourcemap: true,
    chunkSizeWarningLimit: 1000,
  },
});
```

---

### 3.3. Tệp Cấu Hình TypeScript (`frontend/tsconfig.json` & `frontend/tsconfig.node.json`)

#### 1. Cấu hình TypeScript Ứng dụng (`frontend/tsconfig.json`):

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,

    /* Bundler mode */
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": false,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",

    /* Linting & Type-Checking nghiêm ngặt */
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,

    /* Path Aliasing @/* trỏ vào ./src/* */
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

#### 2. Cấu hình TypeScript Node/Vite (`frontend/tsconfig.node.json`):

```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true
  },
  "include": ["vite.config.ts"]
}
```

---

### 3.4. Cấu Hình Tailwind CSS & PostCSS (`frontend/tailwind.config.js` & `frontend/postcss.config.js`)

#### 1. Cấu hình Tailwind CSS (`frontend/tailwind.config.js`):

```javascript
/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        // Bảng màu phân cấp cảnh báo thời tiết thiên tai chuẩn nghiệp vụ
        weather: {
          safe: "#10B981", // Cấp 1: Xanh lục - An toàn / Bình thường
          advisory: "#0EA5E9", // Cấp 2: Xanh lam - Chú ý / Mưa rào
          warning: "#F59E0B", // Cấp 3: Vàng hổ phách - Cảnh báo / Mưa to
          severe: "#F97316", // Cấp 4: Cam đậm - Nguy hiểm / Bão
          extreme: "#EF4444", // Cấp 5: Đỏ cờ - Khẩn cấp / Siêu bão
          catastrophic: "#A855F7", // Cấp thảm họa: Tím
        },
        // Tông màu giao diện tối chuyên dụng cho bản đồ WebGIS
        gis: {
          bg: "#0F172A", // Nền bảng điều khiển (Slate 900)
          panel: "#1E293B", // Nền hộp thoại nổi (Slate 800)
          border: "#334155", // Viền phân cách (Slate 700)
          hover: "#475569", // Màu khi hover chuột (Slate 600)
        },
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "-apple-system", "sans-serif"],
      },
    },
  },
  plugins: [],
};
```

#### 2. Cấu hình PostCSS (`frontend/postcss.config.js`):

```javascript
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
```

---

### 3.5. Tệp CSS Gốc & Reset Giao Diện Bản Đồ (`frontend/src/index.css`)

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  :root {
    color-scheme: dark;
  }

  body {
    @apply m-0 p-0 font-sans antialiased bg-gis-bg text-slate-100 overflow-hidden;
    min-height: 100vh;
  }
}

/* Khắc phục lỗi hiển thị Leaflet trong bố cục Flex/Grid hiện đại */
.leaflet-container {
  width: 100%;
  height: 100%;
  z-index: 10;
  background-color: #1e293b !important;
}

/* Tinh chỉnh thanh cuộn thanh mảnh cho các panel WebGIS */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: #0f172a;
}

::-webkit-scrollbar-thumb {
  background: #334155;
  border-radius: 9999px;
}

::-webkit-scrollbar-thumb:hover {
  background: #475569;
}
```

---

### 3.6. Hàm Tiện Ích Kết Hợp Lớp CSS (`frontend/src/lib/utils.ts`)

Hàm tiện ích `cn()` kết hợp `clsx` và `tailwind-merge`, giải quyết triệt để xung đột thuộc tính class của Tailwind CSS khi gộp class có điều kiện:

```typescript
import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Hợp nhất các lớp CSS Tailwind một cách an toàn và loại bỏ các lớp trùng lặp/xung đột.
 * @param inputs Danh sách các lớp CSS hoặc biểu thức điều kiện.
 * @returns Chuỗi lớp CSS hoàn chỉnh đã được tinh giản.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
```

---

### 3.7. Định Nghĩa Kiểu Dữ Liệu TypeScript (`frontend/src/types/api.ts` & `frontend/src/types/location.ts`)

#### 1. Kiểu Phản Hồi API Chung & Chuẩn Lỗi RFC 7807 (`frontend/src/types/api.ts`):

```typescript
/**
 * Chuẩn bao bọc phản hồi API thành công từ Spring Boot Backend.
 */
export interface ApiResponse<T> {
  success: boolean;
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

/**
 * Định dạng phản hồi lỗi chuẩn công nghiệp Problem Details (RFC 7807).
 * Tương thích 100% với GlobalExceptionHandler tại Backend (Task W1-08).
 */
export interface ProblemDetail {
  type?: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  timestamp?: string;
  invalidParams?: Record<string, string>;
}
```

#### 2. Kiểu Dữ Liệu Trạm Quan Trắc & GeoJSON RFC 7946 (`frontend/src/types/location.ts`):

```typescript
/**
 * Thông tin chi tiết một trạm quan trắc khí tượng thủy văn.
 */
export interface LocationItem {
  id: string;
  code: string;
  name: string;
  province: string;
  region: string;
  latitude: number;
  longitude: number;
  elevation: number;
  isActive: boolean;
  distanceKm?: number;
}

/**
 * Chuẩn cấu trúc GeoJSON Feature (RFC 7946).
 */
export interface GeoJsonFeature<TProps = Record<string, unknown>> {
  type: "Feature";
  geometry: {
    type: "Point";
    coordinates: [number, number]; // [Kinh độ (Longitude), Vĩ độ (Latitude)]
  };
  properties: TProps;
}

/**
 * Chuẩn cấu trúc GeoJSON FeatureCollection phục vụ nạp thẳng vào Leaflet L.geoJSON.
 */
export interface GeoJsonFeatureCollection<TProps = Record<string, unknown>> {
  type: "FeatureCollection";
  features: GeoJsonFeature<TProps>[];
}
```

---

### 3.8. Singleton Axios Client Tích Hợp Interceptor (`frontend/src/services/apiClient.ts`)

```typescript
import axios, { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from "axios";
import { ProblemDetail } from "@/types/api";

/**
 * Tạo Axios Singleton Instance với cấu hình mặc định.
 * BaseURL trỏ tới '/api' để được Vite Reverse Proxy chuyển tiếp sang ':8080'.
 */
export const apiClient = axios.create({
  baseURL: "/api",
  timeout: 10000,
  headers: {
    "Content-Type": "application/json",
    Accept: "application/json",
  },
});

// Request Interceptor: Ghi nhật ký yêu cầu trong chế độ phát triển
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    if (import.meta.env.DEV) {
      console.debug(`[HTTP Request] ${config.method?.toUpperCase()} ${config.url}`);
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// Response Interceptor: Bóc tách dữ liệu và chuẩn hóa thông báo lỗi RFC 7807
apiClient.interceptors.response.use(
  (response: AxiosResponse) => response,
  (error: AxiosError<ProblemDetail>) => {
    if (error.response?.data) {
      const problem = error.response.data;
      console.error(`[HTTP Error ${problem.status || error.response.status}] ${problem.title}: ${problem.detail || "Không có mô tả"}`);
    } else {
      console.error(`[Network Error] Không thể kết nối tới máy chủ Backend: ${error.message}`);
    }
    return Promise.reject(error);
  },
);
```

---

### 3.9. Dịch Vụ Gọi API Trạm Quan Trắc (`frontend/src/services/locationService.ts`)

```typescript
import { apiClient } from "./apiClient";
import { ApiResponse } from "@/types/api";
import { LocationItem, GeoJsonFeatureCollection } from "@/types/location";

export const locationService = {
  /**
   * Lấy toàn bộ danh sách trạm quan trắc dưới dạng JSON Envelope tiêu chuẩn.
   */
  async getAllLocations(): Promise<LocationItem[]> {
    const response = await apiClient.get<ApiResponse<LocationItem[]>>("/v1/locations?format=json");
    return response.data.data;
  },

  /**
   * Lấy toàn bộ danh sách trạm quan trắc dưới dạng GeoJSON RFC 7946 cho Leaflet.
   */
  async getAllLocationsAsGeoJson(): Promise<GeoJsonFeatureCollection<LocationItem>> {
    const response = await apiClient.get<GeoJsonFeatureCollection<LocationItem>>("/v1/locations?format=geojson");
    return response.data;
  },

  /**
   * Tìm kiếm các trạm quan trắc lân cận trong bán kính cho trước.
   */
  async getNearbyLocations(lng: number, lat: number, radiusKm: number): Promise<LocationItem[]> {
    const response = await apiClient.get<ApiResponse<LocationItem[]>>("/v1/locations/nearby", {
      params: { lng, lat, radiusKm },
    });
    return response.data.data;
  },

  /**
   * Kiểm tra trạng thái sức khỏe của Backend Spring Boot qua Actuator.
   */
  async checkBackendHealth(): Promise<{ status: string }> {
    const response = await apiClient.get<{ status: string }>("/actuator/health", {
      baseURL: "", // Bỏ qua '/api' để gọi thẳng '/actuator/health' qua Vite proxy
    });
    return response.data;
  },
};
```

---

### 3.10. Component Huy Hiệu Trạng Thái Hệ Thống (`frontend/src/components/common/StatusBadge.tsx`)

```typescript
import React from 'react';
import { cn } from '@/lib/utils';
import { Activity, CheckCircle2, AlertTriangle, XCircle } from 'lucide-react';

export type StatusType = 'online' | 'warning' | 'error' | 'loading';

interface StatusBadgeProps {
  status: StatusType;
  label: string;
  subLabel?: string;
  className?: string;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({
  status,
  label,
  subLabel,
  className,
}) => {
  const statusConfig = {
    online: {
      bg: 'bg-emerald-950/80 border-emerald-500/50 text-emerald-300',
      dot: 'bg-emerald-400 animate-pulse',
      icon: CheckCircle2,
    },
    warning: {
      bg: 'bg-amber-950/80 border-amber-500/50 text-amber-300',
      dot: 'bg-amber-400',
      icon: AlertTriangle,
    },
    error: {
      bg: 'bg-rose-950/80 border-rose-500/50 text-rose-300',
      dot: 'bg-rose-400',
      icon: XCircle,
    },
    loading: {
      bg: 'bg-slate-800/80 border-slate-600/50 text-slate-300',
      dot: 'bg-sky-400 animate-ping',
      icon: Activity,
    },
  };

  const current = statusConfig[status];
  const Icon = current.icon;

  return (
    <div
      className={cn(
        'inline-flex items-center gap-2 px-3 py-1.5 rounded-full border text-xs font-medium backdrop-blur-sm shadow-sm transition-all',
        current.bg,
        className
      )}
    >
      <span className={cn('w-2 h-2 rounded-full', current.dot)} />
      <Icon className="w-3.5 h-3.5" />
      <span>{label}</span>
      {subLabel && <span className="text-slate-400 font-normal">| {subLabel}</span>}
    </div>
  );
};
```

---

### 3.11. Bảng Điều Khiển Kiểm Định Khởi Tạo (`frontend/src/App.tsx`)

Component `App.tsx` đóng vai trò là **Màn hình Kiểm định Hệ thống (System Verification Screen)** cho Task W1-10, cho phép kỹ sư xác nhận ngay lập tức: Vite HMR hoạt động, Tailwind nạp đúng style, Lucide render sắc nét, và lời gọi API qua Vite Proxy `:8080` trả về dữ liệu PostGIS thật:

```typescript
import { useState, useEffect } from 'react';
import {
  CloudLightning,
  Database,
  Radio,
  Server,
  Layers,
  RefreshCw,
  ExternalLink,
  MapPin,
} from 'lucide-react';
import { StatusBadge, StatusType } from '@/components/common/StatusBadge';
import { locationService } from '@/services/locationService';
import { LocationItem } from '@/types/location';

export function App() {
  const [backendStatus, setBackendStatus] = useState<StatusType>('loading');
  const [stationCount, setStationCount] = useState<number | null>(null);
  const [sampleStations, setSampleStations] = useState<LocationItem[]>([]);
  const [lastChecked, setLastChecked] = useState<string>('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);

  const verifyConnectivity = async () => {
    setIsRefreshing(true);
    setErrorMessage(null);
    try {
      // 1. Kiểm tra Actuator Health qua Vite Reverse Proxy
      await locationService.checkBackendHealth();

      // 2. Thử nghiệm lấy 68 trạm quan trắc từ PostgreSQL/PostGIS
      const locations = await locationService.getAllLocations();
      setStationCount(locations.length);
      setSampleStations(locations.slice(0, 5));
      setBackendStatus('online');
      setLastChecked(new Date().toLocaleTimeString('vi-VN'));
    } catch (err: unknown) {
      setBackendStatus('error');
      const msg = err instanceof Error ? err.message : 'Không rõ lỗi';
      setErrorMessage(`Lỗi proxy kết nối Backend: ${msg}. Đảm bảo container Docker và Backend Spring Boot đang chạy!`);
      setLastChecked(new Date().toLocaleTimeString('vi-VN'));
    } finally {
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    verifyConnectivity();
  }, []);

  return (
    <div className="flex flex-col h-screen w-screen bg-gis-bg text-slate-100 select-none">
      {/* 1. Header Thanh Điều Hướng Trên Cùng */}
      <header className="h-16 px-6 bg-gis-panel border-b border-gis-border flex items-center justify-between z-20">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-xl bg-gradient-to-tr from-sky-500 to-indigo-600 text-white shadow-lg shadow-sky-500/20">
            <CloudLightning className="w-6 h-6 animate-pulse" />
          </div>
          <div>
            <h1 className="text-base font-bold tracking-tight text-white flex items-center gap-2">
              VNstormgis
              <span className="text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full bg-sky-500/20 text-sky-400 border border-sky-500/30">
                Sprint 1 Foundation
              </span>
            </h1>
            <p className="text-xs text-slate-400">
              Hệ Thống Giám Sát Mưa Gió Bão Tích Hợp AI Agent Cảnh Báo
            </p>
          </div>
        </div>

        {/* Cụm Huy Hiệu Trạng Thái */}
        <div className="flex items-center gap-3">
          <StatusBadge
            status={backendStatus}
            label={backendStatus === 'online' ? 'Spring Boot 3.3 Active' : 'Backend Disconnected'}
            subLabel={stationCount ? `${stationCount} Trạm PostGIS` : undefined}
          />
          <button
            onClick={verifyConnectivity}
            disabled={isRefreshing}
            className="p-2 rounded-lg bg-slate-800 hover:bg-gis-hover text-slate-300 hover:text-white border border-gis-border transition-all disabled:opacity-50"
            title="Làm mới kiểm tra kết nối"
          >
            <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin text-sky-400' : ''}`} />
          </button>
        </div>
      </header>

      {/* 2. Thân Ứng Dụng: Bảng Điều Khiển Kiểm Định Bootstrap (Verification Dashboard) */}
      <main className="flex-1 p-6 overflow-y-auto">
        <div className="max-w-6xl mx-auto space-y-6">
          {/* Thông Báo Trạng Thái */}
          {errorMessage && (
            <div className="p-4 rounded-xl bg-rose-950/50 border border-rose-500/50 text-rose-200 text-sm flex items-start gap-3">
              <span className="font-semibold text-rose-400">Cảnh báo:</span>
              <div className="flex-1">{errorMessage}</div>
            </div>
          )}

          {/* Ma Trận Kiểm Định Khởi Tạo Công Nghệ */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* Card 1: Khung Ứng Dụng Frontend */}
            <div className="p-5 rounded-2xl bg-gis-panel border border-gis-border space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold uppercase text-sky-400 tracking-wider">Frontend Core</span>
                <Radio className="w-4 h-4 text-sky-400" />
              </div>
              <div className="text-lg font-bold text-white">React 18 + Vite 5 + TS 5</div>
              <p className="text-xs text-slate-400 leading-relaxed">
                Đã nạp thành công bộ khung TypeScript nghiêm ngặt, Hot Module Replacement (HMR) cực nhanh, và Path Alias <code className="text-sky-300 font-mono">@/*</code>.
              </p>
              <div className="pt-2 flex items-center gap-2 text-[11px] text-emerald-400 font-mono">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                Vite Dev Server :5173 OK
              </div>
            </div>

            {/* Card 2: Reverse Proxy & CORS */}
            <div className="p-5 rounded-2xl bg-gis-panel border border-gis-border space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold uppercase text-amber-400 tracking-wider">Proxy & CORS</span>
                <Server className="w-4 h-4 text-amber-400" />
              </div>
              <div className="text-lg font-bold text-white">Reverse Proxy (/api ➔ :8080)</div>
              <p className="text-xs text-slate-400 leading-relaxed">
                Hóa giải rủi ro RSK-W1-04 bằng cơ chế forward nội bộ, ngăn chặn lỗi trình duyệt chặn CORS khi gọi sang Spring Boot Backend.
              </p>
              <div className="pt-2 flex items-center gap-2 text-[11px] text-emerald-400 font-mono">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                Proxy HTTP Loopback Active
              </div>
            </div>

            {/* Card 3: Design System & Styling */}
            <div className="p-5 rounded-2xl bg-gis-panel border border-gis-border space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold uppercase text-indigo-400 tracking-wider">Design System</span>
                <Layers className="w-4 h-4 text-indigo-400" />
              </div>
              <div className="text-lg font-bold text-white">Tailwind CSS 3.4 & Lucide</div>
              <p className="text-xs text-slate-400 leading-relaxed">
                Tích hợp sẵn bảng màu thời tiết đa cấp độ (<span className="text-weather-safe">Safe</span>, <span className="text-weather-warning">Warning</span>, <span className="text-weather-extreme">Extreme</span>) và Lucide Icons.
              </p>
              <div className="pt-2 flex items-center gap-2 text-[11px] text-emerald-400 font-mono">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                Tailwind JIT Engine OK
              </div>
            </div>
          </div>

          {/* Khu Vực Kiểm Tra Dữ Liệu Thực Tế Từ PostGIS */}
          <div className="p-6 rounded-2xl bg-gis-panel border border-gis-border space-y-4">
            <div className="flex items-center justify-between border-b border-gis-border pb-4">
              <div className="flex items-center gap-2">
                <Database className="w-5 h-5 text-sky-400" />
                <h2 className="text-sm font-bold text-white uppercase tracking-wider">
                  Dữ Liệu Trắc Địa PostGIS Nạp Qua API ({stationCount !== null ? `${stationCount} Trạm` : 'Đang tải...'})
                </h2>
              </div>
              <div className="flex items-center gap-3">
                <span className="text-xs text-slate-400">Kiểm tra lúc: {lastChecked || 'Chưa kiểm tra'}</span>
                <a
                  href="/swagger-ui.html"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1.5 text-xs text-sky-400 hover:text-sky-300 transition-colors"
                >
                  Swagger UI <ExternalLink className="w-3.5 h-3.5" />
                </a>
              </div>
            </div>

            {sampleStations.length > 0 ? (
              <div className="space-y-3">
                <p className="text-xs text-slate-400">
                  Dưới đây là 5 trạm quan trắc tiêu biểu phản hồi từ Backend qua endpoint <code className="text-sky-300 font-mono">GET /api/v1/locations</code>:
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
                  {sampleStations.map((station) => (
                    <div
                      key={station.id}
                      className="p-3.5 rounded-xl bg-slate-900/60 border border-slate-700/60 hover:border-sky-500/40 transition-all space-y-2"
                    >
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-mono font-bold text-sky-400">{station.code}</span>
                        <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-300">
                          {station.region}
                        </span>
                      </div>
                      <div className="text-sm font-semibold text-white truncate">{station.name}</div>
                      <div className="text-[11px] text-slate-400 flex items-center gap-1">
                        <MapPin className="w-3 h-3 text-slate-500" />
                        <span>{station.latitude.toFixed(4)}, {station.longitude.toFixed(4)}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div className="py-8 text-center text-slate-400 text-xs">
                {backendStatus === 'loading'
                  ? 'Đang tiến hành kết nối kiểm tra dữ liệu qua Reverse Proxy...'
                  : 'Chưa thể kết nối tới Backend. Hãy khởi động Spring Boot và thử lại!'}
              </div>
            )}
          </div>

          {/* Vùng Placeholder Sẵn Sàng Cho Task W1-11 & W1-12 */}
          <div className="p-8 rounded-2xl border-2 border-dashed border-slate-700/70 bg-slate-900/30 text-center space-y-3">
            <div className="inline-flex p-3 rounded-full bg-slate-800 text-slate-400 mb-1">
              <Layers className="w-6 h-6" />
            </div>
            <h3 className="text-sm font-bold text-slate-200">
              Khu Vực Bản Đồ Nền Leaflet Việt Nam (BaseMap Container Placeholder)
            </h3>
            <p className="text-xs text-slate-400 max-w-lg mx-auto leading-relaxed">
              Vùng hiển thị này sẽ được tích hợp component <code className="text-sky-300 font-mono">BaseMap.tsx</code> tại <strong>Task W1-11 (Leaflet 1.9 & Fix Icon Bug)</strong> và <strong>Task W1-12 (Center VN, Zoom 6, App Layout)</strong>.
            </p>
          </div>
        </div>
      </main>

      {/* 3. Footer Trạng Thái Kỹ Thuật */}
      <footer className="h-9 px-6 bg-gis-panel border-t border-gis-border flex items-center justify-between text-[11px] text-slate-400">
        <div>VNstormgis © 2026 - Nền tảng Giám Sát Thời Tiết Thông Minh</div>
        <div className="flex items-center gap-4">
          <span>React 18.3</span>
          <span>•</span>
          <span>Vite 5.3</span>
          <span>•</span>
          <span>Tailwind 3.4</span>
          <span>•</span>
          <span>Leaflet 1.9 Ready</span>
        </div>
      </footer>
    </div>
  );
}

export default App;
```

---

### 3.12. Tệp HTML Khung Nhìn & Khởi Tạo Ứng Dụng (`frontend/index.html` & `frontend/src/main.tsx`)

#### 1. Tệp Khung Nhìn HTML (`frontend/index.html`):

```html
<!doctype html>
<html lang="vi" class="dark">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/vite.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <title>VNstormgis - Hệ Thống Giám Sát WebGIS Bão Lũ Việt Nam</title>
    <!-- Phông chữ Inter hiện đại cho bảng điều khiển số liệu -->
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;600&display=swap" rel="stylesheet" />
  </head>
  <body class="bg-gis-bg text-slate-100">
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

#### 2. Tệp Khởi Tạo Gốc (`frontend/src/main.tsx`):

```typescript
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

---

### 3.13. Cấu Hình Linter & Môi Trường Client (`frontend/.eslintrc.cjs` & `src/types/vite-env.d.ts`)

#### 1. Cấu hình Linter nghiêm ngặt (`frontend/.eslintrc.cjs`):

```javascript
module.exports = {
  root: true,
  env: { browser: true, es2020: true },
  extends: ["eslint:recommended", "plugin:@typescript-eslint/recommended", "plugin:react-hooks/recommended"],
  ignorePatterns: ["dist", ".eslintrc.cjs"],
  parser: "@typescript-eslint/parser",
  plugins: ["react-refresh"],
  rules: {
    "react-refresh/only-export-components": ["warn", { allowConstantExport: true }],
    "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
  },
};
```

#### 2. Khai báo Môi trường Vite (`frontend/src/types/vite-env.d.ts`):

```typescript
/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
```

---

## 4. Ma Trận Kịch Bản Kiểm Thử & Nghiệm Thu Định Lượng (Verification Matrix)

Kỹ sư thực thi bắt buộc phải kiểm định và đạt kết quả $100\%$ PASS đối với $10$ kịch bản kiểm thử định lượng dưới đây:

| Mã Kịch Bản | Mục Tiêu Kiểm Định                                                    | Lệnh / Thao Tác Thực Thi                             | Kết Quả Kỳ Vọng (Expected Result)                                                                                                                               | Trạng Thái  |
| :---------: | :-------------------------------------------------------------------- | :--------------------------------------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------- | :---------: |
|  **TC-01**  | Cài đặt các gói phụ thuộc không xung đột                              | `cd frontend && npm install`                         | Quá trình cài đặt kết thúc thành công với mã thoát 0; tệp `package-lock.json` được tạo mới, thư mục `node_modules` hiện diện đầy đủ.                            | 🟢 Bắt buộc |
|  **TC-02**  | Kiểm tra biên dịch TypeScript nghiêm ngặt                             | `npm run type-check`                                 | Trình biên dịch `tsc` hoàn tất kiểm tra kiểu toàn bộ mã nguồn `src/` mà không xuất hiện bất kỳ lỗi kiểu dữ liệu nào.                                            | 🟢 Bắt buộc |
|  **TC-03**  | Kiểm tra tuân thủ Linter & Clean Code                                 | `npm run lint`                                       | ESLint kiểm tra toàn bộ tệp `.ts`, `.tsx`; kết quả trả về `0 errors`, `0 warnings`.                                                                             | 🟢 Bắt buộc |
|  **TC-04**  | Kiểm tra tích hợp Pre-commit Hook Monorepo                            | Tại thư mục gốc: `npm run frontend:lint`             | Lệnh nạp thành công từ Root Monorepo, xác nhận tính đồng nhất giữa cấu hình Husky và phân hệ Frontend.                                                          | 🟢 Bắt buộc |
|  **TC-05**  | Khởi chạy Vite Dev Server siêu tốc                                    | `npm run dev`                                        | Máy chủ Vite khởi động trong thời gian $< 500\text{ms}$; terminal hiển thị sẵn sàng tại `http://localhost:5173/`.                                               | 🟢 Bắt buộc |
|  **TC-06**  | Nạp giao diện trên trình duyệt                                        | Mở trình duyệt tại `http://localhost:5173`           | Giao diện Header "VNstormgis", các thẻ tính năng và khu vực placeholder hiển thị sắc nét; Browser Console không có thông báo lỗi đỏ (Zero Red Errors).          | 🟢 Bắt buộc |
|  **TC-07**  | Kiểm định Design System & Bảng màu Tailwind                           | Kiểm tra phần tử trên trình duyệt (Inspect Element)  | Lớp CSS tiện ích `@apply bg-gis-bg` và các biến màu `text-weather-safe`, `text-weather-warning` hiển thị đúng mã màu hex theo thiết kế.                         | 🟢 Bắt buộc |
|  **TC-08**  | Kiểm định Reverse Proxy chuyển tiếp sang `:8080` (Hóa giải RSK-W1-04) | Nhấn nút "Làm mới kiểm tra kết nối" trên giao diện   | Tab Network của trình duyệt ghi nhận request gửi tới `http://localhost:5173/api/v1/locations?format=json` trả về HTTP `200 OK` mà không bị chặn CORS Preflight. | 🟢 Bắt buộc |
|  **TC-09**  | Kiểm định Hot Module Replacement (HMR)                                | Sửa đổi một chuỗi văn bản trong `App.tsx` và lưu tệp | Trình duyệt cập nhật thay đổi trong vòng $< 100\text{ms}$ mà không phải tải lại toàn bộ trang (Full Page Reload).                                               | 🟢 Bắt buộc |
|  **TC-10**  | Đóng gói sản phẩm tĩnh tối ưu (Production Build)                      | `npm run build`                                      | Trình biên dịch tạo ra thư mục `dist/` chứa `index.html` và các bundle JS/CSS đã được minify tối ưu dung lượng.                                                 | 🟢 Bắt buộc |

---

## 5. Tương Thích Kiến Trúc Tầng Ứng Dụng & Kế Hoạch Chuyển Giao (Integration & Handoff)

### 5.1. Bàn Giao Nền Tảng Cho Task W1-11 (Tích Hợp Leaflet 1.9 & Xử Lý Icon Bug)

- **Điểm kết nối:** Thư viện `leaflet`, `react-leaflet`, `@types/leaflet` đã được cài đặt sẵn sàng trong `package.json`.
- **Nhiệm vụ Task W1-11 tiếp nối:**
  1. Viết tệp `src/components/map/leafletConfig.ts` để nạp trực tiếp tài nguyên hình ảnh marker icon từ `leaflet/dist/images/` vào bộ nhớ Vite, khắc phục lỗi mất hình ảnh do Vite băm đường dẫn (Hash relative path).
  2. Khởi tạo đối tượng `L.Icon.Default.mergeOptions`.
  3. Xây dựng component cơ sở `BaseMap.tsx` với tọa độ tâm Việt Nam (`[16.047079, 108.206230]`) và mức thu phóng mặc định `zoom={6}`.

### 5.2. Bàn Giao Khung Nhìn Cho Task W1-12 (Bố Cục App Layout & Các Bảng Điều Khiển)

- **Điểm kết nối:** `App.tsx` của Task W1-10 cung cấp cấu trúc xương sống phân tầng (Header, Main viewport, Footer).
- **Nhiệm vụ Task W1-12 tiếp nối:**
  1. Tách cấu trúc hiện tại thành `AppLayout.tsx`.
  2. Đưa `BaseMap` chiếm toàn bộ $100\text{vh} - 64\text{px}$ không gian màn hình chính.
  3. Cắm các mốc khung nổi (Floating overlays): Bảng điều khiển lớp bản đồ (`LayerControl`), Dải tin cảnh báo khẩn cấp đáy màn hình (`AlertTicker`), và Nút bấm kích hoạt AI Chat Agent góc phải (`ChatDrawer`).

### 5.3. Bàn Giao Cho Task W1-14 (Smoke Test Kết Nối Đầu Cuối E2E)

- **Điểm kết nối:** Dịch vụ `locationService.ts` đã được viết hoàn chỉnh hai phương thức `getAllLocations()` và `getAllLocationsAsGeoJson()`.
- **Nhiệm vụ Task W1-14 tiếp nối:** Nạp thẳng kết quả GeoJSON từ `getAllLocationsAsGeoJson()` vào lớp `GeoJSON` của Leaflet trong `BaseMap.tsx` để hiển thị 68 trạm quan trắc trắc địa trên toàn bộ lãnh thổ Việt Nam.

---

## 6. Sổ Đăng Ký Quản Trị Rủi Ro Kỹ Thuật (Risk Registry & Contingency Plans)

|   Mã Rủi Ro   | Mô Tả Tình Huống Kỹ Thuật Phát Sinh                                     |     Mức Độ     | Kế Hoạch Ứng Phó & Hóa Giải Kỹ Thuật (Contingency Action)                                                                                                                                      |   Trạng Thái    |
| :-----------: | :---------------------------------------------------------------------- | :------------: | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :-------------: |
| **RSK-W1-04** | Trình duyệt chặn CORS khi Frontend (`:5173`) gọi API Backend (`:8080`). |    **Cao**     | **Hóa giải triệt để:** Sử dụng Vite Reverse Proxy trong `vite.config.ts` (`server.proxy: {'/api': 'http://localhost:8080'}`). Toàn bộ request từ code Frontend đi qua cùng nguồn gốc `:5173`.  | 🟢 Đã Hóa Giải  |
| **RSK-W1-11** | Cổng `:5173` bị chiếm dụng hoặc trùng lặp bởi ứng dụng khác.            |    **Thấp**    | Thiết lập `strictPort: false` trong `vite.config.ts`. Vite sẽ tự động tìm cổng kế tiếp (`5174`, `5175`). Khuyến cáo lập trình viên giải phóng cổng bằng lệnh `lsof -ti:5173 \| xargs kill -9`. | 🟢 Đã Kiểm Soát |
| **RSK-W1-12** | Tailwind CSS JIT loại bỏ nhầm class động (Purging dynamic classes).     | **Trung bình** | Tránh ghép chuỗi class động (như `text-${color}-500`). Khai báo rõ ràng các class tường minh trong đối tượng cấu hình hoặc dùng `safelist` trong `tailwind.config.js`.                         | 🟢 Đã Kiểm Soát |
| **RSK-W1-13** | Xung đột phiên bản Node.js giữa các thành viên dự án.                   | **Trung bình** | Dự án yêu cầu môi trường Node.js $\ge 18.0.0$ (khuyến nghị Node 20 LTS hoặc Node 22). Cung cấp tệp `.nvmrc` với nội dung `20` tại thư mục gốc.                                                 | 🟢 Đã Kiểm Soát |
| **RSK-W1-14** | Lỗi TypeScript không nhận diện Path Alias `@/*` khi build.              | **Trung bình** | Cấu hình đồng bộ cả hai tệp: `tsconfig.json` (phần `"paths": {"@/*": ["src/*"]}`) và `vite.config.ts` (phần `resolve.alias: {'@': path.resolve(__dirname, './src')}`).                         | 🟢 Đã Kiểm Soát |

---

## 7. Sổ Tay Hướng Dẫn Kỹ Sư Thực Thi Từng Bước (Developer Step-by-Step Runbook)

Kỹ sư Frontend thực hiện tuần tự các bước sau đây trên cửa sổ dòng lệnh Terminal:

### Bước 1: Di chuyển vào thư mục phân hệ và dọn dẹp các tệp tạm

```bash
cd /Users/dllv/Documents/GitHub/VNstormgis/frontend
```

### Bước 2: Tạo tệp `package.json` và cài đặt các thư viện phụ thuộc

Tạo tệp `frontend/package.json` theo đúng đặc tả tại [Mục 3.1](#31-tệp-cấu-hình-gói-phụ-thuộc-frontendpackagejson) và tiến hành cài đặt:

```bash
npm install
```

_Kiểm tra:_ Xác nhận thông báo `added ... packages in ...s` và không có lỗi xung đột dependency.

### Bước 3: Tạo các tệp cấu hình Build & Tooling

1. Tạo tệp `vite.config.ts` theo đặc tả tại [Mục 3.2](#32-tệp-cấu-hình-vite-build-tool--reverse-proxy-frontendviteconfigts).
2. Tạo tệp `tsconfig.json` và `tsconfig.node.json` theo đặc tả tại [Mục 3.3](#33-tệp-cấu-hình-typescript-frontendtsconfigjson--frontendtsconfignodejson).
3. Tạo tệp `tailwind.config.js` và `postcss.config.js` theo đặc tả tại [Mục 3.4](#34-cấu-hình-tailwind-css--postcss-frontendtailwindconfigjs--frontendpostcssconfigjs).
4. Tạo tệp `.eslintrc.cjs` theo đặc tả tại [Mục 3.13](#313-cấu-hình-linter--môi-trường-client-frontendeslintrccjs--srctypesvite-envdts).
5. Tạo tệp `index.html` tại thư mục gốc `frontend/` theo đặc tả tại [Mục 3.12](#312-tệp-html-khung-nhìn--khởi-tạo-ứng-dụng-frontendindexhtml--frontendsrcmaintsex).

### Bước 4: Tạo cấu trúc mã nguồn TypeScript và Stylesheet

1. Tạo tệp `src/index.css` theo đặc tả tại [Mục 3.5](#35-tệp-css-gốc--reset-giao-diện-bản-đồ-frontendsrcindexcss).
2. Tạo tệp `src/lib/utils.ts` theo đặc tả tại [Mục 3.6](#36-hàm-tiện-ích-kết-hợp-lớp-css-frontendsrclibutilsts).
3. Tạo tệp `src/types/api.ts` và `src/types/location.ts` theo đặc tả tại [Mục 3.7](#37-định-nghĩa-kiểu-dữ-liệu-typescript-frontendsrctypesapits--frontendsrctypeslocationts).
4. Tạo tệp `src/types/vite-env.d.ts` theo đặc tả tại [Mục 3.13](#313-cấu-hình-linter--môi-trường-client-frontendeslintrccjs--srctypesvite-envdts).
5. Tạo tệp `src/services/apiClient.ts` theo đặc tả tại [Mục 3.8](#38-singleton-axios-client-tích-hợp-interceptor-frontendsrcservicesapiclientts).
6. Tạo tệp `src/services/locationService.ts` theo đặc tả tại [Mục 3.9](#39-dịch-vụ-gọi-api-trạm-quan-trắc-frontendsrcserviceslocationservicets).
7. Tạo tệp `src/components/common/StatusBadge.tsx` theo đặc tả tại [Mục 3.10](#310-component-huy-hiệu-trạng-thái-hệ-thống-frontendsrccomponentscommonstatusbadgetsx).
8. Tạo tệp `src/App.tsx` theo đặc tả tại [Mục 3.11](#311-bảng-điều-khiển-kiểm-định-khởi-tạo-frontendsrcapptsx).
9. Tạo tệp `src/main.tsx` theo đặc tả tại [Mục 3.12](#312-tệp-html-khung-nhìn--khởi-tạo-ứng-dụng-frontendindexhtml--frontendsrcmaintsex).

### Bước 5: Thực hiện kiểm định chất lượng mã nguồn & đóng gói

```bash
# 1. Kiểm tra Type-checking nghiêm ngặt
npm run type-check

# 2. Kiểm tra tuân thủ quy tắc Linter
npm run lint

# 3. Thử nghiệm đóng gói tĩnh Production
npm run build
```

_Kiểm tra:_ Cả 3 lệnh đều phải chạy thành công không có lỗi (Exit code 0).

### Bước 6: Khởi chạy máy chủ phát triển và kiểm định kết nối

1. Bảo đảm container PostGIS và Backend Spring Boot đang chạy ở cổng `:8080`:
   ```bash
   # Nếu chưa chạy Backend:
   cd ../backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```
2. Mở một cửa sổ Terminal khác và khởi chạy Frontend:
   ```bash
   cd ../frontend && npm run dev
   ```
3. Mở trình duyệt tại địa chỉ `http://localhost:5173`.
4. Quan sát huy hiệu trạng thái: Xác nhận trạng thái chuyển sang màu xanh **"Spring Boot 3.3 Active | 68 Trạm PostGIS"**.
5. Nhấn nút "Làm mới kiểm tra kết nối" và xác nhận danh sách 5 trạm quan trắc mẫu được hiển thị đầy đủ trên giao diện.

---

## 8. Danh Mục Sản Phẩm Bàn Giao & Checklist Nghiệm Thu (Acceptance Checklist)

### 8.1. Danh Mục Sản Phẩm Bàn Giao (Deliverables)

| STT | Tên Tệp Sản Phẩm                                      | Trạng Thái | Mô Tả Trách Nhiệm Kỹ Thuật                                                                               |
| :-: | :---------------------------------------------------- | :--------: | :------------------------------------------------------------------------------------------------------- |
|  1  | `frontend/package.json`                               |  Cập nhật  | Danh mục thư viện phụ thuộc (React 18, Vite 5, Tailwind 3.4, Leaflet 1.9, Axios, Lucide) và npm scripts. |
|  2  | `frontend/vite.config.ts`                             |  Tạo mới   | Cấu hình Vite Dev Server, Path Alias `@/*`, và Reverse Proxy chuyển tiếp `/api` sang `:8080`.            |
|  3  | `frontend/tsconfig.json` & `tsconfig.node.json`       |  Tạo mới   | Cấu hình trình biên dịch TypeScript 5 với kiểm tra kiểu nghiêm ngặt và path mapping.                     |
|  4  | `frontend/tailwind.config.js` & `postcss.config.js`   |  Tạo mới   | Khai báo bảng màu khí tượng phân cấp cảnh báo thiên tai và nạp công cụ xử lý CSS.                        |
|  5  | `frontend/src/index.css`                              |  Tạo mới   | Chỉ thị Tailwind directives, CSS Reset cho Leaflet Map Container và thanh cuộn giao diện tối.            |
|  6  | `frontend/src/lib/utils.ts`                           |  Tạo mới   | Hàm tiện ích gộp class `cn()` sử dụng `clsx` và `tailwind-merge`.                                        |
|  7  | `frontend/src/types/api.ts` & `src/types/location.ts` |  Tạo mới   | Định nghĩa hợp đồng kiểu dữ liệu `ApiResponse`, `ProblemDetail` RFC 7807, và GeoJSON RFC 7946.           |
|  8  | `frontend/src/services/apiClient.ts`                  |  Tạo mới   | Singleton Axios Instance với baseURL `/api` và interceptors chuẩn hóa lỗi.                               |
|  9  | `frontend/src/services/locationService.ts`            |  Tạo mới   | Dịch vụ gọi API trích xuất danh sách trạm quan trắc trắc địa và kiểm tra sức khỏe Backend.               |
| 10  | `frontend/src/components/common/StatusBadge.tsx`      |  Tạo mới   | Component huy hiệu trạng thái sống còn kết nối thời gian thực.                                           |
| 11  | `frontend/src/App.tsx`                                |  Tạo mới   | Giao diện Bảng Điều Khiển Kiểm Định Khởi Tạo xác nhận liên thông công nghệ toàn diện.                    |
| 12  | `frontend/index.html` & `src/main.tsx`                |  Tạo mới   | HTML5 template chuẩn SEO và điểm khởi động React 18 createRoot.                                          |
| 13  | `docs/week1/Task W1-10.md`                            |  Tạo mới   | Toàn văn tài liệu đặc tả kỹ thuật và cẩm nang vận hành chi tiết Task W1-10.                              |

---

### 8.2. Checklist Tiêu Chí Chấp Nhận (Definition of Done - DoD)

- [ ] **Môi trường nạp trơn tru:** Lệnh `npm install` hoàn tất không phát sinh lỗi xung đột gói phụ thuộc.
- [ ] **Biên dịch Type-safe:** Lệnh `npm run type-check` vượt qua $100\%$ không có cảnh báo hoặc lỗi type.
- [ ] **Tuân thủ quy chuẩn Clean Code:** Lệnh `npm run lint` và lệnh Monorepo `npm run frontend:lint` trả về $0\text{ errors}$, $0\text{ warnings}$.
- [ ] **Khởi động siêu tốc:** Vite Dev Server khởi động trong thời gian $< 500\text{ms}$ tại cổng `5173`.
- [ ] **Không lỗi Console:** Trình duyệt truy cập `http://localhost:5173` hiển thị giao diện mượt mà, không xuất hiện bất kỳ thông báo lỗi đỏ nào trong Browser Console.
- [ ] **Hóa giải CORS 100%:** Reverse Proxy trong `vite.config.ts` chuyển tiếp thành công request `/api/v1/locations` sang Backend `:8080`, nhận về phản hồi `200 OK` với dữ liệu 68 trạm từ PostGIS.
- [ ] **Design System sẵn sàng:** Bảng màu khí tượng của Tailwind CSS và bộ icon Lucide hiển thị sắc nét, chuẩn nhận diện.
- [ ] **Đóng gói tối ưu:** Lệnh `npm run build` xuất thành công gói tĩnh `dist/` sẵn sàng triển khai Docker/Nginx.
- [ ] **Sẵn sàng chuyển giao:** Toàn bộ cấu trúc thư mục và dependencies đã sẵn sàng để chuyển giao ngay cho **Task W1-11: Tích hợp Leaflet 1.9 & Xử lý Leaflet Icon Bug** và **Task W1-12: Dựng Component BaseMap Lãnh thổ Việt Nam & App Layout**.
