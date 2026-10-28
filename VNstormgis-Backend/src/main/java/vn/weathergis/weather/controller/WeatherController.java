package vn.weathergis.weather.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.common.cache.CacheRegions;
import vn.weathergis.common.cache.GeoJsonCacheService;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.InvalidParameterException;
import vn.weathergis.weather.dto.WeatherHistoryResponse;
import vn.weathergis.weather.service.WeatherService;

/** Bộ điều khiển RESTful API dữ liệu thời tiết khí tượng — tiêu thụ bởi lớp Leaflet Mưa/Gió. */
@Tag(
        name = "2. Dữ Liệu Thời Tiết Khí Tượng (Weather)",
        description =
                "Bản ghi đo đạc mới nhất của từng trạm quan trắc (DISTINCT ON PostGIS, GeoJSON RFC 7946) "
                        + "và chuỗi thời gian lịch sử phục vụ biểu đồ diễn biến mưa/gió. Dữ liệu do Ingestion "
                        + "Engine OpenWeatherMap nạp mỗi 15 phút (W2-03).")
@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

    private final WeatherService weatherService;
    private final GeoJsonCacheService geoJsonCacheService;

    public WeatherController(
            WeatherService weatherService, GeoJsonCacheService geoJsonCacheService) {
        this.weatherService = weatherService;
        this.geoJsonCacheService = geoJsonCacheService;
    }

    @Operation(
            summary = "Lấy dữ liệu thời tiết hiện tại toàn mạng lưới (GeoJSON RFC 7946)",
            description =
                    "Bản ghi MỚI NHẤT của từng trạm active (truy vấn DISTINCT ON). Mặc định trả về "
                            + "FeatureCollection nạp trực tiếp vào Leaflet L.geoJSON(); truyền format=json "
                            + "để nhận ApiResponse danh sách phẳng.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description =
                        "Truy vấn thành công (collection có thể rỗng nếu chưa có dữ liệu nạp)",
                content = {
                    @Content(
                            mediaType = "application/geo+json",
                            schema = @Schema(implementation = GeoJsonFeatureCollection.class)),
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema =
                                    @Schema(
                                            implementation =
                                                    vn.weathergis.common.api.ApiResponse.class))
                }),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi xử lý nội bộ (RFC 7807)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentWeather(
            @Parameter(
                            description = "Định dạng: 'geojson' (mặc định) hoặc 'json'",
                            example = "geojson",
                            schema =
                                    @Schema(
                                            allowableValues = {"geojson", "json"},
                                            defaultValue = "geojson"))
                    @RequestParam(name = "format", defaultValue = "geojson")
                    String format) {

        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(
                    vn.weathergis.common.api.ApiResponse.ok(
                            weatherService.getCurrentWeather(),
                            "Lấy dữ liệu thời tiết hiện tại thành công"));
        }
        // W3-03: chuỗi GeoJSON đã render được đệm 15 phút (khớp chu kỳ sync OWM). Content-type khai
        // báo tường minh để body kiểu String không rơi về text/plain (quyết định #10).
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        geoJsonCacheService.getOrLoad(
                                CacheRegions.WEATHER_CURRENT,
                                "geojson",
                                weatherService::getCurrentWeatherAsGeoJson));
    }

    @Operation(
            summary = "Lấy chuỗi thời gian khí tượng của một trạm",
            description =
                    "Trả về các mốc đo đạc tăng dần theo thời gian trong cửa sổ N giờ qua (mặc định 24h, "
                            + "tối đa 168h) — phục vụ biểu đồ diễn biến lượng mưa và vận tốc gió.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Truy vấn thành công (readings có thể rỗng trong cửa sổ)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema =
                                        @Schema(
                                                implementation =
                                                        vn.weathergis.common.api.ApiResponse
                                                                .class))),
        @ApiResponse(
                responseCode = "400",
                description = "Tham số hours ngoài phạm vi 1..168 (RFC 7807)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Không tìm thấy mã trạm (RFC 7807 WTH_404_NOT_FOUND)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/history")
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<WeatherHistoryResponse>>
            getWeatherHistory(
                    @Parameter(
                                    description = "Mã trạm quan trắc (không phân biệt hoa/thường)",
                                    example = "HAN",
                                    required = true)
                            @RequestParam(name = "location_code")
                            String locationCode,
                    @Parameter(
                                    description =
                                            "Cửa sổ thời gian (giờ), mặc định 24, hợp lệ 1..168",
                                    example = "24",
                                    schema =
                                            @Schema(
                                                    type = "integer",
                                                    defaultValue = "24",
                                                    minimum = "1",
                                                    maximum = "168"))
                            @RequestParam(name = "hours", defaultValue = "24")
                            int hours) {

        if (hours < 1 || hours > 168) {
            throw new InvalidParameterException(
                    ErrorCode.INVALID_PARAMETER, "Tham số hours phải nằm trong phạm vi 1..168");
        }
        return ResponseEntity.ok(
                vn.weathergis.common.api.ApiResponse.ok(
                        weatherService.getWeatherHistory(locationCode, hours),
                        "Lấy chuỗi thời gian trạm "
                                + locationCode.trim().toUpperCase(Locale.ROOT)
                                + " thành công"));
    }
}
