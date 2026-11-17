package vn.weathergis.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Session creation payload (contract §2.2). {@code session_token} is returned exactly once here; no
 * other endpoint ever returns it. {@code created_at} is UTC ISO 8601 ({@code ...Z}).
 */
@Schema(description = "Thông tin phiên hội thoại vừa khởi tạo (session_token chỉ trả một lần)")
public record CreateSessionResponse(
        @Schema(
                        description = "UUID phiên hội thoại",
                        example = "7c8e9f10-1234-5678-abcd-ef0123456789",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("session_id")
                UUID sessionId,
        @Schema(
                        description = "Bearer token 43 ký tự base64url trả đúng một lần duy nhất",
                        example = "dGVzdC1zZXNzaW9uLXRva2VuLTQzLWNoYXJzLWJhc2U2NHVybA",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("session_token")
                String sessionToken,
        @Schema(
                        description = "Tiêu đề mặc định của phiên",
                        example = "Hội thoại mới",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("title")
                String title,
        @Schema(
                        description = "Thời điểm tạo phiên theo UTC ISO 8601",
                        example = "2026-11-17T09:00:00Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("created_at")
                Instant createdAt) {}
