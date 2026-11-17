package vn.weathergis.sit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import vn.weathergis.alert.ai.AlertContentGuardrail;
import vn.weathergis.alert.ai.GeminiGenerationResult.GeminiAlertDraft;
import vn.weathergis.alert.dto.AlertGenerationJobView;
import vn.weathergis.alert.service.AlertGenerationWorker;
import vn.weathergis.alert.service.AlertPersistenceService;
import vn.weathergis.common.AbstractPostGisIntegrationTest;

/**
 * Full-stack System Integration Testing (SIT) and Edge Cases suite (Task W6-02 / Master Timeline
 * §3.7). Exercises all 4 core subsystems end-to-end against real PostGIS 16 + PostGIS 3.4, real
 * Redis 7.2, and WireMock stubs for OWM, GDACS, and Gemini:
 *
 * <pre>
 *   1. Ingestion Engine (OWM + GDACS via /api/v1/internal/sync/*)
 *   2. Distributed Redis Cache (locations, weather_current, active_storms + selective eviction)
 *   3. Threshold Monitoring & Proactive AI Warning Engine (ST_DistanceSphere + Inbox + Worker + Guardrail)
 *   4. Passive Grounded AI Chatbot & Bucket4j Rate Limiter (/api/v1/chat/sessions/*)
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("SystemIntegrationAndEdgeCaseIT — Full 4-Module SIT & Edge Cases (W6-02)")
class SystemIntegrationAndEdgeCaseIT extends AbstractPostGisIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-18T02:00:00Z");
    private static final String MODEL = "w602-sit-model";
    private static final String GEMINI_PATH = "/v1beta/models/" + MODEL + ":generateContent";
    private static final String OWM_PATH = "/data/2.5/weather";
    private static final String GDACS_SEARCH_PATH = "/gdacsapi/api/Events/geteventlist/search";
    private static final String GDACS_TRACK_PATH = "/gdacsapi/api/polygons/getgeometry";
    private static final String YAGI_ID = "1001209";

    private static final String INTERNAL_SECRET = "w602-internal-secret";
    private static final String ADMIN_KEY = "w602-admin-key";
    private static final String GEMINI_KEY = "w602-gemini-key";
    private static final String OWM_KEY = "w602-owm-key";

    private static final String WEATHER_CACHE_KEY = "weathergis:cache:weather_current::geojson";
    private static final String STORMS_CACHE_KEY = "weathergis:cache:active_storms::geojson:active";
    private static final String LOCATIONS_CACHE_KEY = "weathergis:cache:locations::geojson||true|";

    private static final int REDIS_PORT = availablePort();
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379)
                    .withCreateContainerCmdModifier(
                            cmd ->
                                    cmd.getHostConfig()
                                            .withPortBindings(
                                                    new PortBinding(
                                                            Ports.Binding.bindPort(REDIS_PORT),
                                                            ExposedPort.tcp(6379))));

    private static final WireMockServer WIRE_MOCK = new WireMockServer(0);

    static {
        WIRE_MOCK.start();
    }

    @TestConfiguration
    static class SitClockConfig {
        @Bean
        @Primary
        Clock w602FixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @BeforeAll
    static void startContainers() {
        setRedisCacheEnabled(true);
        REDIS.start();
    }

    @AfterAll
    static void stopContainers() {
        setRedisCacheEnabled(false);
        if (REDIS.isRunning()) {
            REDIS.stop();
        }
        if (WIRE_MOCK.isRunning()) {
            WIRE_MOCK.stop();
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getFirstMappedPort());
        registry.add("spring.data.redis.timeout", () -> "500ms");
        registry.add("spring.data.redis.connect-timeout", () -> "1s");
        registry.add("weathergis.external.owm.base-url", WIRE_MOCK::baseUrl);
        registry.add("weathergis.external.owm.api-key", () -> OWM_KEY);
        registry.add("weathergis.external.owm.backoff-ms", () -> "0,0,0");
        registry.add("weathergis.external.gdacs.base-url", WIRE_MOCK::baseUrl);
        registry.add("weathergis.external.gdacs.backoff-ms", () -> "0,0,0");
        registry.add("weathergis.sync.throttle-ms", () -> "0");
        registry.add("weathergis.internal.sync-secret", () -> INTERNAL_SECRET);
        registry.add("weathergis.admin.api-key", () -> ADMIN_KEY);
        registry.add("weathergis.ai.gemini.enabled", () -> "true");
        registry.add("weathergis.ai.gemini.api-key", () -> GEMINI_KEY);
        registry.add("weathergis.ai.gemini.model", () -> MODEL);
        registry.add("weathergis.ai.gemini.base-url", WIRE_MOCK::baseUrl);
        registry.add("weathergis.ai.gemini.request-timeout", () -> "500ms");
        registry.add("weathergis.ai.gemini.connect-timeout", () -> "250ms");
        registry.add("weathergis.alert.worker.enabled", () -> "false");
        registry.add("weathergis.alert.worker.initial-delay-ms", () -> "3600000");
        registry.add("weathergis.chat.ratelimit.trusted-proxies", () -> "127.0.0.1");
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private AlertPersistenceService alertPersistence;
    @Autowired private AlertGenerationWorker alertWorker;
    @Autowired private AlertContentGuardrail alertGuardrail;

    private List<UUID> activeSeedStations;
    private List<Integer> enabledSeedThresholds;
    private List<String> activeSeedStorms;
    private final List<Integer> createdThresholdIds = new ArrayList<>();

    private UUID stationHan;
    private UUID stationDng;

    @BeforeEach
    void isolateEnvironment() {
        WIRE_MOCK.resetAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();

        activeSeedStations =
                jdbc.query(
                        "SELECT id FROM locations WHERE is_active",
                        (rs, i) -> rs.getObject(1, UUID.class));
        enabledSeedThresholds =
                jdbc.query(
                        "SELECT id FROM alert_thresholds WHERE is_enabled",
                        (rs, i) -> rs.getInt(1));
        activeSeedStorms =
                jdbc.query(
                        "SELECT id FROM storms WHERE status = 'active'",
                        (rs, i) -> rs.getString(1));

        jdbc.update("UPDATE locations SET is_active = false WHERE is_active");
        jdbc.update("UPDATE alert_thresholds SET is_enabled = false WHERE is_enabled");
        jdbc.update("UPDATE storms SET status = 'dissipated' WHERE status = 'active'");

        // Seed 2 dedicated SIT stations: W602_HAN (21.0285N, 105.8542E - within 200km of YAGI at
        // 20.7N, 107.2E) and W602_DNG (16.0544N, 108.2022E).
        stationHan = insertStation("W602_HAN", "Trạm W602 Hà Nội", 105.8542, 21.0285);
        stationDng = insertStation("W602_DNG", "Trạm W602 Đà Nẵng", 108.2022, 16.0544);
    }

    @AfterEach
    void restoreEnvironment() {
        jdbc.update(
                "DELETE FROM chat_messages WHERE session_id IN ("
                        + "SELECT id FROM chat_sessions WHERE title LIKE 'W602%')");
        jdbc.update("DELETE FROM chat_sessions WHERE title LIKE 'W602%'");
        jdbc.update(
                "DELETE FROM alert_violation_keys WHERE job_id IN ("
                        + "SELECT id FROM alert_generation_jobs WHERE location_id IN (?, ?))",
                stationHan,
                stationDng);
        jdbc.update(
                "UPDATE alert_generation_jobs SET alert_id = null WHERE location_id IN (?, ?)",
                stationHan,
                stationDng);
        jdbc.update("DELETE FROM alerts WHERE location_id IN (?, ?)", stationHan, stationDng);
        jdbc.update(
                "DELETE FROM alert_generation_jobs WHERE location_id IN (?, ?)",
                stationHan,
                stationDng);
        jdbc.update(
                "DELETE FROM weather_records WHERE location_id IN (?, ?)", stationHan, stationDng);
        jdbc.update("DELETE FROM locations WHERE id IN (?, ?)", stationHan, stationDng);
        for (Integer id : createdThresholdIds) {
            jdbc.update("DELETE FROM alert_thresholds WHERE id = ?", id);
        }
        createdThresholdIds.clear();
        jdbc.update("DELETE FROM storms WHERE id = ?", YAGI_ID);

        activeSeedStations.forEach(
                id -> jdbc.update("UPDATE locations SET is_active = true WHERE id = ?", id));
        enabledSeedThresholds.forEach(
                id ->
                        jdbc.update(
                                "UPDATE alert_thresholds SET is_enabled = true WHERE id = ?", id));
        activeSeedStorms.forEach(
                id -> jdbc.update("UPDATE storms SET status = 'active' WHERE id = ?", id));
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName(
            "SIT-01: Luồng liên hoàn 4 phân hệ — Admin Threshold → GDACS/OWM Sync → Cache Evict → PostGIS Alert → Chat")
    void sit01_FullFourModulePipeline_IngestionToCacheToProactiveAlertToGroundedChat()
            throws Exception {
        // 1. Tạo ngưỡng cảnh báo qua API Admin (POST /api/v1/alert-thresholds)
        int rainRuleId =
                createThresholdViaApi(
                        "W602 Mưa lớn", "rain_1h", ">=", 50.0, "DANGER", "Cảnh báo mưa lớn SIT");
        int stormDistRuleId =
                createThresholdViaApi(
                        "W602 Bão gần",
                        "storm_dist",
                        "<=",
                        250.0,
                        "CRITICAL",
                        "Cảnh báo bão áp sát SIT");

        // 2. Prime cache Redis trước khi nạp dữ liệu
        mvc.perform(get("/api/v1/weather/current"))
                .andExpect(res -> assertThat(res.getResponse().getStatus()).isEqualTo(200));
        mvc.perform(get("/api/v1/storms").param("status", "active"))
                .andExpect(res -> assertThat(res.getResponse().getStatus()).isEqualTo(200));
        assertThat(redis.hasKey(WEATHER_CACHE_KEY)).isTrue();
        assertThat(redis.hasKey(STORMS_CACHE_KEY)).isTrue();

        // 3. Nạp bão YAGI-24 qua POST /api/v1/internal/sync/storms (tâm [107.2, 20.7] ~145km tới
        // W602_HAN)
        stubGdacsSearch("search-yagi-active.json");
        stubGdacsYagiTrack();

        MvcResult stormSyncRes =
                mvc.perform(
                                post("/api/v1/internal/sync/storms")
                                        .header("X-Internal-Secret", INTERNAL_SECRET))
                        .andReturn();
        assertThat(stormSyncRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode stormSyncData = readJson(stormSyncRes).path("data");
        assertThat(stormSyncData.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(stormSyncData.path("stormsProcessed").asInt()).isEqualTo(1);
        assertThat(stormSyncData.path("trackPointsInserted").asInt()).isEqualTo(15);
        // Cache active_storms đã được evict sau khi sync thành công
        assertThat(redis.hasKey(STORMS_CACHE_KEY)).isFalse();

        // Kiểm chứng GET /api/v1/storms?status=active & GET /api/v1/storms/1001209/track
        MvcResult stormsGeoRes =
                mvc.perform(get("/api/v1/storms").param("status", "active")).andReturn();
        JsonNode stormsGeo = readJson(stormsGeoRes);
        assertThat(stormsGeo.path("features")).hasSize(1);
        assertThat(stormsGeo.path("features").get(0).path("properties").path("name").asText())
                .isEqualTo("Tropical Cyclone YAGI-24");
        assertThat(redis.hasKey(STORMS_CACHE_KEY)).isTrue();

        MvcResult trackRes = mvc.perform(get("/api/v1/storms/{id}/track", YAGI_ID)).andReturn();
        assertThat(trackRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(readJson(trackRes).path("features")).isNotEmpty();

        // 4. Nạp thời tiết OWM qua POST /api/v1/internal/sync/weather
        // W602_HAN: rain_1h = 84.5 mm (>= 50.0) VÀ khoảng cách tới YAGI ~145km (<= 250.0 km)
        // W602_DNG: rain_1h = 14.0 mm (< 50.0) và cách YAGI > 500km
        long obsEpoch = NOW.minusSeconds(180).getEpochSecond();
        stubOwmStation(21.0285, 105.8542, 84.5, 110.0, 18.5, 25.0, 90, obsEpoch);
        stubOwmStation(16.0544, 108.2022, 14.0, 22.0, 6.0, 9.5, 180, obsEpoch);

        MvcResult weatherSyncRes =
                mvc.perform(
                                post("/api/v1/internal/sync/weather")
                                        .header("X-Internal-Secret", INTERNAL_SECRET))
                        .andReturn();
        assertThat(weatherSyncRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode weatherSyncData = readJson(weatherSyncRes).path("data");
        assertThat(weatherSyncData.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(weatherSyncData.path("recordsProcessed").asInt()).isEqualTo(2);
        assertThat(redis.hasKey(WEATHER_CACHE_KEY)).isFalse();

        // 5. Kiểm chứng ThresholdEvaluationScheduler đã tự động sinh 1 job PENDING cho W602_HAN
        UUID jobId =
                jdbc.queryForObject(
                        "SELECT id FROM alert_generation_jobs WHERE location_id = ? AND status = 'PENDING'",
                        UUID.class,
                        stationHan);
        assertThat(jobId).isNotNull();
        AlertGenerationJobView pendingJob = alertPersistence.findJobById(jobId).orElseThrow();
        assertThat(pendingJob.violationKeys()).hasSize(2);
        assertThat(pendingJob.severityLevel()).isEqualTo("CRITICAL");
        assertThat(pendingJob.representativeThresholdId()).isEqualTo(stormDistRuleId);
        assertThat(pendingJob.factualSnapshot().violations())
                .extracting(v -> v.thresholdId())
                .containsExactlyInAnyOrder(rainRuleId, stormDistRuleId);

        // 6. Chạy AlertGenerationWorker với phản hồi Gemini hợp lệ theo ngữ pháp đóng W4-05
        GeminiAlertDraft validDraft = alertGuardrail.fallback(pendingJob);
        String alertDraftJson =
                mapper.writeValueAsString(
                        Map.of(
                                "title",
                                validDraft.title(),
                                "ai_explanation",
                                validDraft.aiExplanation()));
        stubGeminiText(alertDraftJson, 64);

        alertWorker.runBatch();

        AlertGenerationJobView completedJob = alertPersistence.findJobById(jobId).orElseThrow();
        assertThat(completedJob.status().name()).isEqualTo("COMPLETED");
        assertThat(completedJob.isFallback()).isFalse();
        UUID publishedAlertId = completedJob.alertId();
        assertThat(publishedAlertId).isNotNull();

        // 7. Kiểm chứng GET /api/v1/alerts/active & GET /api/v1/alerts/{id}
        MvcResult activeAlertsRes = mvc.perform(get("/api/v1/alerts/active")).andReturn();
        assertThat(activeAlertsRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode activeAlerts = readJson(activeAlertsRes);
        assertThat(activeAlerts.path("features"))
                .anySatisfy(
                        f -> {
                            assertThat(f.path("id").asText())
                                    .isEqualTo(publishedAlertId.toString());
                            assertThat(f.path("properties").path("severity_level").asText())
                                    .isEqualTo("CRITICAL");
                            assertThat(f.path("properties").path("location_code").asText())
                                    .isEqualTo("W602_HAN");
                            assertThat(f.path("properties").path("is_fallback").asBoolean())
                                    .isFalse();
                        });

        MvcResult alertDetailRes =
                mvc.perform(get("/api/v1/alerts/{id}", publishedAlertId)).andReturn();
        assertThat(alertDetailRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode alertDetail = readJson(alertDetailRes).path("data");
        assertThat(alertDetail.path("title").asText()).isEqualTo("Cảnh báo tại Trạm W602 Hà Nội");
        assertThat(alertDetail.path("ai_explanation").asText()).contains("84.5 mm");

        // 8. Kiểm chứng luồng Chat ẩn danh hỏi về mưa lớn nhất, bão active và giải thích cảnh báo
        Session chatSession = createChatSession("W602_SIT01");

        // (8a) Hỏi mưa lớn nhất (rain_top)
        WIRE_MOCK.resetRequests();
        stubGeminiText(
                "{\"intent\":\"rain_top\",\"items\":["
                        + "{\"code\":\"W602_HAN\",\"rain_1h\":84.5},"
                        + "{\"code\":\"W602_DNG\",\"rain_1h\":14.0}"
                        + "],\"variant\":\"FULL\"}",
                80);
        MvcResult chatRainRes =
                sendChatMessage(
                        chatSession,
                        "203.0.113.101",
                        Map.of("message", "Khu vực nào đang mưa lớn nhất hiện nay?"));
        assertThat(chatRainRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode chatRainData = readJson(chatRainRes).path("data");
        assertThat(chatRainData.path("is_fallback").asBoolean()).isFalse();
        assertThat(chatRainData.path("reply").asText())
                .contains("W602_HAN")
                .contains("84.5 mm")
                .contains("W602_DNG")
                .contains("14 mm");

        // (8b) Hỏi tình hình bão đang hoạt động (storm_active)
        double storedStormWind =
                jdbc.queryForObject(
                        "SELECT max_wind_speed FROM storms WHERE id = ?", Double.class, YAGI_ID);
        stubGeminiText(
                "{\"intent\":\"storm_active\",\"items\":[{\"storm_id\":\""
                        + YAGI_ID
                        + "\",\"max_wind_speed\":"
                        + storedStormWind
                        + "}],\"variant\":\"FULL\"}",
                75);
        MvcResult chatStormRes =
                sendChatMessage(
                        chatSession,
                        "203.0.113.101",
                        Map.of("message", "Tình hình bão hiện nay ra sao?"));
        assertThat(chatStormRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode chatStormData = readJson(chatStormRes).path("data");
        assertThat(chatStormData.path("is_fallback").asBoolean()).isFalse();
        assertThat(chatStormData.path("reply").asText())
                .contains("YAGI-24")
                .contains(YAGI_ID)
                .contains("GDACS");

        // (8c) Hỏi giải thích cảnh báo vừa phát (alert_explain với referenced_alert_id)
        String firstViolationKey =
                pendingJob.factualSnapshot().violations().stream()
                        .filter(v -> "rain_1h".equals(v.parameter()))
                        .findFirst()
                        .orElseThrow()
                        .violationKey();
        stubGeminiText(
                "{\"intent\":\"alert_explain\",\"alert_id\":\""
                        + publishedAlertId
                        + "\",\"violation_keys\":[\""
                        + firstViolationKey
                        + "\"],\"variant\":\"FULL\"}",
                90);
        MvcResult chatAlertRes =
                sendChatMessage(
                        chatSession,
                        "203.0.113.101",
                        Map.of(
                                "message",
                                "Giải thích nguyên nhân cảnh báo này?",
                                "referenced_alert_id",
                                publishedAlertId.toString()));
        assertThat(chatAlertRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode chatAlertData = readJson(chatAlertRes).path("data");
        assertThat(chatAlertData.path("is_fallback").asBoolean()).isFalse();
        assertThat(chatAlertData.path("reply").asText())
                .contains("Trạm W602 Hà Nội")
                .contains("84.5 mm")
                .contains(">= 50 mm")
                .contains("chênh 34.5 mm")
                .contains("CRITICAL");
    }

    @Test
    @DisplayName(
            "SIT-02: Ca biên — OWM khuyết thiếu trường rain (null → 0.0 mm) & thiếu hướng gió/gió giật không gây NPE")
    void sit02_EdgeCase_NullRainfallAndMissingWindDirectionOrGustHandledWithoutNpe()
            throws Exception {
        long obsEpoch = NOW.minusSeconds(120).getEpochSecond();
        // OWM payload không có đối tượng "rain", không có "gust"
        stubOwmRaw(
                21.0285,
                105.8542,
                """
                {
                  "coord": {"lon": 105.8542, "lat": 21.0285},
                  "weather": [{"main": "Clouds", "description": "scattered clouds"}],
                  "main": {"temp": 29.0, "feels_like": 31.0, "pressure": 1010, "humidity": 70},
                  "wind": {"speed": 5.2, "deg": 120},
                  "dt": %d
                }
                """
                        .formatted(obsEpoch));
        stubOwmRaw(
                16.0544,
                108.2022,
                """
                {
                  "coord": {"lon": 108.2022, "lat": 16.0544},
                  "weather": [{"main": "Clear", "description": "clear sky"}],
                  "main": {"temp": 30.5, "feels_like": 33.0, "pressure": 1009, "humidity": 65},
                  "wind": {"speed": 7.8, "deg": 90},
                  "dt": %d
                }
                """
                        .formatted(obsEpoch));

        MvcResult syncRes =
                mvc.perform(
                                post("/api/v1/internal/sync/weather")
                                        .header("X-Internal-Secret", INTERNAL_SECRET))
                        .andReturn();
        assertThat(syncRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(readJson(syncRes).path("data").path("status").asText()).isEqualTo("SUCCESS");

        // Kiểm chứng GET /api/v1/weather/current (GeoJSON & JSON)
        MvcResult currentGeoRes = mvc.perform(get("/api/v1/weather/current")).andReturn();
        assertThat(currentGeoRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode features = readJson(currentGeoRes).path("features");
        assertThat(features).hasSize(2);
        assertThat(features)
                .anySatisfy(
                        f -> {
                            assertThat(f.path("properties").path("code").asText())
                                    .isEqualTo("W602_HAN");
                            assertThat(f.path("properties").path("rain_1h").asDouble()).isZero();
                            assertThat(f.path("properties").path("rain_3h").asDouble()).isZero();
                            assertThat(f.path("properties").path("severity_rain").asText())
                                    .isEqualTo("NONE");
                        })
                .anySatisfy(
                        f -> {
                            assertThat(f.path("properties").path("code").asText())
                                    .isEqualTo("W602_DNG");
                            assertThat(f.path("properties").path("wind_speed").asDouble())
                                    .isEqualTo(7.8);
                            assertThat(f.path("properties").path("wind_gust").asDouble()).isZero();
                        });

        // Kiểm chứng GET /api/v1/weather/history
        MvcResult historyRes =
                mvc.perform(
                                get("/api/v1/weather/history")
                                        .param("location_code", "W602_DNG")
                                        .param("hours", "24"))
                        .andReturn();
        assertThat(historyRes.getResponse().getStatus()).isEqualTo(200);
        assertThat(readJson(historyRes).path("data").path("readings")).hasSize(1);

        // Kiểm chứng Chat station_current chỉ hiển thị tốc độ gió khi chọn fields ["rain_1h",
        // "wind_speed"]
        Session session = createChatSession("W602_SIT02");
        stubGeminiText(
                "{\"intent\":\"station_current\",\"code\":\"W602_DNG\","
                        + "\"fields\":[\"rain_1h\",\"wind_speed\"],\"variant\":\"FULL\"}",
                55);
        MvcResult chatRes =
                sendChatMessage(
                        session,
                        "203.0.113.102",
                        Map.of("message", "Thời tiết tại trạm W602_DNG hiện nay thế nào?"));
        assertThat(chatRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode chatData = readJson(chatRes).path("data");
        assertThat(chatData.path("is_fallback").asBoolean()).isFalse();
        assertThat(chatData.path("reply").asText())
                .contains("W602_DNG")
                .contains("0 mm")
                .contains("7.8 m/s")
                .doesNotContain("hướng gió");
    }

    @Test
    @DisplayName(
            "SIT-03: Ca biên — Không có bão active (active → dissipated): API trả mảng rỗng hợp lệ & Chat trả lời trung thực")
    void sit03_EdgeCase_ZeroActiveStormsAndStormDissipationLifecycle() throws Exception {
        // Bắt đầu bằng nạp 1 bão active
        stubGdacsSearch("search-yagi-active.json");
        stubGdacsYagiTrack();
        mvc.perform(
                        post("/api/v1/internal/sync/storms")
                                .header("X-Internal-Secret", INTERNAL_SECRET))
                .andExpect(res -> assertThat(res.getResponse().getStatus()).isEqualTo(200));

        // Chu kỳ tiếp theo GDACS trả danh sách rỗng -> YAGI-24 chuyển sang dissipated (SYS-RULE-001
        // Nhánh C)
        WIRE_MOCK.resetAll();
        stubGdacsSearch("search-empty.json");
        MvcResult emptySyncRes =
                mvc.perform(
                                post("/api/v1/internal/sync/storms")
                                        .header("X-Internal-Secret", INTERNAL_SECRET))
                        .andReturn();
        assertThat(emptySyncRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode syncData = readJson(emptySyncRes).path("data");
        assertThat(syncData.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(syncData.path("stormsDissipated").asInt()).isEqualTo(1);

        // Kiểm chứng GET /api/v1/storms?status=active&format=geojson trả features: []
        MvcResult geoRes =
                mvc.perform(
                                get("/api/v1/storms")
                                        .param("status", "active")
                                        .param("format", "geojson"))
                        .andReturn();
        assertThat(geoRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode geoJson = readJson(geoRes);
        assertThat(geoJson.path("type").asText()).isEqualTo("FeatureCollection");
        assertThat(geoJson.path("features")).isEmpty();

        // Kiểm chứng GET /api/v1/storms?status=active&format=json trả { success: true, data: [] }
        MvcResult jsonRes =
                mvc.perform(get("/api/v1/storms").param("status", "active").param("format", "json"))
                        .andReturn();
        assertThat(jsonRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode jsonBody = readJson(jsonRes);
        assertThat(jsonBody.path("success").asBoolean()).isTrue();
        assertThat(jsonBody.path("data").isArray()).isTrue();
        assertThat(jsonBody.path("data")).isEmpty();
        assertThat(jsonBody.path("message").asText())
                .contains("Hiện không có cơn bão nào đang hoạt động");

        // Kiểm chứng Chat hỏi bão khi 0 bão active -> trả 200 fallback trung thực, 0 gọi Gemini
        Session session = createChatSession("W602_SIT03");
        WIRE_MOCK.resetRequests();
        MvcResult chatRes =
                sendChatMessage(
                        session,
                        "203.0.113.103",
                        Map.of("message", "Hiện nay có cơn bão nào đang hoạt động không?"));
        assertThat(chatRes.getResponse().getStatus()).isEqualTo(200);
        JsonNode chatData = readJson(chatRes).path("data");
        assertThat(chatData.path("is_fallback").asBoolean()).isTrue();
        assertThat(chatData.path("reply").asText()).contains("Hiện không có bão đang hoạt động");
        WIRE_MOCK.verify(0, postRequestedFor(urlEqualTo(GEMINI_PATH)));
    }

    @Test
    @DisplayName(
            "SIT-04: Ca biên — Câu hỏi Chat rỗng, đúng 500 ký tự vs 501 ký tự & kiểm tra spatial_filter (Master Timeline §3.7)")
    void sit04_EdgeCase_ChatBoundaryLengthsAndSpatialFilterValidation() throws Exception {
        Session session = createChatSession("W602_SIT04");

        // 1. Câu hỏi rỗng hoặc toàn khoảng trắng -> 400 CHT_400_VALIDATION
        for (String blankMsg : List.of("", "   ")) {
            MvcResult res = sendChatMessage(session, "203.0.113.104", Map.of("message", blankMsg));
            assertThat(res.getResponse().getStatus()).isEqualTo(400);
            assertThat(readJson(res).path("errorCode").asText()).isEqualTo("CHT_400_VALIDATION");
        }

        // 2. Câu hỏi dài quá 500 ký tự (501 ký tự) -> 400 CHT_400_VALIDATION
        String msg501 = "Mưa lớn ".concat("a".repeat(493));
        assertThat(msg501.codePointCount(0, msg501.length())).isEqualTo(501);
        MvcResult res501 = sendChatMessage(session, "203.0.113.104", Map.of("message", msg501));
        assertThat(res501.getResponse().getStatus()).isEqualTo(400);
        assertThat(readJson(res501).path("errorCode").asText()).isEqualTo("CHT_400_VALIDATION");

        // 3. Câu hỏi đúng biên 500 ký tự -> 200 OK
        String msg500 = "Mưa lớn ".concat("a".repeat(492));
        assertThat(msg500.codePointCount(0, msg500.length())).isEqualTo(500);
        MvcResult res500 = sendChatMessage(session, "203.0.113.104", Map.of("message", msg500));
        assertThat(res500.getResponse().getStatus()).isEqualTo(200);

        // 4. spatial_filter đảo ngược [minLng > maxLng] -> 400 CHT_400_INVALID_SPATIAL_FILTER
        MvcResult badBbox =
                sendChatMessage(
                        session,
                        "203.0.113.104",
                        Map.of(
                                "message",
                                "Mưa lớn nhất ở đâu?",
                                "spatial_filter",
                                Map.of("bbox", List.of(109.0, 15.0, 105.0, 17.0))));
        assertThat(badBbox.getResponse().getStatus()).isEqualTo(400);
        assertThat(readJson(badBbox).path("errorCode").asText())
                .isEqualTo("CHT_400_INVALID_SPATIAL_FILTER");
    }

    @Test
    @DisplayName(
            "SIT-05: Ca biên — Tính nhất quán Cache Redis 3 vùng & giữ nguyên cache khi đồng bộ OWM gặp lỗi")
    void sit05_EdgeCase_RedisCacheHitAndSelectiveEvictionAcrossModules() throws Exception {
        // 1. Gọi cả 3 endpoint GeoJSON để nạp đủ 3 vùng cache Redis
        mvc.perform(get("/api/v1/locations").param("format", "geojson"))
                .andExpect(res -> assertThat(res.getResponse().getStatus()).isEqualTo(200));
        mvc.perform(get("/api/v1/weather/current"))
                .andExpect(res -> assertThat(res.getResponse().getStatus()).isEqualTo(200));
        mvc.perform(get("/api/v1/storms").param("status", "active"))
                .andExpect(res -> assertThat(res.getResponse().getStatus()).isEqualTo(200));

        assertThat(redis.hasKey(LOCATIONS_CACHE_KEY)).isTrue();
        assertThat(redis.hasKey(WEATHER_CACHE_KEY)).isTrue();
        assertThat(redis.hasKey(STORMS_CACHE_KEY)).isTrue();

        // 2. Khi OWM trả HTTP 429 (chu kỳ FAILED), cache weather_current KHÔNG bị xóa (W3-04)
        WIRE_MOCK.stubFor(
                WireMock.get(urlPathEqualTo(OWM_PATH)).willReturn(aResponse().withStatus(429)));
        MvcResult failedSync =
                mvc.perform(
                                post("/api/v1/internal/sync/weather")
                                        .header("X-Internal-Secret", INTERNAL_SECRET))
                        .andReturn();
        assertThat(failedSync.getResponse().getStatus()).isEqualTo(200);
        assertThat(readJson(failedSync).path("data").path("status").asText()).isEqualTo("FAILED");
        assertThat(redis.hasKey(WEATHER_CACHE_KEY)).isTrue();
        assertThat(redis.hasKey(LOCATIONS_CACHE_KEY)).isTrue();
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private UUID insertStation(String code, String name, double lng, double lat) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO locations (id, code, name, region, geom, is_active)
                VALUES (?, ?, ?, 'Bắc Bộ', ST_SetSRID(ST_MakePoint(?, ?), 4326), true)
                """,
                id,
                code,
                name,
                lng,
                lat);
        return id;
    }

    private int createThresholdViaApi(
            String name,
            String parameter,
            String operator,
            double value,
            String severity,
            String description)
            throws Exception {
        MvcResult res =
                mvc.perform(
                                post("/api/v1/alert-thresholds")
                                        .header("X-Admin-Api-Key", ADMIN_KEY)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                mapper.writeValueAsBytes(
                                                        Map.of(
                                                                "name", name,
                                                                "parameter", parameter,
                                                                "operator", operator,
                                                                "threshold_value", value,
                                                                "severity_level", severity,
                                                                "description", description,
                                                                "is_enabled", true))))
                        .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
        int id = readJson(res).path("data").path("id").asInt();
        createdThresholdIds.add(id);
        return id;
    }

    private void stubOwmStation(
            double lat,
            double lon,
            double rain1h,
            double rain3h,
            double windSpeed,
            double windGust,
            int windDeg,
            long epochSeconds) {
        String payload =
                """
                {
                  "coord": {"lon": %s, "lat": %s},
                  "weather": [{"main": "Rain", "description": "heavy intensity rain"}],
                  "main": {"temp": 26.5, "feels_like": 29.0, "pressure": 1002, "humidity": 92},
                  "wind": {"speed": %s, "deg": %d, "gust": %s},
                  "rain": {"1h": %s, "3h": %s},
                  "dt": %d
                }
                """
                        .formatted(
                                lon,
                                lat,
                                windSpeed,
                                windDeg,
                                windGust,
                                rain1h,
                                rain3h,
                                epochSeconds);
        stubOwmRaw(lat, lon, payload);
    }

    private void stubOwmRaw(double lat, double lon, String rawJson) {
        WIRE_MOCK.stubFor(
                WireMock.get(urlPathEqualTo(OWM_PATH))
                        .withQueryParam("lat", equalTo(String.valueOf(lat)))
                        .withQueryParam("lon", equalTo(String.valueOf(lon)))
                        .willReturn(okJson(rawJson)));
    }

    private void stubGdacsSearch(String fixtureName) {
        WIRE_MOCK.stubFor(
                WireMock.get(urlPathEqualTo(GDACS_SEARCH_PATH))
                        .withQueryParam("eventlist", equalTo("TC"))
                        .willReturn(okJson(readFixture("fixtures/gdacs/" + fixtureName))));
    }

    private void stubGdacsYagiTrack() {
        WIRE_MOCK.stubFor(
                WireMock.get(urlPathEqualTo(GDACS_TRACK_PATH))
                        .withQueryParam("eventtype", equalTo("TC"))
                        .withQueryParam("eventid", equalTo(YAGI_ID))
                        .willReturn(okJson(readFixture("fixtures/gdacs/track-yagi.json"))));
    }

    private void stubGeminiText(String modelText, int totalTokens) throws Exception {
        String envelope =
                mapper.writeValueAsString(
                        Map.of(
                                "modelVersion",
                                MODEL,
                                "candidates",
                                List.of(
                                        Map.of(
                                                "finishReason",
                                                "STOP",
                                                "content",
                                                Map.of(
                                                        "parts",
                                                        List.of(Map.of("text", modelText))))),
                                "usageMetadata",
                                Map.of("totalTokenCount", totalTokens)));
        WIRE_MOCK.stubFor(WireMock.post(urlEqualTo(GEMINI_PATH)).willReturn(okJson(envelope)));
    }

    private record Session(UUID id, String token) {}

    private Session createChatSession(String title) throws Exception {
        MvcResult res =
                mvc.perform(
                                post("/api/v1/chat/sessions")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(mapper.writeValueAsBytes(Map.of("title", title))))
                        .andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
        JsonNode data = readJson(res).path("data");
        return new Session(
                UUID.fromString(data.path("session_id").asText()),
                data.path("session_token").asText());
    }

    private MvcResult sendChatMessage(Session session, String clientIp, Map<String, Object> body)
            throws Exception {
        return mvc.perform(
                        post("/api/v1/chat/sessions/{id}/messages", session.id())
                                .remoteAddress("127.0.0.1")
                                .header("X-Forwarded-For", clientIp)
                                .header("X-Session-Token", session.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsBytes(body)))
                .andReturn();
    }

    private JsonNode readJson(MvcResult res) throws Exception {
        return mapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String readFixture(String resourcePath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("Fixture tồn tại: %s", resourcePath).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được fixture: " + resourcePath, e);
        }
    }

    private static int availablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Không tìm được cổng trống cho Redis SIT", e);
        }
    }
}
