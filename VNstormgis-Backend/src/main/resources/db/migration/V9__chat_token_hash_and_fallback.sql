-- ============================================================================
-- Database Migration Script: V9__chat_token_hash_and_fallback.sql
-- Project: Vietnam Weather & Storm Monitoring WebGIS with Grounded AI Agent
-- Sprint: 3 (Week 5) | Task: W5-03
-- Target Database: PostgreSQL 16 LTS + PostGIS 3.4
-- Canonical Spec: docs/week5/W5-01 - Chat contract draft.md (§3.2, §3.5)
-- Decisions: D-W5-01-02 (D2 token verifier + D4 retention/is_fallback) — APPROVED 2026-11-17
-- Description:
--   1. chat_sessions: thêm token_hash (SHA-256 hex verifier), backfill từ session_token
--      thô bằng sha256() built-in của PostgreSQL 16 (không cần pgcrypto), SET NOT NULL,
--      unique index, rồi DROP cột session_token thô (destructive có chủ đích theo D2 —
--      chỉ sau backfill; dev DB đã đối soát 0 dòng trước migration, xem report W5-03).
--   2. chat_messages: thêm is_fallback BOOLEAN NOT NULL DEFAULT FALSE (D4/DL-11) để đánh
--      dấu trả lời tự động; dòng legacy nhận default FALSE.
--   3. KHÔNG sửa V1–V8; không thêm expires_at (dùng updated_at + job dọn theo hợp đồng
--      §3.5); không thêm CHECK sender_type (validate tầng application); không thêm khóa
--      dedup client_message_id (D10 để ngỏ W5). FK cascade/SET NULL, index
--      (session_id, created_at) và trigger updated_at của V5 giữ nguyên.
--   Parity hash Java↔SQL được chứng minh bởi ChatMigrationV9IT (kể cả token chứa '-','_').
-- ============================================================================

-- 1. TOKEN VERIFIER (D2) -----------------------------------------------------
ALTER TABLE chat_sessions ADD COLUMN token_hash VARCHAR(64);

UPDATE chat_sessions
SET token_hash = encode(sha256(session_token::bytea), 'hex');

ALTER TABLE chat_sessions ALTER COLUMN token_hash SET NOT NULL;

CREATE UNIQUE INDEX idx_chat_sessions_token_hash ON chat_sessions (token_hash);

ALTER TABLE chat_sessions DROP COLUMN session_token;

COMMENT ON COLUMN chat_sessions.token_hash IS
    'SHA-256(token UTF-8) hex 64 ký tự — verifier của bearer token server cấp; token thô không lưu (D2)';

-- 2. FALLBACK FLAG (D4/DL-11) ------------------------------------------------
ALTER TABLE chat_messages ADD COLUMN is_fallback BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN chat_messages.is_fallback IS
    'TRUE khi reply là template tất định (thiếu dữ liệu/AI lỗi/ngoài phạm vi/vi phạm guardrail); chỉ có nghĩa với assistant (D4)';