-- ============================================================================
-- Database Migration Script: V5__create_alert_chat_and_log_tables.sql
-- Project: Vietnam Weather & Storm Monitoring WebGIS with Grounded AI Agent
-- Sprint: 1 (Week 2 - Day 6) | Task: W2-01
-- Target Database: PostgreSQL 16 LTS + PostGIS 3.4
-- Author: GIS / Database Specialist & Backend Engineer
-- Canonical Spec: docs/The plans of project/1. ERD.md (Section 2.5 - 2.9 & 5)
-- Description:
--   1. Tạo bảng alert_thresholds: bộ ngưỡng định lượng phát hiện vượt ngưỡng.
--   2. Tạo bảng alerts: cảnh báo do AI Agent chủ động sinh (kèm lời giải thích
--      Gemini ai_explanation) — nền tảng cho các tuần AI Agent.
--   3. Tạo bảng chat_sessions & chat_messages: lịch sử hỏi đáp người dùng - AI.
--   4. Tạo bảng data_sync_logs: nhật ký kiểm toán mọi lượt nạp OWM/GDACS
--      (bắt buộc cho Task W2-03, W2-05 và script verify-week2-pipeline.sh).
--   5. Seed 3 ngưỡng thiên tai mặc định theo Quyết định 18/2021/QĐ-TTg.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. BẢNG ALERT_THRESHOLDS (BỘ NGƯỠNG CẢNH BÁO ĐỊNH LƯỢNG)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alert_thresholds (
    id SERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    parameter VARCHAR(64) NOT NULL, -- 'rain_1h', 'rain_3h', 'wind_speed', 'wind_gust', 'storm_dist'
    operator VARCHAR(8) NOT NULL,   -- '>=', '>', '<=', '<', '='
    threshold_value DOUBLE PRECISION NOT NULL,
    severity_level VARCHAR(32) NOT NULL, -- 'INFO', 'WARNING', 'DANGER', 'CRITICAL'
    description TEXT NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP TRIGGER IF EXISTS trg_alert_thresholds_updated_at ON alert_thresholds;
CREATE TRIGGER trg_alert_thresholds_updated_at
BEFORE UPDATE ON alert_thresholds
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- ----------------------------------------------------------------------------
-- 2. BẢNG ALERTS (CẢNH BÁO CHỦ ĐỘNG SINH BỞI AI AGENT)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    threshold_id INT REFERENCES alert_thresholds(id) ON DELETE SET NULL,
    location_id UUID REFERENCES locations(id) ON DELETE SET NULL,
    storm_id VARCHAR(64) REFERENCES storms(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    severity_level VARCHAR(32) NOT NULL,
    trigger_reason TEXT NOT NULL,
    ai_explanation TEXT NOT NULL,
    affected_geom GEOMETRY(Geometry, 4326),
    status VARCHAR(32) NOT NULL DEFAULT 'active', -- 'active' | 'resolved' | 'dismissed'
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Chỉ mục GiST: lọc cảnh báo trong khung nhìn (Bounding Box) của Leaflet
CREATE INDEX IF NOT EXISTS idx_alerts_affected_geom ON alerts USING GIST (affected_geom);

-- Chỉ mục phục vụ thanh danh sách cảnh báo khẩn cấp đang kích hoạt
CREATE INDEX IF NOT EXISTS idx_alerts_status_time ON alerts (status, triggered_at DESC);

-- ----------------------------------------------------------------------------
-- 3. BẢNG CHAT_SESSIONS (PHIÊN HỘI THOẠI NGƯỜI DÙNG - AI AGENT)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_token VARCHAR(128) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL DEFAULT 'Hội thoại mới',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP TRIGGER IF EXISTS trg_chat_sessions_updated_at ON chat_sessions;
CREATE TRIGGER trg_chat_sessions_updated_at
BEFORE UPDATE ON chat_sessions
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- ----------------------------------------------------------------------------
-- 4. BẢNG CHAT_MESSAGES (TIN NHẮN HỎI - ĐÁP NGÔN NGỮ TỰ NHIÊN)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    sender_type VARCHAR(32) NOT NULL, -- 'user' | 'assistant' | 'system'
    content TEXT NOT NULL,
    referenced_alert_id UUID REFERENCES alerts(id) ON DELETE SET NULL,
    spatial_filter JSONB,
    tokens_used INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Chỉ mục tải lại lịch sử hội thoại theo trình tự thời gian
CREATE INDEX IF NOT EXISTS idx_chat_messages_session_time ON chat_messages (session_id, created_at ASC);

-- ----------------------------------------------------------------------------
-- 5. BẢNG DATA_SYNC_LOGS (NHẬT KÝ KIỂM TOÁN TIẾN TRÌNH NẠP DỮ LIỆU)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS data_sync_logs (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(64) NOT NULL, -- 'OPENWEATHERMAP', 'GDACS', 'AI_MONITOR'
    status VARCHAR(32) NOT NULL, -- 'RUNNING', 'SUCCESS', 'FAILED'
    records_processed INT NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

-- Chỉ mục tra cứu lượt nạp gần nhất theo nguồn (huy hiệu "Last Sync" của W2-02)
CREATE INDEX IF NOT EXISTS idx_sync_logs_source_time ON data_sync_logs (source, started_at DESC);

-- ----------------------------------------------------------------------------
-- 6. SEED BỘ NGƯỠNG THIÊN TAI MẶC ĐỊNH (QUYẾT ĐỊNH 18/2021/QĐ-TTG)
-- ----------------------------------------------------------------------------
-- Sử dụng WHERE NOT EXISTS để đảm bảo tính lũy đẳng khi tái thực thi trực tiếp
-- bằng psql (alert_thresholds dùng khóa SERIAL nên không áp dụng ON CONFLICT).
-- Đơn vị đo được ghi rõ trong description theo đúng chuẩn tắc ERD (Mục 2, hàng 4).

INSERT INTO alert_thresholds (name, parameter, operator, threshold_value, severity_level, description)
SELECT 'Mưa rất to 1h', 'rain_1h', '>=', 50.0, 'DANGER',
       'Lượng mưa tích lũy trong 1 giờ đạt hoặc vượt 50 mm - mưa rất to, nguy cơ ngập úng cục bộ và lũ quét (tham chiếu phân cấp Quyết định 18/2021/QĐ-TTg)'
WHERE NOT EXISTS (SELECT 1 FROM alert_thresholds WHERE name = 'Mưa rất to 1h');

INSERT INTO alert_thresholds (name, parameter, operator, threshold_value, severity_level, description)
SELECT 'Gió mạnh cấp 7-8', 'wind_speed', '>=', 15.0, 'WARNING',
       'Vận tốc gió trung bình đạt từ 15 m/s trở lên, tương đương gió mạnh cấp 7-8 thang Beaufort - cảnh báo nguy cơ đổ cây, hư hại mái nhà và công trình nhẹ'
WHERE NOT EXISTS (SELECT 1 FROM alert_thresholds WHERE name = 'Gió mạnh cấp 7-8');

INSERT INTO alert_thresholds (name, parameter, operator, threshold_value, severity_level, description)
SELECT 'Khoảng cách tâm bão gần', 'storm_dist', '<=', 300.0, 'CRITICAL',
       'Tâm bão nhiệt đới tiến vào phạm vi 300 km tính từ trạm quan trắc - kích hoạt trạng thái theo dõi khẩn cấp và cảnh báo sớm cho lực lượng ứng phó'
WHERE NOT EXISTS (SELECT 1 FROM alert_thresholds WHERE name = 'Khoảng cách tâm bão gần');

-- ----------------------------------------------------------------------------
-- 7. CHÚ THÍCH METADATA
-- ----------------------------------------------------------------------------
COMMENT ON TABLE alert_thresholds IS 'Bộ ngưỡng định lượng phát hiện thiên tai vượt ngưỡng (mưa, gió, khoảng cách bão) theo Quyết định 18/2021/QĐ-TTg';
COMMENT ON TABLE alerts IS 'Cảnh báo thiên tai do AI Agent chủ động phát hiện và giải thích bằng Gemini (ai_explanation)';
COMMENT ON TABLE chat_sessions IS 'Phiên hội thoại giữa người dùng và AI Agent cảnh báo';
COMMENT ON TABLE chat_messages IS 'Tin nhắn hỏi - đáp; referenced_alert_id liên kết cảnh báo được hỏi sâu; spatial_filter lưu khung nhìn Leaflet';
COMMENT ON TABLE data_sync_logs IS 'Nhật ký kiểm toán các tiến trình nạp dữ liệu định kỳ OPENWEATHERMAP (15 phút) và GDACS (30 phút)';
