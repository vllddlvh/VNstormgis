package vn.weathergis.automation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Tầng 2 — GeoJSON FeatureCollection quỹ đạo bão từ GDACS geometry API ({@code
 * /gdacsapi/api/polygons/getgeometry?eventtype=TC&eventid=...&episodeid=...}). Mỗi mốc quỹ đạo là
 * một feature {@code LineString} 2 đỉnh [vị trí tại t, vị trí tại t+6h]. GDACS đóng dấu CÙNG MỘT
 * {@code polygondate} (giờ chạy mô hình) cho toàn bộ đoạn — thời gian thật của từng mốc do client
 * suy từ chuỗi liên kết + cửa sổ {@code fromdate}/{@code todate} (xem GdacsStormClient). Cờ {@code
 * forecast} phân loại mốc dự báo/lịch sử; {@code polygonlabel} (HU/TS/TD) là cấp bão tại mốc. Đối
 * chiếu với fixture thật {@code src/test/resources/fixtures/gdacs/track-champi-1001338.json} (chốt
 * 2026-10-22).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GdacsTrackCollection(@JsonProperty("features") List<GdacsTrackFeature> features) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GdacsTrackFeature(
            @JsonProperty("geometry") GdacsTrackGeometry geometry,
            @JsonProperty("properties") GdacsTrackProperties properties) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GdacsTrackGeometry(
            @JsonProperty("type") String type, // chỉ xử lý "LineString"
            // JsonNode vì geometry API trộn feature Point (tọa độ phẳng [lng,lat]) với
            // LineString (mảng các mốc) — List<List<Double>> không parse được Point.
            @JsonProperty("coordinates") JsonNode coordinates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GdacsTrackProperties(
            @JsonProperty("polygondate")
                    String polygonDate, // giờ chạy mô hình — đồng nhất mọi đoạn
            @JsonProperty("polygonlabel") String polygonLabel, // "HU" | "TS" | "TD"
            @JsonProperty("forecast") Boolean forecast,
            @JsonProperty("fromdate") String fromDate, // cửa sổ sự kiện — neo suy thời gian mốc
            @JsonProperty("todate") String toDate) {}
}
