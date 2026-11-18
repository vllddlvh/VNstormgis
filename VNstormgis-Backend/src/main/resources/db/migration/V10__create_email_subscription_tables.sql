-- ============================================================================
-- Database Migration Script: V10__create_email_subscription_tables.sql
-- Project: Vietnam Weather & Storm Monitoring WebGIS with Grounded AI Agent
-- Target Database: PostgreSQL 16 LTS + PostGIS 3.4
-- Description:
--   1. Tạo bảng email_subscriptions: quản lý đăng ký nhận thông báo cảnh báo
--      thiên tai qua email theo Trạm quan trắc (STATION) hoặc Tọa độ người dùng
--      (COORDINATE), hỗ trợ bán kính không gian (km), mức cảnh báo tối thiểu,
--      xác thực OTP 6 chữ số / liên kết token SHA-256 và hủy đăng ký 1-click.
--   2. Thiết lập chỉ mục không gian GiST trên target_geom (EPSG:4326) để lọc
--      ST_DWithin nhanh tức thì khi có cảnh báo mới được xuất bản.
--   3. Tạo bảng email_notification_logs: nhật ký kiểm toán & chống gửi trùng
--      lũy đẳng (UNIQUE subscription_id, alert_id) với ON DELETE CASCADE.
-- ============================================================================

CREATE TABLE IF NOT EXISTS email_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    location_id UUID REFERENCES locations(id) ON DELETE CASCADE,
    target_geom GEOMETRY(Point, 4326) NOT NULL,
    radius_km DOUBLE PRECISION NOT NULL DEFAULT 50.0,
    min_severity VARCHAR(16) NOT NULL DEFAULT 'WARNING',
    label VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    otp_hash VARCHAR(64),
    verify_token_hash VARCHAR(64) UNIQUE,
    verify_expires_at TIMESTAMPTZ,
    unsubscribe_token VARCHAR(64) NOT NULL UNIQUE,
    verified_at TIMESTAMPTZ,
    unsubscribed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Bắt buộc chỉ mục không gian GiST trên mọi cột geometry theo chuẩn dự án (AGENTS.md #1)
CREATE INDEX IF NOT EXISTS idx_email_subscriptions_target_geom
    ON email_subscriptions USING GIST (target_geom);

CREATE INDEX IF NOT EXISTS idx_email_subscriptions_status
    ON email_subscriptions (status);

CREATE INDEX IF NOT EXISTS idx_email_subscriptions_email
    ON email_subscriptions (email);

CREATE INDEX IF NOT EXISTS idx_email_subscriptions_location_id
    ON email_subscriptions (location_id);

DROP TRIGGER IF EXISTS trg_email_subscriptions_updated_at ON email_subscriptions;
CREATE TRIGGER trg_email_subscriptions_updated_at
BEFORE UPDATE ON email_subscriptions
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE email_subscriptions IS
    'Đăng ký nhận thông báo cảnh báo khí tượng - bão lũ qua email theo trạm hoặc tọa độ GPS';
COMMENT ON COLUMN email_subscriptions.target_geom IS
    'Tọa độ tâm theo dõi Point WGS84 (EPSG:4326) định dạng [lng, lat] phục vụ truy vấn ST_DWithin';
COMMENT ON COLUMN email_subscriptions.otp_hash IS
    'Mã băm SHA-256 hex của mã OTP 6 chữ số xác thực quyền sở hữu email';
COMMENT ON COLUMN email_subscriptions.verify_token_hash IS
    'Mã băm SHA-256 hex của token liên kết xác thực 1-click';
COMMENT ON COLUMN email_subscriptions.unsubscribe_token IS
    'Token ngẫu nhiên 256-bit phục vụ liên kết hủy đăng ký 1-click trong mỗi email cảnh báo';

CREATE TABLE IF NOT EXISTS email_notification_logs (
    id BIGSERIAL PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES email_subscriptions(id) ON DELETE CASCADE,
    alert_id UUID REFERENCES alerts(id) ON DELETE CASCADE,
    notification_type VARCHAR(24) NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    delivery_status VARCHAR(20) NOT NULL,
    error_message TEXT,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_email_notification_sub_alert UNIQUE (subscription_id, alert_id)
);

CREATE INDEX IF NOT EXISTS idx_email_notification_logs_email_time
    ON email_notification_logs (recipient_email, sent_at DESC);

CREATE INDEX IF NOT EXISTS idx_email_notification_logs_alert_id
    ON email_notification_logs (alert_id);

COMMENT ON TABLE email_notification_logs IS
    'Nhật ký kiểm toán gửi email và khóa chống gửi trùng lũy đẳng theo cặp (subscription_id, alert_id)';
