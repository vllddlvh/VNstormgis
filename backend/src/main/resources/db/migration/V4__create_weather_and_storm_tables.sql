-- ============================================================================
-- Database Migration Script: V4__create_weather_and_storm_tables.sql
-- Project: Vietnam Weather & Storm Monitoring WebGIS with Grounded AI Agent
-- Sprint: 1 (Week 2 - Day 6) | Task: W2-01
-- Target Database: PostgreSQL 16 LTS + PostGIS 3.4
-- Author: GIS / Database Specialist & Backend Engineer
-- Canonical Spec: docs/The plans of project/1. ERD.md (Section 2.2, 2.3, 2.4 & 5)
-- Description:
--   1. Tạo bảng weather_records: chuỗi thời gian khí tượng nạp từ OpenWeatherMap
--      cho 68 trạm quan trắc (chu kỳ 15 phút/lần, Task W2-03).
--   2. Tạo bảng storms: danh mục bão nhiệt đới từ GDACS, khóa chính là mã sự
--      kiện GDACS (VARCHAR(64)), trạng thái active/dissipated (SYS-RULE-001, W2-05).
--   3. Tạo bảng storm_tracks: quỹ đạo lịch sử & dự báo tâm bão kèm vùng ảnh
--      hưởng (impact_polygon), ràng buộc duy nhất (storm_id, track_type, forecast_time).
--   4. Thiết lập chỉ mục tổng hợp & chỉ mục không gian GiST tối ưu truy vấn
--      DISTINCT ON (Task W2-07) và ST_DWithin / ST_Intersects.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. BẢNG WEATHER_RECORDS (CHUỖI THỜI GIAN KHÍ TƯỢNG OPENWEATHERMAP)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS weather_records (
    id BIGSERIAL PRIMARY KEY,
    location_id UUID NOT NULL REFERENCES locations(id) ON DELETE CASCADE,
    recorded_at TIMESTAMPTZ NOT NULL,
    temperature DOUBLE PRECISION NOT NULL,
    feels_like DOUBLE PRECISION NOT NULL,
    humidity DOUBLE PRECISION NOT NULL,
    pressure DOUBLE PRECISION NOT NULL,
    wind_speed DOUBLE PRECISION NOT NULL,
    wind_direction DOUBLE PRECISION NOT NULL,
    wind_gust DOUBLE PRECISION NOT NULL DEFAULT 0,
    rain_1h DOUBLE PRECISION NOT NULL DEFAULT 0,
    rain_3h DOUBLE PRECISION NOT NULL DEFAULT 0,
    weather_condition VARCHAR(64) NOT NULL,
    weather_description VARCHAR(255) NOT NULL,
    raw_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Chỉ mục tổng hợp phục vụ truy vấn "bản ghi mới nhất mỗi trạm" (DISTINCT ON)
-- mục tiêu < 15ms cho 68 trạm tại Task W2-07
CREATE INDEX IF NOT EXISTS idx_weather_records_loc_time
    ON weather_records (location_id, recorded_at DESC);

-- Chỉ mục phục vụ truy vấn chuỗi thời gian 24h (GET /api/v1/weather/history)
CREATE INDEX IF NOT EXISTS idx_weather_records_recorded_at
    ON weather_records (recorded_at DESC);

-- ----------------------------------------------------------------------------
-- 2. BẢNG STORMS (DANH MỤC BÃO NHIỆT ĐỚI TỪ GDACS)
-- ----------------------------------------------------------------------------
-- Khóa chính id chính là mã sự kiện GDACS (ví dụ: TC1000987) -> đảm bảo lũy
-- đẳng khi StormSyncScheduler nạp lại cùng một sự kiện (upsert theo id).
CREATE TABLE IF NOT EXISTS storms (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    year INT NOT NULL,
    category VARCHAR(64) NOT NULL,
    max_wind_speed DOUBLE PRECISION NOT NULL,
    min_pressure DOUBLE PRECISION NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active', -- 'active' | 'dissipated'
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ,
    geom_current GEOMETRY(Point, 4326) NOT NULL,
    raw_metadata JSONB,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Chỉ mục không gian GiST: tăng tốc ST_DWithin (trạm nằm trong vành đai bão)
CREATE INDEX IF NOT EXISTS idx_storms_geom_current ON storms USING GIST (geom_current);

-- Chỉ mục nghiệp vụ: lớp bản đồ bão khẩn cấp chỉ quét status = 'active'
CREATE INDEX IF NOT EXISTS idx_storms_status ON storms (status);

-- Tái sử dụng hàm trigger dùng chung đã tạo ở Flyway V2 (Task W1-05):
-- tự động gán updated_at mỗi khi GDACS cập nhật vị trí tâm bão
DROP TRIGGER IF EXISTS trg_storms_updated_at ON storms;
CREATE TRIGGER trg_storms_updated_at
BEFORE UPDATE ON storms
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- ----------------------------------------------------------------------------
-- 3. BẢNG STORM_TRACKS (QUỸ ĐẠO LỊCH SỬ & DỰ BÁO CỦA BÃO)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS storm_tracks (
    id BIGSERIAL PRIMARY KEY,
    storm_id VARCHAR(64) NOT NULL REFERENCES storms(id) ON DELETE CASCADE,
    track_type VARCHAR(32) NOT NULL, -- 'historical' | 'forecast'
    forecast_time TIMESTAMPTZ NOT NULL,
    geom GEOMETRY(Point, 4326) NOT NULL,
    wind_speed DOUBLE PRECISION NOT NULL,
    wind_gust DOUBLE PRECISION NOT NULL DEFAULT 0,
    pressure DOUBLE PRECISION NOT NULL,
    category VARCHAR(64) NOT NULL,
    impact_radius_km DOUBLE PRECISION NOT NULL DEFAULT 100,
    impact_polygon GEOMETRY(Polygon, 4326),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_storm_track UNIQUE (storm_id, track_type, forecast_time)
);

-- Chỉ mục GiST vẽ quỹ đạo bão trên Leaflet (Tuần 3: Storm Tracking Layer)
CREATE INDEX IF NOT EXISTS idx_storm_tracks_geom ON storm_tracks USING GIST (geom);

-- Chỉ mục GiST kiểm tra trạm giao cắt vùng gió bão nguy hiểm (ST_Intersects)
CREATE INDEX IF NOT EXISTS idx_storm_tracks_impact_polygon ON storm_tracks USING GIST (impact_polygon);

-- Chỉ mục B-Tree tải quỹ đạo một cơn bão theo đúng trình tự thời gian
CREATE INDEX IF NOT EXISTS idx_storm_tracks_time ON storm_tracks (storm_id, forecast_time ASC);

-- ----------------------------------------------------------------------------
-- 4. CHÚ THÍCH METADATA (DATA DICTIONARY DOCUMENTATION)
-- ----------------------------------------------------------------------------
COMMENT ON TABLE weather_records IS 'Chuỗi thời gian quan trắc khí tượng (mưa, gió, nhiệt độ, áp suất) nạp từ OpenWeatherMap mỗi 15 phút cho 68 trạm';
COMMENT ON COLUMN weather_records.recorded_at IS 'Thời điểm quan trắc thực tế do OpenWeatherMap cung cấp (trường dt trong payload)';
COMMENT ON COLUMN weather_records.rain_1h IS 'Lượng mưa tích lũy 1 giờ gần nhất (mm), mặc định 0 khi API không trả trường rain.1h';
COMMENT ON COLUMN weather_records.rain_3h IS 'Lượng mưa tích lũy 3 giờ gần nhất (mm), mặc định 0 khi API không trả trường rain.3h';
COMMENT ON COLUMN weather_records.raw_data IS 'Payload JSON gốc từ OpenWeatherMap phục vụ audit và mở rộng trường dữ liệu';
COMMENT ON TABLE storms IS 'Danh mục bão nhiệt đới từ GDACS; khóa chính là mã sự kiện GDACS; status: active (đang hoạt động) / dissipated (đã tan)';
COMMENT ON COLUMN storms.geom_current IS 'Tọa độ tâm bão cập nhật mới nhất, Point WGS84 (EPSG:4326)';
COMMENT ON TABLE storm_tracks IS 'Quỹ đạo tâm bão theo mốc thời gian: track_type = historical (đã đi qua) hoặc forecast (dự báo); kèm vùng ảnh hưởng impact_polygon';
COMMENT ON CONSTRAINT uq_storm_track ON storm_tracks IS 'Chống trùng mốc quỹ đạo khi Worker nạp lại cùng một bản tin GDACS';
