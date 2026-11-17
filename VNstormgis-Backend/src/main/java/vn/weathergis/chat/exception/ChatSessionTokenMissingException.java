package vn.weathergis.chat.exception;

import vn.weathergis.common.exception.ErrorCode;

/** Missing or empty X-Session-Token header (contract §2.1). */
public class ChatSessionTokenMissingException extends ChatException {

    public ChatSessionTokenMissingException() {
        super(
                ErrorCode.CHAT_SESSION_TOKEN_MISSING,
                "unauthorized",
                "Thiếu header X-Session-Token của phiên hội thoại");
    }
}
