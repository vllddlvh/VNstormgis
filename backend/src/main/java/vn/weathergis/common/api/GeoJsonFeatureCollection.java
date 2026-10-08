package vn.weathergis.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Đại diện cho tập hợp FeatureCollection theo chuẩn RFC 7946 GeoJSON. Sẵn sàng để nạp trực tiếp vào
 * thư viện Leaflet thông qua L.geoJSON(data).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeoJsonFeatureCollection {

    private final String type = "FeatureCollection";
    private List<GeoJsonFeature> features;

    public GeoJsonFeatureCollection() {
        this.features = new ArrayList<>();
    }

    public GeoJsonFeatureCollection(List<GeoJsonFeature> features) {
        this.features = features != null ? features : new ArrayList<>();
    }

    public String getType() {
        return type;
    }

    public List<GeoJsonFeature> getFeatures() {
        return features;
    }

    public void setFeatures(List<GeoJsonFeature> features) {
        this.features = features;
    }

    public void addFeature(GeoJsonFeature feature) {
        if (this.features == null) {
            this.features = new ArrayList<>();
        }
        this.features.add(feature);
    }
}
