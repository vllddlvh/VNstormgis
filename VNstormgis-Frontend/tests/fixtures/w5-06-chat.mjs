/** Browser-only chat fixtures. No token or message here belongs to a real session. */
export const sessionId = "00000000-0000-4000-8000-900000000001";
export const token = "A".repeat(43);
export const alertId = "00000000-0000-4000-8000-000000000001";
export const instant = "2026-11-17T09:00:00Z";
export const envelope = (data) => ({ success: true, data, message: "FIXTURE", timestamp: instant });
export const session = () => ({ session_id: sessionId, session_token: token, title: "Hội thoại mới", created_at: instant });
export const reply = (overrides = {}) => ({
  message_id: "00000000-0000-4000-8000-900000000002",
  reply: "FIXTURE: Hệ thống chưa ghi nhận dữ liệu mưa phù hợp.",
  is_fallback: true,
  referenced_entities: { stations: [], storm_ids: [], alert_ids: [] },
  created_at: instant,
  ...overrides,
});
export const message = (id, sender_type, content, is_fallback = false) => ({
  id, sender_type, content, referenced_alert_id: null, is_fallback, created_at: instant,
});
export const problem = (status, errorCode, extras = {}) => ({
  type: "about:blank", title: "FIXTURE", status, detail: "FIXTURE: lỗi kiểm thử", errorCode,
  timestamp: instant, ...extras,
});
