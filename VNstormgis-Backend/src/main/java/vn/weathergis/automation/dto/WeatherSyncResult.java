package vn.weathergis.automation.dto;

import java.time.OffsetDateTime;

/** Kết quả tổng hợp một chu kỳ đồng bộ thời tiết — trả về từ endpoint kích hoạt thủ công. */
public record WeatherSyncResult(
        String source,
        String status,
        int recordsProcessed,
        int totalStations,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String errorMessage) {}
