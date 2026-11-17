package vn.weathergis.chat.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.chat.intent.ChatIntent;

/**
 * Immutable bounded factual context (W5-02 §4.7, contract §4.1). All coordinates are WGS84 {@code
 * [lng, lat]}; all times UTC; every observation carries its unit and timestamp. {@code sizeBytes}
 * measures the canonical serialization excluding the {@code size_bytes} field itself. This record
 * is never wrapped in {@code [FACTUAL_SYSTEM_DATA]} here — that belongs to W5-04.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record FactualContext(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("intent") ChatIntent intent,
        @JsonProperty("generated_at") Instant generatedAt,
        @JsonProperty("outcome") ChatContextOutcome outcome,
        @JsonProperty("no_data_reason") NoDataReason noDataReason,
        @JsonProperty("stations") List<StationObservation> stations,
        @JsonProperty("storms") List<StormSummary> storms,
        @JsonProperty("alert") AlertContext alert,
        @JsonProperty("referenced_entities") ReferencedEntities referencedEntities,
        @JsonProperty("meta") ContextMeta meta,
        @JsonProperty("size_bytes") long sizeBytes) {

    public static final String SCHEMA_VERSION = "chat-context-v1";

    public FactualContext {
        stations = stations == null ? List.of() : List.copyOf(stations);
        storms = storms == null ? List.of() : List.copyOf(storms);
    }

    public FactualContext withSizeBytes(long bytes) {
        return new FactualContext(
                schemaVersion,
                intent,
                generatedAt,
                outcome,
                noDataReason,
                stations,
                storms,
                alert,
                referencedEntities,
                meta,
                bytes);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StationObservation(
            @JsonProperty("code") String code,
            @JsonProperty("name") String name,
            @JsonProperty("coordinates") List<Double> coordinates,
            @JsonProperty("recorded_at") Instant recordedAt,
            @JsonProperty("stale") boolean stale,
            @JsonProperty("temperature") Double temperature,
            @JsonProperty("humidity") Double humidity,
            @JsonProperty("pressure") Double pressure,
            @JsonProperty("wind_speed") Double windSpeed,
            @JsonProperty("wind_gust") Double windGust,
            @JsonProperty("wind_direction") Double windDirection,
            @JsonProperty("rain_1h") Double rain1h,
            @JsonProperty("rain_3h") Double rain3h,
            @JsonProperty("weather_condition") String weatherCondition,
            @JsonProperty("weather_description") String weatherDescription,
            @JsonProperty("units") Map<String, String> units) {

        public StationObservation {
            coordinates = coordinates == null ? null : List.copyOf(coordinates);
            units = units == null ? Map.of() : Map.copyOf(units);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StormSummary(
            @JsonProperty("storm_id") String stormId,
            @JsonProperty("name") String name,
            @JsonProperty("category") String category,
            @JsonProperty("max_wind_speed") Double maxWindSpeed,
            @JsonProperty("min_pressure") Double minPressure,
            @JsonProperty("center") List<Double> center,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("updated_at_kind") String updatedAtKind,
            @JsonProperty("stale") boolean stale,
            @JsonProperty("source") String source,
            @JsonProperty("nearest_forecast") ForecastPoint nearestForecast,
            @JsonProperty("units") Map<String, String> units) {

        public StormSummary {
            center = center == null ? null : List.copyOf(center);
            units = units == null ? Map.of() : Map.copyOf(units);
        }

        public StormSummary withoutForecast() {
            return new StormSummary(
                    stormId,
                    name,
                    category,
                    maxWindSpeed,
                    minPressure,
                    center,
                    updatedAt,
                    updatedAtKind,
                    stale,
                    source,
                    null,
                    units);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ForecastPoint(
            @JsonProperty("forecast_time") Instant forecastTime,
            @JsonProperty("track_type") String trackType,
            @JsonProperty("coordinates") List<Double> coordinates,
            @JsonProperty("wind_speed") Double windSpeed,
            @JsonProperty("wind_gust") Double windGust,
            @JsonProperty("pressure") Double pressure,
            @JsonProperty("category") String category) {

        public ForecastPoint {
            coordinates = coordinates == null ? null : List.copyOf(coordinates);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AlertContext(
            @JsonProperty("alert_id") UUID alertId,
            @JsonProperty("status") String status,
            @JsonProperty("severity_level") String severityLevel,
            @JsonProperty("triggered_at") Instant triggeredAt,
            @JsonProperty("resolved_at") Instant resolvedAt,
            @JsonProperty("snapshot_present") boolean snapshotPresent,
            @JsonProperty("factual_snapshot") AlertFactualSnapshot factualSnapshot,
            @JsonProperty("violation_order") List<ViolationOrder> violationOrder,
            @JsonProperty("snapshot_oversize") boolean snapshotOversize,
            @JsonProperty("snapshot_size_bytes") long snapshotSizeBytes) {

        public AlertContext {
            violationOrder = violationOrder == null ? List.of() : List.copyOf(violationOrder);
        }
    }

    /**
     * Stable severity ordering hint for the controlled W5-04 cut; exceedance = measured −
     * threshold.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ViolationOrder(
            @JsonProperty("violation_key") String violationKey,
            @JsonProperty("severity_level") String severityLevel,
            @JsonProperty("exceedance") double exceedance) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReferencedEntities(
            @JsonProperty("stations") List<String> stations,
            @JsonProperty("storm_ids") List<String> stormIds,
            @JsonProperty("alert_ids") List<UUID> alertIds) {

        public ReferencedEntities {
            stations = stations == null ? List.of() : List.copyOf(stations);
            stormIds = stormIds == null ? List.of() : List.copyOf(stormIds);
            alertIds = alertIds == null ? List.of() : List.copyOf(alertIds);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ContextMeta(
            @JsonProperty("stale_threshold_minutes") long staleThresholdMinutes,
            @JsonProperty("omitted_station_count") int omittedStationCount,
            @JsonProperty("omitted_storm_count") int omittedStormCount,
            @JsonProperty("context_oversize") boolean contextOversize) {}
}
