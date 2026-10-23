package vn.weathergis.weather.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Dòng dữ liệu "trạm + bản ghi mới nhất" cho format=json của /weather/current. */
public record WeatherCurrentResponse(
        @Schema(
                        description = "UUID trạm quan trắc",
                        example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
                UUID locationId,
        @Schema(description = "Mã trạm", example = "HAN") String code,
        @Schema(description = "Tên trạm", example = "Hà Nội") String name,
        @Schema(description = "Vùng khí hậu", example = "Bắc Bộ") String region,
        @Schema(description = "Kinh độ WGS84", example = "105.8542") double longitude,
        @Schema(description = "Vĩ độ WGS84", example = "21.0285") double latitude,
        @Schema(description = "Nhiệt độ (°C)", example = "28.5") Double temperature,
        @Schema(description = "Nhiệt độ cảm nhận (°C)") Double feelsLike,
        @Schema(description = "Độ ẩm (%)") Double humidity,
        @Schema(description = "Áp suất (hPa)") Double pressure,
        @Schema(description = "Vận tốc gió (m/s)") Double windSpeed,
        @Schema(description = "Hướng gió (độ azimuth 0-360)") Double windDirection,
        @Schema(description = "Gió giật (m/s)") Double windGust,
        @Schema(description = "Lượng mưa 1 giờ (mm)") Double rain1h,
        @Schema(description = "Lượng mưa 3 giờ (mm)") Double rain3h,
        @Schema(description = "Điều kiện thời tiết (mã OWM)") String weatherCondition,
        @Schema(description = "Mô tả điều kiện thời tiết") String weatherDescription,
        @Schema(
                        description = "Phân cấp mưa theo QĐ 18/2021/QĐ-TTg",
                        allowableValues = {"NONE", "MODERATE", "WARNING", "DANGER"},
                        example = "WARNING")
                String severityRain,
        @Schema(description = "Thời điểm đo đạc (ISO-8601 UTC)") OffsetDateTime recordedAt) {}
