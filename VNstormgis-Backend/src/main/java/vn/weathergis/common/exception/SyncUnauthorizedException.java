package vn.weathergis.common.exception;

/**
 * Ngoại lệ ném ra khi header X-Internal-Secret thiếu hoặc sai (HTTP 401 SYN_401_INVALID_SECRET).
 */
public class SyncUnauthorizedException extends RuntimeException {

    private final ErrorCode errorCode;

    public SyncUnauthorizedException() {
        super(ErrorCode.SYNC_UNAUTHORIZED.getDefaultMessage());
        this.errorCode = ErrorCode.SYNC_UNAUTHORIZED;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
