package vn.weathergis.chat.port;

/**
 * Default W5-03 limiter: allow everything. Safe because no Gemini call exists yet (every question
 * is interim 503, so no token budget is consumed). W5-05 MUST replace this bean with the approved
 * Bucket4j+Redis policy (10/min/session + 30/min/IP, fail-closed on Redis outage).
 */
public class NoOpChatRateLimiter implements ChatRateLimiter {

    @Override
    public void acquire(ChatRateLimitKey key) {
        // intentionally allow-all until W5-05
    }
}
