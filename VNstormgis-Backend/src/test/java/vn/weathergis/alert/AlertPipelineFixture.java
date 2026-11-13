package vn.weathergis.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.weathergis.alert.ai.AlertContentGuardrail;
import vn.weathergis.alert.ai.GeminiGenerationResult.GeminiAlertDraft;
import vn.weathergis.alert.ai.GeminiProperties;
import vn.weathergis.alert.dto.AlertGenerationJobView;
import vn.weathergis.alert.service.AlertGenerationWorker;
import vn.weathergis.alert.service.AlertPersistenceService;
import vn.weathergis.automation.dto.WeatherSyncResult;
import vn.weathergis.automation.service.ThresholdEvaluationScheduler;
import vn.weathergis.common.AbstractPostGisIntegrationTest;

/** Real PostGIS, evaluator, listener, worker and HTTP API; only Gemini is replaced by WireMock. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
abstract class AlertPipelineFixture extends AbstractPostGisIntegrationTest {
    static final WireMockServer GEMINI = new WireMockServer(0);
    static final OffsetDateTime OBSERVED_AT = OffsetDateTime.parse("2026-11-05T10:00:00+07:00");

    static {
        GEMINI.start();
    }

    @DynamicPropertySource
    static void geminiProperties(DynamicPropertyRegistry registry) {
        registry.add("weathergis.ai.gemini.enabled", () -> "true");
        registry.add("weathergis.ai.gemini.api-key", () -> "offline-wiremock-key");
        registry.add("weathergis.ai.gemini.model", () -> "w407-test-model");
        registry.add("weathergis.ai.gemini.base-url", GEMINI::baseUrl);
        registry.add("weathergis.alert.worker.enabled", () -> "false");
        registry.add("weathergis.alert.worker.initial-delay-ms", () -> "3600000");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired TestRestTemplate http;
    @Autowired ThresholdEvaluationScheduler evaluator;
    @Autowired AlertPersistenceService persistence;
    @Autowired AlertGenerationWorker worker;
    @Autowired AlertContentGuardrail guardrail;
    @Autowired GeminiProperties geminiProperties;

    UUID stationId;
    String stationCode;
    final List<Integer> rules = new ArrayList<>();
    final List<String> storms = new ArrayList<>();
    private List<UUID> activeStations;
    private List<Integer> enabledRules;
    private List<String> activeStorms;
    private long initialAuditId;

    @BeforeEach
    void isolate() {
        GEMINI.resetAll();
        activeStations =
                jdbc.query(
                        "select id from locations where is_active",
                        (rs, i) -> rs.getObject(1, UUID.class));
        enabledRules =
                jdbc.query(
                        "select id from alert_thresholds where is_enabled",
                        (rs, i) -> rs.getInt(1));
        activeStorms =
                jdbc.query(
                        "select id from storms where status = 'active'",
                        (rs, i) -> rs.getString(1));
        initialAuditId =
                jdbc.queryForObject("select coalesce(max(id), 0) from data_sync_logs", Long.class);
        jdbc.update("update locations set is_active = false where is_active");
        jdbc.update("update alert_thresholds set is_enabled = false where is_enabled");
        jdbc.update("update storms set status = 'dissipated' where status = 'active'");
        stationId = UUID.randomUUID();
        stationCode = "W407_" + stationId.toString().substring(0, 8);
        jdbc.update(
                """
                insert into locations (id, code, name, region, geom, is_active)
                values (?, ?, 'Trạm Đà Nẵng', 'W4-07', ST_SetSRID(ST_MakePoint(108.2, 16.0), 4326), true)
                """,
                stationId,
                stationCode);
    }

    @AfterEach
    void clean() {
        geminiProperties.setEnabled(true);
        jdbc.update(
                "delete from alert_violation_keys where job_id in (select id from alert_generation_jobs where location_id = ?)",
                stationId);
        jdbc.update(
                "update alert_generation_jobs set alert_id = null where location_id = ?",
                stationId);
        jdbc.update("delete from alerts where location_id = ?", stationId);
        jdbc.update("delete from alert_generation_jobs where location_id = ?", stationId);
        jdbc.update("delete from weather_records where location_id = ?", stationId);
        jdbc.update("delete from locations where id = ?", stationId);
        rules.forEach(id -> jdbc.update("delete from alert_thresholds where id = ?", id));
        storms.forEach(id -> jdbc.update("delete from storms where id = ?", id));
        jdbc.update(
                "delete from data_sync_logs where id > ? and source = 'AI_MONITOR'",
                initialAuditId);
        activeStations.forEach(
                id -> jdbc.update("update locations set is_active = true where id = ?", id));
        enabledRules.forEach(
                id ->
                        jdbc.update(
                                "update alert_thresholds set is_enabled = true where id = ?", id));
        activeStorms.forEach(
                id -> jdbc.update("update storms set status = 'active' where id = ?", id));
        rules.clear();
        storms.clear();
    }

    int rule(String parameter, String operator, double value, String severity, boolean enabled) {
        int id =
                jdbc.queryForObject(
                        """
                insert into alert_thresholds
                    (name, parameter, operator, threshold_value, severity_level, description, is_enabled)
                values ('W4-07 fixture', ?, ?, ?, ?, 'Offline integration fixture', ?) returning id
                """,
                        Integer.class,
                        parameter,
                        operator,
                        value,
                        severity,
                        enabled);
        rules.add(id);
        return id;
    }

    void weather(double rain1h, double rain3h, double wind, double gust) {
        jdbc.update(
                """
                insert into weather_records
                    (location_id, recorded_at, temperature, feels_like, humidity, pressure,
                     wind_speed, wind_direction, wind_gust, rain_1h, rain_3h,
                     weather_condition, weather_description)
                values (?, ?, 25, 25, 80, 1000, ?, 90, ?, ?, ?, 'Rain', 'W4-07 fixture')
                """,
                stationId,
                OBSERVED_AT,
                wind,
                gust,
                rain1h,
                rain3h);
    }

    String storm(double lng, double lat) {
        String id = "W407_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
                """
                insert into storms (id, name, year, category, max_wind_speed, status,
                                    start_date, geom_current, updated_at)
                values (?, 'Bão kiểm thử', 2026, 'Tropical Storm', 80, 'active', ?,
                        ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)
                """,
                id,
                OBSERVED_AT.minusDays(1),
                lng,
                lat,
                OBSERVED_AT);
        storms.add(id);
        return id;
    }

    void evaluate() {
        evaluator.evaluateAfterSync(
                new WeatherSyncResult(
                        "OPENWEATHERMAP", "SUCCESS", 1, 1, OBSERVED_AT, OBSERVED_AT, null));
    }

    AlertGenerationJobView onlyJob() {
        UUID id =
                jdbc.queryForObject(
                        "select id from alert_generation_jobs where location_id = ? and status = 'PENDING' order by created_at desc, id desc limit 1",
                        UUID.class,
                        stationId);
        return persistence.findJobById(id).orElseThrow();
    }

    void validGeminiResponse(AlertGenerationJobView job) throws Exception {
        GeminiAlertDraft draft = guardrail.fallback(job);
        String payload =
                mapper.writeValueAsString(
                        Map.of("title", draft.title(), "ai_explanation", draft.aiExplanation()));
        GEMINI.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(
                                com.github.tomakehurst.wiremock.client.WireMock.anyUrl())
                        .willReturn(
                                com.github.tomakehurst.wiremock.client.WireMock.okJson(
                                        geminiEnvelope(payload))));
    }

    String geminiEnvelope(String text) throws Exception {
        return mapper.writeValueAsString(
                Map.of(
                        "modelVersion",
                        "w407-test-model",
                        "candidates",
                        List.of(
                                Map.of(
                                        "finishReason",
                                        "STOP",
                                        "content",
                                        Map.of("parts", List.of(Map.of("text", text)))))));
    }

    JsonNode publishAndRead(AlertGenerationJobView pending, boolean fallback) throws Exception {
        worker.runBatch();
        var job = persistence.findJobById(pending.jobId()).orElseThrow();
        assertThat(job.status().name()).isEqualTo("COMPLETED");
        assertThat(job.isFallback()).isEqualTo(fallback);
        assertThat(job.alertId()).isNotNull();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alerts where id = ?",
                                Long.class,
                                job.alertId()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alert_violation_keys where job_id = ? and alert_id = ?",
                                Long.class,
                                job.jobId(),
                                job.alertId()))
                .isEqualTo(job.violationKeys().size());
        var detail = http.getForEntity("/api/v1/alerts/" + job.alertId(), String.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = mapper.readTree(detail.getBody()).path("data");
        assertThat(data.path("id").asText()).isEqualTo(job.alertId().toString());
        assertThat(data.path("is_fallback").asBoolean()).isEqualTo(fallback);
        assertThat(data.path("title").asText()).isNotBlank();
        assertThat(data.path("ai_explanation").asText()).isNotBlank();
        assertThat(data.path("trigger_reason").asText()).isEqualTo(job.triggerReason());
        var stored =
                jdbc.queryForMap(
                        """
                        select title, ai_explanation, trigger_reason, severity_level,
                               threshold_id, is_fallback, factual_snapshot::text as snapshot
                        from alerts where id = ?
                        """,
                        job.alertId());
        assertThat(stored.get("title")).isEqualTo(data.path("title").asText());
        assertThat(stored.get("ai_explanation")).isEqualTo(data.path("ai_explanation").asText());
        assertThat(stored.get("trigger_reason")).isEqualTo(job.triggerReason());
        assertThat(stored.get("severity_level")).isEqualTo(job.severityLevel());
        assertThat(stored.get("threshold_id")).isEqualTo(job.representativeThresholdId());
        assertThat(stored.get("is_fallback")).isEqualTo(fallback);
        assertThat(mapper.readTree((String) stored.get("snapshot")))
                .isEqualTo(data.path("factual_snapshot"));
        assertThat(data.path("factual_snapshot").path("violations"))
                .hasSize(job.violationKeys().size());
        assertThat(data.path("factual_snapshot").path("coordinates").get(0).asDouble())
                .isEqualTo(108.2);
        assertThat(data.path("factual_snapshot").path("coordinates").get(1).asDouble())
                .isEqualTo(16.0);
        JsonNode active =
                mapper.readTree(http.getForEntity("/api/v1/alerts/active", String.class).getBody());
        JsonNode feature = null;
        for (JsonNode candidate : active.path("features")) {
            if (candidate.path("id").asText().equals(job.alertId().toString())) feature = candidate;
        }
        assertThat(feature).isNotNull();
        assertThat(feature.path("geometry").path("coordinates").get(0).asDouble()).isEqualTo(108.2);
        assertThat(feature.path("geometry").path("coordinates").get(1).asDouble()).isEqualTo(16.0);
        assertThat(feature.path("properties").path("is_fallback").asBoolean()).isEqualTo(fallback);
        return data;
    }
}
