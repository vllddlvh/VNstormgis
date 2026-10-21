package vn.weathergis.automation.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import vn.weathergis.automation.client.dto.OpenWeatherMapCurrentWeather;
import vn.weathergis.automation.client.exception.OpenWeatherMapClientException;
import vn.weathergis.automation.client.exception.OpenWeatherMapRateLimitException;

/**
 * Client gọi OpenWeatherMap Current Weather API. Bản thân client KHÔNG biết về trạm hay CSDL — chỉ
 * nhận (lat, lon), trả về payload đã bóc tách + JSON gốc (lưu raw_data) để W2-04 fake dễ dàng.
 */
@Component
public class OpenWeatherMapClient {

    private static final Logger log = LoggerFactory.getLogger(OpenWeatherMapClient.class);
    private static final long[] BACKOFF_MS = {
        500, 1000, 2000
    }; // Exponential backoff README Mục 3.2

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public OpenWeatherMapClient(
            RestClient openWeatherMapRestClient,
            ObjectMapper objectMapper,
            @Value("${weathergis.external.owm.api-key:}") String apiKey) {
        this.restClient = openWeatherMapRestClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    /** Kết quả một lượt gọi: payload đã bóc tách kèm JSON gốc phục vụ cột raw_data JSONB. */
    public record OpenWeatherMapResponse(OpenWeatherMapCurrentWeather data, String rawJson) {}

    /**
     * Lấy thời tiết hiện tại của một tọa độ. Retry tối đa 3 lần (tổng 4 lượt gọi) với backoff
     * 500/1000/2000ms cho lỗi mạng tạm thời và HTTP 5xx; HTTP 429 ném exception ngay (hủy chu kỳ).
     */
    public OpenWeatherMapResponse fetchCurrentWeather(double lat, double lon) {
        OpenWeatherMapClientException lastError = null;
        for (int attempt = 0; attempt <= BACKOFF_MS.length; attempt++) {
            if (attempt > 0) {
                log.debug(
                        "OWM retry lần {} cho ({}, {}) sau {}ms",
                        attempt,
                        lat,
                        lon,
                        BACKOFF_MS[attempt - 1]);
                sleepQuietly(BACKOFF_MS[attempt - 1]);
            }
            try {
                return fetchOnce(lat, lon);
            } catch (OpenWeatherMapRateLimitException e) {
                throw e; // 429: KHÔNG retry — dừng an toàn ở tầng WeatherSyncService
            } catch (OpenWeatherMapClientException e) {
                lastError = e;
                if (!e.isRetryable()) {
                    throw e; // 4xx nghiệp vụ (401 key sai, 404...) — retry vô nghĩa
                }
            } catch (ResourceAccessException e) {
                lastError =
                        new OpenWeatherMapClientException(
                                "Lỗi mạng/timeout khi gọi OWM cho (" + lat + ", " + lon + ")",
                                true,
                                e);
            }
        }
        throw lastError; // Cạn 3 lượt retry
    }

    private OpenWeatherMapResponse fetchOnce(double lat, double lon) {
        String rawJson =
                restClient
                        .get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .path("/data/2.5/weather")
                                                .queryParam("lat", lat)
                                                .queryParam("lon", lon)
                                                .queryParam("units", "metric")
                                                .queryParam("appid", apiKey)
                                                .build())
                        .retrieve()
                        .onStatus(
                                status -> status.value() == 429,
                                (req, res) -> {
                                    throw new OpenWeatherMapRateLimitException(
                                            "OWM trả 429 Too Many Requests — vượt ngưỡng rate limit");
                                })
                        .onStatus(
                                HttpStatusCode::is5xxServerError,
                                (req, res) -> {
                                    throw new OpenWeatherMapClientException(
                                            "OWM trả lỗi máy chủ " + res.getStatusCode().value(),
                                            true);
                                })
                        .onStatus(
                                HttpStatusCode::is4xxClientError,
                                (req, res) -> {
                                    throw new OpenWeatherMapClientException(
                                            "OWM từ chối yêu cầu " + res.getStatusCode().value(),
                                            false);
                                })
                        .body(String.class);

        try {
            return new OpenWeatherMapResponse(
                    objectMapper.readValue(rawJson, OpenWeatherMapCurrentWeather.class), rawJson);
        } catch (IOException e) {
            throw new OpenWeatherMapClientException("Payload OWM không parse được JSON", true, e);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis); // An toàn trên Virtual Threads (nhả carrier thread)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenWeatherMapClientException(
                    "Tiến trình bị gián đoạn khi backoff", false, e);
        }
    }
}
