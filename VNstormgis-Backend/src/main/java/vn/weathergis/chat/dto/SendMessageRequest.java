package vn.weathergis.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Raw question payload before validation. {@code referenced_alert_id} stays a String so malformed
 * UUIDs produce CHT_400_INVALID_ALERT_ID instead of a generic parse error; unknown fields are
 * ignored by Jackson (documented in OpenAPI).
 */
public record SendMessageRequest(
        @JsonProperty("message") String message,
        @JsonProperty("referenced_alert_id") String referencedAlertId,
        @JsonProperty("spatial_filter") JsonNode spatialFilter) {}
