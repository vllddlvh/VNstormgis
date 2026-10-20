-- ============================================================================
-- Database Migration Script: V2__create_location_tables.sql
-- Project: Vietnam Weather & Storm Monitoring WebGIS with Grounded AI Agent
-- Sprint: 1 (Week 1 - Day 2) | Task: W1-05
-- Target Database: PostgreSQL 16 LTS + PostGIS 3.4
-- Author: GIS / Database Specialist & Technical Lead
-- Description:
--   1. Tạo bảng locations lưu trữ danh mục điểm quan trắc khí tượng và vị trí
--      địa lý giám sát 63 tỉnh/thành phố trên lãnh thổ Việt Nam.
--   2. Khởi tạo cột không gian geom (Point, EPSG:4326) và boundary (MultiPolygon, EPSG:4326).
--   3. Thiết lập chỉ mục không gian chuyên dụng GiST (Generalized Search Tree)
--      cho cột geom và boundary nhằm tối ưu hóa các truy vấn ST_DWithin, ST_Contains.
--   4. Thiết lập chỉ mục B-Tree trên cột region, is_active và ràng buộc UNIQUE cho code.
--   5. Thiết lập hàm và trigger tự động cập nhật trường thời gian updated_at.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. HÀM TRIGGER CẬP NHẬT THỜI GIAN BIẾN ĐỔI (UPDATED_AT TIMESTAMP)
-- ----------------------------------------------------------------------------
-- Hàm dùng chung cho các bảng nghiệp vụ có nhu cầu tự động gán timestamp mới nhất
-- mỗi khi có thao tác UPDATE bản ghi.
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ----------------------------------------------------------------------------
-- 2. TẠO BẢNG LOCATIONS (ĐIỂM QUAN TRẮC & VỊ TRÍ ĐỊA LÝ GIÁM SÁT)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    region VARCHAR(64) NOT NULL,
    geom GEOMETRY(Point, 4326) NOT NULL,
    boundary GEOMETRY(MultiPolygon, 4326),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------------------------------------------------------
-- 3. THIẾT LẬP CHỈ MỤC KHÔNG GIAN VÀ CHỈ MỤC NGHIỆP VỤ (INDEXING)
-- ----------------------------------------------------------------------------
-- Chỉ mục không gian GiST cho tọa độ điểm quan trắc:
-- Tăng tốc độ lọc theo Bounding Box (&&) của Leaflet và đo khoảng cách ST_DistanceSphere / ST_DWithin
CREATE INDEX IF NOT EXISTS idx_locations_geom ON locations USING GIST (geom);

-- Chỉ mục không gian GiST cho đa giác ranh giới hành chính:
-- Tăng tốc độ kiểm tra giao cắt ST_Intersects / ST_Contains với tâm bão và vành đai mây bão
CREATE INDEX IF NOT EXISTS idx_locations_boundary ON locations USING GIST (boundary);

-- Chỉ mục B-Tree trên phân vùng địa lý (Bắc Bộ, Trung Bộ, Nam Bộ...):
-- Hỗ trợ phân trang và lọc danh sách trạm theo khu vực nghiệp vụ
CREATE INDEX IF NOT EXISTS idx_locations_region ON locations (region);

-- Chỉ mục B-Tree trên cờ kích hoạt:
-- Tối ưu hóa cho Background Scheduled Worker chỉ quét các điểm đang kích hoạt thu thập thời tiết
CREATE INDEX IF NOT EXISTS idx_locations_is_active ON locations (is_active);

-- ----------------------------------------------------------------------------
-- 4. THIẾT LẬP TRIGGER TỰ ĐỘNG CẬP NHẬT UPDATED_AT
-- ----------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_locations_updated_at ON locations;
CREATE TRIGGER trg_locations_updated_at
BEFORE UPDATE ON locations
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- ----------------------------------------------------------------------------
-- 5. CHÚ THÍCH METADATA (DATA DICTIONARY DOCUMENTATION)
-- ----------------------------------------------------------------------------
COMMENT ON TABLE locations IS 'Bảng quản lý trạm quan trắc khí tượng và vị trí địa lý 63 tỉnh/thành Việt Nam';
COMMENT ON COLUMN locations.id IS 'Khóa chính UUID ngẫu nhiên duy nhất toàn cầu (RFC 4122 v4)';
COMMENT ON COLUMN locations.code IS 'Mã định danh duy nhất của trạm/tỉnh (ví dụ: HAN, SGN, DNG, QNH)';
COMMENT ON COLUMN locations.name IS 'Tên hiển thị địa phương (ví dụ: Hà Nội, TP. Hồ Chí Minh, Đà Nẵng)';
COMMENT ON COLUMN locations.region IS 'Phân vùng địa lý (Bắc Bộ, Bắc Trung Bộ, Nam Trung Bộ, Tây Nguyên, Nam Bộ)';
COMMENT ON COLUMN locations.geom IS 'Tọa độ hình học Point WGS84 (EPSG:4326), lưu trữ định dạng (Kinh độ, Vĩ độ)';
COMMENT ON COLUMN locations.boundary IS 'Ranh giới hành chính MultiPolygon WGS84 (EPSG:4326) phục vụ phân tích vùng bão';
COMMENT ON COLUMN locations.is_active IS 'Cờ kích hoạt thu thập dữ liệu thời tiết định kỳ (true: hoạt động, false: tạm dừng)';
COMMENT ON COLUMN locations.created_at IS 'Thời điểm khởi tạo bản ghi trong cơ sở dữ liệu';
COMMENT ON COLUMN locations.updated_at IS 'Thời điểm cập nhật thông tin trạm gần nhất (tự động qua trigger)';
