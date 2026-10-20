-- ============================================================================
-- Database Migration Script: V1__init_spatial_extensions.sql
-- Project: Vietnam Weather & Storm Monitoring WebGIS with Grounded AI Agent
-- Sprint: 1 (Week 1 - Day 2) | Task: W1-04
-- Target Database: PostgreSQL 16 LTS + PostGIS 3.4
-- Author: GIS / Database Specialist & Technical Lead
-- Description:
--   1. Kích hoạt tiện ích uuid-ossp hỗ trợ sinh khóa chính UUID v4 chuẩn RFC 4122.
--   2. Kích hoạt tiện ích postgis hỗ trợ mô hình dữ liệu địa không gian, hệ quy
--      chiếu tọa độ WGS84 (EPSG:4326), cây chỉ mục không gian GiST và các phép
--      toán hình học trắc địa thời gian thực.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. KÍCH HOẠT TIỆN ÍCH MỞ RỘNG ĐỊNH DANH TOÀN CẦU (UUID-OSSP)
-- ----------------------------------------------------------------------------
-- Tiện ích này cung cấp hàm uuid_generate_v4(), sinh khóa chính UUID ngẫu nhiên
-- có tính tương thích cao với Hibernate Spatial và Spring Data JPA Entity.
-- Mệnh đề "IF NOT EXISTS" đảm bảo tính lũy đẳng (Idempotency) khi chạy lại script.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ----------------------------------------------------------------------------
-- 2. KÍCH HOẠT TIỆN ÍCH MỞ RỘNG ĐỊA KHÔNG GIAN (POSTGIS)
-- ----------------------------------------------------------------------------
-- PostGIS nạp các kiểu dữ liệu hình học GEOMETRY / GEOGRAPHY, bảng hệ quy chiếu
-- spatial_ref_sys và toàn bộ thư viện thuật toán trắc địa (GEOS, PROJ, GDAL).
-- Đây là nền tảng cốt lõi cho bảng locations (W1-05) và bảng storm_tracks.
CREATE EXTENSION IF NOT EXISTS "postgis";

-- ----------------------------------------------------------------------------
-- 3. GHI CHÚ BẢO TRÌ & BẢO ĐẢM QUYỀN TRUY CẬP (ACCESS VERIFICATION)
-- ----------------------------------------------------------------------------
-- Đảm bảo schema public chứa đầy đủ các bảng danh mục PostGIS vừa tạo.
COMMENT ON EXTENSION "uuid-ossp" IS 'UUID generator functions according to RFC 4122 standards';
COMMENT ON EXTENSION "postgis" IS 'PostGIS geometry and spatial processing functions';
