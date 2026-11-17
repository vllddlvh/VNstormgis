package vn.weathergis.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * History item (contract §2.4). {@code referenced_alert_id} is null when the alert was deleted (FK
 * SET NULL); {@code is_fallback} is meaningful for assistant rows (user rows are always false).
 * Never exposes {@code session_token} or {@code tokens_used}. {@code created_at} is UTC.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ChatMessageView(
        @JsonProperty("id") UUID id,
        @JsonProperty("sender_type") String senderType,
        @JsonProperty("content") String content,
        @JsonProperty("referenced_alert_id") UUID referencedAlertId,
        @JsonProperty("is_fallback") boolean isFallback,
        @JsonProperty("created_at") Instant createdAt) {}
