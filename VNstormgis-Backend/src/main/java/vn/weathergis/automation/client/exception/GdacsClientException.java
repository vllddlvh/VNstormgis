package vn.weathergis.automation.client.exception;

/**
 * Lỗi khi gọi nguồn GDACS. {@code retryable = true} cho lỗi mạng tạm thời và HTTP 5xx (client retry
 * theo backoff), {@code false} cho 4xx nghiệp vụ. GDACS là nguồn công khai không quota — không có
 * nhánh 429 riêng (quyết định #8, Mục 2 Task W2-05).
 */
public class GdacsClientException extends RuntimeException {

    private final boolean retryable;
    private final Integer httpStatus;

    public GdacsClientException(String message, boolean retryable) {
        this(message, retryable, null, null);
    }

    public GdacsClientException(String message, boolean retryable, Throwable cause) {
        this(message, retryable, null, cause);
    }

    public GdacsClientException(
            String message, boolean retryable, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /** Mã trạng thái HTTP gây lỗi — null nếu lỗi mạng (không có response). */
    public Integer getHttpStatus() {
        return httpStatus;
    }
}
