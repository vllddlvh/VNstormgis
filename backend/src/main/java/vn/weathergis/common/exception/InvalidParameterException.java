package vn.weathergis.common.exception;

/** Ngoại lệ ném ra khi tham số query hoặc path parameter không hợp lệ. */
public class InvalidParameterException extends RuntimeException {

    private final ErrorCode errorCode;

    public InvalidParameterException(String message) {
        super(message);
        this.errorCode = ErrorCode.INVALID_PARAMETER;
    }

    public InvalidParameterException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
