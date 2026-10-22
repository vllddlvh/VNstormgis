package vn.weathergis.automation.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import vn.weathergis.automation.client.exception.GdacsClientException;

/**
 * Suite fault-injection WireMock cho GdacsStormClient (Task W2-05). Mọi lượt gọi HTTP đi vào
 * WireMockServer cổng động — TUYỆT ĐỐI KHÔNG có request mạng thật. Payload chuẩn là fixture GDACS
 * thật chốt ngày 2026-10-22 (quyết định #1, Mục 2 Task W2-05). Backoff inject = 0 để suite < 2s.
 */
@DisplayName("GdacsStormClient — WireMock Fault Injection Tests (W2-05)")
class GdacsStormClientTest {

    private static final String SEARCH_PATH = "/gdacsapi/api/Events/geteventlist/search";
    private static final String TRACK_PATH = "/gdacsapi/api/polygons/getgeometry";
    private static final long[] NO_BACKOFF = {0, 0, 0};

    private static WireMockServer wireMockServer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GdacsStormClient client;

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
        client = newClient(NO_BACKOFF);
    }

    private GdacsStormClient newClient(long[] backoffMs) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(3));
        RestClient restClient =
                RestClient.builder()
                        .baseUrl(wireMockServer.baseUrl())
                        .requestFactory(factory)
                        .build();
        return new GdacsStormClient(restClient, objectMapper, SEARCH_PATH, TRACK_PATH, backoffMs);
    }

    private String fixture(String name) {
        try (InputStream in =
                getClass().getClassLoader().getResourceAsStream("fixtures/gdacs/" + name)) {
            assertThat(in).as("fixture %s tồn tại", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được fixture " + name, e);
        }
    }

    private void stubSearch(String body) {
        wireMockServer.stubFor(
                get(urlPathEqualTo(SEARCH_PATH))
                        .withQueryParam("eventlist", equalTo("TC"))
                        .willReturn(okJson(body)));
    }

    private void stubTrack(String body) {
        wireMockServer.stubFor(
                get(urlPathEqualTo(TRACK_PATH))
                        .withQueryParam("eventtype", equalTo("TC"))
                        .willReturn(okJson(body)));
    }

    // ---------------------------------------------------------------------
    // Chuẩn hóa đơn vị gió (quyết định #6)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("TC-ĐV1: toMs — km/h ÷ 3.6, knot × 0.514444, đơn vị lạ coi như km/h")
    void shouldNormalizeWindToMs() {
        assertThat(GdacsStormClient.toMs(296.2944, "km/h")).isCloseTo(82.304, within(1e-3));
        assertThat(GdacsStormClient.toMs(100.0, "kt")).isCloseTo(51.4444, within(1e-3));
        assertThat(GdacsStormClient.toMs(72.0, null)).isCloseTo(20.0, within(1e-9));
        assertThat(GdacsStormClient.toMs(72.0, "mph")).isCloseTo(20.0, within(1e-9));
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }

    // ---------------------------------------------------------------------
    // Tầng 1 — Search API
    // ---------------------------------------------------------------------

    @Test
    @DisplayName(
            "TC-01: parse fixture thật — chỉ sự kiện iscurrent=true, gió km/h → m/s, [lng,lat]")
    void shouldParseRealSearchFixtureKeepingOnlyCurrentEvents() {
        stubSearch(fixture("search-tc-events.json"));

        GdacsStormClient.GdacsEventFeed feed = client.fetchActiveTropicalCyclones();

        // Fixture chốt 2026-10-22 có 19 sự kiện, duy nhất CHAMPI-26 đang hoạt động
        assertThat(feed.events()).hasSize(1);
        GdacsStormClient.GdacsEvent event = feed.events().get(0);
        assertThat(event.eventId()).isEqualTo("1001338");
        assertThat(event.name()).isEqualTo("Tropical Cyclone CHAMPI-26");
        assertThat(event.category()).isEqualTo("Red");
        assertThat(event.maxWindSpeedMs()).isCloseTo(82.304, within(1e-3)); // 296.2944 km/h ÷ 3.6
        assertThat(event.minPressureHpa()).isNull(); // nguồn không công bố (V6)
        assertThat(event.longitude()).isEqualTo(125.4); // GeoJSON [lng, lat] — không hoán đổi
        assertThat(event.latitude()).isEqualTo(20.9);
        assertThat(event.fromDate()).isEqualTo(OffsetDateTime.parse("2026-10-14T12:00:00Z"));
        assertThat(event.rawJson()).contains("\"eventid\":1001338").contains("\"glide\"");
        assertThat(feed.skipReasons()).isEmpty();
    }

    @Test
    @DisplayName(
            "TC-02: parser phòng vệ — bản ghi thiếu trường bắt buộc bị loại + ghi lý do (RSK-W2-02)")
    void shouldSkipMalformedEventsWithReasons() {
        String body =
                """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","geometry":{"type":"Point","coordinates":[125.4,20.9]},
                   "properties":{"eventtype":"TC","eventid":1,"episodeid":1,"eventname":"A-26",
                     "name":"Tropical Cyclone A-26","alertlevel":"Red","iscurrent":"true",
                     "fromdate":"2026-10-14T12:00:00","todate":"2026-10-20T12:00:00",
                     "severitydata":{"severity":296.2944,"severityunit":"km/h","severitytext":"x"}}},
                  {"type":"Feature","geometry":{"type":"Point","coordinates":[125.4,20.9]},
                   "properties":{"eventtype":"TC","eventid":2,"episodeid":1,"eventname":"B-26",
                     "name":"Tropical Cyclone B-26","alertlevel":"Red","iscurrent":"true",
                     "fromdate":"2026-10-14T12:00:00","todate":"2026-10-20T12:00:00"}},
                  {"type":"Feature","geometry":{"type":"Point","coordinates":[125.4,20.9]},
                   "properties":{"eventtype":"TC","eventid":3,"episodeid":1,"eventname":"C-26",
                     "name":"Tropical Cyclone C-26","alertlevel":"Red","iscurrent":"true",
                     "fromdate":"khong-phai-ngay","todate":"2026-10-20T12:00:00",
                     "severitydata":{"severity":100.0,"severityunit":"km/h","severitytext":"x"}}}
                ]}
                """;
        stubSearch(body);

        GdacsStormClient.GdacsEventFeed feed = client.fetchActiveTropicalCyclones();

        assertThat(feed.events()).hasSize(1);
        assertThat(feed.events().get(0).eventId()).isEqualTo("1");
        assertThat(feed.skipReasons())
                .hasSize(2)
                .anyMatch(r -> r.contains("event 2") && r.contains("gió"))
                .anyMatch(r -> r.contains("event 3") && r.contains("fromdate"));
    }

    @Test
    @DisplayName("TC-03: retry 5xx rồi thành công — đúng số lượt gọi, backoff inject 0")
    void shouldRetryOnServerErrorThenSucceed() {
        wireMockServer.stubFor(
                get(urlPathEqualTo(SEARCH_PATH))
                        .inScenario("retry-5xx")
                        .whenScenarioStateIs(
                                com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                        .willReturn(aResponse().withStatus(503))
                        .willSetStateTo("ok"));
        wireMockServer.stubFor(
                get(urlPathEqualTo(SEARCH_PATH))
                        .inScenario("retry-5xx")
                        .whenScenarioStateIs("ok")
                        .willReturn(okJson(fixture("search-tc-events.json"))));

        GdacsStormClient.GdacsEventFeed feed = client.fetchActiveTropicalCyclones();

        assertThat(feed.events()).hasSize(1);
        wireMockServer.verify(2, getRequestedFor(urlPathEqualTo(SEARCH_PATH)));
    }

    @Test
    @DisplayName("TC-04: 4xx không retry — ném GdacsClientException retryable=false, 1 lượt gọi")
    void shouldFailFastOnClientError() {
        wireMockServer.stubFor(
                get(urlPathEqualTo(SEARCH_PATH)).willReturn(aResponse().withStatus(404)));

        GdacsClientException thrown =
                catchThrowableOfType(
                        () -> client.fetchActiveTropicalCyclones(), GdacsClientException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.isRetryable()).isFalse();
        assertThat(thrown.getHttpStatus()).isEqualTo(404);
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo(SEARCH_PATH)));
    }

    @Test
    @DisplayName("TC-05: payload không phải JSON → GdacsClientException retryable")
    void shouldWrapMalformedPayloadAsRetryableError() {
        wireMockServer.stubFor(
                get(urlPathEqualTo(SEARCH_PATH)).willReturn(okJson("<html>not json</html>")));

        GdacsClientException thrown =
                catchThrowableOfType(
                        () -> client.fetchActiveTropicalCyclones(), GdacsClientException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.isRetryable()).isTrue();
    }

    // ---------------------------------------------------------------------
    // Tầng 2 — geometry API (quỹ đạo)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName(
            "TC-06: parse fixture quỹ đạo thật — thời gian mốc suy từ chuỗi 6h, không dùng polygondate")
    void shouldParseRealTrackFixture() {
        stubTrack(fixture("track-champi-1001338.json"));
        GdacsStormClient.GdacsEvent event =
                new GdacsStormClient.GdacsEvent(
                        "1001338",
                        "32",
                        "CHAMPI-26",
                        OffsetDateTime.parse("2026-10-14T12:00:00Z"),
                        OffsetDateTime.parse("2026-10-22T06:00:00Z"),
                        "Red",
                        82.304,
                        null,
                        125.4,
                        20.9,
                        "{}");

        List<GdacsStormClient.GdacsTrackPoint> points = client.fetchStormTrack(event);

        // 31 đoạn historical (→ 32 mốc gồm mốc fromdate) + 7 đoạn forecast = 39 mốc
        assertThat(points).hasSize(39);
        assertThat(points)
                .extracting(GdacsStormClient.GdacsTrackPoint::trackType)
                .contains("historical", "forecast");
        // polygondate đồng nhất (giờ chạy mô hình) — thời gian mốc phải suy từ chuỗi liên kết
        assertThat(points)
                .extracting(GdacsStormClient.GdacsTrackPoint::atTime)
                .doesNotHaveDuplicates();
        List<OffsetDateTime> histTimes =
                points.stream()
                        .filter(p -> p.trackType().equals("historical"))
                        .map(GdacsStormClient.GdacsTrackPoint::atTime)
                        .sorted()
                        .toList();
        assertThat(histTimes).hasSize(32);
        assertThat(histTimes.get(0)).isEqualTo(OffsetDateTime.parse("2026-10-14T12:00:00Z"));
        assertThat(histTimes.get(31)).isEqualTo(OffsetDateTime.parse("2026-10-22T06:00:00Z"));
        List<OffsetDateTime> fcTimes =
                points.stream()
                        .filter(p -> p.trackType().equals("forecast"))
                        .map(GdacsStormClient.GdacsTrackPoint::atTime)
                        .sorted()
                        .toList();
        assertThat(fcTimes).hasSize(7);
        assertThat(fcTimes.get(0)).isEqualTo(OffsetDateTime.parse("2026-10-22T12:00:00Z"));
        assertThat(fcTimes.get(6)).isEqualTo(OffsetDateTime.parse("2026-10-24T00:00:00Z"));

        GdacsStormClient.GdacsTrackPoint sample = points.get(0);
        assertThat(sample.atTime()).isEqualTo(OffsetDateTime.parse("2026-10-14T12:00:00Z"));
        assertThat(sample.windSpeedMs()).isNull(); // nguồn không công bố theo mốc (V6)
        assertThat(sample.windGustMs()).isNull();
        assertThat(sample.pressureHpa()).isNull();
        assertThat(sample.category()).isIn("HU", "TS", "TD");
        assertThat(sample.impactRadiusKm()).isEqualTo(100.0); // DEFAULT V4
        // Tọa độ hợp lệ: kinh độ Tây Thái Bình Dương 122-161 (nhánh forecast xa nhất), vĩ độ
        // dương — nếu hoán đổi lat/lng thì "vĩ độ" sẽ vượt ±90 và test này thất bại
        assertThat(points)
                .allSatisfy(
                        p -> {
                            assertThat(p.longitude()).isBetween(115.0, 165.0);
                            assertThat(p.latitude()).isBetween(8.0, 36.0);
                        });
    }

    @Test
    @DisplayName("TC-07: quỹ đạo 404 — trả danh sách rỗng + không ném (degradation an toàn)")
    void shouldReturnEmptyTrackOn404() {
        wireMockServer.stubFor(
                get(urlPathEqualTo(TRACK_PATH)).willReturn(aResponse().withStatus(404)));
        GdacsStormClient.GdacsEvent event =
                new GdacsStormClient.GdacsEvent(
                        "999",
                        "1",
                        "X-26",
                        OffsetDateTime.parse("2026-10-14T12:00:00Z"),
                        null,
                        "Green",
                        20.0,
                        null,
                        100.0,
                        10.0,
                        "{}");

        List<GdacsStormClient.GdacsTrackPoint> points = client.fetchStormTrack(event);

        assertThat(points).isEmpty();
    }

    @Test
    @DisplayName("TC-08: quỹ đạo 5xx — retry rồi ném retryable khi cạn lượt")
    void shouldRetryTrackOnServerErrorThenThrow() {
        wireMockServer.stubFor(
                get(urlPathEqualTo(TRACK_PATH)).willReturn(aResponse().withStatus(503)));
        GdacsStormClient.GdacsEvent event =
                new GdacsStormClient.GdacsEvent(
                        "999",
                        "1",
                        "X-26",
                        OffsetDateTime.parse("2026-10-14T12:00:00Z"),
                        null,
                        "Green",
                        20.0,
                        null,
                        100.0,
                        10.0,
                        "{}");

        GdacsClientException thrown =
                catchThrowableOfType(
                        () -> client.fetchStormTrack(event), GdacsClientException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.isRetryable()).isTrue();
        wireMockServer.verify(4, getRequestedFor(urlPathEqualTo(TRACK_PATH))); // 1 + 3 retry
    }
}
