package vn.weathergis.alert.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** OpenAPI-only PUT schema; service validation still uses AlertThresholdRequest. */
public record AlertThresholdPutRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
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
        @Schema(name = "threshold_value", requiredMode = Schema.RequiredMode.REQUIRED)
                Double thresholdValue,
        @Schema(
                        name = "severity_level",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {"INFO", "WARNING", "DANGER", "CRITICAL"})
                String severityLevel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String description,
        @Schema(name = "is_enabled", requiredMode = Schema.RequiredMode.REQUIRED)
                Boolean isEnabled) {}
