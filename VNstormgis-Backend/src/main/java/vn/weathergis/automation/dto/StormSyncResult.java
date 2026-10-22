package vn.weathergis.automation.dto;

import java.time.OffsetDateTime;

/** Kết quả tổng hợp một chu kỳ đồng bộ bão GDACS — trả về từ endpoint kích hoạt thủ công. */
public record StormSyncResult(
        String source,
        String status,
        int stormsProcessed,
        int stormsOnFeed,
        int trackPointsInserted,
        int stormsDissipated,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String errorMessage) {}
