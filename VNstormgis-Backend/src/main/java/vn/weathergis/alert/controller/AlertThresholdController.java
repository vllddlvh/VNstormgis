package vn.weathergis.alert.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import vn.weathergis.alert.dto.AlertThresholdPutRequest;
import vn.weathergis.alert.dto.AlertThresholdRequest;
import vn.weathergis.alert.dto.AlertThresholdResponse;
import vn.weathergis.alert.service.AlertThresholdService;
import vn.weathergis.common.api.ApiResponse;

@RestController
@RequestMapping(value = "/api/v1/alert-thresholds", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
        name = "5. Quản Trị Ngưỡng Cảnh Báo (Alert Thresholds)",
        description =
                "Đọc công khai; POST/PUT/PATCH/DELETE yêu cầu X-Admin-Api-Key. Lỗi RFC 7807 ALT_400/401/404.")
@SecurityScheme(
        name = "AdminApiKey",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-Admin-Api-Key",
        description =
                "Khóa quản trị từ biến môi trường ADMIN_API_KEY; chỉ bắt buộc cho thao tác ghi.")
public class AlertThresholdController {

    private final AlertThresholdService service;

    public AlertThresholdController(AlertThresholdService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Danh sách ngưỡng cảnh báo (public)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Danh sách ngưỡng cảnh báo")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Tham số hoặc body không hợp lệ (ALT_400_*)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Lỗi hệ thống COM_500_INTERNAL_ERROR",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ApiResponse<List<AlertThresholdResponse>>> findAll(
            @Parameter(description = "rain_1h, rain_3h, wind_speed, wind_gust hoặc storm_dist")
                    @RequestParam(required = false)
                    String parameter,
            @RequestParam(name = "is_enabled", required = false) Boolean isEnabled) {
        return ResponseEntity.ok(ApiResponse.ok(service.findAll(parameter, isEnabled)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết ngưỡng cảnh báo (public)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Chi tiết ngưỡng cảnh báo")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Tham số hoặc body không hợp lệ (ALT_400_*)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Không tìm thấy ngưỡng (ALT_404_NOT_FOUND)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Lỗi hệ thống COM_500_INTERNAL_ERROR",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ApiResponse<AlertThresholdResponse>> findById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(service.findById(id)));
    }

    @PostMapping
    @Operation(
            summary = "Tạo ngưỡng cảnh báo",
            security = @SecurityRequirement(name = "AdminApiKey"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Đã tạo ngưỡng cảnh báo",
            headers =
                    @Header(
                            name = "Location",
                            description = "URI chi tiết ngưỡng vừa tạo",
                            schema = @Schema(type = "string", format = "uri")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Tham số hoặc body không hợp lệ (ALT_400_*)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Khóa quản trị thiếu, sai hoặc cấu hình rỗng (ALT_401_UNAUTHORIZED)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Lỗi hệ thống COM_500_INTERNAL_ERROR",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ApiResponse<AlertThresholdResponse>> create(
            @RequestBody AlertThresholdRequest request) {
        AlertThresholdResponse created = service.create(request);
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequestUri()
                        .path("/{id}")
                        .buildAndExpand(created.id())
                        .toUri();
        return ResponseEntity.created(location).body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Thay thế toàn bộ trường nghiệp vụ (kể cả is_enabled)",
            security = @SecurityRequirement(name = "AdminApiKey"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Ngưỡng đã cập nhật")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Tham số hoặc body không hợp lệ (ALT_400_*)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Khóa quản trị thiếu, sai hoặc cấu hình rỗng (ALT_401_UNAUTHORIZED)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Không tìm thấy ngưỡng (ALT_404_NOT_FOUND)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Lỗi hệ thống COM_500_INTERNAL_ERROR",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ApiResponse<AlertThresholdResponse>> update(
            @PathVariable Integer id,
            @RequestBody
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    AlertThresholdPutRequest
                                                                            .class)))
                    AlertThresholdRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, request)));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(
            summary = "Đảo is_enabled một lần mỗi yêu cầu (không có request body)",
            security = @SecurityRequirement(name = "AdminApiKey"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Trạng thái ngưỡng đã đảo")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Tham số hoặc body không hợp lệ (ALT_400_*)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Khóa quản trị thiếu, sai hoặc cấu hình rỗng (ALT_401_UNAUTHORIZED)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Không tìm thấy ngưỡng (ALT_404_NOT_FOUND)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Lỗi hệ thống COM_500_INTERNAL_ERROR",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ApiResponse<AlertThresholdResponse>> toggle(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(service.toggle(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Xóa ngưỡng; giữ cảnh báo cũ với threshold_id=null",
            security = @SecurityRequirement(name = "AdminApiKey"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "Đã xóa ngưỡng cảnh báo",
            content = @Content)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Tham số hoặc body không hợp lệ (ALT_400_*)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Khóa quản trị thiếu, sai hoặc cấu hình rỗng (ALT_401_UNAUTHORIZED)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Không tìm thấy ngưỡng (ALT_404_NOT_FOUND)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Lỗi hệ thống COM_500_INTERNAL_ERROR",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
