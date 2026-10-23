package vn.weathergis.weather.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Response cho GET /weather/history — bọc trong ApiResponse của controller. */
public record WeatherHistoryResponse(
        @Schema(description = "UUID trạm") UUID locationId,
        @Schema(description = "Mã trạm", example = "HAN") String code,
        @Schema(description = "Tên trạm") String name,
        @Schema(description = "Vùng khí hậu") String region,
        @Schema(description = "Cửa sổ truy vấn (giờ)", example = "24") int hours,
        @Schema(description = "Thời điểm bắt đầu cửa sổ (ISO-8601 UTC)") OffsetDateTime from,
        @Schema(description = "Thời điểm kết thúc cửa sổ (ISO-8601 UTC)") OffsetDateTime to,
        @Schema(description = "Số mốc đo đạc trả về") int count,
        @Schema(description = "Chuỗi thời gian tăng dần theo recorded_at")
                List<WeatherReading> readings) {

    /** Một mốc đo đạc trong chuỗi thời gian. */
    public record WeatherReading(
            @Schema(description = "Thời điểm đo (ISO-8601 UTC)") OffsetDateTime recordedAt,
            @Schema(description = "Lượng mưa 1h (mm)") Double rain1h,
            @Schema(description = "Lượng mưa 3h (mm)") Double rain3h,
            @Schema(description = "Vận tốc gió (m/s)") Double windSpeed,
            @Schema(description = "Hướng gió (độ)") Double windDirection,
            @Schema(description = "Gió giật (m/s)") Double windGust,
            @Schema(description = "Nhiệt độ (°C)") Double temperature,
            @Schema(description = "Nhiệt độ cảm nhận (°C)") Double feelsLike,
            @Schema(description = "Độ ẩm (%)") Double humidity,
            @Schema(description = "Áp suất (hPa)") Double pressure,
            @Schema(description = "Điều kiện thời tiết") String weatherCondition) {}
}
