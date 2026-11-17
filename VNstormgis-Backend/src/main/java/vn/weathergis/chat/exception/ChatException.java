package vn.weathergis.chat.exception;

import vn.weathergis.common.exception.ErrorCode;

/**
 * Base class for chat-domain failures mapped to RFC 7807 by GlobalExceptionHandler. Messages are
 * friendly Vietnamese and never contain SQL, stack traces, tokens, prompts or other sessions' data.
 */
public class ChatException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String typeSlug;

    public ChatException(ErrorCode errorCode, String typeSlug, String message) {
        super(message);
        this.errorCode = errorCode;
        this.typeSlug = typeSlug;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getTypeSlug() {
        return typeSlug;
    }
}
