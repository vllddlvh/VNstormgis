package vn.weathergis.chat.port;

/**
 * Rate-limit identity (D6): primary key is the session token verifier; address fields let the
 * limiter derive the trusted-proxy IP key.
 */
public record ChatRateLimitKey(String tokenHash, String remoteAddress, String forwardedFor) {}
