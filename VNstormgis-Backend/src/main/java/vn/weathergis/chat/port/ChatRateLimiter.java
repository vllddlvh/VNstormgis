package vn.weathergis.chat.port;

/**
 * Rate-limit seam handed to W5-05 (contract §3.2/§6, D6/D7). Positioned after session auth and
 * before context/reply/DB write; quota applies only to POST messages. The key carries the token
 * verifier (never the bare session id) plus address material so W5-05 can install Bucket4j+Redis
 * session/IP buckets without changing orchestration.
 */
public interface ChatRateLimiter {

    void acquire(ChatRateLimitKey key);
}
