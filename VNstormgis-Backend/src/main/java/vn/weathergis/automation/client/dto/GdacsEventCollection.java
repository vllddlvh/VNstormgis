package vn.weathergis.automation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Tầng 1 — GeoJSON FeatureCollection trả về bởi GDACS Search API ({@code
 * /gdacsapi/api/Events/geteventlist/search?eventlist=TC}). Tên trường đối chiếu với fixture thật
 * {@code src/test/resources/fixtures/gdacs/search-tc-events.json} (chốt 2026-10-22). Parser phòng
 * vệ RSK-W2-02: mọi trường có thể vắng — client null-check trước khi dùng.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GdacsEventCollection(@JsonProperty("features") List<GdacsEventFeature> features) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GdacsEventFeature(
            @JsonProperty("geometry") GdacsGeometry geometry,
            @JsonProperty("properties") GdacsEventProperties properties) {}

    /** GeoJSON Point — tọa độ luôn {@code [lng, lat]} (AGENTS.md mục 1). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GdacsGeometry(
            @JsonProperty("type") String type,
            @JsonProperty("coordinates") List<Double> coordinates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GdacsEventProperties(
            @JsonProperty("eventid") Integer eventId,
            @JsonProperty("episodeid") Integer episodeId,
            @JsonProperty("eventname") String eventName,
            @JsonProperty("name") String name,
            @JsonProperty("alertlevel") String alertLevel, // "Green" | "Orange" | "Red"
            @JsonProperty("iscurrent")
                    String isCurrent, // "true" | "false" — lọc sự kiện còn hoạt động
            @JsonProperty("fromdate") String fromDate, // ISO-8601 không offset — giờ UTC
            @JsonProperty("todate") String toDate,
            @JsonProperty("severitydata") GdacsSeverityData severityData) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GdacsSeverityData(
            @JsonProperty("severity") Double severity, // giá trị gió tối đa (đơn vị severityUnit)
            @JsonProperty("severitytext") String severityText,
            @JsonProperty("severityunit") String severityUnit) {} // "km/h" | "kt"
}
