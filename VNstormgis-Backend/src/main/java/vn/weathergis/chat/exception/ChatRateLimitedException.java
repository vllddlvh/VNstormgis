package vn.weathergis.chat.exception;

import vn.weathergis.common.exception.ErrorCode;

/** A request rejected before intent classification, AI, or history writes. */
public class ChatRateLimitedException extends ChatException {
    private final String scope;
    private final long retryAfterSeconds;

    public ChatRateLimitedException(String scope, long retryAfterSeconds) {
        super(ErrorCode.CHAT_RATE_LIMITED, "rate-limited", detail(retryAfterSeconds));
        if (!"session".equals(scope) && !"ip".equals(scope)) {
            throw new IllegalArgumentException("Invalid rate limit scope");
        }
        this.scope = scope;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    private static String detail(long seconds) {
        if (seconds < 1) {
            throw new IllegalArgumentException("Retry-After must be positive");
        }
        return "Bạn đang gửi câu hỏi quá nhanh. Xin vui lòng chờ "
                + seconds
                + " giây trước khi tiếp tục.";
    }

    public String getScope() {
        return scope;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
