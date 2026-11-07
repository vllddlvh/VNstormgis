ALTER TABLE alert_generation_jobs
    ADD COLUMN claim_token UUID,
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD COLUMN quarantine_until TIMESTAMPTZ;

CREATE INDEX idx_alert_jobs_pending_claim
    ON alert_generation_jobs (created_at, id) WHERE status = 'PENDING';
