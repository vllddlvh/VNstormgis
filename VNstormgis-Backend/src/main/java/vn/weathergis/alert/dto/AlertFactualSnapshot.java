package vn.weathergis.alert.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable factual snapshot captured at threshold-violation detection time.
 *
 * <p>Stored in {@code alert_generation_jobs.factual_snapshot} and copied verbatim to {@code
 * alerts.factual_snapshot} on publication so historical evidence survives threshold, station, or
 * storm edits and deletions.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AlertFactualSnapshot(
        @JsonProperty("location_id") UUID locationId,
        @JsonProperty("location_code") String locationCode,
        @JsonProperty("location_name") String locationName,
        @JsonProperty("coordinates") List<Double> coordinates,
        @JsonProperty("evaluation_cycle_id") UUID evaluationCycleId,
        @JsonProperty("evaluated_at") Instant evaluatedAt,
        @JsonProperty("representative_threshold")
                RepresentativeThresholdSnapshot representativeThreshold,
        @JsonProperty("violations") List<ViolationSnapshot> violations) {

    public AlertFactualSnapshot {
        coordinates = coordinates == null ? null : List.copyOf(coordinates);
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RepresentativeThresholdSnapshot(
            @JsonProperty("threshold_id") Integer thresholdId,
            @JsonProperty("parameter") String parameter,
            @JsonProperty("operator") String operator,
            @JsonProperty("threshold_value") double thresholdValue,
            @JsonProperty("severity_level") String severityLevel,
            @JsonProperty("unit") String unit,
            @JsonProperty("violation_key") String violationKey) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ViolationSnapshot(
            @JsonProperty("threshold_id") Integer thresholdId,
            @JsonProperty("parameter") String parameter,
            @JsonProperty("operator") String operator,
            @JsonProperty("threshold_value") double thresholdValue,
            @JsonProperty("severity_level") String severityLevel,
            @JsonProperty("measured_value") double measuredValue,
            @JsonProperty("unit") String unit,
            @JsonProperty("source_timestamp") Instant sourceTimestamp,
            @JsonProperty("evaluation_window") Instant evaluationWindow,
            @JsonProperty("violation_key") String violationKey,
            @JsonProperty("storm_id") String stormId,
            @JsonProperty("storm_name") String stormName,
            @JsonProperty("distance_km") Double distanceKm) {}
}
