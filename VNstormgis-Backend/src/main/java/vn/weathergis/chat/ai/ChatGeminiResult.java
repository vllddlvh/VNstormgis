package vn.weathergis.chat.ai;

import java.time.Duration;

/**
 * Typed chat-client outcome (W5-04 §3.1). Value names mirror the W4 {@code
 * GeminiGenerationResult.Outcome} for consistent operations, with {@code ANSWER} in place of {@code
 * DRAFT}; the chat module deliberately does not couple to the alert result record. An {@code
 * ANSWER} still requires independent guardrail verification before anything is published.
 */
public record ChatGeminiResult(
        Outcome outcome,
        String text,
        String model,
        int tokensUsed,
        Duration duration,
        Integer httpStatus) {

    public enum Outcome {
        ANSWER,
        DISABLED,
        TIMEOUT,
        NETWORK_ERROR,
        UPSTREAM_AUTH_ERROR,
        RATE_LIMITED,
        UPSTREAM_HTTP_ERROR,
        BLOCKED,
        INVALID_RESPONSE
    }
}
