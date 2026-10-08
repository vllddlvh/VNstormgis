package vn.weathergis.location.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.weathergis.common.api.ApiResponse;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.location.dto.LocationNearbyResponse;
import vn.weathergis.location.dto.LocationResponse;
import vn.weathergis.location.service.LocationService;

/** Bộ điều khiển RESTful API quản lý trạm quan trắc khí tượng và địa không gian. */
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * Lấy danh sách điểm quan trắc hỗ trợ kép định dạng JSON hoặc GeoJSON.
     *
     * @param format 'json' (mặc định) hoặc 'geojson' chuẩn RFC 7946 cho Leaflet
     * @param region Lọc theo vùng địa lý
     * @param isActive Lọc theo trạng thái hoạt động (mặc định true)
     * @param search Tìm kiếm theo mã hoặc tên trạm
     */
    @GetMapping
    public ResponseEntity<?> getLocations(
            @RequestParam(name = "format", defaultValue = "json") String format,
            @RequestParam(name = "region", required = false) String region,
            @RequestParam(name = "is_active", defaultValue = "true") Boolean isActive,
            @RequestParam(name = "search", required = false) String search) {

        if ("geojson".equalsIgnoreCase(format)) {
            GeoJsonFeatureCollection geoJson =
                    locationService.getAllLocationsAsGeoJson(region, isActive, search);
            return ResponseEntity.ok(geoJson);
        }

        List<LocationResponse> locations =
                locationService.getAllLocations(region, isActive, search);
        return ResponseEntity.ok(
                ApiResponse.ok(locations, "Lấy danh sách trạm quan trắc thành công"));
    }

    /** Lấy chi tiết trạm quan trắc theo khóa định danh UUID. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LocationResponse>> getLocationById(
            @PathVariable("id") UUID id) {
        LocationResponse response = locationService.getLocationById(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /** Lấy chi tiết trạm quan trắc theo mã tự nhiên (ví dụ: HAN, DNG, SGN). */
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<LocationResponse>> getLocationByCode(
            @PathVariable("code") String code) {
        LocationResponse response = locationService.getLocationByCode(code);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /** Tìm kiếm các trạm quan trắc gần nhất quanh một tọa độ trắc địa. */
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<LocationNearbyResponse>>> getNearbyLocations(
            @RequestParam("lng") double longitude,
            @RequestParam("lat") double latitude,
            @RequestParam(name = "radiusKm", defaultValue = "100.0") double radiusKm) {

        List<LocationNearbyResponse> nearby =
                locationService.getNearbyLocations(longitude, latitude, radiusKm);
        return ResponseEntity.ok(
                ApiResponse.ok(nearby, "Tìm thấy " + nearby.size() + " trạm lân cận"));
    }
}
