package vn.weathergis.chat.context;

import java.util.UUID;

/**
 * Retrieval input after W5-03 validation/session/rate limit. {@code bbox} is already validated
 * upstream (D1/DL-18): finite WGS84 {@code [minLng, minLat, maxLng, maxLat]}, min &lt; max per
 * axis.
 */
public record ChatContextRequest(String message, UUID referencedAlertId, Bbox bbox) {

    public record Bbox(double minLng, double minLat, double maxLng, double maxLat) {}
}
