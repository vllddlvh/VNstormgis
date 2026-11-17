package vn.weathergis.chat.exception;

import vn.weathergis.common.exception.ErrorCode;

/** Fail-closed response while the shared quota store is unavailable. */
public class ChatRateLimiterUnavailableException extends ChatException {
    public ChatRateLimiterUnavailableException() {
        super(
                ErrorCode.CHAT_RATE_LIMITER_UNAVAILABLE,
                "rate-limiter-unavailable",
                "Hệ thống hạn mức tạm thời gián đoạn. Vui lòng thử lại sau ít phút.");
    }
}
