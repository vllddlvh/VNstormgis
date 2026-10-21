package vn.weathergis.common.exception;

/** Ngoại lệ ném ra khi tiến trình đồng bộ đang chạy (HTTP 409 SYN_409_SYNC_IN_PROGRESS). */
public class SyncInProgressException extends RuntimeException {

    private final ErrorCode errorCode;

    public SyncInProgressException() {
        super(ErrorCode.SYNC_IN_PROGRESS.getDefaultMessage());
        this.errorCode = ErrorCode.SYNC_IN_PROGRESS;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
