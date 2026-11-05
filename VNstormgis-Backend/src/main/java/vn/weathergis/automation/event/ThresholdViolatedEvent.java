package vn.weathergis.automation.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Verified facts for one station in one evaluation cycle. Delivery is synchronous and in-process;
 * consumers must persist all violation keys before claiming durable idempotency.
 */
public record ThresholdViolatedEvent(
        UUID locationId,
        String locationCode,
        String locationName,
        double longitude,
        double latitude,
        UUID evaluationCycleId,
        Instant evaluatedAt,
        List<Violation> violations) {

    public ThresholdViolatedEvent {
        violations = List.copyOf(violations);
    }

    /**
     * sourceTimestamp/evaluationWindow are observation time for weather, but database update time
     * for a storm centre. Instant normalizes both to UTC; evaluatedAt is never used as source time.
     */
    public record Violation(
            Integer thresholdId,
            String parameter,
            String operator,
            double thresholdValue,
            String severityLevel,
            double measuredValue,
            String unit,
            Instant sourceTimestamp,
            String stormId,
            String stormName,
            Double distanceKm,
            Instant evaluationWindow,
            String violationKey) {}
}
