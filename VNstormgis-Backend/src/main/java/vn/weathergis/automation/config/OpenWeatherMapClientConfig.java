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
 * Cấu hình HTTP client chuyên dụng cho OpenWeatherMap (thay thế blueprint WebClient tại README Mục
 * 5.2 — xem quyết định đối chiếu #1 trong docs/week2/Task W2-03.md, Mục 2). Connect timeout được
 * đặt trên JDK HttpClient vì Spring Framework 6.1 chưa có setter Duration tương ứng.
 */
@Configuration
public class OpenWeatherMapClientConfig {

    @Bean
    public RestClient openWeatherMapRestClient(
            @Value("${weathergis.external.owm.base-url:https://api.openweathermap.org}")
                    String baseUrl,
            @Value("${weathergis.external.owm.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${weathergis.external.owm.read-timeout-ms:5000}") int readTimeoutMs) {

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
