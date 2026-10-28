package vn.weathergis.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import vn.weathergis.common.AbstractPostGisIntegrationTest;
import vn.weathergis.storm.domain.StormEntity;
import vn.weathergis.storm.repository.StormRepository;
import vn.weathergis.storm.service.StormService;

/**
 * Integration Test tầng Redis cache (Task W3-03, TC-15) chạy trên <b>Redis 7.2-alpine thật</b> đặt
 * cạnh PostGIS singleton của suite:
 *
 * <pre>
 *   MockMvc → Controller → GeoJsonCacheService → RedisCacheManager (Lettuce)
 *     → redis:7.2-alpine (Testcontainers)          PostgreSQL 16 + PostGIS 3.4 (singleton)
 * </pre>
 *
 * Ba bằng chứng mà unit test không thể đưa ra: (1) giá trị nằm trong Redis là chuỗi GeoJSON hợp lệ,
 * <b>byte-identical</b> với lượt render trực tiếp (RSK-W3-02, quyết định #1); (2) NullNode D-1 —
 * khóa {@code min_pressure}/{@code end_date} null — sống sót trọn round-trip serialize của cache
 * (quyết định #6); (3) cache-hit không chạm PostGIS (quyết định {@code times(1)}) — tiền đề "giảm ≥
 * 95% truy vấn" của DoD Mục 7.
 *
 * <p>Cờ {@code weathergis.cache.enabled} bật lên bằng {@link
 * AbstractPostGisIntegrationTest#setRedisCacheEnabled(boolean)} chứ không phải
 * {@code @DynamicPropertySource} trùng khóa ở subclass: Spring gọi các method
 * {@code @DynamicPropertySource} theo trật tự subclass trước — superclass sau ( {@code
 * ReflectionUtils.doWithMethods} đi ngược cây kế thừa) và customizer của spring-test ghi đè
 * property theo khóa, nên đăng ký lại cùng khóa chỉ có thể <b>thua</b> base class. Đây là sai khác
 * so với ghi chú "subclass thắng" ở quyết định #11 — xem {@code PROGRESS.md} §5.1 (W3-03 D-2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName(
        "RedisCacheIT — cache Redis 7.2 thật: hit byte-identical, TTL 3 vùng, phạm vi cache (W3-03)")
class RedisCacheIT extends AbstractPostGisIntegrationTest {

    private static final String STORMS_URL = "/api/v1/storms";
    private static final String WEATHER_URL = "/api/v1/weather/current";
    private static final String LOCATIONS_URL = "/api/v1/locations";
    private static final String ACTIVE_ID = "teststorm01";

    private static final String CACHE_KEY_SCAN = "weathergis:cache:*";
    private static final String WEATHER_KEY = "weathergis:cache:weather_current::geojson";
    private static final String STORMS_KEY = "weathergis:cache:active_storms::geojson:active";
    private static final String LOCATIONS_ALL_KEY = "weathergis:cache:locations::geojson||true|";
    private static final String LOCATIONS_NORTH_KEY =
            "weathergis:cache:locations::geojson|Bắc Bộ|true|";

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StormRepository stormRepository;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private RedisConnectionFactory connectionFactory;
    @Autowired private GeoJsonCacheService geoJsonCacheService;

    /**
     * Spy để chứng minh cache-hit không phát truy vấn bão lần thứ hai (mục 3.10: service bất biến).
     */
    @SpyBean private StormService stormService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    private long fallbacksBefore;

    @BeforeAll
    static void startRedisAndEnableCache() {
        REDIS.start();
        setRedisCacheEnabled(true);
    }

    @AfterAll
    static void disableCacheAndStopRedis() {
        setRedisCacheEnabled(false); // trả nguyên trạng cho 186 test còn lại chạy sau
        REDIS.stop();
    }

    /**
     * Hai khóa này base class không đăng ký nên {@code @DynamicPropertySource} của subclass dùng
     * được — không có xung đột precedence. Supplier đọc lúc context khởi tạo, tức sau
     * {@code @BeforeAll}, nên container đã có host/port.
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getFirstMappedPort());
    }

    @BeforeEach
    void startFromCleanRedisAndKnownStorm() {
        redisTemplate.execute(
                (RedisCallback<Void>)
                        connection -> {
                            connection.serverCommands().flushDb();
                            return null;
                        });
        // Seed có chủ đích 1 bão active thiếu min_pressure: hợp đồng 11 khóa + NullNode D-1 (W3-01)
        // phải đi qua Redis mà không mất khóa nào. DELETE cần thiết vì storms là bảng dùng chung
        // suite.
        jdbcTemplate.update("DELETE FROM storms"); // FK cascade dọn storm_tracks
        stormRepository.save(activeStormWithoutPressure());
        fallbacksBefore = geoJsonCacheService.getFallbackCount();
    }

    // ------------------------------------------------------------------
    // Tiền đề hạ tầng
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "Tiền đề: Lettuce trỏ đúng container Testcontainers, không âm thầm dùng Redis localhost:6379")
    void shouldTargetTestcontainersRedis() {
        assertThat(connectionFactory).isInstanceOf(LettuceConnectionFactory.class);

        LettuceConnectionFactory lettuce = (LettuceConnectionFactory) connectionFactory;

        assertThat(lettuce.getHostName()).isEqualTo(REDIS.getHost());
        assertThat(lettuce.getPort()).isEqualTo(REDIS.getFirstMappedPort());
    }

    // ------------------------------------------------------------------
    // TC-15 chủ chốt: hit byte-identical + key đúng vùng + TTL 15 phút
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "TC-15/TC-04/TC-05: /weather/current lượt 2 phục vụ từ Redis — byte-identical, key đúng vùng, TTL 15m")
    void shouldServeWeatherCurrentFromRedisByteIdentically() throws Exception {
        String miss = geoJson(get(WEATHER_URL));
        String hit = geoJson(get(WEATHER_URL));

        assertThat(hit)
                .as("RSK-W3-02: cache-hit phải giống hệt lượt render trực tiếp, kể cả thứ tự byte")
                .isEqualTo(miss);
        assertThat(redisTemplate.hasKey(WEATHER_KEY)).isTrue();
        assertThat(redisTemplate.opsForValue().get(WEATHER_KEY))
                .as("giá trị Redis là chuỗi JSON nguyên văn, không phải blob Java serialization")
                .isEqualTo(miss);
        assertThat(readTree(miss).get("type").asText()).isEqualTo("FeatureCollection");
        assertTtl(WEATHER_KEY, 840L, 900L);
        assertNoFallback();
    }

    // ------------------------------------------------------------------
    // Quyết định #6: NullNode D-1 sống sót round-trip Redis
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "TC-15/TC-06: NullNode D-1 qua Redis — miss & hit cùng giữ 11 khóa, min_pressure/end_date vẫn null, TTL 30m")
    void shouldPreserveNullContractAcrossCacheRoundTrip() throws Exception {
        String miss = geoJson(get(STORMS_URL));
        String hit = geoJson(get(STORMS_URL));

        assertThat(hit).isEqualTo(miss);

        for (String body : List.of(miss, hit)) {
            JsonNode properties = readTree(body).get("features").get(0).get("properties");

            assertThat(keysOf(properties))
                    .as("11 khóa hợp đồng không được mất khóa nào sau round-trip serialize")
                    .containsExactlyElementsOf(expectedContractKeys());
            assertThat(properties.get("min_pressure").isNull())
                    .as("SYS-RULE-004: null thật vẫn là null, không bị số hóa hay nuốt mất khóa")
                    .isTrue();
            assertThat(properties.get("end_date").isNull()).isTrue();
            assertThat(properties.get("name").asText()).isEqualTo("TEST STORM A");
        }

        assertThat(redisTemplate.hasKey(STORMS_KEY)).isTrue();
        assertTtl(STORMS_KEY, 1740L, 1800L);
        assertNoFallback();
    }

    @Test
    @DisplayName(
            "TC-15 + DoD Mục 7: cache-hit không chạm PostGIS — service gọi đúng 1 lần cho 2 request")
    void shouldSkipDatabaseOnCacheHit() throws Exception {
        geoJson(get(STORMS_URL));
        geoJson(get(STORMS_URL));

        verify(stormService, times(1)).getStormsAsGeoJson("active");
    }

    // ------------------------------------------------------------------
    // Vùng locations: key tổ hợp filter, UTF-8, TTL 24h
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "TC-15/TC-08: /locations?format=geojson — 2 filter → 2 key tách biệt (UTF-8), TTL 24h, lặp filter không đẻ key")
    void shouldCacheLocationsPerFilterCombination() throws Exception {
        mockMvc.perform(get(LOCATIONS_URL).param("format", "geojson")).andExpect(status().isOk());
        mockMvc.perform(get(LOCATIONS_URL).param("format", "geojson").param("region", "Bắc Bộ"))
                .andExpect(status().isOk());

        assertThat(redisTemplate.keys(CACHE_KEY_SCAN))
                .as(
                        "mỗi tổ hợp filter một mục tử — hai truy vấn khác filter không dùng chung cache")
                .containsExactlyInAnyOrder(LOCATIONS_ALL_KEY, LOCATIONS_NORTH_KEY);
        assertTtl(LOCATIONS_ALL_KEY, 86340L, 86400L);

        mockMvc.perform(get(LOCATIONS_URL).param("format", "geojson").param("region", "Bắc Bộ"))
                .andExpect(status().isOk());
        String again = geoJson(get(LOCATIONS_URL).param("format", "geojson"));

        assertThat(redisTemplate.keys(CACHE_KEY_SCAN))
                .as("lặp lại hai filter đã gọi không được sinh key thứ ba")
                .hasSize(2);
        assertThat(readTree(again).get("features"))
                .as("V3 seed 68 trạm — cache phải trả dữ liệu thật, không rỗng")
                .isNotEmpty();
        assertNoFallback();
    }

    // ------------------------------------------------------------------
    // Phạm vi cache (quyết định #3): mọi đường khác phải để Redis trống
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "TC-15/TC-07: format=json, status=all, /storms/{id}, /track, /locations/{code}, /nearby — không đẻ key")
    void shouldNotCacheOutsideTheThreeGeoJsonRegions() throws Exception {
        mockMvc.perform(
                        get(STORMS_URL)
                                .param("format", "json")
                                .param("status", "all")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get(STORMS_URL).param("status", "all").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get(WEATHER_URL).param("format", "json").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get(STORMS_URL + "/" + ACTIVE_ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(get(STORMS_URL + "/" + ACTIVE_ID + "/track")).andExpect(status().isOk());
        mockMvc.perform(get(LOCATIONS_URL + "/code/HAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("HAN"));
        mockMvc.perform(
                        get(LOCATIONS_URL + "/nearby")
                                .param("lng", "105.8542")
                                .param("lat", "21.0285")
                                .param("radiusKm", "50"))
                .andExpect(status().isOk());

        assertThat(redisTemplate.keys(CACHE_KEY_SCAN))
                .as("chỉ 3 đường geojson được cache; phần còn lại đi thẳng PostGIS")
                .isNullOrEmpty();
        assertNoFallback();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String geoJson(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        byte[] body =
                mockMvc.perform(request.accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        return new String(body, StandardCharsets.UTF_8);
    }

    private JsonNode readTree(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private void assertTtl(String key, long lowerExclusive, long upperInclusive) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);

        assertThat(ttl).as("TTL của %s phải tồn tại", key).isNotNull();
        assertThat(ttl)
                .as("TTL của %s", key)
                .isGreaterThan(lowerExclusive)
                .isLessThanOrEqualTo(upperInclusive);
    }

    /** Fail-open không được che mờ kết quả: mọi request trong IT phải phục vụ qua Redis thật. */
    private void assertNoFallback() {
        assertThat(geoJsonCacheService.getFallbackCount())
                .as("[CACHE-FALLBACK#n] > 0 nghĩa là IT âm thầm truy vấn PostGIS thay vì Redis")
                .isEqualTo(fallbacksBefore);
    }

    private StormEntity activeStormWithoutPressure() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        StormEntity storm = new StormEntity();
        storm.setId(ACTIVE_ID);
        storm.setName("TEST STORM A");
        storm.setYear(2026);
        storm.setCategory("Typhoon");
        storm.setMaxWindSpeed(40.0);
        storm.setMinPressure(null); // D-1: GDACS không công bố áp suất
        storm.setStatus("active");
        storm.setStartDate(now.minusDays(3));
        storm.setEndDate(null);
        storm.setGeomCurrent(geometryFactory.createPoint(new Coordinate(116.4, 18.2)));
        storm.setRawMetadata("{\"gdacs\":\"payload nội bộ\"}");
        storm.setUpdatedAt(now);
        return storm;
    }

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

    private List<String> keysOf(JsonNode properties) {
        List<String> keys = new ArrayList<>();
        properties.fieldNames().forEachRemaining(keys::add);
        return keys;
    }
}
