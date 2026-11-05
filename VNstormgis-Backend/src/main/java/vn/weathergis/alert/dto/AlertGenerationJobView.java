package vn.weathergis.alert.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.weathergis.alert.domain.AlertJobStatus;

/** Immutable view of an alert generation job for internal orchestration and verification. */
public record AlertGenerationJobView(
        UUID jobId,
        AlertJobStatus status,
        UUID locationId,
        UUID evaluationCycleId,
        Integer representativeThresholdId,
        String stormId,
        String severityLevel,
        String triggerReason,
        Instant triggeredAt,
        AlertFactualSnapshot factualSnapshot,
        List<String> violationKeys,
        String verifiedTitle,
        String verifiedAiExplanation,
        Boolean isFallback,
        int attemptCount,
        Instant nextAttemptAt,
        String lastError,
        UUID alertId) {

    public AlertGenerationJobView {
        violationKeys = violationKeys == null ? List.of() : List.copyOf(violationKeys);
    }
}
