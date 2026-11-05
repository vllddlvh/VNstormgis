package vn.weathergis.alert.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.weathergis.alert.dto.AlertDetailResponse;
import vn.weathergis.alert.service.AlertService;
import vn.weathergis.common.api.GeoJsonFeatureCollection;

@Tag(
        name = "6. Cảnh Báo Thiên Tai Chủ Động (Alerts)",
        description =
                "Truy vấn công khai danh sách cảnh báo đang hoạt động dạng GeoJSON RFC 7946 "
                        + "(/api/v1/alerts/active) và chi tiết từng cảnh báo kèm bản chụp dữ kiện "
                        + "bất biến (/api/v1/alerts/{id}). Lỗi theo RFC 7807 với mã ALR_400_INVALID_ID "
                        + "và ALR_404_NOT_FOUND.")
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @Operation(
            summary = "Lấy danh sách cảnh báo đang hoạt động (GeoJSON RFC 7946)",
            description =
                    "Trả về FeatureCollection thô (không bọc ApiResponse) chỉ gồm các cảnh báo "
                            + "có status='active', sắp xếp theo triggered_at DESC, id ASC. Mỗi Feature "
                            + "chứa id=alerts.id, geometry=[lng, lat] WGS84 (hoặc null cho dữ liệu legacy "
                            + "thiếu hình học) và properties gồm alert_id, location_code, location_name, "
                            + "severity_level, title, trigger_reason, triggered_at, is_fallback. Khi không "
                            + "có cảnh báo active, trả về features: [] với HTTP 200.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description =
                        "Truy vấn thành công (collection có thể rỗng khi không có cảnh báo active)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = GeoJsonFeatureCollection.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi xử lý nội bộ (RFC 7807 COM_500_INTERNAL_ERROR)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping(value = "/active", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GeoJsonFeatureCollection> getActiveAlerts() {
        return ResponseEntity.ok(alertService.getActiveAlertsAsGeoJson());
    }

    @Operation(
            summary = "Lấy chi tiết một cảnh báo theo UUID",
            description =
                    "Trả về ApiResponse<AlertDetailResponse> cho mọi trạng thái (active, resolved, "
                            + "dismissed). Bao gồm nội dung giải thích AI hoặc fallback (is_fallback), "
                            + "các đối tượng liên kết hiện tại (location, threshold, storm — có thể null "
                            + "nếu bản ghi gốc đã xóa) và factual_snapshot lưu dữ kiện bất biến tại thời "
                            + "điểm phát hiện vượt ngưỡng (null đối với bản ghi legacy không có snapshot).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Truy vấn chi tiết cảnh báo thành công",
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
                description =
                        "Mã định danh không đúng định dạng UUID (RFC 7807 ALR_400_INVALID_ID)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description =
                        "Không tìm thấy cảnh báo với UUID yêu cầu (RFC 7807 ALR_404_NOT_FOUND)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi xử lý nội bộ (RFC 7807 COM_500_INTERNAL_ERROR)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<AlertDetailResponse>> getAlertById(
            @Parameter(
                            description = "Định danh UUID của cảnh báo",
                            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                            required = true)
                    @PathVariable("id")
                    UUID id) {
        return ResponseEntity.ok(
                vn.weathergis.common.api.ApiResponse.ok(
                        alertService.getAlertById(id), "Lấy chi tiết cảnh báo thành công"));
    }
}
