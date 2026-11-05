package vn.weathergis.alert.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed alert response for {@code GET /api/v1/alerts/{id}}, wrapped in {@code ApiResponse}.
 *
 * <p>{@code location}, {@code threshold}, and {@code storm} reflect current linked database records
 * (or {@code null} when unlinked/deleted), whereas {@code factual_snapshot} preserves the immutable
 * factual basis recorded when the violation was detected.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(
        description =
                "Chi tiết cảnh báo thiên tai kèm bản chụp dữ kiện gốc và các liên kết hiện tại")
public record AlertDetailResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("threshold_id") Integer thresholdId,
        @JsonProperty("location_id") UUID locationId,
        @JsonProperty("storm_id") String stormId,
        @JsonProperty("title") String title,
        @JsonProperty("severity_level") String severityLevel,
        @JsonProperty("trigger_reason") String triggerReason,
        @JsonProperty("ai_explanation") String aiExplanation,
        @JsonProperty("is_fallback") boolean isFallback,
        @JsonProperty("status") String status,
        @JsonProperty("triggered_at") Instant triggeredAt,
        @JsonProperty("resolved_at") Instant resolvedAt,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("location") AlertLocationSummary location,
        @JsonProperty("threshold") AlertThresholdSummary threshold,
        @JsonProperty("storm") AlertStormSummary storm,
        @JsonProperty("factual_snapshot") AlertFactualSnapshot factualSnapshot) {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AlertLocationSummary(
            @JsonProperty("id") UUID id,
            @JsonProperty("code") String code,
            @JsonProperty("name") String name,
            @JsonProperty("region") String region,
            @JsonProperty("coordinates") List<Double> coordinates) {

        public AlertLocationSummary {
            coordinates = coordinates == null ? null : List.copyOf(coordinates);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AlertThresholdSummary(
            @JsonProperty("id") Integer id,
            @JsonProperty("name") String name,
            @JsonProperty("parameter") String parameter,
            @JsonProperty("operator") String operator,
            @JsonProperty("threshold_value") Double thresholdValue,
            @JsonProperty("severity_level") String severityLevel,
            @JsonProperty("description") String description,
            @JsonProperty("is_enabled") Boolean isEnabled,
            @JsonProperty("updated_at") Instant updatedAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AlertStormSummary(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("category") String category,
            @JsonProperty("status") String status,
            @JsonProperty("max_wind_speed") Double maxWindSpeed,
            @JsonProperty("min_pressure") Double minPressure,
            @JsonProperty("current_center") List<Double> currentCenter,
            @JsonProperty("updated_at") Instant updatedAt) {

        public AlertStormSummary {
            currentCenter = currentCenter == null ? null : List.copyOf(currentCenter);
        }
    }
}
