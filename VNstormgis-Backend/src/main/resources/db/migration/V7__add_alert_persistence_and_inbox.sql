-- ============================================================================
-- Database Migration Script: V7__add_alert_persistence_and_inbox.sql
-- Project: Vietnam Weather & Storm Monitoring WebGIS with Grounded AI Agent
-- Sprint: 3 (Week 4) | Task: W4-03
-- Target Database: PostgreSQL 16 LTS + PostGIS 3.4
-- Description:
--   1. Bổ sung cột is_fallback và factual_snapshot (JSONB) cho bảng alerts.
--   2. Tạo bảng alert_generation_jobs làm PostgreSQL inbox cho quy trình
--      PENDING -> READY -> COMPLETED và cơ chế retry có backoff.
--   3. Tạo bảng alert_violation_keys đảm bảo chống trùng bền vững trên từng
--      violation_key và liên kết bất biến với job_id / alert_id.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. BỔ SUNG CỘT CHO BẢNG ALERTS HIỆN HỮU (GIỮ NGUYÊN FK VÀ INDEX V5)
-- ----------------------------------------------------------------------------
ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS is_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS factual_snapshot JSONB;

COMMENT ON COLUMN alerts.is_fallback IS 'Cờ đánh dấu nội dung cảnh báo sử dụng mẫu dự phòng tất định (true) hay phản hồi AI đã qua guardrail (false)';
COMMENT ON COLUMN alerts.factual_snapshot IS 'Bản chụp dữ kiện bất biến tại thời điểm phát hiện vượt ngưỡng (trạm, tọa độ, chu kỳ, ngưỡng đại diện và danh sách vi phạm)';

-- ----------------------------------------------------------------------------
-- 2. BẢNG ALERT_GENERATION_JOBS (INBOX BỀN VỮNG CHO QUY TRÌNH TẠO CẢNH BÁO)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alert_generation_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL,
    evaluation_cycle_id UUID NOT NULL,
    representative_threshold_id INT NOT NULL,
    storm_id VARCHAR(64),
    severity_level VARCHAR(32) NOT NULL,
    trigger_reason TEXT NOT NULL,
    factual_snapshot JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    verified_title VARCHAR(255),
    verified_ai_explanation TEXT,
    is_fallback BOOLEAN,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error VARCHAR(255),
    alert_id UUID UNIQUE REFERENCES alerts(id) ON DELETE RESTRICT,
    triggered_at TIMESTAMPTZ NOT NULL,
    ready_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_alert_generation_jobs_status
        CHECK (status IN ('PENDING', 'READY', 'COMPLETED'))
);

DROP TRIGGER IF EXISTS trg_alert_generation_jobs_updated_at ON alert_generation_jobs;
CREATE TRIGGER trg_alert_generation_jobs_updated_at
BEFORE UPDATE ON alert_generation_jobs
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX IF NOT EXISTS idx_alert_jobs_status_next_attempt
    ON alert_generation_jobs (status, next_attempt_at ASC);

CREATE INDEX IF NOT EXISTS idx_alert_jobs_location_id
    ON alert_generation_jobs (location_id);

COMMENT ON TABLE alert_generation_jobs IS 'Inbox PostgreSQL lưu công việc tạo cảnh báo (PENDING -> READY -> COMPLETED) và trạng thái retry xuất bản';

-- ----------------------------------------------------------------------------
-- 3. BẢNG ALERT_VIOLATION_KEYS (CHỐNG TRÙNG TỪNG VI PHẠM BỀN VỮNG)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alert_violation_keys (
    violation_key VARCHAR(255) PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES alert_generation_jobs(id) ON DELETE CASCADE,
    alert_id UUID REFERENCES alerts(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alert_violation_keys_job_id
    ON alert_violation_keys (job_id);

CREATE INDEX IF NOT EXISTS idx_alert_violation_keys_alert_id
    ON alert_violation_keys (alert_id);

COMMENT ON TABLE alert_violation_keys IS 'Khóa chống trùng bền vững cho từng vi phạm ngưỡng; gắn với job khi tiếp nhận và gắn alert_id khi xuất bản';
