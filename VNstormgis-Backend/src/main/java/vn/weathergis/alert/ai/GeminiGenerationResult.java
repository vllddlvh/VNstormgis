package vn.weathergis.alert.ai;

import java.time.Duration;
import vn.weathergis.alert.dto.AlertGenerationJobView;

/** Drafts require independent grounding verification before publication. */
public record GeminiGenerationResult(
        AlertGenerationJobView job,
        GeminiAlertDraft draft,
        Outcome outcome,
        String model,
        String promptVersion,
        Duration duration,
        Integer httpStatus) {
    public enum Outcome {
        DRAFT,
        INVALID_FACTUAL_DATA,
        DISABLED,
        CONFIGURATION_ERROR,
        SKIPPED,
        TIMEOUT,
        NETWORK_ERROR,
        UPSTREAM_AUTH_ERROR,
        RATE_LIMITED,
        UPSTREAM_HTTP_ERROR,
        BLOCKED,
        INVALID_RESPONSE
    }

    public record GeminiAlertDraft(String title, String aiExplanation) {}
}
