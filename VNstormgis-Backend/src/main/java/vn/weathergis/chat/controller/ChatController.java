package vn.weathergis.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.weathergis.chat.dto.ChatMessageView;
import vn.weathergis.chat.dto.CreateSessionResponse;
import vn.weathergis.chat.dto.SendMessageRequest;
import vn.weathergis.chat.dto.SendMessageResponse;
import vn.weathergis.chat.service.ChatMessageService;
import vn.weathergis.chat.service.ChatRequestParser;
import vn.weathergis.chat.service.ChatSessionService;

/**
 * Anonymous grounded chat API (W5-03, contract W5-01 v1.0 §2 — D1/D9/D10). Three canonical session
 * endpoints only; the bearer token travels in the X-Session-Token header, never in URLs or bodies,
 * and is returned exactly once at creation.
 *
 * <p>INTERIM (until W5-04): the reply port is not wired to Gemini yet, so POST messages answers 503
 * CHT_503_AI_UNAVAILABLE for every accepted question — no reply content is generated or stored. The
 * 200 path is exercised only with a stubbed port in tests. Rate limiting is a documented no-op seam
 * until W5-05.
 */
@Tag(
        name = "7. Hội Thoại AI Thụ Động (Chat)",
        description =
                "Chat ẩn danh có căn cứ dữ liệu PostGIS: tạo phiên (token trả một lần), gửi câu hỏi"
                        + " và đọc lịch sử phân trang cursor. Ba endpoint chính tắc theo hợp đồng"
                        + " W5-01 v1.0 (D1); không đường tắt /chat/message, không token trong URL."
                        + " Lỗi RFC 7807 với mã CHT_*; thành công bọc ApiResponse. Thời gian payload"
                        + " chat là UTC ISO 8601 (...Z); timestamp của envelope giữ offset máy chủ."
                        + " TỚI W5-04: POST messages trả 503 CHT_503_AI_UNAVAILABLE (chưa nối Gemini);"
                        + " rate limit là seam no-op tới W5-05.")
@SecurityScheme(
        name = "X-Session-Token",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-Session-Token",
        description =
                "Bearer secret phạm vi một phiên, server cấp và trả ĐÚNG MỘT LẦN khi tạo phiên."
                        + " Không nhận token trong URL/body; mất token thì tạo phiên mới, không khôi"
                        + " phục. Thiếu/rỗng header → 401; token sai/phiên không tồn tại/hết hạn →"
                        + " 404 đồng nhất (chống dò phiên).")
@RestController
@RequestMapping("/api/v1/chat/sessions")
public class ChatController {

    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;
    private final ChatRequestParser requestParser;

    public ChatController(
            ChatSessionService sessionService,
            ChatMessageService messageService,
            ChatRequestParser requestParser) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.requestParser = requestParser;
    }

    @Operation(
            summary = "Tạo phiên hội thoại ẩn danh (trả token một lần)",
            description =
                    "Public, không nhận body/token client tự sinh (body gửi kèm bị bỏ qua). Server"
                            + " sinh token SecureRandom 256-bit base64url không padding (43 ký tự),"
                            + " trả DUY NHẤT trong response 201 này và chỉ lưu verifier SHA-256."
                            + " title mặc định 'Hội thoại mới'. created_at UTC (...Z). Không endpoint"
                            + " nào khác trả lại token; không log token. Ví dụ data [FIXTURE]:"
                            + " {\"session_id\":\"7c8e9f10-1234-5678-abcd-ef0123456789\","
                            + "\"session_token\":\"<43-ký-tự-base64url>\",\"title\":\"Hội thoại mới\","
                            + "\"created_at\":\"2026-11-17T09:00:00Z\"}.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Tạo phiên thành công; session_token chỉ xuất hiện tại đây",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = CreateSessionEnvelope.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi lưu trữ (RFC 7807 COM_500_INTERNAL_ERROR)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<CreateSessionResponse>>
            createSession() {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        vn.weathergis.common.api.ApiResponse.ok(
                                sessionService.createSession(),
                                "Khởi tạo phiên hội thoại AI thành công"));
    }

    @Operation(
            summary = "Gửi câu hỏi trong phiên (X-Session-Token)",
            description =
                    "Thứ tự xử lý: validation body → xác thực session/token → rate limit (seam"
                            + " no-op tới W5-05) → intent/context (W5-02) → port sinh trả lời (W5-04)"
                            + " → ghi atomic cặp user+assistant → response. Request 400/401/404/429/503"
                            + " KHÔNG ghi lịch sử. Body ≤ 8 KiB (8192 byte, kiểm cả Content-Length lẫn"
                            + " chunked); message bắt buộc 1–500 code point sau strip() Unicode, từ"
                            + " chối C0 trừ \\t\\n; referenced_alert_id tùy chọn phải UUID hợp lệ;"
                            + " spatial_filter tùy chọn {\"bbox\":[minLng,minLat,maxLng,maxLat]} WGS84"
                            + " [lng,lat], 4 số hữu hạn, min<max mỗi trục — không nhận center+zoom,"
                            + " không âm thầm hoán trục; bbox ngoài vùng dữ liệu → no-data trung thực."
                            + " Trường lạ bị bỏ qua. Nội dung lưu sau strip. referenced_entities trả"
                            + " nguyên trạng từ factual context (không suy diễn từ câu hỏi). message_id"
                            + " là id bản ghi assistant đã lưu; created_at UTC (...Z)."
                            + " TỚI W5-04: mọi câu hỏi hợp lệ trả 503 CHT_503_AI_UNAVAILABLE (chưa nối"
                            + " Gemini, không bịa nội dung). Bảng lỗi: 400 CHT_400_VALIDATION /"
                            + " CHT_400_INVALID_ALERT_ID / CHT_400_INVALID_SPATIAL_FILTER; 401"
                            + " CHT_401_SESSION_TOKEN_MISSING; 404 CHT_404_SESSION_NOT_FOUND (đồng nhất)"
                            + " / CHT_404_ALERT_NOT_FOUND; 429 CHT_429_RATE_LIMITED (W5-05, kèm"
                            + " Retry-After giây); 503 CHT_503_AI_UNAVAILABLE /"
                            + " CHT_503_RATE_LIMITER_UNAVAILABLE (W5-05); 500 COM_500_INTERNAL_ERROR."
                            + " Ví dụ lỗi 400 [FIXTURE]: {\"type\":\"https://weathergis.vn/errors/"
                            + "validation-error\",\"title\":\"Dữ liệu câu hỏi không hợp lệ\",\"status\""
                            + ":400,\"detail\":\"Câu hỏi vượt quá 500 ký tự\",\"instance\":\"/api/v1/"
                            + "chat/sessions/7c8e9f10-.../messages\",\"errorCode\":\"CHT_400_VALIDATION\","
                            + "\"invalidParams\":[{\"field\":\"message\",\"message\":\"Câu hỏi vượt quá"
                            + " 500 ký tự\"}]}.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description =
                        "Câu hỏi được chấp nhận; cặp tin nhắn đã ghi (is_fallback đánh dấu trả lời"
                                + " tự động). Chỉ khả dụng khi port trả lời W5-04 hoạt động.",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = SendMessageEnvelope.class))),
        @ApiResponse(
                responseCode = "400",
                description =
                        "Validation (message/alert id/bbox/limit/body 8 KiB/JSON hỏng/UUID path sai)"
                                + " — RFC 7807 CHT_400_* kèm invalidParams",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Thiếu/rỗng X-Session-Token (CHT_401_SESSION_TOKEN_MISSING)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description =
                        "Phiên không tồn tại/hết hạn/token sai/path lệch (CHT_404_SESSION_NOT_FOUND"
                                + " đồng nhất) hoặc alert không tồn tại (CHT_404_ALERT_NOT_FOUND)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "429",
                description =
                        "Vượt hạn mức (CHT_429_RATE_LIMITED) — hành vi + Retry-After thuộc W5-05",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "503",
                description =
                        "AI chưa sẵn sàng (CHT_503_AI_UNAVAILABLE, interim tới W5-04) hoặc rate"
                                + " limiter outage (CHT_503_RATE_LIMITER_UNAVAILABLE, W5-05)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi lưu trữ/không phân loại (COM_500_INTERNAL_ERROR)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @SecurityRequirement(name = "X-Session-Token")
    @PostMapping(value = "/{sessionId}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<SendMessageResponse>> sendMessage(
            @Parameter(
                            description = "UUID phiên (phải khớp phiên resolve từ token)",
                            required = true)
                    @PathVariable("sessionId")
                    UUID sessionId,
            @Parameter(description = "Token phiên trả một lần khi tạo", required = true)
                    @RequestHeader(value = "X-Session-Token", required = false)
                    String sessionToken,
            HttpServletRequest request)
            throws IOException {
        SendMessageRequest raw = requestParser.parse(request);
        SendMessageResponse data =
                messageService.sendMessage(
                        sessionId,
                        sessionToken,
                        raw,
                        request.getRemoteAddr(),
                        request.getHeader("X-Forwarded-For"));
        return ResponseEntity.ok(vn.weathergis.common.api.ApiResponse.ok(data));
    }

    @Operation(
            summary = "Đọc lịch sử phiên (phân trang cursor, X-Session-Token)",
            description =
                    "Thứ tự ổn định created_at ASC, id ASC (tie-break trùng timestamp). limit mặc"
                            + " định 50, tối đa 200, sai → 400 CHT_400_VALIDATION. after = id message"
                            + " cuối đã nhận (UUID, cursor forward keyset); after hợp lệ nhưng không"
                            + " thuộc phiên → trang rỗng 200 (không lộ tồn tại message phiên khác)."
                            + " Phiên chưa có message → 200 mảng rỗng. Mỗi message: id, sender_type"
                            + " (user|assistant), content, referenced_alert_id (null nếu alert đã xóa"
                            + " — FK SET NULL), is_fallback (user luôn false), created_at UTC (...Z)."
                            + " KHÔNG trả session_token hay tokens_used. Reload: client giữ"
                            + " {session_id, session_token} (localStorage, W5-06) và gọi lại endpoint"
                            + " này; server không giữ trạng thái RAM.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Mảng message theo thứ tự ổn định (có thể rỗng)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = HistoryEnvelope.class))),
        @ApiResponse(
                responseCode = "400",
                description = "limit/after sai hoặc UUID path sai (CHT_400_VALIDATION)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Thiếu/rỗng X-Session-Token (CHT_401_SESSION_TOKEN_MISSING)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Phiên không tồn tại/hết hạn/token sai (CHT_404_SESSION_NOT_FOUND)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi lưu trữ (COM_500_INTERNAL_ERROR)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @SecurityRequirement(name = "X-Session-Token")
    @GetMapping(value = "/{sessionId}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<vn.weathergis.common.api.ApiResponse<List<ChatMessageView>>> getHistory(
            @Parameter(
                            description = "UUID phiên (phải khớp phiên resolve từ token)",
                            required = true)
                    @PathVariable("sessionId")
                    UUID sessionId,
            @Parameter(description = "Token phiên trả một lần khi tạo", required = true)
                    @RequestHeader(value = "X-Session-Token", required = false)
                    String sessionToken,
            @Parameter(description = "Số message tối đa (1–200, mặc định 50)")
                    @RequestParam(name = "limit", defaultValue = "50")
                    int limit,
            @Parameter(description = "Cursor: id message cuối đã nhận (UUID, tùy chọn)")
                    @RequestParam(name = "after", required = false)
                    UUID after) {
        return ResponseEntity.ok(
                vn.weathergis.common.api.ApiResponse.ok(
                        messageService.getHistory(sessionId, sessionToken, limit, after)));
    }

    @Schema(description = "Envelope ApiResponse cho tạo phiên")
    private record CreateSessionEnvelope(
            @Schema(example = "true") boolean success,
            @Schema(example = "Khởi tạo phiên hội thoại AI thành công") String message,
            CreateSessionResponse data,
            @Schema(example = "2026-11-17T16:00:00+07:00") String timestamp) {}

    @Schema(description = "Envelope ApiResponse cho gửi câu hỏi")
    private record SendMessageEnvelope(
            @Schema(example = "true") boolean success,
            @Schema(example = "Thành công") String message,
            SendMessageResponse data,
            @Schema(example = "2026-11-17T16:00:05+07:00") String timestamp) {}

    @Schema(description = "Envelope ApiResponse cho lịch sử")
    private record HistoryEnvelope(
            @Schema(example = "true") boolean success,
            @Schema(example = "Thành công") String message,
            List<ChatMessageView> data,
            @Schema(example = "2026-11-17T16:00:00+07:00") String timestamp) {}
}
