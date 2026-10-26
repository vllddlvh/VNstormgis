package vn.weathergis.storm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Dòng dữ liệu bão cho format=json của GET /storms (và nền tảng cho StormDetailResponse).
 *
 * <p>{@code @JsonInclude(ALWAYS)} ghi đè {@code spring.jackson.default-property-inclusion=non_null}
 * toàn cục: hợp đồng 11 khóa bắt buộc {@code minPressure}/{@code endDate} hiện diện kể cả khi null
 * (SYS-RULE-004 — khóa vắng mặt khác với "nguồn không công bố").
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record StormSummaryResponse(
        @Schema(description = "Mã sự kiện bão GDACS (khóa chính)", example = "1001209") String id,
        @Schema(description = "Tên bão quốc tế", example = "YAGI") String name,
        @Schema(description = "Năm phát sinh bão", example = "2026") Integer year,
        @Schema(description = "Cấp bão theo phân loại GDACS", example = "Typhoon") String category,
        @Schema(description = "Sức gió tối đa duy trì (m/s)", example = "40.0") double maxWindSpeed,
        @Schema(description = "Sức gió tối đa quy đổi (km/h)", example = "144.0")
                double maxWindSpeedKmh,
        @Schema(description = "Áp suất tâm bão (hPa); null khi GDACS không công bố (SYS-RULE-004)")
                Double minPressure,
        @Schema(
                        description = "Trạng thái theo SYS-RULE-001",
                        allowableValues = {"active", "dissipated"},
                        example = "active")
                String status,
        @Schema(description = "Thời điểm bão hình thành (ISO-8601 UTC)") OffsetDateTime startDate,
        @Schema(description = "Thời điểm bão tan (ISO-8601 UTC); null khi còn active")
                OffsetDateTime endDate,
        @Schema(description = "Thời điểm cập nhật mới nhất từ GDACS (ISO-8601 UTC)")
                OffsetDateTime updatedAt) {}
