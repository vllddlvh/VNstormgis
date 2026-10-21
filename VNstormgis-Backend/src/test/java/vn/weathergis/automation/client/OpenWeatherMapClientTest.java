package vn.weathergis.automation.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import vn.weathergis.automation.client.exception.OpenWeatherMapClientException;
import vn.weathergis.automation.client.exception.OpenWeatherMapRateLimitException;

/**
 * Suite fault-injection WireMock cho OpenWeatherMapClient (Task W2-04). Mọi lượt gọi HTTP đi vào
 * WireMockServer cổng động — TUYỆT ĐỐI KHÔNG có request mạng thật. Backoff inject = 0 để suite hoàn
 * tất < 2s (quyết định #2, Mục 2 tài liệu Task W2-04).
 */
@DisplayName("OpenWeatherMapClient — WireMock Fault Injection Tests (W2-04)")
class OpenWeatherMapClientTest {

    private static final String OWM_PATH = "/data/2.5/weather";
    private static final long[] NO_BACKOFF = {0, 0, 0};
    private static final String TEST_API_KEY = "test-key-w2-04";

    /**
     * Payload Hanoi chuẩn — kế thừa nguyên bộ hằng của WireMockSandboxTest (W1-13), thêm dt + gust.
     */
    private static final String HANOI_OK =
            """
            {
              "coord": {"lon": 105.8542, "lat": 21.0285},
              "weather": [{"id": 501, "main": "Rain", "description": "moderate rain", "icon": "10d"}],
              "main": {"temp": 28.5, "feels_like": 32.0, "pressure": 1008, "humidity": 82},
              "wind": {"speed": 4.5, "deg": 45, "gust": 8.2},
              "rain": {"1h": 8.5},
              "dt": 1750000000,
              "name": "Hanoi", "cod": 200
            }
            """;

    private static WireMockServer wireMockServer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OpenWeatherMapClient client;

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
        wireMockServer.resetAll(); // request-journal về 0 — precondition cho mọi verify(n, ...)
        client = newClient(1000, 300, NO_BACKOFF);
    }

    /** Dựng client thủ công trỏ vào WireMock — thay thế bean OpenWeatherMapClientConfig. */
    private OpenWeatherMapClient newClient(
            int connectTimeoutMs, int readTimeoutMs, long[] backoff) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        RestClient restClient =
                RestClient.builder()
                        .baseUrl(wireMockServer.baseUrl())
                        .requestFactory(requestFactory)
                        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .build();
        return new OpenWeatherMapClient(restClient, objectMapper, TEST_API_KEY, backoff);
    }

    @Test
    @DisplayName("TC-03: 200 OK — bóc tách đủ trường + rawJson nguyên vẹn + đúng tham số truy vấn")
    void shouldFetchAndMapSuccessPayload() {
        wireMockServer.stubFor(get(urlPathEqualTo(OWM_PATH)).willReturn(okJson(HANOI_OK)));

        OpenWeatherMapClient.OpenWeatherMapResponse response =
                client.fetchCurrentWeather(21.0285, 105.8542);

        assertThat(response.data().observationEpochSeconds()).isEqualTo(1750000000L);
        assertThat(response.data().main().temp()).isEqualTo(28.5);
        assertThat(response.data().wind().gust()).isEqualTo(8.2);
        assertThat(response.data().rain().oneHour()).isEqualTo(8.5);
        assertThat(response.rawJson()).contains("\"name\": \"Hanoi\"");
        // RÀNG BUỘC TỌA ĐỘ (AGENTS.md mục 1) — kiểm chứng ngay tại tầng HTTP:
        // lat = vĩ độ 21.0285, lon = kinh độ 105.8542, units=metric, appid đúng vị trí
        wireMockServer.verify(
                getRequestedFor(urlPathEqualTo(OWM_PATH))
                        .withQueryParam("lat", equalTo("21.0285"))
                        .withQueryParam("lon", equalTo("105.8542"))
                        .withQueryParam("units", equalTo("metric"))
                        .withQueryParam("appid", equalTo(TEST_API_KEY)));
    }

    @Test
    @DisplayName("TC-05: 500 một lần rồi phục hồi — retry thành công, ĐÚNG 2 lượt gọi")
    void shouldRecoverAfterTransient500() {
        wireMockServer.stubFor(
                get(urlPathEqualTo(OWM_PATH))
                        .inScenario("recover")
                        .whenScenarioStateIs(STARTED)
                        .willReturn(aResponse().withStatus(500).withFixedDelay(50))
                        .willSetStateTo("recovered"));
        wireMockServer.stubFor(
                get(urlPathEqualTo(OWM_PATH))
                        .inScenario("recover")
                        .whenScenarioStateIs("recovered")
                        .willReturn(okJson(HANOI_OK)));

        OpenWeatherMapClient.OpenWeatherMapResponse response =
                client.fetchCurrentWeather(21.0285, 105.8542);

        assertThat(response.data().main().temp()).isEqualTo(28.5);
        wireMockServer.verify(2, getRequestedFor(urlPathEqualTo(OWM_PATH)));
    }

    @Test
    @DisplayName("TC-06: 500 liên tục — cạn 3 retry (TỔNG ĐÚNG 4 lượt), exception retryable")
    void shouldExhaustRetriesOnPersistent500() {
        wireMockServer.stubFor(
                get(urlPathEqualTo(OWM_PATH))
                        .willReturn(aResponse().withStatus(500).withBody("boom")));

        OpenWeatherMapClientException ex =
                catchThrowableOfType(
                        () -> client.fetchCurrentWeather(21.0285, 105.8542),
                        OpenWeatherMapClientException.class);

        assertThat(ex).hasMessageContaining("500");
        assertThat(ex.isRetryable()).isTrue();
        wireMockServer.verify(4, getRequestedFor(urlPathEqualTo(OWM_PATH))); // 1 + 3 retry
    }

    @Test
    @DisplayName("TC-07: 429 — KHÔNG retry, ĐÚNG 1 lượt gọi (dừng an toàn RSK-W2-01)")
    void shouldAbortImmediatelyOn429() {
        wireMockServer.stubFor(
                get(urlPathEqualTo(OWM_PATH))
                        .willReturn(
                                aResponse()
                                        .withStatus(429)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"cod\": 429, \"message\":"
                                                        + " \"Your account is temporary blocked\"}")));

        OpenWeatherMapRateLimitException ex =
                catchThrowableOfType(
                        () -> client.fetchCurrentWeather(21.0285, 105.8542),
                        OpenWeatherMapRateLimitException.class);

        assertThat(ex).isNotNull();
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo(OWM_PATH)));
    }

    @Test
    @DisplayName("TC-08: 401 key sai — non-retryable, ĐÚNG 1 lượt gọi")
    void shouldNotRetryOnInvalidApiKey() {
        wireMockServer.stubFor(
                get(urlPathEqualTo(OWM_PATH))
                        .willReturn(
                                aResponse()
                                        .withStatus(401)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"cod\": 401, \"message\": \"Invalid API key\"}")));

        OpenWeatherMapClientException ex =
                catchThrowableOfType(
                        () -> client.fetchCurrentWeather(21.0285, 105.8542),
                        OpenWeatherMapClientException.class);

        assertThat(ex).hasMessageContaining("401");
        assertThat(ex.isRetryable()).isFalse();
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo(OWM_PATH)));
    }

    @Test
    @DisplayName("TC-09: Read timeout (stub trễ 2000ms > timeout 300ms) — retryable, 4 lượt")
    void shouldWrapReadTimeoutAsRetryable() {
        wireMockServer.stubFor(
                get(urlPathEqualTo(OWM_PATH)).willReturn(okJson(HANOI_OK).withFixedDelay(2000)));

        long startedAt = System.currentTimeMillis();
        OpenWeatherMapClientException ex =
                catchThrowableOfType(
                        () -> client.fetchCurrentWeather(21.0285, 105.8542),
                        OpenWeatherMapClientException.class);
        long elapsedMs = System.currentTimeMillis() - startedAt;

        assertThat(ex).hasMessageContaining("Lỗi mạng/timeout");
        assertThat(ex.isRetryable()).isTrue();
        assertThat(elapsedMs).isLessThan(4000); // 4 x ~300ms
        wireMockServer.verify(4, getRequestedFor(urlPathEqualTo(OWM_PATH)));
    }

    @Test
    @DisplayName("TC-10: 200 nhưng payload sai định dạng JSON — retryable, 4 lượt")
    void shouldRetryOnMalformedPayload() {
        wireMockServer.stubFor(
                get(urlPathEqualTo(OWM_PATH))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{{{not-json")));

        OpenWeatherMapClientException ex =
                catchThrowableOfType(
                        () -> client.fetchCurrentWeather(21.0285, 105.8542),
                        OpenWeatherMapClientException.class);

        assertThat(ex).hasMessageContaining("parse");
        assertThat(ex.isRetryable()).isTrue();
        wireMockServer.verify(4, getRequestedFor(urlPathEqualTo(OWM_PATH)));
    }

    @Test
    @DisplayName("TC-11: 200 không mưa — rain() null truyền xuyên, rawJson giữ nguyên")
    void shouldReturnNullRainWhenFieldAbsent() {
        String noRain =
                """
                {
                  "coord": {"lon": 105.8542, "lat": 21.0285},
                  "weather": [{"id": 800, "main": "Clear", "description": "clear sky"}],
                  "main": {"temp": 30.1, "feels_like": 33.2, "pressure": 1005, "humidity": 70},
                  "wind": {"speed": 2.1, "deg": 180},
                  "dt": 1750000060
                }
                """;
        wireMockServer.stubFor(get(urlPathEqualTo(OWM_PATH)).willReturn(okJson(noRain)));

        OpenWeatherMapClient.OpenWeatherMapResponse response =
                client.fetchCurrentWeather(21.0285, 105.8542);

        assertThat(response.data().rain())
                .isNull(); // service mặc định 0.0 (đã phủ ở W2-03 DTO test)
        assertThat(response.data().wind().gust()).isNull();
        assertThat(response.rawJson()).contains("\"main\": \"Clear\"");
    }
}
