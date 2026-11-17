package vn.weathergis.chat.exception;

import vn.weathergis.common.exception.ErrorCode;

/**
 * The reply dependency cannot produce even a deterministic fallback (contract §2.5). Between W5-03
 * and W5-04 the interim reply bean raises this for every question; no history is written and no
 * reply content is invented.
 */
public class ChatAiUnavailableException extends ChatException {

    public ChatAiUnavailableException() {
        super(
                ErrorCode.CHAT_AI_UNAVAILABLE,
                "ai-unavailable",
                "Hệ thống trợ lý AI đang tạm thời gián đoạn kết nối. Vui lòng thử lại sau.");
    }
}
