package vn.weathergis.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import vn.weathergis.chat.context.FactualContext;

/**
 * Accepted-question payload (contract §2.3). {@code message_id} is the stored assistant row; {@code
 * referenced_entities} comes verbatim from the W5-02 factual context (never inferred from the
 * question text). {@code created_at} is UTC ISO 8601 ({@code ...Z}).
 */
@Schema(description = "Phản hồi câu trả lời AI hoặc template tất định có căn cứ dữ liệu")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SendMessageResponse(
        @Schema(
                        description = "UUID bản ghi tin nhắn assistant đã lưu",
                        example = "9e0f1a22-3456-789a-cdef-0123456789ab",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("message_id")
                UUID messageId,
        @Schema(
                        description = "Nội dung trả lời tiếng Việt đã kiểm chứng hoặc mẫu tất định",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("reply")
                String reply,
        @Schema(
                        description =
                                "true nếu dùng mẫu tất định từ dữ liệu hệ thống, false nếu từ Gemini đã qua guardrail",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("is_fallback")
                boolean isFallback,
        @Schema(
                        description =
                                "Các thực thể có mặt trong factual context (trạm, bão, cảnh báo)",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("referenced_entities")
                FactualContext.ReferencedEntities referencedEntities,
        @Schema(
                        description = "Thời điểm ghi nhận phản hồi theo UTC ISO 8601",
                        example = "2026-11-17T09:00:05Z",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("created_at")
                Instant createdAt) {}
