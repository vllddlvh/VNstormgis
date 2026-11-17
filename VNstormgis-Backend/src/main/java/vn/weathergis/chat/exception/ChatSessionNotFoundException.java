package vn.weathergis.chat.exception;

import vn.weathergis.common.exception.ErrorCode;

/**
 * Uniform 404 for every session-resolution failure — wrong token, unknown session, expired, revoked
 * or path mismatch (contract §2.1, R01). The identical message prevents probing session existence.
 */
public class ChatSessionNotFoundException extends ChatException {

    public ChatSessionNotFoundException() {
        super(
                ErrorCode.CHAT_SESSION_NOT_FOUND,
                "not-found",
                "Phiên hội thoại không tồn tại hoặc đã hết hạn");
    }
}
