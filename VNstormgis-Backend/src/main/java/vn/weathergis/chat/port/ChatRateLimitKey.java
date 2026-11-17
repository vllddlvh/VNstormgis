package vn.weathergis.chat.port;

/**
 * Rate-limit identity (D6): primary key is the session token verifier; address fields let W5-05
 * derive the trusted-proxy IP key. W5-03's no-op limiter ignores them.
 */
public record ChatRateLimitKey(String tokenHash, String remoteAddress, String forwardedFor) {}
