package vn.weathergis.chat.exception;

import vn.weathergis.common.exception.ErrorCode;

/** Input-rule violation (VAL-CHT-001, bbox D1/DL-18); rendered with {@code invalidParams}. */
public class ChatValidationException extends ChatException {

    private final String field;

    public ChatValidationException(ErrorCode errorCode, String field, String message) {
        super(errorCode, "validation-error", message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
