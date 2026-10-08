package vn.weathergis.common.exception;

/** Ngoại lệ ném ra khi có lỗi tính toán tọa độ hoặc không gian địa lý. */
public class SpatialOperationException extends RuntimeException {

    private final ErrorCode errorCode;

    public SpatialOperationException(String message) {
        super(message);
        this.errorCode = ErrorCode.SPATIAL_QUERY_FAILED;
    }

    public SpatialOperationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.SPATIAL_QUERY_FAILED;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
