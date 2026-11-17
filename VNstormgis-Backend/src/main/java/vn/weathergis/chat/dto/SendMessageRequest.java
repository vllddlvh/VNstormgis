package vn.weathergis.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Raw question payload before validation. {@code referenced_alert_id} stays a String so malformed
 * UUIDs produce CHT_400_INVALID_ALERT_ID instead of a generic parse error; unknown fields are
 * ignored by Jackson (documented in OpenAPI).
 */
@Schema(description = "Yêu cầu gửi câu hỏi trong phiên hội thoại ẩn danh")
public record SendMessageRequest(
        @Schema(
                        description = "Câu hỏi tiếng Việt (1–500 code point sau strip Unicode)",
                        example = "Khu vực nào đang có mưa to nhất?",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @JsonProperty("message")
                String message,
        @Schema(
                        description = "UUID cảnh báo cần giải thích (tùy chọn)",
                        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                        nullable = true,
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                @JsonProperty("referenced_alert_id")
                String referencedAlertId,
        @Schema(
                        type = "object",
                        description =
                                "Bộ lọc không gian tùy chọn dạng {\"bbox\":[minLng,minLat,maxLng,maxLat]} WGS84",
                        nullable = true,
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                @JsonProperty("spatial_filter")
                JsonNode spatialFilter) {}
