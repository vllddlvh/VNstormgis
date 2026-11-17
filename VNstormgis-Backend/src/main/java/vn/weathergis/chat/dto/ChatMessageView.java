package vn.weathergis.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * History item (contract §2.4). {@code referenced_alert_id} is null when the alert was deleted (FK
 * SET NULL); {@code is_fallback} is meaningful for assistant rows (user rows are always false).
 * Never exposes {@code session_token} or {@code tokens_used}. {@code created_at} is UTC.
 */
@Schema(description = "Bản ghi tin nhắn trong lịch sử hội thoại")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ChatMessageView(
        @Schema(description = "UUID tin nhắn", requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("id")
                UUID id,
        @Schema(
                        description = "Loại người gửi (user | assistant)",
                        example = "assistant",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("sender_type")
                String senderType,
        @Schema(
                        description = "Nội dung tin nhắn thuần văn bản",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("content")
                String content,
        @Schema(
                        description =
                                "UUID cảnh báo được hỏi (null nếu không gắn hoặc cảnh báo đã xóa)",
                        nullable = true,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("referenced_alert_id")
                UUID referencedAlertId,
        @Schema(
                        description = "true nếu là phản hồi mẫu tất định từ dữ liệu hệ thống",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("is_fallback")
                boolean isFallback,
        @Schema(
                        description = "Thời điểm tạo tin nhắn theo UTC ISO 8601",
                        example = "2026-11-17T09:00:05Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("created_at")
                Instant createdAt) {}
