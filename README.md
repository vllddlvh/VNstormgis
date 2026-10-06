# Vietnam Weather & Storm Monitoring WebGIS with Grounded AI Warning Agent

## Hệ Thống WebGIS Giám Sát Mưa Gió Bão Tích Hợp AI Agent Cảnh Báo

---

## 1. Giới Thiệu Dự Án (Project Overview)

**VNstormgis** là giải pháp nền tảng công nghệ toàn diện phục vụ giám sát lượng mưa, gió giật, bão lũ và cảnh báo thiên tai sớm tại Việt Nam. Hệ thống tích hợp sâu giữa công nghệ bản đồ số địa không gian (WebGIS) và mô hình ngôn ngữ lớn (Grounded AI Warning Agent) nhằm hỗ trợ người dân và cơ quan quản lý tra cứu, cập nhật thông tin chuẩn xác theo thời gian thực.

### Công Nghệ Chủ Đạo (Core Tech Stack)

- **Backend:** Spring Boot 3.3.x, Java 21 LTS, Hibernate Spatial 6, JTS Topology Suite, Flyway.
- **Spatial Database & Cache:** PostgreSQL 16 + PostGIS 3.4, Redis 7.2.
- **Frontend:** React 18, TypeScript 5.x, Vite, Tailwind CSS, Leaflet 1.9 (`react-leaflet`).
- **AI & LLM:** Google Gemini API kết hợp Grounding kỹ thuật kiểm soát ảo giác (Anti-hallucination).
- **DevOps & Hạ Tầng:** Docker Multi-stage, Docker Compose, Nginx Reverse Proxy, GitHub Actions CI/CD.

---

## 2. Cấu Trúc Monorepo (Monorepo Layout)

Dự án được tổ chức theo mô hình **Monorepo** tinh gọn, phân tách 4 phân hệ chính:

```text
VNstormgis/
├── .github/workflows/         # CI/CD Workflows (backend-ci.yml, frontend-ci.yml)
├── .husky/                    # Git Hooks tự động hóa (commit-msg, pre-commit)
├── backend/                   # Backend API Service (Spring Boot 3.3, Java 21)
├── frontend/                  # WebGIS Client (React 18, Vite, Leaflet, Tailwind)
├── deployment/                # Hạ tầng Docker Compose, Nginx gateway, biến môi trường
├── docs/                      # Toàn bộ tài liệu kiến trúc, BRD/SRS & nhật ký Sprint
├── .editorconfig              # Quy chuẩn soạn thảo đa IDE
├── .gitignore                 # Bộ lọc loại trừ rác và mã bảo mật
├── .lintstagedrc.json         # Cấu hình tự động format mã nguồn
├── commitlint.config.js       # Quy chuẩn Conventional Commits v1.0.0
├── package.json               # Công cụ quản trị Monorepo cấp Root
├── AGENT.md                   # Cẩm nang vận hành và kim chỉ nam kỹ thuật cho AI Agent
├── PROGRESS.md                # Bảng theo dõi tiến độ thực thi & trạng thái kiểm định
└── README.md                  # Hướng dẫn Onboarding nhà phát triển
```

---

## 3. Hướng Dẫn Thiết Lập Môi Trường Phát Triển (Quickstart Guide)

### Yêu Cầu Tiên Quyết (Prerequisites)

- **Node.js:** v20.x hoặc v22.x LTS (`node -v`)
- **Java Development Kit (JDK):** OpenJDK 21 LTS (`java -version`)
- **Docker & Docker Compose:** Docker Desktop 4.x+ (`docker compose version`)
- **Git:** >= 2.38

### Các Bước Bắt Đầu Nhanh (Step-by-Step Onboarding)

```bash
# Bước 1: Clone kho lưu trữ
git clone https://github.com/vllddlvh/WebGIS-for-weather-of-Vietnam.git
cd VNstormgis

# Bước 2: Chuyển sang nhánh staging mới nhất
git checkout staging
git pull origin staging

# Bước 3: Cài đặt công cụ quản trị Monorepo & kích hoạt Git Hooks
npm install

# Bước 4: Sao chép tệp biến môi trường mẫu
cp deployment/env/.env.example deployment/env/.env.local

# Bước 5: Khởi động cơ sở dữ liệu PostGIS & Redis bằng Docker
docker compose -f deployment/docker-compose.yml up -d

# Bước 6: Tạo nhánh tính năng cho nhiệm vụ được giao
git checkout -b feature/<mã-task>-<tên-tính-năng>
```

---

## 4. Quy Chế Phân Nhánh & Cam Kết Mã Nguồn (Git Workflow)

### 4.1. Sơ Đồ Nhánh (Branching Model)

- **`main`**: Nhánh mã nguồn phát hành chính thức (Production). Cấm push trực tiếp, bắt buộc PR có duyệt từ Tech Lead.
- **`staging`**: Nhánh tích hợp liên tục (Continuous Integration). Mọi nhánh tính năng đều tạo ra và hợp nhất vào đây.
- **`feature/*`**, **`bugfix/*`**, **`chore/*`**: Nhánh làm việc cho từng task riêng lẻ.

### 4.2. Quy Chuẩn Commit (Conventional Commits v1.0.0)

Mọi commit bắt buộc tuân theo định dạng:

```text
<loại-commit>(<phạm-vi>): <mô-tả-ngắn-gọn> [Mã-Task]
```

**Ví dụ hợp lệ:**

- `feat(location): implement Spatial JTS Entity and GeoJSON DTO [W1-08]`
- `chore(repo): configure spotless gradle plugin and husky pre-commit [W1-01]`
- `fix(map): resolve missing leaflet marker shadow icon in vite bundler [W1-11]`

Các loại commit được hỗ trợ: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `ci`, `revert`.  
Hệ thống sử dụng **Husky + Commitlint** để tự động kiểm tra định dạng trước khi commit được chấp nhận.

---

## 5. Lệnh Quản Trị Hệ Thống Thường Dùng (Useful Commands)

| Lệnh Thực Thi                                           | Mục Đích                                                        |
| :------------------------------------------------------ | :-------------------------------------------------------------- |
| `npm run lint:root`                                     | Kiểm tra định dạng JSON, YAML, Markdown toàn dự án              |
| `npm run format:root`                                   | Tự động format toàn bộ JSON, YAML, Markdown                     |
| `npm run backend:format`                                | Tự động format mã nguồn Java bằng Google Java Format (Spotless) |
| `npm run frontend:lint`                                 | Chạy ESLint kiểm tra lỗi mã nguồn React TypeScript              |
| `npm run frontend:format`                               | Tự động format mã nguồn React bằng Prettier                     |
| `docker compose -f deployment/docker-compose.yml up -d` | Khởi động PostGIS 16 và Redis 7.2                               |
| `docker compose -f deployment/docker-compose.yml ps`    | Kiểm tra trạng thái healthcheck các container                   |

---

## 6. Quản Trị Dự Án & Cẩm Nang Thực Thi (Governance & Engineering Harness)

Dự án áp dụng chặt chẽ mô hình **Harness Engineering** để phân tách rõ ràng giữa hướng dẫn phát triển, cẩm nang vận hành cho AI Agent và bảng theo dõi tiến độ:

- 📋 **[PROGRESS.md](file:///Users/dllv/Documents/GitHub/VNstormgis/PROGRESS.md)**: Nguồn chân lý duy nhất (Single Source of Truth) theo dõi toàn bộ tiến độ thực thi, ma trận 15 nhiệm vụ Tuần 1, vận tốc Sprint, trạng thái các cổng kiểm định chất lượng (Quality Gates) và nhật ký bàn giao nhiệm vụ.
- 🤖 **[AGENT.md](file:///Users/dllv/Documents/GitHub/VNstormgis/AGENT.md)**: Cẩm nang vận hành thực thi, mô hình tư duy kiến trúc (Modular Monolith), các rào chắn kỹ thuật bất biến (Spatial Coordinates WGS84, Grounded AI anti-hallucination) và bảng lệnh chuẩn dành cho AI Agent.

---

## 7. Tài Liệu Dự Án (Documentation Reference)

- [Kế hoạch chi tiết Tuần 1](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/week1/README.md)
- [Kiến trúc hệ thống (System Architecture)](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/5.%20System%20Architecture.md)
- [Đặc tả RESTful API](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/2.%20API%20docs.md)
- [Thiết kế CSDL không gian ERD](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/1.%20ERD.md)
- [Hạ tầng triển khai & CI/CD](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/7.%20Deployment%20Infrastructure.md)
- [Lộ trình tổng thể Master Timeline](file:///Users/dllv/Documents/GitHub/VNstormgis/docs/The%20plans%20of%20project/8.%20Master%20Timeline.md)

---

_© 2026 UET — WebGIS for Weather of Vietnam Team._
