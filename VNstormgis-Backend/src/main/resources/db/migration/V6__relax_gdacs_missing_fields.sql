-- ============================================================================
-- V6: NỚI LỎNG RÀNG BUỘC NOT NULL CHO CÁC CỘT GDACS KHÔNG CÒN CUNG CẤP (W2-05)
-- ============================================================================
-- Xác minh trực tiếp nguồn GDACS ngày 2026-10-22: Search API, geometry API,
-- episode API và RSS hiện hành KHÔNG công bố áp suất tâm bão và KHÔNG có gió/gust
-- theo từng mốc quỹ đạo (chỉ có gió tối đa cấp sự kiện, đơn vị km/h).
-- Quyết định đã duyệt (biên bản trong Decisions.md + PR W2-05): lưu NULL khi
-- nguồn không có giá trị — tuyệt đối không điền số giả (SYS-RULE-004).
-- ============================================================================

ALTER TABLE storms
    ALTER COLUMN min_pressure DROP NOT NULL;

ALTER TABLE storm_tracks
    ALTER COLUMN wind_speed DROP NOT NULL,
    ALTER COLUMN wind_gust DROP NOT NULL,
    ALTER COLUMN pressure DROP NOT NULL;

COMMENT ON COLUMN storms.min_pressure IS
    'Áp suất tâm bão (hPa); NULL khi nguồn GDACS không công bố (xác minh 2026-10-22)';
COMMENT ON COLUMN storm_tracks.wind_speed IS
    'Sức gió duy trì (m/s) tại mốc quỹ đạo; NULL khi nguồn GDACS không công bố theo mốc';
COMMENT ON COLUMN storm_tracks.wind_gust IS
    'Gió giật (m/s) tại mốc quỹ đạo; NULL khi nguồn GDACS không công bố theo mốc';
COMMENT ON COLUMN storm_tracks.pressure IS
    'Áp suất (hPa) tại mốc quỹ đạo; NULL khi nguồn GDACS không công bố theo mốc';
