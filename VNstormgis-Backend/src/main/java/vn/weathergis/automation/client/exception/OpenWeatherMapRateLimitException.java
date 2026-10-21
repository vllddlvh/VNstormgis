package vn.weathergis.automation.client.exception;

/**
 * Ngoại lệ ném ra khi OpenWeatherMap trả HTTP 429 Too Many Requests. Không retry — tầng
 * WeatherSyncService hủy an toàn phần còn lại của chu kỳ.
 */
public class OpenWeatherMapRateLimitException extends RuntimeException {

    public OpenWeatherMapRateLimitException(String message) {
        super(message);
    }
}
