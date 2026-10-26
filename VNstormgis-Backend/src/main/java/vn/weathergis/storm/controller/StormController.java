package vn.weathergis.storm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.InvalidParameterException;
import vn.weathergis.storm.dto.StormDetailResponse;
import vn.weathergis.storm.dto.StormSummaryResponse;
import vn.weathergis.storm.service.StormService;

/** Bộ điều khiển RESTful API dữ liệu bão nhiệt đới — tiêu thụ bởi lớp Leaflet Bão (W3-05/W3-06). */
@Tag(
        name = "3. Theo Dõi Bão Nhiệt Đới (Storms)",
        description =
                "Danh mục bão nhiệt đới từ GDACS: danh sách theo trạng thái SYS-RULE-001 (mặc định "
                        + "chỉ bão active) dạng GeoJSON RFC 7946 và chi tiết từng cơn bão. Dữ liệu do "
                        + "Ingestion Engine GDACS nạp mỗi 30 phút (W2-05).")
@RestController
@RequestMapping("/api/v1/storms")
public class StormController {

    private static final Set<String> VALID_STATUS = Set.of("active", "dissipated", "all");

    private final StormService stormService;

    public StormController(StormService stormService) {
        this.stormService = stormService;
    }

    @Operation(
            summary = "Lấy danh sách bão (GeoJSON RFC 7946)",
            description =
                    "Mặc định chỉ trả bão đang hoạt động (status=active) theo SYS-RULE-001 — lớp bản "
                            + "đồ khẩn cấp loại trừ bão đã tan. Truyền status=dissipated/all để truy vết "
                            + "bão đã tan. Mỗi Feature là 1 Point tại tâm bão hiện tại (geom_current), "
                            + "tọa độ [lng, lat] WGS84. Mặc định format=geojson nạp trực tiếp vào "
                            + "Leaflet L.geoJSON(); format=json trả ApiResponse danh sách phẳng.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description =
                        "Truy vấn thành công (collection có thể rỗng khi Biển Đông không có bão)",
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
                responseCode = "400",
                description = "Giá trị status không hợp lệ (RFC 7807 STM_400_INVALID_STATUS)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi xử lý nội bộ (RFC 7807)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public ResponseEntity<?> getStorms(
            @Parameter(
                            description = "Lọc trạng thái bão theo SYS-RULE-001",
                            example = "active",
                            schema =
                                    @Schema(
                                            allowableValues = {"active", "dissipated", "all"},
                                            defaultValue = "active"))
                    @RequestParam(name = "status", defaultValue = "active")
                    String status,
            @Parameter(
                            description = "Định dạng: 'geojson' (mặc định) hoặc 'json'",
                            example = "geojson",
                            schema =
                                    @Schema(
                                            allowableValues = {"geojson", "json"},
                                            defaultValue = "geojson"))
                    @RequestParam(name = "format", defaultValue = "geojson")
                    String format) {

        String normalized = status == null ? "active" : status.trim().toLowerCase(Locale.ROOT);
        if (!VALID_STATUS.contains(normalized)) {
            throw new InvalidParameterException(
                    ErrorCode.STORM_INVALID_STATUS,
                    "Tham số status phải thuộc {active, dissipated, all}, nhận: " + status);
        }
        if ("json".equalsIgnoreCase(format)) {
            List<StormSummaryResponse> storms = stormService.getStorms(normalized);
            String message =
                    storms.isEmpty() && "active".equals(normalized)
                            ? "Hiện không có cơn bão nào đang hoạt động trong khu vực theo dõi"
                            : "Lấy danh sách bão thành công";
            return ResponseEntity.ok(vn.weathergis.common.api.ApiResponse.ok(storms, message));
        }
        return ResponseEntity.ok(stormService.getStormsAsGeoJson(normalized));
    }

    @Operation(
            summary = "Lấy chi tiết một cơn bão theo mã sự kiện GDACS",
            description =
                    "Trả về toàn bộ thông số cơn bão (tên, cấp, gió tối đa m/s và km/h, áp suất tâm "
                            + "khi GDACS công bố) kèm vị trí tâm hiện tại và tổng số mốc quỹ đạo. "
                            + "Áp suất tâm có thể null — nguồn GDACS không luôn công bố (SYS-RULE-004).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Truy vấn thành công",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema =
                                        @Schema(
                                                implementation =
                                                        vn.weathergis.common.api.ApiResponse
                                                                .class))),
        @ApiResponse(
                responseCode = "404",
                description = "Không tìm thấy mã sự kiện bão (RFC 7807 STM_404_NOT_FOUND)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi xử lý nội bộ (RFC 7807)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<StormDetailResponse>> getStormDetail(
            @Parameter(
                            description = "Mã sự kiện bão GDACS (khóa chính trong bảng storms)",
                            example = "1001209",
                            required = true)
                    @PathVariable(name = "id")
                    String id) {

        return ResponseEntity.ok(
                vn.weathergis.common.api.ApiResponse.ok(
                        stormService.getStormDetail(id), "Lấy chi tiết bão thành công"));
    }
}
