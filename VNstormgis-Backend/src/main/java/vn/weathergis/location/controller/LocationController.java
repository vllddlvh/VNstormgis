package vn.weathergis.location.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.common.cache.CacheRegions;
import vn.weathergis.common.cache.GeoJsonCacheService;
import vn.weathergis.location.dto.LocationNearbyResponse;
import vn.weathergis.location.dto.LocationResponse;
import vn.weathergis.location.service.LocationService;

/** Bộ điều khiển RESTful API quản lý trạm quan trắc khí tượng và địa không gian. */
@Tag(
        name = "1. Trạm Quan Trắc Trắc Địa (Locations)",
        description =
                "Truy vấn mạng lưới 68 điểm trắc địa quốc gia (63 tỉnh/thành phố và 5 trạm hải đảo) "
                        + "hỗ trợ định dạng kép JSON / GeoJSON RFC 7946 và truy vấn không gian PostGIS ST_DistanceSphere.")
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;
    private final GeoJsonCacheService geoJsonCacheService;

    public LocationController(
            LocationService locationService, GeoJsonCacheService geoJsonCacheService) {
        this.locationService = locationService;
        this.geoJsonCacheService = geoJsonCacheService;
    }

    /** Lấy danh sách điểm quan trắc hỗ trợ kép định dạng JSON hoặc GeoJSON. */
    @Operation(
            summary = "Lấy danh sách trạm quan trắc (Hỗ trợ kép JSON / GeoJSON)",
            description =
                    "Truy vấn danh bạ các trạm quan trắc khí tượng trên toàn lãnh thổ Việt Nam. "
                            + "Khi truyền `format=geojson`, trả về cấu trúc FeatureCollection chuẩn RFC 7946 nạp trực tiếp vào Leaflet `L.geoJSON()`.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Truy vấn danh sách trạm thành công",
                content = {
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema =
                                    @Schema(
                                            implementation =
                                                    vn.weathergis.common.api.ApiResponse.class)),
                    @Content(
                            mediaType = "application/geo+json",
                            schema = @Schema(implementation = GeoJsonFeatureCollection.class))
                }),
        @ApiResponse(
                responseCode = "400",
                description = "Tham số lọc không hợp lệ (RFC 7807)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public ResponseEntity<?> getLocations(
            @Parameter(
                            description =
                                    "Định dạng kết quả trả về: 'json' (mặc định) hoặc 'geojson' chuẩn Leaflet",
                            example = "json",
                            schema =
                                    @Schema(
                                            allowableValues = {"json", "geojson"},
                                            defaultValue = "json"))
                    @RequestParam(name = "format", defaultValue = "json")
                    String format,
            @Parameter(
                            description = "Lọc theo vùng địa lý (Bắc Bộ, Trung Bộ, Nam Bộ)",
                            example = "Bắc Bộ")
                    @RequestParam(name = "region", required = false)
                    String region,
            @Parameter(
                            description = "Lọc theo trạng thái hoạt động (mặc định true)",
                            example = "true")
                    @RequestParam(name = "is_active", defaultValue = "true")
                    Boolean isActive,
            @Parameter(
                            description = "Từ khóa tìm kiếm theo tên tỉnh/thành hoặc mã trạm",
                            example = "Hà Nội")
                    @RequestParam(name = "search", required = false)
                    String search) {

        if ("geojson".equalsIgnoreCase(format)) {
            // W3-03: bộ lọc nằm trong URL nên khóa đệm ghép đủ 3 tham số — hai truy vấn khác
            // filter không được dùng chung một mục tử Redis (TTL 24h cho danh bạ gần như tĩnh).
            // Content-type khai báo tường minh vì body là String đã render sẵn (quyết định #10).
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                            geoJsonCacheService.getOrLoad(
                                    CacheRegions.LOCATIONS,
                                    "geojson|"
                                            + nullToEmpty(region)
                                            + "|"
                                            + isActive
                                            + "|"
                                            + nullToEmpty(search),
                                    () ->
                                            locationService.getAllLocationsAsGeoJson(
                                                    region, isActive, search)));
        }

        List<LocationResponse> locations =
                locationService.getAllLocations(region, isActive, search);
        return ResponseEntity.ok(
                vn.weathergis.common.api.ApiResponse.ok(
                        locations, "Lấy danh sách trạm quan trắc thành công"));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** Lấy chi tiết trạm quan trắc theo khóa định danh UUID. */
    @Operation(
            summary = "Lấy chi tiết trạm theo UUID",
            description =
                    "Tra cứu thông tin chi tiết một trạm trắc địa thông qua khóa chính UUID duy nhất.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tìm thấy trạm quan trắc",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        vn.weathergis.common.api.ApiResponse
                                                                .class))),
        @ApiResponse(
                responseCode = "404",
                description = "Không tìm thấy trạm với UUID cung cấp (RFC 7807)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<LocationResponse>> getLocationById(
            @Parameter(
                            description = "Định danh UUID của trạm",
                            example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
                    @PathVariable("id")
                    UUID id) {
        LocationResponse response = locationService.getLocationById(id);
        return ResponseEntity.ok(vn.weathergis.common.api.ApiResponse.ok(response));
    }

    /** Lấy chi tiết trạm quan trắc theo mã tự nhiên (ví dụ: HAN, DNG, SGN). */
    @Operation(
            summary = "Lấy chi tiết trạm theo mã trắc địa (Code)",
            description =
                    "Tra cứu nhanh thông tin trạm theo mã ký hiệu viết tắt 3 ký tự (ví dụ: HAN - Hà Nội, DNG - Đà Nẵng, SGN - TP.HCM).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tìm thấy trạm quan trắc",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        vn.weathergis.common.api.ApiResponse
                                                                .class))),
        @ApiResponse(
                responseCode = "404",
                description = "Không tìm thấy mã trạm (RFC 7807)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/code/{code}")
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<LocationResponse>> getLocationByCode(
            @Parameter(description = "Mã viết tắt trạm trắc địa (3 ký tự in hoa)", example = "HAN")
                    @PathVariable("code")
                    String code) {
        LocationResponse response = locationService.getLocationByCode(code);
        return ResponseEntity.ok(vn.weathergis.common.api.ApiResponse.ok(response));
    }

    /** Tìm kiếm các trạm quan trắc gần nhất quanh một tọa độ trắc địa. */
    @Operation(
            summary = "Tìm kiếm trạm lân cận quanh tọa độ (ST_DistanceSphere)",
            description =
                    "Thực thi truy vấn không gian PostGIS sử dụng hàm cầu `ST_DistanceSphere` để tính "
                            + "khoảng cách thực tế theo đường chim bay từ một điểm trắc địa WGS84 tâm tới các trạm trong bán kính cho trước.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Truy vấn không gian thành công",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        vn.weathergis.common.api.ApiResponse
                                                                .class))),
        @ApiResponse(
                responseCode = "400",
                description = "Tọa độ hoặc bán kính không hợp lệ (RFC 7807)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/nearby")
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<List<LocationNearbyResponse>>>
            getNearbyLocations(
                    @Parameter(description = "Kinh độ tâm WGS84 [-180, 180]", example = "105.8542")
                            @RequestParam("lng")
                            double longitude,
                    @Parameter(description = "Vĩ độ tâm WGS84 [-90, 90]", example = "21.0285")
                            @RequestParam("lat")
                            double latitude,
                    @Parameter(
                                    description =
                                            "Bán kính quét tìm kiếm tính bằng Kilometers (mặc định 100km)",
                                    example = "50.0")
                            @RequestParam(name = "radiusKm", defaultValue = "100.0")
                            double radiusKm) {

        List<LocationNearbyResponse> nearby =
                locationService.getNearbyLocations(longitude, latitude, radiusKm);
        return ResponseEntity.ok(
                vn.weathergis.common.api.ApiResponse.ok(
                        nearby, "Tìm thấy " + nearby.size() + " trạm lân cận"));
    }
}
