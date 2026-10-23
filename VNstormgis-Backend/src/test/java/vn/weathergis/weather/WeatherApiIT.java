package vn.weathergis.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.weathergis.common.AbstractPostGisIntegrationTest;

/**
 * Integration Test tầng đọc API thời tiết (Task W2-07) trên PostGIS thật:
 *
 * <pre>
 *   MockMvc → WeatherController → WeatherServiceImpl → WeatherRecordQueryRepository (native SQL)
 *     → PostgreSQL 16 + PostGIS 3.4 (Testcontainers, Flyway V1→V6)
 * </pre>
 *
 * Mục tiêu: DISTINCT ON trả đúng bản ghi mới nhất từng trạm, tọa độ [lng, lat] khớp ST_X/ST_Y của
 * seed V3, history đúng cửa sổ và thứ tự, lỗi RFC 7807, collection rỗng không crash, và planner
 * dùng chỉ mục tổng hợp idx_weather_records_loc_time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("WeatherApiIT — DISTINCT ON, history & RFC 7807 trên PostGIS (W2-07)")
class WeatherApiIT extends AbstractPostGisIntegrationTest {

    private static final String CURRENT_URL = "/api/v1/weather/current";
    private static final String HISTORY_URL = "/api/v1/weather/history";
    private static final double HAN_LNG = 105.8542;
    private static final double HAN_LAT = 21.0285;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanWeatherRecords() {
        jdbcTemplate.update("DELETE FROM weather_records");
    }

    // ------------------------------------------------------------------
    // TC-04 / TC-06: GeoJSON + DISTINCT ON lấy bản ghi mới nhất
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "TC-04/TC-06: chỉ trả 1 Feature/trạm và đúng bản ghi mới nhất (bản 30 phút trước bị loại)")
    void shouldReturnOnlyLatestRecordPerStation() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        insertRecord("HAN", now.minusMinutes(30), 20.0, 0.0, "Clouds", "nhiều mây");
        insertRecord("HAN", now.minusMinutes(5), 30.5, 18.5, "Rain", "mưa rào");
        insertRecord("DNG", now.minusMinutes(10), 27.0, 2.0, "Rain", "mưa nhỏ");

        String body =
                new String(
                        mockMvc.perform(get(CURRENT_URL).accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                                .andExpect(jsonPath("$.success").doesNotExist())
                                .andReturn()
                                .getResponse()
                                .getContentAsByteArray(),
                        StandardCharsets.UTF_8);
        JsonNode collection = objectMapper.readTree(body);

        assertThat(collection.get("features")).hasSize(2);

        JsonNode hanoi = findFeature(collection, "HAN");
        assertThat(hanoi.get("type").asText()).isEqualTo("Feature");
        assertThat(hanoi.get("geometry").get("type").asText()).isEqualTo("Point");
        assertThat(hanoi.get("properties").get("temperature").asDouble()).isEqualTo(30.5);
        assertThat(hanoi.get("properties").get("rain_1h").asDouble()).isEqualTo(18.5);
        assertThat(hanoi.get("properties").get("severity_rain").asText()).isEqualTo("WARNING");
        assertThat(hanoi.get("properties").get("weather_description").asText())
                .isEqualTo("mưa rào");
        assertThat(hanoi.get("properties").has("raw_data")).isFalse();

        OffsetDateTime recordedAt =
                OffsetDateTime.parse(hanoi.get("properties").get("recorded_at").asText());
        assertThat(recordedAt)
                .as("DISTINCT ON phải chọn bản ghi 5 phút trước, không phải 30 phút")
                .isCloseTo(now.minusMinutes(5), within(2, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName(
            "TC-13: coordinates[0] = ST_X = kinh độ, coordinates[1] = ST_Y = vĩ độ (không hoán đổi)")
    void shouldSerializeCoordinatesAsLngLatMatchingPostGis() throws Exception {
        insertRecord("HAN", OffsetDateTime.now(ZoneOffset.UTC), 28.5, 0.0, "Clear", "trời trong");

        JsonNode collection = readJson(get(CURRENT_URL));
        JsonNode coordinates = findFeature(collection, "HAN").get("geometry").get("coordinates");

        Double dbLng =
                jdbcTemplate.queryForObject(
                        "SELECT ST_X(geom) FROM locations WHERE code = 'HAN'", Double.class);
        Double dbLat =
                jdbcTemplate.queryForObject(
                        "SELECT ST_Y(geom) FROM locations WHERE code = 'HAN'", Double.class);
        assertThat(dbLng).isNotNull();
        assertThat(dbLat).isNotNull();

        assertThat(coordinates.get(0).asDouble()).isCloseTo(dbLng, within(1e-9));
        assertThat(coordinates.get(1).asDouble()).isCloseTo(dbLat, within(1e-9));
        assertThat(coordinates.get(0).asDouble()).isCloseTo(HAN_LNG, within(1e-9));
        assertThat(coordinates.get(1).asDouble()).isCloseTo(HAN_LAT, within(1e-9));
        assertThat(coordinates.get(0).asDouble())
                .as("Kinh độ Hà Nội phải lớn hơn vĩ độ — phát hiện hoán đổi lat/lng")
                .isGreaterThan(coordinates.get(1).asDouble());
    }

    // ------------------------------------------------------------------
    // TC-07 / TC-10: history
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "TC-07: history 'han' → HAN, tăng dần, chỉ gồm 2 mốc trong 24h (loại mốc 30h và trạm khác)")
    void shouldReturnAscendingHistoryWithinWindow() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        insertRecord("HAN", now.minusHours(30), 20.0, 50.0, "Rain", "ngoài cửa sổ");
        insertRecord("HAN", now.minusHours(2), 27.0, 2.0, "Rain", "mưa nhỏ");
        insertRecord("HAN", now.minusHours(1), 28.0, 18.5, "Rain", "mưa rào");
        insertRecord("DNG", now.minusHours(1), 30.0, 5.0, "Rain", "mưa nhỏ");

        String body =
                new String(
                        mockMvc.perform(
                                        get(HISTORY_URL)
                                                .param("location_code", "han")
                                                .param("hours", "24")
                                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.code").value("HAN"))
                                .andExpect(jsonPath("$.data.hours").value(24))
                                .andExpect(jsonPath("$.data.count").value(2))
                                .andExpect(jsonPath("$.data.readings.length()").value(2))
                                .andReturn()
                                .getResponse()
                                .getContentAsByteArray(),
                        StandardCharsets.UTF_8);

        JsonNode readings = objectMapper.readTree(body).get("data").get("readings");
        OffsetDateTime first = OffsetDateTime.parse(readings.get(0).get("recordedAt").asText());
        OffsetDateTime second = OffsetDateTime.parse(readings.get(1).get("recordedAt").asText());
        assertThat(first).isBefore(second);
        assertThat(readings.get(0).get("rain1h").asDouble()).isEqualTo(2.0);
        assertThat(readings.get(1).get("rain1h").asDouble()).isEqualTo(18.5);
    }

    @Test
    @DisplayName("TC-10: trạm hợp lệ nhưng không có bản ghi trong cửa sổ → 200, readings rỗng")
    void shouldReturnEmptyReadingsForStationWithoutDataInWindow() throws Exception {
        insertRecord(
                "HAN",
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(30),
                20.0,
                0.0,
                "Clear",
                "trời trong");

        mockMvc.perform(
                        get(HISTORY_URL)
                                .param("location_code", "HAN")
                                .param("hours", "24")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.readings.length()").value(0));
    }

    @Test
    @DisplayName("TC-16: DB chưa có bản ghi thời tiết → 200 FeatureCollection rỗng, không crash")
    void shouldReturnEmptyFeatureCollectionWhenNoWeatherRecords() throws Exception {
        mockMvc.perform(get(CURRENT_URL).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(0));
    }

    // ------------------------------------------------------------------
    // TC-08 / TC-09: RFC 7807
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TC-08: mã trạm không tồn tại → 404 RFC 7807 WTH_404_NOT_FOUND")
    void shouldReturnProblemDetailForUnknownStation() throws Exception {
        mockMvc.perform(
                        get(HISTORY_URL)
                                .param("location_code", "XXX")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("WTH_404_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Không tìm thấy trạm: XXX"))
                .andExpect(jsonPath("$.instance").value(HISTORY_URL))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("TC-09: hours ngoài 1..168 → 400 RFC 7807 COM_400_INVALID_PARAM")
    void shouldReturnProblemDetailForInvalidHours() throws Exception {
        mockMvc.perform(
                        get(HISTORY_URL)
                                .param("location_code", "HAN")
                                .param("hours", "169")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("COM_400_INVALID_PARAM"))
                .andExpect(
                        jsonPath("$.detail").value("Tham số hours phải nằm trong phạm vi 1..168"));
    }

    // ------------------------------------------------------------------
    // TC-11: planner dùng idx_weather_records_loc_time ở quy mô 45.696 dòng
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "TC-11: DISTINCT ON @ 68×672 dòng dùng idx_weather_records_loc_time (không Seq Scan bảng)")
    void shouldUseCompositeIndexForDistinctOn() {
        jdbcTemplate.execute(
                """
                INSERT INTO weather_records (location_id, recorded_at, temperature, feels_like,
                    humidity, pressure, wind_speed, wind_direction, weather_condition,
                    weather_description)
                SELECT l.id, now() - (g || ' minutes')::interval,
                       25 + random() * 5, 27, 70, 1008, 1 + random() * 10, 0,
                       'Clouds', 'nhiều mây'
                FROM locations l, generate_series(1, 672) g
                WHERE l.is_active = true
                """);
        jdbcTemplate.execute("ANALYZE weather_records");

        Integer rows =
                jdbcTemplate.queryForObject("SELECT count(*) FROM weather_records", Integer.class);
        assertThat(rows).isEqualTo(68 * 672);

        String plan = explainDistinctOnQuery();

        assertThat(plan)
                .as("Plan phải dùng chỉ mục tổng hợp idx_weather_records_loc_time")
                .contains("idx_weather_records_loc_time");
        assertThat(plan)
                .as("Không được Seq Scan toàn bảng weather_records")
                .doesNotContain("Seq Scan on weather_records");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void insertRecord(
            String code,
            OffsetDateTime recordedAt,
            double temperature,
            double rain1h,
            String condition,
            String description) {
        jdbcTemplate.update(
                """
                INSERT INTO weather_records (location_id, recorded_at, temperature, feels_like,
                    humidity, pressure, wind_speed, wind_direction, wind_gust, rain_1h, rain_3h,
                    weather_condition, weather_description)
                VALUES ((SELECT id FROM locations WHERE code = ?), ?, ?, ?, 70, 1008,
                        5.0, 120, 8.0, ?, 0, ?, ?)
                """,
                code,
                recordedAt,
                temperature,
                temperature + 3,
                rain1h,
                condition,
                description);
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

    private JsonNode findFeature(JsonNode collection, String code) {
        for (JsonNode feature : collection.get("features")) {
            if (code.equals(feature.get("id").asText())) {
                return feature;
            }
        }
        throw new AssertionError("Không tìm thấy Feature với id = " + code);
    }

    /** EXPLAIN trong MỘT connection — SET phiên không rò rỉ sang connection khác của Hikari. */
    private String explainDistinctOnQuery() {
        return jdbcTemplate.execute(
                (ConnectionCallback<String>)
                        connection -> {
                            try (Statement statement = connection.createStatement()) {
                                statement.execute("SET enable_seqscan = off");
                                try (ResultSet rs =
                                        statement.executeQuery(
                                                """
                                                EXPLAIN (COSTS OFF)
                                                SELECT DISTINCT ON (w.location_id)
                                                    l.id AS "locationId", l.code AS "code",
                                                    l.name AS "name", l.region AS "region",
                                                    ST_X(l.geom) AS "longitude",
                                                    ST_Y(l.geom) AS "latitude",
                                                    w.temperature AS "temperature",
                                                    w.feels_like AS "feelsLike",
                                                    w.humidity AS "humidity",
                                                    w.pressure AS "pressure",
                                                    w.wind_speed AS "windSpeed",
                                                    w.wind_direction AS "windDirection",
                                                    w.wind_gust AS "windGust",
                                                    w.rain_1h AS "rain1h", w.rain_3h AS "rain3h",
                                                    w.weather_condition AS "weatherCondition",
                                                    w.weather_description AS "weatherDescription",
                                                    w.recorded_at AS "recordedAt"
                                                FROM locations l
                                                INNER JOIN weather_records w ON l.id = w.location_id
                                                WHERE l.is_active = true
                                                ORDER BY w.location_id, w.recorded_at DESC
                                                """)) {
                                    StringBuilder plan = new StringBuilder();
                                    while (rs.next()) {
                                        plan.append(rs.getString(1)).append('\n');
                                    }
                                    return plan.toString();
                                } finally {
                                    statement.execute("SET enable_seqscan = on");
                                }
                            }
                        });
    }
}
