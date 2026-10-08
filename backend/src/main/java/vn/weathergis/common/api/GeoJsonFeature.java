package vn.weathergis.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;

/** Đại diện cho một đối tượng Feature theo chuẩn RFC 7946 GeoJSON. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeoJsonFeature {

    private final String type = "Feature";
    private String id;
    private Geometry geometry;
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
