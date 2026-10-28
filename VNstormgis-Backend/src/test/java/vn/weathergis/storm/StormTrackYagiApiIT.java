package vn.weathergis.storm;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.weathergis.automation.dto.StormSyncResult;
import vn.weathergis.automation.service.StormSyncService;
import vn.weathergis.common.AbstractPostGisIntegrationTest;

/**
 * Integration Test end-to-end của {@code GET /api/v1/storms/{id}/track} (Task W3-02) — chứng cứ chủ
 * chốt của task, chạy trên chuỗi thật không mock tầng service:
 *
 * <pre>
 *   WireMock (fixture YAGI W2-06) → GdacsStormClient → StormSyncService → PostGIS (Testcontainers)
 *     → MockMvc → StormController → StormServiceImpl → GeoJSON RFC 7946
 * </pre>
 *
 * <p>Khung WireMock clone nguyên vẹn từ {@code StormIngestionIT} (W2-06 đã nghiệm thu): cùng 2
 * endpoint GDACS giả lập, cùng {@code @DynamicPropertySource} ghi đè base-url. Tách class riêng vì
 * context property của nó khác {@code StormApiIT}.
 *
 * <p>Quỹ đạo fixture: 12 đoạn historical nối tiếp (13 mốc, {@code 2024-09-04T00:00:00Z} → {@code
 * 2024-09-07T00:00:00Z}, bước 6h) + 2 đoạn forecast ({@code +06h}, {@code +12h}). Hợp đồng /track
 * vì thế là <b>17 feature</b>: 2 LineString + 15 Point, và {@code impact-zone} <b>vắng mặt</b> vì
 * ingestion GDACS không ghi {@code impact_polygon} (quyết định #9/#10 — không bịa vùng ảnh hưởng).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("StormTrackYagiApiIT — WireMock YAGI → sync thật → /storms/1001209/track (W3-02)")
class StormTrackYagiApiIT extends AbstractPostGisIntegrationTest {

    private static final String SEARCH_PATH = "/gdacsapi/api/Events/geteventlist/search";
    private static final String TRACK_PATH = "/gdacsapi/api/polygons/getgeometry";
    private static final String YAGI_ID = "1001209";
    private static final String TRACK_URL = "/api/v1/storms/" + YAGI_ID + "/track";

    private static final List<String> POINT_CONTRACT_KEYS =
            List.of(
                    "track_id",
                    "storm_id",
                    "track_type",
                    "forecast_time",
                    "wind_speed",
                    "wind_gust",
                    "pressure",
                    "category",
                    "impact_radius_km");

    /**
     * 13 mốc historical theo forecast_time ASC — đỉnh đầu đoạn cổ nhất rồi lần lượt các đỉnh cuối.
     */
    private static final double[][] HISTORY = {
        {116.5, 18.4},
        {115.2, 18.6},
        {114.0, 18.7},
        {113.0, 18.9},
        {112.2, 19.0},
        {111.5, 19.2},
        {110.8, 19.4},
        {110.2, 19.6},
        {109.6, 19.8},
        {108.6, 20.1},
        {107.8, 20.4},
        {107.5, 20.6},
        {107.2, 20.7},
    };

    /** LineString dự báo = đỉnh nối (mốc historical cuối) + 2 mốc forecast. */
    private static final double[][] FORECAST = {
        {107.2, 20.7},
        {106.3, 20.9},
        {105.3, 21.1},
    };

    private static final WireMockServer WIRE_MOCK =
            new WireMockServer(wireMockConfig().dynamicPort());

    static {
        WIRE_MOCK.start(); // phải start TRƯỚC khi Spring context nạp @DynamicPropertySource
    }

    @AfterAll
    static void stopWireMock() {
        if (WIRE_MOCK.isRunning()) {
            WIRE_MOCK.stop();
        }
    }

    @DynamicPropertySource
    static void redirectGdacsToWireMock(DynamicPropertyRegistry registry) {
        registry.add("weathergis.external.gdacs.base-url", WIRE_MOCK::baseUrl);
        registry.add("weathergis.external.gdacs.backoff-ms", () -> "0,0,0");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StormSyncService stormSyncService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void stubAndIngestYagi() {
        WIRE_MOCK.resetAll();
        jdbcTemplate.update("DELETE FROM storms"); // cascade dọn storm_tracks (FK V4)
        jdbcTemplate.update("DELETE FROM data_sync_logs");

        stubSearch("search-yagi-active.json");
        stubTrack("track-yagi.json");

        StormSyncResult result = stormSyncService.triggerStormSync();
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.trackPointsInserted())
                .as("fixture YAGI phải nạp 13 historical + 2 forecast")
                .isEqualTo(15);
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("TC-17: /track trả đúng 17 feature — 2 LineString + 15 Point, không envelope")
    void shouldReturnSeventeenFeaturesFromRealIngestion() throws Exception {
        JsonNode root = readTrack();

        assertThat(root.get("type").asText()).isEqualTo("FeatureCollection");
        assertThat(root.has("success")).as("GeoJSON trả thô, không bọc ApiResponse").isFalse();
        assertThat(root.has("data")).isFalse();

        JsonNode features = root.get("features");
        assertThat(features).hasSize(17);
        assertThat(features.get(0).get("id").asText()).isEqualTo("track-history");
        assertThat(features.get(1).get("id").asText()).isEqualTo("track-forecast");
        assertThat(featureIds(features).subList(2, 17))
                .allSatisfy(id -> assertThat(id).startsWith("track-point-"))
                .doesNotHaveDuplicates();
        assertThat(geometryTypes(features))
                .containsExactlyElementsOf(
                        concat(List.of("LineString", "LineString"), repeat("Point", 15)));
    }

    @Test
    @DisplayName(
            "TC-17: LineString lịch sử 13 đỉnh [116.5,18.4] → [107.2,20.7] đúng trật tự forecast_time ASC")
    void shouldBuildHistoryLineWithThirteenVerticesInTimeOrder() throws Exception {
        JsonNode history = readTrack().get("features").get(0);

        assertThat(history.get("geometry").get("type").asText()).isEqualTo("LineString");
        assertThatCoordinateOrder(history.get("geometry").get("coordinates"), HISTORY);
        assertThat(history.get("properties").get("kind").asText()).isEqualTo("history");
        assertThat(history.get("properties").get("storm_id").asText()).isEqualTo(YAGI_ID);
        assertThat(history.get("properties").get("points").asInt()).isEqualTo(13);
        assertThat(keysOf(history.get("properties"))).containsExactly("kind", "storm_id", "points");

        // Đối chiếu thẳng với CSDL: LineString phải là đúng chuỗi truy vấn có chỉ mục
        List<List<Double>> dbVertices =
                jdbcTemplate.query(
                        "SELECT ST_X(geom), ST_Y(geom) FROM storm_tracks WHERE storm_id = ?"
                                + " AND track_type = 'historical' ORDER BY forecast_time ASC",
                        (rs, n) -> List.of(rs.getDouble(1), rs.getDouble(2)),
                        YAGI_ID);
        assertThat(verticesOf(history.get("geometry").get("coordinates"))).isEqualTo(dbVertices);
    }

    @Test
    @DisplayName(
            "TC-17: LineString dự báo 3 đỉnh có đỉnh nối từ tâm bão, points=2 (đỉnh nối không đếm)")
    void shouldBuildForecastLinePrependedWithJunction() throws Exception {
        JsonNode forecast = readTrack().get("features").get(1);

        assertThat(forecast.get("geometry").get("type").asText()).isEqualTo("LineString");
        assertThatCoordinateOrder(forecast.get("geometry").get("coordinates"), FORECAST);
        assertThat(forecast.get("properties").get("kind").asText()).isEqualTo("forecast");
        assertThat(forecast.get("properties").get("points").asInt())
                .as("properties.points đếm mốc forecast, không tính đỉnh nối")
                .isEqualTo(2);
        assertThat(forecast.get("geometry").get("coordinates").get(0).get(0).asDouble())
                .as("đỉnh 1 nối liền với tâm bão để W3-06 vẽ đường đứt đoạn không hở")
                .isEqualTo(107.2);
    }

    @Test
    @DisplayName(
            "TC-17 / quyết định #9: GDACS không cung cấp polygon → KHÔNG có impact-zone, vẫn HTTP 200")
    void shouldNotFabricateImpactZoneOnLiveData() throws Exception {
        JsonNode root = readTrack();

        assertThat(featureIds(root.get("features")))
                .as("không buffer từ impact_radius_km default 100.0 — đó là dữ liệu bịa")
                .doesNotContain("impact-zone");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM storm_tracks WHERE impact_polygon IS NOT NULL",
                                Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("TC-17 / SYS-RULE-004 + D-1: 15 Point đủ 9 khóa, gió/giật/áp suất in ra null")
    void shouldExposeNineKeysAndHonestNullsOnEveryPoint() throws Exception {
        JsonNode root = readTrack();
        List<JsonNode> points = pointFeatures(root);

        assertThat(points).hasSize(15);
        for (JsonNode props : points.stream().map(f -> f.get("properties")).toList()) {
            assertThat(keysOf(props)).containsExactlyElementsOf(POINT_CONTRACT_KEYS);
            assertThat(props.get("storm_id").asText()).isEqualTo(YAGI_ID);
            for (String nullable : List.of("wind_speed", "wind_gust", "pressure")) {
                assertThat(props.has(nullable))
                        .as("%s phải ở lại trên dây truyền nhờ NullNode", nullable)
                        .isTrue();
                assertThat(props.get(nullable).isNull())
                        .as("%s phải null, nhận: %s", nullable, props.get(nullable))
                        .isTrue();
            }
            assertThat(props.get("category").isNull()).isFalse();
            assertThat(props.get("impact_radius_km").asDouble()).isEqualTo(100.0);
        }
    }

    @Test
    @DisplayName("TC-17: 13 mốc historical trước rồi 2 mốc forecast, thời gian tăng dần tuyệt đối")
    void shouldOrderPointsHistoricalThenForecastByTime() throws Exception {
        List<JsonNode> points = pointFeatures(readTrack());

        List<String> types =
                points.stream().map(f -> f.get("properties").get("track_type").asText()).toList();
        assertThat(types.subList(0, 13)).containsOnly("historical");
        assertThat(types.subList(13, 15)).containsOnly("forecast");

        List<Instant> times =
                points.stream()
                        .map(
                                f ->
                                        OffsetDateTime.parse(
                                                        f.get("properties")
                                                                .get("forecast_time")
                                                                .asText())
                                                .toInstant())
                        .toList();
        assertThat(times).isSorted();
        assertThat(times.get(0)).isEqualTo(Instant.parse("2024-09-04T00:00:00Z"));
        assertThat(times.get(12)).isEqualTo(Instant.parse("2024-09-07T00:00:00Z"));
        assertThat(times.get(13)).isEqualTo(Instant.parse("2024-09-07T06:00:00Z"));
        assertThat(times.get(14)).isEqualTo(Instant.parse("2024-09-07T12:00:00Z"));
    }

    @Test
    @DisplayName("TC-17 / AGENTS.md mục 1: mọi coordinate [lng, lat] — không hoán đổi, SRID 4326")
    void shouldKeepSridAndCoordinateOrderAcrossChain() throws Exception {
        JsonNode root = readTrack();

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM storm_tracks WHERE storm_id = ?"
                                        + " AND ST_SRID(geom) = 4326",
                                Integer.class,
                                YAGI_ID))
                .isEqualTo(15);

        List<JsonNode> points = pointFeatures(root);
        for (int i = 0; i < HISTORY.length; i++) {
            JsonNode coordinates = points.get(i).get("geometry").get("coordinates");
            assertThat(coordinates.get(0).asDouble()).isCloseTo(HISTORY[i][0], within(1e-9));
            assertThat(coordinates.get(1).asDouble()).isCloseTo(HISTORY[i][1], within(1e-9));
            assertThat(coordinates.get(0).asDouble())
                    .as("kinh độ Biển Đông luôn lớn hơn vĩ độ tại mọi mốc")
                    .isGreaterThan(coordinates.get(1).asDouble());
            assertThat(coordinates.get(0).asDouble()).isBetween(100.0, 128.0);
            assertThat(coordinates.get(1).asDouble()).isBetween(0.0, 28.0);
        }
    }

    @Test
    @DisplayName("TC-17: bão dissipated vẫn truy vết được quỹ đạo — /track không lọc status")
    void shouldServeTrackOfDissipatedStorm() throws Exception {
        jdbcTemplate.update("UPDATE storms SET status = 'dissipated' WHERE id = ?", YAGI_ID);

        mockMvc.perform(get(TRACK_URL).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(17));
    }

    @Test
    @DisplayName("Chuẩn tắc WireMock: chuỗi nạp gọi đúng 2 endpoint GDACS của production")
    void shouldHaveRequestedBothGdacsEndpoints() {
        WIRE_MOCK.verify(
                getRequestedFor(urlPathEqualTo(SEARCH_PATH))
                        .withQueryParam("eventlist", equalTo("TC")));
        WIRE_MOCK.verify(
                getRequestedFor(urlPathEqualTo(TRACK_PATH))
                        .withQueryParam("eventid", equalTo(YAGI_ID)));
    }

    @Test
    @DisplayName("Hồi quy W3-01: /storms/1001209 detail vẫn đếm đủ 15 mốc sau khi thêm /track")
    void shouldNotDisturbStormDetail() throws Exception {
        mockMvc.perform(get("/api/v1/storms/" + YAGI_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTrackPoints").value(15));
    }

    // ------------------------------------------------------------------
    // Trợ giúp — fixture, stub WireMock, đọc GeoJSON
    // ------------------------------------------------------------------

    private String fixture(String name) {
        try (InputStream in =
                getClass().getClassLoader().getResourceAsStream("fixtures/gdacs/" + name)) {
            assertThat(in).as("fixture %s tồn tại (tái sử dụng từ W2-06)", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được fixture " + name, e);
        }
    }

    private void stubSearch(String fixtureName) {
        WIRE_MOCK.stubFor(
                WireMock.get(urlPathEqualTo(SEARCH_PATH))
                        .withQueryParam("eventlist", equalTo("TC"))
                        .willReturn(okJson(fixture(fixtureName))));
    }

    private void stubTrack(String fixtureName) {
        WIRE_MOCK.stubFor(
                WireMock.get(urlPathEqualTo(TRACK_PATH))
                        .withQueryParam("eventtype", equalTo("TC"))
                        .withQueryParam("eventid", equalTo(YAGI_ID))
                        .willReturn(okJson(fixture(fixtureName))));
    }

    private JsonNode readTrack() throws Exception {
        String body =
                new String(
                        mockMvc.perform(get(TRACK_URL).accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsByteArray(),
                        StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private void assertThatCoordinateOrder(JsonNode coordinates, double[][] expected) {
        assertThat(coordinates).hasSize(expected.length);
        List<List<Double>> actual = verticesOf(coordinates);
        for (int i = 0; i < expected.length; i++) {
            assertThat(actual.get(i))
                    .as("đỉnh %d của LineString", i)
                    .isEqualTo(List.of(expected[i][0], expected[i][1]));
        }
    }

    private List<JsonNode> pointFeatures(JsonNode root) {
        List<JsonNode> points = new ArrayList<>();
        root.get("features")
                .forEach(
                        f -> {
                            if (f.get("id").asText().startsWith("track-point-")) {
                                points.add(f);
                            }
                        });
        return points;
    }

    private List<String> featureIds(JsonNode features) {
        List<String> ids = new ArrayList<>();
        features.forEach(f -> ids.add(f.get("id").asText()));
        return ids;
    }

    private List<String> geometryTypes(JsonNode features) {
        List<String> types = new ArrayList<>();
        features.forEach(f -> types.add(f.get("geometry").get("type").asText()));
        return types;
    }

    private List<String> keysOf(JsonNode properties) {
        List<String> keys = new ArrayList<>();
        properties.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private List<List<Double>> verticesOf(JsonNode coordinates) {
        List<List<Double>> vertices = new ArrayList<>();
        coordinates.forEach(c -> vertices.add(List.of(c.get(0).asDouble(), c.get(1).asDouble())));
        return vertices;
    }

    private static List<String> repeat(String value, int times) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            values.add(value);
        }
        return values;
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        List<T> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
