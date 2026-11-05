package vn.weathergis.alert.dto;

import java.util.List;
import java.util.UUID;

/**
 * Internal verified-content submission produced by W4-05 after guardrail validation or fallback
 * template rendering.
 *
 * <p>{@code isFallback} must be explicitly supplied ({@code true} for deterministic fallback,
 * {@code false} for guardrail-verified AI output). {@code violationKeys} and {@code
 * factualSnapshot} are checked against the persisted inbox job before transitioning {@code PENDING
 * -> READY}.
 */
public record VerifiedAlertSubmission(
        UUID jobId,
        String title,
        String aiExplanation,
        Boolean isFallback,
        List<String> violationKeys,
        AlertFactualSnapshot factualSnapshot) {

    public VerifiedAlertSubmission {
        violationKeys = violationKeys == null ? null : List.copyOf(violationKeys);
    }
}
