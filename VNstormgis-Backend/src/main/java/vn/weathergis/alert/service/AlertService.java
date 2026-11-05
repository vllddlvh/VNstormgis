package vn.weathergis.alert.service;

import java.util.UUID;
import vn.weathergis.alert.dto.AlertDetailResponse;
import vn.weathergis.common.api.GeoJsonFeatureCollection;

/** Public read service for active GeoJSON alerts and detailed alert inspection. */
public interface AlertService {

    GeoJsonFeatureCollection getActiveAlertsAsGeoJson();

    AlertDetailResponse getAlertById(UUID id);
}
