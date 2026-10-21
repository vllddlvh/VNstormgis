package vn.weathergis.automation.client.exception;

/**
 * Ngoại lệ phân loại lỗi gọi OpenWeatherMap: lỗi mạng tạm thời, timeout và HTTP 5xx là retryable;
 * HTTP 4xx nghiệp vụ (key sai 401, không tìm thấy 404...) là không retry.
 */
public class OpenWeatherMapClientException extends RuntimeException {

    private final boolean retryable;

    public OpenWeatherMapClientException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public OpenWeatherMapClientException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
