package vn.weathergis.alert.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** OpenAPI-only view of the alert FeatureCollection returned by the shared GeoJSON classes. */
public final class AlertGeoJsonSchema {
    private AlertGeoJsonSchema() {}

    @Schema(requiredProperties = {"type", "features"})
    public record Collection(
            @Schema(example = "FeatureCollection") String type, List<Feature> features) {}

    @Schema(requiredProperties = {"type", "id", "geometry", "properties"})
    public record Feature(
            @Schema(example = "Feature") String type,
            UUID id,
            @Schema(
                            type = "object",
                            nullable = true,
                            description = "Point [lng, lat] WGS84; null for legacy alerts")
                    Object geometry,
            Properties properties) {}

    @Schema(
            requiredProperties = {
                "alert_id",
                "location_code",
                "location_name",
                "severity_level",
                "title",
                "trigger_reason",
                "triggered_at",
                "is_fallback"
            })
    public record Properties(
            UUID alert_id,
            @Schema(nullable = true) String location_code,
            @Schema(nullable = true) String location_name,
            @Schema(allowableValues = {"INFO", "WARNING", "DANGER", "CRITICAL"})
                    String severity_level,
            String title,
            String trigger_reason,
            Instant triggered_at,
            boolean is_fallback) {}
}
