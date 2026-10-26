package vn.weathergis.storm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Response cho GET /storms/{id} — bọc trong ApiResponse của controller.
 *
 * <p>{@code @JsonInclude(ALWAYS)} như {@link StormSummaryResponse}: {@code minPressure}/{@code
 * endDate} null phải hiện diện tường minh trên dây truyền (SYS-RULE-004).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record StormDetailResponse(
        @Schema(description = "Mã sự kiện bão GDACS", example = "1001209") String id,
        @Schema(description = "Tên bão quốc tế", example = "YAGI") String name,
        @Schema(description = "Năm phát sinh bão", example = "2026") Integer year,
        @Schema(description = "Cấp bão GDACS", example = "Typhoon") String category,
        @Schema(description = "Sức gió tối đa duy trì (m/s)", example = "40.0") double maxWindSpeed,
        @Schema(description = "Sức gió tối đa quy đổi (km/h)", example = "144.0")
                double maxWindSpeedKmh,
        @Schema(description = "Áp suất tâm bão (hPa); null khi nguồn không công bố")
                Double minPressure,
        @Schema(
                        description = "Trạng thái active/dissipated",
                        allowableValues = {"active", "dissipated"})
                String status,
        @Schema(description = "Kinh độ tâm bão hiện tại WGS84 (độ)", example = "116.4")
                double longitude,
        @Schema(description = "Vĩ độ tâm bão hiện tại WGS84 (độ)", example = "18.2")
                double latitude,
        @Schema(description = "Tổng số mốc quỹ đạo đã ghi nhận (historical + forecast)")
                long totalTrackPoints,
        @Schema(description = "Thời điểm bão hình thành") OffsetDateTime startDate,
        @Schema(description = "Thời điểm bão tan; null khi còn active") OffsetDateTime endDate,
        @Schema(description = "Thời điểm cập nhật mới nhất từ GDACS") OffsetDateTime updatedAt) {}
