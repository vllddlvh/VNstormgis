package vn.weathergis.subscription.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.weathergis.common.exception.InvalidParameterException;
import vn.weathergis.subscription.config.SubscriptionConfig.SubscriptionMailProperties;
import vn.weathergis.subscription.dto.CreateSubscriptionRequest;
import vn.weathergis.subscription.dto.SubscriptionResponse;
import vn.weathergis.subscription.dto.TokenActionRequest;
import vn.weathergis.subscription.dto.VerifyOtpRequest;
import vn.weathergis.subscription.service.EmailDispatchService;
import vn.weathergis.subscription.service.EmailSubscriptionService;

@Tag(
        name = "8. Đăng Ký Cảnh Báo Email (Subscriptions)",
        description =
                "Đăng ký nhận thông báo cảnh báo thiên tai tự động qua Email theo Tỉnh/Trạm quan trắc "
                        + "hoặc Tọa độ vị trí hiện tại (GPS + bán kính PostGIS ST_DWithin), hỗ trợ xác thực OTP 6 chữ số, "
                        + "xác thực liên kết 1-click và hủy đăng ký 1-click.")
@RestController
@RequestMapping("/api/v1/subscriptions")
public class EmailSubscriptionController {

    private final EmailSubscriptionService subscriptionService;
    private final SubscriptionMailProperties mailProperties;

    public EmailSubscriptionController(
            EmailSubscriptionService subscriptionService,
            SubscriptionMailProperties mailProperties) {
        this.subscriptionService = subscriptionService;
        this.mailProperties = mailProperties;
    }

    @Operation(
            summary = "Đăng ký nhận thông báo cảnh báo qua Email (gửi mã xác thực OTP & liên kết)",
            description =
                    "Tạo đăng ký ở trạng thái PENDING theo STATION (locationCode/locationId) hoặc COORDINATE (longitude/latitude), "
                            + "kèm bán kính cảnh báo (5–500 km) và mức độ tối thiểu (INFO/WARNING/DANGER/CRITICAL).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Đã tạo yêu cầu đăng ký và gửi mã xác thực"),
        @ApiResponse(
                responseCode = "400",
                description = "Dữ liệu không hợp lệ (RFC 7807 SUB_400_VALIDATION)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "429",
                description = "Vượt tần suất gửi mã xác thực (RFC 7807 SUB_429_RATE_LIMITED)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<SubscriptionResponse>>
            createSubscription(@Valid @RequestBody CreateSubscriptionRequest request) {
        SubscriptionResponse response = subscriptionService.createSubscription(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        vn.weathergis.common.api.ApiResponse.ok(
                                response,
                                "Đã gửi mã xác thực 6 chữ số tới hộp thư "
                                        + response.maskedEmail()));
    }

    @Operation(summary = "Xác thực đăng ký bằng mã OTP 6 chữ số")
    @PostMapping("/{id}/verify")
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<SubscriptionResponse>> verifyByOtp(
            @PathVariable("id") UUID id, @Valid @RequestBody VerifyOtpRequest request) {
        SubscriptionResponse response = subscriptionService.verifyByOtp(id, request.otp());
        return ResponseEntity.ok(
                vn.weathergis.common.api.ApiResponse.ok(
                        response,
                        "Đã xác thực và kích hoạt nhận cảnh báo cho khu vực " + response.label()));
    }

    @Operation(summary = "Xác thực đăng ký bằng token liên kết (JSON API)")
    @PostMapping("/verify-token")
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<SubscriptionResponse>> verifyByToken(
            @Valid @RequestBody TokenActionRequest request) {
        SubscriptionResponse response = subscriptionService.verifyByToken(request.token());
        return ResponseEntity.ok(
                vn.weathergis.common.api.ApiResponse.ok(
                        response,
                        "Đã xác thực và kích hoạt nhận cảnh báo cho khu vực " + response.label()));
    }

    @Operation(summary = "Liên kết xác thực trực tiếp 1-click từ Email (HTML)")
    @GetMapping(value = "/verify", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verifyByEmailLink(
            @RequestParam(name = "token", required = false) String token) {
        try {
            SubscriptionResponse res = subscriptionService.verifyByToken(token);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                    .body(
                            renderSanRitaStatusPage(
                                    true,
                                    "ĐÃ KÍCH HOẠT NHẬN CẢNH BÁO EMAIL",
                                    "Đăng ký nhận cảnh báo cho khu vực "
                                            + res.label()
                                            + " (bán kính "
                                            + res.radiusKm()
                                            + " km, mức "
                                            + res.minSeverity()
                                            + " trở lên) đã được kích hoạt thành công."));
        } catch (InvalidParameterException ex) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                    .body(
                            renderSanRitaStatusPage(
                                    false, "XÁC THỰC KHÔNG THÀNH CÔNG", ex.getMessage()));
        }
    }

    @Operation(summary = "Hủy đăng ký nhận thông báo bằng token 1-click (JSON API)")
    @PostMapping("/unsubscribe")
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<SubscriptionResponse>>
            unsubscribeJson(@Valid @RequestBody TokenActionRequest request) {
        SubscriptionResponse response = subscriptionService.unsubscribeByToken(request.token());
        return ResponseEntity.ok(
                vn.weathergis.common.api.ApiResponse.ok(
                        response, "Đã hủy đăng ký nhận cảnh báo cho khu vực " + response.label()));
    }

    @Operation(summary = "Liên kết hủy đăng ký trực tiếp 1-click từ Email (HTML)")
    @GetMapping(value = "/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribeByEmailLink(
            @RequestParam(name = "token", required = false) String token) {
        try {
            SubscriptionResponse res = subscriptionService.unsubscribeByToken(token);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                    .body(
                            renderSanRitaStatusPage(
                                    true,
                                    "ĐÃ HỦY ĐĂNG KÝ NHẬN EMAIL",
                                    "Hệ thống đã ngừng gửi thông báo cảnh báo cho khu vực "
                                            + res.label()
                                            + ". Bạn có thể đăng ký lại bất cứ lúc nào trên bản đồ VNstormgis."));
        } catch (InvalidParameterException ex) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                    .body(renderSanRitaStatusPage(false, "KHÔNG THỂ HỦY ĐĂNG KÝ", ex.getMessage()));
        }
    }

    @Operation(
            summary =
                    "Gửi bản tin kiểm tra kết nối Email (số liệu thực tế PostGIS tại điểm đăng ký)")
    @PostMapping("/{id}/test-notification")
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<SubscriptionResponse>>
            sendTestNotification(
                    @PathVariable("id") UUID id,
                    @RequestHeader(name = "X-Unsubscribe-Token", required = false)
                            String headerToken,
                    @RequestBody(required = false) TokenActionRequest bodyToken) {
        String token =
                headerToken != null && !headerToken.isBlank()
                        ? headerToken
                        : (bodyToken != null ? bodyToken.token() : null);
        SubscriptionResponse response = subscriptionService.sendTestNotification(id, token);
        return ResponseEntity.ok(
                vn.weathergis.common.api.ApiResponse.ok(
                        response, "Đã gửi bản tin kiểm tra kết nối tới " + response.maskedEmail()));
    }

    private String renderSanRitaStatusPage(boolean success, String heading, String message) {
        String accent = success ? "#e2ffcc" : "#dc2626";
        String webUrl = EmailDispatchService.escapeHtml(mailProperties.publicWebUrl());
        return "<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>VNstormgis — "
                + EmailDispatchService.escapeHtml(heading)
                + "</title></head>"
                + "<body style=\"margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#161b13;color:#dde2e4;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;padding:20px;\">"
                + "<div style=\"max-width:520px;width:100%;background:#1a2117;border:1px solid "
                + accent
                + ";padding:28px;\">"
                + "<div style=\"font-size:11px;color:#84907f;margin-bottom:8px;\">VNSTORMGIS // EMAIL NOTIFICATION</div>"
                + "<h1 style=\"margin:0 0 12px;font-size:20px;color:"
                + accent
                + ";\">"
                + EmailDispatchService.escapeHtml(heading)
                + "</h1>"
                + "<p style=\"margin:0 0 20px;font-size:13px;line-height:1.5;color:#dde2e4;\">"
                + EmailDispatchService.escapeHtml(message)
                + "</p>"
                + "<a href=\""
                + webUrl
                + "\" style=\"display:inline-block;padding:10px 18px;background:#e2ffcc;color:#161b13;text-decoration:none;font-weight:700;font-size:12px;\">QUAY VỀ BẢN ĐỒ VNSTORMGIS</a>"
                + "</div></body></html>";
    }
}
