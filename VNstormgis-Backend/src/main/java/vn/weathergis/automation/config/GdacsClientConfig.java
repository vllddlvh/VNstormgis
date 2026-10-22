package vn.weathergis.automation.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cấu hình HTTP client chuyên dụng cho GDACS — đồng khuôn mẫu OpenWeatherMapClientConfig (W2-03,
 * quyết định #8 Task W2-05). Read timeout 10s (payload quỹ đạo lớn hơn OWM). KHÔNG appid — GDACS là
 * nguồn công khai không yêu cầu khóa (quyết định #10).
 */
@Configuration
public class GdacsClientConfig {

    @Bean
    public RestClient gdacsRestClient(
            @Value("${weathergis.external.gdacs.base-url:https://www.gdacs.org}") String baseUrl,
            @Value("${weathergis.external.gdacs.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${weathergis.external.gdacs.read-timeout-ms:10000}") int readTimeoutMs) {

        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
