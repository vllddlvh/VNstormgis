package vn.weathergis.chat.port;

/**
 * Allow-all limiter seam kept for W5-05, which MUST replace this bean with the approved
 * Bucket4j+Redis policy (10/min/session + 30/min/IP, fail-closed on Redis outage). The seam sits
 * before intent/context and the reply port, so quota will be consumed before any Gemini call.
 */
public class NoOpChatRateLimiter implements ChatRateLimiter {

    @Override
    public void acquire(ChatRateLimitKey key) {
        // intentionally allow-all until W5-05
    }
}
