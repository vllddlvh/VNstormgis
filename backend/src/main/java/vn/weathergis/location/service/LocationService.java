package vn.weathergis.location.service;

import java.util.List;
import java.util.UUID;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.location.dto.LocationNearbyResponse;
import vn.weathergis.location.dto.LocationResponse;

/** Giao diện dịch vụ nghiệp vụ quản lý trạm quan trắc địa không gian. */
public interface LocationService {

    List<LocationResponse> getAllLocations(String region, Boolean isActive, String search);

    GeoJsonFeatureCollection getAllLocationsAsGeoJson(
            String region, Boolean isActive, String search);

    LocationResponse getLocationById(UUID id);

    LocationResponse getLocationByCode(String code);

    List<LocationNearbyResponse> getNearbyLocations(
            double longitude, double latitude, double radiusKm);
}
