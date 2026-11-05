package vn.weathergis.alert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import vn.weathergis.alert.domain.AlertThresholdEntity;

public record AlertThresholdResponse(
        Integer id,
        String name,
        String parameter,
        String operator,
        @JsonProperty("threshold_value") Double thresholdValue,
        @JsonProperty("severity_level") String severityLevel,
        String description,
        @JsonProperty("is_enabled") Boolean isEnabled,
        @JsonProperty("updated_at") OffsetDateTime updatedAt) {

    public static AlertThresholdResponse fromEntity(AlertThresholdEntity entity) {
        return new AlertThresholdResponse(
                entity.getId(),
                entity.getName(),
                entity.getParameter(),
                entity.getOperator(),
                entity.getThresholdValue(),
                entity.getSeverityLevel(),
                entity.getDescription(),
                entity.getIsEnabled(),
                entity.getUpdatedAt());
    }
}
