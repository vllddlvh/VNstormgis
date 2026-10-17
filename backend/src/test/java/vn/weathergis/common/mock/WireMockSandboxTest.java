package vn.weathergis.common.mock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Khung kiểm thử môi trường giả lập mạng cách ly (WireMock Sandbox). Chuẩn bị môi trường thử nghiệm
 * cho Ingestion Client OpenWeatherMap và GDACS Storm Feed ở Tuần 2. Kiểm định việc giả lập các kịch
 * bản mạng biên: HTTP 200 OK, HTTP 429 Quota Exceeded và HTTP 504 Timeout.
 */
@DisplayName("WireMock HTTP Sandbox Environment Tests")
class WireMockSandboxTest {

    private static WireMockServer wireMockServer;
    private HttpClient httpClient;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Test
    @DisplayName("TC-WM-01: Giả lập Stub 200 OK phản hồi dữ liệu khí tượng OpenWeatherMap JSON")
    void testStubOpenWeatherMapSuccess() throws IOException, InterruptedException {
        String mockResponseBody =
                """
                {
                  "coord": {"lon": 105.8542, "lat": 21.0285},
                  "weather": [{"id": 501, "main": "Rain", "description": "moderate rain", "icon": "10d"}],
                  "main": {"temp": 28.5, "feels_like": 32.0, "pressure": 1008, "humidity": 82},
                  "wind": {"speed": 4.5, "deg": 45},
                  "rain": {"1h": 8.5},
                  "name": "Hanoi"
                }
                """;

        wireMockServer.stubFor(
                get(urlPathEqualTo("/data/2.5/weather"))
                        .withQueryParam("lat", equalTo("21.0285"))
                        .withQueryParam("lon", equalTo("105.8542"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(mockResponseBody)));

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        wireMockServer.baseUrl()
                                                + "/data/2.5/weather?lat=21.0285&lon=105.8542"))
                        .GET()
                        .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(response.body()).contains("\"name\": \"Hanoi\"");
        assertThat(response.body()).contains("\"temp\": 28.5");
    }

    @Test
    @DisplayName("TC-WM-02: Giả lập Stub 429 Too Many Requests (Vượt ngưỡng Rate Limit OWM)")
    void testStubRateLimitExceeded() throws IOException, InterruptedException {
        wireMockServer.stubFor(
                get(urlPathEqualTo("/data/2.5/weather"))
                        .willReturn(
                                aResponse()
                                        .withStatus(429)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"cod\": 429, \"message\": \"Your account is temporary blocked due to exceeding of requests limitation.\"}")));

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(wireMockServer.baseUrl() + "/data/2.5/weather"))
                        .GET()
                        .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.body()).contains("exceeding of requests limitation");
    }

    @Test
    @DisplayName("TC-WM-03: Giả lập lỗi máy chủ 500 Internal Server Error và mạng trễ")
    void testStubServerFailure() throws IOException, InterruptedException {
        wireMockServer.stubFor(
                get(urlPathEqualTo("/api/v1/gdacs/storms"))
                        .willReturn(
                                aResponse()
                                        .withStatus(500)
                                        .withFixedDelay(200) // Trễ 200ms
                                        .withBody(
                                                "{\"error\": \"Upstream GDACS Gateway Timeout\"}")));

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(wireMockServer.baseUrl() + "/api/v1/gdacs/storms"))
                        .GET()
                        .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("Upstream GDACS Gateway Timeout");
    }
}
