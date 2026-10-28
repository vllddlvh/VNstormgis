package vn.weathergis.storm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.weathergis.common.AbstractPostGisIntegrationTest;
import vn.weathergis.storm.domain.StormEntity;
import vn.weathergis.storm.repository.StormRepository;
import vn.weathergis.storm.repository.StormTrackRepository;

/**
 * Integration Test tầng đọc API bão (Task W3-01) trên PostGIS thật:
 *
 * <pre>
 *   MockMvc → StormController → StormServiceImpl → StormRepository / StormTrackRepository
 *     → PostgreSQL 16 + PostGIS 3.4 (Testcontainers, Flyway V1→V6)
 * </pre>
 *
 * Seed trực tiếp qua repository (không WireMock — fixture YAGI dành trọn cho W3-02): chứng minh lọc
 * status mặc định theo SYS-RULE-001, sắp xếp updated_at DESC, tọa độ [lng, lat] kiểm chéo ST_X/ST_Y
 * trên CSDL, SRID 4326, total_track_points khớp COUNT thật và lỗi RFC 7807 mang `errorCode`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("StormApiIT — /storms list & detail trên PostGIS thật (W3-01)")
class StormApiIT extends AbstractPostGisIntegrationTest {

    private static final String STORMS_URL = "/api/v1/storms";
    private static final String ACTIVE_ID = "teststorm01";
    private static final String TRACK_URL = STORMS_URL + "/" + ACTIVE_ID + "/track";
    private static final String DISSIPATED_ID = "teststorm02";
    private static final double ACTIVE_LNG = 116.4;
    private static final double ACTIVE_LAT = 18.2;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StormRepository stormRepository;
    @Autowired private StormTrackRepository stormTrackRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @BeforeEach
    void cleanStormTables() {
        jdbcTemplate.update("DELETE FROM storms"); // cascade dọn storm_tracks (FK V4)
    }

    // ------------------------------------------------------------------
    // TC-04 / TC-05: danh sách GeoJSON
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "TC-04: mặc định chỉ trả bão active — FeatureCollection thô, không envelope, không raw_metadata")
    void shouldReturnOnlyActiveStormsAsBareGeoJson() throws Exception {
        seedTwoStorms();

        JsonNode root = readJson(get(STORMS_URL));
        JsonNode feature = root.get("features").get(0);
        JsonNode props = feature.get("properties");

        assertThat(root.get("type").asText()).isEqualTo("FeatureCollection");
        assertThat(root.has("success")).as("GeoJSON trả thô, không bọc envelope").isFalse();
        assertThat(root.has("data")).isFalse();
        assertThat(root.get("features"))
                .as("SYS-RULE-001: bão dissipated bị loại khỏi lớp khẩn cấp")
                .hasSize(1);
        assertThat(feature.get("type").asText()).isEqualTo("Feature");
        assertThat(feature.get("id").asText()).isEqualTo(ACTIVE_ID);
        assertThat(props.get("id").asText()).isEqualTo(ACTIVE_ID);
        assertThat(feature.get("geometry").get("type").asText()).isEqualTo("Point");
        assertThat(props.get("name").asText()).isEqualTo("TEST STORM A");
        assertThat(props.get("year").asInt()).isEqualTo(2026);
        assertThat(props.get("category").asText()).isEqualTo("Typhoon");
        assertThat(props.get("status").asText()).isEqualTo("active");
        assertThat(props.get("max_wind_speed").asDouble()).isCloseTo(40.0, within(1e-9));
        assertThat(props.get("max_wind_speed_kmh").asDouble()).isCloseTo(144.0, within(1e-9));
        assertThat(props.has("raw_metadata")).as("payload GDACS không lộ ra API").isFalse();
        assertThat(props.has("code")).as("quyết định #2: không có khóa code").isFalse();
        assertThat(props.has("central_pressure")).as("quyết định #3: dùng min_pressure").isFalse();

        assertThat(keysOf(props)).containsExactlyElementsOf(expectedContractKeys());
    }

    @Test
    @DisplayName(
            "TC-05: status=dissipated chỉ bão đã tan; status=all trả cả 2 theo updated_at DESC")
    void shouldFilterByStatusAndOrderRecentlyUpdatedFirst() throws Exception {
        seedTwoStorms();

        JsonNode dissipated = readJson(get(STORMS_URL).param("status", "dissipated"));
        assertThat(statusesOf(dissipated)).containsExactly("dissipated");
        assertThat(dissipated.get("features").get(0).get("id").asText()).isEqualTo(DISSIPATED_ID);
        // Bão dissipated có min_pressure NULL trong seed — vẫn phải nguyên hợp đồng 11 khóa
        assertThat(keysOf(dissipated.get("features").get(0).get("properties")))
                .containsExactlyElementsOf(expectedContractKeys());
        assertPressureIsNeverFabricated(dissipated.get("features").get(0).get("properties"));

        JsonNode all = readJson(get(STORMS_URL).param("status", "all"));
        assertThat(statusesOf(all)).containsExactly("active", "dissipated");
        assertThat(idsOf(all.get("features"))).containsExactly(ACTIVE_ID, DISSIPATED_ID);
    }

    // ------------------------------------------------------------------
    // TC-11: kiểm chéo tọa độ với PostGIS
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "TC-11: coordinates[0]=ST_X=kinh độ, coordinates[1]=ST_Y=vĩ độ — không hoán đổi, SRID 4326")
    void shouldMatchPostGisCoordinatesWithoutSwap() throws Exception {
        seedTwoStorms();

        JsonNode coordinates =
                readJson(get(STORMS_URL)).get("features").get(0).get("geometry").get("coordinates");

        Double dbLng =
                jdbcTemplate.queryForObject(
                        "SELECT ST_X(geom_current) FROM storms WHERE id = ?",
                        Double.class,
                        ACTIVE_ID);
        Double dbLat =
                jdbcTemplate.queryForObject(
                        "SELECT ST_Y(geom_current) FROM storms WHERE id = ?",
                        Double.class,
                        ACTIVE_ID);
        Integer srid =
                jdbcTemplate.queryForObject(
                        "SELECT ST_SRID(geom_current) FROM storms WHERE id = ?",
                        Integer.class,
                        ACTIVE_ID);

        assertThat(srid).as("geom_current phải SRID 4326").isEqualTo(4326);
        assertThat(coordinates.get(0).asDouble()).isCloseTo(dbLng, within(1e-9));
        assertThat(coordinates.get(1).asDouble()).isCloseTo(dbLat, within(1e-9));
        assertThat(coordinates.get(0).asDouble()).isCloseTo(ACTIVE_LNG, within(1e-9));
        assertThat(coordinates.get(1).asDouble()).isCloseTo(ACTIVE_LAT, within(1e-9));
        assertThat(coordinates.get(0).asDouble())
                .as("Hoán đổi lat/lng sẽ đẩy tâm bão sang Ấn Độ Dương")
                .isGreaterThan(coordinates.get(1).asDouble());
        assertThat(coordinates.get(0).asDouble()).isBetween(100.0, 128.0); // bbox Biển Đông (W3-09)
        assertThat(coordinates.get(1).asDouble()).isBetween(0.0, 28.0);
    }

    // ------------------------------------------------------------------
    // TC-07 / TC-08: format=json & chi tiết
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TC-07: format=json trả ApiResponse phẳng camelCase, active đứng trước")
    void shouldReturnJsonEnvelopeWithFlatCamelCaseDto() throws Exception {
        seedTwoStorms();

        mockMvc.perform(
                        get(STORMS_URL)
                                .param("format", "json")
                                .param("status", "all")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(ACTIVE_ID))
                .andExpect(jsonPath("$.data[0].maxWindSpeed").value(40.0))
                .andExpect(jsonPath("$.data[0].maxWindSpeedKmh").value(144.0))
                .andExpect(jsonPath("$.data[0].minPressure").value(960.0))
                .andExpect(jsonPath("$.data[0].status").value("active"))
                .andExpect(jsonPath("$.data[0].code").doesNotExist())
                .andExpect(jsonPath("$.data[0].rawMetadata").doesNotExist())
                .andExpect(jsonPath("$.data[1].id").value(DISSIPATED_ID))
                .andExpect(jsonPath("$.data[1].status").value("dissipated"));
    }

    @Test
    @DisplayName(
            "TC-08/TC-17: detail theo mã GDACS — tọa độ phẳng, gió 2 đơn vị, totalTrackPoints khớp COUNT thật")
    void shouldReturnStormDetailWithTrackCount() throws Exception {
        seedTwoStorms();
        seedThreeTrackPoints();

        JsonNode data = readJson(get(STORMS_URL + "/" + ACTIVE_ID)).get("data");

        assertThat(data.get("id").asText()).isEqualTo(ACTIVE_ID);
        assertThat(data.get("name").asText()).isEqualTo("TEST STORM A");
        assertThat(data.get("year").asInt()).isEqualTo(2026);
        assertThat(data.get("category").asText()).isEqualTo("Typhoon");
        assertThat(data.get("maxWindSpeed").asDouble()).isCloseTo(40.0, within(1e-9));
        assertThat(data.get("maxWindSpeedKmh").asDouble()).isCloseTo(144.0, within(1e-9));
        assertThat(data.get("minPressure").asDouble()).isCloseTo(960.0, within(1e-9));
        assertThat(data.get("status").asText()).isEqualTo("active");
        assertThat(data.get("longitude").asDouble()).isCloseTo(ACTIVE_LNG, within(1e-9));
        assertThat(data.get("latitude").asDouble()).isCloseTo(ACTIVE_LAT, within(1e-9));
        assertThat(data.get("longitude").asDouble()).isGreaterThan(data.get("latitude").asDouble());
        assertThat(data.has("endDate"))
                .as("TC-08: bão còn active vẫn phải có khóa endDate với giá trị null")
                .isTrue();
        assertThat(data.get("endDate").isNull()).isTrue();
        assertThat(data.has("rawMetadata")).as("không lộ raw_metadata trong detail").isFalse();

        Integer actualTracks =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM storm_tracks WHERE storm_id = ?",
                        Integer.class,
                        ACTIVE_ID);
        assertThat(data.get("totalTrackPoints").asLong())
                .isEqualTo(actualTracks.longValue())
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("TC-08: bão chưa có mốc quỹ đạo → totalTrackPoints = 0 (không lỗi)")
    void shouldReturnZeroTrackPointsForStormWithoutTrack() throws Exception {
        seedTwoStorms();

        mockMvc.perform(get(STORMS_URL + "/" + DISSIPATED_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTrackPoints").value(0));
    }

    // ------------------------------------------------------------------
    // TC-06 / TC-09: RFC 7807
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TC-09: mã sự kiện không tồn tại → 404 RFC 7807 STM_404_NOT_FOUND")
    void shouldReturnProblemDetailForUnknownStormCode() throws Exception {
        seedTwoStorms();

        mockMvc.perform(get(STORMS_URL + "/khong-ton-tai-999").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Tài nguyên không tồn tại"))
                .andExpect(jsonPath("$.detail").value("Không tìm thấy bão: khong-ton-tai-999"))
                .andExpect(jsonPath("$.errorCode").value("STM_404_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value(STORMS_URL + "/khong-ton-tai-999"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("TC-06: status ngoài hợp đồng → 400 RFC 7807 STM_400_INVALID_STATUS")
    void shouldReturnProblemDetailForInvalidStatus() throws Exception {
        seedTwoStorms();

        mockMvc.perform(
                        get(STORMS_URL)
                                .param("status", "active-xyz")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Tham số không hợp lệ"))
                .andExpect(jsonPath("$.errorCode").value("STM_400_INVALID_STATUS"))
                .andExpect(jsonPath("$.instance").value(STORMS_URL))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("TC-06: active/dissipated/all và hoa/thường đều hợp lệ (runbook 7.8)")
    void shouldAcceptAllContractStatuses() throws Exception {
        seedTwoStorms();

        mockMvc.perform(get(STORMS_URL).param("status", "all")).andExpect(status().isOk());
        mockMvc.perform(get(STORMS_URL).param("status", "dissipated")).andExpect(status().isOk());
        mockMvc.perform(get(STORMS_URL).param("status", "ACTIVE")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // TC-10 / TC-13: empty state & áp suất null
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TC-10: DB không có bão → 200 + features: [] / data: [] + message thân thiện")
    void shouldReturnEmptyStateWithoutError() throws Exception {
        mockMvc.perform(get(STORMS_URL).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(0));

        mockMvc.perform(get(STORMS_URL).param("format", "json").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Hiện không có cơn bão nào đang hoạt động trong khu vực"
                                                + " theo dõi"));
    }

    @Test
    @DisplayName(
            "TC-10: chỉ có bão dissipated → mặc định trả collection rỗng, status=all vẫn thấy bão")
    void shouldReturnEmptyActiveListWhenOnlyDissipatedStormExists() throws Exception {
        seedTwoStorms();
        jdbcTemplate.update("UPDATE storms SET status = 'dissipated' WHERE id = ?", ACTIVE_ID);

        assertThat(readJson(get(STORMS_URL)).get("features")).isEmpty();
        assertThat(statusesOf(readJson(get(STORMS_URL).param("status", "all"))))
                .containsOnly("dissipated");
    }

    @Test
    @DisplayName(
            "TC-13 / SYS-RULE-004: min_pressure NULL → không bịa số, null trên cả GeoJSON lẫn detail")
    void shouldExposeNullMinPressureHonestly() throws Exception {
        seedTwoStorms();
        jdbcTemplate.update("UPDATE storms SET min_pressure = NULL WHERE id = ?", ACTIVE_ID);

        JsonNode props = readJson(get(STORMS_URL)).get("features").get(0).get("properties");
        assertPressureIsNeverFabricated(props);

        mockMvc.perform(get(STORMS_URL + "/" + ACTIVE_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxWindSpeed").value(40.0))
                .andExpect(jsonPath("$.data.minPressure", nullValue()));
    }

    /**
     * Chứng minh SYS-RULE-004: khi GDACS không công bố áp suất, API KHÔNG điền số giả (0, 1013
     * hPa...). Khóa bắt buộc hiện diện với giá trị JSON null — StormService phát NullNode thay vì
     * Java null để thoát khỏi {@code default-property-inclusion=non_null}, nhờ đó hợp đồng 11 khóa
     * (TC-04) đúng cả trên dây truyền.
     */
    private void assertPressureIsNeverFabricated(JsonNode props) {
        assertThat(props.has("min_pressure"))
                .as("khóa min_pressure phải hiện diện (hợp đồng 11 khóa)")
                .isTrue();
        assertThat(props.get("min_pressure").isNull())
                .as("min_pressure phải null, không phải số bịa: %s", props.get("min_pressure"))
                .isTrue();
    }

    // ------------------------------------------------------------------
    // W3-02: GET /storms/{id}/track trên PostGIS thật
    // ------------------------------------------------------------------

    @Nested
    @DisplayName(
            "W3-02 — /storms/{id}/track: 4 loại feature, thứ tự thời gian, kiểm chéo ST_X/ST_Y")
    class TrackEndpointTests {

        @Test
        @DisplayName(
                "TC-04: trật tự cố định history → forecast → impact → 5 Point, LineString/Polygon đúng kiểu")
        void shouldReturnTrackFeaturesInFixedOrder() throws Exception {
            seedStormWithFullTrack();

            JsonNode root = readJson(get(TRACK_URL));
            JsonNode features = root.get("features");

            assertThat(root.get("type").asText()).isEqualTo("FeatureCollection");
            assertThat(root.has("success")).as("GeoJSON trả thô, không envelope").isFalse();
            assertThat(root.has("data")).isFalse();
            // id Point mang BIGSERIAL của storm_tracks — container dùng chung cả suite nên sequence
            // không bắt đầu lại sau DELETE; assert cấu trúc thay vì hard-code id (runbook 7.8).
            List<String> ids = featureIds(features);
            assertThat(ids).hasSize(8);
            assertThat(ids.subList(0, 3))
                    .containsExactly("track-history", "track-forecast", "impact-zone");
            assertThat(ids.subList(3, 8))
                    .allSatisfy(id -> assertThat(id).startsWith("track-point-"))
                    .doesNotHaveDuplicates();
            assertThat(geometryTypes(features))
                    .containsExactly(
                            "LineString",
                            "LineString",
                            "Polygon",
                            "Point",
                            "Point",
                            "Point",
                            "Point",
                            "Point");
            assertThat(features.get(0).get("properties").get("kind").asText()).isEqualTo("history");
            assertThat(features.get(0).get("properties").get("storm_id").asText())
                    .isEqualTo(ACTIVE_ID);
            assertThat(features.get(0).get("properties").get("points").asInt()).isEqualTo(3);
            assertThat(features.get(1).get("properties").get("kind").asText())
                    .isEqualTo("forecast");
            assertThat(features.get(2).get("properties").get("kind").asText()).isEqualTo("impact");
            assertThat(features.get(2).get("properties").get("radius_km").asDouble())
                    .as("radius_km đọc từ cột của mốc thắng, không phải hằng số")
                    .isEqualTo(150.0);
            assertThat(features.get(3).get("properties").has("raw_metadata")).isFalse();
        }

        @Test
        @DisplayName(
                "TC-05: đỉnh LineString khớp nguyên trật tự truy vấn forecast_time ASC của PostGIS")
        void shouldOrderLineVerticesByForecastTime() throws Exception {
            seedStormWithFullTrack();

            JsonNode history = readJson(get(TRACK_URL)).get("features").get(0);
            List<List<Double>> dbOrder =
                    jdbcTemplate.query(
                            "SELECT ST_X(geom), ST_Y(geom) FROM storm_tracks WHERE storm_id = ?"
                                    + " AND track_type = 'historical' ORDER BY forecast_time ASC",
                            (rs, n) -> List.of(rs.getDouble(1), rs.getDouble(2)),
                            ACTIVE_ID);

            assertThat(dbOrder).hasSize(3);
            assertThat(verticesOf(history.get("geometry").get("coordinates"))).isEqualTo(dbOrder);

            JsonNode forecast = readJson(get(TRACK_URL)).get("features").get(1);
            List<List<Double>> fcstVertices =
                    verticesOf(forecast.get("geometry").get("coordinates"));
            assertThat(fcstVertices.get(0))
                    .as("đỉnh 1 = mốc historical cuối (đỉnh nối)")
                    .isEqualTo(dbOrder.get(2));
            assertThat(fcstVertices).hasSize(3);
            assertThat(forecast.get("properties").get("points").asInt())
                    .as("đỉnh nối không đếm vào properties.points")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName(
                "TC-06: phân tách track_type — 3 historical + 2 forecast, không mốc nào lẫn nhánh")
        void shouldSplitTracksByType() throws Exception {
            seedStormWithFullTrack();

            JsonNode root = readJson(get(TRACK_URL));
            List<String> types = new ArrayList<>();
            root.get("features")
                    .forEach(
                            f -> {
                                JsonNode t = f.get("properties").get("track_type");
                                if (t != null) {
                                    types.add(t.asText());
                                }
                            });

            assertThat(types).hasSize(5);
            assertThat(types.subList(0, 3)).containsOnly("historical");
            assertThat(types.subList(3, 5)).containsOnly("forecast");
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT count(*) FROM storm_tracks WHERE storm_id = ?",
                                    Integer.class,
                                    ACTIVE_ID))
                    .isEqualTo(5);
        }

        @Test
        @DisplayName(
                "TC-07 / SYS-RULE-004: mốc NULL gió/giật/áp suất → khóa hiện diện null, không 0, không mất khóa")
        void shouldExposeNullWindFieldsHonestly() throws Exception {
            seedStormWithFullTrack();

            JsonNode root = readJson(get(TRACK_URL));
            List<String> keys = pointContractKeys();
            int nullRowsChecked = 0;
            for (JsonNode feature : root.get("features")) {
                if (!feature.get("id").asText().startsWith("track-point-")) {
                    continue;
                }
                JsonNode props = feature.get("properties");
                assertThat(keysOf(props))
                        .as("hợp đồng 9 khóa của %s", feature.get("id").asText())
                        .containsExactlyElementsOf(keys);
                if ("forecast".equals(props.get("track_type").asText())) {
                    for (String nullable : List.of("wind_speed", "wind_gust", "pressure")) {
                        assertThat(props.get(nullable).isNull())
                                .as("%s phải null, nhận: %s", nullable, props.get(nullable))
                                .isTrue();
                    }
                    nullRowsChecked++;
                }
            }
            assertThat(nullRowsChecked)
                    .as("phải kiểm đúng 2 mốc forecast NULL gió theo V6")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("TC-08: có impact_polygon thật → feature impact-zone với ring đóng")
        void shouldReturnImpactZoneWhenPolygonExists() throws Exception {
            seedStormWithFullTrack();

            JsonNode impact = featureById(readJson(get(TRACK_URL)), "impact-zone");

            assertThat(impact.get("geometry").get("type").asText()).isEqualTo("Polygon");
            JsonNode ring = impact.get("geometry").get("coordinates").get(0);
            assertThat(ring.get(0).get(0).asDouble())
                    .isEqualTo(ring.get(ring.size() - 1).get(0).asDouble());
            assertThat(ring.get(0).get(1).asDouble())
                    .isEqualTo(ring.get(ring.size() - 1).get(1).asDouble());
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT ST_IsClosed(impact_polygon) FROM storm_tracks"
                                            + " WHERE storm_id = ? AND impact_polygon IS NOT NULL",
                                    Boolean.class,
                                    ACTIVE_ID))
                    .isTrue();
        }

        @Test
        @DisplayName(
                "TC-08: xoá impact_polygon → impact-zone biến mất, vẫn 200, các feature khác nguyên vẹn (không bịa buffer)")
        void shouldDropImpactZoneWhenPolygonCleared() throws Exception {
            seedStormWithFullTrack();
            jdbcTemplate.update(
                    "UPDATE storm_tracks SET impact_polygon = NULL WHERE storm_id = ?", ACTIVE_ID);

            JsonNode root = readJson(get(TRACK_URL));

            assertThat(featureIds(root.get("features")))
                    .as("không suy vùng nguy hiểm từ impact_radius_km default 100.0")
                    .doesNotContain("impact-zone");
            assertThat(root.get("features")).hasSize(7); // 2 LineString + 5 Point
        }

        @Test
        @DisplayName("TC-09: mã sự kiện không tồn tại → 404 RFC 7807 STM_404_NOT_FOUND")
        void shouldReturnProblemDetailForUnknownTrackId() throws Exception {
            seedStormWithFullTrack();

            mockMvc.perform(
                            get(STORMS_URL + "/khong-ton-tai-999/track")
                                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Tài nguyên không tồn tại"))
                    .andExpect(jsonPath("$.detail").value("Không tìm thấy bão: khong-ton-tai-999"))
                    .andExpect(jsonPath("$.errorCode").value("STM_404_NOT_FOUND"))
                    .andExpect(
                            jsonPath("$.instance").value(STORMS_URL + "/khong-ton-tai-999/track"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("TC-10: bão tồn tại nhưng 0 mốc → 200 + features: [] (không 404, không 500)")
        void shouldReturnEmptyTrackCollectionForStormWithoutTrack() throws Exception {
            seedTwoStorms();

            mockMvc.perform(get(STORMS_URL + "/" + DISSIPATED_ID + "/track"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("FeatureCollection"))
                    .andExpect(jsonPath("$.features.length()").value(0));
        }

        @Test
        @DisplayName("TC-11: bão chỉ 1 mốc historical → không LineString nào, vẫn đúng 1 Point")
        void shouldOmitLinesWhenOnlyOneTrackExists() throws Exception {
            seedTwoStorms();
            stormTrackRepository.insertTrackPointIfAbsent(
                    ACTIVE_ID,
                    "historical",
                    OffsetDateTime.now(ZoneOffset.UTC),
                    116.5,
                    18.4,
                    35.0,
                    45.0,
                    965.0,
                    "Typhoon",
                    150.0);

            JsonNode root = readJson(get(TRACK_URL));

            assertThat(root.get("features")).hasSize(1);
            assertThat(root.get("features").get(0).get("id").asText()).startsWith("track-point-");
            assertThat(root.get("features").get(0).get("geometry").get("type").asText())
                    .isEqualTo("Point");
        }

        @Test
        @DisplayName(
                "TC-12: coordinates[0]=ST_X=kinh độ, [1]=ST_Y=vĩ độ; SRID 4326 mọi geometry; bbox Biển Đông")
        void shouldMatchPostGisCoordinatesForAllTrackGeometries() throws Exception {
            seedStormWithFullTrack();

            JsonNode historyCoords =
                    readJson(get(TRACK_URL))
                            .get("features")
                            .get(0)
                            .get("geometry")
                            .get("coordinates");

            Double firstLng =
                    jdbcTemplate.queryForObject(
                            "SELECT ST_X(geom) FROM storm_tracks WHERE storm_id = ?"
                                    + " AND track_type = 'historical'"
                                    + " ORDER BY forecast_time ASC LIMIT 1",
                            Double.class,
                            ACTIVE_ID);
            Double firstLat =
                    jdbcTemplate.queryForObject(
                            "SELECT ST_Y(geom) FROM storm_tracks WHERE storm_id = ?"
                                    + " AND track_type = 'historical'"
                                    + " ORDER BY forecast_time ASC LIMIT 1",
                            Double.class,
                            ACTIVE_ID);

            assertThat(historyCoords.get(0).get(0).asDouble()).isCloseTo(firstLng, within(1e-9));
            assertThat(historyCoords.get(0).get(1).asDouble()).isCloseTo(firstLat, within(1e-9));
            assertThat(historyCoords.get(0).get(0).asDouble())
                    .as("kinh độ Biển Đông luôn lớn hơn vĩ độ — đảo trật tự là hỏng quỹ đạo")
                    .isGreaterThan(historyCoords.get(0).get(1).asDouble());

            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT count(*) FROM storm_tracks WHERE storm_id = ?"
                                            + " AND ST_SRID(geom) = 4326",
                                    Integer.class,
                                    ACTIVE_ID))
                    .isEqualTo(5);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT ST_SRID(impact_polygon) FROM storm_tracks WHERE storm_id = ?"
                                            + " AND impact_polygon IS NOT NULL",
                                    Integer.class,
                                    ACTIVE_ID))
                    .isEqualTo(4326);

            for (JsonNode vertex : historyCoords) {
                assertThat(vertex.get(0).asDouble()).isBetween(100.0, 128.0);
                assertThat(vertex.get(1).asDouble()).isBetween(0.0, 28.0);
            }
        }

        @Test
        @DisplayName("TC-14: CORS cho frontend :5173 trên endpoint track")
        void shouldExposeCorsHeaderOnTrack() throws Exception {
            seedStormWithFullTrack();

            mockMvc.perform(
                            get(TRACK_URL)
                                    .header("Origin", "http://localhost:5173")
                                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(
                            header().string(
                                            "Access-Control-Allow-Origin",
                                            "http://localhost:5173"));
        }

        @Test
        @DisplayName("Hồi quy W3-01: /track không phá total_track_points của /storms/{id}")
        void shouldNotDisturbDetailTrackCount() throws Exception {
            seedStormWithFullTrack();

            mockMvc.perform(get(STORMS_URL + "/" + ACTIVE_ID).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalTrackPoints").value(5));
        }
    }

    // ------------------------------------------------------------------
    // Seed
    // ------------------------------------------------------------------

    /**
     * 3 mốc historical (gió có số) + 2 mốc forecast (NULL gió theo V6), polygon chỉ gắn ở mốc
     * historical cuối — đúng hình dạng dữ liệu W3-02 phải xử lý.
     */
    private void seedStormWithFullTrack() {
        seedTwoStorms();
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).minusHours(12);
        String[][] plan = {
            {"historical", "116.5", "18.4", "35.0", "45.0", "965.0", "Typhoon", "150.0"},
            {"historical", "112.2", "19.0", "38.0", "48.0", "960.0", "Typhoon", "150.0"},
            {"historical", "107.5", "20.6", "40.0", "50.0", "955.0", "Typhoon", "150.0"},
            {"forecast", "106.3", "20.9", null, null, null, "HU", "100.0"},
            {"forecast", "105.3", "21.1", null, null, null, "HU", "100.0"},
        };
        for (int i = 0; i < plan.length; i++) {
            String[] row = plan[i];
            stormTrackRepository.insertTrackPointIfAbsent(
                    ACTIVE_ID,
                    row[0],
                    base.plusHours(6L * i),
                    Double.parseDouble(row[1]),
                    Double.parseDouble(row[2]),
                    row[3] == null ? null : Double.valueOf(row[3]),
                    row[4] == null ? null : Double.valueOf(row[4]),
                    row[5] == null ? null : Double.valueOf(row[5]),
                    row[6],
                    Double.parseDouble(row[7]));
        }
        // Ingestion GDACS không ghi impact_polygon — seed SQL mô phỏng nguồn có polygon (TC-08).
        // PostgreSQL không hỗ trợ UPDATE ... ORDER BY/LIMIT, nên chọn id bằng subquery.
        jdbcTemplate.update(
                "UPDATE storm_tracks SET impact_polygon ="
                        + " ST_MakeEnvelope(105.0, 18.0, 110.0, 22.0, 4326)"
                        + " WHERE id = (SELECT id FROM storm_tracks WHERE storm_id = ?"
                        + " AND track_type = 'historical' ORDER BY forecast_time DESC LIMIT 1)",
                ACTIVE_ID);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM storm_tracks WHERE impact_polygon IS NOT NULL",
                                Integer.class))
                .as("seed phải để lại đúng 1 mốc có polygon")
                .isEqualTo(1);
    }

    private List<String> pointContractKeys() {
        return List.of(
                "track_id",
                "storm_id",
                "track_type",
                "forecast_time",
                "wind_speed",
                "wind_gust",
                "pressure",
                "category",
                "impact_radius_km");
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

    private JsonNode featureById(JsonNode root, String id) {
        for (JsonNode f : root.get("features")) {
            if (id.equals(f.get("id").asText())) {
                return f;
            }
        }
        throw new AssertionError("Feature " + id + " vắng mặt trong collection");
    }

    private List<List<Double>> verticesOf(JsonNode coordinates) {
        List<List<Double>> vertices = new ArrayList<>();
        coordinates.forEach(c -> vertices.add(List.of(c.get(0).asDouble(), c.get(1).asDouble())));
        return vertices;
    }

    private void seedTwoStorms() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        stormRepository.save(
                storm(
                        ACTIVE_ID,
                        "TEST STORM A",
                        2026,
                        "Typhoon",
                        40.0,
                        960.0,
                        "active",
                        now,
                        ACTIVE_LNG,
                        ACTIVE_LAT));
        stormRepository.save(
                storm(
                        DISSIPATED_ID,
                        "TEST STORM B",
                        2025,
                        "Tropical Storm",
                        15.0,
                        null,
                        "dissipated",
                        now.minusDays(1),
                        112.0,
                        15.0));
    }

    private StormEntity storm(
            String id,
            String name,
            int year,
            String category,
            double maxWindSpeedMs,
            Double minPressure,
            String status,
            OffsetDateTime updatedAt,
            double lng,
            double lat) {
        StormEntity storm = new StormEntity();
        storm.setId(id);
        storm.setName(name);
        storm.setYear(year);
        storm.setCategory(category);
        storm.setMaxWindSpeed(maxWindSpeedMs);
        storm.setMinPressure(minPressure);
        storm.setStatus(status);
        storm.setStartDate(updatedAt.minusDays(30));
        storm.setEndDate("dissipated".equals(status) ? updatedAt.minusDays(20) : null);
        storm.setGeomCurrent(geometryFactory.createPoint(new Coordinate(lng, lat)));
        storm.setRawMetadata("{\"gdacs\":\"payload nội bộ không được lộ ra API\"}");
        storm.setUpdatedAt(updatedAt);
        return storm;
    }

    private void seedThreeTrackPoints() {
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).minusHours(12);
        for (int i = 0; i < 3; i++) {
            stormTrackRepository.insertTrackPointIfAbsent(
                    ACTIVE_ID,
                    "historical",
                    base.plusHours(6L * i),
                    ACTIVE_LNG + i * 0.5,
                    ACTIVE_LAT - i * 0.5,
                    40.0,
                    50.0,
                    960.0,
                    "Typhoon",
                    120.0);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private List<String> expectedContractKeys() {
        return List.of(
                "id",
                "name",
                "year",
                "category",
                "max_wind_speed",
                "max_wind_speed_kmh",
                "min_pressure",
                "status",
                "start_date",
                "end_date",
                "updated_at");
    }

    private JsonNode readJson(MockHttpServletRequestBuilder request) throws Exception {
        String body =
                new String(
                        mockMvc.perform(request.accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsByteArray(),
                        StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private List<String> statusesOf(JsonNode collection) {
        List<String> statuses = new ArrayList<>();
        collection
                .get("features")
                .forEach(f -> statuses.add(f.get("properties").get("status").asText()));
        return statuses;
    }

    private List<String> idsOf(JsonNode features) {
        List<String> ids = new ArrayList<>();
        features.forEach(f -> ids.add(f.get("id").asText()));
        return ids;
    }

    private List<String> keysOf(JsonNode properties) {
        List<String> keys = new ArrayList<>();
        properties.fieldNames().forEachRemaining(keys::add);
        return keys;
    }
}
