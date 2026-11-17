package vn.weathergis.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import vn.weathergis.chat.context.FactualContext;

/**
 * Accepted-question payload (contract §2.3). {@code message_id} is the stored assistant row; {@code
 * referenced_entities} comes verbatim from the W5-02 factual context (never inferred from the
 * question text). {@code created_at} is UTC ISO 8601 ({@code ...Z}).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SendMessageResponse(
        @JsonProperty("message_id") UUID messageId,
        @JsonProperty("reply") String reply,
        @JsonProperty("is_fallback") boolean isFallback,
        @JsonProperty("referenced_entities") FactualContext.ReferencedEntities referencedEntities,
        @JsonProperty("created_at") Instant createdAt) {}
