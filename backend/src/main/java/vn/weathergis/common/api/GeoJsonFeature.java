package vn.weathergis.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;

/** Đại diện cho một đối tượng Feature theo chuẩn RFC 7946 GeoJSON. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Đối tượng địa không gian GeoJSON Feature RFC 7946")
public class GeoJsonFeature {

    @Schema(description = "Kiểu đối tượng Feature", example = "Feature")
    private final String type = "Feature";

    @Schema(
            description = "Định danh duy nhất của Feature",
            example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private String id;

    @Schema(description = "Hình học không gian GeoJSON (Point, Polygon...)")
    private Geometry geometry;

    @Schema(description = "Thuộc tính nghiệp vụ đính kèm của trạm quan trắc")
    private Map<String, Object> properties;

    public GeoJsonFeature() {}

    public GeoJsonFeature(String id, Geometry geometry, Map<String, Object> properties) {
        this.id = id;
        this.geometry = geometry;
        this.properties = properties;
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
