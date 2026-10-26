package vn.weathergis.storm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    // Seed
    // ------------------------------------------------------------------

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
