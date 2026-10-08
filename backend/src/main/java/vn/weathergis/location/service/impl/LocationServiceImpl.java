package vn.weathergis.location.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.weathergis.common.api.GeoJsonFeature;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.InvalidParameterException;
import vn.weathergis.common.exception.ResourceNotFoundException;
import vn.weathergis.location.domain.LocationEntity;
import vn.weathergis.location.dto.LocationNearbyResponse;
import vn.weathergis.location.dto.LocationResponse;
import vn.weathergis.location.repository.LocationRepository;
import vn.weathergis.location.service.LocationService;

/** Hiện thực hóa logic nghiệp vụ quản lý trạm quan trắc. */
@Service
@Transactional(readOnly = true)
public class LocationServiceImpl implements LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationServiceImpl.class);
    private final LocationRepository locationRepository;

    public LocationServiceImpl(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Override
    public List<LocationResponse> getAllLocations(String region, Boolean isActive, String search) {
        List<LocationEntity> entities =
                locationRepository.searchLocations(region, isActive, search);
        return entities.stream().map(LocationResponse::fromEntity).toList();
    }

    @Override
    public GeoJsonFeatureCollection getAllLocationsAsGeoJson(
            String region, Boolean isActive, String search) {
        List<LocationEntity> entities =
                locationRepository.searchLocations(region, isActive, search);
        GeoJsonFeatureCollection collection = new GeoJsonFeatureCollection();

        for (LocationEntity entity : entities) {
            Map<String, Object> props = new HashMap<>();
            props.put("id", entity.getId().toString());
            props.put("code", entity.getCode());
            props.put("name", entity.getName());
            props.put("region", entity.getRegion());
            props.put("isActive", entity.getIsActive());
            props.put(
                    "updatedAt",
                    entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);

            GeoJsonFeature feature =
                    new GeoJsonFeature(entity.getId().toString(), entity.getGeom(), props);
            collection.addFeature(feature);
        }

        return collection;
    }

    @Override
    public LocationResponse getLocationById(UUID id) {
        LocationEntity entity =
                locationRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                ErrorCode.LOCATION_NOT_FOUND,
                                                "Không tìm thấy trạm quan trắc với định danh ID: "
                                                        + id));
        return LocationResponse.fromEntity(entity);
    }

    @Override
    public LocationResponse getLocationByCode(String code) {
        LocationEntity entity =
                locationRepository
                        .findByCode(code)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                ErrorCode.LOCATION_NOT_FOUND,
                                                "Không tìm thấy trạm quan trắc với mã trạm: "
                                                        + code));
        return LocationResponse.fromEntity(entity);
    }

    @Override
    public List<LocationNearbyResponse> getNearbyLocations(
            double longitude, double latitude, double radiusKm) {
        if (longitude < -180.0 || longitude > 180.0) {
            throw new InvalidParameterException(
                    "Kinh độ (longitude) phải nằm trong khoảng [-180, 180]");
        }
        if (latitude < -90.0 || latitude > 90.0) {
            throw new InvalidParameterException("Vĩ độ (latitude) phải nằm trong khoảng [-90, 90]");
        }
        if (radiusKm <= 0.0 || radiusKm > 2000.0) {
            throw new InvalidParameterException(
                    "Bán kính tìm kiếm (radiusKm) phải lớn hơn 0 và nhỏ hơn hoặc bằng 2000 km");
        }

        double radiusMeters = radiusKm * 1000.0;
        List<Object[]> rawList =
                locationRepository.findNearbyStationsNative(longitude, latitude, radiusMeters);

        return rawList.stream()
                .map(
                        row ->
                                new LocationNearbyResponse(
                                        row[0] instanceof UUID u
                                                ? u
                                                : UUID.fromString(row[0].toString()),
                                        (String) row[1],
                                        (String) row[2],
                                        (String) row[3],
                                        ((Number) row[4]).doubleValue(),
                                        ((Number) row[5]).doubleValue(),
                                        Math.round(((Number) row[6]).doubleValue() * 10.0) / 10.0))
                .toList();
    }
}
