package vn.weathergis.alert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bộ ngưỡng cảnh báo; PUT thay thế toàn bộ trường nghiệp vụ")
public record AlertThresholdRequest(
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Tên sau khi trim, dài 1–128 ký tự",
                        example = "Mưa rất to 1h")
                String name,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {
                            "rain_1h",
                            "rain_3h",
                            "wind_speed",
                            "wind_gust",
                            "storm_dist"
                        })
                String parameter,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {">=", ">", "<=", "<", "="})
                String operator,
        @JsonProperty("threshold_value")
                @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Số hữu hạn lớn hơn 0",
                        example = "50.0")
                Double thresholdValue,
        @JsonProperty("severity_level")
                @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {"INFO", "WARNING", "DANGER", "CRITICAL"})
                String severityLevel,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Mô tả bắt buộc, không rỗng")
                String description,
        @JsonProperty("is_enabled")
                @Schema(description = "POST mặc định true khi bỏ trống; PUT bắt buộc truyền")
                Boolean isEnabled) {}
