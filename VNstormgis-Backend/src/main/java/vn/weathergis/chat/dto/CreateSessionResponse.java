package vn.weathergis.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Session creation payload (contract §2.2). {@code session_token} is returned exactly once here; no
 * other endpoint ever returns it. {@code created_at} is UTC ISO 8601 ({@code ...Z}).
 */
public record CreateSessionResponse(
        @JsonProperty("session_id") UUID sessionId,
        @JsonProperty("session_token") String sessionToken,
        @JsonProperty("title") String title,
        @JsonProperty("created_at") Instant createdAt) {}
